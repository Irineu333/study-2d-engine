# tictactoe-sample Specification

## Purpose

Jogo da Velha (tic-tac-toe) humano vs humano em `:games:tictactoe`, exercitando entrada discreta por mouse, hit-test via `Rect.contains`, desenho de linhas via `Renderer.drawLine`, e medição de texto via `Renderer.measureText`. Estado do jogo (células, jogador atual, vencedor) vive num único nó `Board`; as nove células NÃO são nós do scene graph.

## Requirements

### Requirement: Tic-tac-toe is an executable standalone module

The project SHALL provide a `:games:tictactoe` module that depends on `:engine` and `:engine-compose` and contains a `main()` entry point opening a Compose Desktop window hosting a `GameSurface` rendering the tic-tac-toe scene. The module MUST be runnable via `./gradlew :games:tictactoe:run`. The module MUST NOT depend on any other game module.

#### Scenario: Tic-tac-toe runs from Gradle

- **WHEN** a developer runs `./gradlew :games:tictactoe:run` from the project root
- **THEN** a desktop window opens displaying the tic-tac-toe scene
- **AND** the game is responsive to mouse input

#### Scenario: Tic-tac-toe uses only public engine API

- **WHEN** the `:games:tictactoe` source is inspected
- **THEN** all engine interactions go through types exported by `:engine` and `:engine-compose`
- **AND** no internal/private API of either module is referenced

### Requirement: Tic-tac-toe scene composition

The tic-tac-toe scene SHALL contain a single `Board` node and a `StatusText` (or equivalent text) node displaying the current turn or end-of-game message. The `Board` MUST own the full game state (cells, current player, winner) and MUST be a single node — the nine cells are NOT modeled as separate scene-graph nodes.

#### Scenario: Scene contains the expected nodes after construction

- **WHEN** a new `TicTacToeScene` is instantiated
- **THEN** its tree contains exactly one `Board` node and one status text node (plus any purely decorative nodes such as a background)

#### Scenario: Cells are not scene-graph nodes

- **WHEN** the `Board` node's children are enumerated
- **THEN** no per-cell `Node` exists in the children list

### Requirement: Turn-based gameplay with two human players

The game SHALL start with player `X` to move. After a legal move, the turn SHALL alternate to the other player. The first move of every new partida MUST be `X`.

#### Scenario: First move is X

- **WHEN** a new partida starts
- **THEN** the status text indicates that `X` is to play
- **AND** the first legal click places an `X` on the board

#### Scenario: Turn alternates after a legal move

- **WHEN** player `X` places a mark in an empty cell during X's turn
- **THEN** the status text indicates that `O` is to play
- **AND** the next legal click places an `O`

### Requirement: Click input drives moves with hit-testing

The `Board` SHALL detect a left mouse click whose pointer position falls inside an empty cell during an ongoing partida and place the current player's mark in that cell. Clicks outside any cell, or inside an already-occupied cell, MUST NOT mutate the board.

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

### Requirement: Hover ghost indicates the next move

The `Board` SHALL render a faded representation of the current player's mark in the empty cell currently under the pointer, while a partida is ongoing. The ghost MUST disappear when the pointer is outside any cell, when the cell is occupied, or when the partida has ended.

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

The `Board` SHALL detect end of partida after every legal move. A partida MUST end with a winner when one player occupies all three cells of any row, column, or main diagonal. A partida MUST end as a draw when all nine cells are occupied and no winner exists. Once a partida is finished, further moves MUST NOT be accepted until a reset occurs.

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
- **THEN** no mark is placed in that cell

### Requirement: Winning line is highlighted

When a partida ends with a winner, the `Board` SHALL render a visible line segment connecting the centers of the three cells of the winning trinca, drawn over the existing marks.

#### Scenario: Winning row gets a line

- **WHEN** player `X` wins by completing the top row
- **THEN** a line segment is rendered from approximately the center of cell `(0,0)` to approximately the center of cell `(0,2)`

#### Scenario: No line on draw

- **WHEN** a partida ends as a draw
- **THEN** no winning-line segment is rendered

### Requirement: Click after end resets the partida

Once a partida has ended (win or draw), the next left mouse click on the canvas SHALL reset the board to an empty state, set the current player to `X`, and clear the winning line.

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

### Requirement: Scene layout is responsive

The `TicTacToeScene` SHALL recompute board size and position whenever `onResize(width, height)` is invoked, so that the board remains centered horizontally and the status text remains visible. Cell side length MUST scale with the smaller of the available width and the available height (after reserving space for the status text), so the board fits within the visible canvas.

#### Scenario: Board recenters on window resize

- **WHEN** the hosting window is resized from `(800, 600)` to `(1024, 768)`
- **THEN** the board origin moves so the board is approximately centered in the new canvas
- **AND** the board still fits entirely within the visible canvas

#### Scenario: Cell size scales with smaller axis

- **WHEN** the canvas size is `(400, 800)`
- **THEN** cell side length is bounded by the available width rather than the available height
