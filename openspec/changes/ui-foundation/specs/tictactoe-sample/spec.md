## MODIFIED Requirements

### Requirement: Tic-tac-toe scene composition

The Tic-tac-toe scene SHALL be loaded from `src/main/resources/tictactoe/scene.json`, with the orchestrator logic in `src/main/resources/tictactoe/scripts/board.lua`. The scene MUST include:

- A root of type `com.neoutils.engine.scene.Node` whose `script` is `scripts/board.lua`.
- A `Camera2D` child with `current: true` and `bounds = Rect(Vec2.ZERO, Vec2(600f, 600f))`.
- Four `Line2D` children forming the grid (two vertical, two horizontal) declared with absolute coordinates.
- A **`CanvasLayer`** child named `Hud` containing a `Label` named `status` holding the current status text. `status` lives in screen-space, with `transform.position` authored in screen pixels (e.g. centered horizontally near the top edge).

The previously-existing Kotlin classes `Board`, `Mark`, `StatusText`, and `TicTacToeRoot` MUST NOT exist anywhere under `games/tictactoe/src/main/kotlin/`. The previous Python script `scripts/board.py` MUST NOT exist anywhere under `games/tictactoe/src/main/resources/tictactoe/scripts/`. After this change, the bundle MAY differ from previous versions only by the documented `ui-foundation` migration (wrapping the `status` Label in a `CanvasLayer` named `Hud`).

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
