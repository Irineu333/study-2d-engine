## MODIFIED Requirements

### Requirement: Tic-tac-toe scene composition

The tic-tac-toe tree SHALL be rooted in a plain `Node` subclass (e.g. `TicTacToeRoot : Node()`) whose `onEnter()` populates the children: a single `Board` node and a `StatusText` (or equivalent text) node displaying the current turn or end-of-game message. The root MUST NOT extend `Scene` (the class no longer exists in `:engine`) and MUST NOT extend `SceneTree` (which is not a `Node`). The `Board` MUST own the full game state (cells, current player, winner) and MUST be a single node — the nine cells are NOT modeled as separate scene-graph nodes.

`Main.kt` MUST instantiate the root, wrap it in `SceneTree(root = TicTacToeRoot())`, and pass the tree to `ComposeHost.run(tree, config)`.

#### Scenario: Tree contains the expected nodes after construction

- **WHEN** `TicTacToeRoot()` is instantiated and wrapped in `SceneTree(root = TicTacToeRoot())`, then `tree.start()` runs
- **THEN** the tree's `root.children` contains exactly one `Board` node and one status text node (plus any purely decorative nodes such as a background)

#### Scenario: Root is a plain Node subclass, not a Scene

- **WHEN** the source of the tic-tac-toe root is inspected
- **THEN** the class declaration extends `Node` (or `Node2D`, `Camera2D`, etc.) — NEVER `Scene`
- **AND** the symbol `Scene` does not appear in any `.kt` file under `games/tictactoe/src/main/kotlin/`

#### Scenario: Cells are not scene-graph nodes

- **WHEN** the `Board` node's children are enumerated
- **THEN** no per-cell `Node` exists in the children list

#### Scenario: Main.kt wraps the root in a SceneTree

- **WHEN** `games/tictactoe/.../Main.kt` is inspected
- **THEN** the file contains `ComposeHost().run(SceneTree(root = TicTacToeRoot()), GameConfig(...))` (or equivalent)
- **AND** no parameter passed to `ComposeHost.run` is of type `Scene`

### Requirement: Scene layout is responsive

The tic-tac-toe root SHALL register a resize callback on its owning `SceneTree` (via `tree.onResize = { w, h -> ... }`) so that board size and position are recomputed whenever the surface resizes. Cell side length MUST scale with the smaller of the available width and the available height (after reserving space for the status text), so the board fits within the visible canvas. The callback MUST be installed during `onEnter()` (when `tree` is non-null) and torn down or naturally cleared on `onExit()`.

#### Scenario: Board recenters on window resize

- **WHEN** the hosting window is resized from `(800, 600)` to `(1024, 768)`
- **THEN** the board origin moves so the board is approximately centered in the new canvas
- **AND** the board still fits entirely within the visible canvas

#### Scenario: Cell size scales with smaller axis

- **WHEN** the canvas size is `(400, 800)`
- **THEN** cell side length is bounded by the available width rather than the available height

#### Scenario: Resize handling is wired via SceneTree.onResize

- **WHEN** the source of the tic-tac-toe root or `Board` is inspected
- **THEN** there is exactly one site that sets `tree.onResize = ...` (or equivalent assignment) for the resize-driven layout recompute
- **AND** no override of `Scene.onResize` (legacy API) exists — the symbol does not exist after this change

### Requirement: Tic-tac-toe uses Godot-style lifecycle names

The Kotlin source under `:games:tictactoe` SHALL override the Godot-style hook names (`onProcess`, `onDraw`, `onEnter`, `onExit`) and SHALL NOT override the legacy names (`onUpdate`, `onRender`). The text display SHALL use `Label`, not `Text`. The hover/click logic that currently lives in `onUpdate` SHALL move to `onProcess`. The drawing logic that currently lives in `onRender` SHALL move to `onDraw`. Game code reading the surface size MUST do so via `tree?.size` / `tree?.width` / `tree?.height` (where `tree` is `Node.tree`) — `rootScene()` does not exist after this change.

#### Scenario: No legacy hook overrides exist

- **WHEN** any `.kt` file under `games/tictactoe/src/main/kotlin/` is inspected
- **THEN** no `override fun onUpdate` or `override fun onRender` exists

#### Scenario: Board overrides onProcess and onDraw

- **WHEN** `Board.kt` is inspected
- **THEN** it overrides `onProcess(dt: Float)` (covering the current hover + click handling)
- **AND** it overrides `onDraw(renderer: Renderer)` (covering the current draw)

#### Scenario: StatusText is a Label

- **WHEN** `StatusText.kt` (or equivalent) is inspected
- **THEN** the class is declared as `class StatusText : Label()` (or composes a `Label` field)
- **AND** there is no reference to a removed `Text` class

#### Scenario: Surface-size reads go through tree

- **WHEN** any `.kt` file under `games/tictactoe/src/main/kotlin/` is inspected
- **THEN** no occurrence of `rootScene()` remains
- **AND** surface-size reads use `tree?.size`, `tree?.width`, `tree?.height`, or `tree?.viewport`

#### Scenario: Tic-tac-toe still runs on Compose backend

- **WHEN** a developer runs `./gradlew :games:tictactoe:run`
- **THEN** a desktop window opens displaying the tic-tac-toe scene rendered by `ComposeHost`
- **AND** click handling and grid rendering behave identically to before the rename
