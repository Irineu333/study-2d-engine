## MODIFIED Requirements

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
