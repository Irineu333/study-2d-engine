## MODIFIED Requirements

### Requirement: Snake scene composition

The Snake scene SHALL contain: one `Camera2D` (`current=true`, `bounds=Rect(0,0, 400,400)`); one `Node2D` named `Snake` whose script is `scripts/snake.py` and which holds one child `Timer` named `MoveTimer` (`waitTime=0.125`, `autostart=true`, `oneShot=false`, `processCallback=PHYSICS`); one `ColorRect` named `Food` whose script is `scripts/food.py`; and one **`CanvasLayer`** named `Hud` containing two `Label` children — `ScoreLabel` (initial text `"Score: 0"`, script `scripts/score.py`) and `GameOverLabel` (initial text containing the restart hint, initially invisible by `color.a = 0.0` or `visible=false`, script `scripts/gameover.py`). The scene file MUST declare `"version": 2`.

`ScoreLabel` SHALL be positioned near the top-left of the screen surface (e.g. `Vec2(10, 10)` in screen pixels), and `GameOverLabel` SHALL be centered on the screen via positioning relative to the screen surface (the script MAY compute the center on resize using `tree.size`). Both labels' positions are screen pixels — NOT subject to the `Camera2D.bounds` projection that the world subtree uses.

#### Scenario: Scene contains expected nodes after loading

- **WHEN** the scene is loaded via `BundleLoader.fromResources("snake", scripting = python)`
- **THEN** `scene.findChild("Camera2D")` resolves to a `Camera2D` with `current == true` and `bounds.size == Vec2(400f, 400f)`
- **AND** `scene.findChild("Snake")` resolves to a `Node2D` whose script is `scripts/snake.py`
- **AND** `scene.findChild("Snake").findChild("MoveTimer")` resolves to a `Timer` with `waitTime == 0.125f`, `autostart == true`, `oneShot == false`, `processCallback == TimerMode.PHYSICS`
- **AND** `scene.findChild("Food")` resolves to a `ColorRect` whose script is `scripts/food.py`
- **AND** `scene.findChild("Hud")` resolves to a `CanvasLayer` instance
- **AND** `scene.findChild("Hud").findChild("ScoreLabel")` and `scene.findChild("Hud").findChild("GameOverLabel")` both resolve to `Label` nodes

#### Scenario: Bundle version is 2

- **WHEN** the JSON content of `scene.json` is parsed
- **THEN** the top-level key `"version"` is the integer `2`

#### Scenario: HUD labels render in screen-space

- **WHEN** the game runs and the Camera2D applies its `bounds = Rect(0,0, 400,400)` projection (potentially scaling world content non-1:1 to the surface)
- **THEN** `ScoreLabel` and `GameOverLabel` render at constant screen-pixel positions, independent of the camera projection (they are drawn during the UI pass under the `Hud` CanvasLayer)

### Requirement: ScoreLabel updates from foodEaten signal

The script `score.py` SHALL connect to `snake.foodEaten` and `snake.restart` in `_ready`. On `foodEaten`, the script MUST increment an internal counter and set `label.text = "Score: {n}"` where `n` is the new counter value. On `restart`, the script MUST reset the counter to `0` and set `label.text = "Score: 0"`. The script MUST resolve the `Snake` reference and signal subscriptions correctly even though `ScoreLabel` now lives under the `Hud` `CanvasLayer` (i.e. it is no longer a sibling of `Snake` — both share `tree.root` as a common ancestor).

#### Scenario: Score increments on each food

- **GIVEN** a fresh game with `Score: 0` displayed
- **WHEN** the snake eats food three times
- **THEN** the label displays `Score: 3`

#### Scenario: Score resets on restart

- **GIVEN** a game where the label displays `Score: 5` and `gameOver` has emitted
- **WHEN** the player presses Enter and `restart` emits
- **THEN** the label displays `Score: 0`

#### Scenario: Score wiring survives ScoreLabel living under CanvasLayer

- **WHEN** the scene loads with `ScoreLabel` placed under `Hud` (a `CanvasLayer`)
- **THEN** `score.py._ready` successfully resolves the `Snake` node (e.g. via `NodeRef("../../Snake")` or `tree.root.findChild("Snake")`) and connects to its `foodEaten` and `restart` signals

### Requirement: GameOverLabel visibility tracks gameOver and restart

The script `gameover.py` SHALL connect to `snake.gameOver` and `snake.restart` in `_ready`. On `gameOver`, the script MUST make the label visible (set `color.a = 1.0` if the engine lacks a `visible` property today, otherwise `visible = true`). On `restart`, the script MUST hide the label by the inverse operation. The label MUST start hidden when the scene first loads. As with `ScoreLabel`, the script MUST resolve `Snake` correctly given that `GameOverLabel` now lives under the `Hud` `CanvasLayer`.

#### Scenario: Label hidden at start

- **WHEN** the scene first loads
- **THEN** `GameOverLabel` is hidden (`color.a == 0.0` or `visible == false`)

#### Scenario: Game over wiring survives GameOverLabel living under CanvasLayer

- **WHEN** the scene loads with `GameOverLabel` placed under `Hud`
- **THEN** `gameover.py._ready` successfully resolves the `Snake` node and connects to its `gameOver` and `restart` signals
