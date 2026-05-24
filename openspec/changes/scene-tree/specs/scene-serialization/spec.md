## MODIFIED Requirements

### Requirement: NodeRegistry maps type names to factories

A engine SHALL prover um `NodeRegistry` que mantém um mapeamento **bidirecional** entre um identificador `String` e o par `(KClass<out Node>, factory: () -> Node)`. O identificador é o que aparece no campo `type` do JSON: para tipos compilados em Kotlin, é o FQN; para scripts, é o path do script relativo ao bundle (ex.: `scripts/paddle.nengine.kts`). A API MUST incluir, no mínimo:

- `register(identifier: String, klass: KClass<out Node>, factory: () -> Node)` — registro explícito do mapeamento bidirecional.
- `create(identifier: String): Node` — invoca a factory; lança `UnknownNodeTypeException` se `identifier` não está registrado.
- `identifierFor(klass: KClass<out Node>): String?` — devolve o identificador associado à classe, ou `null` se a classe nunca foi registrada.
- `registerEngineTypes()` — idempotente; registra todos os tipos `Node` concretos publicados por `:engine` usando seus FQN.
- `clear()` — descarta todos os registros (apenas para uso em testes).

Após esta change, `registerEngineTypes()` MUST registrar exatamente o seguinte conjunto de tipos: `Node`, `Node2D`, `Camera2D`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `Label`, `BoxCollider`. O identificador `com.neoutils.engine.scene.Scene` MUST NOT ser registrado por `registerEngineTypes()`, porque a classe `Scene` não existe mais em `:engine`. Tentativas de instanciar `com.neoutils.engine.scene.Scene` via `NodeRegistry.create(...)` MUST falhar com a mensagem padrão de tipo não registrado.

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

#### Scenario: registerEngineTypes does not register Scene

- **GIVEN** `NodeRegistry.clear()` foi chamado e depois `NodeRegistry.registerEngineTypes()`
- **WHEN** código chama `NodeRegistry.create("com.neoutils.engine.scene.Scene")`
- **THEN** uma exceção `UnknownNodeTypeException` é lançada cuja mensagem nomeia `com.neoutils.engine.scene.Scene`
- **AND** a mensagem indica que o tipo não está registrado (não há tratamento especial)

### Requirement: SceneLoader round-trips a scene to JSON

A engine SHALL prover um `SceneLoader` com duas operações: `save(root: Node): String` devolve a representação JSON da árvore enraizada em `root`; `load(json: String): Node` parseia JSON e devolve o nó raiz destacado cuja sub-árvore espelha o arquivo. O documento JSON MUST seguir esta forma:

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

O campo `type` do `root` MUST ser **um identificador registrado em `NodeRegistry`** — qualquer subclasse concreta de `Node` registrada. O `SceneLoader` MUST resolver o tipo exclusivamente por `NodeRegistry.create(type)`; ele MUST NOT discriminar `.kts` nem consultar qualquer SPI externa; ele MUST NOT exigir que o root seja de um tipo específico (não há cast `as? Scene` ou equivalente). Se o tipo não está registrado, o loader MUST lançar `UnknownNodeTypeException`.

A factory invocada produz a instância do nó, depois `name` é aplicado. Em seguida, se `script` é não-nulo, o callback `attachScript(node, scriptPath)` (fornecido pelo caller, tipicamente `BundleLoader`) MUST ser chamado e o `ScriptAttachment` resultante usado para roteamento; senão, considera-se um conjunto vazio de exports. Por fim, o `properties` bag é roteado: para cada chave, o loader decide se aplica via `@Inspect` setter do Node ou via `ScriptAttachment.applyExport`. O array `children` MUST preservar a ordem de `parent.children`. Carregar MUST instanciar cada nó, aplicar `name`, anexar o script (se houver), rotear `properties`, e em seguida anexar seus filhos em ordem via `addChild`. Carregar MUST NOT chamar `SceneTree.start()` nem envolver o root em uma `SceneTree`; o caller decide quando criar a `SceneTree` e tornar a árvore viva.

O campo `version` MUST ser exatamente `2`. Carregar um documento com `version != 2` MUST lançar exceção cuja mensagem nomeia a versão encontrada, a versão esperada (`2`), e a change que quebrou o formato (`godot-style-properties`). NÃO há leitor legacy para `version: 1`.

Save/load do mesmo root MUST ser idempotente: `save(load(save(root)))` SHALL ser equivalente a `save(root)` após canonicalização (whitespace-insensitive, key-ordered).

Quando `SceneLoader.save` serializa um nó, o campo `type` salvo MUST ser obtido por `NodeRegistry.identifierFor(node::class)`. Se o registry não conhece a classe, `save` MUST cair de volta para `node::class.qualifiedName` como último recurso. `save` MUST NOT consultar nenhuma SPI externa.

Para emitir `properties`, `save` MUST mesclar dois conjuntos:

1. **Propriedades `@Inspect` do Node**: walk de `memberProperties` filtrado por `@Inspect`, valor lido via getter, serializado via `kotlinx.serialization`.
2. **Exports do script anexado** (se `node.scriptInstance != null` e o script é conhecido): para cada `ExportedProperty` declarado, valor lido via `ScriptInstance.currentValue(name)` e serializado usando o `ExportedProperty.type` como serializer hint.

A ordem no JSON emitido MUST ser: primeiro todas as chaves de `@Inspect` (na ordem de declaração da classe), depois todas as chaves de exports (na ordem declarada em `Script.exports`). Não há campo `props` separado.

#### Scenario: save produces well-formed JSON with version 2 and root

- **WHEN** código chama `SceneLoader.save(root)` para qualquer root Node
- **THEN** a string devolvida parseia como JSON
- **AND** o objeto top-level tem campos `version` (inteiro **2**) e `root` (objeto)
- **AND** `root` tem campos `type`, `name`, `properties`, `children`
- **AND** `root` NÃO tem campo `props`

#### Scenario: load returns the root node, not a Scene

- **WHEN** código chama `SceneLoader.load(json)`
- **THEN** o valor devolvido é declarado com tipo de retorno `Node`
- **AND** nenhum cast `root as? Scene` é executado pelo loader
- **AND** nenhuma exceção "Root node is not a Scene" pode ser produzida pelo loader

#### Scenario: load accepts any concrete Node subtype as root

- **GIVEN** um documento JSON `version: 2` com `root.type = "com.neoutils.engine.scene.Node"`
- **WHEN** código chama `SceneLoader.load(json)`
- **THEN** o resultado é uma instância de `Node` (não uma subclasse) e nenhuma exceção é lançada
- **AND** o mesmo loader aceita `root.type = "com.neoutils.engine.scene.Node2D"`, `"com.neoutils.engine.scene.Camera2D"`, ou qualquer outro tipo registrado

#### Scenario: load rejects version 1 with explicit message

- **GIVEN** um documento JSON com `"version": 1`
- **WHEN** código chama `SceneLoader.load(json)`
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia a versão encontrada (`1`) e a esperada (`2`)
- **AND** a mensagem nomeia a change `godot-style-properties` como origem da quebra

#### Scenario: load returns a detached root

- **WHEN** código chama `SceneLoader.load(json)`
- **THEN** o root devolvido tem `isLive == false`
- **AND** o root NÃO está registrado em nenhuma `SceneTree`
- **AND** nenhum `onEnter` foi disparado em nenhum nó da árvore

#### Scenario: load preserves tree shape and inspect properties

- **GIVEN** um documento JSON `version: 2` descrevendo uma árvore com três filhos em ordem específica, cada com propriedades `@Inspect` em `properties`
- **WHEN** código chama `SceneLoader.load(json)` seguido de `SceneTree(root = result).start()`
- **THEN** os filhos aparecem na mesma ordem
- **AND** cada filho tem suas propriedades `@Inspect` com os valores do JSON

#### Scenario: Round-trip is stable

- **GIVEN** uma árvore `root`
- **WHEN** código computa `json1 = SceneLoader.save(root)` então `root2 = SceneLoader.load(json1)` então `json2 = SceneLoader.save(root2)`
- **THEN** `json1` e `json2` são documentos JSON equivalentes
- **AND** os dois têm `"version": 2`
- **AND** nenhum tem campo `props`

#### Scenario: Loading does not invoke onEnter until SceneTree start

- **GIVEN** um tipo de nó cujo `onEnter` incrementa um contador
- **WHEN** código chama `SceneLoader.load(json)` em uma árvore contendo esse nó
- **THEN** o contador NÃO foi incrementado
- **AND** após chamada subsequente de `SceneTree(root = result).start()`, o contador foi incrementado exatamente uma vez

#### Scenario: SceneLoader does not discriminate .kts identifiers

- **WHEN** o source de `SceneLoader.load` e `SceneLoader.save` é inspecionado
- **THEN** não há checagem `endsWith(".kts")`
- **AND** não há import de nenhuma SPI de scripting (`ScriptHost`, `ScriptHosts`)
- **AND** não há referência ao símbolo `Scene` (a classe foi removida)

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

- **GIVEN** uma árvore viva cuja raiz é um nó cuja `KClass` foi registrada com identificador `"scripts/pong.nengine.kts"`
- **WHEN** código chama `SceneLoader.save(root)`
- **THEN** o JSON devolvido tem `type = "scripts/pong.nengine.kts"` na raiz
- **AND** NÃO o FQN runtime da classe gerada pelo script

#### Scenario: save emits script exports inside properties

- **GIVEN** um nó live com `@Inspect var size: Vec2` (valor `Vec2(16, 16)`) e um script anexado declarando `ballSize: float = 16.0` e `initialSpeed: float = 280.0`
- **WHEN** código chama `SceneLoader.save(root)`
- **THEN** o `properties` desse nó contém entradas para `size`, `ballSize` e `initialSpeed`
- **AND** os valores correspondem aos atuais lidos via getter `@Inspect` e via `ScriptInstance.currentValue`
- **AND** não existe campo `props` separado
