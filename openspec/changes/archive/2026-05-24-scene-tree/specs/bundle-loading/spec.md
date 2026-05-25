## MODIFIED Requirements

### Requirement: BundleLoader provides fromResources and fromPath

O módulo `:engine-bundle` SHALL expor um objeto `BundleLoader` com a seguinte API pública:

```kotlin
object BundleLoader {
    fun fromResources(
        name: String,
        types: List<KClass<out Node>> = emptyList(),
        scripting: ScriptHost? = null,
    ): Node

    fun fromPath(
        bundleDir: File,
        types: List<KClass<out Node>> = emptyList(),
        scripting: ScriptHost? = null,
    ): Node
}
```

`fromResources(name)` MUST resolver o bundle como um diretório lógico relativo à raiz do classpath JVM (ex.: `fromResources("pong")` carrega `pong/scene.json` via `ClassLoader.getResource`). `fromPath(bundleDir)` MUST resolver via filesystem. Ambas as funções MUST retornar o nó raiz destacado (mesmo contrato de `SceneLoader.load`: `isLive == false`, tipo do retorno `Node`), permitindo que o caller envolva o resultado em uma `SceneTree(root = result)` antes de entregar ao host. O argumento `types` MUST aceitar tipos `Node` compilados em Kotlin que o jogo precisa expor para o `NodeRegistry` (factory derivada por reflection sobre construtor no-args). O argumento `scripting` MUST ser uma instância de `ScriptHost` quando o bundle referencia ao menos um script via campo `NodeEntry.script`; MAY ser `null` quando o bundle não referencia nenhum script. Internamente, ambas as funções MUST:

1. Ler `scene.json` via a `BundleSource` correspondente (classpath ou filesystem).
2. Coletar o conjunto de paths de script referenciados na árvore (todo `NodeEntry.script` não nulo).
3. Se o conjunto está vazio, prosseguir sem invocar nada relativo a scripting.
4. Se o conjunto não está vazio e `scripting == null`, falhar com mensagem que nomeia ao menos um path encontrado e recomenda passar um `ScriptHost`.
5. Se o conjunto não está vazio e `scripting != null`, validar que cada path termina com `scripting.extension`; falhar com mensagem que nomeia o path e a extensão suportada quando a validação falhar.
6. Para cada path validado, chamar `scripting.load(path, bundle)` para obter o `Map<String, Script>`.
7. Instanciar a árvore via `SceneLoader.load(jsonText, scripts)` (ou equivalente), o qual cria Nodes nativos e atacha `ScriptInstance` aos Nodes cujo `script` foi declarado.

A função MUST NOT consultar nenhum registro global de `ScriptHost`. O caller é responsável por construir e (opcionalmente) reutilizar a instância de `ScriptHost` entre múltiplas chamadas. O caller é também responsável por envolver o `Node` devolvido em uma `SceneTree` antes de entregá-lo ao `GameHost.run(...)`.

#### Scenario: fromResources returns a detached root from classpath bundle

- **GIVEN** o classpath contém `pong/scene.json` na raiz dos recursos
- **AND** o caller construiu um `ScriptHost` compatível com as extensões usadas no bundle
- **WHEN** código chama `BundleLoader.fromResources("pong", scripting = host)`
- **THEN** a função retorna um `Node` cujo `isLive == false`
- **AND** o tipo declarado de retorno é `Node` (não `Scene`)
- **AND** a árvore reflete o conteúdo de `pong/scene.json`

#### Scenario: fromPath returns a detached root from a directory

- **GIVEN** uma pasta `/tmp/foo/` com `scene.json` e `scripts/`
- **AND** o caller construiu um `ScriptHost` compatível
- **WHEN** código chama `BundleLoader.fromPath(File("/tmp/foo"), scripting = host)`
- **THEN** a função retorna um `Node` cujo `isLive == false`
- **AND** a árvore reflete o conteúdo de `/tmp/foo/scene.json`

#### Scenario: Returned root can be wrapped in a SceneTree

- **GIVEN** o resultado `root = BundleLoader.fromResources("pong", scripting = host)`
- **WHEN** código executa `val tree = SceneTree(root = root); tree.start()`
- **THEN** a `SceneTree` aceita o `root` sem erro de tipo
- **AND** o `root.tree` passa a ser a `SceneTree` recém-criada após `start()`
- **AND** `onEnter()` foi disparado em pre-order em todos os nós da árvore

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
