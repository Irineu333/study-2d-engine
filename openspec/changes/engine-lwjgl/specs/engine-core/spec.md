## MODIFIED Requirements

### Requirement: GameHost SPI

The engine SHALL define a `GameHost` interface that represents the host of execution of a game: it owns a window/surface, drives the per-frame pulse, wires `Input` events from the platform into the engine, and runs the game loop until the host is closed. The interface MUST expose a single `run(tree: SceneTree, config: GameConfig)` operation. The operation MUST be blocking: it returns only after the host's window/surface has been closed by the user or by code. The interface MUST NOT expose backend-specific types in its method signatures; both inputs (`SceneTree`, `GameConfig`) and the return type live in `:engine`. The interface MUST be implementable without reflection or service loaders.

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

### Requirement: Engine module has zero UI framework dependency

The `:engine` Gradle module SHALL declare no dependency on any UI or render framework artifact, directly or transitively. The prohibited list includes at minimum: `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, `org.lwjgl.*`, AWT/Swing types (`java.awt.*`, `javax.swing.*`) beyond what the standard library guarantees, and any future render backend (e.g. Vulkan bindings, WebGPU). This invariant SHALL be enforced by the module's `build.gradle.kts` and verified during code review. Backend-specific types only appear in their respective backend modules: `:engine-skiko` (Skia/Skiko, default backend) and `:engine-lwjgl` (NanoVG/GLFW/OpenGL via LWJGL, second backend).

#### Scenario: Adding a Compose dependency to :engine is rejected

- **WHEN** a contributor adds `androidx.compose.foundation` or any `org.jetbrains.compose.*` artifact to `:engine`'s dependencies
- **THEN** code review blocks the change
- **AND** the contributor is directed to use the `Renderer`/`Input`/`GameHost` SPI in a separate backend module

#### Scenario: Adding a Skiko dependency to :engine is rejected

- **WHEN** a contributor adds `org.jetbrains.skiko:skiko-awt` or any `org.jetbrains.skia.*` artifact to `:engine`'s dependencies
- **THEN** code review blocks the change
- **AND** the contributor is directed to use the `Renderer`/`Input`/`GameHost` SPI; Skiko-specific code lives in `:engine-skiko`

#### Scenario: Adding an LWJGL dependency to :engine is rejected

- **WHEN** a contributor adds `org.lwjgl:lwjgl` or any `org.lwjgl.*` artifact to `:engine`'s dependencies
- **THEN** code review blocks the change
- **AND** the contributor is directed to use the `Renderer`/`Input`/`GameHost` SPI; LWJGL-specific code lives in `:engine-lwjgl`
