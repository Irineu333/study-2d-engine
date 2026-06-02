# debug-physics-gizmos Specification

## Purpose

Tornar o passo de colisão observável expondo o que o `PhysicsSystem` já
calcula: os **vetores de velocidade** dos corpos e os **pontos de contato e
normais** — tanto os que o solver de impulso usou (`RigidBody2D`) quanto os
resolvidos por `CharacterBody2D.moveAndCollide` (jogos cinemáticos como
Pong/Snake). Dois `WorldDebugWidget` (`VelocityGizmoWidget`,
`ContactGizmoWidget`), um buffer de contatos por-`SceneTree` com staging para
contatos cinemáticos consolidados no `PhysicsSystem.step` (custo zero quando
desabilitado), e o registro dos dois como built-ins togglável no `DebugHud`. A
**geometria real** de cada collider (contorno de círculo, quad rotacionado de
retângulo) é desenhada pelo `ColliderWidget` no modo `REAL` (ver capability
`debug-overlay`); o `RectangleShape2D.worldCorners` que ela usa permanece
especificado aqui.

## Requirements


### Requirement: RectangleShape2D exposes world corners

`RectangleShape2D` SHALL expose a public `fun worldCorners(world: Transform):
List<Vec2>` returning its four corners in world space, honoring
`world.position`, `world.rotation`, and `world.scale` (so a rotated
rectangle yields a rotated quad, not an axis-aligned box). The order SHALL
be documented and stable (top-left, top-right, bottom-right, bottom-left
forming a closed loop). This promotes the existing OBB-corner logic from a
private helper to public API.

#### Scenario: Unrotated rectangle corners match its AABB

- **GIVEN** a `RectangleShape2D` with `size = Vec2(10, 20)` and a `world` transform with `position = (0, 0)`, `rotation = 0`, `scale = (1, 1)`
- **WHEN** `worldCorners(world)` is called
- **THEN** the four corners SHALL be `(0,0)`, `(10,0)`, `(10,20)`, `(0,20)` (in the documented order)

#### Scenario: Rotated rectangle yields a rotated quad

- **GIVEN** a `RectangleShape2D` rotated by a non-zero `world.rotation`
- **WHEN** `worldCorners(world)` is called
- **THEN** the four returned points SHALL NOT be axis-aligned
- **AND** their centroid SHALL equal the rectangle's world center within float tolerance

### Requirement: VelocityGizmoWidget draws body velocity vectors

`VelocityGizmoWidget` SHALL extend `WorldDebugWidget` and, when `enabled`,
SHALL walk every live, non-disabled `RigidBody2D` and `CharacterBody2D` and
draw a line from an anchor along its linear velocity scaled by a configurable
scale factor (`var velocityScale: Float` — named to avoid shadowing
`Node2D.scale: Vec2`). The anchor SHALL be the world-space centroid of the
body's active collision shapes (falling back to the node's world position when
it has none): linear velocity is identical at every point of a body, so the
anchor is cosmetic, and centering it on the shapes keeps the arrow on the
visible body even when the node origin sits off-center. A body with zero
linear velocity SHALL produce no line. It SHALL NOT call
`pushTransform`/`popTransform`.

#### Scenario: Moving rigid body gets a velocity line

- **GIVEN** a `RigidBody2D` whose active collision shapes are centered at world point `p`, with `linearVelocity = v` (non-zero), widget enabled with `velocityScale = s`
- **WHEN** a frame is rendered against a recording `Renderer`
- **THEN** a `drawLine` from `p` to `p + v * s` SHALL be observed

#### Scenario: Stationary body produces no line

- **GIVEN** a `CharacterBody2D` with zero velocity, widget enabled
- **WHEN** a frame is rendered
- **THEN** no velocity line SHALL be drawn for that body

### Requirement: PhysicsSystem records resolved contacts when enabled

`SceneTree.debug` SHALL own a per-tree contact buffer holding immutable
`ContactRecord(point: Vec2, normal: Vec2)` entries, never `@Serializable`
and never shared across trees. When contact recording is enabled (driven by
`ContactGizmoWidget.enabled`), `PhysicsSystem.step` SHALL clear the buffer at
the start of the step and append one `ContactRecord` per resolved contact
(world-space `point` and unit `normal` from the contact's `SweepResult`)
during resolution. When recording is disabled, `PhysicsSystem.step` SHALL
record nothing and SHALL incur no per-contact recording cost.

#### Scenario: Contacts recorded only while enabled

- **GIVEN** two bodies that collide during a `step`, with contact recording disabled
- **WHEN** `PhysicsSystem.step(tree, dt)` runs
- **THEN** the contact buffer SHALL be empty
- **WHEN** contact recording is then enabled and another colliding `step` runs
- **THEN** the buffer SHALL contain at least one `ContactRecord` with the resolved world `point` and unit `normal`

#### Scenario: Buffer is cleared each step

- **GIVEN** contact recording enabled and a buffer populated by a prior step
- **WHEN** a subsequent `step` runs in which no contacts occur
- **THEN** the buffer SHALL be empty after that step

### Requirement: ContactGizmoWidget draws recorded contacts and toggles recording

`ContactGizmoWidget` SHALL extend `WorldDebugWidget`. Its `enabled` SHALL
drive contact recording in `PhysicsSystem.step` (enabling it turns recording
on; disabling it turns recording off). When `enabled`, `drawDebug` SHALL
draw, for each `ContactRecord` in the buffer, a marker at `point` and a line
from `point` along `normal` (a short fixed length) to visualize the contact
normal. It SHALL NOT call `pushTransform`/`popTransform`.

#### Scenario: Each recorded contact yields a point and a normal line

- **GIVEN** the buffer holds one `ContactRecord(point = p, normal = n)` and the widget is enabled
- **WHEN** a frame is rendered against a recording `Renderer`
- **THEN** a marker SHALL be drawn at `p`
- **AND** a `drawLine` from `p` along `n` SHALL be observed

#### Scenario: Enabling the widget turns recording on

- **GIVEN** `ContactGizmoWidget.enabled = false` and an empty buffer
- **WHEN** `ContactGizmoWidget.enabled = true` is set and a colliding `step` runs
- **THEN** the buffer SHALL be populated with that step's contacts

### Requirement: PhysicsContactBuffer stages kinematic contacts

`PhysicsContactBuffer` SHALL expose, alongside its persistent `records`, a
staging area for contacts captured **before** `PhysicsSystem.step` runs in a
physics substep. It SHALL provide a way to stage a `(point, normal)` pair and
a way for `PhysicsSystem.step` to fold every staged pair into `records` and
empty the staging area in one operation. The staging area SHALL be runtime
only (never `@Serializable`, never shared across trees) and SHALL be cleared
by the fold so staged contacts are consolidated exactly once.

#### Scenario: Staged contacts fold into records

- **GIVEN** a `PhysicsContactBuffer` with one staged `(point, normal)` pair and empty `records`
- **WHEN** the fold operation runs
- **THEN** `records` SHALL contain that pair
- **AND** the staging area SHALL be empty

#### Scenario: Fold clears prior records first

- **GIVEN** a `PhysicsContactBuffer` whose `records` already holds a pair from a previous step and whose staging area holds a new pair
- **WHEN** the fold operation runs (the start-of-step clear+consolidate)
- **THEN** `records` SHALL contain only the newly staged pair, not the prior one

### Requirement: moveAndCollide records the resolved kinematic contact

`CharacterBody2D.moveAndCollide` SHALL stage the resolved contact — the
`point` and unit `normal` of the `KinematicCollision2D` it returns — into the
tree's `PhysicsContactBuffer` when contact recording is enabled (driven by
`ContactGizmoWidget.enabled`, mirrored onto `PhysicsContactBuffer.recording`).
A `moveAndCollide` call that does not hit (returns `null`) SHALL stage nothing.
When recording is disabled, `moveAndCollide` SHALL stage nothing and SHALL
incur no recording cost. The staged `point`/`normal` are expressed in the
frame `moveAndCollide` operates in (the body's parent frame), matching the
frame the `RigidBody2D` contact path already records in.

#### Scenario: Kinematic contact staged only while enabled

- **GIVEN** a `CharacterBody2D` that hits a `StaticBody2D` during `moveAndCollide`, with contact recording disabled
- **WHEN** `moveAndCollide` resolves the contact
- **THEN** the contact buffer's staging area SHALL be empty
- **WHEN** recording is then enabled and another colliding `moveAndCollide` runs
- **THEN** the staging area SHALL hold one `(point, normal)` matching the returned `KinematicCollision2D`

#### Scenario: A miss stages nothing

- **GIVEN** contact recording enabled and a `CharacterBody2D` whose motion hits nothing
- **WHEN** `moveAndCollide` returns `null`
- **THEN** the contact buffer's staging area SHALL remain empty

### Requirement: Step consolidates kinematic and rigid contacts in one buffer

When contact recording is enabled, `PhysicsSystem.step` SHALL, at the start of
the step, fold the contacts staged since the previous step into `records`
(replacing the previous step's records) before resolving and appending the
`RigidBody2D` contacts of this step. The resulting `records` SHALL therefore
contain both this substep's kinematic contacts (from `moveAndCollide` during
`_physics_process`) and its rigid contacts (from the impulse solver), so the
`ContactGizmoWidget` draws both. When recording is disabled, `step` SHALL fold
nothing and incur no recording cost.

#### Scenario: Kinematic and rigid contacts coexist after a step

- **GIVEN** contact recording enabled, a `CharacterBody2D` that staged a contact via `moveAndCollide`, and a `RigidBody2D` that resolves a contact during the same `step`
- **WHEN** `PhysicsSystem.step(tree, dt)` runs
- **THEN** the buffer's `records` SHALL contain both the kinematic contact and the rigid contact

#### Scenario: A purely kinematic game populates the buffer

- **GIVEN** a tree whose only colliding bodies are `CharacterBody2D` (no `RigidBody2D`), contact recording enabled
- **WHEN** a `_physics_process` runs `moveAndCollide` into a contact and the following `PhysicsSystem.step` runs
- **THEN** the buffer's `records` SHALL contain the kinematic contact (so `ContactGizmoWidget` shows it in games like Pong)

#### Scenario: Buffer stays single-substep

- **GIVEN** contact recording enabled and `records` populated by a prior step
- **WHEN** a subsequent substep runs in which no contacts occur (no `moveAndCollide` hit, no rigid contact)
- **THEN** `records` SHALL be empty after that step

### Requirement: Physics gizmos are registered built-ins toggled via the HUD

The engine SHALL register `VelocityGizmoWidget` and `ContactGizmoWidget` as
built-in widgets during `DebugLayer` auto-insertion, hosted in the
`WorldDebugContainer`, and SHALL expose them as convenience fields on
`DebugRegistry`. Each SHALL appear as its own togglable row in the `DebugHud`.
Both SHALL default to `enabled = false`. The engine SHALL NOT register a
separate `ShapeGizmoWidget` — real collider geometry is drawn by
`ColliderWidget` via its `REAL` mode (see capability `debug-overlay`).

#### Scenario: The two gizmos are present and world-hosted

- **WHEN** `SceneTree.start()` has completed
- **THEN** the `DebugRegistry` convenience fields for the velocity and contact gizmos SHALL be non-null
- **AND** each gizmo's `parent` SHALL be the `WorldDebugContainer` instance
- **AND** each SHALL appear in `tree.debug.widgets`
- **AND** no `ShapeGizmoWidget` instance SHALL exist under `DebugLayer`

#### Scenario: HUD lists a row per gizmo

- **WHEN** the `DebugHud` is opened
- **THEN** rows for the velocity and contact gizmos SHALL each be present and individually togglable
