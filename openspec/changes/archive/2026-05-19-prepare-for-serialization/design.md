## Context

A engine atual foi construída em três changes anteriores (`engine-foundation`, `engine-consistency`, `add-skiko-runtime`) com foco em code-only. Os jogos amarram comportamento entre nodes de jeitos que serializam mal:

- **Lambdas-em-construtor**: `Ball(onScore = { scorer -> leftScore.increment() })`, `Paddle.aiTargetY = { ball.transform.position.y }`. Lambdas não atravessam o disco.
- **Subclasses anônimas**: `Ball.collider = object : BoxCollider(...) { override onCollide(...) }`. Não têm nome de tipo para o editor referenciar.
- **Construtores com parâmetros obrigatórios**: `BoxCollider(var size: Vec2)`, `Paddle(playFieldHeight, upKey, downKey, ...)`. Hostis a "instancia vazio, depois popule" — o caminho natural de bibliotecas de deserialização.
- **Identidade frágil**: só `name: String` com default `class.simpleName`. Sem unicidade entre irmãos. Sem id estável. Impossível referenciar um node específico no arquivo.
- **Mistura de config e state**: `Ball.velocity`, `Board.cells`, `Score.value` convivem com config inicial; sem separação explícita, o serializador não sabe o que pular.

Por outro lado, várias coisas **já estão prontas** para serialização:
- Math primitives (`Vec2`, `Rect`, `Transform`) e `Color` já são `data class` simples.
- Scene graph já é uma árvore acíclica com pai/filhos explícitos.
- Renderer, Input e GameHost são SPIs não-serializáveis, e isso é o desejado — eles não aparecem no arquivo.
- O contrato lifecycle (`onEnter`/`onUpdate`/`onRender`/`onExit`) é bem definido e suficiente para deserialização "monta árvore, marca live, deixa `onEnter` propagar".

A meta dessa change é fechar a lacuna mínima: criar três primitivas (`NodeRef`, `Signal`, `@Inspect`), refatorar os jogos para usá-las, e fechar com um scene loader experimental que carrega `PongScene` de JSON e roda igual.

## Goals / Non-Goals

**Goals:**
- Permitir que uma cena seja descrita por um arquivo JSON: tipos de Node, árvore, propriedades iniciais, conexões entre nodes.
- Manter scene graph por herança como invariante (sem ECS).
- Preservar DX code-only razoável: factories opcionais cobrem a verbosidade do `apply { }`.
- Provar o caminho ponta-a-ponta com `PongScene`: salvar para `pong.scene.json`, carregar, jogar.
- Adicionar `kotlinx.serialization` como dependência sem violar invariante "`:engine` sem Compose".

**Non-Goals:**
- **Save game / snapshot de partida**: estado runtime (`velocity`, `cells`, `value`) fica `@Transient`. Próxima change.
- **Editor visual**: esta change só prepara o terreno. Não há UI nem inspector.
- **Conexão de signals por arquivo**: signals conectáveis apenas em code-only nesta change. Conexão declarativa exige UI e fica para mais tarde.
- **UUID por node**: nome único entre irmãos basta para o escopo blueprint.
- **Compiler plugin para auto-`@Transient`**: aceitamos a redundância de anotar `@Inspect` em uns campos e `@Transient` em outros. Reavaliar se virar dor real.
- **Migração automática de arquivos**: o formato é versão 1; mudanças futuras podem quebrar arquivos sem migrator. Aceitável enquanto não há editor publicado.

## Decisions

### D1 — `NodeRef<T : Node>` por path relativo

```kotlin
@Serializable
class NodeRef<T : Node>(
    @Inspect var path: String = "",
) {
    @Transient private var cached: T? = null
    @Transient private var resolvedAt: Node? = null

    fun resolve(from: Node): T? { /* walk path, cache */ }
}
```

- Path relativo no estilo Godot: `..` sobe um nível, segmentos separados por `/` descem, primeiro segmento sem `..` é um filho direto do nó-origem.
- Resolução **lazy**: a primeira chamada de `resolve(this)` dentro de `onUpdate` faz o walk e cacheia.
- Invalidação: o cache é invalidado quando o node-origem é re-anexado (re-attach detecta no `onEnter`) **ou** quando o callee chama `NodeRef.invalidate()` manualmente. Não tentamos detectar mutações arbitrárias da árvore — é o suficiente para o uso real (refs em geral apontam para nodes que vivem o jogo todo).
- Tipagem: `NodeRef<Ball>` força o consumidor a confirmar o tipo. Resolução faz `as? T` e devolve `null` se o nó resolvido não for do tipo esperado.

**Por que path em vez de UUID:** legibilidade no arquivo, refactor-amigável até pequena escala, alinha com Godot (a inspiração explícita). UUID entra se save game pedir.

**Alternativa considerada:** `NodeRef` por callback (`() -> T?`) — rejeitada porque não serializa; volta ao problema atual.

### D2 — `Signal<T>` mínimo

```kotlin
class Signal<T> {
    private val handlers = mutableListOf<(T) -> Unit>()
    operator fun plusAssign(handler: (T) -> Unit) { handlers += handler }
    operator fun minusAssign(handler: (T) -> Unit) { handlers -= handler }
    fun emit(value: T) { for (h in handlers.toList()) h(value) }
}
```

- **Não é `@Serializable`**: handlers são lambdas, não vão para o arquivo. O `Signal` em si é um campo `@Transient` do node que o expõe.
- Conexão code-only é `ball.onScore += { side -> leftScore.increment() }`.
- Conexão declarativa por arquivo fica adiada — exige um modelo de "qual método de qual node assina qual signal" que faz mais sentido com editor.
- `emit` snapshot-copia handlers antes de invocar para tolerar `+=`/`-=` durante emissão (não trava nem perde o handler novo, mas o novo só recebe a partir da próxima emissão).

**Por que não usar `kotlinx-coroutines-flow`:** trazer Flow só para event bus é peso demais para uma engine educativa. Lista de lambdas é o suficiente.

### D3 — `@Inspect` como anotação de superfície

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inspect(
    val displayName: String = "",
)
```

- Marca propriedade como inspecionável pelo editor **e** sinaliza que ela faz parte do "contrato sério" do node (configuração inicial).
- Não é por si só o gate de serialização — `kotlinx.serialization` não consulta anotações arbitrárias sem custom serializer. O **contrato com o autor** é:
  - Classe do Node leva `@Serializable`.
  - Toda propriedade que descreve configuração inicial ganha `@Inspect`.
  - Toda propriedade de estado runtime ganha `@Transient` explícito.
- Convenção testada na arquivagem: lint custom (futuro) ou code review garante que toda `var` é `@Inspect` ou `@Transient`. Por ora, é disciplina explicitada no CLAUDE.md.

**Alternativa considerada:** anotar só com `@Serializable`/`@Transient` (sem `@Inspect`). Rejeitada porque `@Inspect` carrega metadado adicional para o editor futuro (`displayName`, e futuramente faixas, enums, etc.) e separa semanticamente "interno mas serializado" de "exposto ao designer".

### D4 — Identidade: nome único entre irmãos com auto-suffix

- `Node.addChild(child)` continua aceitando qualquer nome, mas se já existir um irmão com o mesmo nome, sufixa `_2`, `_3`... até resolver.
- Auto-suffix preserva DX (não obriga o autor a pensar em unicidade em loops dinâmicos), e arquivos editados à mão ficam previsíveis (o editor garante unicidade antes de salvar).
- `parent.findChild(name: String): Node?` exposto publicamente para suportar `NodeRef` walking.
- **Não validamos unicidade ao construir o `name`** — só ao anexar. Isso permite construir nodes desconectados livremente.

**Alternativa considerada:** UUID por node. Rejeitada para o escopo blueprint (excesso de infra, arquivos ilegíveis). Pode entrar com save game.

### D5 — Construtores no-args + propriedades `var` com defaults

- Toda classe candidata a serializável tem construtor `()` (público no-args).
- Propriedades configuráveis são `var` com defaults sensatos, todas `@Inspect`.
- Construtores "ricos" (com parâmetros obrigatórios) deixam de existir nas classes serializáveis. DX volta via **factory functions** opcionais quando o ergonomia justificar:

```kotlin
fun paddle(upKey: Key? = null, downKey: Key? = null, ai: Boolean = false): Paddle =
    Paddle().apply {
        this.upKey = upKey
        this.downKey = downKey
        this.ai = ai
    }
```

Factories ficam num arquivo separado (`Paddle.kt` mantém a classe, `PaddleFactory.kt` ou um companion provê o helper). Não obrigatório.

**Alternativa considerada:** manter construtores ricos + classe paralela `*Data` para serialização. Rejeitada — dobra superfície de manutenção sem ganho real.

### D6 — Formato de arquivo `*.scene.json`

```json
{
  "version": 1,
  "root": {
    "type": "com.neoutils.engine.games.pong.PongScene",
    "name": "PongScene",
    "properties": { },
    "children": [
      {
        "type": "com.neoutils.engine.games.pong.Paddle",
        "name": "left",
        "properties": {
          "transform": { "position": { "x": 32.0, "y": 250.0 }, "scale": { "x": 1.0, "y": 1.0 }, "rotation": 0.0 },
          "upKey": "W",
          "downKey": "S",
          "ai": false,
          "speed": 360.0
        },
        "children": []
      }
    ]
  }
}
```

- Wrapper externo com `version` + `root`. Root é sempre um `Scene`.
- Cada node entry tem `type` (fully-qualified Kotlin class name), `name`, `properties` (mapa de `@Inspect` properties), `children` (recursão).
- `type` resolvido em tempo de carga via lookup numa `NodeRegistry` populada pelo autor do jogo ao iniciar (`NodeRegistry.register(PongScene::class) { PongScene() }`). Esse registry é necessário porque `Class.forName` + reflection em multi-module exige permissões/serviços que queremos evitar; registrar explicitamente é mais simples e funciona em qualquer setup.
- `properties` é desserializado via `kotlinx.serialization` JSON com o serializer da classe em si (gerado pelo plugin a partir de `@Serializable`).

**Alternativa considerada:** representação plana com lista de nodes + referências por id. Rejeitada — JSON aninhado espelha a árvore, é mais legível, e o editor naturalmente serializa por hierarquia.

### D7 — Scene Loader como prova de conceito

- `SceneLoader.save(scene: Scene): String` — caminha a árvore, monta o JSON.
- `SceneLoader.load(json: String): Scene` — parseia, resolve tipos via `NodeRegistry`, monta a árvore desconectada, `addChild` propaga até a raiz. Não chama `start()` — quem carrega decide quando ativar.
- Round-trip: `save(load(save(scene))) == save(scene)` (compara strings JSON normalizadas).
- Prova de aceite: Pong tem dois entrypoints — `Main.kt` (code-only) e `MainFromFile.kt` (carrega `pong.scene.json` colocado em `resources/`). Os dois resultados são jogáveis e idênticos.

## Risks / Trade-offs

- **Risco**: `kotlinx.serialization` em `:engine` aumenta tempo de build e tamanho do classpath. → **Mitigação**: dependência única, well-established, nenhum override do compilador além do plugin `kotlin.plugin.serialization`. Aceitável.
- **Risco**: Refatorar Pong/Demos pode regredir comportamento (especialmente colisão). → **Mitigação**: testes existentes de física continuam passando; teste manual em Skiko ao terminar; SpawnerDemo serve como teste contínuo de mutação durante traversal (já estabelecido).
- **Risco**: Disciplina de `@Inspect`/`@Transient` é só convenção. Autor esquece e vaza state no arquivo. → **Mitigação**: documentar no CLAUDE.md; teste unitário sobre PongScene round-trip pega vazamentos óbvios. Futuro: lint custom.
- **Risco**: `NodeRegistry` exige populamento manual — autor que esquecer um tipo vê load falhar em runtime. → **Mitigação**: erro de load é explícito (`UnknownNodeTypeException` com nome do tipo). Considerar futuramente um discover via reflection do package, mas não nesta change.
- **Trade-off**: DX code-only fica um pouco mais verbosa (apply blocks em vez de named args). → **Aceitável**: a meta declarada é "serialização é a forma principal de uso". Factories cobrem onde dói mais.
- **Trade-off**: Path-based refs quebram se autor renomeia um node sem renomear refs que apontam para ele. → **Aceitável** para o escopo: editor futuro corrige automaticamente em rename. Em code-only, é como variável: cuidado.
- **Trade-off**: Pong perde sua estrutura "Ball é dono de um collider filho anônimo". → **Aceitável**: `Ball : BoxCollider` é mais Godot-like e mais simples; nenhum benefício real estava na composição.

## Migration Plan

1. **Engine primeiro, jogos depois.** Implementar `NodeRef`/`Signal`/`@Inspect`/`SceneLoader` em `:engine` com testes. Jogos quebrando temporariamente é OK durante a change.
2. **Adicionar `@Serializable` nos primitives** (`Vec2`, `Rect`, `Transform`, `Color`) — efeito nulo no comportamento, libera o resto.
3. **Reescrever classes da engine** (`Node`, `Node2D`, `Shape`, `Text`, `BoxCollider`) para construtores no-args + `@Serializable` + `@Inspect`. Atualizar testes que dependem dos construtores antigos.
4. **Migrar Pong** — `Ball : BoxCollider`, `Signal<Goal.Side>`, `NodeRef<Node2D>`. Quebrar/atualizar `PongScene.init` para a nova superfície. Rodar manualmente.
5. **Migrar SpawnerDemo** — classes nomeadas. Rodar manualmente.
6. **Revisar Tic Tac Toe** — adicionar `@Serializable`/`@Inspect` onde fizer sentido; sem mudança de comportamento.
7. **Scene loader** — implementar `NodeRegistry`, `SceneLoader.save`/`load`. Adicionar `pong.scene.json` em `:games:pong/src/main/resources/`. Adicionar `MainFromFile.kt`.
8. **Atualizar CLAUDE.md** — nova capability, regras de `@Inspect`/`@Transient`, como rodar Pong a partir de arquivo.
9. **Sem rollback automático**: a change quebra DX em pontos. Rollback = reverter os commits.

## Open Questions

- **`NodeRegistry` global vs por-scene?** Inicial: um singleton global em `:engine`. Cada módulo de jogo registra seus tipos no startup do `Main`. Se ficar incômodo, evoluir para por-scene depois.
- **Comportamento de `addChild` quando o `Scene` ainda não está live**: hoje aceita sem `onEnter`. Continua igual. Auto-suffix aplica nos dois casos.
- **Como o scene loader lida com `Color.WHITE`/constantes?** O `@Serializable` data class serializa os fields RGBA. Constantes do companion não são preservadas como referência simbólica — são reidratadas como instâncias iguais. Aceitável.
- **Pong com `pong.scene.json` precisa de duas `Main.kt` ou uma só com flag CLI?** Decisão na implementação. Duas é mais legível como prova; uma com flag é mais compacto. Provavelmente duas.
