## ADDED Requirements

### Requirement: GameHost SPI

The engine SHALL define a `GameHost` interface that represents the host of execution of a game: it owns a window/surface, drives the per-frame pulse, wires `Input` events from the platform into the engine, and runs the game loop until the host is closed. The interface MUST expose a single `run(scene: Scene, config: GameConfig)` operation. The operation MUST be blocking: it returns only after the host's window/surface has been closed by the user or by code. The interface MUST NOT expose backend-specific types in its method signatures; both inputs (`Scene`, `GameConfig`) and the return type live in `:engine`. The interface MUST be implementable without reflection or service loaders.

#### Scenario: Engine module reads no backend type to declare GameHost

- **WHEN** the `:engine` module is compiled
- **THEN** `GameHost` is declared with parameters whose types come only from `:engine`
- **AND** no import in the file declaring `GameHost` begins with `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, or `javax.swing.*`

#### Scenario: run blocks until the host is closed

- **WHEN** code calls `host.run(scene, config)` and the host opens a window
- **THEN** the call does not return while the window remains open
- **AND** the call returns after the window is closed by the user or by code

#### Scenario: Compose and Skiko hosts implement GameHost

- **WHEN** code in `:games:tictactoe` instantiates `ComposeHost()` from `:engine-compose`
- **THEN** the result is assignable to `GameHost`
- **AND** when code in `:games:pong` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is assignable to `GameHost`

### Requirement: GameConfig host configuration

The engine SHALL provide a `data class GameConfig` carrying the configuration a `GameHost` needs to open its window and behave consistently across backends. `GameConfig` MUST expose at minimum a `title: String`, a `width: Int`, a `height: Int`, a `toggleFpsKey: Key`, and a `toggleCollidersKey: Key`. All fields MUST have sensible defaults so that `GameConfig()` is a valid call site. The default values for `toggleFpsKey` and `toggleCollidersKey` MUST be `Key.F1` and `Key.F2` respectively, so that any host implementation honors the historical F1/F2 affordance without per-game wiring. `GameConfig` MUST be a `data class` so equality, `copy()`, and component destructuring are available.

#### Scenario: Default constructor is valid

- **WHEN** code calls `GameConfig()`
- **THEN** the result is a valid `GameConfig`
- **AND** `title` is a non-empty string
- **AND** `width` and `height` are positive integers

#### Scenario: Default toggle keys are F1 and F2

- **WHEN** code reads `GameConfig().toggleFpsKey` and `GameConfig().toggleCollidersKey`
- **THEN** the results are `Key.F1` and `Key.F2` respectively

#### Scenario: Toggle keys are configurable

- **WHEN** code calls `GameConfig(toggleFpsKey = Key.DIGIT_9, toggleCollidersKey = Key.DIGIT_0)`
- **THEN** the result reports `Key.DIGIT_9` and `Key.DIGIT_0` for the respective fields
- **AND** any `GameHost.run(scene, this)` honors those keys instead of F1/F2

### Requirement: Toggle keys flip debug flags through the host

Every `GameHost` implementation SHALL, on each tick, observe `Input.wasKeyPressed(config.toggleFpsKey)` and `Input.wasKeyPressed(config.toggleCollidersKey)` and toggle `Debug.showFps` / `Debug.colliderVisualization` respectively when a press is observed. This responsibility lives in the host so that game `Main.kt` files do not need to wire keyboard handlers outside the engine to control debug overlays.

#### Scenario: Pressing the configured FPS toggle flips Debug.showFps

- **WHEN** the user presses the key configured as `toggleFpsKey` while a `GameHost` is running a scene
- **THEN** `Debug.showFps` is flipped to its negation by the time the next frame is rendered
- **AND** the next frame either shows or hides the FPS overlay accordingly

#### Scenario: Pressing the configured colliders toggle flips Debug.colliderVisualization

- **WHEN** the user presses the key configured as `toggleCollidersKey` while a `GameHost` is running a scene
- **THEN** `Debug.colliderVisualization` is flipped to its negation by the time the next frame is rendered

#### Scenario: Toggles never live in game code

- **WHEN** any `Main.kt` under `:games:` is inspected after this change
- **THEN** no file installs a keyboard handler outside the engine for the purpose of toggling `Debug.showFps` or `Debug.colliderVisualization`
- **AND** game code relies on the host to perform those toggles
