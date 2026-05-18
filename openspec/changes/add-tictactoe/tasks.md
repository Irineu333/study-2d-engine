## 1. Engine SPI evolution

- [x] 1.1 Add `MouseButton` enum (`Left`, `Right`, `Middle`) under `com.neoutils.engine.input`
- [x] 1.2 Extend `Input` interface with `isMouseDown(button: MouseButton): Boolean` and `wasMouseClicked(button: MouseButton): Boolean`
- [x] 1.3 Add `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` to `Renderer` interface
- [x] 1.4 Add `Rect.contains(point: Vec2): Boolean` (inclusive at origin, exclusive at far edges)
- [x] 1.5 Unit-test `Rect.contains` for inside/outside/edge points in `RectTest.kt`

## 2. Compose backend implementation

- [x] 2.1 Extend `ComposeInput` with `downButtons`, `pendingButtonPresses`, `pressedButtonsThisTick` sets mirroring the key-state pattern
- [x] 2.2 Add `ComposeInput.onPointerButton(buttonId, pressed)` (or equivalent) called from `GameSurface`'s `pointerInput` block on `PointerEventType.Press`/`Release`
- [x] 2.3 Extend `ComposeInput.beginTick()` to swap pending button presses into the per-tick snapshot
- [x] 2.4 Implement `Input.isMouseDown` and `Input.wasMouseClicked` on `ComposeInput`
- [x] 2.5 Update `GameSurface` `pointerInput` block to detect button press/release events and forward them to `ComposeInput`
- [x] 2.6 Implement `ComposeRenderer.drawLine` on top of `DrawScope.drawLine`

## 3. Tic-tac-toe module scaffolding

- [x] 3.1 Add `games/tictactoe/` directory with `build.gradle.kts` (depends on `:engine` and `:engine-compose`, applies Kotlin JVM + Compose Desktop application plugins)
- [x] 3.2 Register `:games:tictactoe` in `settings.gradle.kts`
- [x] 3.3 Create `com.neoutils.engine.games.tictactoe.Main.kt` with a Compose `application { Window { GameSurface(scene) } }` mirroring `:games:pong`
- [x] 3.4 Wire F1 (FPS) and F2 (collider visualization, harmless without colliders) toggles in `Main.kt`

## 4. Board node and game state

- [x] 4.1 Define `Mark` sealed/enum type (`X`, `O`) with `other()` helper inside `:games:tictactoe`
- [x] 4.2 Create `Board : Node2D` with fields: `cells: Array<Mark?>(9)`, `currentPlayer: Mark`, `winner: Mark?`, `isDraw: Boolean`, `winningLine: Triple<Int, Int, Int>?`, plus layout fields (`origin`, `cellSize`)
- [x] 4.3 Implement `Board.cellRect(index: Int): Rect` returning world-space rectangle for hit-testing and rendering
- [x] 4.4 Implement `Board.cellAt(point: Vec2): Int?` using `Rect.contains` against each cell
- [x] 4.5 Implement `Board.checkWinner()` returning the winning trinca (or null) by scanning rows, columns, and the two diagonals
- [x] 4.6 Implement `Board.reset()` clearing cells, winner, winningLine, draw flag and setting `currentPlayer = X`

## 5. Board lifecycle and input

- [x] 5.1 In `Board.onUpdate(dt)`: compute `hoveredCell` from `scene.input!!.pointerPosition`
- [ ] 5.2 In `Board.onUpdate(dt)`: handle `wasMouseClicked(MouseButton.Left)` — branch on `gameOver`: reset, else play move if hovered cell is empty
- [ ] 5.3 After a move, run `checkWinner()`; if winner found, store `winningLine` and `winner`; else if all cells filled, set `isDraw = true`; else advance turn
- [ ] 5.4 Ensure a single click that resets does NOT also play a move (consume the click for reset only)

## 6. Board rendering

- [ ] 6.1 In `Board.onRender(renderer)`: draw the 4 grid lines as `drawLine` with thickness proportional to `cellSize`
- [ ] 6.2 Draw each cell's mark — X as two `drawLine` diagonals, O as `drawCircle(filled = false)` with inner radius proportional to `cellSize`
- [ ] 6.3 If `!gameOver` and `hoveredCell != null` and cell is empty, draw the ghost mark with reduced alpha (~0.3)
- [ ] 6.4 If `winningLine != null`, draw a highlight `drawLine` from the center of the first cell to the center of the third cell with a contrasting color/thickness

## 7. Scene assembly and status text

- [ ] 7.1 Create `TicTacToeScene : Scene` instantiating the `Board` and a `Text` node for status
- [ ] 7.2 Implement `TicTacToeScene.onResize(width, height)`: reserve space for status text (e.g., 60px), set `Board.origin` and `Board.cellSize` so the board is centered and fits within the smaller axis
- [ ] 7.3 In `TicTacToeScene.onUpdate(dt)` (or via a small `StatusText` node): update displayed text based on `board.winner`/`board.isDraw`/`board.currentPlayer`
- [ ] 7.4 Default window size 800x600 in `Main.kt`, black background like Pong

## 8. Documentation and roadmap

- [ ] 8.1 Update the roadmap table in `CLAUDE.md` to list `add-tictactoe` as Active (or Archived once merged)
- [ ] 8.2 Add a "Para rodar Velha" section in `CLAUDE.md` with the gradle command and controls (left-click to play, left-click after end to reset, F1/F2 overlays)
- [ ] 8.3 Update `README.md` if it references the runnable samples

## 9. Validation

- [ ] 9.1 Run `./gradlew :engine:test` — all tests green, including new `Rect.contains` cases
- [ ] 9.2 Run `./gradlew :games:tictactoe:run` — verify: X starts; click places mark; turn alternates; ghost on hover; win → line drawn + status updates; draw scenario; click after end resets without playing on that click
- [ ] 9.3 Resize window mid-partida — verify board recenters and remains fully visible; state preserved
- [ ] 9.4 Run `./gradlew :games:pong:run` — verify Pong still works (no regression from SPI changes)
- [ ] 9.5 Run `openspec validate add-tictactoe --strict` (or `openspec verify`) — passes
