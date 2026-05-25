## Context

A engine começou com `Scene : Node()` em `godot-style-foundation` (a primeira change foundational arquivada). Esse desenho misturou dois conceitos: a **raiz da árvore** (um Node entre Nodes) e o **driver/dono da árvore viva** (input, size, viewport, flags de fase de loop, traversals, queries). A Godot — fonte de inspiração arquitetural declarada no `CLAUDE.md` — separa explicitamente esses dois conceitos: a cena é um arquivo (`PackedScene`), e a árvore viva é dona de um `SceneTree` que **não é Node** e **não tem hierarquia própria**. O `MainLoop` da Godot opera sobre o `SceneTree`; o `root: Viewport` (um Node qualquer) é dele.

O sintoma na nossa base hoje:

- `:games:tictactoe` e `:games:demos` subclassam `Scene` apenas para popular a árvore inicial (`TicTacToeScene`, `DemoSwitcherScene`). Não há gameplay nessas classes — são "setup", e Godot pediria isso como `_ready()` do root, não como subclasse do tree.
- `SceneLoader.load(...)` força `root as? Scene` (`SceneLoader.kt:59`), fixando o tipo do root num conceito que nem deveria estar na árvore. O bundle do Pong (`pong/scene.json`) declara `engine.Scene` como root só para passar nesse cast.
- 13 sítios em demos/tictactoe chamam `rootScene()?.x`. O nome carrega o vocabulário antigo e está prestes a ser revisto pelo editor futuro de qualquer forma.

A janela para fazer essa separação está aberta agora: temos 3 jogos, um bundle real, e o editor visual ainda não existe (zero usuário externo). O custo de mudar agora é confinado e linear na quantidade de sítios. Adiar é multiplicar dívida.

## Goals / Non-Goals

**Goals:**

- Eliminar a sobrecarga conceitual de `Scene` como `Node`. Após esta change, "Scene" como tipo Kotlin não existe; o que existe é `SceneTree` (runtime) e `SceneFile` (formato).
- Liberar o tipo do root do JSON. `SceneLoader.load` retorna `Node`; root pode ser `Node`, `Node2D`, `Camera2D`, qualquer subclasse registrada.
- Dar a cada Node vivo um ponteiro O(1) para a sua `SceneTree` (`Node.tree`).
- Migrar Pong, TicTacToe e Demos para o novo modelo sem perda de comportamento observável (mesmo input, mesma física, mesmo render).
- Manter a API code-only ergonômica: `Host.run(SceneTree(root = ...))` é a forma única e clara.
- Estabelecer o invariante "SceneTree não é Node" em `CLAUDE.md` para evitar drift futuro.

**Non-Goals:**

- Não implementa "múltiplas cenas vivas" (overlay/loading screen como árvore irmã). O `SceneTree` desta change tem **um** `root`. Multi-tree fica como exploração futura se ganhar utilidade.
- Não promove `onResize` a `Signal<Vec2>`. O slot é um callback simples; promover a Signal pisa em `node-timer`, que reivindica ser o primeiro Kotlin Signal a cruzar para Python.
- Não toca em `PhysicsSystem` além da assinatura. O modelo de colisão é revisitado em `collision-overhaul` (in flight); esta change só ajusta o que `step()` recebe.
- Não introduz `PackedScene` como recurso Kotlin nem mecanismo `instantiate()` separado de `BundleLoader`. `SceneFile`/`SceneLoader`/`BundleLoader` já tocam essa responsabilidade; renomear/reorganizar fica para uma change futura, se necessário.
- Não muda o schema do `SceneFile` (continua `version: 2`). Só muda o valor do `type` no root de bundles que apontavam para `engine.Scene`.
- Não promove `engine.Scene` a typealias de `Node` (zero shim). O nome `Scene` desaparece do `:engine`.

## Decisions

### D1: `SceneTree` é uma classe Kotlin pura, não-Node, não-`@Serializable`, num pacote novo `com.neoutils.engine.tree`

`SceneTree` carrega exclusivamente o que era driver/host/query na `Scene` antiga: o ponteiro para o `root: Node`, o `input` injetado pelo loop, o `size: Vec2` injetado pelo host, o `viewport: Rect` (computado), as flags `isMutationDeferred`/`isRendering`, as operações `start/stop/process/physicsProcess/render/applyPending`, e as queries `getNodesInGroup`/`currentCamera`/`screenToWorld`/`worldToScreen`.

**Não é Node:** não tem `children`, `parent`, `transform`, lifecycle hooks, nem participa de traversals. Conceitualmente é o dono da árvore, não um membro dela.

**Não é `@Serializable`:** todo o estado é runtime (input, size, flags, ponteiros). Não há nada significativo a persistir. Eliminar a anotação remove a anomalia de a classe ter sido `@Serializable` antes só por herdar de `Node`.

**Pacote novo `com.neoutils.engine.tree`:** separa visualmente "membros da árvore" (`com.neoutils.engine.scene.*`) de "dona da árvore" (`com.neoutils.engine.tree.SceneTree`). Alternativa rejeitada: deixar tudo em `engine.scene` — viável tecnicamente, mas dilui o sinal "isso aqui não é Node".

**Alternativas consideradas:**

- *Manter `Scene : Node()` e renomear membros driver para sub-objeto interno* — preserva API mas mantém a confusão conceitual. Rejeitado: não resolve o sintoma raiz (subclasse de Scene como pattern de setup) e mantém o cast `root as? Scene`.
- *Tornar `SceneTree` uma interface com implementação default* — sobre-engenharia. A engine não tem caso de uso para múltiplas implementações de tree. YAGNI.

### D2: `PhysicsSystem.step(tree: SceneTree)`, não `step(root: Node)`

A enumeração de colliders precisa partir do `root`. Duas opções:

| Opção                                          | Prós                                            | Contras                                            |
|------------------------------------------------|-------------------------------------------------|----------------------------------------------------|
| `step(tree: SceneTree)`                        | Acesso às flags `isMutationDeferred` se precisar; simetria com debug overlay; chamador único | Pequeno acoplamento de `:engine.physics` ao tipo `SceneTree` |
| `step(root: Node)`                             | Acopla menos; physics é puro tree-walk          | Quem chama precisa expor o root; se um dia o physics precisar das flags do tree, refator |

Escolho `step(tree)` — o physics já lê estado da árvore via `node.children`; um ponteiro a mais para o tree não muda nada substantivo, e mantém a porta aberta para o `PhysicsSystem` registrar/consultar fases de mutação. `collision-overhaul` (in flight) muda profundamente este método; alinhar para `SceneTree` agora simplifica a fusão.

### D3: `Node.tree` é um campo cacheado, setado em `attachToLiveTree`

Toda chamada futura a `node.tree` é O(1). Walk-up via `parent.parent...` ficaria O(profundidade) e seria pago repetidamente por código de gameplay (`tree?.width` em `onProcess`/`onDraw`).

**Mecânica:**

- `Node.attachToLiveTree(tree: SceneTree)` — assina `this.tree = tree`, dispara `onEnter()`, recurse para children. Hoje a função aceita `Scene`; muda para `SceneTree`.
- `Node.detachFromLiveTree()` — dispara `onExit()`, zera `this.tree = null`, recurse para children **antes** de zerar o próprio (lifecycle: child `onExit` antes do pai zerar — mesma ordem atual).
- `SceneTree.start()` chama `root.attachToLiveTree(this)`. `SceneTree.stop()` chama `root.detachFromLiveTree()`.
- `Node.addChild(child)`: se `this.isLive`, chama `child.attachToLiveTree(this.tree!!)`. (Hoje passa `this.scene!!`; mesma estrutura, tipo diferente.)
- `Node.removeChild(child)`: se `child.isLive`, chama `child.detachFromLiveTree()`. Inalterado.

**Vivacidade:** `isLive: Boolean` em `Node` continua existindo, agora **derivado** como `tree != null` — não há mais dois campos a sincronizar (`scene` e `isLive`). Pequena simplificação que cai do lugar.

**Alternativa rejeitada:** *campo opcional + helper `tree(): SceneTree?` separado* — duplicidade sem ganho. O setter é `internal` (só `attachToLiveTree`/`detachFromLiveTree` mexem); leitura externa é o campo direto.

### D4: API code-only do host é explícita: `Host.run(SceneTree(root = ...), config)`

Decisão do usuário no `/opsx:explore`: a opção explícita força entender o modelo (`SceneTree` é um objeto real, não detalhe escondido). Não há overload `run(root: Node, config)` que envolva implicitamente — quem chama paga as 18 letras a mais e ganha clareza. Pong, TicTacToe e Demos seguem este padrão.

### D5: Jogos não subclassam `SceneTree` para customizar setup; subclassam um Node root

`TicTacToeScene : Scene()` vira `TicTacToeRoot : Node()`. Setup que estava no construtor de `TicTacToeScene` move para `TicTacToeRoot.onEnter()`. Mesma ergonomia, mesmo número de classes, mas o que se subclassa agora é um Node (e portanto tudo o que o roadmap pretende suportar futuramente — `_ready`, `scriptInstance`, etc. — fica direto disponível). É exatamente o pattern Godot: o root é um Node qualquer; `_ready` popula filhos.

**Não há helper de tipo "GameRoot" no `:engine`.** A engine não tem opinião sobre o que o root deve fazer — é só um Node. Quem quiser instanciar `Node()` direto e empilhar `addChild` no chamador também funciona; subclassar é só convenção de organização.

### D6: `SceneFile` mantém `version: 2`; só o valor do `type` no root pode mudar

O schema (`SceneFile { version, root: NodeEntry }`) não muda. O que muda em `pong/scene.json` é o valor literal: `"type": "com.neoutils.engine.scene.Scene"` → `"type": "com.neoutils.engine.scene.Node"`. Como `NodeRegistry` deixa de registrar `engine.Scene`, abrir um JSON antigo dispara "no factory registered for node type: com.neoutils.engine.scene.Scene" — mensagem suficiente. Não introduzimos lógica de migração nem leitor de legado (não há usuários externos; demais bundles ou não existem ou nascem direto no formato novo).

**Alternativa rejeitada:** *bump `version: 3` com leitor de v2 que mapeia `engine.Scene` → `engine.Node`* — bagagem sem propósito. Custaria mais código de manutenção do que vale para um único arquivo.

### D7: `SceneTree.onResize` é um slot listener `((Float, Float) -> Unit)?`, não método aberto, não Signal

Hoje `Scene.onResize(width, height)` é método aberto sobrecarregado por subclasses. `SceneTree` não deve ser subclassado (D1), então método aberto sai. Duas alternativas:

| Opção                                       | Prós                                                                | Contras                                       |
|---------------------------------------------|---------------------------------------------------------------------|-----------------------------------------------|
| `var onResize: ((Float, Float) -> Unit)? = null` | Trivial; chamadores fazem `tree.onResize = { w, h -> ... }`         | Único listener (sobrescreve)                  |
| `val resized: Signal<Vec2>`                  | Múltiplos handlers; alinhado com a vibe Godot                       | Pisa em `node-timer` (que reivindica primazia) |

Escolho a primeira. Caso real do `onResize` na base atual: zero. Cobertura útil futura: hosts/jogos que precisem reposicionar HUD ou recomputar fits. Um único callback chega.

Se a necessidade aparecer, promoção a `Signal<Vec2>` é trivial num futuro change — não trava nada.

### D8: Zero backward compat; remoção completa de `Scene`, `rootScene()`, e `engine.Scene` no registry

Não há shim, alias, ou typealias. `Scene` some, `rootScene()` some, `engine.Scene` some do `NodeRegistry`. Justificativas:

- Não há consumidor externo (`:engine` é monorepo de aprendizado).
- Shims atrasam a internalização do novo modelo. Quem ler `Node.tree` aprende SceneTree direto; quem ler `rootScene().tree` aprende confusão.
- O type checker do Kotlin pega 100% dos sítios quebrados em compile time. Não é um cenário runtime-quebra-em-produção.

### D9: `engine.Scene` é removido de `NodeRegistry.registerEngineTypes()` sem mensagem custom

Tentar instanciar `com.neoutils.engine.scene.Scene` via JSON cai no caminho normal de erro: `"No factory registered for node type: com.neoutils.engine.scene.Scene"`. Nenhuma mensagem especial "esta change removeu Scene" — quem topar a mensagem entende imediatamente pelo nome.

## Risks / Trade-offs

**[Risco] Testes existentes em `:engine` quebram simultaneamente em múltiplos arquivos** (`SceneLoaderTest`, `NodeRegistryTest`, `DebugOverlayTest`, `PhysicsSystemTest`). → **Mitigação:** todos os testes que constroem `Scene` ou registram `engine.Scene` são adaptados na mesma change. O ciclo de feedback é compilar `:engine:test` após cada lote de alterações; o type checker guia o resto.

**[Risco] Bundle JSON do Pong abre na transição com `version: 2` mas root `engine.Scene` inválido**, gerando crash em devs que estejam num branch parcial. → **Mitigação:** o passo de migração de `pong/scene.json` (mudar o `type` do root) acontece **na mesma commit** que apaga `Scene::class` do `NodeRegistry`. Não há janela onde o arquivo está em estado inconsistente sob `main`.

**[Risco] `bundle-tictactoe` (in flight) baseou seu plano em `BundleLoader` devolvendo `Scene`.** → **Mitigação:** `bundle-tictactoe` é pausada até `scene-tree` arquivar. Quando retomar, o autor revisa proposal/design/tasks para refletir o novo retorno `Node` — simplificação líquida (não precisa mais criar uma classe `Scene` shell para Velha).

**[Risco] Demos têm padrões `rootScene() as? DemoSwitcherScene`** que se quebram se o tipo do root mudar. → **Mitigação:** o cast continua existindo, só muda o tipo: `tree?.root as? DemoSwitcherRoot`. Os 13 sítios são localizados via grep e migrados em uma passada.

**[Trade-off] Adicionar um campo `tree: SceneTree?` a `Node` aumenta o footprint de cada nó em 8 bytes (pointer JVM).** → **Aceito:** a engine é deliberadamente didática, não premium para performance; ter `tree` cacheado evita walk-up de `parent` em cada chamada de gameplay, o que pode ser O(profundidade) num Node mais fundo. O ganho de clareza+latência supera o custo de memória num cenário de aprendizado.

**[Trade-off] Pacote novo `com.neoutils.engine.tree` cria mais um lugar para procurar coisa.** → **Aceito:** `tree/SceneTree.kt` é um arquivo só por enquanto. Quando houver mais peças relacionadas à árvore (e.g. `SceneSwitch`, `TreeQuery`), elas se agrupam naturalmente lá. Alternativa de deixar em `engine.scene` foi rejeitada para reforçar visualmente "isto não é Node".

**[Trade-off] `SceneTree.onResize` é single-listener.** → **Aceito** com plano explícito de promover a `Signal<Vec2>` num change futuro se aparecer demanda real.
