## ADDED Requirements

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

### Requirement: ShapeGizmoWidget draws real collider geometry

`ShapeGizmoWidget` SHALL extend `WorldDebugWidget` and, when `enabled`,
SHALL iterate `collectActiveCollisionShapes(tree)` and draw each shape's
**real geometry** (not its AABB): a non-filled circle outline for
`CircleShape2D` (world center and scaled radius), and the closed quad of
`worldCorners` for `RectangleShape2D` (covering the rotated case). It SHALL
NOT call `pushTransform`/`popTransform` — the world pass applies the
`Camera2D` view transform. The existing `ColliderWidget` (AABB) SHALL remain
unchanged and SHALL NOT be replaced by this widget.

#### Scenario: Circle drawn as outline at world center

- **GIVEN** an active `CircleShape2D` of radius `r` at world center `c`, with `tree.debug.find<ShapeGizmoWidget>()!!.enabled = true`
- **WHEN** a frame is rendered against a recording `Renderer`
- **THEN** a non-filled `drawCircle(c, r, _)` SHALL be observed
- **AND** zero `pushTransform`/`popTransform` calls SHALL be attributed to the widget

#### Scenario: Rotated rectangle drawn as a rotated quad

- **GIVEN** an active `RectangleShape2D` with non-zero world rotation and the widget enabled
- **WHEN** a frame is rendered
- **THEN** the four edges between consecutive `worldCorners` SHALL be drawn as lines
- **AND** the drawn quad SHALL NOT coincide with the shape's axis-aligned `worldBounds()`

#### Scenario: Disabled widget draws nothing

- **GIVEN** `ShapeGizmoWidget.enabled = false`
- **WHEN** a frame is rendered
- **THEN** zero draw calls SHALL be attributed to the widget

### Requirement: VelocityGizmoWidget draws body velocity vectors

`VelocityGizmoWidget` SHALL extend `WorldDebugWidget` and, when `enabled`,
SHALL walk every live, non-disabled `RigidBody2D` and `CharacterBody2D` and
draw a line from the body's world position along its linear velocity scaled
by a configurable `var scale: Float`. A body with zero linear velocity SHALL
produce no line. It SHALL NOT call `pushTransform`/`popTransform`.

#### Scenario: Moving rigid body gets a velocity line

- **GIVEN** a `RigidBody2D` at world position `p` with `linearVelocity = v` (non-zero), widget enabled with `scale = s`
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

### Requirement: Physics gizmos are registered built-ins toggled via the HUD

The engine SHALL register `ShapeGizmoWidget`, `VelocityGizmoWidget`, and
`ContactGizmoWidget` as built-in widgets during `DebugLayer` auto-insertion,
hosted in the `WorldDebugContainer`, and SHALL expose them as convenience
fields on `DebugRegistry`. Each SHALL appear as its own togglable row in the
`DebugHud`. All three SHALL default to `enabled = false`.

#### Scenario: The three gizmos are present and world-hosted

- **WHEN** `SceneTree.start()` has completed
- **THEN** the `DebugRegistry` convenience fields for the three gizmos SHALL be non-null
- **AND** each gizmo's `parent` SHALL be the `WorldDebugContainer` instance
- **AND** each SHALL appear in `tree.debug.widgets`

#### Scenario: HUD lists a row per gizmo

- **WHEN** the `DebugHud` is opened
- **THEN** rows for the shape, velocity, and contact gizmos SHALL each be present and individually togglable
