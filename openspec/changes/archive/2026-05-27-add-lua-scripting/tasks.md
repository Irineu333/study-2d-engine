## 1. Module skeleton & build wiring

- [x] 1.1 Add `luaj` version and `luaj-jse` library alias to `gradle/libs.versions.toml`
- [x] 1.2 Create directory `engine-bundle-lua/` with `build.gradle.kts` declaring `kotlinJvm` plugin and dependencies on `:engine`, `:engine-bundle`, `libs.luaj.jse`, plus test deps (`kotlin-test`, `junit`)
- [x] 1.3 Add `include(":engine-bundle-lua")` to `settings.gradle.kts` and verify `./gradlew projects` lists it
- [x] 1.4 Confirm `./gradlew :engine-bundle-lua:build` succeeds with the empty source set

## 2. LuaScriptHost — bare skeleton

- [x] 2.1 Create `com.neoutils.engine.bundle.lua.LuaScriptHost : ScriptHost` with `extension = ".lua"`, internal primary constructor taking `Globals`, and public companion `create()` factory
- [x] 2.2 In `create()`, build `Globals` via `JsePlatform.standardGlobals()` and store on the host
- [x] 2.3 Implement stub `load(path, bundle)` that throws `NotImplementedError` and `attach(node, script)` that throws `NotImplementedError` (compile-only milestone)
- [x] 2.4 Add unit test asserting `LuaScriptHost.create().extension == ".lua"` and the primary constructor is `internal`

## 3. nengine global table injection

- [x] 3.1 In the host init block, build a Kotlin map of `nengine.*` factories and types and install it into `Globals` as a single `LuaTable` named `nengine`
- [x] 3.2 Add value-type factories: `nengine.Vec2`, `nengine.Color`, `nengine.Rect`, `nengine.Transform`, `nengine.NodeRef`, each backed by a `OneArgFunction`/`TwoArgFunction`/`VarArgFunction` that coerces Lua numbers to Kotlin `Float` and constructs the host class
- [x] 3.3 Add enum table for `nengine.Key` (iterate `Key.entries`) and `nengine.MouseButton` (iterate `MouseButton.entries`)
- [x] 3.4 Add Node class bindings: `nengine.Node2D`, `nengine.Camera2D`, `nengine.Label`, `nengine.ColorRect`, `nengine.Circle2D`, `nengine.Line2D`, `nengine.Polygon2D`, `nengine.Area2D`, `nengine.StaticBody2D`, `nengine.CharacterBody2D`, `nengine.RigidBody2D`, `nengine.CollisionShape2D`, `nengine.Shape2D`, `nengine.RectangleShape2D`, `nengine.CircleShape2D`, `nengine.Timer`. Each binding must be callable to construct an instance
- [x] 3.5 Add `nengine.signal(typeHint)` factory returning a fresh `Signal<Any?>` Kotlin instance
- [x] 3.6 Add placeholder `nengine.script_of(node)` that throws (will be wired to instance index in section 7)
- [x] 3.7 Remove default `_G` symbols that would let scripts bypass the namespace (do NOT remove standard library: `string`, `math`, `table`, etc.)
- [x] 3.8 Test: a script with `return { extends = "Node2D" }` that uses `nengine.Vec2(1, 2)` inside `_ready` is loadable and the Vec2 round-trips to Kotlin

## 4. Script loading — chunk-returns-table contract

- [x] 4.1 Implement `LuaScriptHost.load(path, bundle)`: read source via `bundle.read(path)`, compile via `globals.load(source, path, "t", globals)`, execute the chunk, capture the returned `LuaValue`
- [x] 4.2 Validate the returned value is a `LuaTable`; fail fast with mensagem nomeando o path se for `nil` ou outro tipo
- [x] 4.3 Read `extends` field (string only); fail fast se ausente ou não-string
- [x] 4.4 Resolve `extendsType` via `NodeRegistry.findBySimpleName(extendsString)`; fail fast com mensagem clara quando desconhecido (reaproveitar padrão de mensagem de `UnknownExtendsTypeException`)
- [x] 4.5 Cache the returned table in a `Map<String, LuaTable>` por path para reuso em `attach`
- [x] 4.6 Implement `ScriptData(path, extendsType, exports, signals)` data class (igual ao do Python) that backs the `Script` interface
- [x] 4.7 Tests: minimal load, missing return, non-table return, missing extends, unknown extends type — todos os cenários da spec "Lua chunk returns a table"

## 5. Exports discovery

- [x] 5.1 Implement `buildExports(table, path)`: read `table.get("exports")`, validate é `LuaTable`, iterate entries, build `ExportedProperty` per entry
- [x] 5.2 Para cada entry, read `type` string e `default` valor; mapear `"float"` → `Float::class`, `"int"` → `Int::class`, `"bool"` → `Boolean::class`, `"string"` → `String::class`, `"Vec2"` → `Vec2::class`, `"Color"` → `Color::class`, `"Rect"` → `Rect::class`, `"NodeRef"` → `NodeRef::class`, `"Key"` → `Key::class`
- [x] 5.3 Tipo desconhecido falha fast nomeando script, key, e tipo
- [x] 5.4 Convert `default` Lua values to Kotlin via helper `luaValueToKotlin(value, type)` (números → Float/Int conforme tipo; tables/userdata → unwrap host object)
- [x] 5.5 Tests: float export, int export, Vec2 export with `nengine.Vec2(...)` default, bool export, unsupported type fails

## 6. Signals discovery

- [x] 6.1 Implement `buildSignals(table, path)`: read `table.get("signals")`, validate é `LuaTable`, iterate entries, return `Map<String, SignalDeclaration>`
- [x] 6.2 Cross-validate against exports: same name in both fails fast
- [x] 6.3 Tests: signal declared, name collision with exports fails

## 7. Attach: userdata wrapper + metatable

- [x] 7.1 Implement `LuaScriptHost.attach(node, script)`: lookup the cached chunk table; create an instance `LuaTable` whose contents start as a copy/clone of the chunk table (hooks survive)
- [x] 7.2 Set instance metatable with `__index(self, key)` resolving in order: (1) script signals slots, (2) reflection-discovered `Signal<*>` fields on node, (3) `script.exports` entries, (4) `Node2D` properties (`position`/`rotation`/`scale`) when applicable, (5) public Node fields/methods via reflection
- [x] 7.3 Set `__newindex(self, key, value)` mirroring the same resolution order for writes
- [x] 7.4 Instantiate `Signal<Any?>` for each `script.signals` entry; store map on the instance for `__index` lookup and for `ScriptInstance.signals` exposure
- [x] 7.5 Index instance by host Node in `instanceByNode: MutableMap<Node, LuaTable>` para `nengine.script_of(node)` lookup
- [x] 7.6 Wire `nengine.script_of` para consultar `instanceByNode` (e devolver um bare wrapper para Nodes script-less; o bare wrapper SÓ resolve signal fields refletivos do Kotlin)
- [x] 7.7 Build `LuaScriptInstance(instance, script, signals)` implementando `ScriptInstance`
- [x] 7.8 Seed instance com defaults de cada export via `setExport` (igual ao Python)
- [x] 7.9 Tests: position/rotation/scale read & write round-trips, world() composes, signal per-instance independence

## 8. Hook dispatch

- [x] 8.1 Implementar `LuaScriptInstance.callHook(name, vararg args)`: fetch `instance.get(name)`, skip se `isnil()` ou não-`function`; call via `fn.invoke(varargsOf(instance, ...))`
- [x] 8.2 Map cada hook do `ScriptInstanceContract` para o nome Lua: `onEnter → _ready`, `onProcess → _process`, `onPhysicsProcess → _physics_process`, `onDraw → _draw`, `onExit → _exit_tree`, `onAreaEntered → _on_area_entered`, `onAreaExited → _on_area_exited`, `onBodyEntered → _on_body_entered`, `onBodyExited → _on_body_exited`
- [x] 8.3 Wrap chamada Kotlin args (Float, Renderer, Area2D, PhysicsBody2D) em `CoerceJavaToLua.coerce` antes do invoke
- [x] 8.4 Garantir que LuaError lançada de dentro de hook propaga para o caller Kotlin com mensagem incluindo path/linha
- [x] 8.5 Tests: _process called with correct dt, _draw called with renderer, missing hook is no-op, exception from _ready propagates

## 9. currentValue for round-trip

- [x] 9.1 Implementar `LuaScriptInstance.currentValue(name)`: validar contra `script.exports`; throw `IllegalArgumentException` nomeando name e path se desconhecido
- [x] 9.2 Read attribute via `instance.get(name)`; convert via `luaValueToKotlin(value, exportType)`; cair para `export.default` se `isnil`
- [x] 9.3 Tests: float export round-trip, Vec2 export round-trip, default fallback, unknown name throws

## 10. require via package.searchers

- [x] 10.1 Stash `BundleSource` recebido em `load` num field do host (último bundle source usado é o ativo); ou passar para o setup do `Globals` no momento certo
- [x] 10.2 Após `JsePlatform.standardGlobals()`, replace `globals.package_["searchers"]` por um `LuaTable` contendo: (1) o `preload` searcher original, (2) um `BundleSearcher` custom
- [x] 10.3 `BundleSearcher.call(modname)`: convert `"scripts.utils"` to `"scripts/utils.lua"` (replace `.` with `/`, append `.lua`); try `bundle.read(path)`; if succeeds, compile and return as `LuaValue`; if fails, return string descrevendo a falha (Lua semantics)
- [x] 10.4 Tests: require sibling module works, require missing fails with module name in error, require is memoized (executed once when called twice), filesystem searchers are gone

## 11. Reflection-discovered Signal fields

- [x] 11.1 Replicar lógica do Python (em `PythonScriptHost.discoverSignals`): varrer `node::class.memberProperties` por `Signal<*>` `val`s e expor cada um via `__index` da instância
- [x] 11.2 Cada field reflexivo retorna um proxy Lua com métodos `connect(handler)`, `disconnect(handler)` que rotam para o `Signal<T>` Kotlin
- [x] 11.3 Tratar `T == Unit` corretamente: handler Lua é chamado sem args quando o signal emite `Unit`
- [x] 11.4 Errors levantados pelo handler Lua propagam para `Signal.emit` caller
- [x] 11.5 Cache de proxies por (node, field) para que `disconnect(handler)` ache o mesmo proxy que `connect(handler)` usou
- [x] 11.6 Tests: Timer.timeout connect (Unit signal), disconnect removes handler, exception propagates

## 12. LuaCATS stubs

- [x] 12.1 Criar diretório `engine-bundle-lua/src/main/resources/stubs/engine/`
- [x] 12.2 Escrever `nengine.lua` declarando `---@class nengine` com todos os campos (factories, enums, types, script_of)
- [x] 12.3 Escrever stubs por tipo: `node.lua`, `node2d.lua` (com position/rotation/scale/world()/tree), `camera2d.lua`, `label.lua`, `colorrect.lua`, `circle2d.lua`, `line2d.lua`, `polygon2d.lua`, `vec2.lua`, `color.lua`, `rect.lua`, `transform.lua`, `noderef.lua`, `key.lua`, `mousebutton.lua`, `signal.lua` (connect/disconnect/emit)
- [x] 12.4 Escrever stubs de colisão: `collisionobject2d.lua` (com area_entered/area_exited/body_entered/body_exited signals), `area2d.lua`, `physicsbody2d.lua`, `staticbody2d.lua`, `characterbody2d.lua`, `rigidbody2d.lua`, `collisionshape2d.lua`, `shape2d.lua`, `rectangleshape2d.lua`, `circleshape2d.lua`
- [x] 12.5 Escrever `timer.lua` com waitTime/autostart/oneShot/processCallback/timeLeft/isStopped/timeout/start/stop, e `timermode.lua` com PHYSICS/IDLE
- [x] 12.6 Escrever `renderer.lua` e `input.lua`
- [x] 12.7 Verificar (via grep) que nenhum stub declara `BoxCollider`, `Collider`, `Shape` (legacy removed), `on_enter`, `on_update`, `on_render`, `on_exit`, `on_collide`

## 13. Tic-tac-toe migration

- [x] 13.1 Atualizar `games/tictactoe/build.gradle.kts`: trocar `projects.engineBundlePython` por `projects.engineBundleLua`
- [x] 13.2 Atualizar `games/tictactoe/src/main/kotlin/.../Main.kt`: substituir `PythonScriptHost.create()` por `LuaScriptHost.create()`, remover import de `PythonScriptHost`
- [x] 13.3 Renomear `scene.json` field `script` de `"scripts/board.py"` para `"scripts/board.lua"`
- [x] 13.4 Criar `games/tictactoe/src/main/resources/tictactoe/scripts/board.lua` portando a lógica de `board.py`:
  - Retornar tabela com `extends = "Node"`, exports (se houver — `cellSize` se aplicável), hooks `_ready`, `_process`, `_draw`
  - `_ready`: inicializar `self._cells = { 0, 0, 0, 0, 0, 0, 0, 0, 0 }`, `self._turn = "X"`, lookup do `status` Label
  - `_process`: ler `self.tree.input.pointerPosition`, projetar via `self.tree:screenToWorld(...)`, hit-test cells, processar clique (move ou reset), atualizar status text
  - `_draw`: desenhar X (duas `drawLine`), O (uma `drawCircle` não-preenchida), linha vencedora se houver, ghost no hover
- [x] 13.5 Deletar `games/tictactoe/src/main/resources/tictactoe/scripts/board.py`
- [x] 13.6 Verificar `./gradlew :games:tictactoe:run` abre janela responsiva
- [x] 13.7 Smoke test manual: jogada X, jogada O, vitória, empate, reset funcionam end-to-end

## 14. Documentation

- [x] 14.1 Atualizar `CLAUDE.md` seção "Module Structure & How to Run":
  - Adicionar `:engine-bundle-lua` ao mapa de módulos
  - Atualizar comando de rodar TTT (continua o mesmo `./gradlew :games:tictactoe:run`, mas a explicação muda)
  - Atualizar texto descrevendo TTT como "sentinela do segundo backend de render **e** do segundo backend de scripting"
- [x] 14.2 Adicionar sub-seção "Scripting contract (Lua)" em paralelo à "Scripting contract (Python)" com o contrato chunk-returns-table, hooks, nengine.*, signals
- [x] 14.3 Adicionar seção sobre stubs LuaCATS e como apontar `sumneko-lua` (via `.luarc.json` com `workspace.library`)
- [x] 14.4 Atualizar `ROADMAP.md` marcando "Lua como segundo backend de scripting" como feito (item ou linha do tempo)

## 15. Validation

- [x] 15.1 `./gradlew :engine:build :engine-bundle:build :engine-bundle-lua:build` passa sem warnings novos
- [x] 15.2 `./gradlew :engine-bundle-lua:test` passa (todos os testes adicionados em 2–11)
- [x] 15.3 `./gradlew :games:tictactoe:run` abre janela e o jogo está jogável
- [x] 15.4 `./gradlew :games:pong:run` continua funcional (regressão Python+Skiko)
- [x] 15.5 `openspec validate add-lua-scripting --strict` passa
- [x] 15.6 Grep cross-projeto: nenhum módulo fora de `:engine-bundle-lua` importa `org.luaj.*`
