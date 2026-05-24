## MODIFIED Requirements

### Requirement: ScriptHost SPI in :engine-bundle

O módulo `:engine-bundle` SHALL expor uma SPI agnóstica de linguagem para scripting composta dos seguintes tipos públicos no pacote `com.neoutils.engine.bundle.script`:

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

`Script` MUST representar um script carregado e analisado (com `exports` já descobertos estaticamente). `ScriptInstance` MUST representar um script anexado a uma instância de `Node`. `ScriptHost` MUST NOT vazar tipos específicos de runtime (ex.: `org.graalvm.polyglot.Value`, `org.graalvm.polyglot.Context`) através dessas interfaces. A SPI MUST NOT prover nenhum mecanismo de registro global de hosts — instâncias de `ScriptHost` são repassadas explicitamente ao `BundleLoader` pelo caller.

#### Scenario: ScriptHost is the only entry point of the SPI

- **WHEN** o pacote `com.neoutils.engine.bundle.script` é inspecionado
- **THEN** os tipos públicos exportados são exatamente `ScriptHost`, `Script`, `ScriptInstance`, `ExportedProperty` e `BundleSource`
- **AND** nenhum tipo específico de implementação (Python, Lua, etc.) aparece nesse pacote
- **AND** nenhum tipo de registro global (ex.: `ScriptHostRegistry`) é exportado

#### Scenario: SPI types do not leak runtime-specific types

- **WHEN** as assinaturas públicas dos tipos SPI são lidas
- **THEN** nenhuma menciona `org.graalvm.polyglot.*`
- **AND** nenhuma menciona `org.luaj.*` ou qualquer outro runtime de scripting

## REMOVED Requirements

### Requirement: ScriptHostRegistry dispatches by file extension

**Reason**: O singleton mutável `ScriptHostRegistry` é eliminado em favor de injeção explícita: `BundleLoader.fromResources/fromPath` recebem o `ScriptHost` como parâmetro `scripting`. A indireção de despacho por extensão deixa de ter consumidor (com singular não há ambiguidade) e passa a viver como simples checagem inline dentro do `BundleLoader` (`path.endsWith(host.extension)`), sem expor um tipo de registro.

**Migration**: Quem dependia de `ScriptHostRegistry.register(host)` agora deve passar a instância de `ScriptHost` diretamente no argumento `scripting` de `BundleLoader.fromResources` ou `BundleLoader.fromPath`. Quem dependia de `ScriptHostRegistry.hostFor(path)` ou `ScriptHostRegistry.loadAll(paths, bundle)` deve chamar `scripting.load(path, bundle)` diretamente — o `BundleLoader` faz essa orquestração internamente. O tipo `UnsupportedScriptExtensionException` também é removido; o erro equivalente é levantado pelo `BundleLoader` como `IllegalStateException` cuja mensagem nomeia o path e a extensão suportada pelo host injetado.
