## ADDED Requirements

### Requirement: Paddles are script-controlled CharacterBody2D solids

Each paddle SHALL be a `com.neoutils.engine.physics.CharacterBody2D` (NOT a `StaticBody2D`), honoring engine invariant #3: a solid body moved by a script is a `CharacterBody2D` driven through `moveAndCollide`, never a `StaticBody2D` teleported by writing `position` directly. The paddle script SHALL declare `# extends CharacterBody2D`, and the scene node SHALL be typed `CharacterBody2D` — both kept in sync because the script's `extends` is validated by simple name against `NodeRegistry` and MUST match the node's class.

Each tick the paddle SHALL compute its desired vertical displacement `dy` (from input or AI) and apply it with `moveAndCollide(Vec2(0, dy))`, so the motion is swept: the paddle **stops at the contact** with any other `PhysicsBody2D` instead of teleporting into it. As a direct consequence, a paddle MUST NOT be able to push the ball through or into a wall (the squeeze trap is removed at the source). After `moveAndCollide`, the paddle SHALL re-pin its horizontal coordinate to the value it had before the move, so any horizontal depenetration from a transient overlap cannot make the paddle drift off its column. The paddle SHALL remain within the play field by colliding with the top and bottom wall bodies (swept), NOT by a hand-written numeric clamp; the reachable vertical range is therefore bounded by the walls.

#### Scenario: Paddle nodes are CharacterBody2D

- **WHEN** the Pong scene is loaded
- **THEN** both paddle nodes (`left` and `right`) are instances of `com.neoutils.engine.physics.CharacterBody2D`
- **AND** the paddle script declares `# extends CharacterBody2D`
- **AND** no paddle node is a `StaticBody2D`

#### Scenario: Paddle moves via moveAndCollide, not by writing position

- **WHEN** `paddle.py::_physics_process` runs with a non-zero desired displacement
- **THEN** the paddle advances by calling `moveAndCollide(Vec2(0, dy))`
- **AND** it does NOT set `self.position` to the unswept target as its movement mechanism

#### Scenario: Paddle stops at the ball instead of squeezing it

- **WHEN** the paddle moves toward the ball while the ball rests against a wall
- **THEN** the paddle's swept motion stops at contact with the ball
- **AND** the ball is not pushed into or through the wall, and does not become trapped

#### Scenario: Paddle stays on its horizontal column

- **WHEN** the paddle begins a tick transiently overlapping the ball and `moveAndCollide` reports a depenetration with a horizontal component
- **THEN** after the move the paddle's `position.x` equals its `position.x` at the start of the tick
- **AND** the paddle never drifts horizontally out of its lane

#### Scenario: Paddle is bounded by the walls, not a numeric clamp

- **WHEN** the paddle is driven continuously toward the top or bottom wall
- **THEN** it stops against the wall body via swept collision
- **AND** `paddle.py` contains no hand-written `[0, max_y]` position clamp

## MODIFIED Requirements

### Requirement: Ball physics

The Ball SHALL be a `com.neoutils.engine.physics.CharacterBody2D` whose collision shape is a `CircleShape2D`. Each tick the Ball SHALL advance its motion with `moveAndCollide(velocity * dt)` (Godot-style kinematic sweep), so it stops exactly at the first contact with no tunneling at high speed; the engine does NOT integrate `velocity` automatically. On a resolved contact the Ball SHALL react in script by classifying the contact:

- **Paddle face** — the contact is a paddle (group `paddles`) AND the ball's center lies within the paddle's vertical face span. The Ball applies the angle-based "english" bounce: the horizontal direction flips away from the paddle and the vertical component is derived from where the ball struck the face, with the speed allowed to increase modestly per hit up to a configured maximum.
- **Paddle top/bottom edge, paddle corner, or wall** — every other contact. The Ball reflects its velocity across the contact normal (`v' = v - 2(v·n)n`).

Classification MUST be geometric (ball center within the paddle's vertical face span), NOT a comparison of the contact normal's dominant axis (`abs(n.x) > abs(n.y)`), so that a diagonal corner normal (`|n.x| ≈ |n.y|`) is resolved deterministically and never traps the ball. Walls and goals keep their current roles: walls are solid bodies picked up by the sweep; goals are `Area2D` sensors handled on the `_on_area_entered` path.

#### Scenario: Ball reflects off top wall

- **WHEN** the ball's swept motion contacts the top wall while moving upward
- **THEN** its velocity is reflected across the wall's (vertical-facing) contact normal, negating the Y component
- **AND** the X component is unchanged

#### Scenario: Ball gets the english bounce on a paddle face

- **WHEN** the ball's swept motion contacts a paddle and the ball's center is within the paddle's vertical face span
- **THEN** the horizontal direction flips away from the paddle face
- **AND** the vertical component is set from the ball's offset along the face (the angle-based bounce)
- **AND** the ball does not pass through the paddle on the next tick

#### Scenario: Ball corner hit does not trap

- **WHEN** the ball contacts a paddle corner, producing a near-diagonal contact normal
- **THEN** the contact is classified by the ball-center-vs-face-span geometry, not by `abs(n.x) > abs(n.y)`
- **AND** the resulting velocity carries the ball away from the paddle, never oscillating in place at the corner
