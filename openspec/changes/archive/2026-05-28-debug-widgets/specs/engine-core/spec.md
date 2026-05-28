## MODIFIED Requirements

### Requirement: GameHost SPI

The engine SHALL define a `GameHost` interface that represents the host of execution of a game: it owns a window/surface, drives the per-frame pulse, wires `Input` events from the platform into the engine, and runs the game loop until the host is closed. The interface MUST expose a single `run(tree: SceneTree, config: GameConfig)` operation. The operation MUST be blocking: it returns only after the host's window/surface has been closed by the user or by code. The interface MUST NOT expose backend-specific types in its method signatures; both inputs (`SceneTree`, `GameConfig`) and the return type live in `:engine`. The interface MUST be implementable without reflection or service loaders.

`GameHost` implementations SHALL NOT issue any `renderer.draw*` calls outside of `tree.render(renderer)`. All visual output, including debug overlays, SHALL be produced by `SceneTree.render` walking the scene graph. `GameHost` implementations SHALL NOT poll input keys for the purpose of toggling debug visualization — the engine performs that polling internally via `DebugToggleNode`. The host's only debug-related responsibility SHALL be to set `tree.debugHudKey = config.debugHudKey` during startup so the engine knows which key opens the HUD; the host SHALL NOT read or write any other field of `tree.debug` on a per-frame basis.

#### Scenario: Engine module reads no backend type to declare GameHost

- **WHEN** the `:engine` module is compiled
- **THEN** `GameHost` is declared with parameters whose types come only from `:engine`
- **AND** no import in the file declaring `GameHost` begins with `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, `javax.swing.*`, or `org.lwjgl.*`

#### Scenario: run blocks until the host is closed

- **WHEN** code calls `host.run(tree, config)` and the host opens a window
- **THEN** the call does not return while the window remains open
- **AND** the call returns after the window is closed by the user or by code

#### Scenario: SkikoHost implements GameHost

- **WHEN** code in `:games:pong` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is assignable to `GameHost`
- **AND** when code in `:games:tictactoe` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is also assignable to `GameHost`
- **AND** every shipped game in the project (Pong, Demos, Hello-World, Tic Tac Toe) obtains its default `GameHost` implementation from `:engine-skiko`

#### Scenario: LwjglHost implements GameHost

- **WHEN** code in `:games:demos` (alternate entrypoint `MainLwjgl.kt`) instantiates `LwjglHost()` from `:engine-lwjgl`
- **THEN** the result is assignable to `GameHost`
- **AND** `:engine-lwjgl` is recognized as the second active render backend after `:engine-skiko`

#### Scenario: GameHost does not draw outside SceneTree.render

- **WHEN** the source of any `GameHost` implementation is inspected for direct uses of `renderer.drawText`, `renderer.drawRect`, `renderer.drawLine`, `renderer.drawCircle`, or `renderer.drawPolygon`
- **THEN** every such call SHALL occur transitively inside a `tree.render(renderer)` invocation, not in the host's frame body before or after the render call.

#### Scenario: GameHost does not poll debug toggle keys

- **WHEN** the source of any `GameHost` implementation is grep'd for `input.wasKeyPressed(`
- **THEN** the only matches SHALL be unrelated to debug visualization (e.g. user-defined game keys passed through via `Input`)
- **AND** no host file SHALL reference `FpsCounter`, `MomentumOverlay`, `tree.debug.show*`, or `tree.debug.current*`

#### Scenario: GameHost sets debugHudKey once during startup

- **WHEN** a `GameHost` implementation is observed across a full `run(tree, config)` invocation
- **THEN** exactly one assignment of `tree.debugHudKey` from `config.debugHudKey` SHALL be observed
- **AND** the assignment SHALL precede the first call to `loop.tick(...)`

### Requirement: GameConfig host configuration

The engine SHALL provide a `data class GameConfig` carrying the configuration a `GameHost` needs to open its window and behave consistently across backends. `GameConfig` MUST expose at minimum a `title: String`, a `width: Int`, a `height: Int`, a `debugHudKey: Key`, and a `physicsHz: Int`. All fields MUST have sensible defaults so that `GameConfig()` is a valid call site. The default value for `debugHudKey` MUST be `Key.F1` so that any host implementation honors a single conventional affordance for opening the debug HUD without per-game wiring. `GameConfig` MUST be a `data class` so equality, `copy()`, and component destructuring are available.

`GameConfig` SHALL NOT carry per-widget toggle keys (such as `toggleFpsKey`, `toggleCollidersKey`, `toggleMomentumOverlayKey`). Individual widgets are toggled via the HUD's clickable rows, not via per-flag keys.

#### Scenario: Default constructor is valid

- **WHEN** code calls `GameConfig()`
- **THEN** the result is a valid `GameConfig`
- **AND** `title` is a non-empty string
- **AND** `width` and `height` are positive integers
- **AND** `debugHudKey` is `Key.F1`

#### Scenario: debugHudKey is configurable

- **WHEN** code calls `GameConfig(debugHudKey = Key.GRAVE)`
- **THEN** the result reports `Key.GRAVE` for `debugHudKey`
- **AND** any `GameHost.run(tree, this)` SHALL set `tree.debugHudKey = Key.GRAVE` during startup

#### Scenario: Legacy toggle key fields do not exist

- **WHEN** code attempts to call `GameConfig(toggleFpsKey = Key.F1)` or read `GameConfig().toggleCollidersKey`
- **THEN** the call SHALL fail to compile because no such fields exist on `GameConfig`

### Requirement: Scene rendering decoupled from DX surface

The `SceneTree.render(renderer: Renderer)` traversal SHALL NOT depend on or consult any symbol from `com.neoutils.engine.dx.*`. Visualization of debug artifacts (collider bounds, FPS overlay, etc.) SHALL be produced by nodes registered via `tree.debug.register(...)` and rendered as part of the standard scene graph walk (world pass for `WorldDebugWidget`, UI pass for `ScreenDebugWidget`). The `:engine.tree` package MUST compile without `:engine.dx` being on the classpath — only the `:engine.debug` package may be referenced by `SceneTree` for the purpose of instantiating `DebugRegistry` and auto-inserting `DebugLayer`.

#### Scenario: SceneTree.kt has no import from engine.dx

- **WHEN** the source file `engine/src/main/kotlin/com/neoutils/engine/tree/SceneTree.kt` is parsed
- **THEN** it contains no import statement beginning with `com.neoutils.engine.dx`

#### Scenario: SceneTree.kt may import from engine.debug

- **WHEN** the source file `engine/src/main/kotlin/com/neoutils/engine/tree/SceneTree.kt` is parsed
- **THEN** it MAY contain imports from `com.neoutils.engine.debug.*` (for `DebugRegistry`, `DebugLayer`)
- **AND** these imports SHALL be the only debug-related imports it carries

#### Scenario: SceneTree.render does not draw collider bounds

- **WHEN** `tree.render(renderer)` is invoked
- **THEN** no `Renderer.drawRect(_, _, filled = false)` call is issued by `SceneTree` itself for the purpose of debug visualization
- **AND** the only debug draw calls during the traversal originate from `DebugWidget.drawDebug` overrides reached via the standard scene-graph walk

## ADDED Requirements

### Requirement: SceneTree exposes debugHudKey

`SceneTree` SHALL expose a mutable `var debugHudKey: Key` property (default `Key.F1`). `GameHost` implementations SHALL set this property from `GameConfig.debugHudKey` during startup, before the first `loop.tick(...)`. The engine's internal `DebugToggleNode` (inside the auto-inserted `DebugLayer`) SHALL read this property each tick when checking for the HUD toggle.

#### Scenario: Default debugHudKey is F1

- **WHEN** a `SceneTree` is constructed
- **THEN** `tree.debugHudKey` SHALL equal `Key.F1`

#### Scenario: Host writes debugHudKey from config

- **GIVEN** a `GameConfig(debugHudKey = Key.GRAVE)`
- **WHEN** a `GameHost.run(tree, config)` invocation begins
- **THEN** `tree.debugHudKey` SHALL equal `Key.GRAVE` by the time the first frame is processed

## REMOVED Requirements

### Requirement: Toggle keys flip debug flags through the host

**Reason**: Per-widget toggle keys are eliminated. The engine's internal `DebugToggleNode` polls a single `debugHudKey` and toggles the HUD; individual widgets are toggled via the HUD's clickable rows. Hosts no longer poll input for debug purposes.

**Migration**: Hosts strip the three `if (input.wasKeyPressed(config.toggleXxxKey))` blocks. Game code that previously relied on F1/F2/F3 muscle memory must learn the new affordance: F1 opens a HUD that contains FPS/Colliders/Momentum/etc. as checkbox rows. The `Scenario: Toggles never live in game code` clause is preserved in spirit by the engine-driven `DebugToggleNode` (no game code wires keyboard handlers for debug).
