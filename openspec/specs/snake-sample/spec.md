# snake-sample Specification

## Purpose

Jogo Snake jogável como módulo executável `:games:snake`, primeiro validador end-to-end de gameplay discreto/grid-based, mutação dinâmica de scene graph via script, wraparound em `Camera2D.bounds`, e ponte Kotlin Signal → Python handler através de `Timer.timeout`.

## Requirements

### Requirement: Snake is an executable standalone module

The project SHALL provide a Gradle module `:games:snake` that depends on `:engine`, `:engine-skiko`, `:engine-bundle`, `:engine-bundle-python`, and `kotlinx-serialization`, with a `Main.kt` entry point that opens a desktop window hosting Snake via `SkikoHost`. The module MUST be executable via `./gradlew :games:snake:run`. The module MUST NOT depend on any other game module. The `Main.kt` SHALL construct a single `PythonScriptHost` via `PythonScriptHost.create()` and inject it into the `BundleLoader` via the `scripting` parameter, then load the scene via `BundleLoader.fromResources("snake", scripting = python)` and hand it to `SkikoHost().run(...)`. The body of `main` MUST be as concise as Pong's — no manual `NodeRegistry` registration, no script manifest.

#### Scenario: Snake runs from Gradle

- **WHEN** a developer runs `./gradlew :games:snake:run` from the repository root
- **THEN** a desktop window opens displaying the Snake scene
- **AND** the game responds to keyboard input

#### Scenario: Snake uses only public engine API

- **WHEN** the `:games:snake` source tree is inspected
- **THEN** every engine interaction goes through public types exported by `:engine`, `:engine-skiko`, `:engine-bundle`, and `:engine-bundle-python`
- **AND** no internal/private API is referenced

#### Scenario: Snake bundle lives in resources

- **WHEN** the `:games:snake/src/main/resources/snake/` directory is inspected
- **THEN** it contains `scene.json` at the root
- **AND** a `scripts/` subdirectory containing the Python script files

#### Scenario: Main.kt is concise

- **WHEN** `:games:snake/src/main/kotlin/.../Main.kt` is inspected
- **THEN** `main` constructs `PythonScriptHost.create()`, calls `BundleLoader.fromResources("snake", scripting = python)`, and starts `SkikoHost()` — nothing more
- **AND** the file does not reference `NodeRegistry`, script manifest manipulation, or any manual type registration

### Requirement: Snake scene composition

The Snake scene SHALL contain: one `Camera2D` (`current=true`, `bounds=Rect(0,0, 400,400)`); one `Node2D` named `Snake` whose script is `scripts/snake.py` and which holds one child `Timer` named `MoveTimer` (`waitTime=0.125`, `autostart=true`, `oneShot=false`, `processCallback=PHYSICS`); one `ColorRect` named `Food` whose script is `scripts/food.py`; one `Label` named `ScoreLabel` (initial text `"Score: 0"`) whose script is `scripts/score.py`; one `Label` named `GameOverLabel` (initial text containing the restart hint, initially invisible by `color.a = 0.0` or `visible=false`) whose script is `scripts/gameover.py`. The scene file MUST declare `"version": 2`.

#### Scenario: Scene contains expected nodes after loading

- **WHEN** the scene is loaded via `BundleLoader.fromResources("snake", scripting = python)`
- **THEN** `scene.findChild("Camera2D")` resolves to a `Camera2D` with `current == true` and `bounds.size == Vec2(400f, 400f)`
- **AND** `scene.findChild("Snake")` resolves to a `Node2D` whose script is `scripts/snake.py`
- **AND** `scene.findChild("Snake").findChild("MoveTimer")` resolves to a `Timer` with `waitTime == 0.125f`, `autostart == true`, `oneShot == false`, `processCallback == TimerMode.PHYSICS`
- **AND** `scene.findChild("Food")` resolves to a `ColorRect` whose script is `scripts/food.py`
- **AND** `scene.findChild("ScoreLabel")` and `scene.findChild("GameOverLabel")` both resolve to `Label` nodes

#### Scenario: Bundle version is 2

- **WHEN** the JSON content of `scene.json` is parsed
- **THEN** the top-level key `"version"` is the integer `2`

### Requirement: Snake.py advances on Timer.timeout

The script `snake.py` SHALL connect to the `timeout` signal of the child `MoveTimer` in `_ready` and implement the per-tick advance: compute the new head cell from the current direction, apply wraparound modulo `Camera2D.bounds.size`, prepend the new head `ColorRect` via `addChild`, and pop the tail via `removeChild` unless the head landed on the `Food` cell. When the head lands on the `Food` cell, the snake MUST NOT remove the tail in that tick (growth) and MUST emit a `foodEaten: Signal()` declared at module scope.

#### Scenario: Tick advances head and removes tail

- **GIVEN** a running Snake of length 3 with direction pointing right and no food on the next cell
- **WHEN** `MoveTimer.timeout` emits once
- **THEN** the snake has length 3
- **AND** the head segment is one cell to the right of its previous position
- **AND** the previous tail segment is no longer in the tree

#### Scenario: Eating food grows the snake

- **GIVEN** a running Snake of length 3 with direction pointing right, and the `Food` is on the cell immediately to the right of the head
- **WHEN** `MoveTimer.timeout` emits once
- **THEN** the snake has length 4
- **AND** the `foodEaten` signal is emitted exactly once on that tick

### Requirement: Direction input is edge-triggered and buffered

The script `snake.py` SHALL read direction inputs in `_process(dt)` via `wasKeyPressed(Key.ArrowUp/Down/Left/Right)`. Pressing a key whose target direction equals the negation of the current direction (180° reversal) MUST be ignored. Pressed keys MUST queue into a 1-slot pending buffer that is applied at the start of the next `_tick` (Timer emission), then cleared. Multiple key presses between two ticks resolve to the LAST valid press.

#### Scenario: Arrow key in current frame applies on next tick

- **GIVEN** a Snake with current direction right, no pending direction
- **WHEN** `wasKeyPressed(Key.ArrowDown)` reports true during `_process`
- **AND** the next `MoveTimer.timeout` fires
- **THEN** the head moves downward on that tick
- **AND** the pending buffer is empty after the tick

#### Scenario: 180° reversal is ignored

- **GIVEN** a Snake with current direction right
- **WHEN** `wasKeyPressed(Key.ArrowLeft)` reports true during `_process`
- **AND** the next `MoveTimer.timeout` fires
- **THEN** the head moves right (unchanged), not left

#### Scenario: Last valid press wins between ticks

- **GIVEN** a Snake with current direction right, no pending
- **WHEN** during a single `_process` `wasKeyPressed(Key.ArrowUp)` and then `wasKeyPressed(Key.ArrowDown)` both report true
- **AND** the next `MoveTimer.timeout` fires
- **THEN** the head moves downward (last valid press) — the up press is overwritten

### Requirement: Wraparound uses Camera2D bounds

When `snake.py` advances the head and the resulting position falls outside `bounds`, it MUST wrap modulo `bounds.size.x` (horizontal) and `bounds.size.y` (vertical). Negative results MUST land in the positive range (Python's `%` semantics). The snake MUST NOT trigger game-over from touching a wall.

#### Scenario: Crossing the right edge wraps to the left

- **GIVEN** a Snake whose head is at cell `(19, 5)` in a 20×20 grid (cell size 20px, bounds 400×400) with direction right
- **WHEN** the next tick fires
- **THEN** the head appears at cell `(0, 5)`
- **AND** no `gameOver` signal is emitted by the wall transit

#### Scenario: Crossing the top edge wraps to the bottom

- **GIVEN** a head at cell `(5, 0)` with direction up
- **WHEN** the next tick fires
- **THEN** the head appears at cell `(5, 19)`

### Requirement: Snake emits gameOver on self-collision and stops the timer

The script `snake.py` SHALL declare `gameOver: Signal = signal()` (zero-arg Signal) and emit it when the new head position equals the position of any non-head segment. On `gameOver` emission, `snake.py` MUST call `MoveTimer.stop()` and set its internal `_dead = True` flag so further ticks (if any race through) are no-ops. The snake MUST NOT trigger self-collision in the first tick after spawn when only one segment exists.

#### Scenario: Self-collision triggers gameOver

- **GIVEN** a Snake long enough that turning into itself is possible, and a sequence of directions that loops the head onto a body segment
- **WHEN** the colliding tick fires
- **THEN** the `gameOver` signal is emitted exactly once
- **AND** `MoveTimer.isStopped == true` immediately after the tick

#### Scenario: Length-1 snake never self-collides

- **GIVEN** a freshly reset Snake with a single head segment and an arbitrary direction
- **WHEN** any number of ticks fire (without eating)
- **THEN** no `gameOver` signal is emitted from self-collision (only wraparound moves apply)

### Requirement: Restart reconstructs the snake without process restart

The script `snake.py` SHALL expose `reset()` and SHALL invoke it from `_process` when `self._dead == True` and `wasKeyPressed(Key.Enter)` returns true. `reset()` MUST: remove every child `ColorRect` representing a body segment via `removeChild`; recreate the initial head segment (3 cells horizontally aligned at scene center, direction right); clear the pending direction buffer; clear the `_dead` flag; emit `restart: Signal()` (zero-arg); call `MoveTimer.start()`. The `Food` script MUST listen to `restart` and reposition the food to a fresh random empty cell. The `GameOverLabel` script MUST listen to `restart` and hide itself (set `color.a = 0.0` or `visible = false`). The `ScoreLabel` script MUST listen to `restart` and reset the score to 0.

#### Scenario: Enter after death restarts the game

- **GIVEN** a Snake in `_dead == True` state with `GameOverLabel` visible and score `7`
- **WHEN** `wasKeyPressed(Key.Enter)` reports true in `_process`
- **THEN** every previous body segment is removed
- **AND** exactly 3 head segments are recreated at the center
- **AND** the score reads `Score: 0`
- **AND** `GameOverLabel` is invisible (`color.a == 0.0` or `visible == false`)
- **AND** the `Food` has been moved to a new cell not overlapping the new snake

#### Scenario: Restart re-enables the timer

- **GIVEN** a just-restarted Snake (post `reset()`)
- **WHEN** `MoveTimer.isStopped` is read
- **THEN** the value is `false`
- **AND** subsequent `_physics_process` ticks emit `MoveTimer.timeout` again

### Requirement: Food repositions on foodEaten to a random empty cell

The script `food.py` SHALL connect to `snake.foodEaten` in `_ready` and, on emission, choose a new cell uniformly at random among all cells in the 20×20 grid that are NOT currently occupied by any Snake segment, then set its `transform.position` to that cell's pixel coordinate. The handler MUST tolerate the snake having grown by exactly one segment in the same tick.

#### Scenario: Food never overlaps the snake after repositioning

- **GIVEN** a Snake of length `N` with known cell positions
- **WHEN** `foodEaten` is emitted (e.g., by the snake stepping onto the previous food cell)
- **THEN** the new `Food.transform.position` corresponds to a grid cell NOT in the snake's current cell list (including the newly grown segment)

#### Scenario: Food is randomized across runs

- **GIVEN** two independent runs of Snake that eat the first food
- **WHEN** the resulting food positions are compared
- **THEN** the positions MAY differ (the choice is random over empty cells; the test asserts membership in valid positions, not equality)

### Requirement: ScoreLabel updates from foodEaten signal

The script `score.py` SHALL connect to `snake.foodEaten` and `snake.restart` in `_ready`. On `foodEaten`, the script MUST increment an internal counter and set `label.text = "Score: {n}"` where `n` is the new counter value. On `restart`, the script MUST reset the counter to `0` and set `label.text = "Score: 0"`.

#### Scenario: Score increments on each food

- **GIVEN** a fresh game with `Score: 0` displayed
- **WHEN** the snake eats food three times
- **THEN** the label displays `Score: 3`

#### Scenario: Score resets on restart

- **GIVEN** a game where the label displays `Score: 5` and `gameOver` has emitted
- **WHEN** the player presses Enter and `restart` emits
- **THEN** the label displays `Score: 0`

### Requirement: GameOverLabel visibility tracks gameOver and restart

The script `gameover.py` SHALL connect to `snake.gameOver` and `snake.restart` in `_ready`. On `gameOver`, the script MUST make the label visible (set `color.a = 1.0` if the engine lacks a `visible` property today, otherwise `visible = true`). On `restart`, the script MUST hide the label by the inverse operation. The label MUST start hidden when the scene first loads.

#### Scenario: Label hidden at start

- **WHEN** the scene first loads
- **THEN** `GameOverLabel` is hidden (`color.a == 0.0` or `visible == false`)

#### Scenario: Label appears on gameOver

- **WHEN** the snake self-collides and `gameOver` emits
- **THEN** `GameOverLabel` is visible

#### Scenario: Label hides on restart

- **GIVEN** `GameOverLabel` is visible
- **WHEN** the player presses Enter and `restart` emits
- **THEN** `GameOverLabel` is hidden again
