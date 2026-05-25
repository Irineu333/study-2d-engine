## MODIFIED Requirements

### Requirement: Physics primitives are Godot-style nodes

The engine SHALL provide collision support via a `CollisionObject2D` hierarchy rather than a single `Collider` class. The hierarchy MUST be:

```
CollisionObject2D (abstract, : Node2D)
├── Area2D                                    (trigger; does not block)
└── PhysicsBody2D (abstract)
    ├── StaticBody2D                          (solid, position moved by script)
    └── CharacterBody2D                       (solid, exposes velocity slot)
```

Every concrete subclass (`Area2D`, `StaticBody2D`, `CharacterBody2D`) MUST be `@Serializable` and instantiable with a public no-args constructor. `CollisionObject2D` MUST expose `@Inspect var disabled: Boolean = false`. `CharacterBody2D` MUST expose `@Inspect var velocity: Vec2 = Vec2.ZERO`. The engine MUST NOT integrate `CharacterBody2D.velocity` automatically — integration is the script's responsibility (Godot-style).

#### Scenario: Each collision class is instantiable with no args

- **WHEN** code evaluates `Area2D()`, `StaticBody2D()`, `CharacterBody2D()`
- **THEN** each call returns a valid instance assignable to `CollisionObject2D`

#### Scenario: CharacterBody2D velocity slot exists and is mutable

- **WHEN** code creates `CharacterBody2D()` and sets `body.velocity = Vec2(100f, 0f)`
- **THEN** reading `body.velocity` returns `Vec2(100f, 0f)`
- **AND** the engine does NOT automatically integrate `transform.position` from `velocity` between ticks

#### Scenario: PhysicsBody2D is not directly instantiable

- **WHEN** code attempts to call `PhysicsBody2D()`
- **THEN** the compiler rejects the call (`abstract`)

### Requirement: CollisionShape2D holds a Shape2D resource

The engine SHALL provide a `CollisionShape2D : Node2D` class with `@Inspect var shape: Shape2D? = null` and `@Inspect var disabled: Boolean = false`. `CollisionShape2D` MUST be `@Serializable` and instantiable with no args. The `Shape2D` type MUST be a `@Serializable sealed class` with at least two concrete subtypes: `RectangleShape2D(@Inspect var size: Vec2 = Vec2(10f, 10f))` and `CircleShape2D(@Inspect var radius: Float = 5f)`. `Shape2D` MUST expose a method `bounds(world: Transform, localOffset: Vec2): Rect` returning the axis-aligned bounding box in world space.

`CollisionShape2D` is meaningful only as a direct child of a `CollisionObject2D`; placing one elsewhere SHALL NOT crash but SHALL be ignored by `PhysicsSystem`.

#### Scenario: CollisionShape2D defaults

- **WHEN** code evaluates `CollisionShape2D()`
- **THEN** `shape` is `null`, `disabled` is `false`

#### Scenario: RectangleShape2D bounds reflect transform scale

- **GIVEN** a `RectangleShape2D` with `size = Vec2(10f, 20f)`
- **WHEN** `bounds(Transform(position = Vec2(50f, 50f), scale = Vec2(2f, 2f), rotation = 0f), Vec2.ZERO)` is computed
- **THEN** the resulting `Rect` has `origin = Vec2(50f, 50f)` and `size = Vec2(20f, 40f)`

#### Scenario: CircleShape2D bounds are square

- **GIVEN** a `CircleShape2D` with `radius = 10f`
- **WHEN** `bounds(Transform(position = Vec2(0f, 0f), scale = Vec2(1f, 1f), rotation = 0f), Vec2.ZERO)` is computed
- **THEN** the resulting `Rect` has `size.x == 20f` and `size.y == 20f`

#### Scenario: Shape2D supports polymorphic serialization

- **WHEN** code round-trips a `RectangleShape2D(size = Vec2(8f, 4f))` through `kotlinx.serialization` JSON
- **THEN** the JSON contains a polymorphic discriminator identifying the subtype
- **AND** deserialization produces an instance whose `size` equals `Vec2(8f, 4f)`

### Requirement: Collision lifecycle hooks emit enter and exit events

The engine SHALL provide four open hooks on `CollisionObject2D`, all defaulting to no-op:

```kotlin
open fun onAreaEntered(area: Area2D)
open fun onAreaExited(area: Area2D)
open fun onBodyEntered(body: PhysicsBody2D)
open fun onBodyExited(body: PhysicsBody2D)
```

The engine SHALL also expose four built-in signals on every `CollisionObject2D`, all `@Transient`:

```kotlin
val areaEntered: Signal<Area2D>
val areaExited:  Signal<Area2D>
val bodyEntered: Signal<PhysicsBody2D>
val bodyExited:  Signal<PhysicsBody2D>
```

When two objects begin overlapping, the engine MUST dispatch — exactly once per pair, per begin-of-overlap event — both the corresponding hook AND emit on the corresponding signal, on **both** objects of the pair. Symmetric dispatch on exit. The previous `Collider.onCollide(other)` hook and the previous `Collider`/`BoxCollider` classes MUST be removed entirely.

#### Scenario: Body-vs-Body emits bodyEntered on both

- **GIVEN** two `StaticBody2D` instances A and B in a live scene, not overlapping
- **WHEN** A's `transform.position` changes such that their shapes overlap, and a physics step runs
- **THEN** `A.onBodyEntered(B)` is invoked exactly once
- **AND** `B.onBodyEntered(A)` is invoked exactly once
- **AND** `A.bodyEntered.emit(B)` and `B.bodyEntered.emit(A)` each fire exactly once

#### Scenario: Area-vs-Body dispatches across both APIs

- **GIVEN** an `Area2D` A and a `CharacterBody2D` B starting non-overlapping
- **WHEN** their shapes start overlapping in a physics step
- **THEN** `A.onBodyEntered(B)` is invoked (Area sees Body)
- **AND** `B.onAreaEntered(A)` is invoked (Body sees Area)

#### Scenario: Exit fires when overlap ends

- **GIVEN** two `CollisionObject2D` previously overlapping
- **WHEN** their shapes separate in a subsequent physics step
- **THEN** the corresponding `*Exited` hook is invoked on both
- **AND** the corresponding `*Exited` signal emits on both

#### Scenario: Sustained overlap does not re-fire enter

- **GIVEN** two `CollisionObject2D` whose shapes overlap during a physics step
- **WHEN** they remain overlapping in the next physics step
- **THEN** no `*Entered` hook or signal fires again

#### Scenario: Old onCollide hook is removed

- **WHEN** the `:engine` source tree is inspected
- **THEN** no `Collider` class exists under `com.neoutils.engine.physics`
- **AND** no `BoxCollider` class exists
- **AND** no `onCollide(other)` method exists on any `Node` subclass in `:engine`

### Requirement: PhysicsSystem detects overlaps between CollisionObjects

The engine SHALL provide a `PhysicsSystem` whose `step(scene: Scene)` operation:

1. Enumerates every `CollisionObject2D` with `disabled == false` in the live scene tree.
2. Collects each object's active `CollisionShape2D` children (those whose `shape != null` and `disabled == false`).
3. For every unordered pair `(A, B)` of objects (A ≠ B), tests whether **any** pair `(shapeA, shapeB)` overlaps. Overlap MUST be exact for axis-aligned cases (rect-rect AABB, circle-circle distance, rect-circle closest-point); rotated shapes MAY be approximated by their AABB.
4. Maintains an internal `Set<UnorderedPair<CollisionObject2D>>` of currently overlapping pairs. Pairs new this step → dispatch enter. Pairs gone this step → dispatch exit.
5. Filters out pairs whose endpoints are no longer in the live scene before dispatching (cleanup of detached nodes).

Order: enter dispatches MUST run after exit dispatches within the same step. Both MUST run after the per-pair overlap test (no interleaving). The system MUST NOT crash if a hook removes a node from the scene mid-dispatch — mutation deferral applies.

#### Scenario: Detached nodes are removed from pair set

- **GIVEN** two `CollisionObject2D` A and B that were overlapping last step
- **WHEN** A is detached from the scene (via `parent.removeChild(A)`) before the next step
- **THEN** the next `step(scene)` does NOT invoke `B.onBodyExited(A)` for A
- **AND** the pair (A, B) is no longer tracked

#### Scenario: Multiple shapes per object — overlap is union

- **GIVEN** a `CollisionObject2D` with two `CollisionShape2D` children at different positions
- **AND** another `CollisionObject2D` whose single shape overlaps only the second of the first object's shapes
- **WHEN** a physics step runs
- **THEN** the pair is treated as overlapping
- **AND** exactly one `*Entered` event is dispatched per side (not one per shape pair)

#### Scenario: Step runs after physicsProcess each fixed step

- **WHEN** `GameLoop.tick` runs one physics step (configured in `godot-style-foundation`)
- **THEN** `scene.physicsProcess(dt)` runs before `physics.step(scene)` in that step
- **AND** the engine drains pending mutations before each phase

## REMOVED Requirements

### Requirement: Collider node abstraction and BoxCollider

**Reason:** Replaced by the Godot-style `CollisionObject2D` hierarchy with separate `Area2D` (trigger) and `PhysicsBody2D` (`StaticBody2D` + `CharacterBody2D`) subtypes, plus `CollisionShape2D` holding a polymorphic `Shape2D` resource. The single `BoxCollider` collapsed shape, body, and behavior into one class, preventing multiple shapes per body, trigger vs. solid intent, and decoupling shape from object lifetime.

**Migration:**

- Every `BoxCollider` becomes either `Area2D + CollisionShape2D(RectangleShape2D)` (for triggers) or `StaticBody2D + CollisionShape2D(RectangleShape2D)` (for solid stationary obstacles) or `CharacterBody2D + CollisionShape2D(RectangleShape2D)` (for solid moving entities with velocity).
- Every `onCollide(other)` override or `_on_collide` script hook becomes one of `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited` depending on the type of `other`.
- Discriminating by `other.name == "leftGoal"` is replaced by either type check (`isinstance(other, Area2D)`) or group membership (`other.is_in_group("walls")`).
