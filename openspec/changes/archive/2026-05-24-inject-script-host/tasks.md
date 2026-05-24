## 1. BundleLoader API

- [x] 1.1 Adicionar parâmetro `scripting: ScriptHost? = null` em `BundleLoader.fromResources` e `BundleLoader.fromPath` (em `engine-bundle/src/main/kotlin/com/neoutils/engine/bundle/BundleLoader.kt`)
- [x] 1.2 Extrair helper privado `loadScripts(scriptPaths, host, bundle): Map<String, Script>` que (a) retorna mapa vazio se `scriptPaths.isEmpty()`, (b) falha-fast com mensagem nomeando o primeiro path + recomendação de passar `ScriptHost` se `host == null`, (c) valida `path.endsWith(host.extension)` com mensagem clara antes de chamar `host.load(...)`
- [x] 1.3 Trocar a chamada interna a `ScriptHostRegistry.loadAll(...)` em `BundleLoader.load(...)` pelo novo helper
- [x] 1.4 Atualizar a chamada interna `ScriptHostRegistry.hostFor(scriptPath)` (na callback de `SceneLoader.load`) para usar o `host` recebido como parâmetro

## 2. Remove ScriptHostRegistry

- [x] 2.1 Deletar `engine-bundle/src/main/kotlin/com/neoutils/engine/bundle/script/ScriptHostRegistry.kt` (inclui `UnsupportedScriptExtensionException`)
- [x] 2.2 Deletar `engine-bundle/src/test/kotlin/com/neoutils/engine/bundle/script/ScriptHostRegistryTest.kt`
- [x] 2.3 Buscar referências residuais (`grep -rn "ScriptHostRegistry\|UnsupportedScriptExtensionException"`) em `engine-bundle`, `engine-bundle-python` e `games/pong`; remover/ajustar todas

## 3. PythonScriptHost factory

- [x] 3.1 Em `engine-bundle-python/src/main/kotlin/com/neoutils/engine/bundle/python/PythonScriptHost.kt`: tornar o construtor primário `internal constructor(context: Context)`
- [x] 3.2 Adicionar `companion object { fun create(): PythonScriptHost }` que instancia `PythonScriptHost(defaultContext())`
- [x] 3.3 Mover o builder do `Context` para `private fun defaultContext(): Context` no companion (mesmas opções: `python.PosixModuleBackend=java`, `python.EmulateJython=true`, `engine.WarnInterpreterOnly=false`, `allowAllAccess(true)`, `allowExperimentalOptions(true)`)
- [x] 3.4 Remover o método `install()` do companion (e qualquer comentário/Kdoc que referencie singleton)

## 4. Tests update

- [x] 4.1 Atualizar `PythonScriptHostTest` para construir via `PythonScriptHost.create()` (ou via construtor `internal` quando precisar de `Context` customizado, já que o teste está no mesmo módulo)
- [x] 4.2 Atualizar `PythonRenderingIntegrationTest` idem
- [x] 4.3 Atualizar `GraalPySmokeTest` idem
- [x] 4.4 Atualizar `BundleLoaderTest`: trocar setup que registrava em `ScriptHostRegistry` por construção local de host + passagem via `scripting`
- [x] 4.5 Adicionar caso `BundleLoaderTest.script-less bundle loads without scripting`: bundle só com `scene.json` sem campo `script`, chamada `fromResources(name)` sem `scripting` deve passar
- [x] 4.6 Adicionar caso `BundleLoaderTest.bundle with scripts and no host fails fast`: bundle com `script: scripts/foo.py`, chamada sem `scripting`, deve lançar exceção com mensagem que cita o path e a recomendação
- [x] 4.7 Adicionar caso `BundleLoaderTest.extension mismatch fails fast`: bundle com `script: scripts/foo.lua`, host com `extension == ".py"`, deve lançar com mensagem que cita o path e a extensão suportada

## 5. Pong Main.kt

- [x] 5.1 Em `games/pong/src/main/kotlin/com/neoutils/engine/games/pong/Main.kt`: substituir `PythonScriptHost.install()` por `val python = PythonScriptHost.create()` no topo de `main(...)`
- [x] 5.2 Passar `scripting = python` em ambas as branches `BundleLoader.fromResources("pong", scripting = python)` e `BundleLoader.fromPath(File(path), scripting = python)`
- [x] 5.3 Confirmar que o corpo de `main()` tem exatamente: 1 construção de host, 1 condicional de path, 1 chamada a `SkikoHost().run(...)` — nada mais

## 6. Docs

- [x] 6.1 Em `CLAUDE.md`: reescrever a seção "Instalando o ScriptHost" para "Construindo o ScriptHost e passando ao BundleLoader" — mostrar o padrão `val python = PythonScriptHost.create()` + `scripting = python`
- [x] 6.2 Em `CLAUDE.md`: atualizar a descrição de `Main.kt` no bloco "Module Structure & How to Run" (substituir menção a `PythonScriptHost.install()` pelo novo padrão)
- [x] 6.3 Em `CLAUDE.md`: adicionar entrada `inject-script-host` na tabela do Roadmap, status `Active`
- [x] 6.4 KDoc em `BundleLoader.fromResources/fromPath` documentando o parâmetro `scripting`: nullable para bundles script-less; **reusar a mesma instância entre múltiplos loads** (custo de boot do `Context`); falha-fast quando há script e o argumento é `null`
- [x] 6.5 KDoc em `PythonScriptHost.create()` documentando que constrói um `Context` GraalPy default e que a instância retornada deve ser reutilizada entre loads

## 7. Smoke & verify

- [x] 7.1 `./gradlew check` passa
- [x] 7.2 `./gradlew :games:pong:run` abre e roda Pong normalmente (WASD/setas, F1, F2)
- [x] 7.3 `./gradlew :games:pong:run --args="<path para bundle pong extraído>"` carrega via filesystem
- [x] 7.4 `./gradlew :games:tictactoe:run` ainda roda (módulo não tocado, sanity check)
- [x] 7.5 `./gradlew :games:demos:run` ainda roda (sanity check)
- [x] 7.6 `openspec validate inject-script-host --strict` passa
