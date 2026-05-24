# scene-serialization Specification

## Purpose

Primitivas de serialização do scene graph: `NodeRef<T>` (referência tipada por caminho relativo), `Signal<T>` (event bus por nó), anotação `@Inspect` para marcar propriedades inspecionáveis/serializadas, `NodeRegistry` (mapa de nome de tipo → factory) e `SceneLoader` (round-trip `Scene` ↔ JSON via `kotlinx.serialization`). Habilitam scene files como entry point dos jogos e preparam terreno para o editor visual estilo Godot.
## Requirements
### Requirement: NodeRef resolves a typed reference by relative path

The engine SHALL provide a `NodeRef<T : Node>` type that carries a relative path string and resolves at runtime to a node of type `T` in the scene graph. The path syntax SHALL use Godot-style segments: `..` to walk up to the parent, segment names separated by `/` to walk down, and an empty path to point at the bearer itself. Resolution SHALL be a method `resolve(from: Node): T?` that walks the path starting from `from` and returns the resolved node cast to `T`, or `null` if the path is invalid, the target does not exist, or the target is not of type `T`. `NodeRef` MUST be `@Serializable` so that the path persists in scene files. The resolved target MAY be cached internally and the cache MUST be invalidated when the bearing node is re-attached to the tree.

#### Scenario: NodeRef walks up and down

- **GIVEN** a tree where `Scene` has children `A` and `B`, and `A` has child `C`
- **WHEN** code calls `NodeRef<Node>(path = "../B").resolve(from = C)`
- **THEN** the result is the node `B`

#### Scenario: NodeRef returns null when target type does not match

- **GIVEN** a tree where node `Foo : Node2D` exists and a `NodeRef<Ball>(path = "Foo")` is held on the scene root
- **WHEN** code calls `ref.resolve(from = sceneRoot)`
- **AND** `Foo` is not a `Ball`
- **THEN** the result is `null`

#### Scenario: NodeRef returns null when path is invalid

- **GIVEN** a tree where node `A` has no child named `Missing`
- **WHEN** code calls `NodeRef<Node>(path = "Missing").resolve(from = A)`
- **THEN** the result is `null`

#### Scenario: Empty path resolves to the bearer

- **WHEN** code calls `NodeRef<Node>(path = "").resolve(from = nodeA)`
- **THEN** the result is `nodeA` itself

#### Scenario: NodeRef is serializable

- **WHEN** code holds a `NodeRef<Node2D>` instance and serializes it with `kotlinx.serialization` JSON
- **THEN** the resulting JSON contains the path string
- **AND** deserializing it yields a new `NodeRef<Node2D>` with the same path
- **AND** the deserialized `NodeRef` resolves correctly once placed in a live tree

#### Scenario: Resolution cache invalidates on re-attach

- **GIVEN** a `Paddle` holding `NodeRef<Node2D>(path = "../Ball")` and the paddle is part of a live scene
- **WHEN** the paddle is removed from the scene and then re-added
- **THEN** the next call to `ref.resolve(from = paddle)` recomputes the resolution rather than returning a stale cached value

### Requirement: Signal primitive for inter-node communication

The engine SHALL provide `Signal<T>` (in `com.neoutils.engine.serialization` for source-compatibility with existing imports, but conceptually an event hub used throughout `:engine`) as a runtime event hub with `connect(handler) -> Disposable`, `disconnect(disposable)`, and `emit(value)` operations. `Signal` instances appearing on `@Serializable Node` subclasses MUST be annotated with `@Transient` because their handlers are runtime-only and cannot be serialized; the static configuration that a future editor would want (e.g. "this signal is wired to that handler in another node") MUST live in a separate serializable structure (out of scope for this change). The previous `Signal` API that conflated wiring (`var path: NodeRef`) with the signal contract SHALL be removed; routing in the editor era will use `NodeRef` + `Signal` composition, not a hybrid type.

#### Scenario: Signal has connect/emit/disconnect runtime API

- **WHEN** code instantiates `Signal<String>()` and calls `signal.connect { ... }`
- **THEN** the call returns a `Disposable`
- **AND** subsequent `signal.emit("hello")` invokes the registered handler with `"hello"`

#### Scenario: Signal field on a Serializable Node is Transient

- **WHEN** any class in `:engine` extending `Node` and annotated `@Serializable` declares a `Signal` field
- **THEN** that field is annotated `@Transient`
- **AND** the field is not present in the JSON produced by `SceneLoader.save`

#### Scenario: Old Signal-with-NodeRef shape is removed

- **WHEN** the source of `Signal.kt` (or its replacement) is inspected
- **THEN** there is no `var path: NodeRef` property on `Signal`
- **AND** there is no constructor of `Signal` accepting a `NodeRef`

### Requirement: @Inspect and @Transient discipline on Node subclasses

Every `var` on a `@Serializable Node` subclass shipped by `:engine` SHALL be annotated either with `@Inspect` (configuration that appears in `scene.json` and will be editable in the future visual editor) or with `@Transient` (runtime state that MUST NOT be persisted). This rule applies recursively to subclasses but does NOT apply to game-side code (games are encouraged to follow it but no engine machinery enforces). `Signal` properties, callback fields, current-tick caches, and similar runtime-only state MUST be `@Transient`.

#### Scenario: Every engine var is annotated

- **WHEN** any class in `:engine` extending `Node` and annotated `@Serializable` is inspected
- **THEN** every `var` property is annotated either with `@Inspect` (`com.neoutils.engine.serialization.Inspect`) or with `@Transient` (`kotlinx.serialization.Transient`)

#### Scenario: Signal fields are Transient

- **WHEN** any engine-shipped Node has a `Signal<*>` field
- **THEN** the field is `@Transient`
- **AND** it does not appear in serialized JSON

### Requirement: @Inspect marks properties as inspectable and serialized

The engine SHALL define an annotation `@Inspect` with `@Target(AnnotationTarget.PROPERTY)` and `@Retention(AnnotationRetention.RUNTIME)`. The annotation MAY carry an optional `displayName: String` (default empty). The annotation's semantic contract is: a property annotated with `@Inspect` is part of the node's serialized contract and is intended to be shown by a future editor; a property NOT annotated with `@Inspect` SHALL be explicitly annotated with `@Transient` so that it is excluded from the serialized form. This contract MUST be documented in `CLAUDE.md` so that contributors uphold it.

#### Scenario: @Inspect is present on every editor-facing property of a serializable node

- **WHEN** any class annotated with `@Serializable` that extends `Node` is inspected for `var` properties intended as initial configuration
- **THEN** each such property is annotated with `@Inspect`

#### Scenario: @Transient is present on every runtime-only property of a serializable node

- **WHEN** any class annotated with `@Serializable` that extends `Node` is inspected for `var` properties that hold transient runtime state
- **THEN** each such property is annotated with `@Transient`

#### Scenario: @Inspect carries an optional display name

- **WHEN** code reads `@Inspect(displayName = "Move speed")` on a property
- **THEN** the annotation's `displayName` value is `"Move speed"`

### Requirement: NodeRegistry maps type names to factories

A engine SHALL prover um `NodeRegistry` que mantém um mapeamento **bidirecional** entre um identificador `String` e o par `(KClass<out Node>, factory: () -> Node)`. O identificador é o que aparece no campo `type` do JSON: para tipos compilados em Kotlin, é o FQN; para scripts, é o path do script relativo ao bundle (ex.: `scripts/paddle.nengine.kts`). A API MUST incluir, no mínimo:

- `register(identifier: String, klass: KClass<out Node>, factory: () -> Node)` — registro explícito do mapeamento bidirecional.
- `create(identifier: String): Node` — invoca a factory; lança `UnknownNodeTypeException` se `identifier` não está registrado.
- `identifierFor(klass: KClass<out Node>): String?` — devolve o identificador associado à classe, ou `null` se a classe nunca foi registrada.
- `registerEngineTypes()` — idempotente; registra todos os tipos `Node` concretos publicados por `:engine` usando seus FQN.
- `clear()` — descarta todos os registros (apenas para uso em testes).

Tipos com identificador terminando em `.kts` MUST ser tratados pelo registry como qualquer outro tipo — não há mais ramo especial. O `BundleLoader` (em `:engine-bundle`) MUST popular o registry com mapeamentos `script-path → (class, factory)` antes de chamar `SceneLoader.load`.

#### Scenario: Registered type is instantiable by name

- **GIVEN** código chamou `NodeRegistry.register("com.foo.Paddle", Paddle::class) { Paddle() }`
- **WHEN** o loader encontra uma entrada com `type = "com.foo.Paddle"`
- **THEN** o registry devolve uma instância fresh de `Paddle` via a factory

#### Scenario: Unknown type fails loud

- **GIVEN** nenhum registro feito para `com.example.Mystery`
- **WHEN** o loader encontra uma entrada com `type = "com.example.Mystery"`
- **THEN** o loader lança `UnknownNodeTypeException` cuja mensagem nomeia `com.example.Mystery`

#### Scenario: Script path is a first-class identifier

- **GIVEN** o `BundleLoader` registrou `NodeRegistry.register("scripts/paddle.nengine.kts", PaddleScriptClass::class) { ... }`
- **WHEN** o loader encontra uma entrada com `type = "scripts/paddle.nengine.kts"`
- **THEN** o registry devolve uma instância fresh via a factory
- **AND** o `SceneLoader` não consulta nenhuma SPI externa (nenhum `ScriptHosts`)

#### Scenario: identifierFor recovers the identifier from a KClass

- **GIVEN** `NodeRegistry.register("scripts/paddle.nengine.kts", PaddleScriptClass::class) { ... }`
- **WHEN** código chama `NodeRegistry.identifierFor(PaddleScriptClass::class)`
- **THEN** o resultado é `"scripts/paddle.nengine.kts"`

#### Scenario: identifierFor returns null for unregistered classes

- **WHEN** código chama `NodeRegistry.identifierFor(Node2D::class)` e `Node2D::class` nunca foi registrado
- **THEN** o resultado é `null`

#### Scenario: registerEngineTypes is idempotent

- **WHEN** código chama `NodeRegistry.registerEngineTypes()` duas vezes seguidas
- **THEN** a segunda chamada não lança exceção
- **AND** o estado do registry é equivalente ao de uma única chamada

### Requirement: SceneLoader round-trips a scene to JSON

A engine SHALL prover um `SceneLoader` com duas operações: `save(scene: Scene): String` devolve a representação JSON da cena; `load(json: String): Scene` parseia JSON e devolve uma `Scene` destacada cujo árvore espelha o arquivo. O documento JSON MUST seguir esta forma:

```json
{
  "version": 2,
  "root": {
    "type": "<identificador registrado no NodeRegistry>",
    "name": "<string>",
    "script": "<bundle-relative path>",
    "properties": { "<inspect-or-export-name>": <value>, ... },
    "children": [ <node entry>, ... ]
  }
}
```

O campo `type` MUST ser **um identificador registrado em `NodeRegistry`** — seja um FQN (para tipos compilados) ou um path de script relativo ao bundle (para tipos de script). O `SceneLoader` MUST resolver o tipo exclusivamente por `NodeRegistry.create(type)`; ele MUST NOT discriminar `.kts` nem consultar qualquer SPI externa. Se o tipo não está registrado, o loader MUST lançar `UnknownNodeTypeException`.

A factory invocada produz a instância do nó, depois `name` é aplicado. Em seguida, se `script` é não-nulo, o callback `attachScript(node, scriptPath)` (fornecido pelo caller, tipicamente `BundleLoader`) MUST ser chamado e o `ScriptAttachment` resultante usado para roteamento; senão, considera-se um conjunto vazio de exports. Por fim, o `properties` bag é roteado: para cada chave, o loader decide se aplica via `@Inspect` setter do Node ou via `ScriptAttachment.applyExport`. O array `children` MUST preservar a ordem de `parent.children`. Carregar MUST instanciar cada nó, aplicar `name`, anexar o script (se houver), rotear `properties`, e em seguida anexar seus filhos em ordem via `addChild`. Carregar MUST NOT chamar `Scene.start()`.

O campo `version` MUST ser exatamente `2`. Carregar um documento com `version != 2` MUST lançar exceção cuja mensagem nomeia a versão encontrada, a versão esperada (`2`), e a change que quebrou o formato (`godot-style-properties`). NÃO há leitor legacy para `version: 1`.

Save/load do mesmo cena MUST ser idempotente: `save(load(save(scene)))` SHALL ser equivalente a `save(scene)` após canonicalização (whitespace-insensitive, key-ordered).

Quando `SceneLoader.save` serializa um nó, o campo `type` salvo MUST ser obtido por `NodeRegistry.identifierFor(node::class)`. Se o registry não conhece a classe, `save` MUST cair de volta para `node::class.qualifiedName` como último recurso. `save` MUST NOT consultar nenhuma SPI externa.

Para emitir `properties`, `save` MUST mesclar dois conjuntos:

1. **Propriedades `@Inspect` do Node**: walk de `memberProperties` filtrado por `@Inspect`, valor lido via getter, serializado via `kotlinx.serialization`.
2. **Exports do script anexado** (se `node.scriptInstance != null` e o script é conhecido): para cada `ExportedProperty` declarado, valor lido via `ScriptInstance.currentValue(name)` e serializado usando o `ExportedProperty.type` como serializer hint.

A ordem no JSON emitido MUST ser: primeiro todas as chaves de `@Inspect` (na ordem de declaração da classe), depois todas as chaves de exports (na ordem declarada em `Script.exports`). Não há campo `props` separado.

#### Scenario: save produces well-formed JSON with version 2 and root

- **WHEN** código chama `SceneLoader.save(scene)`
- **THEN** a string devolvida parseia como JSON
- **AND** o objeto top-level tem campos `version` (inteiro **2**) e `root` (objeto)
- **AND** `root` tem campos `type`, `name`, `properties`, `children`
- **AND** `root` NÃO tem campo `props`

#### Scenario: load rejects version 1 with explicit message

- **GIVEN** um documento JSON com `"version": 1`
- **WHEN** código chama `SceneLoader.load(json)`
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia a versão encontrada (`1`) e a esperada (`2`)
- **AND** a mensagem nomeia a change `godot-style-properties` como origem da quebra

#### Scenario: load produces a detached scene

- **WHEN** código chama `SceneLoader.load(json)`
- **THEN** a `Scene` devolvida tem `isLive == false`
- **AND** a cena não está registrada em nenhum `GameLoop`

#### Scenario: load preserves tree shape and inspect properties

- **GIVEN** um documento JSON `version: 2` descrevendo uma cena com três filhos em ordem específica, cada com propriedades `@Inspect` em `properties`
- **WHEN** código chama `SceneLoader.load(json)` seguido de `Scene.start()`
- **THEN** os filhos aparecem na mesma ordem
- **AND** cada filho tem suas propriedades `@Inspect` com os valores do JSON

#### Scenario: Round-trip is stable

- **GIVEN** uma cena `scene`
- **WHEN** código computa `json1 = SceneLoader.save(scene)` então `scene2 = SceneLoader.load(json1)` então `json2 = SceneLoader.save(scene2)`
- **THEN** `json1` e `json2` são documentos JSON equivalentes
- **AND** os dois têm `"version": 2`
- **AND** nenhum tem campo `props`

#### Scenario: Loading does not invoke onEnter until start

- **GIVEN** um tipo de nó cujo `onEnter` incrementa um contador
- **WHEN** código chama `SceneLoader.load(json)` em uma cena contendo esse nó
- **THEN** o contador NÃO foi incrementado
- **AND** após chamada subsequente de `scene.start()`, o contador foi incrementado exatamente uma vez

#### Scenario: SceneLoader does not discriminate .kts identifiers

- **WHEN** o source de `SceneLoader.load` e `SceneLoader.save` é inspecionado
- **THEN** não há checagem `endsWith(".kts")`
- **AND** não há import de nenhuma SPI de scripting (`ScriptHost`, `ScriptHosts`)

#### Scenario: Script-typed entry resolves via NodeRegistry

- **GIVEN** o `NodeRegistry` foi previamente populado com `register("scripts/paddle.nengine.kts", ...)` pelo `BundleLoader`
- **WHEN** código chama `SceneLoader.load(json)` para JSON que contém esse identificador
- **THEN** o nó é instanciado via a factory registrada
- **AND** o caminho de código é o mesmo de qualquer outro identificador

#### Scenario: Unknown type fails fast regardless of suffix

- **GIVEN** um JSON com `type = "scripts/foo.nengine.kts"` e nenhum registro prévio para esse identificador
- **WHEN** código chama `SceneLoader.load(json)`
- **THEN** o loader lança `UnknownNodeTypeException` cuja mensagem nomeia o identificador

#### Scenario: save round-trips script-typed nodes by script path via identifierFor

- **GIVEN** uma cena live cuja raiz é um nó cuja `KClass` foi registrada com identificador `"scripts/pong.nengine.kts"`
- **WHEN** código chama `SceneLoader.save(scene)`
- **THEN** o JSON devolvido tem `type = "scripts/pong.nengine.kts"` na raiz
- **AND** NÃO o FQN runtime da classe gerada pelo script

#### Scenario: save emits script exports inside properties

- **GIVEN** um nó live com `@Inspect var size: Vec2` (valor `Vec2(16, 16)`) e um script anexado declarando `ballSize: float = 16.0` e `initialSpeed: float = 280.0`
- **WHEN** código chama `SceneLoader.save(scene)`
- **THEN** o `properties` desse nó contém entradas para `size`, `ballSize` e `initialSpeed`
- **AND** os valores correspondem aos atuais lidos via getter `@Inspect` e via `ScriptInstance.currentValue`
- **AND** não existe campo `props` separado

### Requirement: properties bag is routed between @Inspect and script exports

O `SceneLoader` MUST rotear cada chave em `NodeEntry.properties` para um único destino:

- Se a chave corresponde a uma propriedade `@Inspect` do Node (case-sensitive, match exato de nome) **e** não corresponde a nenhum export do script anexado: aplica via setter `@Inspect`.
- Se a chave corresponde a um export declarado do script anexado **e** não corresponde a nenhuma propriedade `@Inspect` do Node: aplica via `ScriptAttachment.applyExport(name, jsonElement)`.
- Se a chave corresponde a ambos (`@Inspect` do Node E export do script): MUST lançar exceção fatal de colisão.
- Se a chave não corresponde a nenhum dos dois (incluindo quando não há script): MUST lançar exceção fatal de chave desconhecida.

Mensagens de erro MUST incluir: o caminho hierárquico do nó (`/SceneRoot/.../NodeName`), o nome da chave, e a lista de candidatos consultados (nomes `@Inspect` do tipo Node e, se houver script, nomes de exports do script com o path do script).

#### Scenario: Key matching only @Inspect is applied to Node

- **GIVEN** Node `Ball` é `BoxCollider` com `@Inspect var size: Vec2`, script anexado declara exports `ballSize` e `initialSpeed` (nenhum se chama `size`)
- **AND** `properties.size = { "x": 16, "y": 16 }`
- **WHEN** o loader processa esse nó
- **THEN** o setter `BoxCollider.size` é chamado com `Vec2(16, 16)`
- **AND** `ScriptInstance.setExport` NÃO é chamado para `size`

#### Scenario: Key matching only script export is applied to script

- **GIVEN** Node `Ball` é `BoxCollider` (sem `@Inspect var ballSize`), script anexado declara export `ballSize: float`
- **AND** `properties.ballSize = 16.0`
- **WHEN** o loader processa esse nó
- **THEN** `ScriptInstance.setExport("ballSize", 16f)` é chamado (após coerção)
- **AND** nenhum setter Kotlin é chamado para `ballSize`

#### Scenario: Collision between @Inspect and export is fatal

- **GIVEN** Node tem `@Inspect var size: Vec2` e o script anexado também declara export `size: Vec2`
- **AND** `properties.size = { "x": 16, "y": 16 }`
- **WHEN** o loader processa esse nó
- **THEN** uma exceção é lançada antes de qualquer setter ser invocado
- **AND** a mensagem nomeia a chave `size`, o caminho do nó, o tipo do Node, e o path do script

#### Scenario: Unknown key with script is fatal

- **GIVEN** Node `Ball` com script anexado, `@Inspect` names = `[transform, size]`, exports = `[ballSize, initialSpeed]`
- **AND** `properties.ballSizr = 16.0` (typo)
- **WHEN** o loader processa esse nó
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia `ballSizr`, o caminho do nó, e lista os candidatos `[transform, size]` (do Node) e `[ballSize, initialSpeed]` (do script com path)

#### Scenario: Unknown key without script is fatal

- **GIVEN** Node `topWall` sem script, `@Inspect` names = `[transform, size]`
- **AND** `properties.foo = 1`
- **WHEN** o loader processa esse nó
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia `foo`, o caminho do nó, lista os candidatos `[transform, size]` (do Node), e indica que não há script anexado

### Requirement: ScriptAttachment SPI bridges loader and script host

`SceneLoader.load` MUST aceitar um callback `attachScript: (node: Node, scriptPath: String) -> ScriptAttachment?` (opcional; default `null` significa "não atacha nenhum script"). Quando o callback é `null` e `entry.script != null`, o loader MUST lançar exceção indicando que nenhum host foi fornecido para um nó com script.

`ScriptAttachment` MUST ser uma estrutura pública em `:engine` com:

```kotlin
data class ScriptAttachment(
    val instance: ScriptInstanceContract,
    val exportNames: Set<String>,
    val applyExport: (name: String, value: JsonElement) -> Unit,
)
```

Após `attachScript` retornar, o loader MUST armazenar `instance` em `node.scriptInstance` e usar `exportNames` para roteamento; `applyExport` MUST ser chamado para cada chave roteada como export, recebendo o `JsonElement` raw do `properties` bag.

#### Scenario: attachScript callback is null and entry has no script

- **GIVEN** um `NodeEntry` sem `script`
- **WHEN** `SceneLoader.load(text, attachScript = null)` é chamado
- **THEN** o nó é instanciado normalmente
- **AND** `node.scriptInstance` é null
- **AND** nenhuma exceção é lançada

#### Scenario: attachScript callback is null but entry has script

- **GIVEN** um `NodeEntry` com `script = "scripts/foo.py"`
- **WHEN** `SceneLoader.load(text, attachScript = null)` é chamado
- **THEN** uma exceção é lançada indicando que nenhum host foi fornecido para o nó com script

#### Scenario: applyExport is invoked for each export-routed key

- **GIVEN** um `NodeEntry` com `script = "scripts/paddle.py"` e `properties = { "transform": ..., "speed": 360.0 }` onde `transform` é `@Inspect` e `speed` é export
- **AND** o callback `attachScript` devolve `ScriptAttachment(instance, exportNames = ["speed"], applyExport = capture)`
- **WHEN** `SceneLoader.load(text, attachScript)` é chamado
- **THEN** `capture` foi invocado exatamente uma vez com `("speed", JsonPrimitive(360.0))`
- **AND** o setter `@Inspect transform` foi invocado com o valor desserializado

