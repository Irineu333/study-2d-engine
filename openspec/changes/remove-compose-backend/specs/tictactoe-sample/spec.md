## MODIFIED Requirements

### Requirement: Tic-tac-toe is an executable standalone module

The project SHALL provide a `:games:tictactoe` module that depends on `:engine`, `:engine-skiko`, `:engine-bundle`, and `:engine-bundle-lua`, and contains a `main()` entry point that:

1. Constructs a `LuaScriptHost` via `LuaScriptHost.create()`.
2. Loads the bundle via `BundleLoader.fromResources("tictactoe", scripting = lua)`, which returns the detached root `Node`.
3. Wraps the root in `SceneTree(root = ...)` and passes the resulting `SceneTree` to `SkikoHost().run(tree, config)`.

The module MUST be runnable via `./gradlew :games:tictactoe:run`. The module MUST NOT depend on any other game module. The module MUST NOT depend on `:engine-bundle-python` (the migration to Lua removes the previous Python dependency). The module MUST NOT depend on `:engine-compose` (that module is removed in this change).

#### Scenario: Tic-tac-toe runs from Gradle

- **WHEN** a developer runs `./gradlew :games:tictactoe:run` from the project root
- **THEN** a desktop window opens displaying the tic-tac-toe scene
- **AND** the game is responsive to mouse input

#### Scenario: Main.kt is a thin wiring entry point

- **WHEN** `games/tictactoe/src/main/kotlin/.../Main.kt` is inspected
- **THEN** `main()` calls `LuaScriptHost.create()`, then `BundleLoader.fromResources("tictactoe", scripting = lua)`, then wraps the result in `SceneTree(root = ...)` and passes the tree to `SkikoHost().run(...)`
- **AND** `main()` does not import any game-specific class (no `Board`, `TicTacToeRoot`, `StatusText`, `Mark`)
- **AND** `main()` does not import `PythonScriptHost`
- **AND** `main()` does not import `ComposeHost` or any symbol from `com.neoutils.engine.compose.*`

#### Scenario: Build depends on Skiko backend and Lua bundle module

- **WHEN** `games/tictactoe/build.gradle.kts` is inspected
- **THEN** the dependencies include `projects.engineBundle` and `projects.engineBundleLua`
- **AND** the dependencies include `projects.engineSkiko` (Skiko is the active backend)
- **AND** the dependencies do NOT include `projects.engineCompose`
- **AND** the dependencies do NOT include `projects.engineBundlePython`
- **AND** the `plugins {}` block does NOT include `composeMultiplatform` or `composeCompiler`

### Requirement: Tic-tac-toe scene composition

The Tic-tac-toe scene SHALL be loaded from `src/main/resources/tictactoe/scene.json`, with the orchestrator logic in `src/main/resources/tictactoe/scripts/board.lua`. The scene MUST include:

- A root of type `com.neoutils.engine.scene.Node` whose `script` is `scripts/board.lua`.
- A `Camera2D` child with `current: true` and `bounds = Rect(Vec2.ZERO, Vec2(600f, 600f))`.
- Four `Line2D` children forming the grid (two vertical, two horizontal) declared with absolute coordinates.
- A `Label` child named `status` holding the current status text.

The previously-existing Kotlin classes `Board`, `Mark`, `StatusText`, and `TicTacToeRoot` MUST NOT exist anywhere under `games/tictactoe/src/main/kotlin/`. The previous Python script `scripts/board.py` MUST NOT exist anywhere under `games/tictactoe/src/main/resources/tictactoe/scripts/`. The bundle (scene.json + scripts/) MUST NOT change as part of the Compose→Skiko host migration — the proof point of this change is that the same bundle runs identically in the new host.

#### Scenario: Bundle directory layout

- **WHEN** `games/tictactoe/src/main/resources/tictactoe/` is inspected
- **THEN** the directory contains `scene.json` at its root
- **AND** the directory contains `scripts/board.lua`
- **AND** the directory does NOT contain `scripts/board.py`

#### Scenario: Scene root is a Node with Lua script attached

- **WHEN** `scene.json` is inspected
- **THEN** the `root.type` is `"com.neoutils.engine.scene.Node"`
- **AND** the `root.script` is `"scripts/board.lua"`

#### Scenario: Grid is declarative via Line2D nodes

- **WHEN** `scene.json` is inspected
- **THEN** the root has at least four children of type `com.neoutils.engine.scene.Line2D` forming a 3×3 grid
- **AND** the script `board.lua` does NOT define the grid lines itself

#### Scenario: Status text is declarative Label

- **WHEN** `scene.json` is inspected
- **THEN** the root has a child of type `com.neoutils.engine.scene.Label` named `status`

#### Scenario: No game-specific Kotlin types exist

- **WHEN** `games/tictactoe/src/main/kotlin/` is inspected after this change
- **THEN** no file named `Board.kt`, `Mark.kt`, `StatusText.kt`, or `TicTacToeRoot.kt` exists
- **AND** the only `.kt` file present is `Main.kt`

#### Scenario: Bundle survives host migration unchanged

- **WHEN** the bundle files at `games/tictactoe/src/main/resources/tictactoe/` are compared against the previous Compose-hosted version
- **THEN** `scene.json` is byte-equal
- **AND** `scripts/board.lua` is byte-equal
