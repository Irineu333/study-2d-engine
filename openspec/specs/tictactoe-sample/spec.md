# tictactoe-sample Specification

## Purpose

Jogo da Velha (tic-tac-toe) humano vs humano em `:games:tictactoe`, exercitando entrada discreta por mouse, hit-test via `Rect.contains`, desenho de linhas via `Renderer.drawLine`, e medição de texto via `Renderer.measureText`. Toda a lógica de gameplay (estado das 9 células, jogador atual, vencedor, empate, linha vencedora, ghost) mora num único script Lua `scripts/board.lua` (chunk retorna `{ extends = "Node", ... }`); o root da cena é declarado em `scene.json` com `Camera2D` + grade de `Line2D` + `Label` para status. As nove células NÃO são nós do scene graph. O módulo serve como prova viva de que `ScriptHost` é polimórfico (TTT usa `LuaScriptHost`, Pong usa `PythonScriptHost`) sob o mesmo backend de render (`SkikoHost`), todos consumidos pelo mesmo `BundleLoader`.

## Requirements

### Requirement: Tic-tac-toe is an executable standalone module

The project SHALL provide a `:games:tictactoe` module that depends on `:engine`, `:engine-skiko`, `:engine-bundle`, and `:engine-bundle-lua`, and contains a `main()` entry point that:

1. Constructs a `LuaScriptHost` via `LuaScriptHost.create()`.
2. Loads the bundle via `BundleLoader.fromResources("tictactoe", scripting = lua)`, which returns the detached root `Node`.
3. Wraps the root in `SceneTree(root = ...)` and passes the resulting `SceneTree` to `SkikoHost().run(tree, config)`.

The module MUST be runnable via `./gradlew :games:tictactoe:run`. The module MUST NOT depend on any other game module. The module MUST NOT depend on `:engine-bundle-python` (the migration to Lua removes the previous Python dependency). The module MUST NOT depend on `:engine-compose` (that module is removed).

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
- A **`CanvasLayer`** child named `Hud` containing a `Label` named `status` holding the current status text. `status` lives in screen-space, with `transform.position` authored in screen pixels (e.g. centered horizontally near the top edge).

The previously-existing Kotlin classes `Board`, `Mark`, `StatusText`, and `TicTacToeRoot` MUST NOT exist anywhere under `games/tictactoe/src/main/kotlin/`. The previous Python script `scripts/board.py` MUST NOT exist anywhere under `games/tictactoe/src/main/resources/tictactoe/scripts/`. After the `ui-foundation` migration the bundle MAY differ from previous versions only by the documented diff (wrapping the `status` Label in a `CanvasLayer` named `Hud`).

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

#### Scenario: Status text is declarative Label inside CanvasLayer

- **WHEN** `scene.json` is inspected
- **THEN** the root has a child of type `com.neoutils.engine.scene.CanvasLayer` named `Hud`
- **AND** that `CanvasLayer` has a child of type `com.neoutils.engine.scene.Label` named `status`
- **AND** `status.text` is updated by `board.lua` (writing through `NodeRef` resolution or `findChild` lookup; the new path traverses through `Hud`)

#### Scenario: No game-specific Kotlin types exist

- **WHEN** `games/tictactoe/src/main/kotlin/` is inspected after this change
- **THEN** no file named `Board.kt`, `Mark.kt`, `StatusText.kt`, or `TicTacToeRoot.kt` exists
- **AND** the only `.kt` file present is `Main.kt`

#### Scenario: Status renders in screen-space regardless of Camera2D

- **WHEN** the game runs and the `Camera2D` projects its `bounds = Rect(Vec2.ZERO, Vec2(600f, 600f))` onto a surface that may not match 600×600 (e.g. window resized)
- **THEN** the `status` Label renders at constant screen-pixel coordinates inside the `Hud` `CanvasLayer`, NOT scaled or letterboxed by the camera projection

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

### Requirement: Turn-based gameplay with two human players

The game SHALL start with player `X` to move. After a legal move, the turn SHALL alternate to the other player. The first move of every new partida MUST be `X`. The player identity SHALL be represented in `board.py` as the strings `"X"` and `"O"` (no Kotlin `Mark` enum).

#### Scenario: First move is X

- **WHEN** a new partida starts
- **THEN** the status text indicates that `X` is to play
- **AND** the first legal click places an `X` on the board

#### Scenario: Turn alternates after a legal move

- **WHEN** player `X` places a mark in an empty cell during X's turn
- **THEN** the status text indicates that `O` is to play
- **AND** the next legal click places an `O`

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

### Requirement: Hover ghost shows the next move

The script `board.py` SHALL render a faded representation of the current player's mark in the empty cell currently under the pointer while a partida is ongoing. The ghost MUST disappear when the pointer is outside any cell, when the cell is occupied, or when the partida has ended. The ghost SHALL be drawn from `_draw` with reduced alpha (approximately `0.3`).

#### Scenario: Ghost appears in empty hovered cell during play

- **GIVEN** an ongoing partida with player `X` to move and cell `(0,2)` empty
- **WHEN** the pointer is inside the cell `(0,2)` rectangle
- **THEN** a faded `X` is rendered in cell `(0,2)`

#### Scenario: Ghost does not appear in occupied cell

- **GIVEN** an ongoing partida and cell `(0,2)` already containing `O`
- **WHEN** the pointer is inside cell `(0,2)`
- **THEN** no ghost is rendered in that cell

#### Scenario: Ghost does not appear after game over

- **GIVEN** a finished partida (win or draw)
- **WHEN** the pointer is inside an empty cell
- **THEN** no ghost is rendered

### Requirement: Win and draw detection ends the partida

`board.py` SHALL detect end of partida after every legal move. A partida MUST end with a winner when one player occupies all three cells of any row, column, or main diagonal. A partida MUST end as a draw when all nine cells are occupied and no winner exists. Once a partida is finished, further moves MUST NOT be accepted until a reset occurs.

#### Scenario: Three in a row triggers a win

- **WHEN** player `X` places marks completing the top row `(0,0)`, `(0,1)`, `(0,2)`
- **THEN** the partida ends with `X` as the winner
- **AND** the status text indicates `X` has won

#### Scenario: Full board without a line is a draw

- **WHEN** all nine cells are occupied and no row, column, or diagonal contains three identical marks
- **THEN** the partida ends as a draw
- **AND** the status text indicates a draw

#### Scenario: Moves after end are rejected

- **GIVEN** a finished partida
- **WHEN** the user left-clicks an empty cell
- **THEN** no mark is placed in that cell (the click is consumed as a reset trigger only)

### Requirement: Winning line is highlighted

When a partida ends with a winner, `board.py` SHALL draw a visible line segment from `_draw` connecting the centers of the three cells of the winning trinca, on top of the existing marks.

#### Scenario: Winning row gets a line

- **WHEN** player `X` wins by completing the top row
- **THEN** a line segment is drawn from approximately the center of cell `(0,0)` to approximately the center of cell `(0,2)`

#### Scenario: No line on draw

- **WHEN** a partida ends as a draw
- **THEN** no winning-line segment is drawn

### Requirement: Click after end resets the partida

Once a partida has ended (win or draw), the next left mouse click on the canvas SHALL reset the board to an empty state, set the current player to `X`, and clear the winning line. The reset click MUST NOT also place a mark from the same click.

#### Scenario: Click after a win starts a new partida

- **GIVEN** a partida finished with `X` as winner
- **WHEN** the user left-clicks anywhere on the canvas
- **THEN** every cell becomes empty
- **AND** the current player is `X`
- **AND** the status text indicates `X` is to play

#### Scenario: Reset click does not also play a move

- **GIVEN** a partida finished with `X` as winner
- **WHEN** the user left-clicks inside an empty cell rectangle
- **THEN** the board resets to empty
- **AND** no mark is placed in that cell from the same click
