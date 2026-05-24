## MODIFIED Requirements

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

## ADDED Requirements

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
