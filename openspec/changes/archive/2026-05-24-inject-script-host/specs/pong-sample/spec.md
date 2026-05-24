## MODIFIED Requirements

### Requirement: Pong is an executable standalone module

O projeto SHALL prover um módulo `:games:pong` que depende de `:engine`, `:engine-skiko`, `:engine-bundle` e `:engine-bundle-python`, e contém um entry point `main()` que abre uma janela hospedando Pong via `SkikoHost`. O módulo MUST ser executável via `./gradlew :games:pong:run`. O módulo MUST NOT depender de nenhum outro módulo de jogo. O `Main.kt` SHALL construir uma única instância de `PythonScriptHost` via `PythonScriptHost.create()` e injetá-la no `BundleLoader` via o parâmetro `scripting`. O `Main.kt` SHALL carregar a cena via `BundleLoader.fromResources("pong", scripting = python)` por padrão e MAY aceitar um path opcional via argumento de programa para carregar via `BundleLoader.fromPath(File(args[0]), scripting = python)` (cenário de editor / verificação de disco). O `Main.kt` MUST NOT registrar tipos da engine no `NodeRegistry` manualmente nem declarar manifesto de scripts; a única dependência explícita relativa a scripting é a construção do `PythonScriptHost`.

#### Scenario: Pong runs from Gradle

- **WHEN** um desenvolvedor executa `./gradlew :games:pong:run` da raiz do projeto
- **THEN** uma janela desktop abre exibindo a cena Pong
- **AND** o jogo é responsivo a input de teclado

#### Scenario: Pong loads from a filesystem bundle when a path argument is provided

- **GIVEN** uma pasta `<dir>` que é um bundle Pong válido (`scene.json` + `scripts/`)
- **WHEN** um desenvolvedor executa `./gradlew :games:pong:run --args="<dir>"`
- **THEN** o `Main.kt` resolve o bundle via `BundleLoader.fromPath(File(<dir>), scripting = python)`
- **AND** o jogo abre com a mesma cena que `fromResources("pong", scripting = python)` produziria sobre o mesmo conteúdo

#### Scenario: Pong uses only public engine API

- **WHEN** o source de `:games:pong` é inspecionado
- **THEN** todas as interações com engine passam por tipos exportados por `:engine`, `:engine-skiko`, `:engine-bundle` e `:engine-bundle-python`
- **AND** nenhuma API interna/privada desses módulos é referenciada

#### Scenario: Pong depends on engine-bundle and engine-bundle-python

- **WHEN** o build configuration de `:games:pong` é inspecionado
- **THEN** declara dependência em `:engine-bundle`
- **AND** declara dependência em `:engine-bundle-python`
- **AND** NÃO declara dependência em `:engine-scripting` (que não existe)
- **AND** NÃO declara dependência em `kotlin-scripting-*`

#### Scenario: Main.kt is concise

- **WHEN** o source de `:games:pong/src/main/kotlin/.../Main.kt` é inspecionado
- **THEN** o corpo de `main()` se resume a (1) construir `val python = PythonScriptHost.create()`, (2) escolher entre `BundleLoader.fromResources("pong", scripting = python)` e `BundleLoader.fromPath(File(args[0]), scripting = python)` (essa escolha é o único condicional admissível), e (3) uma única chamada a `SkikoHost().run(...)`
- **AND** NÃO contém referência a `PythonScriptHost.install`, `ScriptHostRegistry`, `KotlinScriptingHost`, `ScriptHosts` (formato antigo), `NodeRegistry.registerEngineTypes()`, `classLoader.getResource`, nem manifesto de scripts
