## MODIFIED Requirements

### Requirement: BundleLoader provides fromResources and fromPath

O módulo `:engine-bundle` SHALL expor um objeto `BundleLoader` com a seguinte API pública:

```kotlin
object BundleLoader {
    fun fromResources(
        name: String,
        types: List<KClass<out Node>> = emptyList(),
        scripting: ScriptHost? = null,
    ): Scene

    fun fromPath(
        bundleDir: File,
        types: List<KClass<out Node>> = emptyList(),
        scripting: ScriptHost? = null,
    ): Scene
}
```

`fromResources(name)` MUST resolver o bundle como um diretório lógico relativo à raiz do classpath JVM (ex.: `fromResources("pong")` carrega `pong/scene.json` via `ClassLoader.getResource`). `fromPath(bundleDir)` MUST resolver via filesystem. Ambas as funções MUST retornar uma `Scene` destacada (mesmo contrato de `SceneLoader.load`: `isLive == false`). O argumento `types` MUST aceitar tipos `Node` compilados em Kotlin que o jogo precisa expor para o `NodeRegistry` (factory derivada por reflection sobre construtor no-args). O argumento `scripting` MUST ser uma instância de `ScriptHost` quando o bundle referencia ao menos um script via campo `NodeEntry.script`; MAY ser `null` quando o bundle não referencia nenhum script. Internamente, ambas as funções MUST:

1. Ler `scene.json` via a `BundleSource` correspondente (classpath ou filesystem).
2. Coletar o conjunto de paths de script referenciados na árvore (todo `NodeEntry.script` não nulo).
3. Se o conjunto está vazio, prosseguir sem invocar nada relativo a scripting.
4. Se o conjunto não está vazio e `scripting == null`, falhar com mensagem que nomeia ao menos um path encontrado e recomenda passar um `ScriptHost`.
5. Se o conjunto não está vazio e `scripting != null`, validar que cada path termina com `scripting.extension`; falhar com mensagem que nomeia o path e a extensão suportada quando a validação falhar.
6. Para cada path validado, chamar `scripting.load(path, bundle)` para obter o `Map<String, Script>`.
7. Instanciar a árvore via `SceneLoader.load(jsonText, scripts)` (ou equivalente), o qual cria Nodes nativos e atacha `ScriptInstance` aos Nodes cujo `script` foi declarado.

A função MUST NOT consultar nenhum registro global de `ScriptHost`. O caller é responsável por construir e (opcionalmente) reutilizar a instância de `ScriptHost` entre múltiplas chamadas.

#### Scenario: fromResources returns a detached scene from classpath bundle

- **GIVEN** o classpath contém `pong/scene.json` na raiz dos recursos
- **AND** o caller construiu um `ScriptHost` compatível com as extensões usadas no bundle
- **WHEN** código chama `BundleLoader.fromResources("pong", scripting = host)`
- **THEN** a função retorna uma `Scene` cuja `isLive == false`
- **AND** a árvore reflete o conteúdo de `pong/scene.json`

#### Scenario: fromPath returns a detached scene from a directory

- **GIVEN** uma pasta `/tmp/foo/` com `scene.json` e `scripts/`
- **AND** o caller construiu um `ScriptHost` compatível
- **WHEN** código chama `BundleLoader.fromPath(File("/tmp/foo"), scripting = host)`
- **THEN** a função retorna uma `Scene` cuja `isLive == false`
- **AND** a árvore reflete o conteúdo de `/tmp/foo/scene.json`

#### Scenario: Script-less bundle loads without a ScriptHost

- **GIVEN** um bundle cujo `scene.json` não tem nenhum campo `script` em nenhum `NodeEntry`
- **WHEN** código chama `BundleLoader.fromResources(name)` (sem passar `scripting`, ou passando `scripting = null`)
- **THEN** o carregamento ocorre normalmente
- **AND** nenhuma exceção é lançada
- **AND** nenhum runtime de scripting é inicializado

#### Scenario: Bundle with scripts and no ScriptHost fails fast

- **GIVEN** um bundle cujo `scene.json` referencia ao menos um `script` (ex.: `scripts/paddle.py`)
- **WHEN** código chama `BundleLoader.fromResources(name)` com `scripting = null` (ou omite o parâmetro)
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia ao menos um path de script encontrado
- **AND** a mensagem recomenda explicitamente passar um `ScriptHost` no argumento `scripting`

#### Scenario: Script extension mismatch fails fast

- **GIVEN** um bundle cujo `scene.json` referencia `scripts/foo.lua`
- **AND** o caller passa um `ScriptHost` cuja `extension == ".py"`
- **WHEN** código chama `BundleLoader.fromResources(name, scripting = host)`
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o path `scripts/foo.lua` e a extensão suportada `.py`

#### Scenario: Custom types parameter registers compiled Node classes

- **GIVEN** uma classe custom `class FooNode : Node2D()` em Kotlin compilado
- **AND** `scene.json` contém uma entrada com `type` igual ao FQN de `FooNode`
- **WHEN** código chama `BundleLoader.fromResources("bundle", types = listOf(FooNode::class))`
- **THEN** o nó correspondente é instanciado como `FooNode`
- **AND** se `types` fosse vazio, a chamada falharia com `UnknownNodeTypeException`

#### Scenario: Engine types are auto-registered idempotently

- **GIVEN** `NodeRegistry.clear()` foi chamado antes
- **WHEN** código chama `BundleLoader.fromResources("bundle")` cujo `scene.json` referencia `com.neoutils.engine.physics.BoxCollider`
- **THEN** o carregamento ocorre sem o chamador ter registrado tipos da engine manualmente
- **AND** múltiplas chamadas consecutivas de `BundleLoader.from*` não duplicam registros nem falham

#### Scenario: Missing bundle fails with clear message

- **WHEN** código chama `BundleLoader.fromResources("inexistente")` e não há `inexistente/scene.json` no classpath
- **THEN** uma exceção é lançada cuja mensagem nomeia o argumento `inexistente`

### Requirement: Scripts are discovered by tree-walk on the scene JSON

O `BundleLoader` MUST descobrir quais scripts carregar percorrendo o JSON parseado: para cada `NodeEntry`, se o campo `script` é não-nulo, esse path é adicionado ao conjunto de scripts a carregar. Scripts presentes na pasta `scripts/` mas NÃO referenciados pela árvore MUST NOT ser carregados. Scripts referenciados MUST ser passados ao `ScriptHost` recebido pelo `BundleLoader` (parâmetro `scripting`); cada chamada `scripting.load(path, bundle)` MUST ocorrer no máximo uma vez por path, ainda que múltiplos Nodes referenciem o mesmo path. Os paths coletados MUST ser usados exatamente como aparecem no JSON (bundle-relative). A heurística baseada na extensão do `type` (`endsWith(".nengine.kts")`) MUST NOT existir mais — o gatilho é sempre o campo `script`, nunca o `type`.

#### Scenario: Only referenced scripts are loaded

- **GIVEN** um bundle com `scripts/used.py` e `scripts/orphan.py`
- **AND** `scene.json` referencia apenas `scripts/used.py` (via campo `script` em algum nó)
- **WHEN** código chama `BundleLoader.fromResources(name, scripting = host)`
- **THEN** `used.py` é carregado pelo `ScriptHost` injetado
- **AND** `orphan.py` NÃO é carregado

#### Scenario: Scripts referenced multiple times load once

- **GIVEN** um `scene.json` em que dois nós distintos têm `script = "scripts/paddle.py"`
- **WHEN** código chama `BundleLoader.fromResources(name, scripting = host)`
- **THEN** o carregamento de `paddle.py` (parse + análise estática) ocorre uma única vez
- **AND** os dois nós recebem `ScriptInstance` distintas do mesmo `Script`

#### Scenario: type field with .py is no longer a script trigger

- **GIVEN** um `scene.json` em que algum nó tem `type = "scripts/something.py"` (uso ilegítimo do campo `type`)
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** o carregamento falha como tipo desconhecido em `NodeRegistry`
- **AND** o caminho que tratava `.nengine.kts` em `type` não existe mais
