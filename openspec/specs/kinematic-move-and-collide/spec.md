# kinematic-move-and-collide Specification

## Purpose
TBD - created by archiving change kinematic-move-and-collide. Update Purpose after archive.
## Requirements
### Requirement: KinematicCollision2D represents a swept collision result

The engine SHALL provide a `KinematicCollision2D` data class in package `com.neoutils.engine.physics` with the following fields, all immutable: `point: Vec2` (world-space contact point), `normal: Vec2` (unit vector pointing from the moving body outward from the collider it hit), `collider: CollisionObject2D` (reference to the body that was hit), `remainder: Vec2` (the portion of the original motion that was not consumed because the body stopped at TOI).

#### Scenario: KinematicCollision2D is a data class with immutable fields

- **WHEN** code instantiates `KinematicCollision2D(point = Vec2(5f, 0f), normal = Vec2(-1f, 0f), collider = someBody, remainder = Vec2(2f, 0f))`
- **THEN** the resulting instance exposes `point`, `normal`, `collider`, `remainder` as `val` properties
- **AND** the class participates in structural equality via `data class` semantics

### Requirement: CharacterBody2D exposes moveAndCollide

The engine SHALL add a public method `fun moveAndCollide(motion: Vec2): KinematicCollision2D?` on `CharacterBody2D`. The method MUST perform a swept-shape test from the body's current world position along `motion` against every other live `PhysicsBody2D` (both `StaticBody2D` and `CharacterBody2D`) in the tree with `disabled == false` whose parent matches `this.parent`. `Area2D` instances MUST be ignored by `moveAndCollide` (areas are triggers, not blockers). If no collision is detected, the body's `transform.position` MUST be advanced by `motion` and the method MUST return `null`. If a collision is detected with smallest time-of-impact `toi` in `[0f, 1f]`, the body's `transform.position` MUST be advanced by `motion * toi + sweepResult.depenetration` (the body stops at the contact and, when starting-overlapping, is pushed out by the depenetration vector) and the method MUST return a `KinematicCollision2D` whose `remainder` equals `motion * (1f - toi)`.

The method MUST support arbitrary rotations of `this` and of target bodies, provided they share the same parent (so the sweep is performed in the common parent frame). Rotations local to `this` or to the target NO LONGER cause the swept test to fall through.

#### Scenario: moveAndCollide with no obstacles advances the full motion

- **GIVEN** a `CharacterBody2D` at world position `(0f, 0f)` with a `CollisionShape2D + CircleShape2D(radius=5f)` and an empty tree of other bodies
- **WHEN** the script calls `body.moveAndCollide(Vec2(100f, 0f))`
- **THEN** the call returns `null`
- **AND** `body.transform.position` equals `Vec2(100f, 0f)` after the call

#### Scenario: moveAndCollide stops at the contact with a static body

- **GIVEN** a `CharacterBody2D` A at world position `(0f, 0f)` with `CircleShape2D(radius=5f)`
- **AND** a `StaticBody2D` B at world position `(20f, 0f)` with `CircleShape2D(radius=5f)`
- **WHEN** the script calls `A.moveAndCollide(Vec2(20f, 0f))`
- **THEN** the call returns a non-null `KinematicCollision2D`
- **AND** `A.transform.position.x` is approximately `10f` (A's edge touches B's edge)
- **AND** the returned `normal` is approximately `Vec2(-1f, 0f)`
- **AND** the returned `collider` is B
- **AND** the returned `remainder` is approximately `Vec2(10f, 0f)` (half the requested motion)

#### Scenario: moveAndCollide ignores Area2D in the path

- **GIVEN** a `CharacterBody2D` A at `(0f, 0f)` with `CircleShape2D(radius=5f)`
- **AND** an `Area2D` T at `(10f, 0f)` with `CircleShape2D(radius=5f)`
- **WHEN** the script calls `A.moveAndCollide(Vec2(40f, 0f))`
- **THEN** the call returns `null`
- **AND** `A.transform.position` equals `Vec2(40f, 0f)`

#### Scenario: moveAndCollide returns TOI=0 with depenetration when bodies start overlapping

- **GIVEN** two `CharacterBody2D` A and B both with overlapping `CircleShape2D` (radius=5f each), with B center 4f away from A center
- **WHEN** the script calls `A.moveAndCollide(Vec2.ZERO)`
- **THEN** the call returns a non-null `KinematicCollision2D` with `toi == 0f` and `remainder == Vec2.ZERO`
- **AND** `A.transform.position` is advanced by the depenetration vector (separating A from B by the penetration depth along the contact normal)

#### Scenario: moveAndCollide on a rotated body bounces against a rotated wall

- **GIVEN** a `CharacterBody2D` A with `aWorld.rotation = π / 6` and `RectangleShape2D` size `(10f, 10f)`
- **AND** a `StaticBody2D` B in the path with `bWorld.rotation = π / 6` and `RectangleShape2D` size `(10f, 60f)`
- **WHEN** the script calls `A.moveAndCollide(motion)` with motion approaching B frontally in the shared rotated frame
- **THEN** the call returns a non-null `KinematicCollision2D` with a `toi` in `(0f, 1f)`
- **AND** the returned `normal` is the contact normal in the parent frame
- **AND** A's position advances by `motion * toi` (stops at contact, no tunneling)

### Requirement: Shape2D supports sweepOverlap for axis-aligned shape pairs

The engine SHALL provide a top-level function `fun sweepOverlap(a: Shape2D, aWorld: Transform, motion: Vec2, b: Shape2D, bWorld: Transform): SweepResult?` in `com.neoutils.engine.physics`. `SweepResult` MUST be a data class with `toi: Float` (in `[0f, 1f]`), `point: Vec2`, `normal: Vec2`, and `depenetration: Vec2` (defaulting to `Vec2.ZERO`). The function MUST return a non-null result with the smallest valid TOI when shape `a` swept by `motion` from `aWorld` would intersect shape `b` at `bWorld`, for any of the following pairs: (a) `CircleShape2D` vs `CircleShape2D`; (b) `CircleShape2D` vs `RectangleShape2D` (rect axis-aligned OR rotated); (c) `RectangleShape2D` vs `RectangleShape2D` (any combination of rotations on both transforms).

When the pair is `RectangleShape2D` vs `RectangleShape2D` and at least one transform has `rotation != 0f`, the function MUST use a temporal SAT (Separating Axis Theorem with motion-projection per axis) on the four candidate axes (two normals per OBB). When the pair is `CircleShape2D` vs `RectangleShape2D` (in either order) with the rect rotated, the function MUST transform the problem into the rect's local frame (inverse-rotate circle position and motion), reuse the axis-aligned circle-vs-rect math, and rotate the resulting `point` and `normal` back into the original frame.

For shapes returning `null`, the call MUST not panic — it MUST simply indicate "no swept contact found in `[0, 1]`". Callers (e.g. `CharacterBody2D.moveAndCollide`, `RigidBody2D` solver) interpret `null` as "advance the full motion".

`SweepResult.point` MUST be the **geometric contact point** at the moment of contact in the same frame as `aWorld`/`bWorld`, with these refinements per pair:

- **Circle vs Circle**: the point lies on circle A's surface at the moment of contact along the contact normal.
- **Circle vs Rectangle**: the point is the closest-point on the rectangle's surface to the circle center at the moment of contact (clamping circle-local coordinates inside the rect's local frame and rotating back when the rect is rotated).
- **Rectangle vs Rectangle**: the point is the vertex of the swept (moving) rect that is most penetrated along `-normal` at the moment of contact; vertices tied within a small epsilon are averaged so face-vs-face contacts collapse to the face midpoint.

`SweepResult.point` MUST NOT be the geometric center of either shape (which was an acceptable approximation in the prior contract but is insufficient for angular contact resolution).

#### Scenario: Swept circle-vs-circle returns the analytic TOI

- **GIVEN** circle A radius `5f` at `(0f, 0f)`, motion `(20f, 0f)`, circle B radius `5f` at `(12f, 0f)`
- **WHEN** code calls `sweepOverlap(a, aWorld, motion, b, bWorld)`
- **THEN** the result's `toi` is approximately `0.1f` (i.e. A travels `2f` of the `20f` motion before contact)
- **AND** the result's `normal` is approximately `Vec2(-1f, 0f)`

#### Scenario: Swept circle-vs-rect axis-aligned returns the analytic TOI

- **GIVEN** circle A radius `3f` at `(0f, 0f)`, motion `(10f, 0f)`, rect B `size=(4f, 4f)` at `(8f, 0f)`
- **WHEN** code calls `sweepOverlap(a, aWorld, motion, b, bWorld)` with both transforms at `rotation=0f`
- **THEN** the result's `toi` is approximately `0.5f`
- **AND** the result's `normal` is approximately `Vec2(-1f, 0f)`

#### Scenario: Swept rect-vs-rect axis-aligned returns the analytic TOI

- **GIVEN** rect A `size=(4f, 4f)` at `(0f, 0f)`, motion `(20f, 0f)`, rect B `size=(4f, 4f)` at `(10f, 0f)`
- **WHEN** code calls `sweepOverlap(a, aWorld, motion, b, bWorld)` with both transforms at `rotation=0f`
- **THEN** the result's `toi` is approximately `0.3f`
- **AND** the result's `normal` is approximately `Vec2(-1f, 0f)`

#### Scenario: Swept shape returns null when motion does not intersect

- **GIVEN** circle A radius `5f` at `(0f, 0f)`, motion `(10f, 0f)`, circle B radius `5f` at `(0f, 100f)`
- **WHEN** code calls `sweepOverlap(...)`
- **THEN** the function returns `null`

#### Scenario: Swept circle-vs-rotated-rect returns the analytic TOI in the rect's local frame

- **GIVEN** circle A radius `2f` at `(0f, 0f)`, motion `(20f, 0f)`, rect B `size=(4f, 4f)` at `(10f, 0f)` with `bWorld.rotation = π / 2` (B rotated 90° — local x becomes world y)
- **AND** in B's local frame the relevant face of B that A approaches is the local-bottom face at world `(10f, 0f)`
- **WHEN** code calls `sweepOverlap(...)`
- **THEN** the function returns a non-null `SweepResult`
- **AND** the `normal` corresponds to B's local-bottom-face normal rotated back to world (the value depends on the rotated geometry — the normal is the unit vector that the script can use to reflect)

#### Scenario: Swept rotated-rect-vs-rotated-rect returns the analytic TOI on aligned faces

- **GIVEN** rect A `size=(4f, 4f)` at `(0f, 0f)` with `aWorld.rotation = π / 4`, motion `(20f, 0f)`, rect B `size=(4f, 4f)` at `(10f, 0f)` with `bWorld.rotation = π / 4` (same rotation)
- **WHEN** code calls `sweepOverlap(...)`
- **THEN** the function returns a non-null `SweepResult`
- **AND** the `toi` is consistent with the geometric contact distance in the rotated frame

#### Scenario: Swept rotated-rect-vs-rotated-rect returns null when motion misses

- **GIVEN** rect A `size=(4f, 4f)` at `(0f, 0f)` with `aWorld.rotation = π / 4`, motion `(20f, 0f)`, rect B `size=(4f, 4f)` at `(0f, 100f)` with `bWorld.rotation = π / 4`
- **WHEN** code calls `sweepOverlap(...)`
- **THEN** the function returns `null`

#### Scenario: Starting-overlap on rotated pair returns toi=0 with depenetration

- **GIVEN** rect A `size=(10f, 10f)` at `(0f, 0f)` with `aWorld.rotation = π / 6` and rect B `size=(10f, 10f)` at `(5f, 0f)` with `bWorld.rotation = π / 6` (same rotation; deep overlap)
- **WHEN** code calls `sweepOverlap(a, aWorld, motion, b, bWorld)` with any `motion`
- **THEN** the function returns a non-null `SweepResult` with `toi == 0f`
- **AND** `depenetration` is a non-zero vector along the SAT axis of least overlap (the minimum-translation vector pointing from B toward A)

#### Scenario: SweepResult.point is on A's surface for circle-vs-circle, not at A's center

- **GIVEN** circle A radius `5f` at `(0f, 0f)`, motion `(20f, 0f)`, circle B radius `5f` at `(12f, 0f)`
- **WHEN** code calls `sweepOverlap(...)` and receives a non-null result
- **THEN** `result.point` lies on A's surface at the contact moment (its position is consistent with `centroA_at_contact + n * radiusA`)
- **AND** `result.point` is NOT equal to A's center at the contact moment (`(2f, 0f)`)

#### Scenario: SweepResult.point is the leading corner for rotated rect-vs-rect

- **GIVEN** rect A `size = (4f, 4f)` at `(0f, 0f)` with `rotation = π / 6` swept by motion `(20f, 0f)` against axis-aligned rect B in the path
- **WHEN** code calls `sweepOverlap(...)` and receives a non-null result
- **THEN** `result.point` corresponds to A's vertex with the smallest projection onto `n` (the leading corner — the vertex that penetrates first)
- **AND** `result.point` is NOT A's center

### Requirement: Behavioral integration test harness validates multi-frame sweep correctness

The engine test suite SHALL include a `BehavioralSweepTest` (or analogous) file in `engine/src/test/.../physics/` that exercises `CharacterBody2D.moveAndCollide` across **multiple frames** via a real `GameLoop` with a stub `Renderer` and stub `Input`. Each scenario MUST construct a scene, run N ticks at fixed `dt` (16.666 ms), record positions/velocities across frames, and assert **trajectory properties** rather than single-call outputs. The harness MUST cover at minimum:

- A bouncing body that should separate from a contact within K frames (no oscillation in place — guards against the tangent-leaving freeze pattern).
- A pair of bodies spawned overlapping that should reach mutual non-overlap within K frames (guards against the spawn-overlap freeze pattern).
- A body inside a rotated walled arena that, after N frames of bouncing, has covered measurable distance (guards against any rotated-sweep regression that re-introduces freezing).

The harness MUST NOT depend on `Skiko`, `Compose`, or any backend with display side-effects — it MUST run within `./gradlew :engine:test`.

#### Scenario: Bounce-then-separate harness asserts post-contact motion

- **GIVEN** a `CharacterBody2D` with initial velocity directed at a `StaticBody2D` wall
- **WHEN** the harness runs 30 frames via `GameLoop.tick(16_666_666L)`
- **THEN** at some frame `f` the body's position changes direction (bounce detected)
- **AND** for every frame `f+1` through `f+K`, the body's distance to the wall is strictly greater than at frame `f` (no re-collision within `K = 3` frames)

#### Scenario: Spawn-overlap harness asserts eventual separation

- **GIVEN** two `CharacterBody2D` spawned with overlapping shapes
- **WHEN** the harness runs `K = 5` frames
- **THEN** by the last frame the two bodies' shapes no longer overlap
- **AND** at no intermediate frame did the body's velocity oscillate (sign-flip more than once on the same axis without crossing a contact event)

#### Scenario: Rotated arena harness asserts measurable trajectory distance

- **GIVEN** a `CharacterBody2D` inside an arena bounded by four `StaticBody2D` walls at rotation `π / 4` (45°)
- **WHEN** the harness runs 60 frames with initial velocity at moderate speed
- **THEN** the cumulative Euclidean distance traveled by the body across all frames exceeds a threshold proportional to `|velocity| * dt * 60 * 0.5` (i.e. the body moves at least half the free-flight distance — confirming it is not frozen)

### Requirement: Demos module ships a rotated-sweep visualization scene

The `:games:demos` module SHALL include a scene that exercises rotated swept collision visually in runtime (currently `TumblingSwarmDemo`, selectable via the `DemoSwitcherRoot` digit key). The scene MUST host multiple bodies with non-zero `transform.rotation` (so every pair sweep routes through the rotated path, not the axis-aligned fast paths), arranged inside walls built from `StaticBody2D`.

Starting with the `add-rigid-body-2d` change, the bodies in this demo MUST be `RigidBody2D` instances (not `CharacterBody2D`), and the demo MUST NOT carry inline impulse math (no `resolveSquareSquare`, no `resolveSquareWall`, no `leadingOffset` helper). Linear AND angular response (including Coulomb friction tangential impulse) MUST be produced by the engine's solver from each body's `mass`, `inertia`, `restitution`, and `friction` configuration. The squares MUST have `restitution = 1f` (perfectly elastic — preserves the visible KE of the original demo) and `friction > 0f` (preserves the rolling-on-walls behavior of the original demo). Initial `linearVelocity` and `angularVelocity` MUST be set directly on the body (not on private `vx`/`vy`/`angularVel` fields).

Similarly, `CollisionStressDemo` MUST host `RigidBody2D` balls. Each ball MUST set `restitution = 1f` and `friction = 0f` to preserve the visible elastic-bouncing behavior of the original demo. The demo MUST NOT call `moveAndCollide` or apply `vel.reflect(normal)` in `onPhysicsProcess` — the engine integrates and resolves.

#### Scenario: Tumbling squares are RigidBody2D after the change

- **WHEN** the `:games:demos` source tree is inspected after the `add-rigid-body-2d` change
- **THEN** `TumblingSquare` extends `RigidBody2D`, not `CharacterBody2D`
- **AND** `TumblingSwarmDemo.kt` no longer contains a `resolveSquareSquare`, `resolveSquareWall`, or `leadingOffset` helper
- **AND** `TumblingSquare.onPhysicsProcess` either does not exist or contains no contact-resolution math

#### Scenario: Stress balls are RigidBody2D with restitution 1

- **WHEN** the `:games:demos` source tree is inspected after the `add-rigid-body-2d` change
- **THEN** `Ball` extends `RigidBody2D`, not `CharacterBody2D`
- **AND** `Ball`'s constructor or `init` sets `restitution = 1f` and `friction = 0f`
- **AND** `Ball.onPhysicsProcess` either does not exist or does not call `moveAndCollide`

#### Scenario: A rotated body's corner hit against a wall induces spin (engine-resolved)

- **GIVEN** a `RigidBody2D` square at rotation `π / 6` moving frontally into a `StaticBody2D` wall (so contact is along an OBB corner, not a flat face)
- **WHEN** the engine's physics tick processes the collision
- **THEN** the body's `angularVelocity` after the collision differs from before by a non-zero amount (lever arm of the contact corner relative to the body's center is non-zero; the impulse produces angular change)

#### Scenario: A rotated body sliding along a wall picks up rolling (engine-resolved)

- **GIVEN** a `RigidBody2D` square with `friction > 0f` in tangential motion along a wall (velocity component parallel to the wall is non-zero at the contact)
- **WHEN** the engine's solver applies the Coulomb-friction tangential impulse
- **THEN** the body's `angularVelocity` after the contact has changed in a sense consistent with rolling (sliding direction transfers to spin), bounded by the `μ * |jn|` cap

#### Scenario: Pair contact between two RigidBody2D squares conserves angular momentum locally

- **GIVEN** two `RigidBody2D` squares with `restitution = 1f`, `friction = 0f`, colliding in a glancing offset hit (the contact normal is not aligned with the line between centers)
- **WHEN** the engine's solver applies the elastic impulse with the real contact point from `SweepResult.point`
- **THEN** the angular impulse magnitudes applied to the two bodies are of comparable order (one body's lever arm does not dominate the other by an unbounded factor)
- **AND** the per-collision update preserves total linear + angular momentum and total kinetic energy within float precision (verifiable via `tree.totalLinearMomentum()`, `tree.totalAngularMomentum()`, `tree.totalKineticEnergy()`)

### Requirement: Area2D exposes persistent overlap queries

The engine SHALL add two methods to `Area2D`: `fun getOverlappingAreas(): List<Area2D>` and `fun getOverlappingBodies(): List<PhysicsBody2D>`. These methods MUST return, in unspecified order, every peer that currently overlaps `this` according to the `PhysicsSystem`'s last completed dispatch. Calling these methods outside of an attached live tree MUST return an empty list. The returned lists MUST be snapshots (mutations to them MUST NOT affect engine state).

#### Scenario: getOverlappingAreas returns peers currently in overlap

- **GIVEN** an `Area2D` A and an `Area2D` B whose shapes overlap, both live in the tree
- **AND** `PhysicsSystem.step` has just run and dispatched `_entered`
- **WHEN** the script calls `A.getOverlappingAreas()`
- **THEN** the returned list contains B
- **AND** does not contain A itself

#### Scenario: getOverlappingBodies returns physics body peers, not areas

- **GIVEN** an `Area2D` A overlapping with both another `Area2D` X and a `StaticBody2D` Y
- **WHEN** the script calls `A.getOverlappingBodies()`
- **THEN** the returned list contains Y
- **AND** does not contain X

#### Scenario: Queries on detached areas return empty

- **GIVEN** an `Area2D` A constructed but never added to a live tree
- **WHEN** the script calls `A.getOverlappingAreas()`
- **THEN** the returned list is empty (no exception)

#### Scenario: Queries inside _on_area_entered see the current state including the caller

- **GIVEN** `Area2D` A about to enter overlap with `Area2D` B for the first time
- **WHEN** the engine dispatches `A.onAreaEntered(B)` and inside that callback the script calls `A.getOverlappingAreas()`
- **THEN** the returned list contains B (the pair has been added to the overlap set before dispatch)

### Requirement: moveAndCollide preserves coexistence with PhysicsSystem.step dispatch

The engine SHALL NOT remove or suppress the `PhysicsSystem.step` enter/exit dispatch when bodies use `moveAndCollide`. After a body advances via `moveAndCollide`, the next `PhysicsSystem.step` MUST observe the body's new position and dispatch enter/exit signals normally for any peer relationships that changed. This MUST allow Areas (triggers) to continue receiving `_on_*_entered` even when peers move via `moveAndCollide`.

#### Scenario: Area still receives _on_body_entered after CharacterBody2D moveAndCollide

- **GIVEN** an `Area2D` T at `(50f, 0f)` and a `CharacterBody2D` B at `(0f, 0f)`, both live
- **WHEN** B calls `moveAndCollide(Vec2(60f, 0f))` and the call returns `null` (no body in the path)
- **AND** the next `PhysicsSystem.step` runs
- **THEN** T receives `onBodyEntered(B)` exactly once
- **AND** T.bodyEntered emits B exactly once

