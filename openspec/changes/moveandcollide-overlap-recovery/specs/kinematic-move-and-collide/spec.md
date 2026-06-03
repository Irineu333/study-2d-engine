## MODIFIED Requirements

### Requirement: CharacterBody2D exposes moveAndCollide

The engine SHALL add a public method `fun moveAndCollide(motion: Vec2): KinematicCollision2D?` on `CharacterBody2D`. The method MUST perform a swept-shape test from the body's current world position along `motion` against every other live `PhysicsBody2D` (both `StaticBody2D` and `CharacterBody2D`) in the tree with `disabled == false` whose parent matches `this.parent`. `Area2D` instances MUST be ignored by `moveAndCollide` (areas are triggers, not blockers). If no collision is detected, the body's `transform.position` MUST be advanced by `motion` and the method MUST return `null`.

If a collision is detected with smallest time-of-impact `toi` in `(0f, 1f]` (a clean contact ahead), the body's `transform.position` MUST be advanced by `motion * toi + sweepResult.depenetration` (the body stops at the contact) and the method MUST return a `KinematicCollision2D` whose `remainder` equals `motion * (1f - toi)`.

If the smallest time-of-impact is `0f` (the body starts overlapping a collider), the engine MUST apply the `depenetration` vector AND then **continue spending the still-unspent motion via a bounded recovery**: it re-sweeps the remaining motion from the depenetrated position, repeating up to a fixed iteration cap. As a consequence, motion pointing **out of** the collider MUST be applied (the body leaves an overlap it is trying to leave, even while another body keeps re-pressing a marginal overlap), while motion pointing **into** the collider MUST make no forward progress (the body rests against the surface, never tunnels). The returned `KinematicCollision2D` MUST report the **first** contact's `point`, `normal`, and `collider` (so the caller can still react to the contact), with `remainder` equal to the portion of `motion` left unspent after the recovery. A call with `motion == Vec2.ZERO` on a starting overlap MUST still apply only the depenetration and return `remainder == Vec2.ZERO`.

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

#### Scenario: moveAndCollide with zero motion on a starting overlap only depenetrates

- **GIVEN** two `CharacterBody2D` A and B both with overlapping `CircleShape2D` (radius=5f each), with B center 4f away from A center
- **WHEN** the script calls `A.moveAndCollide(Vec2.ZERO)`
- **THEN** the call returns a non-null `KinematicCollision2D` with `toi == 0f` and `remainder == Vec2.ZERO`
- **AND** `A.transform.position` is advanced by the depenetration vector (separating A from B by the penetration depth along the contact normal)

#### Scenario: Outward motion on a starting overlap is applied (body escapes)

- **GIVEN** two `CharacterBody2D` A and B with overlapping `CircleShape2D` (radius=5f each), B center 4f to the right (`+x`) of A center, so the contact normal on A points `-x`
- **WHEN** the script calls `A.moveAndCollide(Vec2(-20f, 0f))` (motion pointing OUT of B, along the contact normal)
- **THEN** the call returns a non-null `KinematicCollision2D` whose `collider` is B and whose `normal` is approximately `Vec2(-1f, 0f)` (the first contact is reported)
- **AND** `A.transform.position` has moved clearly away from B (well beyond the bare depenetration — the outward motion was spent, not discarded)
- **AND** A and B no longer overlap after the call

#### Scenario: Inward motion on a starting overlap makes no forward progress

- **GIVEN** two `CharacterBody2D` A and B with overlapping `CircleShape2D` (radius=5f each), B center 4f to the right (`+x`) of A center
- **WHEN** the script calls `A.moveAndCollide(Vec2(20f, 0f))` (motion pointing INTO B)
- **THEN** the body does not tunnel into or past B (its center stays on its side of the contact, separated by the depenetration)
- **AND** the returned `remainder` reflects the inward motion left unspent

#### Scenario: A body re-pressed into another still escapes within a few frames

- **GIVEN** two `CharacterBody2D` A and B that begin a frame in a marginal overlap, where B is moved back into A by a fixed amount at the start of every frame (a sustained re-press) and A is driven each frame with a velocity pointing away from B
- **WHEN** A advances with `moveAndCollide(velocity * dt)` every frame for a bounded number of frames
- **THEN** A separates from B and keeps moving away (it does not freeze oscillating in place)

#### Scenario: moveAndCollide on a rotated body bounces against a rotated wall

- **GIVEN** a `CharacterBody2D` A with `aWorld.rotation = π / 6` and `RectangleShape2D` size `(10f, 10f)`
- **AND** a `StaticBody2D` B in the path with `bWorld.rotation = π / 6` and `RectangleShape2D` size `(10f, 60f)`
- **WHEN** the script calls `A.moveAndCollide(motion)` with motion approaching B frontally in the shared rotated frame
- **THEN** the call returns a non-null `KinematicCollision2D` with a `toi` in `(0f, 1f)`
- **AND** the returned `normal` is the contact normal in the parent frame
- **AND** A's position advances by `motion * toi` (stops at contact, no tunneling)
