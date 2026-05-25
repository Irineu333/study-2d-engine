## MODIFIED Requirements

### Requirement: Pong scripts use Godot-style lifecycle names

The Python scripts in `:games:pong/src/main/resources/pong/scripts/` SHALL use the Godot-style hook names exclusively: `_ready`, `_process(dt)`, `_physics_process(dt)`, `_draw(renderer)`, `_exit_tree`, `_on_collide(other)`. The legacy names `on_enter`, `on_update`, `on_render`, `on_exit`, `on_collide` MUST NOT appear in any script under that directory.

Pong scripts SHALL additionally use the ergonomic accessors introduced in this change: writes to local transform components go through `self.position = Vec2(...)`, `self.rotation = ...`, and `self.scale = Vec2(...)`; world-space reads go through `self.world()` (or `self.world().position`). The legacy spellings `self.transform = Transform(Vec2(...), self.transform.scale, self.transform.rotation)` and `self.worldPosition()` MUST NOT appear in any Pong script after this change.

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

#### Scenario: Paddle uses ergonomic position accessor

- **WHEN** `paddle.py` is inspected
- **THEN** position writes go through `self.position = Vec2(...)` (not `self.transform = Transform(...)`)
- **AND** any world-space read uses `self.world().position` (not `self.worldPosition()`)

#### Scenario: No worldPosition call sites remain in Pong scripts

- **WHEN** any file under `games/pong/src/main/resources/pong/scripts/` is grepped for `worldPosition`
- **THEN** no matches are found
