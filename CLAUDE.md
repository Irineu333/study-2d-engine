# CLAUDE.md

Orientação perene para contribuidores (humanos ou agentes). Mantenha este arquivo atualizado quando decisões fundamentais mudarem.

## Purpose

`nengine` é uma 2D game engine **construída para aprender arquitetura de engine**. Começa em modo code-only com jogos de exemplo (Pong é o primeiro) e evolui em direção a um editor visual. A meta é clareza didática e evolução incremental — não performance prematura.

Stack: Kotlin + Skiko (JVM Desktop). Skiko é o **único backend de render ativo no momento**; LWJGL está planejado como **segundo backend experimental** para revalidar a SPI (`Renderer`/`Input`/`GameHost`).

## Architectural Invariants

Toda mudança deve respeitar os quatro invariantes abaixo. Eles vêm das decisões arquiteturais consolidadas em `openspec/changes/archive/2026-05-18-engine-foundation/design.md` e não podem ser quebrados sem uma nova change OpenSpec discutindo a revisão.

1. **Scene graph estilo Godot, por herança.** Comportamento de gameplay é adicionado por subclasses de `Node` / `Node2D`. **Sem** `List<Component>` ou ECS. Cada Node tem sua identidade de tipo (`class Paddle : Node2D()`).
2. **`:engine` não depende de nenhum framework de UI/render.** O módulo `:engine` não declara — direta ou transitivamente — `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, AWT/Swing além do estritamente necessário, nem futuras dependências LWJGL/OpenGL/Vulkan. Quem precisa de qualquer um desses é o módulo de backend correspondente (e.g. `:engine-skiko`).
3. **Colisão via `CollisionObject2D` + `CollisionShape2D` + `PhysicsSystem` central.** Cada participante de colisão é uma subclasse de `CollisionObject2D` (`Area2D` para triggers; `StaticBody2D`/`CharacterBody2D`/`RigidBody2D` para corpos sólidos) com um ou mais `CollisionShape2D` filhos carregando um `Shape2D` polimórfico (`RectangleShape2D` ou `CircleShape2D`). O `PhysicsSystem.step(tree, dt)` integra `RigidBody2D` (gravity + accumulated forces → velocity), executa o TOI loop com impulse solver bilateral (linear + angular + Coulomb tangencial, combine rules `e = max(eA, eB)`, `μ = sqrt(μA · μB)`), integra rotação, e em seguida enumera todos os objects ativos, testando pares em broad phase O(N²) intencional, e dispara `onAreaEntered`/`onAreaExited`/`onBodyEntered`/`onBodyExited` exatamente uma vez por par-transição (enter no início da sobreposição, exit no fim — não há evento "still touching"). Os mesmos eventos são emitidos via signals built-in (`areaEntered`, `areaExited`, `bodyEntered`, `bodyExited`) em cada object. **Três paths de movimento solid-body** convivem: (a) `CharacterBody2D.moveAndCollide(motion)` para controle direto pelo script (paddles, snake head, player) — sweep TOI exato sem resposta de impulso; (b) `RigidBody2D` integrado pela engine — script aplica forças/impulsos via `applyForce`/`applyImpulse`/etc. e a engine resolve; (c) `StaticBody2D` imóvel. O sweep cobre os três pares de shape (circle-circle, circle-rect, rect-rect) com **qualquer combinação de rotação** dos transforms, contanto que ambos vivam no mesmo frame parent local (circle-vs-rotated-rect via inverse-rotate pro frame local do rect; rotated-rect-vs-rotated-rect via SAT temporal). `Area2D` permanece sensor puro (sem `moveAndCollide`); para "quem está em mim agora?" use `Area2D.getOverlappingAreas()` / `getOverlappingBodies()`.

### RigidBody2D vs CharacterBody2D

A engine oferece **dois modelos de movimento solid-body**, com critérios claros para escolher:

- **`CharacterBody2D`** — controle direto pelo script. O script lê input, computa `velocity`, e chama `moveAndCollide(velocity * dt)` para mover com sweep CCD-correto. **Sem resposta automática**: sem massa, sem restituição, sem inércia. Use para entidades sob controle do jogador ou IA simples (paddles em Pong, snake head, player platformer, naves em Asteroids).
- **`RigidBody2D`** — integração pela engine. Script aplica forças/impulsos (`applyForce`, `applyImpulse`, `applyTorque`, etc.) ou seta velocidades diretamente; a engine integra (`v += (g + F/m) * dt`), faz sweep com TOI loop e resolve cada contato via impulso linear+angular + fricção de Coulomb. Use para objetos "soltos no mundo" com semântica física real (bolinhas quicando, blocos empurrados, projéteis com gravidade, peças de demolição).

Em colisão cruzada, `Static` e `Character` são tratados como massa infinita pela perspectiva do `Rigid` — o `Rigid` recebe impulso, eles não recoluem (preserva o controle direto do paddle/Character pelo script). Para "kick" do Character no Rigid, o script chama `rigid.applyImpulse(...)` manualmente dentro de `_on_body_entered`.
4. **`Renderer`, `Input` e `GameHost` permanecem SPIs obrigatórias.** Skiko é o backend padrão (`:engine-skiko`); `:engine` não pode vazar tipos de Skiko (`org.jetbrains.skia.*`, `SkikoView`, `SkiaLayer`) — quem precisa de Skia direto é o `:engine-skiko`. LWJGL está planejado como segundo backend experimental para revalidar a SPI. Jogos novos devem usar Skiko por default.
5. **A árvore viva é dona de `SceneTree`, não de uma `Scene` que é Node.** `SceneTree` não é `Node` e não é `@Serializable`; a classe `Scene` não existe mais em `:engine`. Nodes alcançam a árvore via `node.tree` (set no attach, null no detach). `SceneTree` não é subclassável para customizar setup — para popular a árvore inicial, escreva um Node root com `onEnter`. `SceneLoader.load` e `BundleLoader` devolvem `Node` (root livre); o host envolve em `SceneTree(root = ...)` antes de `run(...)`.

## Performance Notes

`Node2D.world()` cacheia o resultado por nó (campo `@Transient cachedWorld`). O cache começa `null` e é populado na primeira leitura; leituras consecutivas sem mutação retornam em O(1). O cache é invalidado de forma **eager** — o nó mutado e todos os seus descendentes `Node2D` (atravessando `Node` puros no meio do caminho) são marcados dirty imediatamente — nos seguintes momentos:

1. **Atribuição do `transform` local** (`node.transform = ...`) — o setter custom de `Node2D.transform` chama `invalidateWorldTransformRecursive()`.
2. **Mudança de hierarquia** (`addChild`/`removeChild`, incluindo caminhos diferidos via `pendingAdd`/`pendingRemove`) — `Node.applyAdd` e `Node.applyRemove` chamam `invalidateWorldTransformRecursive()` no filho.
3. **Mutação de ancestral** — como a invalidação propaga recursivamente pelos descendentes, alterar o transform de um pai invalida automaticamente todos os `Node2D` filhos/netos.

O cache é **estado runtime puro**: nunca persiste em `scene.json` (anotado com `@Transient` do `kotlinx.serialization`). Se você adicionar um novo caminho de mutação de transform ao engine, deve chamar `invalidateWorldTransformRecursive()` para manter a coerência.

`Node.tree` segue o mesmo contrato: é cacheado em `attachToLiveTree(tree)` durante `start()` e zerado em `detachFromLiveTree()` durante `stop()`. Leituras em gameplay (`tree?.input`, `tree?.viewport`) são O(1). O campo é `@Transient` e nunca persiste em `scene.json`.

## Module Structure & How to Run

```
:engine                ← núcleo Kotlin puro (scene graph, math, SPIs, física, loop, DX, GameHost SPI)
:engine-bundle         ← carregamento de cena via bundle (scene.json + scripts/) + ScriptHost SPI agnóstica de linguagem
:engine-bundle-python  ← implementação Python do ScriptHost via GraalPy 24.x; distribui stubs .pyi em resources/stubs/
:engine-bundle-lua     ← implementação Lua do ScriptHost via LuaJ 3.0.x (JAR puro JVM); distribui stubs LuaCATS em resources/stubs/
:engine-skiko          ← backend Skiko puro sobre SkiaLayer + JFrame (SkikoRenderer, SkikoInput, SkikoHost) — backend padrão
:games:pong            ← jogo Pong executável (humano vs IA), roda em Skiko — prova viva da fundação
:games:tictactoe       ← jogo Velha (humano vs humano), roda em **Skiko** com scripting Lua — sentinela do segundo backend de scripting
:games:demos           ← cenas de demonstração visual das melhorias da engine (roda em Skiko)
:games:hello-world     ← exemplo code-only mínimo — único Label centralizado em Skiko, sem bundle nem scripting
:games:snake           ← jogo Snake executável (grid-based, tick-based), roda em Skiko com scripting Python — primeiro validador de gameplay discreto e mutação dinâmica de scene graph
```

Os módulos `:shared` e `:desktopApp` do template KMP foram **removidos** durante a change `engine-foundation`.

Para rodar Pong:

```sh
./gradlew :games:pong:run
```

`Main.kt` constrói o `PythonScriptHost` via `PythonScriptHost.create()`, carrega o bundle `pong/` (em `:games:pong/src/main/resources/pong/`, com `scene.json` na raiz e `scripts/*.py`) via `BundleLoader.fromResources("pong", scripting = python)` — que devolve o `Node` raiz destacado — e em seguida envolve em `SceneTree(root = ...)` antes de entregar ao `SkikoHost`. O `scene.json` é a fonte da verdade da árvore — editar o JSON ou os `.py` altera o comportamento sem recompilar Kotlin.

Durante o jogo:

- `W`/`S` movem o paddle esquerdo
- `F1` liga/desliga overlay de FPS (tratado pelo `GameHost`, configurável via `GameConfig.toggleFpsKey`)
- `F2` liga/desliga visualização de colliders (idem, via `GameConfig.toggleCollidersKey`)

Para rodar Velha:

```sh
./gradlew :games:tictactoe:run
```

Velha carrega via `BundleLoader.fromResources("tictactoe", scripting = lua)` (em `:games:tictactoe/src/main/resources/tictactoe/`, com `scene.json` na raiz e `scripts/board.lua`) — mesma pipeline do Pong; aqui ela serve como prova viva de que `ScriptHost` é polimórfico (TTT usa `LuaScriptHost`, Pong usa `PythonScriptHost`) sob o mesmo backend de render (`SkikoHost`). O root é um `Node` plain com `script = "scripts/board.lua"` e quatro filhos: um `Camera2D` (`bounds = 600×600`), quatro `Line2D` formando a grade 3×3, e um `Label` `status`. Toda lógica de gameplay (estado das 9 células, hit-test, vitória/empate, ghost, linha vencedora) mora em `board.lua` (chunk retorna `{ extends = "Node", _ready = ..., _process = ..., _draw = ... }`); o único Kotlin que sobrou no módulo é `Main.kt`, que instancia `LuaScriptHost.create()`, chama o `BundleLoader`, envolve o root em `SceneTree(root = ...)` e entrega ao `SkikoHost`.

Durante o jogo:

- Clique esquerdo numa célula vazia faz a jogada do jogador atual (X começa, depois alterna O)
- Quando a partida termina (vitória ou empate), o próximo clique esquerdo em qualquer lugar reinicia (esse clique só reinicia — não joga)
- `F1` liga/desliga overlay de FPS (tratado pelo `GameHost`, configurável via `GameConfig.toggleFpsKey`)
- `F2` liga/desliga visualização de colliders (idem, via `GameConfig.toggleCollidersKey`; Velha não usa colliders, mas o overlay continua disponível)

Para rodar Demos:

```sh
./gradlew :games:demos:run
```

Durante a execução:

- `1` Solar system — Sol amarelo no centro com 8 planetas (Mercúrio→Netuno) e as luas conhecidas (Lua na Terra; Io, Europa, Ganimedes, Calisto em Júpiter; Titã em Saturno; Tritão em Netuno) orbitando seus pais. Saturno carrega um `SaturnRing` (anel achatado via scale não-uniforme). Exercita o invariante A1 — composição aninhada de transform — em até 4 níveis (Sol → órbita-planeta → planeta → órbita-lua → lua), validando que `world()` cacheia corretamente sob mutação simultânea de múltiplos ancestrais por frame.
- `2` Scale hierarchy — pai com `scale` oscilando faz o filho crescer e encolher (composição de scale via `Shape.onRender`, A1)
- `3` Spawner — clique do mouse adiciona bolinhas durante `onUpdate`; o trap central (`Area2D`) remove durante `onAreaEntered` (mutação durante traversal, A4); F2 mostra que o overlay de colliders sai do `GameHost` (A2) e usa cores distintas para `Area2D` vs `PhysicsBody2D`.
- `4` Collision stress — 30 `RigidBody2D` bolinhas (`restitution=1f`, `friction=0f`) dentro de uma arena `BoundaryWalls` (4 `StaticBody2D` que acompanham `tree.size` em tempo real durante resize). O **engine solver** integra cada bolinha (sem `moveAndCollide` no script), sweep com TOI loop e aplica o impulso bilateral (linear + angular) em cada contato — bola pesada empurra bola leve (transferência de momento), sem tunneling estrutural mesmo em alta velocidade. F2 mostra os AABBs das `CollisionShape2D` (vermelho para Bodies). F3 mostra `Σp`, `ΣL`, `ΣKE` com sparklines: KE permanece constante (elástico).
- `5` Rotating box — 12 `CharacterBody2D` bolinhas vivem como filhas de um `Node2D` "caixa" que rotaciona **e** translada a cada frame (envelope AABB quicando nas paredes da scene). 4 `StaticBody2D` paredes são filhas do mesmo wrapper rotativo, em coordenadas locais. `moveAndCollide` opera no parent frame compartilhado (= local da caixa), de modo que o sweep continua axis-aligned mesmo com a caixa girando em world — bolinhas batem corretamente em paredes e em siblings sem tunelar. Exercita o invariante de invalidação por mutação de ancestral (D5 do design.md) sob carga real de colisão e em frame rotativo não-estacionário. F2 mostra os AABBs envelopados dos `CollisionShape2D` rotacionados em world.
- `6` Tumbling swarm — 16 quadrados `RigidBody2D` (`restitution=1f`, `friction=0.4f`) com velocidade linear e angular iniciais, dentro de `BoundaryWalls` (paredes acompanham `tree.size` no resize). O **engine solver** resolve cada contato pelo caminho rotated do sweep (`sweepRotatedRectRotatedRect`) com leading-corner contact point, impulso normal + Coulomb tangencial — squares quicam elasticamente contra paredes e entre si, com spin perceptível em hits glancing. F2 mostra os OBBs rotacionados envelope. F3 mostra `ΣL` (angular momentum) conservado em hits elásticos frictionless e drift sob fricção.
- `F1` liga/desliga overlay de FPS (tratado pelo `GameHost`, configurável via `GameConfig.toggleFpsKey`)
- `F2` liga/desliga visualização de colliders (idem, via `GameConfig.toggleCollidersKey`)
- `F3` liga/desliga overlay de momento (`Σp`, `ΣL`, `ΣKE` + sparklines), configurável via `GameConfig.toggleMomentumOverlayKey`

Para rodar Snake:

```sh
./gradlew :games:snake:run
```

Snake carrega via `BundleLoader.fromResources("snake", scripting = python)` (em `:games:snake/src/main/resources/snake/`, com `scene.json` na raiz e `scripts/{snake,food,score,gameover}.py`) — mesma pipeline do Pong, e é o primeiro jogo do repo com **gameplay discreto/tick-based**. O `scene.json` declara um `Camera2D` com `bounds = 400×400` (campo lógico = grid 20×20 células de 20px), um `Node2D` `Snake` com `script = "scripts/snake.py"` e um filho `Timer` `MoveTimer` (`waitTime = 0.125`, `processCallback = PHYSICS`, `autostart = true`), um `ColorRect` `Food`, e dois `Label`s (`ScoreLabel`, `GameOverLabel`). A `Snake` mantém uma lista interna de células e, a cada `MoveTimer.timeout`, adiciona um `ColorRect` filho na cabeça e remove o da cauda — exercício canônico de mutação dinâmica do scene graph dirigida por script. Wraparound usa `bounds.size` como contrato lógico via módulo em Python.

Durante o jogo:

- Setas movem a cobra (direção corrente lida em `_process` via `wasKeyPressed`); reversão 180° é ignorada; mudanças entre ticks colapsam para a última seta válida (buffer de 1-slot aplicado no início do próximo `_tick`)
- `Enter` reinicia o jogo após `gameOver` (`reset()` remove segmentos, recria a cabeça de comprimento 3 no centro, emite `restart`, religa o `MoveTimer`)
- `F1` liga/desliga overlay de FPS (tratado pelo `GameHost`, configurável via `GameConfig.toggleFpsKey`)
- `F2` liga/desliga visualização de colliders (idem, via `GameConfig.toggleCollidersKey`; Snake não usa colliders, então a tecla é no-op visual)

Para rodar Hello World:

```sh
./gradlew :games:hello-world:run
```

Janela 800×600 com `Hello, world!` centralizado; sem input — o texto se recentraliza ao redimensionar.

## Coding Conventions

- **Comentários só para o "por quê" não-óbvio.** Nunca para o "o quê" (nomes já dizem). Evite docstrings cerimoniais.
- **Identificadores em inglês.** Texto in-game e mensagens ao usuário podem ser em português; nomes de classe/função/variável devem permanecer em inglês.
- **API pública de `:engine` documentada com KDoc** quando o uso pretendido não for óbvio.
- **Imutabilidade onde for barata.** `Vec2`, `Rect`, `Transform`, `Color` são data classes; operações retornam novas instâncias. Para ergonomia de escrita, `Node2D` expõe as properties `position: Vec2`, `rotation: Float` e `scale: Vec2`, que são puro açúcar sobre `transform = transform.copy(...)` — o invariante de imutabilidade dos valores não muda (escrever `vec2.y = X` continua proibido; em Kotlin não compila, em Python lança `AttributeError`).
- **Folhas `Node2D` shipped por `:engine` são `open` por default.** A política existe para coerência com o invariante #1 ("comportamento é adicionado por herança"). Tornar uma folha `final` exige justificativa documentada no KDoc da classe explicando que invariante quebraria sob herança; absente isso, mantenha `open class`.
- **Sem dependências escondidas.** Se um módulo precisa de um framework de UI/render (Skiko, AWT/Swing, futuramente LWJGL), declara no `build.gradle.kts` daquele módulo. Se `:engine` ganhar uma dependência transitiva proibida, é bug.
- **Testes para regras invariantes.** Cada decisão arquitetural com risco de regressão (lifecycle ordering, broad phase) tem teste unitário.
- **`Node2D.onDraw` desenha em local space.** Cada subclasse de `Node2D` (shipped ou de jogo) implementa `onDraw` em coordenadas **locais** — origem `(0, 0)`, sem somar `world().position` manualmente. `SceneTree.render` empilha a `transform` local do nó via `Renderer.pushTransform(translation, rotation, scale)` antes de chamar `onDraw` e desempilha depois (com `try/finally`), de modo que rotação e escala dos ancestrais chegam ao desenho pela stack do renderer — não há código manual de rotação em `onDraw`. Nodes não-`Node2D` (e.g. `Timer`) NÃO recebem push; apenas encaminham o walk aos filhos.

### Camera2D define o mundo virtual

Quando uma árvore tem um `Camera2D` com `current = true`, seu `bounds` (um `Rect` em coordenadas de mundo) define a região visível do mundo virtual; o `Renderer` projeta esse `bounds` sobre a surface (`tree.size`) respeitando `aspectMode` antes do tree-walk de `_draw`. Padrão é `AspectMode.FIT` (zoom uniforme, letterbox bars nas margens sobressalentes). Árvores sem `Camera2D` (ex.: `:games:tictactoe` antes da migração para Camera2D-everywhere) caem no fallback identity — coordenadas mundiais são pixels da surface. A view transform é aplicada por `SceneTree.render` via `Renderer.pushTransform/popTransform` (LIFO) com `rotation = 0f`, e em seguida cada `Node2D` recebe seu próprio push da `transform` local — o stack final ao tempo de `onDraw` é `view ∘ ancestor1.local ∘ ... ∘ self.local`, equivalente a `view ∘ self.world` por composição de pushes. O overlay de debug de colliders usa a mesma view transform para que os bounds desenhem alinhados ao mundo projetado.

### Serialization contract (`@Inspect` / `@Transient`)

Classes candidatas a aparecer numa scene file levam `@Serializable` (do `kotlinx.serialization`). Para essas classes vale a disciplina abaixo — exigida porque a engine ainda não tem lint custom para fazê-la cumprir:

- Toda `var` é anotada **ou** com `@Inspect` (configuração inicial: vai para o arquivo e é exposta ao editor futuro) **ou** com `@Transient` (estado runtime: nunca persiste).
- `@Inspect` mora em `com.neoutils.engine.serialization.Inspect`; `@Transient` é o do `kotlinx.serialization`.
- Não deixe uma `var` sem anotação. Mesmo que o serializer atual aceite, fica armadilha para o próximo refator.
- `val` não precisa de anotação — não é mutável e por padrão fica de fora do JSON gerado por `SceneLoader`.

Exemplo:

```kotlin
@Serializable
class Paddle : Node2D() {
    @Inspect var speed: Float = 360f
    @Inspect var ai: Boolean = false

    @Transient internal var lastInputDirection: Float = 0f
}
```

### Scripting contract (Python)

A engine usa **Python via GraalPy** como mecanismo de scripting. O modelo é Godot-like: o Node Kotlin permanece a instância nativa; um script `.py` é **anexado** a ele via slot `Node.scriptInstance`. Somente jogos que dependem de `:engine-bundle-python` têm custo do runtime GraalPy — é estritamente opt-in.

#### Estrutura de um script

```python
# extends Node2D          ← obrigatório: tipo Node nativo que o script adorna

speed: float = 360.0      ← export: anotação top-level, descoberta estaticamente via AST
ai: bool = False
target: NodeRef = NodeRef("")

def _ready(self):                       ← hooks underscore-prefixed estilo Godot; todos opcionais
    ...

def _process(self, dt: float):          ← frame-step (dt variável)
    ...

def _physics_process(self, dt: float):  ← fixed-step (dt constante, default 60Hz)
    ...

def _draw(self, renderer):
    ...

def _exit_tree(self):
    ...

def _on_area_entered(self, area):       ← apenas para CollisionObject2D (Area2D, StaticBody2D, ...)
    ...

def _on_area_exited(self, area):
    ...

def _on_body_entered(self, body):
    ...

def _on_body_exited(self, body):
    ...
```

- **`# extends <NodeType>`** na primeira linha não vazia. `<NodeType>` é resolvido contra o `NodeRegistry` (ex.: `Node2D`, `Area2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`). Falhar nesta linha é erro fatal.
- **Exports** são atribuições anotadas no top-level. Tipos suportados: `int`, `float`, `bool`, `str`, `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`, `Optional[T]` (ou `T | None`). Qualquer outro tipo é silenciosamente ignorado.
- **Estado runtime** fica em `self._private` (convenção: atributos com prefixo `_` não são exports, são instâncias por-nó).
- **Hooks fixos** (todos opcionais; ausência é no-op):
  - `_ready(self)` — equivale a `Kotlin Node.onEnter()`, roda quando o nó entra na live tree.
  - `_process(self, dt)` — frame-step, `dt` variável; em geral para animação visual e input.
  - `_physics_process(self, dt)` — fixed-step (default 60Hz via `GameConfig.physicsHz`), `dt` constante; integração e detecção de colisão moram aqui.
  - `_draw(self, renderer)` — desenho do próprio nó (Godot-orthodox: o nó é seu próprio visual).
  - `_exit_tree(self)` — limpeza ao sair da live tree.
  - `_on_area_entered(self, area)` — disparado pelo `PhysicsSystem` quando um `CollisionObject2D` deste nó começa a sobrepor um `Area2D`. One-shot por begin-of-overlap.
  - `_on_area_exited(self, area)` — análogo, no fim da sobreposição.
  - `_on_body_entered(self, body)` — disparado quando começa sobreposição com um `PhysicsBody2D` (Static ou Character).
  - `_on_body_exited(self, body)` — análogo, no fim da sobreposição.
  - **Built-in signals** em cada `CollisionObject2D`: `area_entered`, `area_exited`, `body_entered`, `body_exited`, todos `Signal`s do tipo apropriado. Conecte com `self.body_entered.connect(handler)` ou via `script_of(other_node).area_entered.connect(...)`.
- **Bindings implícitos no Context**: `Vec2`, `Color`, `Rect`, `Transform`, `NodeRef`, `Key`, `MouseButton`, `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`, `Shape2D`, `RectangleShape2D`, `CircleShape2D`, `Node2D`, `Camera2D`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `Label`, `Signal`, `signal`. `MouseButton` expõe `MouseButton.Left`, `MouseButton.Right`, `MouseButton.Middle` — para mouse discreto use `tree.input.wasMouseClicked(MouseButton.Left)` (e converta `pointerPosition` para mundo via `tree.screenToWorld(...)` antes de hit-testar).
- **Acessar/escrever transform local**: `Node2D` expõe `position`, `rotation`, `scale` como properties — `self.position = Vec2(...)`, `self.rotation = math.pi / 4`, `self.scale = Vec2(2.0, 2.0)`. Não escreva componente individual: `self.position.y = 5.0` lança `AttributeError` em runtime porque `Vec2.y` é `val` Kotlin (fail-fast intencional). O idioma correto é reconstruir o `Vec2`: `self.position = Vec2(self.position.x, 5.0)`. Para ler a transform composta (mundo), use `self.world(): Transform`; ex.: `wp = self.world().position`.
- **Coordenadas surface ↔ mundo**: scripts que precisam converter input em pixels (mouse) para coordenadas do mundo (ou vice-versa) usam `Camera2D.screenToWorld(screenPosition, sceneSize)` e `Camera2D.worldToScreen(worldPosition, sceneSize)`. Ambos honram `bounds` + `aspectMode` e caem em identity quando `bounds.size <= 0`. Não usados em Pong/Demos/Tic hoje, mas é o caminho documentado para qualquer jogo novo com clique-no-mundo.
- **Fail-fast**: qualquer erro (parse, `extends` desconhecido, exception num hook) propaga até o `Main.kt` e crasha o processo.

#### Signals

Comunicação entre nós (gols, scoreboards, etc.) usa `Signal<T>`. Declare no top-level via a factory `signal(<type>)`:

```python
# extends CharacterBody2D

scored: Signal = signal(str)          ← descoberto via AST; instanciado por-nó em attach

def _on_area_entered(self, area):
    if area.name == "rightGoal":
        self.scored.emit("Left")      ← emit dispara handlers em ordem de inscrição
```

Do outro lado, `connect` retorna um `Disposable` para futuro `dispose()`:

```python
def _ready(self):
    ball = script_of(self._node.findChild("Ball"))
    ball.scored.connect(lambda side: print("goal:", side))
```

A factory `signal(type)` recebe o tipo apenas como dica de documentação — Python não tem `Signal<T>` runtime. Cada attach instancia um `Signal<Any?>` Kotlin novo e o expõe no wrapper Python, sombreando qualquer placeholder de módulo.

#### Wiring no `scene.json`

```json
{
  "type": "engine.Node2D",
  "script": "scripts/paddle.py",
  "properties": {
    "transform": { "position": { "x": 0.0, "y": 0.0 }, "scale": { "x": 1.0, "y": 1.0 }, "rotation": 0.0 },
    "speed": 360.0,
    "ai": false
  }
}
```

`scene.json` usa **bag único** `properties` (estilo Godot): propriedades `@Inspect` nativas do Node e exports do script anexado convivem no mesmo objeto. O arquivo MUST declarar `"version": 2` no topo — `version: 1` é rejeitado com mensagem explícita nomeando a change `godot-style-properties` (sem leitor legacy; migre manualmente fundindo `props` em `properties`).

`BundleLoader` faz tree-walk no JSON, coleta todos os `script` paths, carrega via o `ScriptHost` recebido no parâmetro `scripting` (validando que o `host.extension` bate com a extensão de cada path), atacha cada script ao Node e devolve um `ScriptAttachment` ao `SceneLoader`. O `SceneLoader` então roteia cada chave de `properties`:

- chave bate só com `@Inspect` do Node → aplica via setter `@Inspect`.
- chave bate só com export do script → aplica via `setExport` (após `PropCoercion`).
- chave bate com ambos → **erro fatal de colisão**, nomeando o nó, a chave, o tipo do Node e o path do script.
- chave não bate com nada → **erro fatal de chave desconhecida**, listando os candidatos (`@Inspect` names do Node e, se houver script, exports do script com o path).

Essa disciplina é fail-fast: typos viram crash com mensagem acionável em vez de propriedades silenciosamente ignoradas.

#### Construindo o ScriptHost e passando ao BundleLoader

O `Main.kt` do jogo constrói um `ScriptHost` explicitamente e injeta no `BundleLoader`:

```kotlin
val python = PythonScriptHost.create()
val root = BundleLoader.fromResources("pong", scripting = python)
val tree = SceneTree(root = root)
```

`PythonScriptHost.create()` boota o `Context` GraalPy (operação cara) e devolve uma instância segura para compartilhar entre múltiplos loads — reuse a mesma referência em vez de chamar `create()` repetidamente. Bundles sem nenhum `script` referenciado podem omitir `scripting` (ou passar `null`); nesse caso o GraalPy nunca é instanciado. Se o bundle referencia ao menos um `script` e `scripting` é `null`, o `BundleLoader` falha-fast nomeando o path encontrado e recomendando a chamada literal `PythonScriptHost.create()`.

#### Inspecting Python scripts with IDE

O `:engine-bundle-python` publica stubs `.pyi` em `src/main/resources/stubs/engine/`. Para habilitar autocompletar e type-checking no seu editor (Pyright/Pylance):

1. Extraia os stubs do JAR ou localize o source em `engine-bundle-python/src/main/resources/stubs/`.
2. Adicione o diretório `stubs/` ao `extraPaths` do Pyright (em `pyrightconfig.json`):
   ```json
   { "extraPaths": ["engine-bundle-python/src/main/resources/stubs"] }
   ```
3. Para Pylance (VS Code), configure `python.analysis.extraPaths` no `settings.json`.

### Scripting contract (Lua)

A engine também aceita **Lua via LuaJ 3.0.x** como segundo backend de scripting, ativado via `LuaScriptHost.create()` (em `:engine-bundle-lua`). LuaJ é JAR puro JVM — sem libs nativas, igual a Skiko no eixo de render. O contrato é Godot-style + LÖVE-style: cada `.lua` retorna uma **tabela** com `extends`, `exports`, `signals`, e hooks. Filosofia LÖVE: todos os símbolos da engine vivem numa tabela global **`nengine.*`** (factories, enums, tipos de Node, `script_of`).

#### Estrutura de um script

```lua
return {
    extends = "Node2D",                         -- obrigatório: string

    exports = {                                 -- opcional
        speed = { type = "float", default = 360 },
        ai    = { type = "bool",  default = false },
    },

    signals = {                                 -- opcional
        scored = "string",                      -- valor é typeHint puramente documental
    },

    _ready           = function(self) end,
    _process         = function(self, dt) end,
    _physics_process = function(self, dt) end,
    _draw            = function(self, renderer) end,
    _exit_tree       = function(self) end,
    _on_area_entered = function(self, area) end,
    _on_area_exited  = function(self, area) end,
    _on_body_entered = function(self, body) end,
    _on_body_exited  = function(self, body) end,
}
```

- **`extends`** é string e é resolvido contra `NodeRegistry.findBySimpleName(...)`. Falha-rápido se ausente, não-string, ou tipo desconhecido.
- **Exports** são entradas `{ type, default }` em `exports = {...}`. Tipos suportados: `"float"`, `"int"`, `"bool"`, `"string"`, `"Vec2"`, `"Color"`, `"Rect"`, `"NodeRef"`, `"Key"`. Padrões podem usar literais Lua ou `nengine.Vec2(...)`/`nengine.Color(...)`.
- **Signals** declarados em `signals = {...}` viram `Signal<Any?>` por-instância em `attach`. Conecte com `self.scored:connect(handler)` e emita com `self.scored:emit("Left")`.
- **`nengine.*` namespace**: `nengine.Vec2(x,y)`, `nengine.Color(r,g,b,a)`, `nengine.Rect(origin,size)`, `nengine.Transform(pos,scale,rot)`, `nengine.NodeRef(path)`, `nengine.signal(typeHint)`, `nengine.Key.W`, `nengine.MouseButton.Left`, e bindings dos tipos de Node (`nengine.Node2D`, `nengine.Area2D`, `nengine.Timer`, etc.).
- **`self` é o instance wrapper** — uma `LuaTable` cuja metatable (`__index`/`__newindex`) roteia para o Node Kotlin via reflexão. Leituras/escritas de `self.position`, `self.rotation`, `self.scale` viram chamadas ao `Node2D` setter (que invalida o cache de world transform). Leituras de `self.tree`, `self.name`, etc. retornam wrappers idiomáticos. Para alcançar o Node Kotlin diretamente (por exemplo para `NodeRef:resolve(...)`), use `self.node`.
- **`require` é sandboxado**: scripts só conseguem importar via `require "scripts.utils"` se `scripts/utils.lua` existir no mesmo bundle. Filesystem real, `package.path`, `package.cpath` e `dofile`/`loadfile` são removidos do `_G`.
- **Fail-fast**: parse/compile errors, `extends` desconhecido, exception num hook — tudo propaga para o caller Kotlin com o path do script e linha quando disponível.

#### Construindo o LuaScriptHost e passando ao BundleLoader

```kotlin
val lua = LuaScriptHost.create()
val root = BundleLoader.fromResources("tictactoe", scripting = lua)
val tree = SceneTree(root = root)
```

`LuaScriptHost.create()` constrói um `Globals` via `JsePlatform.standardGlobals()`, instala a tabela `nengine`, e wira `package.searchers` contra o `BundleSource` ativo. O host é seguro para reuso entre múltiplos loads — não reconstrua um por carregamento de bundle.

#### Inspecting Lua scripts with IDE (LuaCATS)

O `:engine-bundle-lua` publica stubs LuaCATS em `src/main/resources/stubs/engine/*.lua`. Para habilitar autocompletar e type-checking em `sumneko-lua` (VS Code):

1. Crie `.luarc.json` na raiz do projeto:
   ```json
   { "workspace": { "library": ["engine-bundle-lua/src/main/resources/stubs"] } }
   ```
2. O language server passa a reconhecer `nengine.*`, `Node2D`, `Vec2`, `Signal`, etc.

## OpenSpec Workflow

Mudanças materiais (arquitetura, API pública, novos módulos, novas capabilities) **passam por uma change OpenSpec antes da implementação**. Roteiro padrão:

1. Explore mode: `/opsx:explore <tema>` — gera notas e perguntas.
2. Propose: `/opsx:propose <change-name>` — gera proposal, design, specs, tasks.
3. Apply: `/opsx:apply <change-name>` — implementa tarefa por tarefa.
4. Verify: `/opsx:verify <change-name>` — confere se a implementação fecha com os artefatos.
5. Archive: `/opsx:archive <change-name>` — congela a change e atualiza specs principais.

Para uma feature nova ou refator significativo: abra uma change OpenSpec, **não** um PR direto de código.

## Roadmap

- Plano de evolução vive em [`ROADMAP.md`](./ROADMAP.md). 
- Histórico de changes concluídas: [`openspec/changes/archive/`](./openspec/changes/archive/).
