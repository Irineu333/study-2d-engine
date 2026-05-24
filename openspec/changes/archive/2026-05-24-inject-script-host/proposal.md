## Why

`BundleLoader.fromResources("pong")` carrega scripts via um singleton mutável (`ScriptHostRegistry`) que precisa ser populado por um side-effect (`PythonScriptHost.install()`) chamado em algum lugar antes — invisível ao compilador. Esquecer a chamada compila e quebra só em runtime; a dependência fica oculta. A change torna o `ScriptHost` um parâmetro explícito do `BundleLoader`, eliminando estado global e tornando a relação loader↔host óbvia no call site.

## What Changes

- **BREAKING** `BundleLoader.fromResources(name, types)` ganha parâmetro `scripting: ScriptHost? = null`; mesma adição em `fromPath(bundleDir, types)`.
- **BREAKING** `ScriptHostRegistry` (e `UnsupportedScriptExtensionException`) removidos. A validação de extensão (`path` casa com `host.extension`) passa a ser feita inline no `BundleLoader`, com mensagem clara quando o bundle pede uma extensão diferente da que o host suportado.
- **BREAKING** `PythonScriptHost.install()` removido. Em seu lugar: `PythonScriptHost.create()` (factory pública que monta o `Context` GraalPy default). Construtor primário permanece `internal` para permitir injeção de `Context` customizado em testes.
- Bundles sem nenhum `script` referenciado funcionam com `scripting = null` (sem custo de boot de runtime de scripting).
- Quando `scripting != null` mas o bundle não tem scripts, é warning silencioso (sem erro) — `host.extension` simplesmente não é consultada.
- Quando `scripting == null` mas o bundle tem ao menos um `script`, falha-fast com mensagem nomeando o primeiro path encontrado e a recomendação de passar um host.
- `:games:pong/Main.kt` migra para o novo padrão: `val python = PythonScriptHost.create()` no topo + `BundleLoader.fromResources("pong", scripting = python)`.
- Caller é dono do ciclo de vida do host. KDoc em `BundleLoader` e em `PythonScriptHost.create` documenta que o host deve ser reusado entre múltiplos loads (o `Context` GraalPy é caro de construir).
- `CLAUDE.md` atualiza a seção "Instalando o ScriptHost" → "Construindo o ScriptHost e passando ao BundleLoader".

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `bundle-loading`: `BundleLoader.fromResources/fromPath` ganham parâmetro `scripting`; comportamento de despacho de scripts deixa de consultar `ScriptHostRegistry` e passa a consultar o host injetado; novos cenários para bundle sem scripts (`scripting = null`) e para erro quando script existe mas host está ausente.
- `script-host`: requirement de `ScriptHostRegistry` removido inteiro; SPI passa a listar apenas `ScriptHost`, `Script`, `ScriptInstance`, `ExportedProperty`, `BundleSource`. Despacho por extensão deixa de ser preocupação da SPI.
- `python-scripting`: requirement de auto-registro via `install()` removido; passa a falar em `PythonScriptHost.create()` como entry point. Eager init do `Context` ocorre na construção da instância, não em `install()`.
- `pong-sample`: requirement que proíbe `Main.kt` de "instanciar manualmente nenhum host de scripting" é invertido — passa a exigir que `Main.kt` construa explicitamente um `PythonScriptHost.create()` e injete no `BundleLoader`.

## Impact

- **Código tocado:**
  - `:engine-bundle` — `BundleLoader.kt` (assinatura + lógica de despacho); `ScriptHostRegistry.kt` (removido); `ScriptHostRegistryTest.kt` (removido); `BundleLoaderTest.kt` (atualizado).
  - `:engine-bundle-python` — `PythonScriptHost.kt` (construtor `internal`, factory `create()`, `install()` removido); `PythonScriptHostTest.kt`, `PythonRenderingIntegrationTest.kt`, `GraalPySmokeTest.kt` (instanciar via `create()`).
  - `:games:pong` — `Main.kt`.
- **Documentação:** `CLAUDE.md` (seção scripting + tabela do roadmap ganha entrada `inject-script-host`).
- **Sem mudança transitiva:** `:engine`, `:engine-skiko`, `:engine-compose`, `:games:tictactoe`, `:games:demos` não tocam.
- **Sem impacto em multi-language:** API singular agora; se um segundo backend nascer, a evolução natural é overload com `List<ScriptHost>` — sem quebrar quem usa singular.
