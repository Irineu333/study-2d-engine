# rigid-body-2d Specification

## Purpose

Define `RigidBody2D` (engine-integrated dynamic body) e o solver de impulso linear+angular+Coulomb que o `PhysicsSystem.step` executa em cada physics tick. Inclui:

- Massa, inércia, restituição, fricção, gravity scale e damping.
- API de forças/impulsos no script (`applyForce`, `applyImpulse`, `applyForceAt`, `applyImpulseAt`, `applyTorque`).
- Integrador (gravity + accumulated forces → velocity → position) + TOI loop com impulse solver bilateral.
- Auto-derive de inércia a partir das `CollisionShape2D` filhas.
- Diagnósticos didáticos: `SceneTree.totalLinearMomentum()`, `totalAngularMomentum()`, `totalKineticEnergy()` + overlay F3.
- Warning de teleporte mid-frame por body.

Corresponde à change `add-rigid-body-2d`; complementa as capabilities `engine-core` (taxonomia) e `kinematic-move-and-collide` (sweep e geometric contact point).

## Requirements

### Requirement: RigidBody2D is a PhysicsBody2D controlled by the engine

The engine SHALL provide a concrete class `RigidBody2D` in package `com.neoutils.engine.physics`, extending `PhysicsBody2D`, alongside `StaticBody2D` and `CharacterBody2D`. `RigidBody2D` represents a body whose `position`, `linearVelocity`, and `angularVelocity` are integrated by the `PhysicsSystem` each physics tick — not by user scripts. Scripts MUST influence motion through accumulators (`applyForce`, `applyImpulse`, `applyForceAt`, `applyImpulseAt`, `applyTorque`) and through direct read/write of `linearVelocity` and `angularVelocity`. `RigidBody2D` MUST be annotated `@Serializable` and registered in `NodeRegistry` under the canonical name `engine.RigidBody2D`.

#### Scenario: RigidBody2D is a third type of PhysicsBody2D

- **WHEN** the `:engine` source tree is inspected
- **THEN** `com.neoutils.engine.physics.RigidBody2D` exists as `open class RigidBody2D : PhysicsBody2D()`
- **AND** `RigidBody2D` carries the `@Serializable` annotation
- **AND** `NodeRegistry` resolves the string `"engine.RigidBody2D"` to construct a `RigidBody2D` instance

#### Scenario: CharacterBody2D and StaticBody2D remain untouched

- **WHEN** the `:engine` source tree is inspected after this change
- **THEN** `CharacterBody2D` continues to expose `velocity: Vec2` and `moveAndCollide(motion: Vec2): KinematicCollision2D?`
- **AND** `StaticBody2D` continues to exist with no velocity slot
- **AND** the existing kinematic test suite still passes

### Requirement: RigidBody2D exposes mass, inertia, restitution, friction, gravity scale, damping

`RigidBody2D` SHALL expose the following `@Inspect var` fields with the indicated defaults:

- `mass: Float = 1f`
- `inertia: Float = 0f` (sentinel — when `0f`, the system MUST derive moment of inertia from attached `CollisionShape2D` children)
- `restitution: Float = 0f` (inelastic by default — Godot/Unity convention)
- `friction: Float = 1f`
- `gravityScale: Float = 1f`
- `linearDamping: Float = 0f`
- `angularDamping: Float = 0f`

`RigidBody2D` SHALL expose the following `@Transient var` fields, all defaulting to zero:

- `linearVelocity: Vec2 = Vec2.ZERO`
- `angularVelocity: Float = 0f`

The system SHALL provide an internal accessor `effectiveInertia: Float` that returns `inertia` when `inertia != 0f`, otherwise derives the value from the body's active `CollisionShape2D` children:

- For `CircleShape2D` of radius `r`: contribution is `mass · r² / 2`.
- For `RectangleShape2D` of size `(w, h)`: contribution is `mass · (w² + h²) / 12`.
- An offset from the body center (the shape's `transform.position`) of magnitude `d` adds a parallel-axis term `mass · d²`.
- Multiple shape children sum.
- A body with no shape children and `inertia == 0f` MUST return `1f` (avoid division by zero in the solver).

`effectiveInertia` MUST be cached `@Transient` and invalidated when shape children are added, removed, or have their `shape` mutated.

#### Scenario: A RigidBody2D defaults to inelastic with friction

- **WHEN** code constructs `val b = RigidBody2D()` with no overrides
- **THEN** `b.mass == 1f`, `b.restitution == 0f`, `b.friction == 1f`, `b.gravityScale == 1f`
- **AND** `b.linearDamping == 0f`, `b.angularDamping == 0f`

#### Scenario: Auto-derived inertia for a circle shape

- **GIVEN** `val b = RigidBody2D().apply { mass = 2f }` with a single `CollisionShape2D` child carrying `CircleShape2D(radius = 5f)` at the body's center
- **WHEN** the system reads `b.effectiveInertia`
- **THEN** the result equals `2f * 25f / 2f` = `25f` (within float precision)

#### Scenario: Auto-derived inertia for a rectangle shape with offset

- **GIVEN** `val b = RigidBody2D().apply { mass = 1f }` with a single `CollisionShape2D` child carrying `RectangleShape2D(size = Vec2(6f, 4f))` at shape-local `transform.position = Vec2(3f, 0f)`
- **WHEN** the system reads `b.effectiveInertia`
- **THEN** the result equals `1f * (36f + 16f) / 12f + 1f * 9f` = `4.333... + 9f` = `13.333...` (within float precision)

#### Scenario: Explicit inertia overrides the auto value

- **GIVEN** `val b = RigidBody2D().apply { mass = 1f; inertia = 42f }` with any shape children
- **WHEN** the system reads `b.effectiveInertia`
- **THEN** the result equals `42f` verbatim

### Requirement: RigidBody2D exposes force and impulse application methods

`RigidBody2D` SHALL provide the following public methods, all of which mutate `@Transient` accumulator state on the body:

- `fun applyForce(force: Vec2)`: accumulates a continuous force (consumed across `dt`).
- `fun applyImpulse(impulse: Vec2)`: applies an instantaneous change to `linearVelocity` immediately (`linearVelocity += impulse / mass`).
- `fun applyForceAt(force: Vec2, worldPoint: Vec2)`: accumulates a force plus its torque about the body center.
- `fun applyImpulseAt(impulse: Vec2, worldPoint: Vec2)`: instantaneous linear impulse plus instantaneous angular impulse about the body center.
- `fun applyTorque(torque: Float)`: accumulates a continuous torque.

`applyForce` and `applyTorque` accumulate into `@Transient` fields `appliedForce: Vec2` and `appliedTorque: Float` that the system consumes during the integration step and clears each tick. `applyImpulse` and `applyImpulseAt` modify `linearVelocity` / `angularVelocity` immediately (no accumulator).

#### Scenario: applyImpulse mutates linearVelocity immediately

- **GIVEN** a `RigidBody2D` with `mass = 2f`, `linearVelocity = Vec2(0f, 0f)`
- **WHEN** the script calls `body.applyImpulse(Vec2(10f, 0f))`
- **THEN** `body.linearVelocity` equals `Vec2(5f, 0f)` after the call

#### Scenario: applyForce accumulates and is consumed in the next integration

- **GIVEN** a `RigidBody2D` with `mass = 2f`, `linearVelocity = Vec2.ZERO`, no gravity
- **WHEN** the script calls `body.applyForce(Vec2(10f, 0f))` and the physics system integrates with `dt = 0.5f`
- **THEN** `body.linearVelocity` equals `Vec2(2.5f, 0f)` (= `Vec2(10/2 * 0.5, 0)`)
- **AND** `body.appliedForce` equals `Vec2.ZERO` after integration (accumulator cleared)

#### Scenario: applyImpulseAt couples linear and angular

- **GIVEN** a `RigidBody2D` with `mass = 1f`, `inertia = 1f`, position `(0f, 0f)`, both velocities zero
- **WHEN** the script calls `body.applyImpulseAt(Vec2(0f, 10f), worldPoint = Vec2(5f, 0f))`
- **THEN** `body.linearVelocity` equals `Vec2(0f, 10f)`
- **AND** `body.angularVelocity` equals `5f * 10f / 1f` = `50f` (the cross product of lever arm × impulse divided by inertia)

### Requirement: PhysicsSystem.step integrates and resolves RigidBody2D motion within a physics tick

The `PhysicsSystem.step(tree: SceneTree, dt: Float)` method (new signature including `dt`) MUST execute the following stages in order, after `SceneTree` has already dispatched `onPhysicsProcess(dt)` to all nodes for this tick:

1. **Integrate forces**: For every live, non-disabled `RigidBody2D` `r` in the tree (pre-order traversal from the root):
   - `r.linearVelocity += (gravity * r.gravityScale + r.appliedForce / r.mass) * dt`
   - `r.angularVelocity += r.appliedTorque / r.effectiveInertia * dt`
   - `r.linearVelocity *= max(0f, 1f - r.linearDamping * dt)`
   - `r.angularVelocity *= max(0f, 1f - r.angularDamping * dt)`
   - Clear `r.appliedForce` and `r.appliedTorque`.
2. **Sweep and resolve contacts (TOI loop)**: For every live `RigidBody2D` `r` in the same pre-order, iterate up to `R = 4` times:
   - Compute `motion = r.linearVelocity * dt_remaining` (initial `dt_remaining = dt`).
   - Sweep `r`'s active shapes against every other live non-disabled `PhysicsBody2D` whose parent matches `r.parent`, using the same `sweepOverlap` machinery as `moveAndCollide`.
   - If contact found at `toi in [0, 1]`:
     - Advance `r.position += motion * toi + bestHit.depenetration`.
     - Apply the impulse equation (see "RigidBody2D contact resolution" requirement) to `r` and to `other`; both bodies' velocities update.
     - Decrement `dt_remaining *= (1f - toi)`.
     - If `dt_remaining * |r.linearVelocity|` is below an epsilon, break.
   - Else advance `r.position += motion` and break.
3. **Integrate angular**: For every live `RigidBody2D` `r`: `r.transform.rotation += r.angularVelocity * dt`.
4. **Dispatch enter/exit**: Run the existing `computeOverlapping` and dispatch loop unchanged.

`PhysicsSystem` SHALL expose a public mutable property `gravity: Vec2 = Vec2.ZERO` consumed in stage 1.

#### Scenario: A free-falling RigidBody2D integrates gravity

- **GIVEN** `PhysicsSystem.gravity = Vec2(0f, 100f)`, a single `RigidBody2D` with `gravityScale = 1f`, `linearDamping = 0f`, position `(0f, 0f)`, `linearVelocity = Vec2.ZERO`, no other bodies in the tree
- **WHEN** one physics tick of `dt = 0.5f` runs
- **THEN** `body.linearVelocity` equals `Vec2(0f, 50f)`
- **AND** `body.position` equals `Vec2(0f, 25f)` (= initial 0 + velocity 50 * dt 0.5)

#### Scenario: gravityScale = 0 means body ignores gravity

- **GIVEN** the same setup but `gravityScale = 0f`
- **WHEN** one physics tick of `dt = 1f` runs
- **THEN** `body.linearVelocity` remains `Vec2.ZERO`
- **AND** `body.position` remains `Vec2.ZERO`

#### Scenario: A RigidBody2D with velocity and no obstacle advances by velocity * dt

- **GIVEN** a `RigidBody2D` with `linearVelocity = Vec2(100f, 0f)`, `gravityScale = 0f`, no other bodies
- **WHEN** one physics tick of `dt = 0.1f` runs
- **THEN** `body.position.x` advanced by approximately `10f`

### Requirement: RigidBody2D contact resolution uses an impulse equation that updates linear and angular velocity

When `PhysicsSystem.step` detects a contact between a `RigidBody2D` `r` and another `PhysicsBody2D` `other` at world point `P` with outward unit normal `n` (pointing from `other` toward `r`), the system MUST apply the following impulse:

```
rA  = P - centroA (with centroA = r.position in r.parent frame)
rB  = P - centroB
vAP = r.linearVelocity + cross(r.angularVelocity, rA)
vBP = other-velocity-at-P
v_rel = vAP - vBP
vRelN = v_rel · n
if vRelN >= 0: skip (already separating)

invMA = 1f / r.mass
invIA = 1f / r.effectiveInertia
invMB = if other is RigidBody2D: 1f / other.mass else 0f
invIB = if other is RigidBody2D: 1f / other.effectiveInertia else 0f

e = max(r.restitution, other-restitution-or-0)
denom_N = invMA + invMB + (cross2D(rA, n)² * invIA) + (cross2D(rB, n)² * invIB)
jn = -(1 + e) * vRelN / denom_N

r.linearVelocity += jn * n * invMA
r.angularVelocity += cross2D(rA, n) * jn * invIA
if other is RigidBody2D:
  other.linearVelocity -= jn * n * invMB
  other.angularVelocity -= cross2D(rB, n) * jn * invIB
```

Where `cross2D(a, b) = a.x * b.y - a.y * b.x` and `cross(scalar ω, vec r) = Vec2(-ω * r.y, ω * r.x)`.

For `StaticBody2D` and `CharacterBody2D` as the `other`: the system MUST treat their inverse mass and inverse inertia as `0f` (and their velocity at P as `Vec2.ZERO` for Static; for Character use its `velocity` projected at P but DO NOT mutate the Character — Static and Character are treated as infinite-mass kinematic obstacles from the Rigid's perspective in this change).

The combined restitution rule is `e = max(e_A, e_B)`. The combined friction (used in the tangential impulse below) is `μ = sqrt(μ_A · μ_B)`.

After the normal impulse, the system MUST apply a Coulomb friction impulse along the tangential direction `t` (the unit vector of the tangential component of `v_rel`):

```
v_tang = v_rel - vRelN * n
|v_tang| < FRICTION_EPS: skip tangential
t = v_tang / |v_tang|
denom_T = invMA + invMB + (cross2D(rA, t)² * invIA) + (cross2D(rB, t)² * invIB)
jt_brake = |v_tang| / denom_T
jt = min(jt_brake, μ * |jn|)
apply -jt * t with the same per-body distribution as jn * n
```

The pair MUST be resolved exactly once per contact (the `PhysicsSystem.step` driver iterates rigid bodies and applies the bilateral impulse in the same pass; the other body does not re-resolve the same pair from its own iteration).

#### Scenario: Equal-mass elastic head-on impact swaps velocities

- **GIVEN** two `RigidBody2D` bodies A and B with `mass = 1f`, `restitution = 1f`, `friction = 0f`, `gravityScale = 0f`, `inertia = 1f`, `angularVelocity = 0f`, positions and shapes set so they collide head-on along the x-axis with `A.linearVelocity = Vec2(100f, 0f)` and `B.linearVelocity = Vec2(0f, 0f)`
- **WHEN** the physics tick processes the contact
- **THEN** after the resolution, `A.linearVelocity.x` is approximately `0f` and `B.linearVelocity.x` is approximately `100f` (within float precision; angular velocities remain zero for axis-aligned head-on hit)

#### Scenario: Inelastic head-on impact equalizes velocity components on the normal

- **GIVEN** the same A and B as above, but `restitution = 0f` on both
- **WHEN** the physics tick processes the contact
- **THEN** after the resolution, `A.linearVelocity.x` is approximately `B.linearVelocity.x` (both bodies move together along the normal axis — relative normal velocity ≈ 0)

#### Scenario: Mass-heavy body barely loses speed against light body

- **GIVEN** A with `mass = 10f`, `linearVelocity = (100f, 0f)`, restitution = 1f; B with `mass = 1f`, `linearVelocity = Vec2.ZERO`, restitution = 1f, head-on
- **WHEN** the contact is resolved
- **THEN** `A.linearVelocity.x` ≈ `100f - 2 * (10 * 100) / (10 + 1) / 10` ≈ `81.81f`
- **AND** `B.linearVelocity.x` ≈ `2 * (10 * 100) / (10 + 1)` ≈ `181.81f`

#### Scenario: Static body is treated as infinite mass

- **GIVEN** RigidBody2D A with `mass = 1f`, `restitution = 1f`, `linearVelocity = Vec2(100f, 0f)` hitting a StaticBody2D wall with normal `(-1f, 0f)`
- **WHEN** the contact is resolved
- **THEN** `A.linearVelocity.x` is approximately `-100f` (perfect bounce; the wall does not move)

#### Scenario: Pair already separating is not impulsed

- **GIVEN** two `RigidBody2D` whose shapes are in contact (TOI = 0) but whose relative velocity along the contact normal is already positive (`v_rel · n >= 0`)
- **WHEN** the system would apply an impulse
- **THEN** no impulse is applied (`linearVelocity` and `angularVelocity` of both bodies are unchanged)

#### Scenario: Off-center hit produces angular velocity (lever arm)

- **GIVEN** RigidBody2D A with `mass = 1f`, `inertia = 1f`, `linearVelocity = (100f, 0f)`, `angularVelocity = 0f`, hitting a static wall at world contact point offset `(0f, 5f)` from A's center, normal `(-1f, 0f)`
- **WHEN** the contact is resolved with `restitution = 1f`
- **THEN** `A.angularVelocity` after the resolution differs from `0f` (non-zero spin from the lever arm `r × n`)

### Requirement: SweepResult.point is the geometric contact point

The `Shape2D.sweepOverlap` function (defined in the `kinematic-move-and-collide` capability) MUST populate `SweepResult.point` with the **geometric contact point** in the same frame the sweep was performed, per shape pair:

- **Circle vs Circle**: `point = centroA_at_contact + n * radiusA` (the contact lies on the surface of A at the moment of contact).
- **Circle vs Rectangle (any rotation)**: `point` is the closest point on the rectangle's surface to the circle center at the moment of contact (computed by clamping the circle's local coordinates inside the rect's local frame and rotating back).
- **Rectangle vs Rectangle (any rotation)**: `point` is the vertex of the sweeping rect that is most penetrated along `-n` (the "leading corner"); ties within an epsilon collapse to the midpoint of the tied vertices (face-vs-face contacts produce the face midpoint, yielding `r × n = 0` and zero induced spin).

This is a refinement of the existing contract — the field's name and type do not change, only its semantics. The `KinematicCollision2D.point` returned to scripts inherits the same value.

#### Scenario: Circle-vs-circle contact point lies on circle A's surface

- **GIVEN** circle A radius `5f` at `(0f, 0f)`, motion `(20f, 0f)`, circle B radius `5f` at `(12f, 0f)`
- **WHEN** code calls `sweepOverlap(...)` and receives a non-null `SweepResult`
- **THEN** `result.point` is approximately `(5f, 0f)` (A's center after advancing by `2f` plus radius along normal `(1f, 0f)` reaches `(2f + 5f * 1f, 0f) = (7f, 0f)` — exact value depends on the sweep formulation, but it lies on A's contact-time surface, not at A's center)

#### Scenario: Rect-vs-rect leading corner is selected for vertex hit

- **GIVEN** rect A `size = (4f, 4f)` at `(0f, 0f)` with `rotation = π / 6` swept against a static rect B aligned with the x-axis
- **WHEN** the swept contact resolves
- **THEN** `result.point` is the world-space position of the vertex of A that has the smallest projection onto `n` (i.e., the corner that "leads" into B), not A's center

#### Scenario: Rect-vs-rect face-vs-face collapses to face midpoint

- **GIVEN** two axis-aligned rects A and B with the same rotation in head-on contact
- **WHEN** the swept contact resolves
- **THEN** `result.point` lies on the midpoint of the contacting face (the two leading vertices of A tie within epsilon and average together)

### Requirement: SceneTree exposes momentum and kinetic energy diagnostics

The engine SHALL provide three top-level extension methods on `SceneTree` (or methods on `SceneTree` itself), all of which iterate every live, non-disabled `RigidBody2D` in the tree:

- `fun SceneTree.totalLinearMomentum(): Vec2` — sum of `r.mass * r.linearVelocity` over all `RigidBody2D`.
- `fun SceneTree.totalAngularMomentum(): Float` — sum of `r.effectiveInertia * r.angularVelocity + r.mass * (r.position.x * r.linearVelocity.y - r.position.y * r.linearVelocity.x)` over all `RigidBody2D`.
- `fun SceneTree.totalKineticEnergy(): Float` — sum of `0.5f * r.mass * r.linearVelocity.lengthSquared() + 0.5f * r.effectiveInertia * r.angularVelocity * r.angularVelocity`.

These methods MUST return `Vec2.ZERO`, `0f`, `0f` respectively for trees containing no `RigidBody2D`.

#### Scenario: Single body's total linear momentum equals its m*v

- **GIVEN** a tree with one `RigidBody2D` at `mass = 3f`, `linearVelocity = Vec2(4f, -2f)`
- **WHEN** code calls `tree.totalLinearMomentum()`
- **THEN** the result equals `Vec2(12f, -6f)` (within float precision)

#### Scenario: Tree with no RigidBody2D returns zero diagnostics

- **GIVEN** a tree populated only with `CharacterBody2D`, `Area2D`, and `Node2D`
- **WHEN** code calls `tree.totalLinearMomentum()`, `tree.totalAngularMomentum()`, `tree.totalKineticEnergy()`
- **THEN** the results are `Vec2.ZERO`, `0f`, `0f` respectively

#### Scenario: Elastic collision conserves total kinetic energy

- **GIVEN** two `RigidBody2D` with `restitution = 1f`, `friction = 0f`, `angularDamping = 0f`, `linearDamping = 0f`, set up to collide head-on
- **WHEN** N physics ticks run, with the collision happening on some tick `k`
- **THEN** for every tick `i`, `|tree.totalKineticEnergy() - initial_KE|` < `epsilon` (e.g. `0.5%` of `initial_KE`)

#### Scenario: Inelastic collision dissipates kinetic energy but conserves linear momentum

- **GIVEN** two `RigidBody2D` with `restitution = 0f`, `friction = 0f`, set up to collide head-on
- **WHEN** the collision tick processes the contact
- **THEN** `tree.totalKineticEnergy()` after the contact is strictly less than before
- **AND** `tree.totalLinearMomentum()` after the contact equals the value before (within float precision)

### Requirement: Mid-frame position teleport on RigidBody2D logs a single warning per body

When script code (Kotlin or Python) writes directly to `RigidBody2D.transform` (e.g. `body.transform = Transform(...)` or `body.position = Vec2(...)`) while the body is live in a tree, the engine SHALL:

- Allow the mutation (the existing `Node2D.transform` setter applies, invalidates world-transform cache, and propagates).
- Log a single warning per body via `Log.w` on the first such mutation per instance, with message including the body's `name` and a hint that teleporting bypasses physics resolution.
- Suppress further warnings for that body instance via a `@Transient warnedAboutTeleport: Boolean` flag set on first occurrence.

The warning MUST NOT throw and MUST NOT block subsequent physics resolution.

#### Scenario: First teleport logs warning, subsequent teleports are silent

- **GIVEN** a live `RigidBody2D` `b` named `"Player"`
- **WHEN** the script calls `b.position = Vec2(100f, 0f)` twice in different frames
- **THEN** `Log.w` records exactly one warning whose message contains `"Player"`
- **AND** the second mutation completes without further warning entries

#### Scenario: Constructing and teleporting before attaching does not warn

- **GIVEN** a `RigidBody2D` constructed in memory but never added to a live tree
- **WHEN** code writes to `transform` repeatedly
- **THEN** no warning is logged (teleport detection requires `isLive == true`)

### Requirement: GameConfig exposes toggleMomentumOverlayKey

The engine's `GameConfig` (or equivalent host configuration) SHALL expose a `toggleMomentumOverlayKey: Key` field with default `Key.F3`. The `GameHost` (Skiko backend, primary) SHALL render an opt-in debug overlay when this key is pressed, displaying:

- Current total linear momentum (`Σp` as `(x, y)`).
- Current total angular momentum (`ΣL`).
- Current total kinetic energy (`ΣKE`).
- Sparkline of the last `N = 60` samples for each (one sample per physics tick).

The overlay MUST be toggleable and default to **off** so games that don't care pay no cost. The overlay MUST live in screen-space (UI surface), unaffected by `Camera2D` view transform.

#### Scenario: GameConfig has toggleMomentumOverlayKey default F3

- **WHEN** the `GameConfig` API is inspected
- **THEN** it exposes `var toggleMomentumOverlayKey: Key = Key.F3`

#### Scenario: F3 toggles the momentum overlay in Skiko host

- **GIVEN** a `:games:demos` instance running in Skiko with the overlay initially off
- **WHEN** the F3 key is pressed
- **THEN** the next frame renders the overlay block in the bottom-left corner with `Σp`, `ΣL`, `ΣKE` labels and live values
- **AND** pressing F3 again hides the overlay
