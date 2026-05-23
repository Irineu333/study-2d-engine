## MODIFIED Requirements

### Requirement: PythonScriptHost implements ScriptHost for .py files

`:engine-bundle-python` SHALL expor a classe `PythonScriptHost : ScriptHost` cuja `extension` é `.py`. A classe MUST ser construída via a factory pública `PythonScriptHost.create()`, que MUST montar um `org.graalvm.polyglot.Context` default (Python language, `allowAllAccess`, opções `PosixModuleBackend=java`, `EmulateJython=true`, `WarnInterpreterOnly=false`). O construtor primário MUST ter visibilidade `internal` e aceitar um `Context` pré-construído, para que testes do módulo possam injetar `Context` customizado sem expor `org.graalvm.polyglot.*` na API pública. Cada `PythonScriptHost` MUST manter um único `Context` Polyglot compartilhado entre todos os scripts que carrega. Instâncias de `PythonScriptHost` MUST ser repassadas explicitamente ao `BundleLoader` pelo caller (via parâmetro `scripting`); a classe MUST NOT registrar a si mesma em nenhum singleton global.

#### Scenario: PythonScriptHost.create returns a usable host for .py

- **WHEN** código chama `PythonScriptHost.create()`
- **THEN** a função retorna uma instância de `PythonScriptHost` cuja `extension == ".py"`
- **AND** essa instância pode ser passada diretamente como `scripting` em `BundleLoader.fromResources("pong", scripting = host)`
- **AND** nenhuma função de registro global é invocada

#### Scenario: Primary constructor is internal

- **WHEN** a API pública de `PythonScriptHost` é inspecionada
- **THEN** o construtor primário tem visibilidade `internal`
- **AND** nenhum construtor `public` aceitando `org.graalvm.polyglot.Context` é exposto
- **AND** o único entry point público de construção é a factory `create()`

#### Scenario: Multiple scripts share one Polyglot Context per host

- **GIVEN** um `PythonScriptHost` construído via `create()` e dois scripts `a.py` e `b.py` no bundle
- **WHEN** ambos são carregados pelo mesmo host (via `BundleLoader.fromResources(name, scripting = host)`)
- **THEN** o `Context` Polyglot criado pelo host é exatamente um
- **AND** ambos os scripts são avaliados nesse contexto

### Requirement: Polyglot Context is eagerly initialized

Para evitar cold start visível durante o primeiro frame, `PythonScriptHost.create()` MUST inicializar o `Context` Polyglot e instalar bindings + runtime Python **antes** de retornar. Quando o caller passa essa instância ao `BundleLoader`, o primeiro `load` MUST encontrar o `Context` já pronto, sem incluir custo de boot.

#### Scenario: Context is ready when first load runs

- **WHEN** o tempo entre o retorno de `PythonScriptHost.create()` e a primeira chamada de `BundleLoader.fromResources(..., scripting = host)` é medido
- **THEN** o Context já está construído
- **AND** o `load` em si não inclui custo de boot do Context (apenas parse + eval do módulo)
