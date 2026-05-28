# lua-scripting Specification

## Purpose

Implementação concreta de `ScriptHost` para scripts Lua `.lua` no módulo `:engine-bundle-lua`, usando LuaJ 3.0.x (JAR puro JVM). É a segunda impl da SPI definida em `script-host`, paralela à `python-scripting`. Encapsula tipos `org.luaj.vm2.*` — esses não vazam para `:engine`, `:engine-bundle`, outros backends ou para os jogos. Contrato é Godot-style (chunk retorna tabela com `extends`, `exports`, `signals`, hooks underscore-prefixed) + LÖVE-style (todos os símbolos da engine vivem sob a tabela global `nengine.*`). `:games:tictactoe` é o consumidor canônico — sentinela do segundo backend de scripting sob o mesmo backend de render (`:engine-skiko`) usado pelos outros jogos.

## Requirements


### Requirement: engine-bundle-lua module hosts the Lua ScriptHost

O projeto SHALL prover um módulo Gradle `:engine-bundle-lua` que depende de `:engine`, `:engine-bundle` e de LuaJ 3.0.x (`org.luaj:luaj-jse`). Esse módulo MUST ser o único local que conhece tipos de `org.luaj.vm2.*` no projeto. O módulo MUST NOT ser dependência de `:engine`, `:engine-bundle`, `:engine-skiko`, `:engine-bundle-python`, ou de jogos que não usem scripting Lua.

#### Scenario: engine-bundle-lua exists with the right dependencies

- **WHEN** a build configuration de `:engine-bundle-lua` é inspecionada
- **THEN** declara dependência em `:engine`
- **AND** declara dependência em `:engine-bundle`
- **AND** declara dependência em LuaJ (`org.luaj:luaj-jse` ou alias equivalente)

#### Scenario: engine modules do not depend on engine-bundle-lua

- **WHEN** a configuração de build de `:engine`, `:engine-bundle`, `:engine-skiko` e `:engine-bundle-python` é inspecionada
- **THEN** nenhum deles declara `:engine-bundle-lua` como dependência

#### Scenario: LuaJ is contained in engine-bundle-lua

- **WHEN** o classpath de compilação de `:engine`, `:engine-bundle`, `:engine-skiko` e `:engine-bundle-python` é resolvido
- **THEN** nenhum artefato `org.luaj:*` está presente

### Requirement: LuaScriptHost implements ScriptHost for .lua files

`:engine-bundle-lua` SHALL expor a classe `LuaScriptHost : ScriptHost` cuja `extension` é `.lua`. A classe MUST ser construída via a factory pública `LuaScriptHost.create()`, que MUST montar um `org.luaj.vm2.Globals` default (via `JsePlatform.standardGlobals()` ou equivalente), instalar o searcher customizado de `require`, injetar a tabela global `nengine` com todos os bindings (ver requirements abaixo), e avaliar quaisquer helpers Lua publicados como resources. O construtor primário MUST ter visibilidade `internal` e aceitar um `Globals` pré-construído, para que testes do módulo possam injetar globals customizados sem expor `org.luaj.vm2.*` na API pública. Cada `LuaScriptHost` MUST manter um único `Globals` LuaJ compartilhado entre todos os scripts que carrega. Instâncias de `LuaScriptHost` MUST ser repassadas explicitamente ao `BundleLoader` pelo caller (via parâmetro `scripting`); a classe MUST NOT registrar a si mesma em nenhum singleton global.

#### Scenario: LuaScriptHost.create returns a usable host for .lua

- **WHEN** código chama `LuaScriptHost.create()`
- **THEN** a função retorna uma instância de `LuaScriptHost` cuja `extension == ".lua"`
- **AND** essa instância pode ser passada diretamente como `scripting` em `BundleLoader.fromResources("tictactoe", scripting = host)`
- **AND** nenhuma função de registro global é invocada

#### Scenario: Primary constructor is internal

- **WHEN** a API pública de `LuaScriptHost` é inspecionada
- **THEN** o construtor primário tem visibilidade `internal`
- **AND** nenhum construtor `public` aceitando `org.luaj.vm2.Globals` é exposto
- **AND** o único entry point público de construção é a factory `create()`

#### Scenario: Multiple scripts share one Globals per host

- **GIVEN** um `LuaScriptHost` construído via `create()` e dois scripts `a.lua` e `b.lua` no bundle
- **WHEN** ambos são carregados pelo mesmo host (via `BundleLoader.fromResources(name, scripting = host)`)
- **THEN** o `Globals` LuaJ criado pelo host é exatamente um
- **AND** ambos os scripts são avaliados nesse `Globals`

### Requirement: Lua chunk returns a table with extends, exports, signals and hooks

Todo script Lua carregado por `LuaScriptHost` MUST ser um chunk válido cujo `return` final é uma tabela. A tabela MUST conter no mínimo o campo `extends` (string, nome do tipo Node nativo). A tabela MAY conter `exports`, `signals`, e qualquer subconjunto dos hooks `_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`, `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`. Scripts cujo chunk retorna `nil`, retorna um valor não-tabela, ou cuja tabela retornada não tem campo `extends` (ou tem `extends` que não é string) MUST falhar fast no `load`, com mensagem nomeando o path do script e a causa.

`<extends>` MUST ser o nome simples (`"Node2D"`, `"Camera2D"`, `"Area2D"`, etc.) ou FQN (`"com.neoutils.engine.scene.Node2D"`) de um tipo registrado no `NodeRegistry`. O resolvedor MUST consultar `NodeRegistry` por nome simples primeiro (varrendo tipos registrados) e cair para FQN se houver match.

#### Scenario: Minimal script loads with only extends

- **GIVEN** um script `minimal.lua` cujo conteúdo é exatamente `return { extends = "Node2D" }`
- **WHEN** `LuaScriptHost.load("minimal.lua", bundle)` é chamado
- **THEN** o load completa sem erro
- **AND** `Script.extendsType` é `Node2D::class`
- **AND** `Script.exports` é vazio
- **AND** `Script.signals` é vazio

#### Scenario: Chunk that returns nil fails fast

- **GIVEN** um script cujo conteúdo é apenas `local x = 1` (sem `return`)
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o path e indica que o chunk deve retornar uma tabela

#### Scenario: Chunk that returns non-table fails fast

- **GIVEN** um script cujo conteúdo é `return 42`
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o path e indica que o retorno deve ser uma tabela

#### Scenario: Missing extends field fails fast

- **GIVEN** um script cujo retorno é uma tabela sem campo `extends`
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o path do script e indica a falta do campo `extends`

#### Scenario: Unknown extends type fails fast

- **GIVEN** um script com `return { extends = "BananaNode" }` e `BananaNode` não registrado
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia `BananaNode` e o path do script

### Requirement: Exports are discovered from the exports table

`LuaScriptHost.load` MUST descobrir `Script.exports` lendo o campo `exports` da tabela retornada pelo chunk. O campo `exports`, se presente, MUST ser uma tabela cujas chaves são strings (nomes de export) e cujos valores são tabelas com os campos `type` (string com o nome do tipo suportado) e `default` (valor literal Lua). Tipos suportados: `"int"`, `"float"`, `"bool"`, `"string"`, `"Vec2"`, `"Color"`, `"Rect"`, `"NodeRef"`, `"Key"`. Cada entrada MUST virar um `ExportedProperty` (`name`, `type`, `default`, `nullable=false` por padrão). Tipos não suportados MUST falhar fast com mensagem clara nomeando o script, a chave, e o tipo declarado.

#### Scenario: Numeric export becomes ExportedProperty

- **GIVEN** script com `return { extends = "Node2D", exports = { speed = { type = "float", default = 360.0 } } }`
- **WHEN** `load` é chamado
- **THEN** `Script.exports` contém `(name="speed", type=Float::class, default=360.0f, nullable=false)`

#### Scenario: Vec2 default literal is parsed

- **GIVEN** script com `exports = { offset = { type = "Vec2", default = nengine.Vec2(16.0, 96.0) } }`
- **WHEN** `load` é chamado
- **THEN** `Script.exports` contém `(name="offset", type=Vec2::class, default=Vec2(16f, 96f))`

#### Scenario: Unsupported export type fails fast

- **GIVEN** script com `exports = { x = { type = "BananaType", default = 1 } }`
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o script, a chave `x`, e o tipo `BananaType`

### Requirement: Signals are discovered from the signals table

`LuaScriptHost.load` MUST descobrir `Script.signals` lendo o campo `signals` da tabela retornada pelo chunk. O campo `signals`, se presente, MUST ser uma tabela cujas chaves são strings (nomes de signal) e cujos valores são strings com type-hint puramente documental (ignorados runtime). Cada entrada MUST virar uma `SignalDeclaration(name)` em `Script.signals`. Signals declarados aqui MUST NOT aparecer em `Script.exports`. Tentar declarar o mesmo nome simultaneamente em `exports` e `signals` MUST falhar fast com mensagem clara.

#### Scenario: Signal declaration becomes a signal slot

- **GIVEN** script com `return { extends = "CharacterBody2D", signals = { scored = "string" } }`
- **WHEN** `load` é chamado
- **THEN** `Script.signals` contém uma entrada com nome `scored`
- **AND** `Script.exports` NÃO contém entrada para `scored`

#### Scenario: Same name in both exports and signals fails fast

- **GIVEN** script com `exports = { hit = {...} }` e `signals = { hit = "string" }`
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o script e o nome `hit`

### Requirement: nengine global table is the only injection namespace

`LuaScriptHost` MUST injetar em `_G` uma única tabela chamada `nengine` que carrega todos os símbolos da engine acessíveis aos scripts Lua. A tabela `nengine` MUST conter, no mínimo:

- **Factories de valor:** `nengine.Vec2(x, y)`, `nengine.Color(r, g, b [, a])`, `nengine.Rect(position, size)`, `nengine.Transform([position [, scale [, rotation]]])`, `nengine.NodeRef(path)`. Cada factory MUST devolver uma instância Kotlin imutável correspondente, convertendo `number` Lua para `Float` Kotlin quando aplicável.
- **Factory de signal:** `nengine.signal(typeHint)` que devolve uma instância `Signal<Any?>` Kotlin. O `typeHint` é apenas documentação e MUST ser ignorado runtime.
- **Enums:** `nengine.Key.<Name>` para cada entrada do enum `com.neoutils.engine.input.Key`. `nengine.MouseButton.Left`, `nengine.MouseButton.Right`, `nengine.MouseButton.Middle`.
- **Tipos de Node:** `nengine.Node2D`, `nengine.Camera2D`, `nengine.Label`, `nengine.ColorRect`, `nengine.Circle2D`, `nengine.Line2D`, `nengine.Polygon2D`, **`nengine.CanvasLayer`**, **`nengine.Panel`**, **`nengine.Button`**, `nengine.Area2D`, `nengine.StaticBody2D`, `nengine.CharacterBody2D`, `nengine.RigidBody2D`, `nengine.CollisionShape2D`, `nengine.Shape2D`, `nengine.RectangleShape2D`, `nengine.CircleShape2D`, `nengine.Timer`. Cada binding MUST permitir construção via chamada-de-função (ex.: `local n = nengine.Node2D()`, `local b = nengine.Button()`).
- **Utilitário cross-script:** `nengine.script_of(node)` que devolve o wrapper Lua do script anexado ao Node (ou um wrapper "bare" capaz de expor `Signal<*>` fields refletivos quando o Node não tem script attached).

Símbolos da engine MUST NOT ser expostos como globais top-level (não há `Vec2` global; é sempre `nengine.Vec2`). `_G` MUST conter apenas: a tabela `nengine`, os builtins padrão do LuaJ (`print`, `pairs`, `ipairs`, `string`, `math`, `table`, `coroutine`, `os` limitado, `io` limitado), e o que cada script colocar lá por conta própria.

#### Scenario: nengine table is present in script globals

- **WHEN** um script Lua é avaliado pelo host
- **THEN** dentro do chunk, `_G.nengine` resolve para uma tabela não-nil
- **AND** `_G.Vec2` é `nil` (Vec2 só existe sob `nengine`)

#### Scenario: nengine.Vec2 builds a Kotlin Vec2

- **GIVEN** um script com `local v = nengine.Vec2(3.0, 4.0)`
- **WHEN** o chunk é avaliado
- **THEN** `v` é uma instância de `com.neoutils.engine.math.Vec2` cuja `x = 3f` e `y = 4f`

#### Scenario: nengine.MouseButton.Left resolves to enum constant

- **GIVEN** um script que chama `tree.input:wasMouseClicked(nengine.MouseButton.Left)`
- **WHEN** o hook é executado
- **THEN** o argumento recebido pelo método Kotlin é exatamente `MouseButton.Left`

#### Scenario: nengine.signal creates a Signal instance

- **GIVEN** um script com `return { extends = "Node2D", signals = { hit = "string" } }`
- **WHEN** o `load` analisa a tabela retornada e o `attach` instancia o slot
- **THEN** o slot `hit` é uma instância de `com.neoutils.engine.serialization.Signal` que aceita `connect`/`emit`

#### Scenario: UI nodes are bound under nengine

- **WHEN** um script faz `local cl = nengine.CanvasLayer(); local p = nengine.Panel(); local b = nengine.Button()`
- **THEN** cada chamada retorna uma instância válida do tipo Kotlin correspondente
- **AND** o script pode declarar `return { extends = "Button", _ready = function(self) self.pressed:connect(...) end }`

#### Scenario: Button.pressed signal is connectable from Lua

- **GIVEN** um script Lua com `return { extends = "Button", _ready = function(self) self.pressed:connect(function() print("clicked") end) end }`
- **WHEN** o `Button` é clicado e a UI hit-test consome o clique
- **THEN** o handler conectado executa exatamente uma vez por click cycle

#### Scenario: Scripts cannot bypass nengine namespace

- **GIVEN** um script que tenta `local v = Vec2(1.0, 2.0)` (sem `nengine.`)
- **WHEN** o chunk é avaliado
- **THEN** Lua levanta erro nomeando `Vec2` como global não definido

### Requirement: require resolves via BundleSource through custom package.searchers

`LuaScriptHost` MUST instalar um searcher customizado em `package.searchers` (primeiro slot, antes de qualquer searcher default) que delega leitura ao `BundleSource` recebido em `load`. Para `require "scripts.utils"`, o searcher MUST:

1. Converter o nome modular (`"scripts.utils"`) para path com extensão (`"scripts/utils.lua"`), substituindo `.` por `/`.
2. Chamar `bundle.read("scripts/utils.lua")`.
3. Compilar o conteúdo com LuaJ (`Globals.load`) e retornar a função-chunk para o `require` engine de Lua.
4. Se `bundle.read` falhar (arquivo não existe), o searcher MUST devolver uma string descrevendo a falha (Lua tenta os próximos searchers automaticamente).

Searchers default (filesystem, `package.path`, `package.cpath`) MUST ser removidos do `package.searchers` durante o setup do `LuaScriptHost`, garantindo que scripts não consigam ler arquivos fora do bundle. O cache `package.loaded` MUST ser respeitado normalmente — `require "scripts.utils"` chamado duas vezes MUST executar o módulo apenas uma vez.

#### Scenario: require resolves a sibling module in the bundle

- **GIVEN** um bundle com `scripts/main.lua` (contendo `local U = require "scripts.utils"; ...`) e `scripts/utils.lua` (contendo `return { add = function(a, b) return a + b end }`)
- **WHEN** o host carrega `scripts/main.lua`
- **THEN** o `require "scripts.utils"` resolve sem erro
- **AND** `U.add(2, 3)` retorna `5`

#### Scenario: require for missing module fails

- **GIVEN** um script que chama `require "scripts.nonexistent"`
- **AND** `bundle.read("scripts/nonexistent.lua")` lança/retorna falha
- **WHEN** o chunk é avaliado
- **THEN** Lua levanta erro de `require` com mensagem que cita o módulo

#### Scenario: Filesystem searchers are removed

- **WHEN** o `LuaScriptHost` é construído
- **THEN** `package.searchers` contém apenas o searcher do `BundleSource` (mais o preload searcher do LuaJ padrão, que continua válido para módulos pre-carregados)
- **AND** o searcher de filesystem (`package.path`) não está presente

#### Scenario: require is memoized via package.loaded

- **GIVEN** um módulo `scripts/utils.lua` que loga ao ser executado
- **WHEN** dois scripts diferentes do bundle ambos chamam `require "scripts.utils"`
- **THEN** o módulo é executado exatamente uma vez
- **AND** ambos os requires recebem a mesma tabela cacheada

### Requirement: ScriptInstance wraps the host Node via userdata + metatable

`LuaScriptHost.attach(node, script)` MUST criar uma `LuaTable` instância que combina:

1. A tabela retornada pelo chunk (com hooks `_ready`, `_process`, ...).
2. Uma metatable cujo `__index(self, key)` resolve, **nesta ordem**:
   - Signal slots declarados em `script.signals` (instanciados por-Node no attach).
   - Signal slots reflection-discovered em campos `val signal: Signal<*>` no Node Kotlin.
   - Exports declarados em `script.exports`.
   - Properties ergonômicas de `Node2D` quando aplicável (`position`, `rotation`, `scale`).
   - Properties e métodos públicos do Node Kotlin via reflexão (`name`, `tree`, `world()`, `findChild()`, etc.).
3. Uma `__newindex(self, key, value)` que espelha a mesma ordem para escrita: exports primeiro, depois `position`/`rotation`/`scale` em Node2D, depois fields públicos do Node.

A instância MUST ser passada como **primeiro argumento** (`self`) a cada hook (`fn:invoke(varargsOf(self, ...))`). Dentro de um hook `_process(self, dt)`, ler `self.position` MUST chamar o getter Kotlin `Node2D.position`, e escrever `self.position = nengine.Vec2(x, y)` MUST chamar o setter Kotlin com invalidação de cache de world.

Os mesmos contratos de signal já estabelecidos para Python valem aqui: cada attach instancia novos `Signal<Any?>` Kotlin para cada slot em `script.signals`; dois Nodes com o mesmo script têm signals independentes.

#### Scenario: self.position reads through to Kotlin transform

- **GIVEN** um script Lua anexado a um `Node2D` cuja `transform.position = Vec2(10f, 20f)` foi set pelo Kotlin
- **WHEN** o script lê `self.position` em qualquer hook
- **THEN** o valor é uma `Vec2` Kotlin com `x = 10f`, `y = 20f`

#### Scenario: self.position write updates host transform and invalidates child cache

- **GIVEN** um script Lua anexado a um pai `Node2D` que tem um filho `Node2D` com `world()` cacheado
- **WHEN** `_physics_process` do pai executa `self.position = nengine.Vec2(99.0, 99.0)`
- **THEN** `node.transform.position` é `Vec2(99, 99)` após o retorno
- **AND** o próximo `child:world()` reflete a nova posição do pai

#### Scenario: Same script on two nodes yields independent signals

- **GIVEN** um script `paddle.lua` com `signals = { scored = "string" }` anexado a dois Nodes diferentes
- **WHEN** código conecta um handler em `node1.scriptInstance.signals["scored"]` e emite em `node2.scriptInstance.signals["scored"]`
- **THEN** o handler em `node1` NÃO é invocado

#### Scenario: Lua accesses self.<signal_name> for declared signals

- **GIVEN** um script com `signals = { scored = "string" }` e um hook `_process` que faz `self.scored:emit("Left")`
- **WHEN** `instance.onProcess(0.016f)` é invocado
- **THEN** o `Signal` Kotlin recebe `emit("Left")`
- **AND** handlers conectados em Kotlin são notificados com `"Left"`

### Requirement: Hooks delegate from Node to Lua table fields

`LuaScriptHost.attach` MUST retornar um `ScriptInstance` cujos métodos invocam os campos-função homônimos na tabela do chunk. O conjunto de hooks despachados é:

```
Kotlin / SPI                Lua (campo da tabela retornada pelo chunk)
─────────────────────────────────────────────────────────────────────
onEnter()                   _ready(self)
onProcess(dt)               _process(self, dt)
onPhysicsProcess(dt)        _physics_process(self, dt)
onDraw(renderer)            _draw(self, renderer)
onExit()                    _exit_tree(self)
onAreaEntered(area)         _on_area_entered(self, area)
onAreaExited(area)          _on_area_exited(self, area)
onBodyEntered(body)         _on_body_entered(self, body)
onBodyExited(body)          _on_body_exited(self, body)
```

Campos ausentes na tabela ou cujo valor seja `nil` / não-`function` MUST resultar em no-op no `ScriptInstance` (nenhuma exceção). Exceções lançadas de dentro de um hook Lua MUST propagar para o caller Kotlin como `LuaError` (ou subclasse), de forma que o engine falhe fast, consistente com a política de fail-fast da política de scripting Python.

#### Scenario: _process is dispatched

- **GIVEN** um script Lua cuja tabela retornada inclui `_process = function(self, dt) self._counter = (self._counter or 0) + 1 end`
- **WHEN** o engine invoca `instance.onProcess(0.016f)`
- **THEN** `_process` é chamado com `self` igual à instância e `dt ≈ 0.016`
- **AND** `self._counter` é incrementado

#### Scenario: _draw is dispatched with the renderer

- **GIVEN** um script Lua com `_draw = function(self, renderer) renderer:drawRect(...) end`
- **WHEN** o engine invoca `instance.onDraw(renderer)`
- **THEN** o renderer Kotlin recebe `drawRect(...)`

#### Scenario: Missing hooks are no-ops

- **GIVEN** um script Lua cuja tabela NÃO define `_physics_process`
- **WHEN** o engine invoca `instance.onPhysicsProcess(1f / 60f)`
- **THEN** nenhuma exceção é lançada
- **AND** o tick segue normalmente

#### Scenario: Lua exception in hook propagates to caller

- **GIVEN** um script cuja `_ready` chama `error("boom")`
- **WHEN** `instance.onEnter()` é invocado
- **THEN** uma exceção propaga para o caller Kotlin
- **AND** a mensagem contém `"boom"` e o path/linha do script

#### Scenario: _on_area_entered is dispatched

- **GIVEN** um script Lua anexado a um `Area2D`, com `_on_area_entered = function(self, area) ... end`
- **WHEN** o `PhysicsSystem` detecta entrada em sobreposição com outra `Area2D`
- **THEN** `_on_area_entered(self, otherArea)` é invocado exatamente uma vez

### Requirement: ScriptInstance implements currentValue for round-trip

O `ScriptInstance` Lua MUST implementar `currentValue(name: String): Any?` lendo o atributo da userdata pelo mesmo nome via a metatable `__index` e devolvendo o valor convertido para o tipo Kotlin declarado em `ExportedProperty.type`. A conversão MUST ser o inverso de `PropCoercion.coerce`: `LuaNumber` Lua vira `Float` ou `Int` conforme o tipo declarado, `LuaUserdata` envolvendo um `Vec2` vira `Vec2` Kotlin, etc. Se o atributo Lua é `nil` (porque o export nunca foi acessado), `currentValue` MUST devolver o `ExportedProperty.default`. Chamar `currentValue` com um nome não declarado em `exports` MUST lançar `IllegalArgumentException` nomeando o nome desconhecido e o path do script.

#### Scenario: currentValue returns Lua value converted to Kotlin type

- **GIVEN** script com `exports = { speed = { type = "float", default = 360.0 } }`, anexado, com `setExport("speed", 480.0f)` previamente chamado
- **WHEN** código chama `instance.currentValue("speed")`
- **THEN** o valor devolvido é o `Float` Kotlin `480.0f`
- **AND** NÃO é um `LuaNumber` ou `Double` cru

#### Scenario: currentValue returns default when attribute is absent

- **GIVEN** script com `exports = { speed = { type = "float", default = 360.0 } }`, sem `setExport` nem leitura prévia
- **WHEN** código chama `instance.currentValue("speed")`
- **THEN** o valor devolvido é `360.0f`

#### Scenario: currentValue on unknown name fails

- **GIVEN** script cujo `exports` não inclui `mystery`
- **WHEN** código chama `instance.currentValue("mystery")`
- **THEN** uma `IllegalArgumentException` é lançada
- **AND** a mensagem nomeia `mystery` e o path do script

### Requirement: Lua scripts can connect to Kotlin-declared Signal fields

Quando uma subclasse Kotlin de `Node` expõe um `val` público do tipo `Signal<T>` (ex.: `Timer.timeout: Signal<Unit>`), o wrapper Lua do Node SHALL expor o field como um atributo legível via `__index`. Ler o atributo MUST devolver um proxy Lua cujos métodos `:connect(handler)` e `:disconnect(handler)` roteiam para o `Signal<T>` Kotlin subjacente. O `:connect` MUST aceitar qualquer função Lua; o proxy MUST adaptar a função para que `emit` do lado Kotlin chame a função Lua com o valor emitido (ou sem argumentos quando `T == Unit`). A descoberta de fields `Signal<*>` MUST ser baseada em reflexão — código Kotlin SHALL NOT precisar anotar signals com um marker para serem visíveis. Erros levantados pela função Lua durante a emissão MUST propagar pra fora de `Signal.emit`, consistente com a política de fail-fast.

#### Scenario: Lua connects to Timer.timeout

- **GIVEN** uma instância Kotlin de `Timer` numa cena live com `waitTime = 0.1f`, `autostart = true`
- **AND** um script Lua anexado ao pai que faz `timer.timeout:connect(my_handler)` em `_ready`
- **WHEN** o timer emite `timeout`
- **THEN** `my_handler` é invocado sem argumentos

#### Scenario: Lua disconnect removes the handler

- **GIVEN** um script Lua que conectou `my_handler` a `timer.timeout`
- **WHEN** o script depois chama `timer.timeout:disconnect(my_handler)`
- **THEN** emissões subsequentes NÃO invocam `my_handler`

#### Scenario: Reflection discovers Signal fields without annotation

- **WHEN** o wrapper Lua para uma instância `Timer` é construído
- **THEN** o atributo `timeout` é legível sem qualquer annotation no `val timeout` Kotlin

#### Scenario: Lua handler exception propagates

- **GIVEN** um handler Lua conectado a `timer.timeout` que faz `error("boom")`
- **WHEN** o timer emite `timeout`
- **THEN** a exceção propaga pra fora de `Signal.emit` e crasha o loop com mensagem visível

### Requirement: Globals is eagerly initialized

Para evitar cold start visível durante o primeiro frame, `LuaScriptHost.create()` MUST inicializar o `Globals` LuaJ e instalar bindings + searcher + runtime helpers **antes** de retornar. Quando o caller passa essa instância ao `BundleLoader`, o primeiro `load` MUST encontrar `Globals` já pronto, sem incluir custo de boot.

#### Scenario: Globals is ready when first load runs

- **WHEN** o tempo entre o retorno de `LuaScriptHost.create()` e a primeira chamada de `BundleLoader.fromResources(..., scripting = host)` é medido
- **THEN** o `Globals` já está construído
- **AND** o `load` em si não inclui custo de boot dos `Globals` (apenas parse + eval do chunk)

### Requirement: LuaCATS stubs are published as module resources

`:engine-bundle-lua` SHALL publicar arquivos `.lua` no formato LuaCATS (`---@class`, `---@field`, `---@param`, `---@return`) em `src/main/resources/stubs/engine/`, cobrindo no mínimo: `Node`, `Node2D`, `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `CanvasLayer`, `Panel`, `Button`, `Vec2`, `Color`, `Rect`, `Transform`, `NodeRef`, `Key`, `MouseButton`, `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `RigidBody2D`, `CollisionShape2D`, `Shape2D`, `RectangleShape2D`, `CircleShape2D`, `Timer`, `TimerMode`, `Signal`, `Renderer`, `Input`. Um arquivo `nengine.lua` adicional SHALL descrever a tabela global `nengine` listando todos os símbolos disponíveis (incluindo `CanvasLayer`, `Panel`, `Button`). Os stubs MUST refletir os nomes de hook Godot-style (`_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`, `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`) e MUST NOT incluir `on_enter`/`on_update`/`on_render`/`on_exit`/`on_collide`. `Node2D` MUST declarar os campos `position: Vec2`, `rotation: number`, `scale: Vec2` e o método `world(): Transform`. `CollisionObject2D` MUST declarar os signal-attributes `area_entered`, `area_exited`, `body_entered`, `body_exited` como `Signal`. `Button` MUST declarar o signal-attribute `pressed` como `Signal`. `Timer` MUST declarar `waitTime`, `autostart`, `oneShot`, `processCallback`, `timeLeft`, `isStopped`, `timeout`, `start`, `stop`.

#### Scenario: Stubs resource directory exists

- **WHEN** o jar de `:engine-bundle-lua` é construído
- **THEN** contém o diretório `stubs/engine/` com pelo menos `nengine.lua`, `node.lua`, `node2d.lua`, `vec2.lua`, `color.lua`, `signal.lua`, `timer.lua`, `physics.lua`, `ui.lua` (ou stubs equivalentes cobrindo `CanvasLayer`, `Panel`, `Button`)

#### Scenario: nengine stub describes the global table

- **WHEN** o stub `engine/nengine.lua` é inspecionado
- **THEN** declara `---@class nengine` com `Vec2`, `Color`, `Rect`, `Transform`, `NodeRef`, `signal`, `Key`, `MouseButton`, `Node2D`, `Camera2D`, `Label`, `CanvasLayer`, `Panel`, `Button`, `Area2D`, `StaticBody2D`, `CharacterBody2D`, `RigidBody2D`, `script_of` como campos
- **AND** declara o tipo de retorno de cada factory (ex.: `Vec2(x: number, y: number): Vec2`)

#### Scenario: Node2D stub exposes ergonomic accessors

- **WHEN** o stub `engine/node2d.lua` é inspecionado
- **THEN** declara `---@field position Vec2`, `---@field rotation number`, `---@field scale Vec2`
- **AND** declara `---@return Transform` em `world()`

#### Scenario: Button stub declares pressed signal field

- **WHEN** o stub de `Button` é inspecionado
- **THEN** declara `---@field pressed Signal` (signal built-in instanciado por instância)
- **AND** declara `---@field size Vec2`, `---@field text string`, `---@field disabled boolean`, e as quatro cores

#### Scenario: Signal stub exposes connect, emit, disconnect

- **WHEN** o stub `engine/signal.lua` é inspecionado
- **THEN** declara métodos `connect(handler)`, `disconnect(handler)`, `emit(value)`
- **AND** declara que `Signal` é parametrizada (LuaCATS generic ou comentário equivalente)

#### Scenario: CollisionObject2D stub declares built-in signals

- **WHEN** o stub de `CollisionObject2D` é inspecionado
- **THEN** declara `area_entered`, `area_exited`, `body_entered`, `body_exited` como `Signal`

#### Scenario: Legacy hook names are not present

- **WHEN** os stubs sob `engine/` são listados
- **THEN** nenhum stub declara `on_enter`, `on_update`, `on_render`, `on_exit`, `on_collide`

#### Scenario: BoxCollider stub does not exist

- **WHEN** os stubs sob `engine/` são listados
- **THEN** nenhum arquivo declara `BoxCollider` ou `Collider` (apenas os tipos novos `CollisionObject2D`, `CollisionShape2D`, `Shape2D` e descendentes)
