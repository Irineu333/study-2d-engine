## 1. Pre-flight

- [ ] 1.1 Confirm `engine-foundation` is implemented and archived (`openspec list` shows it under archived changes; `openspec/specs/engine-core/`, `compose-runtime/`, `dx-tooling/`, `project-conventions/` exist).
- [ ] 1.2 Confirm Pong is jogável from `main` (`./gradlew :games:pong:run`) — baseline before adding features.

## 2. Signals primitive

- [ ] 2.1 Implement `Signal<T>` in `:engine` with `emit(value)`, `connect(handler): Connection`. `Connection` exposes `disconnect()`.
- [ ] 2.2 Add `SignalUnit` alias (or document `Signal<Unit>` idiom) for payload-less signals.
- [ ] 2.3 Implement debug emission logging guarded by `Debug.logSignals` (default false), tag `"Events"`, level `Debug`.
- [ ] 2.4 Unit tests: ordering of connect/emit, disconnect during emit (safe for the running emit pass), per-handler isolation.

## 3. RenderMode and tick-on-demand

- [ ] 3.1 Add `enum class RenderMode { Continuous, OnDemand }` in `:engine`.
- [ ] 3.2 Add `Scene.renderMode: RenderMode` (default `Continuous`) and `Scene.requestRender()` that sets a dirty flag.
- [ ] 3.3 Add an "active animation" registry on `Scene` (`scene.beginAnimation(token)`, `scene.endAnimation(token)`) — used as wake condition; no animation system yet, just the hook.
- [ ] 3.4 Update `GameLoop.tick(dtNanos)` (and / or wire its callers) to evaluate wake conditions before running update/physics/render when `renderMode == OnDemand`. Clear dirty flag at the start of an on-demand tick.
- [ ] 3.5 Unit tests: `OnDemand` idle frame skips tick; queued event triggers exactly one tick; `requestRender` triggers exactly one tick; `Continuous` unchanged.

## 4. Input: pointer events and buttons

- [ ] 4.1 Add `enum class PointerButton { Primary, Secondary, Tertiary }` in `:engine`.
- [ ] 4.2 Add sealed `PointerEvent` with subtypes `Click(button, position)` and `Hover(position)`.
- [ ] 4.3 Add `Input.isButtonDown(button: PointerButton): Boolean`.
- [ ] 4.4 Add an interface `Interactive` with `onClick`, `onRightClick`, `onHover` (empty default impls).
- [ ] 4.5 Implement dispatcher routine (lives in engine, executed each tick before `onUpdate`): drain pointer event queue, reverse-render-order traversal of the scene tree, deliver each event to the first `Interactive` node whose `bounds()` contain the position (skipping invisible nodes).
- [ ] 4.6 Unit tests: topmost hit wins; secondary routes to `onRightClick`; click outside any interactive is no-op; invisible nodes skipped.

## 5. Grid<T> utility

- [ ] 5.1 Implement `Grid<T>(rows, cols, init)` with `get`, `set`, `forEachIndexed`, `neighbors(row, col, includeDiagonals)`, named `GridOutOfBoundsException`.
- [ ] 5.2 Unit tests: init lambda; neighbors counts (4 / 8) and exclusion of center; out-of-bounds throws.

## 6. Compose runtime extensions

- [ ] 6.1 Extend `ComposeInput` to expose `isButtonDown` and to enqueue `PointerEvent.Click` on pointer release (primary, secondary, tertiary).
- [ ] 6.2 Forward hover position changes as `PointerEvent.Hover` (only when position changes since previous frame).
- [ ] 6.3 In `GameSurface`, consult `scene.renderMode`; when `OnDemand`, skip `GameLoop.tick` if no wake condition holds (no queued events, no `requestRender`, no active animation). Always continue requesting `withFrameNanos`.
- [ ] 6.4 Map Compose mouse buttons → `PointerButton` in a single private helper; cover Primary/Secondary/Tertiary.
- [ ] 6.5 Manual verification: run Pong (`Continuous` default) — must behave identically to baseline.

## 7. DX additions

- [ ] 7.1 Add `Debug.renderModeOverride: RenderMode?` (default null). When non-null, runtime uses it instead of each scene's mode.
- [ ] 7.2 Bind `F8` in `:engine-compose` to cycle the override (`null → Continuous → OnDemand → null`); log the new value at `Info` under tag `DX`.
- [ ] 7.3 Add `Debug.logSignals: Boolean` (default false); ensure `Signal.emit` calls log only when flag is on.
- [ ] 7.4 Quick smoke test: toggle `renderModeOverride` from Pong window to confirm cycle works and logs.

## 8. `:games:tictactoe`

- [ ] 8.1 Create `games/tictactoe/build.gradle.kts` as Kotlin JVM application; depend on `:engine` and `:engine-compose`. Configure `application` plugin + Compose Desktop launcher. Update `settings.gradle.kts`.
- [ ] 8.2 Implement `Cell : Node2D, Interactive` with state (`Empty`, `X`, `O`), `bounds()` reflecting its size, render of X/O via `Renderer.drawText` or shapes, signal `onClicked: Signal<CellCoord>`.
- [ ] 8.3 Implement `TicTacToeScene`: 3x3 grid of `Cell` (`Grid<Cell>` for layout); `StatusText`; `ResetButton` (an `Interactive` node).
- [ ] 8.4 Controller (probably in the scene class): connects to each cell's `onClicked` signal during `onEnter`, manages current turn and game state, runs win/draw detection, updates `StatusText`. Disconnects in `onExit`.
- [ ] 8.5 Reset behavior: clears cells, resets turn, clears status, calls `scene.requestRender()`.
- [ ] 8.6 Scene opts into `RenderMode.OnDemand`.
- [ ] 8.7 Implement `main()` opening Compose window with `GameSurface(TicTacToeScene())`.
- [ ] 8.8 Manual playtest: full game, win each line (rows, cols, diagonals), draw, reset, click on occupied cell, click after game end, OnDemand verification (idle should not tick — toggle F8 to confirm).

## 9. `:games:minesweeper`

- [ ] 9.1 Create `games/minesweeper/build.gradle.kts` (parallel to tictactoe). Update `settings.gradle.kts`.
- [ ] 9.2 Define `CellState`: `Hidden`, `Revealed`, `Flagged`. Define `MineCell` (mine or number, but kept hidden under state).
- [ ] 9.3 Implement `Cell : Node2D, Interactive` with current `CellState`, `bounds()`, render per state, signals `onReveal`, `onFlagToggle`.
- [ ] 9.4 Implement `MinesweeperScene(config = BeginnerConfig)`:
  - holds a `Grid<Cell>` and a parallel `Grid<MineCell>` (or a single Cell carrying both presentation and internal state)
  - mine placement deferred until first click; ensure first-clicked cell and 8 neighbors are mine-free
  - flood reveal using `Grid.neighbors(includeDiagonals = true)` for zero-adjacent cells
  - HUD: mines-remaining counter, timer text, status indicator, reset button
  - timer started on first reveal, increments via tick when active, halted on game end
  - scene opts into `RenderMode.OnDemand`; while timer is running, scene calls `requestRender()` once per second (or uses an `active animation` token registered with `Scene`).
- [ ] 9.5 Controller logic: on `onReveal`, dispatch to reveal flow (with mine-hit ending the game and revealing all mines); on `onFlagToggle`, dispatch to flag flow updating counter; win check after each successful reveal.
- [ ] 9.6 Reset: rebuild grid, hide all cells, reset timer to 0 (stopped), reset counter, `scene.requestRender()`.
- [ ] 9.7 Implement `main()` opening Compose window with `GameSurface(MinesweeperScene())`.
- [ ] 9.8 Manual playtest:
  - first click never hits a mine and reveals a meaningful area
  - left/right click distinction works
  - flood fill matches expectation visually
  - flagging decreases counter, unflagging increases
  - revealing a mine ends the game and shows all mines
  - revealing all non-mine cells wins
  - timer increments and halts as specified
  - reset returns to fresh state
  - OnDemand idle (between user actions, before first click) does not tick

## 10. Documentation

- [ ] 10.1 Update `CLAUDE.md`:
  - Add "Signals" subsection (contract: synchronous, single-threaded, connect/disconnect).
  - Add "RenderMode" subsection (Continuous default, OnDemand wake conditions, F8 debug override).
  - Amend invariants to record the four new clauses (signals preferred for decoupled comms, RenderMode default Continuous, pointer hit-test lives in input not physics, additions are aditive — Pong unchanged).
  - Update roadmap: `engine-foundation` archived, `event-driven-games` active (or completed at archive time), editor change still planned.
- [ ] 10.2 Cross-check `README.md` lists the three runnable games (`pong`, `tictactoe`, `minesweeper`).

## 11. Acceptance and verification

- [ ] 11.1 `./gradlew build` clean from root.
- [ ] 11.2 `./gradlew :games:pong:run` — Pong is intocado e jogável (non-regression).
- [ ] 11.3 `./gradlew :games:tictactoe:run` — full playthrough passes per Scenarios in `tictactoe-sample` spec.
- [ ] 11.4 `./gradlew :games:minesweeper:run` — full playthrough passes per Scenarios in `minesweeper-sample` spec.
- [ ] 11.5 Confirm `:engine` has no Compose dependency (visual inspection of `engine/build.gradle.kts` plus dependency report).
- [ ] 11.6 `openspec validate event-driven-games --strict` passes.
- [ ] 11.7 `/opsx:verify event-driven-games` passes before archiving.
