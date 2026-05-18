## ADDED Requirements

### Requirement: Transform composition by ancestry

The engine SHALL compose `Transform`s along the chain of `Node2D` ancestors so that `position`, `scale` and `rotation` of a parent affect the world-space transform of its descendants. `Node2D` MUST expose `worldTransform(): Transform` that returns the composed transform from the topmost `Node2D` ancestor down to `this`, applying `scale` and `rotation` of each ancestor to the local frame of the next descendant. `Node2D.worldPosition()` SHALL be equivalent to `worldTransform().position`. `Transform` MUST expose a pure `compose(child: Transform): Transform` (or equivalent operator) that, given a parent transform, returns the world transform for a child described in the parent's local frame.

#### Scenario: Translation only composes additively

- **WHEN** a parent `Node2D` has `transform.position = (10, 20)`, no rotation and unit scale, and a child `Node2D` has `transform.position = (3, 4)` and unit transform otherwise
- **THEN** `child.worldTransform().position` equals `(13, 24)`
- **AND** `child.worldTransform().scale` equals `(1, 1)`
- **AND** `child.worldTransform().rotation` equals `0`

#### Scenario: Parent scale scales child position and size

- **WHEN** a parent `Node2D` has `transform.scale = (2, 3)` and the child has `transform.position = (10, 0)` and `transform.scale = (1, 1)`
- **THEN** `child.worldTransform().position` equals `(20, 0)`
- **AND** `child.worldTransform().scale` equals `(2, 3)`

#### Scenario: Parent rotation rotates child position

- **WHEN** a parent `Node2D` has `transform.rotation = π / 2` (90° counter-clockwise in engine convention) and the child has `transform.position = (10, 0)` and identity otherwise
- **THEN** `child.worldTransform().position` is approximately `(0, 10)` within floating-point tolerance
- **AND** `child.worldTransform().rotation` equals `π / 2`

#### Scenario: Composition is associative across three levels

- **WHEN** three nested `Node2D`s `a → b → c` each carry non-identity translation, scale and rotation
- **THEN** `c.worldTransform()` equals the composition `a.transform ∘ b.transform ∘ c.transform` as defined by `Transform.compose`

#### Scenario: worldPosition delegates to worldTransform

- **WHEN** any `Node2D` `n` is queried via `n.worldPosition()`
- **THEN** the result equals `n.worldTransform().position`

### Requirement: Safe mutation during scene traversal

The engine SHALL allow `addChild` and `removeChild` to be called from within `onUpdate`, `onCollide` and other traversal-driven hooks without corrupting the children list or raising `ConcurrentModificationException`. When invoked while a scene traversal is in progress, mutations MUST be enqueued onto pending queues on the affected `Node` and applied at deterministic drain points within the same tick. When invoked outside traversal, mutations MUST take effect immediately, preserving the current contract. Pending removals MUST be applied before pending additions to prevent re-adding a node scheduled for removal in the same drain. `onRender` MUST NOT be used to mutate the scene tree; the engine MAY log a warning if such mutation is detected, but SHALL NOT crash.

#### Scenario: addChild during onUpdate does not crash

- **WHEN** a `Node`'s `onUpdate(dt)` calls `scene.addChild(other)` and the scene update traversal is in progress
- **THEN** no exception is raised
- **AND** the children list visible to the remainder of the current update phase MAY or MAY NOT contain `other`, but is consistent (no partial state)

#### Scenario: addChild during onUpdate is visible to physics in the same tick

- **WHEN** a `Node`'s `onUpdate(dt)` enqueues a new child `other` via `addChild`
- **THEN** `other` is part of the live scene during `physics.step(scene)` of the same tick
- **AND** `other.onEnter()` has been invoked exactly once before `physics.step` begins

#### Scenario: removeChild during onCollide does not crash

- **WHEN** a `Collider`'s `onCollide(other)` calls `parent.removeChild(self)` and the physics step is in progress
- **THEN** no exception is raised
- **AND** the collider continues to receive any remaining `onCollide` callbacks already in flight for the current pair iteration without crash

#### Scenario: removeChild during onCollide is visible to render in the same tick

- **WHEN** a `Collider`'s `onCollide` enqueues `removeChild(self)`
- **THEN** the removed node is no longer rendered in `scene.render` of the same tick
- **AND** the removed node received `onExit()` exactly once before `scene.render` begins

#### Scenario: Mutation outside traversal applies immediately

- **WHEN** game code calls `scene.addChild(node)` from outside any lifecycle hook
- **THEN** `node` appears in `scene.children` immediately
- **AND** if the scene is live, `onEnter()` is invoked synchronously before `addChild` returns

#### Scenario: Pending removes drain before pending adds

- **WHEN** within a single traversal, code calls `parent.removeChild(a)` then `parent.addChild(a)`
- **THEN** at the next drain point, `a` ends up still attached (remove then add nets to add)
- **AND** the lifecycle hooks are coherent: `a` receives `onExit` then `onEnter` exactly once each, in that order

### Requirement: Scene reference cached on Node

Every `Node` SHALL expose a property that returns its owning `Scene` in constant time when the node is part of a live tree. When the node is detached, the property SHALL return `null`. The engine SHALL preserve the existing `Node.rootScene(): Scene?` signature; its implementation MAY be backed by a cached field rather than a parent walk. The cached value MUST be set before `onEnter()` runs and cleared after `onExit()` returns, so lifecycle hooks may rely on it.

#### Scenario: rootScene returns the owning Scene in constant time

- **WHEN** any node `n` is part of a live scene
- **THEN** `n.rootScene()` returns the `Scene` instance at the root of `n`'s tree
- **AND** the call performs no parent-chain walk in steady state

#### Scenario: rootScene returns null for detached node

- **WHEN** a node has no parent and is not itself a `Scene`
- **THEN** `n.rootScene()` returns `null`

#### Scenario: onEnter observes a non-null scene

- **WHEN** the engine invokes `onEnter()` on a node being attached to a live scene
- **THEN** `rootScene()` inside that `onEnter()` returns the owning `Scene`

#### Scenario: onExit observes a non-null scene

- **WHEN** the engine invokes `onExit()` on a node being detached from a live scene
- **THEN** `rootScene()` inside that `onExit()` still returns the owning `Scene`

#### Scenario: Detached node does not retain scene reference

- **WHEN** a node is removed from a live scene and the detach completes
- **THEN** subsequent calls to `rootScene()` on the removed node return `null`

### Requirement: Scene rendering decoupled from DX surface

The `Scene.render(renderer: Renderer)` traversal SHALL NOT depend on or consult any symbol from `com.neoutils.engine.dx.*`. Visualization of debug artifacts (collider bounds, FPS overlay, etc.) SHALL be the responsibility of the integrating runtime (e.g. `:engine-compose`), not of the core scene graph. The `:engine.scene` package MUST compile without `:engine.dx` being on the classpath as far as `Scene.render` is concerned, even if other parts of `:engine` continue to expose the `Debug` surface.

#### Scenario: Scene.kt has no import from engine.dx

- **WHEN** the source file `engine/src/main/kotlin/com/neoutils/engine/scene/Scene.kt` is parsed
- **THEN** it contains no import statement beginning with `com.neoutils.engine.dx`

#### Scenario: Scene.render does not draw collider bounds

- **WHEN** `scene.render(renderer)` is invoked
- **THEN** no `Renderer.drawRect(_, _, filled = false)` call is issued by `Scene` itself for the purpose of debug visualization
- **AND** the only draw calls during the traversal originate from `Node.onRender` overrides on user nodes

## MODIFIED Requirements

### Requirement: Math primitives

The engine SHALL provide value-type math primitives sufficient for 2D gameplay: `Vec2` (x, y as `Float`), `Rect` (origin and size), and `Transform` (position as `Vec2`, scale as `Vec2`, rotation as `Float` in radians). All primitives MUST be immutable data classes or equivalent; operations that "modify" them MUST return new instances. `Rect` MUST expose a `contains(point: Vec2): Boolean` operation that returns `true` when the given point lies inside the rectangle's axis-aligned bounds (inclusive on the origin edges, exclusive on the far edges). `Transform` MUST expose a pure `compose(child: Transform): Transform` (or equivalent operator) such that, given a parent transform `P` and a child transform `C` expressed in `P`'s local frame, `P.compose(C)` returns the world transform of the child, applying `P.scale` and `P.rotation` to `C.position` and composing `scale` (component-wise multiplication) and `rotation` (sum) accordingly.

#### Scenario: Vec2 arithmetic returns a new instance

- **WHEN** code evaluates `Vec2(1f, 2f) + Vec2(3f, 4f)`
- **THEN** the result is `Vec2(4f, 6f)`
- **AND** neither operand has been mutated

#### Scenario: Rect intersection detection

- **WHEN** two `Rect` instances overlap on both axes
- **THEN** `rectA.intersects(rectB)` returns `true`

#### Scenario: Rect non-intersection

- **WHEN** two `Rect` instances are disjoint on at least one axis
- **THEN** `rectA.intersects(rectB)` returns `false`

#### Scenario: Rect contains a point strictly inside

- **WHEN** a `Rect` at position `(10, 20)` with size `(30, 40)` is queried with `contains(Vec2(15f, 25f))`
- **THEN** the result is `true`

#### Scenario: Rect does not contain a point outside

- **WHEN** the same `Rect` is queried with `contains(Vec2(5f, 25f))`
- **THEN** the result is `false`

#### Scenario: Transform.compose is identity-neutral

- **WHEN** `Transform()` (identity) is composed with any transform `t`
- **THEN** `Transform().compose(t)` equals `t`
- **AND** `t.compose(Transform())` equals `t`

#### Scenario: Transform.compose multiplies scale component-wise

- **WHEN** parent has `scale = (2, 3)` and child has `scale = (4, 5)`
- **THEN** `parent.compose(child).scale` equals `(8, 15)`

#### Scenario: Transform.compose sums rotation

- **WHEN** parent has `rotation = 0.5` and child has `rotation = 0.25`
- **THEN** `parent.compose(child).rotation` equals `0.75`

#### Scenario: Transform.compose rotates and scales child position

- **WHEN** parent has `position = (10, 20)`, `scale = (2, 2)`, `rotation = π / 2`, and child has `position = (5, 0)` and identity otherwise
- **THEN** `parent.compose(child).position` is approximately `(10, 30)` within floating-point tolerance, reflecting that `(5, 0)` is scaled to `(10, 0)` then rotated 90° to `(0, 10)` then translated by `(10, 20)`

### Requirement: Collision as Collider nodes

The engine SHALL provide an abstract `Collider` subclass of `Node2D` exposing a `bounds(): Rect` method computed in world space. The engine SHALL provide at least one concrete subclass, `BoxCollider`, whose bounds are derived from the node's `worldTransform()` and a configurable local size. `BoxCollider.bounds()` MUST honor `scale` inherited along the ancestor chain. When `worldTransform()` includes a non-zero rotation along the chain, `BoxCollider.bounds()` MUST return the axis-aligned bounding box of the rotated rectangle (AABB-of-OBB); this is a known conservative approximation documented as an evolution point for the future collision-lifecycle change. `Collider` MUST expose an open `onCollide(other: Collider)` hook invoked by the physics system; default implementation MUST be empty.

#### Scenario: BoxCollider bounds reflect transform

- **WHEN** a `BoxCollider` is created with size `(10, 20)` and its node's transform position is set to `(5, 7)`, with no ancestor transforms
- **THEN** `bounds()` returns a `Rect` covering position `(5, 7)` and size `(10, 20)` (axis-aligned)

#### Scenario: BoxCollider bounds reflect parent translation

- **WHEN** a `BoxCollider` with size `(10, 20)` is a child of a `Node2D` whose transform position is `(100, 100)` and the collider's own position is `(5, 7)`
- **THEN** `bounds()` returns a `Rect` covering position `(105, 107)` and size `(10, 20)`

#### Scenario: BoxCollider bounds reflect parent scale

- **WHEN** a `BoxCollider` with size `(10, 20)` is a child of a `Node2D` whose transform has `scale = (2, 3)` and identity otherwise, and the collider's own transform is identity
- **THEN** `bounds()` returns a `Rect` whose size equals `(20, 60)`

#### Scenario: BoxCollider bounds with rotation expand to AABB of OBB

- **WHEN** a `BoxCollider` with size `(10, 10)` has a parent `Node2D` rotated by `π / 4` (45°), with no scale and no translation
- **THEN** `bounds()` returns the axis-aligned bounding box that fully contains the rotated rectangle (width and height ≈ `10 · √2`)

#### Scenario: onCollide receives the colliding partner

- **WHEN** the physics system detects overlap between colliders A and B
- **THEN** A receives `onCollide(B)` and B receives `onCollide(A)` within the same tick

### Requirement: Game loop coordination

The engine SHALL provide a `GameLoop` class that, given a `Scene`, a `Renderer`, an `Input`, and a `PhysicsSystem`, exposes a `tick(dtNanos: Long)` operation. Each `tick` MUST: (1) compute `dt` in seconds, (2) propagate `onUpdate(dt)` to the scene tree, (3) call `physics.step(scene)`, (4) call `scene.render(renderer)`. Between each of these three phases, the engine MUST drain pending child mutations enqueued during the previous phase, in the order `pendingRemove → pendingAdd`, so that nodes spawned in `onUpdate` are visible to `physics.step` and nodes removed in `onCollide` are absent from `scene.render`. The `GameLoop` itself MUST NOT spawn threads or impose its own pacing; the host runtime supplies the pulse.

#### Scenario: Tick order is deterministic

- **WHEN** `gameLoop.tick(dtNanos)` is called
- **THEN** updates complete before physics
- **AND** physics completes before render

#### Scenario: Tick converts nanoseconds to seconds

- **WHEN** `gameLoop.tick(16_666_666L)` is called
- **THEN** scene nodes receive `onUpdate(dt)` with `dt` approximately `0.01667`

#### Scenario: Pending mutations drain between phases

- **WHEN** during `scene.update(dt)` a node enqueues `addChild(spawn)` and during `physics.step(scene)` another node enqueues `removeChild(victim)`
- **THEN** `spawn.onEnter()` runs before `physics.step` begins
- **AND** `victim.onExit()` runs before `scene.render` begins
- **AND** `scene.render` sees `spawn` and does not see `victim`
