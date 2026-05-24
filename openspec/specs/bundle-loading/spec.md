# bundle-loading Specification

## Purpose

Carregamento de cena via **bundle**: uma pasta autocontida com `scene.json` na raiz e (opcionalmente) `scripts/` com arquivos de script em alguma linguagem suportada (atualmente `.py` via `:engine-bundle-python`). O carregamento é resolvido tanto via classpath JVM (`fromResources`) quanto via filesystem (`fromPath`). `BundleLoader` descobre scripts por tree-walk no `scene.json` e despacha para um `ScriptHost` recebido explicitamente via o parâmetro `scripting` — o módulo `:engine-bundle` é agnóstico de linguagem; implementações concretas vivem em módulos separados (ex.: `:engine-bundle-python`) e o caller injeta a instância no `BundleLoader`.

## Requirements

### Requirement: engine-bundle module hosts bundle loading and the ScriptHost SPI

O projeto SHALL prover um módulo Gradle `:engine-bundle` que depende de `:engine` (e **nada mais** do ecossistema de scripting de runtime). Esse módulo MUST hospedar a API pública `BundleLoader` e a SPI `ScriptHost` / `Script` / `ScriptInstance` / `ExportedProperty` / `BundleSource`. O módulo MUST NOT declarar dependência em `org.jetbrains.kotlin:kotlin-scripting-*` (a infraestrutura Kotlin Scripting some integralmente). O módulo MUST NOT declarar dependência em GraalPy nem em qualquer outro runtime de scripting concreto. Apenas jogos que carregam cena via bundle dependem dele. O módulo MUST NOT ser dependência de `:engine`, `:engine-skiko`, ou `:engine-compose`.

#### Scenario: engine-bundle has minimal dependencies

- **WHEN** a build configuration de `:engine-bundle` é inspecionada
- **THEN** declara dependência em `:engine`
- **AND** NÃO declara nenhuma dependência `org.jetbrains.kotlin:kotlin-scripting-*`
- **AND** NÃO declara nenhuma dependência `org.graalvm.polyglot:*`
- **AND** NÃO declara nenhuma outra dependência de módulo do projeto

#### Scenario: engine modules do not depend on engine-bundle

- **WHEN** a configuração de build de `:engine`, `:engine-skiko` e `:engine-compose` é inspecionada
- **THEN** nenhum deles declara `:engine-bundle` como dependência, direta ou transitiva

#### Scenario: Games without bundles do not pull engine-bundle transitively

- **WHEN** o runtime classpath de `:games:tictactoe` ou `:games:demos` é resolvido
- **THEN** nenhum artefato de `:engine-bundle` está presente

### Requirement: Scene bundle layout convention

Um **scene bundle** SHALL ser uma pasta autocontida com a forma:

```
<bundle>/
  scene.json          (obrigatório, raiz)
  scripts/            (opcional)
    *.py              (Python via :engine-bundle-python; outras extensões via outros hosts)
  assets/             (reservado; ignorado pela engine nesta change)
```

`scene.json` MUST estar diretamente sob a raiz do bundle e MUST ser o JSON serializado por `SceneLoader.save` (formato `SceneFile`). Os paths internos em `scene.json` para scripts SHALL ser **relativos ao bundle** (ex.: `"script": "scripts/paddle.py"`). A pasta `scripts/` MAY conter arquivos de várias extensões, desde que o `ScriptHost` passado ao `BundleLoader` via parâmetro `scripting` suporte a extensão usada por cada `script` referenciado em `scene.json`. A pasta `assets/` MAY estar ausente; quando presente, esta change não exige que a engine a leia.

#### Scenario: Bundle root has scene.json

- **WHEN** uma pasta é tratada como bundle
- **AND** não há `scene.json` na raiz
- **THEN** o `BundleLoader` lança exceção cuja mensagem nomeia o bundle e indica que `scene.json` está faltando

#### Scenario: Script paths in scene.json are bundle-relative

- **GIVEN** um bundle `pong/` com `scripts/paddle.py`
- **WHEN** `scene.json` referencia esse script via campo `script`
- **THEN** o valor é a string exata `scripts/paddle.py`
- **AND** NÃO é uma string que inclui o nome do bundle (ex.: `pong/scripts/paddle.py`)

#### Scenario: assets/ directory is reserved and ignored

- **GIVEN** um bundle com `assets/` populada
- **WHEN** `BundleLoader.fromResources` ou `fromPath` é chamado para esse bundle
- **THEN** o carregamento ocorre normalmente
- **AND** nenhum erro é lançado pela presença de `assets/`

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

### Requirement: NodeEntry supports script field with unified properties routing

O formato `scene.json` SHALL aceitar um campo opcional `script: String?` em cada `NodeEntry`, ao lado dos campos existentes (`type`, `name`, `properties`, `children`). NÃO há campo `props` separado.

Quando `script` é não-nulo, o `BundleLoader` MUST:

1. Instanciar o Node nativo via `NodeRegistry.create(type)`.
2. Atachar o `ScriptInstance` via `scripting.attach(node, script)` (usando o `ScriptHost` recebido pelo parâmetro `scripting`).
3. Construir um `ScriptAttachment` cuja `exportNames` é o conjunto `Script.exports.map { it.name }.toSet()`, e cujo `applyExport(name, jsonEl)` chama `PropCoercion.coerce(jsonEl, export.type, export.nullable)` seguido de `instance.setExport(name, value)`.
4. Devolver esse `ScriptAttachment` para o `SceneLoader` rotear `properties`.
5. Armazenar `node.scriptInstance = instance`.

Quando `script` é nulo, o Node se comporta como antes — apenas o tipo nativo, sem `ScriptInstance`, e o `SceneLoader` roteia `properties` exclusivamente contra `@Inspect`.

O roteamento de `properties` (decisão `@Inspect` vs export, colisão fatal, chave desconhecida fatal) MUST ser delegado ao `SceneLoader` conforme requirement em `scene-serialization`. O `BundleLoader` não duplica essa lógica.

#### Scenario: Node with script slot is instantiated, attached, and routed

- **GIVEN** `scene.json` contém um nó `{ "type": "engine.Node2D", "script": "scripts/paddle.py", "properties": { "speed": 360.0 } }` onde `speed` é export do `paddle.py` e não há `@Inspect var speed` em `Node2D`
- **AND** o caller passou um `PythonScriptHost` no parâmetro `scripting`
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** um `Node2D` é instanciado
- **AND** `node.scriptInstance` é não-nulo
- **AND** `scriptInstance.setExport("speed", 360.0f)` foi chamado (após coerção via `PropCoercion`)

#### Scenario: Node without script slot has no scriptInstance

- **GIVEN** `scene.json` contém um nó `{ "type": "engine.Node2D", "properties": {} }` (sem `script`)
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** um `Node2D` é instanciado
- **AND** `node.scriptInstance` é nulo

#### Scenario: Unknown key in properties is rejected via SceneLoader

- **GIVEN** `scene.json` contém um nó `{ "type": "engine.Node2D", "properties": { "ballSize": 16.0 } }` (sem `script`)
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** uma exceção é lançada (vinda do `SceneLoader` route step)
- **AND** a mensagem indica `ballSize` como chave desconhecida no `Node2D`

#### Scenario: Prop type mismatch fails fast during routing

- **GIVEN** um script declara `speed: float = 360.0` e `scene.json` traz `"properties": { "speed": "fast" }`
- **WHEN** `BundleLoader` aplica a chave (via `applyExport` → `PropCoercion`)
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia `speed`, o tipo esperado e o valor recebido
