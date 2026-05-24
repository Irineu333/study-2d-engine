# script-host Specification

## Purpose

SPI agnóstica de linguagem para scripting de Nodes, vivendo em `:engine-bundle`. Define os contratos `ScriptHost`, `Script`, `ScriptInstance`, `ExportedProperty`, `ScriptHostRegistry` e `BundleSource` que permitem que diferentes linguagens (Python via `:engine-bundle-python`, futuramente Lua, etc.) sejam plugadas no `BundleLoader` sem vazar tipos específicos de runtime para a engine ou para os jogos. Substitui o capability `scripting` (que era específico a Kotlin Scripting) por uma abstração reutilizável.

## Requirements

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

### Requirement: ScriptHostRegistry dispatches by file extension

O módulo `:engine-bundle` SHALL prover um objeto `ScriptHostRegistry` que mantém um mapa de extensão de arquivo (`.py`, `.lua`, ...) para `ScriptHost` registrado. A API SHALL ser:

```kotlin
object ScriptHostRegistry {
    fun register(host: ScriptHost)
    fun clear()
    fun hostFor(path: String): ScriptHost?
    fun loadAll(paths: Set<String>, bundle: BundleSource): Map<String, Script>
}
```

`hostFor(path)` MUST escolher o host cuja `extension` casa com o sufixo do `path`. `loadAll` MUST chamar `load` no host correspondente para cada path do conjunto, retornando um mapa path → `Script`. Se algum path não tem host correspondente, `loadAll` MUST lançar exceção cuja mensagem nomeia o path e a extensão não suportada.

#### Scenario: Host is dispatched by file extension

- **GIVEN** um `ScriptHost` para `.py` registrado em `ScriptHostRegistry`
- **WHEN** código chama `ScriptHostRegistry.hostFor("scripts/paddle.py")`
- **THEN** retorna o host registrado para `.py`

#### Scenario: Unknown extension fails fast

- **GIVEN** apenas `.py` registrado no `ScriptHostRegistry`
- **WHEN** código chama `ScriptHostRegistry.loadAll(setOf("scripts/paddle.lua"), bundle)`
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia `scripts/paddle.lua` e indica que a extensão `.lua` não tem host registrado

#### Scenario: Multiple hosts coexist

- **GIVEN** dois `ScriptHost` registrados (`.py` e `.lua` hipotético)
- **WHEN** código chama `ScriptHostRegistry.loadAll(setOf("a.py", "b.lua"), bundle)`
- **THEN** cada path é roteado para o host correto
- **AND** o mapa de retorno contém ambas as entradas

### Requirement: Node carries an optional script instance slot

A classe `Node` em `:engine` SHALL ganhar um slot `internal var scriptInstance: ScriptInstance?` (defaultando a `null`). Os hooks de lifecycle do Node (`onEnter`, `onUpdate`, `onRender`, `onCollide`) MUST delegar para o `scriptInstance` quando presente, **após** rodar o comportamento do próprio Node. Quando `scriptInstance == null`, o comportamento é idêntico ao atual.

#### Scenario: Node without script instance behaves like before

- **GIVEN** um `Node2D` sem `scriptInstance` atribuído
- **WHEN** o Node passa por `onUpdate(dt)`
- **THEN** o comportamento é idêntico ao de antes da introdução do slot
- **AND** nenhuma chamada a `ScriptInstance` ocorre

#### Scenario: Node with script instance dispatches hooks

- **GIVEN** um `Node2D` com `scriptInstance` atribuído a uma instância mock
- **WHEN** `onUpdate(dt = 0.016f)` é chamado no Node
- **THEN** `scriptInstance.onUpdate(0.016f)` é chamado exatamente uma vez

#### Scenario: scriptInstance is internal, not part of public API

- **WHEN** a API pública de `Node` é inspecionada
- **THEN** `scriptInstance` aparece com visibilidade `internal`
- **AND** não há getter/setter público para esse campo

### Requirement: Script declares which Node type it extends

Toda implementação de `Script` MUST expor a propriedade `extendsType: KClass<out Node>` apontando para um tipo Node nativo registrado no `NodeRegistry`. O valor MUST ser determinado estaticamente pela implementação (sem rodar o script). Se a declaração `extends` no script aponta para um tipo não registrado, `ScriptHost.load` MUST lançar exceção cuja mensagem nomeia o tipo declarado e o path do script.

#### Scenario: extendsType is resolved against NodeRegistry

- **GIVEN** um script declara `extends Node2D` e o `NodeRegistry` registra `Node2D`
- **WHEN** `ScriptHost.load(path, bundle)` é chamado
- **THEN** o `Script` retornado tem `extendsType == Node2D::class`

#### Scenario: Unknown extendsType fails fast

- **GIVEN** um script declara `extends BananaNode` que não está no `NodeRegistry`
- **WHEN** `ScriptHost.load(path, bundle)` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia `BananaNode` e o path do script

### Requirement: Exports are discovered statically without executing the script

Toda implementação de `ScriptHost.load` MUST descobrir a lista `Script.exports` **sem executar** o código do script (sem efeitos colaterais, sem necessidade de imports do script estarem resolvidos em runtime). Para Python isso significa parsing AST; para outras linguagens, parser equivalente. Padrões dinâmicos (atribuições em loops, `globals()[...]`, etc.) MUST NOT ser detectados — exports são limitados a declarações top-level estaticamente analisáveis.

#### Scenario: Static analysis discovers top-level exports

- **GIVEN** um script Python contendo `speed: float = 360.0` no top-level (sem `def`/`class` aninhando)
- **WHEN** `ScriptHost.load(path, bundle)` é chamado
- **THEN** o `Script.exports` retornado contém uma entrada `(name="speed", type=Float::class, default=360.0)`
- **AND** nenhum código do módulo é executado (efeitos colaterais não disparam)

#### Scenario: Dynamic patterns are intentionally ignored

- **GIVEN** um script Python contendo `for k in ["a"]: globals()[k] = 1`
- **WHEN** `ScriptHost.load(path, bundle)` é chamado
- **THEN** `Script.exports` está vazio (ou não contém entrada `a`)

### Requirement: Exported property types are limited to engine-supported set

As implementações de `ScriptHost` MUST aceitar apenas o seguinte conjunto fechado de tipos como `ExportedProperty.type`:

- Primitivos: `Int`, `Float`, `Boolean`, `String`
- Engine: `Vec2`, `Color`, `Rect`, `NodeRef`
- Enums: `Key` (e quaisquer enums registrados pela engine no futuro)
- Nullable de qualquer um dos acima

Tipos fora dessa lista declarados como anotação top-level MUST ser silenciosamente ignorados (não viram `ExportedProperty`) — o autor pode usá-los como variáveis privadas do script, mas a engine não os expõe ao Inspector nem persiste em `scene.json`.

#### Scenario: Supported type becomes an export

- **GIVEN** `speed: float = 360.0` no top-level
- **WHEN** `load` analisa o script
- **THEN** `exports` contém `(name="speed", type=Float::class, default=360.0)`

#### Scenario: Unsupported type is ignored

- **GIVEN** `cache: dict = {}` no top-level
- **WHEN** `load` analisa o script
- **THEN** `exports` NÃO contém entrada para `cache`
- **AND** o script continua válido (não falha por causa do tipo `dict`)

#### Scenario: Nullable supported type becomes an export

- **GIVEN** `up_key: Optional[Key] = None` no top-level
- **WHEN** `load` analisa o script
- **THEN** `exports` contém `(name="up_key", type=Key::class, default=null)`

### Requirement: Hook contract is fixed (onEnter, onUpdate, onRender, onCollide)

Toda implementação de `ScriptInstance` MUST suportar exatamente os quatro hooks `onEnter`, `onUpdate(dt: Float)`, `onRender(renderer: Renderer)`, `onCollide(other: Node)`. A SPI MUST NOT introspectar nem chamar nenhum outro nome de método do script. Hooks ausentes no script MUST ser tratados como no-op (sem erro).

#### Scenario: Defined hooks are dispatched

- **GIVEN** um script Python que define `on_update(self, dt)` e `on_enter(self)` e não define os outros hooks
- **WHEN** um Node com esse script é atualizado e entra na cena
- **THEN** `on_enter` é chamado uma vez na entrada
- **AND** `on_update` é chamado em cada tick
- **AND** nenhuma exceção é lançada por hooks ausentes

#### Scenario: Hook not in the fixed set is not dispatched

- **GIVEN** um script Python que define `on_late_update(self, dt)` (nome fora da SPI)
- **WHEN** o Node correspondente passa pelo ciclo de update
- **THEN** `on_late_update` NÃO é chamado pela engine

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

### Requirement: BundleSource abstracts script file resolution

A SPI SHALL expor uma interface `BundleSource` que abstrai a leitura de arquivos do bundle. `ScriptHost.load(path, bundle)` consome essa interface para ler o conteúdo do script. A interface MUST suportar pelo menos: leitura de texto (`read(path: String): String`) e existência (`exists(path: String): Boolean`). Implementações concretas (classpath, filesystem) vivem no `:engine-bundle` mas a interface vive ao lado da SPI para que implementações de `ScriptHost` em outros módulos possam consumi-la.

#### Scenario: ScriptHost reads source via BundleSource

- **GIVEN** um `BundleSource` que fornece `scripts/paddle.py` com o source de exemplo
- **WHEN** `ScriptHost.load("scripts/paddle.py", bundle)` é chamado
- **THEN** o host obtém o source via `bundle.read("scripts/paddle.py")`
- **AND** NÃO acessa o filesystem nem o classpath diretamente
