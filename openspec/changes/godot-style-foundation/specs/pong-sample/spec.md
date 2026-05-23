## MODIFIED Requirements

### Requirement: Pong scripts use Godot-style lifecycle names

The Python scripts in `:games:pong/src/main/resources/pong/scripts/` SHALL use the Godot-style hook names exclusively: `_ready`, `_process(dt)`, `_physics_process(dt)`, `_draw(renderer)`, `_exit_tree`, `_on_collide(other)`. The legacy names `on_enter`, `on_update`, `on_render`, `on_exit`, `on_collide` MUST NOT appear in any script under that directory.

#### Scenario: No legacy hook names in Pong scripts

- **WHEN** any file under `games/pong/src/main/resources/pong/scripts/` is inspected
- **THEN** the file does not contain `def on_enter`, `def on_update`, `def on_render`, `def on_exit`, or `def on_collide`

#### Scenario: Ball runs in physics step

- **WHEN** `ball.py` is inspected
- **THEN** position integration (current `pos += v * dt` logic) lives in `_physics_process(self, dt)`
- **AND** no position update occurs in `_process` or `_draw`

#### Scenario: Paddle moves in physics step

- **WHEN** `paddle.py` is inspected
- **THEN** the AI/human direction integration lives in `_physics_process(self, dt)`
- **AND** the human-input read (current key) MAY happen in `_process` (variable dt) or be polled at physics step start — implementation choice — but resulting motion MUST be in `_physics_process`

### Requirement: Pong communicates score via Signal, not ad-hoc callback

`ball.py` SHALL declare a top-level `scored: Signal = signal(str)`. When the ball touches `leftGoal` or `rightGoal`, it SHALL `self.scored.emit("Right")` or `self.scored.emit("Left")` respectively (side scored, not side hit). The orchestrator script (`pong_scene.py`) SHALL `ball.scored.connect(self._on_scored)` during its `_ready`, and the handler `_on_scored(self, side)` SHALL increment the corresponding score label. The ad-hoc attribute `ball._on_score = callback` pattern MUST be removed.

#### Scenario: Ball declares the scored signal

- **WHEN** `ball.py` is inspected
- **THEN** it contains a top-level `scored: Signal = signal(str)` declaration

#### Scenario: Ball emits the scored signal on goal hit

- **WHEN** `_on_collide(self, other)` runs and `other.name == "leftGoal"`
- **THEN** `self.scored.emit("Right")` is called exactly once for that collision

#### Scenario: Orchestrator connects via signal

- **WHEN** `pong_scene.py` is inspected
- **THEN** its `_ready` looks up the `ball` node via `NodeRef` and calls `ball.scored.connect(self._on_scored)`
- **AND** no assignment of the form `ball._on_score = ...` exists anywhere in `games/pong/`

#### Scenario: No ad-hoc callback attribute

- **WHEN** any file under `games/pong/src/main/resources/pong/scripts/` is inspected
- **THEN** no occurrence of `_on_score` as an instance attribute exists
- **AND** no `hasattr(self, '_on_score')` check exists

### Requirement: Pong reads viewport from the scene, not a prop

The `playFieldHeight` prop on `paddle.py` SHALL be removed. The paddle clamping logic SHALL read `self.rootScene().viewport.size.y` (or equivalent) at clamp time. The Pong `scene.json` SHALL include a `Camera2D` node with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `current = true`, declaring the play field as world bounds.

#### Scenario: paddle.py has no playFieldHeight export

- **WHEN** `paddle.py` is inspected
- **THEN** no top-level `playFieldHeight: float = ...` declaration exists
- **AND** the clamp logic reads `self.rootScene().viewport.size.y` (or an equivalent accessor on `Scene` returning the viewport height)

#### Scenario: Pong scene declares a Camera2D

- **WHEN** `pong.scene.json` is inspected
- **THEN** the root contains a child of type `com.neoutils.engine.scene.Camera2D` with `current: true` and `bounds: Rect(Vec2.ZERO, Vec2(800f, 600f))`

### Requirement: Pong visuals use new primitive nodes and direct _draw

The Pong scripts SHALL no longer reference `Shape`. Paddles and the ball SHALL render via `_draw(self, renderer)` inside their own script (drawing rect/circle directly). The `centerLine` node SHALL be a declarative `Line2D` child in `scene.json` (no script). Score labels SHALL use `Label`, not `Text`. The previous `center_line.py` script SHALL be removed.

#### Scenario: No Shape references in Pong

- **WHEN** any file under `games/pong/src/main/resources/pong/` is inspected
- **THEN** no occurrence of the identifier `Shape` exists (neither in JSON `type` fields nor as Python references)

#### Scenario: Paddle and ball draw via _draw

- **WHEN** `paddle.py` and `ball.py` are inspected
- **THEN** each defines `_draw(self, renderer)` that issues the rect/circle draw
- **AND** neither script attaches a `ColorRect`/`Circle2D` child node for visual purposes

#### Scenario: centerLine is a Line2D in scene.json

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `centerLine` node has `type: "com.neoutils.engine.scene.Line2D"`
- **AND** it has no `script` field

#### Scenario: center_line.py is removed

- **WHEN** `games/pong/src/main/resources/pong/scripts/` is listed
- **THEN** there is no file named `center_line.py`

#### Scenario: Score nodes use Label

- **WHEN** `score.py` and `pong.scene.json` are inspected
- **THEN** the type of the score nodes (or their visual children) references `com.neoutils.engine.scene.Label`
- **AND** no occurrence of the identifier `Text` exists
