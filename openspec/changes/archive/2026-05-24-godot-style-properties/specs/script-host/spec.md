## MODIFIED Requirements

### Requirement: ScriptHost SPI in :engine-bundle

O módulo `:engine-bundle` SHALL expor uma SPI agnóstica de linguagem para scripting composta de quatro tipos públicos no pacote `com.neoutils.engine.bundle.script`:

```kotlin
interface ScriptHost {
    val extension: String
    fun load(path: String, bundle: BundleSource): Script
    fun attach(node: Node, script: Script): ScriptInstance
}

interface Script {
    val path: String
    val extendsType: KClass<out Node>
    val exports: List<ExportedProperty>
}

interface ScriptInstance {
    fun setExport(name: String, value: Any?)
    fun currentValue(name: String): Any?
    fun onEnter()
    fun onUpdate(dt: Float)
    fun onRender(renderer: Renderer)
    fun onCollide(other: Node)
}

data class ExportedProperty(
    val name: String,
    val type: KClass<*>,
    val default: Any?,
)
```

`Script` MUST representar um script carregado e analisado (com `exports` já descobertos estaticamente). `ScriptInstance` MUST representar um script anexado a uma instância de `Node`. `ScriptHost` MUST NOT vazar tipos específicos de runtime (ex.: `org.graalvm.polyglot.Value`, `org.graalvm.polyglot.Context`) através dessas interfaces.

`ScriptInstance.currentValue(name)` MUST devolver o valor atual do export nomeado, conforme presente na instância do script (após qualquer `setExport`, `_ready`, ou mutação durante hooks). Se `name` não corresponde a um `ExportedProperty` declarado em `Script.exports`, a chamada MUST lançar `IllegalArgumentException` nomeando o `name` e o path do script. O valor devolvido MUST estar no tipo Kotlin correspondente a `ExportedProperty.type` (para que `SceneLoader.save` possa serializá-lo via `kotlinx.serialization`). Esse método existe especificamente para suportar round-trip em `SceneLoader.save`.

#### Scenario: ScriptHost is the only entry point of the SPI

- **WHEN** o pacote `com.neoutils.engine.bundle.script` é inspecionado
- **THEN** os tipos públicos exportados são exatamente `ScriptHost`, `Script`, `ScriptInstance`, `ExportedProperty`, `ScriptHostRegistry` e `BundleSource`
- **AND** nenhum tipo específico de implementação (Python, Lua, etc.) aparece nesse pacote

#### Scenario: SPI types do not leak runtime-specific types

- **WHEN** as assinaturas públicas dos quatro tipos SPI são lidas
- **THEN** nenhuma menciona `org.graalvm.polyglot.*`
- **AND** nenhuma menciona `org.luaj.*` ou qualquer outro runtime de scripting

#### Scenario: currentValue returns the default for an untouched export

- **GIVEN** um script com export `speed: float = 360.0` é anexado a um Node sem que `setExport("speed", ...)` seja chamado
- **WHEN** código chama `instance.currentValue("speed")`
- **THEN** o valor devolvido é `360.0f`

#### Scenario: currentValue reflects setExport

- **GIVEN** após anexar, código chama `instance.setExport("speed", 480.0f)`
- **WHEN** código chama `instance.currentValue("speed")`
- **THEN** o valor devolvido é `480.0f`

#### Scenario: currentValue on unknown name fails

- **GIVEN** um script cujo `exports` não inclui `mystery`
- **WHEN** código chama `instance.currentValue("mystery")`
- **THEN** uma `IllegalArgumentException` é lançada
- **AND** a mensagem nomeia `mystery` e o path do script

### Requirement: Script errors propagate fail-fast

Erros em qualquer fase de scripting MUST propagar sem captura silenciosa pela engine. As fases são: carregamento (parsing + análise estática), anexação ao Node, roteamento de `properties` (coerção de tipo em `applyExport`), execução de hooks (`_ready`, `_process`, `_physics_process`, `_draw`, `_on_collide`, `_exit_tree`), e `currentValue` em `save`. A engine MUST NOT envolver essas chamadas em `try/catch` que descarte a exceção; o `GameHost` decide o que fazer (tipicamente: log e crash do processo com stack trace).

#### Scenario: Parse error crashes the load

- **GIVEN** `scripts/broken.py` com sintaxe inválida
- **WHEN** `BundleLoader.fromResources(name)` é chamado
- **THEN** a chamada lança exceção
- **AND** o stack trace contém a linha problemática

#### Scenario: Missing script file fails fast

- **GIVEN** `scene.json` referencia `scripts/missing.py` que não existe no bundle
- **WHEN** `BundleLoader.fromResources(name)` é chamado
- **THEN** a chamada lança exceção
- **AND** a mensagem nomeia `scripts/missing.py`

#### Scenario: Exception in hook crashes the frame

- **GIVEN** `on_update` em um script lança exceção em runtime
- **WHEN** o frame loop executa esse hook
- **THEN** a exceção propaga até o `GameHost`
- **AND** o processo encerra com o stack trace
- **AND** a engine NÃO instala handler que engole a exceção

#### Scenario: Invalid prop type crashes during routing

- **GIVEN** `scene.json` define `properties: { "speed": "fast" }` para um export `speed: Float`
- **WHEN** `BundleLoader` aplica a chave via `applyExport` (que delega para `PropCoercion`)
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o prop `speed`, o tipo esperado (`Float`) e o valor recebido (`"fast"`)
