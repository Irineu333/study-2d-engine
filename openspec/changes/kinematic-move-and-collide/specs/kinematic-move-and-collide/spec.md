## ADDED Requirements

### Requirement: KinematicCollision2D represents a swept collision result

The engine SHALL provide a `KinematicCollision2D` data class in package `com.neoutils.engine.physics` with the following fields, all immutable: `point: Vec2` (world-space contact point), `normal: Vec2` (unit vector pointing from the moving body outward from the collider it hit), `collider: CollisionObject2D` (reference to the body that was hit), `remainder: Vec2` (the portion of the original motion that was not consumed because the body stopped at TOI).

#### Scenario: KinematicCollision2D is a data class with immutable fields

- **WHEN** code instantiates `KinematicCollision2D(point = Vec2(5f, 0f), normal = Vec2(-1f, 0f), collider = someBody, remainder = Vec2(2f, 0f))`
- **THEN** the resulting instance exposes `point`, `normal`, `collider`, `remainder` as `val` properties
- **AND** the class participates in structural equality via `data class` semantics

### Requirement: CharacterBody2D exposes moveAndCollide

The engine SHALL add a public method `fun moveAndCollide(motion: Vec2): KinematicCollision2D?` on `CharacterBody2D`. The method MUST perform a swept-shape test from the body's current world position along `motion` against every other live `PhysicsBody2D` (both `StaticBody2D` and `CharacterBody2D`) in the tree with `disabled == false`. `Area2D` instances MUST be ignored by `moveAndCollide` (areas are triggers, not blockers). If no collision is detected, the body's `transform.position` MUST be advanced by `motion` and the method MUST return `null`. If a collision is detected with smallest time-of-impact `toi` in `[0f, 1f]`, the body's `transform.position` MUST be advanced by `motion * toi` (the body stops at the contact) and the method MUST return a `KinematicCollision2D` whose `remainder` equals `motion * (1f - toi)`.

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

#### Scenario: moveAndCollide returns TOI=0 when bodies start overlapping

- **GIVEN** two `CharacterBody2D` A and B both at world position `(0f, 0f)` with overlapping `CircleShape2D` (radius=5f each)
- **WHEN** the script calls `A.moveAndCollide(Vec2(10f, 0f))`
- **THEN** the call returns a non-null `KinematicCollision2D` with `remainder` approximately equal to the input motion
- **AND** `A.transform.position` is unchanged (or advanced by the smallest non-negative amount)
- **AND** the script is given a normal it can use to push A out of B

#### Scenario: moveAndCollide returns null on rotated shapes (current limitation)

- **GIVEN** a `CharacterBody2D` A whose `world().rotation == π / 4`
- **AND** a `StaticBody2D` B in the path with `world().rotation == 0f`
- **WHEN** the script calls `A.moveAndCollide(Vec2(20f, 0f))`
- **THEN** the swept test falls through (no axis-aligned overlap detected) and the call returns `null`
- **AND** `A.transform.position` advances by the full motion
- **AND** this behavior is documented as a known limitation to be addressed by future `kinematic-rotated-sweep` work

### Requirement: Shape2D supports sweepOverlap for axis-aligned shape pairs

The engine SHALL provide a top-level function `fun sweepOverlap(a: Shape2D, aWorld: Transform, motion: Vec2, b: Shape2D, bWorld: Transform): SweepResult?` in `com.neoutils.engine.physics`. `SweepResult` MUST be a data class with `toi: Float` (in `[0f, 1f]`), `point: Vec2`, `normal: Vec2`. The function MUST return a non-null result with the smallest valid TOI when shape `a` swept by `motion` from `aWorld` would intersect shape `b` at `bWorld`, for the following axis-aligned pairs only: (a) `CircleShape2D` vs `CircleShape2D`; (b) `CircleShape2D` vs `RectangleShape2D`; (c) `RectangleShape2D` vs `RectangleShape2D`. For any pair where `aWorld.rotation != 0f` OR `bWorld.rotation != 0f`, the function MUST return `null` (rotated swept tests are out of scope of this change).

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

#### Scenario: Swept shape returns null on any rotated input

- **GIVEN** rect A `size=(4f, 4f)` at `(0f, 0f)` with `aWorld.rotation = π / 4`, motion `(20f, 0f)`, rect B `size=(4f, 4f)` at `(10f, 0f)` with `bWorld.rotation = 0f`
- **WHEN** code calls `sweepOverlap(...)`
- **THEN** the function returns `null`
- **AND** this is documented as the deferred path

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
