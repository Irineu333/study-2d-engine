## MODIFIED Requirements

### Requirement: Pong uses Godot-style collision nodes

The Pong scene SHALL be rewritten with the new collision taxonomy. Specifically:

- The ball SHALL be a `CharacterBody2D` with a child `CollisionShape2D` whose `shape` is a `CircleShape2D`.
- `topWall` and `bottomWall` SHALL be `StaticBody2D` instances, each with a child `CollisionShape2D` (`RectangleShape2D`) sized to span the play field width and positioned at the actual wall location (not the (0,0) placeholder of the previous era).
- `leftGoal` and `rightGoal` SHALL be `Area2D` instances, each with a child `CollisionShape2D` (`RectangleShape2D`) positioned at the play field's left/right boundary.
- Paddles (`left`, `right`) SHALL be `StaticBody2D` instances with a child `CollisionShape2D` (`RectangleShape2D`), declared in `scene.json` — the paddle script SHALL NOT create the collider in `_ready`.
- The `pong.scene.json` SHALL NOT contain any reference to the identifiers `BoxCollider` or `Collider`.

#### Scenario: pong.scene.json has no BoxCollider references

- **WHEN** `games/pong/src/main/resources/pong/pong.scene.json` is inspected
- **THEN** the file does not contain the string `BoxCollider`
- **AND** the file does not contain the string `Collider"` (the closing-quote variant catches any leftover)

#### Scenario: Ball is a CharacterBody2D with CircleShape2D

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `Ball` node has `type: "com.neoutils.engine.physics.CharacterBody2D"`
- **AND** the `Ball` node has at least one child of type `com.neoutils.engine.physics.CollisionShape2D` whose `shape` is `CircleShape2D`

#### Scenario: Goals are Area2D

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `leftGoal` and `rightGoal` nodes have `type: "com.neoutils.engine.physics.Area2D"`
- **AND** each has a `CollisionShape2D` child with a `RectangleShape2D`

#### Scenario: Paddles are StaticBody2D with declared shape

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `left` and `right` nodes have `type: "com.neoutils.engine.physics.StaticBody2D"`
- **AND** each has a `CollisionShape2D` child with a `RectangleShape2D(size = Vec2(16f, 96f))` declared inline (not created in `_ready`)

### Requirement: Pong scripts use enter/exit collision hooks

The Pong scripts SHALL replace the legacy `_on_collide(self, other)` hook with the four Godot-style enter/exit hooks: `_on_area_entered(self, area)`, `_on_area_exited(self, area)`, `_on_body_entered(self, body)`, `_on_body_exited(self, body)`. The string `_on_collide` SHALL NOT appear in any Pong script. Discrimination between walls and paddles in `ball.py` SHALL use `body.is_in_group("walls")` and `body.is_in_group("paddles")` rather than `body.name in ("left", "right")` or `other.parent.name`.

#### Scenario: ball.py uses area/body hooks

- **WHEN** `games/pong/src/main/resources/pong/scripts/ball.py` is inspected
- **THEN** it defines `_on_area_entered(self, area)` to handle goals
- **AND** it defines `_on_body_entered(self, body)` to handle wall and paddle bounces
- **AND** it does NOT define `_on_collide`

#### Scenario: Ball discriminates by group, not by name

- **WHEN** `ball.py` is inspected
- **THEN** the body-entered handler reads `body.is_in_group("walls")` or `body.is_in_group("paddles")`
- **AND** the handler does not branch on `body.name in (...)` or `body.parent.name == ...`

#### Scenario: Ball is a CharacterBody2D and uses self.velocity

- **WHEN** `ball.py` is inspected
- **THEN** the script declares `# extends CharacterBody2D` on its first non-empty line
- **AND** velocity is read/written as `self.velocity` (the inherited slot), not as a private attribute like `self._velocity`

#### Scenario: Paddle is a StaticBody2D without ad-hoc collider creation

- **WHEN** `paddle.py` is inspected
- **THEN** the script declares `# extends StaticBody2D` on its first non-empty line
- **AND** the script does not call `addChild(BoxCollider())` or any equivalent — the collision shape is declared in `pong.scene.json`

#### Scenario: Goal scripts extend Area2D

- **WHEN** `goal.py` is inspected
- **THEN** the script declares `# extends Area2D`

### Requirement: Pong walls and paddles join groups for behavior discrimination

The `pong.scene.json` SHALL declare `groups` on walls and paddles so that the ball script can branch on intent rather than node names:

- `topWall`, `bottomWall` SHALL be members of the group `"walls"`.
- `left`, `right` (paddles) SHALL be members of the group `"paddles"`.

The groups MAY be declared via the `properties.groups` field of the node JSON (preferred) or via `_ready` calls to `add_to_group` in their scripts (acceptable fallback).

#### Scenario: Walls are in the walls group

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `topWall` and `bottomWall` nodes contain `"walls"` in their `properties.groups` array (or their script's `_ready` calls `self.add_to_group("walls")`)

#### Scenario: Paddles are in the paddles group

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `left` and `right` nodes contain `"paddles"` in their `properties.groups` array (or equivalent)
