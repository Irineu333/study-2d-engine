## ADDED Requirements

### Requirement: Minesweeper is an executable standalone module

The project SHALL provide a `:games:minesweeper` module depending on `:engine` and `:engine-compose`, with a `main()` entry point opening a Compose Desktop window hosting `GameSurface(MinesweeperScene())`. The module MUST be runnable via `./gradlew :games:minesweeper:run`. The module MUST NOT depend on other game modules.

#### Scenario: Minesweeper runs from Gradle

- **WHEN** a developer runs `./gradlew :games:minesweeper:run`
- **THEN** a desktop window opens displaying the Beginner-difficulty board
- **AND** the game responds to mouse clicks

#### Scenario: Minesweeper uses only public engine API

- **WHEN** the `:games:minesweeper` source is inspected
- **THEN** every engine interaction goes through public types of `:engine` and `:engine-compose`

### Requirement: Minesweeper uses OnDemand render mode

The Minesweeper scene SHALL opt into `RenderMode.OnDemand`. The scene MUST advance ticks only in response to user input, `requestRender()`, or the timer's per-second pulse during an active game.

#### Scenario: Idle pre-game scene does not tick

- **WHEN** the board is displayed before the first click and the user is not interacting
- **THEN** no `onUpdate` calls execute on Minesweeper nodes
- **AND** the timer has not started

#### Scenario: Timer-pulse wakes scene during active game

- **WHEN** the game is active and the user is idle
- **THEN** the scene ticks at most once per second to update the displayed timer

### Requirement: Default configuration is Beginner (9x9, 10 mines)

The Minesweeper scene SHALL default to a 9x9 grid with 10 mines. The configuration MAY be replaced via constructor parameter for future expansion, but the default `MinesweeperScene()` MUST yield Beginner.

#### Scenario: Default construction yields Beginner

- **WHEN** `MinesweeperScene()` is instantiated with no arguments
- **THEN** the grid has 9 rows, 9 cols, and 10 mines after the first click

### Requirement: First click is always safe and not adjacent to a mine

Mine placement SHALL be deferred until the user's first click. Mines MUST be placed such that the first-clicked cell and all 8 cells adjacent to it (clamped to grid bounds) contain no mine. This guarantees the first click reveals a non-zero region.

#### Scenario: First click reveals a zero or flood

- **WHEN** the user makes their first click on any cell
- **THEN** that cell has 0 adjacent mines (it MAY trigger flood reveal)
- **AND** none of its up-to-8 neighbors contains a mine

### Requirement: Left click reveals a cell

A primary (left) click on a hidden, non-flagged cell SHALL reveal it. If the revealed cell has 0 adjacent mines, the engine MUST flood-reveal connected zero-adjacent cells and their neighbors using the `Grid.neighbors` utility. If the revealed cell contains a mine, the game MUST end in defeat: all mines revealed, the clicked mine highlighted, and timer stopped.

#### Scenario: Revealing a zero floods to neighbors

- **WHEN** the user reveals a cell with 0 adjacent mines
- **THEN** all cells in the connected zero-adjacent region are revealed
- **AND** the immediate non-zero neighbors of the region are revealed (showing their numbers)

#### Scenario: Revealing a number does not flood

- **WHEN** the user reveals a cell with `n > 0` adjacent mines
- **THEN** only that cell is revealed
- **AND** the displayed number equals `n`

#### Scenario: Revealing a mine ends the game

- **WHEN** the user reveals a mine
- **THEN** all mines become visible
- **AND** the timer stops
- **AND** further clicks (except reset) are ignored

### Requirement: Right click toggles flag

A secondary (right) click on a hidden cell SHALL toggle a flag marker on/off. Flagged cells MUST NOT be revealable by left click until unflagged. Flagged cells MUST NOT count as revealed for win detection. A counter MUST display `totalMines - placedFlags` and MUST update on each flag toggle.

#### Scenario: Right-click flags a hidden cell

- **WHEN** the user right-clicks a hidden non-flagged cell
- **THEN** the cell displays a flag marker
- **AND** the mines-remaining counter decreases by 1

#### Scenario: Right-click unflags a flagged cell

- **WHEN** the user right-clicks a flagged cell
- **THEN** the flag is removed
- **AND** the mines-remaining counter increases by 1

#### Scenario: Left-click on flagged cell does nothing

- **WHEN** the user left-clicks a flagged cell
- **THEN** the cell remains flagged
- **AND** no reveal occurs

### Requirement: Timer counts elapsed seconds during active game

A timer SHALL start at the first reveal and stop on win or defeat. It MUST display elapsed whole seconds. It MUST NOT pause when the window loses focus.

#### Scenario: Timer starts on first reveal

- **WHEN** the user makes a first reveal
- **THEN** the timer begins counting at 0 and increments at least once per second

#### Scenario: Timer stops on game end

- **WHEN** the game ends (win or loss)
- **THEN** the timer halts at its current value
- **AND** does not resume without a reset

### Requirement: Win condition

The game SHALL be considered won when every non-mine cell is revealed. Flagging all mines without revealing all non-mine cells MUST NOT count as a win. On win, the timer MUST stop and a status indicator MUST display the victory.

#### Scenario: Revealing the last non-mine cell wins

- **WHEN** all non-mine cells are revealed
- **THEN** the status indicator reflects victory
- **AND** the timer halts

#### Scenario: Flagging all mines is not enough

- **WHEN** all mines are flagged but some non-mine cells remain hidden
- **THEN** the game does not declare victory

### Requirement: Reset returns the board to fresh state

A reset action (button or shortcut) SHALL clear the grid (no mines placed, all cells hidden), reset the timer to 0 (stopped), reset the mines counter, and request a render.

#### Scenario: Reset clears game state

- **WHEN** the user triggers reset at any time
- **THEN** all cells are hidden
- **AND** the timer reads 0 and is stopped
- **AND** the mines-remaining counter reads `totalMines`

### Requirement: Minesweeper exercises pointer buttons and signals

Cells SHALL implement `Interactive`. Left clicks MUST be handled in `onClick`, right clicks in `onRightClick`. The cell MUST emit a typed signal (e.g., `onReveal: Signal<CellCoord>`, `onFlagToggle: Signal<CellCoord>`) so the controller is decoupled from individual cells.

#### Scenario: Buttons routed to distinct callbacks

- **WHEN** a left click hits a cell
- **THEN** `onClick` runs on that cell
- **AND** `onRightClick` does not run for that event

#### Scenario: Signals wire cells to controller

- **WHEN** the Minesweeper source is reviewed
- **THEN** cells expose signals for reveal and flag-toggle events
- **AND** the controller connects to those signals rather than holding direct cell references for state changes
