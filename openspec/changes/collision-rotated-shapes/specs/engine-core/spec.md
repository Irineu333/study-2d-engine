## ADDED Requirements

### Requirement: Rectangle-rectangle overlap is exact under rotation

The pure function `overlap(a: Shape2D, aWorld: Transform, b: Shape2D, bWorld: Transform): Boolean` defined in `com.neoutils.engine.physics` MUST return `true` if and only if the two oriented rectangles described by `(a, aWorld)` and `(b, bWorld)` geometrically intersect in world space, when both `a` and `b` are `RectangleShape2D` and at least one of `aWorld.rotation` or `bWorld.rotation` is non-zero.

When **both** rotations are exactly `0f`, the implementation MAY take a faster axis-aligned path (intersecting `bounds()` AABBs) — that path is equivalent to the rotated test for axis-aligned inputs.

The exact test MUST be implemented via the Separating Axis Theorem on the four candidate axes (two per OBB, perpendicular to their sides). `RectangleShape2D.bounds(world, localOffset)` continues to return the axis-aligned envelope of the rotated corners — this requirement does NOT change the `bounds()` contract; it only changes the `overlap()` semantics for the rect-rect rotated case.

`PhysicsSystem` MAY continue to use `bounds()` AABB intersection as a cheap broad-phase rejection step before calling `overlap()`.

#### Scenario: Two rotated rectangles whose AABB envelopes overlap but whose OBBs do not are reported as not overlapping

- **GIVEN** `RectangleShape2D` A with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(0f, 0f), rotation = π/4)`
- **AND** `RectangleShape2D` B with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(22f, 0f), rotation = π/4)`
- **AND** the AABB envelopes (~28.28 × 28.28 each, centered on each position) overlap
- **AND** the actual rotated rectangles are tangent or separated
- **WHEN** `overlap(A, aWorld, B, bWorld)` is computed
- **THEN** the result is `false`

#### Scenario: Two rotated rectangles whose OBBs touch are reported as overlapping

- **GIVEN** `RectangleShape2D` A with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(0f, 0f), rotation = π/4)`
- **AND** `RectangleShape2D` B with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(10f, 10f), rotation = π/4)`
- **AND** the rotated rectangles geometrically overlap
- **WHEN** `overlap(A, aWorld, B, bWorld)` is computed
- **THEN** the result is `true`

#### Scenario: Two axis-aligned rectangles preserve the existing AABB behavior

- **GIVEN** `RectangleShape2D` A with `size = Vec2(10f, 10f)` at `world = Transform(position = Vec2(0f, 0f), rotation = 0f)`
- **AND** `RectangleShape2D` B with `size = Vec2(10f, 10f)` at `world = Transform(position = Vec2(5f, 5f), rotation = 0f)`
- **WHEN** `overlap(A, aWorld, B, bWorld)` is computed
- **THEN** the result is `true`
- **AND** when B is moved to `Vec2(100f, 100f)` the result is `false`

#### Scenario: One rectangle rotated, the other axis-aligned uses the OBB path

- **GIVEN** `RectangleShape2D` A with `size = Vec2(20f, 20f)` at `world = Transform(rotation = π/4)`
- **AND** `RectangleShape2D` B with `size = Vec2(20f, 20f)` at `world = Transform(rotation = 0f)`
- **WHEN** their positions place the OBBs apart but their AABB envelopes overlap
- **THEN** `overlap(A, aWorld, B, bWorld)` returns `false`
