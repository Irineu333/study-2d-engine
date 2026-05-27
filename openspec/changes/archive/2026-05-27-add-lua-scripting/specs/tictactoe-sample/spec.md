## MODIFIED Requirements

### Requirement: Tic-tac-toe is an executable standalone module

The project SHALL provide a `:games:tictactoe` module that depends on `:engine`, `:engine-compose`, `:engine-bundle`, and `:engine-bundle-lua`, and contains a `main()` entry point that:

1. Constructs a `LuaScriptHost` via `LuaScriptHost.create()`.
2. Loads the bundle via `BundleLoader.fromResources("tictactoe", scripting = lua)`, which returns the detached root `Node`.
3. Wraps the root in `SceneTree(root = ...)` and passes the resulting `SceneTree` to `ComposeHost().run(tree, config)`.

The module MUST be runnable via `./gradlew :games:tictactoe:run`. The module MUST NOT depend on any other game module. The module MUST NOT depend on `:engine-bundle-python` (the migration to Lua removes the previous Python dependency).

#### Scenario: Tic-tac-toe runs from Gradle

- **WHEN** a developer runs `./gradlew :games:tictactoe:run` from the project root
- **THEN** a desktop window opens displaying the tic-tac-toe scene
- **AND** the game is responsive to mouse input

#### Scenario: Main.kt is a thin wiring entry point

- **WHEN** `games/tictactoe/src/main/kotlin/.../Main.kt` is inspected
- **THEN** `main()` calls `LuaScriptHost.create()`, then `BundleLoader.fromResources("tictactoe", scripting = lua)`, then wraps the result in `SceneTree(root = ...)` and passes the tree to `ComposeHost().run(...)`
- **AND** `main()` does not import any game-specific class (no `Board`, `TicTacToeRoot`, `StatusText`, `Mark`)
- **AND** `main()` does not import `PythonScriptHost`

#### Scenario: Build depends on Lua bundle module

- **WHEN** `games/tictactoe/build.gradle.kts` is inspected
- **THEN** the dependencies include `projects.engineBundle` and `projects.engineBundleLua`
- **AND** the dependencies include `projects.engineCompose` (Compose remains the backend)
- **AND** the dependencies do NOT include `projects.engineBundlePython`

### Requirement: Tic-tac-toe scene composition

The Tic-tac-toe scene SHALL be loaded from `src/main/resources/tictactoe/scene.json`, with the orchestrator logic in `src/main/resources/tictactoe/scripts/board.lua`. The scene MUST include:

- A root of type `com.neoutils.engine.scene.Node` whose `script` is `scripts/board.lua`.
- A `Camera2D` child with `current: true` and `bounds = Rect(Vec2.ZERO, Vec2(600f, 600f))`.
- Four `Line2D` children forming the grid (two vertical, two horizontal) declared with absolute coordinates.
- A `Label` child named `status` holding the current status text.

The previously-existing Kotlin classes `Board`, `Mark`, `StatusText`, and `TicTacToeRoot` MUST NOT exist anywhere under `games/tictactoe/src/main/kotlin/`. The previous Python script `scripts/board.py` MUST NOT exist anywhere under `games/tictactoe/src/main/resources/tictactoe/scripts/`.

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

### Requirement: Game state and rendering live in board.lua

The Lua script `board.lua` SHALL be the single source of truth for game state: the 9-cell array, current player, winner, draw flag, winning line. Cell hit-testing, move placement, win detection, and reset SHALL all live in `board.lua`. The visual rendering of marks (X / O) and the winning line SHALL be issued from `board.lua._draw(self, renderer)` using `renderer:drawLine(...)` and `renderer:drawCircle(...)`. The status `Label` SHALL be updated by `board.lua` (writing to its `text` field via `NodeRef` resolution or `findChild` lookup).

#### Scenario: board.lua returns a table with extends = "Node"

- **WHEN** `scripts/board.lua` is inspected
- **THEN** the chunk ends with `return { extends = "Node", ... }`
- **AND** the returned table's `extends` field equals the string `"Node"`

#### Scenario: board.lua owns game state

- **WHEN** `scripts/board.lua` is inspected
- **THEN** the table (or the userdata's internal state populated in `_ready`) holds the 9-cell state, the current player, the winner, the draw flag, and the winning line as internal attributes
- **AND** no other script file is required for the game to function

#### Scenario: board.lua renders marks via _draw

- **WHEN** `scripts/board.lua` is inspected
- **THEN** the returned table defines `_draw = function(self, renderer) ... end` that draws each placed mark (X as two crossed `drawLine` calls, O as a `drawCircle` with `filled = false`)
- **AND** the winning line, when present, is also drawn from `_draw`

#### Scenario: Status text is updated via Node lookup

- **WHEN** `scripts/board.lua` is inspected
- **THEN** the script resolves the `status` Label (via `NodeRef`, `findChild`, or an export) and updates `<label>.text` to reflect game state on every state transition (player change, win, draw, reset)

### Requirement: Click input drives moves with hit-testing

The script `board.lua` SHALL detect a left mouse click whose pointer position falls inside an empty cell during an ongoing partida and place the current player's mark in that cell. Clicks outside any cell, or inside an already-occupied cell, MUST NOT mutate the board. Detection MUST be done by reading `self.tree.input.pointerPosition` (projected to world coordinates via `self.tree:screenToWorld(...)`) and `self.tree.input:wasMouseClicked(nengine.MouseButton.Left)` from inside `_process(self, dt)`.

#### Scenario: Click in empty cell places the current mark

- **GIVEN** an ongoing partida with player `X` to move and cell `(row=1, col=1)` empty
- **WHEN** the user left-clicks at a pointer position inside the cell `(1,1)` rectangle
- **THEN** cell `(1,1)` becomes `X`
- **AND** the turn advances to `O`

#### Scenario: Click in occupied cell is ignored

- **GIVEN** an ongoing partida with cell `(0,0)` already containing `O`
- **WHEN** the user left-clicks inside the cell `(0,0)` rectangle
- **THEN** cell `(0,0)` remains `O`
- **AND** the current player does not change

#### Scenario: Click outside the board is ignored during play

- **GIVEN** an ongoing partida
- **WHEN** the user left-clicks at a pointer position outside every cell rectangle
- **THEN** no cell is mutated
- **AND** the current player does not change
