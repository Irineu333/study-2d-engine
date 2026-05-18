## ADDED Requirements

### Requirement: Tic-tac-toe is an executable standalone module

The project SHALL provide a `:games:tictactoe` module depending on `:engine` and `:engine-compose`, with a `main()` entry point opening a Compose Desktop window hosting `GameSurface(TicTacToeScene())`. The module MUST be runnable via `./gradlew :games:tictactoe:run`. The module MUST NOT depend on other game modules.

#### Scenario: Tic-tac-toe runs from Gradle

- **WHEN** a developer runs `./gradlew :games:tictactoe:run`
- **THEN** a desktop window opens displaying the 3x3 board
- **AND** the game responds to mouse clicks

#### Scenario: Tic-tac-toe uses only public engine API

- **WHEN** the `:games:tictactoe` source is inspected
- **THEN** every engine interaction goes through public types of `:engine` and `:engine-compose`

### Requirement: Tic-tac-toe uses OnDemand render mode

The Tic-tac-toe scene SHALL opt into `RenderMode.OnDemand`. The scene MUST advance ticks only in response to pointer events (clicks/hover) or explicit `requestRender()` calls (e.g., after pressing reset).

#### Scenario: Idle scene consumes no game logic

- **WHEN** the board is displayed and the user is not interacting
- **THEN** no `onUpdate` calls execute on tic-tac-toe nodes
- **AND** CPU usage attributable to game ticks is negligible

### Requirement: Tic-tac-toe scene composition

The Tic-tac-toe scene SHALL contain: a 3x3 grid of `Cell` interactive nodes (`Grid<Cell>` or equivalent layout), a `StatusText` node showing whose turn it is or the result, and a `ResetButton` interactive node. Each `Cell` MUST implement `Interactive` and have `bounds()` matching its visual area.

#### Scenario: Scene tree has the expected nodes after construction

- **WHEN** a new `TicTacToeScene` is instantiated
- **THEN** it contains exactly 9 `Cell` nodes, 1 `StatusText` node, and 1 `ResetButton` node

### Requirement: Turn-based marking via clicks

The Tic-tac-toe scene SHALL track two players (`X` and `O`) alternating turns, starting with `X`. Clicking an empty `Cell` MUST place the current player's mark there and switch turns. Clicking a non-empty cell MUST NOT alter the cell or change turns. After a win or draw, further clicks on cells MUST be ignored until reset.

#### Scenario: First click places X

- **WHEN** the board is empty and the user clicks a cell
- **THEN** that cell displays `X`
- **AND** `StatusText` indicates it is `O`'s turn

#### Scenario: Click on occupied cell is no-op

- **WHEN** a cell already contains `X` and the user clicks it
- **THEN** the cell still contains `X`
- **AND** the current turn does not change

#### Scenario: Clicks ignored after game ends

- **WHEN** the game has been won and the user clicks an empty cell
- **THEN** the cell remains empty
- **AND** `StatusText` continues to display the result

### Requirement: Win and draw detection

The Tic-tac-toe scene SHALL detect a win when any row, column, or diagonal contains three of the same non-empty mark, and detect a draw when all 9 cells are filled without a win. On win, `StatusText` MUST indicate the winner; on draw, it MUST indicate the draw.

#### Scenario: Row win

- **WHEN** player `X` places marks completing a full row
- **THEN** `StatusText` displays a win for `X`
- **AND** further cell clicks are ignored

#### Scenario: Diagonal win

- **WHEN** player `O` completes either diagonal with `O` marks
- **THEN** `StatusText` displays a win for `O`

#### Scenario: Draw

- **WHEN** all cells are filled with no row/column/diagonal of three matching marks
- **THEN** `StatusText` displays "Draw" (or equivalent)

### Requirement: Reset button restarts the game

Clicking the `ResetButton` SHALL clear all cells, set the current player back to `X`, clear any end-of-game status, and call `scene.requestRender()` to repaint immediately.

#### Scenario: Reset clears the board

- **WHEN** the game is in any state and the user clicks `ResetButton`
- **THEN** all cells are empty
- **AND** `StatusText` indicates it is `X`'s turn

### Requirement: Tic-tac-toe exercises signals

Cells SHALL communicate clicks to a controller via a signal (e.g., `cell.onClicked: Signal<CellCoord>`), not by holding a direct reference to the controller. The controller SHALL connect to each cell's signal during `onEnter` and disconnect during `onExit`. Direct reference between `Cell` and controller MUST NOT be used.

#### Scenario: Source review confirms signal-based wiring

- **WHEN** the Tic-tac-toe source is reviewed
- **THEN** `Cell` does not hold a typed reference to the controller
- **AND** the controller's wiring goes through `signal.connect`

#### Scenario: Disconnects on exit

- **WHEN** the Tic-tac-toe scene is removed from a live runtime
- **THEN** each cell's controller-side connection is disconnected
