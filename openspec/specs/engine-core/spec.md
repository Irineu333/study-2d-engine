# engine-core Specification

## Purpose

Scene graph estilo Godot em Kotlin puro — `Node` hierarchy, lifecycle, `Scene`, math primitives, SPI de `Renderer`/`Input`, `Collider` + `PhysicsSystem`, `GameLoop`. Invariante: zero dependência em `androidx.compose.*` ou `org.jetbrains.compose.*`.

## Requirements

### Requirement: Scene graph node hierarchy

The engine SHALL provide a base abstract class `Node` from which all scene elements derive. Each `Node` MUST hold an optional reference to a parent `Node` and a list of children `Node`s, forming a tree. Each `Node` MUST have a mutable `name: String` attribute and a mutable `groups: Set<String>` accessor with `addToGroup(name: String)` / `removeFromGroup(name: String)` / `isInGroup(name: String): Boolean`. The engine SHALL provide a concrete subclass `Node2D` that adds a `Transform` with position, scale and rotation. The engine SHALL provide additional concrete `Node2D` subclasses for primitive visuals: `ColorRect` (filled rectangle), `Circle2D` (filled circle), `Line2D` (connected line segments), `Polygon2D` (filled polygon by vertices), `Label` (text), and `Camera2D` (viewport bounds carrier).

#### Scenario: Adding a child node attaches it to the parent

- **WHEN** code calls `parent.addChild(child)` on a `Node` where `child` had no previous parent
- **THEN** `child.parent` becomes `parent`
- **AND** `parent.children` contains `child`

#### Scenario: Removing a child node detaches it from the parent

- **WHEN** code calls `parent.removeChild(child)` for a `child` whose `parent` is `parent`
- **THEN** `child.parent` becomes `null`
- **AND** `parent.children` does not contain `child`

#### Scenario: Node2D exposes a mutable transform

- **WHEN** code creates a `Node2D` and modifies its `transform.position`
- **THEN** subsequent reads of `transform.position` reflect the new value
- **AND** the change does not affect sibling nodes

#### Scenario: Node belongs to groups

- **GIVEN** a `Node` with `addToGroup("paddles")` called
- **WHEN** code reads `node.isInGroup("paddles")`
- **THEN** the result is `true`
- **AND** `node.groups` contains `"paddles"`

#### Scenario: Shape is no longer present in the engine

- **WHEN** the `:engine` source tree is inspected after this change
- **THEN** no class named `Shape` exists under `com.neoutils.engine.scene`
- **AND** the `Kind` enum (`Rect`, `Circle`) does not exist on any scene type

#### Scenario: Text is renamed to Label

- **WHEN** the `:engine` source tree is inspected
- **THEN** the class previously known as `Text` is now `Label` under `com.neoutils.engine.scene`
- **AND** the public API exposes `text: String`, `size: Float`, and `color: Color` as `@Inspect var` fields

### Requirement: Node lifecycle hooks

The engine SHALL invoke five lifecycle hooks on each `Node` in deterministic order: `onEnter()` when the node is first attached to a live `Scene`, `onProcess(dt: Float)` once per frame while the node is in a live scene (variable `dt` from the host pulse), `onPhysicsProcess(dt: Float)` zero-or-more times per frame at a fixed step (driven by the loop accumulator), `onDraw(renderer: Renderer)` once per frame after all process calls of that frame, and `onExit()` when the node is removed from a live scene. Hooks SHALL be open methods on `Node` with empty default implementations so subclasses can override only what they need. The hooks SHALL be named exactly `onEnter`, `onProcess`, `onPhysicsProcess`, `onDraw`, and `onExit` — the names `onUpdate` and `onRender` SHALL NOT exist on `Node` after this change.

#### Scenario: onEnter fires once when node enters a live scene

- **WHEN** a node is added as a child of a node already in a live `Scene`
- **THEN** the engine invokes `onEnter()` on that node exactly once
- **AND** the engine invokes `onEnter()` recursively on its descendants in pre-order

#### Scenario: onProcess receives delta time in seconds

- **WHEN** the game loop ticks with a frame delta of 16 milliseconds
- **THEN** the engine invokes `onProcess(dt)` on every node in a live scene with `dt` approximately equal to `0.016`

#### Scenario: onPhysicsProcess receives a fixed delta time

- **GIVEN** the loop is configured with `physicsHz = 60`
- **WHEN** `onPhysicsProcess(dt)` is invoked on any node in the tick
- **THEN** `dt` is exactly `1f / 60f` (approximately `0.01667`) regardless of the frame `dtNanos`

#### Scenario: onExit fires once when node leaves the scene

- **WHEN** a node currently in a live scene is removed via `removeChild`
- **THEN** the engine invokes `onExit()` on that node exactly once
- **AND** the engine invokes `onExit()` recursively on its descendants in post-order

#### Scenario: onDraw runs after onProcess within the same frame

- **WHEN** a frame is being processed
- **THEN** all `onProcess(dt)` calls for that frame complete before any `onDraw(renderer)` call begins

#### Scenario: Old hook names do not exist on Node

- **WHEN** the source of `Node.kt` is inspected after this change
- **THEN** no `open fun onUpdate(dt: Float)` or `open fun onRender(renderer: Renderer)` declarations exist
- **AND** no override of `onUpdate` or `onRender` exists in any engine-shipped `Node` subclass

### Requirement: Scene as root container

The engine SHALL provide a `Scene` class that acts as the root of a live node tree. A `Scene` MUST be marked "live" once registered with a `GameLoop`. Adding nodes to a live `Scene` (directly or transitively) MUST trigger their `onEnter()`; removing them MUST trigger `onExit()`. The engine SHALL expose `Scene.process(dt: Float)`, `Scene.physicsProcess(dt: Float)` and `Scene.render(renderer: Renderer)` operations that traverse the tree and invoke the corresponding lifecycle hooks. `Scene` MUST expose a mutable `size: Vec2` set by the host runtime via `resize(width: Float, height: Float)`. `Scene` MUST expose a computed property `viewport: Rect` that returns `currentCamera.bounds` when a `Camera2D` with `current = true` exists in the tree, otherwise `Rect(Vec2.ZERO, size)`. `Scene` MUST expose a helper `getNodesInGroup(name: String): List<Node>` that walks the tree and returns every live node whose `groups` contains `name` (order: pre-order traversal).

#### Scenario: process traverses the entire live tree

- **WHEN** `scene.process(dt)` is called on a `Scene` containing nodes A, B (child of A), and C
- **THEN** the engine invokes `onProcess(dt)` on A, B, and C

#### Scenario: physicsProcess traverses the entire live tree

- **WHEN** `scene.physicsProcess(dt)` is called on a `Scene` containing nodes A, B, and C
- **THEN** the engine invokes `onPhysicsProcess(dt)` on each of A, B, C

#### Scenario: Render order follows tree pre-order

- **WHEN** `scene.render(renderer)` is called
- **THEN** parents are rendered before their children
- **AND** siblings are rendered in the order they appear in `children`

#### Scenario: Scene size reflects host resize

- **GIVEN** a `Scene` whose host runtime invokes `scene.resize(800f, 600f)`
- **WHEN** code reads `scene.size`
- **THEN** the result is `Vec2(800f, 600f)`

#### Scenario: viewport falls back to scene size when no Camera2D is current

- **GIVEN** a `Scene` of size `Vec2(800f, 600f)` with no `Camera2D` in the tree (or none with `current = true`)
- **WHEN** code reads `scene.viewport`
- **THEN** the result is `Rect(Vec2.ZERO, Vec2(800f, 600f))`

#### Scenario: viewport reflects current Camera2D bounds

- **GIVEN** a `Scene` containing a `Camera2D` with `bounds = Rect(Vec2(100f, 100f), Vec2(400f, 300f))` and `current = true`
- **WHEN** code reads `scene.viewport`
- **THEN** the result is `Rect(Vec2(100f, 100f), Vec2(400f, 300f))`

#### Scenario: getNodesInGroup returns every node with the group

- **GIVEN** a live scene with nodes `A`, `B`, `C` where `A.addToGroup("paddles")` and `C.addToGroup("paddles")`
- **WHEN** code calls `scene.getNodesInGroup("paddles")`
- **THEN** the result is `[A, C]` in pre-order
- **AND** the result does not contain `B`

### Requirement: Math primitives

The engine SHALL provide value-type math primitives sufficient for 2D gameplay: `Vec2` (x, y as `Float`), `Rect` (origin and size), and `Transform` (position as `Vec2`, scale as `Vec2`, rotation as `Float` in radians). All primitives MUST be immutable data classes or equivalent; operations that "modify" them MUST return new instances. All primitives MUST be annotated with `@Serializable` (kotlinx.serialization) so they can be transparently embedded as property values in serialized scene files. `Rect` MUST expose a `contains(point: Vec2): Boolean` operation that returns `true` when the given point lies inside the rectangle's axis-aligned bounds (inclusive on the origin edges, exclusive on the far edges). `Transform` MUST expose a pure `compose(child: Transform): Transform` (or equivalent operator) such that, given a parent transform `P` and a child transform `C` expressed in `P`'s local frame, `P.compose(C)` returns the world transform of the child, applying `P.scale` and `P.rotation` to `C.position` and composing `scale` (component-wise multiplication) and `rotation` (sum) accordingly.

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

#### Scenario: Math primitives are serializable

- **WHEN** code serializes `Vec2(1f, 2f)`, `Rect(Vec2(0f, 0f), Vec2(10f, 10f))`, or `Transform(position = Vec2(5f, 5f), scale = Vec2(2f, 2f), rotation = 1f)` via `kotlinx.serialization` JSON
- **THEN** each call returns a JSON document
- **AND** deserializing each document yields an instance equal (by `equals`) to the original

### Requirement: Renderer SPI

The engine SHALL define a `Renderer` interface used by `onDraw` hooks. The interface MUST cover the operations needed by the sample games in this change: clearing the surface, drawing filled and outlined rectangles, drawing filled and outlined circles, drawing line segments, drawing filled polygons via `drawPolygon(points: List<Vec2>, color: Color)`, drawing text, and measuring text. The interface MUST NOT expose types from `androidx.compose.*` or any backend-specific package. The interface MUST be implementable without reflection or service loaders. The `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` operation MUST draw a straight segment between the two world-space points with the given stroke thickness. The `drawPolygon(points: List<Vec2>, color: Color)` operation MUST fill the polygon described by the vertex list interpreted as a closed path; the implementation MAY assume the polygon is simple (non-self-intersecting) and convex-or-concave-without-holes. The `measureText(text: String, size: Float): Vec2` operation MUST return the bounding box (`Vec2(width, height)`) that `drawText` would produce for the same text and size, allowing callers to align text without backend-specific measurement. The `Color` type used by the renderer MUST be annotated with `@Serializable` (kotlinx.serialization) so it can be embedded as a property value in serialized scene files.

The interface SHALL additionally expose a 2D affine transform stack via two operations:

```kotlin
fun pushTransform(translation: Vec2, scale: Vec2)
fun popTransform()
```

`pushTransform(translation, scale)` MUST push a new entry onto an internal LIFO stack representing the composition `translate(translation) ∘ scale(scale)` applied to all subsequent `draw*` calls until the matching `popTransform()`. Pushes MUST nest (composition order is parent-then-child: a deeper push composes with the current top). `popTransform()` MUST restore the top to the previous entry and SHALL throw `IllegalStateException` if the stack is empty.

The stack state SHALL start as identity at every backend-defined frame boundary (e.g. when `SkikoRenderer.bind()` runs or when a new `DrawScope` is entered in `ComposeRenderer`). Every `pushTransform` issued during a frame MUST be matched by a `popTransform` before the renderer's frame boundary ends; the engine MUST NOT rely on cross-frame stack state.

#### Scenario: Engine module has no Compose dependency

- **WHEN** the `:engine` module is compiled
- **THEN** its build classpath contains no `androidx.compose.*` artifact

#### Scenario: Renderer is consumed only via the interface

- **WHEN** any class in `:engine` references `Renderer`
- **THEN** it depends only on the `Renderer` interface, not on any concrete backend implementation

#### Scenario: drawLine accepts arbitrary endpoints

- **WHEN** a node calls `renderer.drawLine(Vec2(0f, 0f), Vec2(100f, 100f), thickness = 2f, color = Color.WHITE)`
- **THEN** the backend draws a diagonal stroke between the two points with the requested thickness and color

#### Scenario: drawPolygon fills the polygon described by vertices

- **WHEN** a node calls `renderer.drawPolygon(listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(5f, 10f)), Color.WHITE)`
- **THEN** the backend renders a filled triangle covering those three vertices
- **AND** subsequent calls with different vertex lists produce independent shapes (no state leakage)

#### Scenario: measureText reports the bounding box

- **WHEN** a node calls `renderer.measureText("hello", size = 22f)`
- **THEN** the result is a `Vec2` whose `x` is the rendered width and `y` is the rendered height of `drawText("hello", _, 22f, _)` in the same frame

#### Scenario: Color is serializable

- **WHEN** code serializes `Color(0.5f, 0.25f, 0.125f, 0.75f)` via `kotlinx.serialization` JSON
- **THEN** the resulting JSON document contains the four channel values
- **AND** deserializing yields a `Color` equal (by `equals`) to the original

#### Scenario: pushTransform translates subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2(100f, 50f), scale = Vec2(1f, 1f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the rendered rectangle appears at surface position `(100, 50)` with size `(10, 10)`

#### Scenario: pushTransform scales subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2.ZERO, scale = Vec2(2f, 2f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the rendered rectangle appears at surface position `(0, 0)` with size `(20, 20)`

#### Scenario: popTransform restores the previous transform

- **WHEN** code calls `renderer.pushTransform(Vec2(100f, 0f), Vec2(1f, 1f))`, draws a rect at `(0, 0)`, calls `renderer.popTransform()`, then draws another rect at `(0, 0)`
- **THEN** the first rect appears at surface position `(100, 0)`
- **AND** the second rect appears at surface position `(0, 0)`

#### Scenario: popTransform on empty stack fails fast

- **WHEN** code calls `renderer.popTransform()` without a preceding `pushTransform`
- **THEN** an `IllegalStateException` is raised naming the empty-stack precondition

#### Scenario: Transform stack starts as identity each frame

- **WHEN** a new frame begins on the backend (e.g. `SkikoRenderer.bind(canvas)` or a new `DrawScope` invocation)
- **THEN** a `drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, true)` issued before any `pushTransform` renders at surface position `(0, 0)` with size `(10, 10)`

### Requirement: Input SPI

The engine SHALL define an `Input` interface providing read-only access to current input state at tick time. The interface MUST allow querying whether a given key is currently pressed and the current pointer position. The interface MUST allow querying whether a given mouse button is currently pressed (`isMouseDown`) and whether it was pressed during the current tick (`wasMouseClicked`). The interface MUST define a `MouseButton` enum covering at least `Left`, `Right`, and `Middle`. The interface MUST NOT expose backend-specific event types. The interface MAY expose pressed/released edge events as boolean queries valid for the current tick.

#### Scenario: Engine module reads input only via the interface

- **WHEN** a node in `:engine` queries input state inside `onUpdate`
- **THEN** it does so through the `Input` interface
- **AND** no backend type leaks into the node's source

#### Scenario: Key state reflects current frame

- **WHEN** the game loop polls input at the start of a tick and the underlying backend reports key `K` as pressed
- **THEN** every `Input.isKeyDown(K)` call within that tick returns `true`

#### Scenario: Mouse button click is observable for exactly one tick

- **WHEN** the user presses the left mouse button between tick `N-1` and tick `N`
- **THEN** `Input.wasMouseClicked(MouseButton.Left)` returns `true` for every call within tick `N`
- **AND** returns `false` from tick `N+1` onward unless a new press occurs

#### Scenario: Mouse button held reads as down across ticks

- **WHEN** the user presses and holds the left mouse button across multiple ticks without releasing
- **THEN** `Input.isMouseDown(MouseButton.Left)` returns `true` for every tick during the hold

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

### Requirement: Physics system performs broad-phase collision detection

The engine SHALL provide a `PhysicsSystem` that, on each `step(scene)` call, enumerates all `Collider` nodes attached to the live scene and tests every pair for intersection using axis-aligned bounding boxes. For each intersecting pair, it MUST invoke `onCollide` on both colliders exactly once per tick. The current implementation MAY use a naive O(N²) algorithm; this MUST be documented as a known evolution point.

#### Scenario: Each pair is tested exactly once per tick

- **WHEN** `physics.step(scene)` runs with N active colliders
- **THEN** at most `N * (N - 1) / 2` intersection tests are performed in that tick

#### Scenario: Non-overlapping colliders never receive onCollide

- **WHEN** `physics.step(scene)` runs and colliders A and B do not intersect
- **THEN** A does not receive `onCollide(B)` for that tick
- **AND** B does not receive `onCollide(A)` for that tick

### Requirement: Game loop coordination

The engine SHALL provide a `GameLoop` class that, given a `Scene`, a `Renderer`, an `Input`, a `PhysicsSystem`, and a `physicsHz: Int` (default `60`), exposes a `tick(dtNanos: Long)` operation. Each `tick` MUST: (1) compute `dtFrame` in seconds; (2) accumulate `dtFrame` into an internal `accumulator` and, while `accumulator >= 1f / physicsHz` and steps `< maxStepsPerFrame` (`= 5`), execute a physics step: drain pending → `scene.physicsProcess(physicsDt)` → drain pending → `physics.step(scene)` → decrement accumulator; (3) drain pending and call `scene.process(dtFrame)`; (4) drain pending and call `scene.render(renderer)`. When the inner loop hits `maxStepsPerFrame` and accumulator is still above `physicsDt`, the engine MUST clamp `accumulator` to `0f` to prevent spiral-of-death. The `GameLoop` itself MUST NOT spawn threads or impose its own pacing; the host runtime supplies the pulse.

#### Scenario: Tick order is deterministic

- **WHEN** `gameLoop.tick(dtNanos)` is called and `dtNanos >= physicsDtNanos`
- **THEN** at least one physics step runs before `scene.process(dtFrame)`
- **AND** `scene.process(dtFrame)` runs before `scene.render(renderer)`

#### Scenario: Physics step uses fixed dt

- **GIVEN** `physicsHz = 60`
- **WHEN** `gameLoop.tick(dtNanos)` triggers `n` physics steps
- **THEN** each `scene.physicsProcess(dt)` call sees `dt == 1f / 60f`
- **AND** each `physics.step(scene)` is paired one-to-one with a `scene.physicsProcess` call

#### Scenario: Process phase uses variable dt

- **WHEN** `gameLoop.tick(dtNanos)` is called with `dtNanos = 16_666_666L`
- **THEN** `scene.process(dt)` runs with `dt ≈ 0.01667`

#### Scenario: Sub-physics-step frames accumulate without running physics

- **GIVEN** `physicsHz = 60` (physics dt ≈ 16.67ms) and an empty accumulator
- **WHEN** `gameLoop.tick(8_333_333L)` is called (8.33ms frame)
- **THEN** no physics step runs in that tick
- **AND** `scene.process(dt)` still runs once with `dt ≈ 0.00833`

#### Scenario: Multiple physics steps run in a long frame

- **GIVEN** `physicsHz = 60` and an empty accumulator
- **WHEN** `gameLoop.tick(50_000_000L)` is called (50ms frame)
- **THEN** between 2 and 3 physics steps execute in that tick (3 if accumulator reaches threshold three times)
- **AND** `scene.process(dt)` still runs exactly once at the end with `dt ≈ 0.050`

#### Scenario: Spiral-of-death clamps accumulator

- **GIVEN** the loop has just executed `maxStepsPerFrame` physics steps
- **AND** `accumulator > physicsDt` still
- **WHEN** the loop exits the inner step loop
- **THEN** `accumulator` is reset to `0f`
- **AND** the next call to `tick` does not catastrophically queue more steps

#### Scenario: Pending mutations drain between phases

- **WHEN** during `scene.physicsProcess(dt)` a node enqueues `addChild(spawn)` and during `physics.step(scene)` another node enqueues `removeChild(victim)`
- **THEN** `spawn.onEnter()` runs before `physics.step` begins
- **AND** `victim.onExit()` runs before subsequent traversals see it
- **AND** `scene.render` sees `spawn` and does not see `victim`

### Requirement: Transform composition by ancestry

The engine SHALL compose `Transform`s along the chain of `Node2D` ancestors so that `position`, `scale` and `rotation` of a parent affect the world-space transform of its descendants. `Node2D` MUST expose `worldTransform(): Transform` that returns the composed transform from the topmost `Node2D` ancestor down to `this`, applying `scale` and `rotation` of each ancestor to the local frame of the next descendant. `Node2D.worldPosition()` SHALL be equivalent to `worldTransform().position`. `Transform` MUST expose a pure `compose(child: Transform): Transform` (or equivalent operator) that, given a parent transform, returns the world transform for a child described in the parent's local frame.

`Node2D` MUST cache the result of `worldTransform()` per-node so that consecutive reads without any intervening mutation return the cached value in O(1). The cache MUST be invalidated when (a) the node's own `transform` property is assigned a new value, (b) the node is attached to or detached from a parent (reparenting), or (c) any ancestor's `transform` is assigned a new value. Cache invalidation MUST propagate from the mutated node to all `Node2D` descendants, traversing through non-`Node2D` nodes in the chain. The cached value MUST NOT be persisted by `SceneLoader` — it is runtime-only state that begins unset after deserialization and is populated lazily on first read.

The cached value MUST be observably indistinguishable from a fresh computation: any sequence of mutations followed by a read MUST yield the same `Transform` as if `worldTransform()` had been computed from scratch. `Transform`, `Vec2` and `compose` MUST remain immutable value types; the cache is the only state added to support caching.

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

#### Scenario: Repeated reads without mutation return cached value

- **WHEN** `n.worldTransform()` is called twice in a row without any intervening mutation to `n.transform`, `n.parent`, or any ancestor's `transform`
- **THEN** both calls return a `Transform` equal in `position`, `scale` and `rotation`
- **AND** the second call MUST NOT walk the ancestor chain or compose any transforms

#### Scenario: Assigning a new local transform invalidates self and descendants

- **WHEN** `parent.transform = parent.transform.copy(position = (50, 50))` is executed after both `parent.worldTransform()` and `child.worldTransform()` have been called
- **THEN** the next `parent.worldTransform()` reflects the new position
- **AND** the next `child.worldTransform().position` reflects the parent's new position composed with the child's local transform

#### Scenario: Invalidation propagates through non-Node2D intermediates

- **WHEN** a hierarchy `grandparent: Node2D → middle: Node (not Node2D) → grandchild: Node2D` exists, both `grandparent.worldTransform()` and `grandchild.worldTransform()` have been called, and `grandparent.transform` is then reassigned
- **THEN** the next `grandchild.worldTransform()` reflects the grandparent's new transform composed with the grandchild's local transform

#### Scenario: Reparenting invalidates the moved subtree

- **WHEN** a node `child` whose `worldTransform()` has already been computed is removed from its current parent and added to a different parent with a different transform
- **THEN** the next `child.worldTransform()` reflects the composition with the new parent's transform, not the old one

#### Scenario: Loading a scene yields nodes whose cache is unpopulated

- **WHEN** a scene is loaded via `SceneLoader.load(...)` and `worldTransform()` is called on a freshly loaded node
- **THEN** the returned `Transform` is computed from the live ancestor chain (not from any persisted cache)
- **AND** the produced JSON from `SceneLoader.save(scene)` does not contain any field for the cached world transform

### Requirement: Safe mutation during scene traversal

The engine SHALL allow `addChild` and `removeChild` to be called from within `onProcess`, `onPhysicsProcess`, `onCollide` and other traversal-driven hooks without corrupting the children list or raising `ConcurrentModificationException`. When invoked while a scene traversal is in progress, mutations MUST be enqueued onto pending queues on the affected `Node` and applied at deterministic drain points within the same tick. When invoked outside traversal, mutations MUST take effect immediately, preserving the current contract. Pending removals MUST be applied before pending additions to prevent re-adding a node scheduled for removal in the same drain. `onDraw` MUST NOT be used to mutate the scene tree; the engine MAY log a warning if such mutation is detected, but SHALL NOT crash.

#### Scenario: addChild during onProcess does not crash

- **WHEN** a `Node`'s `onProcess(dt)` calls `scene.addChild(other)` and the scene traversal is in progress
- **THEN** no exception is raised
- **AND** the children list visible to the remainder of the current process phase MAY or MAY NOT contain `other`, but is consistent (no partial state)

#### Scenario: addChild during onPhysicsProcess is visible to physics in the same step

- **WHEN** a `Node`'s `onPhysicsProcess(dt)` enqueues a new child `other` via `addChild`
- **THEN** `other` is part of the live scene during `physics.step(scene)` of the same physics step
- **AND** `other.onEnter()` has been invoked exactly once before `physics.step` begins

#### Scenario: removeChild during onCollide does not crash

- **WHEN** a `Collider`'s `onCollide(other)` calls `parent.removeChild(self)` and the physics step is in progress
- **THEN** no exception is raised
- **AND** the collider continues to receive any remaining `onCollide` callbacks already in flight for the current pair iteration without crash

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

### Requirement: Engine module has zero Compose dependency

The `:engine` Gradle module SHALL declare no dependency on any `org.jetbrains.compose.*` or `androidx.compose.*` artifact, directly or transitively. This invariant SHALL be enforced by the module's `build.gradle.kts` and verified during code review.

#### Scenario: Adding a Compose dependency to :engine is rejected

- **WHEN** a contributor adds `androidx.compose.foundation` or similar to `:engine`'s dependencies
- **THEN** code review blocks the change
- **AND** the contributor is directed to use the `Renderer`/`Input` SPI or extend the `:engine-compose` runtime instead

### Requirement: GameHost SPI

The engine SHALL define a `GameHost` interface that represents the host of execution of a game: it owns a window/surface, drives the per-frame pulse, wires `Input` events from the platform into the engine, and runs the game loop until the host is closed. The interface MUST expose a single `run(scene: Scene, config: GameConfig)` operation. The operation MUST be blocking: it returns only after the host's window/surface has been closed by the user or by code. The interface MUST NOT expose backend-specific types in its method signatures; both inputs (`Scene`, `GameConfig`) and the return type live in `:engine`. The interface MUST be implementable without reflection or service loaders.

#### Scenario: Engine module reads no backend type to declare GameHost

- **WHEN** the `:engine` module is compiled
- **THEN** `GameHost` is declared with parameters whose types come only from `:engine`
- **AND** no import in the file declaring `GameHost` begins with `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, or `javax.swing.*`

#### Scenario: run blocks until the host is closed

- **WHEN** code calls `host.run(scene, config)` and the host opens a window
- **THEN** the call does not return while the window remains open
- **AND** the call returns after the window is closed by the user or by code

#### Scenario: Compose and Skiko hosts implement GameHost

- **WHEN** code in `:games:tictactoe` instantiates `ComposeHost()` from `:engine-compose`
- **THEN** the result is assignable to `GameHost`
- **AND** when code in `:games:pong` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is assignable to `GameHost`

### Requirement: GameConfig host configuration

The engine SHALL provide a `data class GameConfig` carrying the configuration a `GameHost` needs to open its window and behave consistently across backends. `GameConfig` MUST expose at minimum a `title: String`, a `width: Int`, a `height: Int`, a `toggleFpsKey: Key`, and a `toggleCollidersKey: Key`. All fields MUST have sensible defaults so that `GameConfig()` is a valid call site. The default values for `toggleFpsKey` and `toggleCollidersKey` MUST be `Key.F1` and `Key.F2` respectively, so that any host implementation honors the historical F1/F2 affordance without per-game wiring. `GameConfig` MUST be a `data class` so equality, `copy()`, and component destructuring are available.

#### Scenario: Default constructor is valid

- **WHEN** code calls `GameConfig()`
- **THEN** the result is a valid `GameConfig`
- **AND** `title` is a non-empty string
- **AND** `width` and `height` are positive integers

#### Scenario: Default toggle keys are F1 and F2

- **WHEN** code reads `GameConfig().toggleFpsKey` and `GameConfig().toggleCollidersKey`
- **THEN** the results are `Key.F1` and `Key.F2` respectively

#### Scenario: Toggle keys are configurable

- **WHEN** code calls `GameConfig(toggleFpsKey = Key.DIGIT_9, toggleCollidersKey = Key.DIGIT_0)`
- **THEN** the result reports `Key.DIGIT_9` and `Key.DIGIT_0` for the respective fields
- **AND** any `GameHost.run(scene, this)` honors those keys instead of F1/F2

### Requirement: Toggle keys flip debug flags through the host

Every `GameHost` implementation SHALL, on each tick, observe `Input.wasKeyPressed(config.toggleFpsKey)` and `Input.wasKeyPressed(config.toggleCollidersKey)` and toggle `Debug.showFps` / `Debug.colliderVisualization` respectively when a press is observed. This responsibility lives in the host so that game `Main.kt` files do not need to wire keyboard handlers outside the engine to control debug overlays.

#### Scenario: Pressing the configured FPS toggle flips Debug.showFps

- **WHEN** the user presses the key configured as `toggleFpsKey` while a `GameHost` is running a scene
- **THEN** `Debug.showFps` is flipped to its negation by the time the next frame is rendered
- **AND** the next frame either shows or hides the FPS overlay accordingly

#### Scenario: Pressing the configured colliders toggle flips Debug.colliderVisualization

- **WHEN** the user presses the key configured as `toggleCollidersKey` while a `GameHost` is running a scene
- **THEN** `Debug.colliderVisualization` is flipped to its negation by the time the next frame is rendered

#### Scenario: Toggles never live in game code

- **WHEN** any `Main.kt` under `:games:` is inspected after this change
- **THEN** no file installs a keyboard handler outside the engine for the purpose of toggling `Debug.showFps` or `Debug.colliderVisualization`
- **AND** game code relies on the host to perform those toggles

### Requirement: Sibling node names are unique with auto-suffix

The engine SHALL enforce uniqueness of `Node.name` among the children of any given parent. When `parent.addChild(child)` is invoked (either directly or via pending-drain) and `parent` already has a child whose `name` equals `child.name`, the engine MUST mutate `child.name` by appending `_2`, `_3`, ... until the resulting name is unique among `parent.children`. The mutated name MUST become the canonical name of `child` for the remainder of its lifetime, including subsequent `findChild` lookups and `NodeRef` resolutions. Removal of a child SHALL NOT renumber surviving siblings.

#### Scenario: Auto-suffix on first conflict

- **GIVEN** a parent already containing a child named `Ball`
- **WHEN** code calls `parent.addChild(other)` where `other.name == "Ball"`
- **THEN** `other.name` becomes `Ball_2`
- **AND** both children are present in `parent.children`

#### Scenario: Auto-suffix increments past existing suffixed siblings

- **GIVEN** a parent containing children named `Ball`, `Ball_2`
- **WHEN** code calls `parent.addChild(another)` where `another.name == "Ball"`
- **THEN** `another.name` becomes `Ball_3`

#### Scenario: Names without conflict are preserved

- **GIVEN** a parent with no child named `Paddle`
- **WHEN** code calls `parent.addChild(paddle)` where `paddle.name == "Paddle"`
- **THEN** `paddle.name` remains `Paddle`

#### Scenario: Suffix survives detach

- **GIVEN** a child whose name was auto-suffixed to `Ball_2`
- **WHEN** the child is removed from its parent
- **THEN** the child's `name` remains `Ball_2`

#### Scenario: Removing a child does not renumber siblings

- **GIVEN** a parent with children named `Ball`, `Ball_2`, `Ball_3`
- **WHEN** code calls `parent.removeChild(ballChild)` (the one named `Ball`)
- **THEN** the surviving children's names remain `Ball_2` and `Ball_3`

### Requirement: findChild looks up a direct child by name

The engine SHALL expose `Node.findChild(name: String): Node?` that returns the direct child of the receiver whose `name` equals `name`, or `null` if no such child exists. The lookup MUST be a single-level search (not recursive). The lookup MAY be `O(n)` in the number of children.

#### Scenario: findChild returns the matching child

- **GIVEN** a parent with children named `A`, `B`, `C`
- **WHEN** code calls `parent.findChild("B")`
- **THEN** the result is the child named `B`

#### Scenario: findChild returns null for missing name

- **GIVEN** a parent with children named `A`, `B`
- **WHEN** code calls `parent.findChild("Z")`
- **THEN** the result is `null`

#### Scenario: findChild does not descend into grandchildren

- **GIVEN** a parent with child `A` that itself has a child named `Target`
- **WHEN** code calls `parent.findChild("Target")`
- **THEN** the result is `null`

### Requirement: Serializable Node classes have no-args public constructors

Every concrete `Node` subclass shipped by `:engine` (i.e. `Node2D`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `Label`, `Camera2D`, `BoxCollider`) SHALL provide a public no-args primary constructor with sensible defaults. All initial configuration that previously lived in constructor parameters SHALL be exposed as `var` properties on the class instead. Each such property SHALL be annotated either with `@Inspect` (when it is part of the serialized contract) or with `@Transient` (when it is internal runtime state). The class itself SHALL be annotated with `@Serializable` (kotlinx.serialization). DX-oriented factory functions MAY exist on the side to reduce verbosity, but they MUST NOT be the only path to instantiate the class.

#### Scenario: Concrete Node classes can be instantiated with no arguments

- **WHEN** code evaluates `Node2D()`, `ColorRect()`, `Circle2D()`, `Line2D()`, `Polygon2D()`, `Label()`, `Camera2D()`, `BoxCollider()`
- **THEN** each call returns a valid instance with default property values

#### Scenario: Configuration is set via mutable properties

- **WHEN** code instantiates `ColorRect()` and then sets `colorRect.size = Vec2(20f, 20f)` and `colorRect.color = Color.WHITE`
- **THEN** subsequent reads of those properties reflect the assignments

#### Scenario: Every var property on a serializable Node is annotated

- **WHEN** any class in `:engine` that extends `Node` and is annotated `@Serializable` is inspected
- **THEN** every `var` property on the class is annotated either with `@Inspect` or with `@Transient`

### Requirement: Camera2D viewport carrier

The engine SHALL provide a `Camera2D : Node2D` class with `@Inspect var bounds: Rect` (the visible-world region in world coordinates), `@Inspect var current: Boolean = false` (whether this is the active camera), and `@Inspect var aspectMode: AspectMode = AspectMode.FIT` (how the world bounds map onto the surface when the aspect ratios differ). `AspectMode` SHALL be an enum with members `FIT`, `FILL`, and `STRETCH`. Setting `current = true` while live MUST cause `Scene.viewport` to reflect this camera's `bounds` until either `current` is set back to `false` or another `Camera2D` becomes current later in the tree. When multiple `Camera2D` nodes have `current = true`, the engine MUST pick the first one in pre-order traversal. `Camera2D` MUST be `@Serializable` and instantiable via no-args constructor, like every other `Node` shipped by `:engine`.

`Camera2D` SHALL additionally expose two pure coordinate-conversion helpers:

```kotlin
fun screenToWorld(screenPosition: Vec2, sceneSize: Vec2): Vec2
fun worldToScreen(worldPosition: Vec2, sceneSize: Vec2): Vec2
```

Both helpers MUST honor `bounds` and `aspectMode`: for `FIT` they use the uniform scale `min(sceneSize.x / bounds.size.x, sceneSize.y / bounds.size.y)` with centered offsets; for `FILL` they use `max(...)`; for `STRETCH` they use independent per-axis scales. The two helpers MUST be true inverses on the world rectangle covered by the visible viewport: `worldToScreen(screenToWorld(p, s), s)` MUST equal `p` within floating-point tolerance for any `p` inside the visible region. When `bounds.size.x` or `bounds.size.y` is `<= 0f`, both helpers MUST fall back to identity (return their argument unchanged) so caller code does not encounter division by zero.

#### Scenario: Camera2D is a Node2D with bounds, current, and aspect mode

- **WHEN** code instantiates `Camera2D()`
- **THEN** the result is a valid `Camera2D` with `current == false`, `bounds == Rect(Vec2.ZERO, Vec2.ZERO)`, and `aspectMode == AspectMode.FIT`
- **AND** assignable to `Node2D`

#### Scenario: First current Camera2D in pre-order wins

- **GIVEN** a scene with two `Camera2D` nodes both having `current = true`, one at root child position 0 and another deeper at position 2
- **WHEN** code reads `scene.viewport`
- **THEN** the bounds returned are from the camera at child position 0

#### Scenario: AspectMode FIT maps the world fully inside the surface

- **GIVEN** a `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `aspectMode = AspectMode.FIT`
- **WHEN** code calls `camera.worldToScreen(Vec2(0f, 0f), Vec2(1280f, 900f))` and `camera.worldToScreen(Vec2(800f, 600f), Vec2(1280f, 900f))`
- **THEN** both results lie inside the rectangle `Rect(Vec2.ZERO, Vec2(1280f, 900f))`
- **AND** the uniform scale applied is `min(1280f / 800f, 900f / 600f) = 1.5f`

#### Scenario: screenToWorld inverts worldToScreen

- **GIVEN** a `Camera2D` with `bounds = Rect(Vec2(0f, 0f), Vec2(800f, 600f))` and `aspectMode = AspectMode.FIT` and `sceneSize = Vec2(1280f, 900f)`
- **WHEN** code calls `camera.worldToScreen(p, sceneSize)` for any `p` inside `bounds`, then feeds the result back through `camera.screenToWorld(_, sceneSize)`
- **THEN** the round-trip returns a `Vec2` equal to `p` within `0.001f` tolerance

#### Scenario: Degenerate bounds fall back to identity

- **WHEN** code calls `camera.screenToWorld(Vec2(50f, 50f), Vec2(800f, 600f))` and `camera.worldToScreen(Vec2(50f, 50f), Vec2(800f, 600f))` on a `Camera2D` whose `bounds.size` has a zero or negative component
- **THEN** both calls return `Vec2(50f, 50f)` unchanged
- **AND** no exception is raised

### Requirement: Visual primitive nodes

The engine SHALL provide concrete `Node2D` subclasses dedicated to common 2D visuals, each `@Serializable` with no-args public primary constructor and configuration via `@Inspect var` properties:

- `ColorRect`: `size: Vec2`, `color: Color`. `onDraw` issues a filled `drawRect(Rect(worldPosition(), worldScaledSize()), color)`.
- `Circle2D`: `radius: Float`, `color: Color`. `onDraw` issues a filled `drawCircle(worldCenter(), worldRadius(), color, filled = true)` where center accounts for parent scale.
- `Line2D`: `points: List<Vec2>` (local-space), `thickness: Float`, `color: Color`. `onDraw` issues consecutive `drawLine` calls between adjacent points after applying world translation (rotation/scale of ancestors NOT applied to points in this change — known limitation).
- `Polygon2D`: `points: List<Vec2>` (local-space), `color: Color`. `onDraw` issues `drawPolygon` of points translated by `worldPosition()`.
- `Label`: `text: String`, `size: Float`, `color: Color`. `onDraw` issues `drawText(text, worldPosition(), size, color)`.

None of these nodes apply ancestor `rotation` visually in this change; this matches the previous `Shape` limitation and is a documented evolution point for a future renderer `withTransform` capability.

#### Scenario: ColorRect renders a filled rectangle at world position

- **WHEN** a `ColorRect` with `size = Vec2(40f, 20f)` and `color = Color.WHITE` is in a live scene at world position `Vec2(10f, 10f)`
- **THEN** `scene.render(renderer)` issues at least one `drawRect(Rect(Vec2(10f, 10f), Vec2(40f, 20f)), Color.WHITE, filled = true)`

#### Scenario: Circle2D renders a filled circle at world center

- **WHEN** a `Circle2D` with `radius = 10f` and `color = Color.WHITE` is in a live scene at world position `Vec2(50f, 50f)`
- **THEN** `scene.render(renderer)` issues a `drawCircle` whose center accounts for `Vec2(50f, 50f)` and radius `10f`
- **AND** the color is `Color.WHITE`

#### Scenario: Line2D renders consecutive segments

- **WHEN** a `Line2D` with `points = listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(10f, 10f))` and `thickness = 2f` is in a live scene at world position `Vec2(0f, 0f)`
- **THEN** `scene.render(renderer)` issues exactly two `drawLine` calls — one from `(0, 0)` to `(10, 0)`, and one from `(10, 0)` to `(10, 10)`

#### Scenario: Polygon2D renders a filled polygon

- **WHEN** a `Polygon2D` with `points = listOf(Vec2(0f, 0f), Vec2(20f, 0f), Vec2(10f, 20f))` and `color = Color.WHITE` is in a live scene at world position `Vec2(100f, 100f)`
- **THEN** `scene.render(renderer)` issues a `drawPolygon` with points `[Vec2(100, 100), Vec2(120, 100), Vec2(110, 120)]` and color `Color.WHITE`

#### Scenario: Label renders text at world position

- **WHEN** a `Label` with `text = "score"`, `size = 24f`, `color = Color.WHITE` is in a live scene at world position `Vec2(0f, 0f)`
- **THEN** `scene.render(renderer)` issues `drawText("score", Vec2(0f, 0f), 24f, Color.WHITE)` exactly once

### Requirement: Signal as runtime event hub

The engine SHALL provide a generic `Signal<T>` class in `:engine` exposing the following runtime API:

```kotlin
class Signal<T> {
    fun connect(handler: (T) -> Unit): Disposable
    fun disconnect(disposable: Disposable)
    fun emit(value: T)
}
```

`connect` MUST return a `Disposable` that, when disposed, removes the handler. `emit(value)` MUST invoke every connected handler synchronously in registration order. Emitting from inside a handler MUST be safe (handlers added during emit MAY or MAY NOT be invoked in the same emit pass — implementation-defined, but must not crash). `Signal` MUST be `@Serializable`-tolerant: instances appearing as fields on a `@Serializable Node` MUST be annotated `@Transient` (they hold runtime handlers, not configuration).

#### Scenario: connect and emit invoke the handler

- **GIVEN** a `Signal<String>` with one handler connected
- **WHEN** code calls `signal.emit("hello")`
- **THEN** the handler is invoked with the value `"hello"` exactly once

#### Scenario: disconnect removes the handler

- **GIVEN** a `Signal<String>` with two handlers, one of which has been disposed
- **WHEN** code calls `signal.emit("hello")`
- **THEN** only the still-connected handler is invoked

#### Scenario: Emit invokes handlers in connection order

- **GIVEN** a `Signal<Int>` where handler `A` was connected before `B`
- **WHEN** code calls `signal.emit(1)`
- **THEN** `A` is invoked before `B`

### Requirement: Camera2D registers as the scene's current camera

When a `Camera2D` has `current = true` and is attached to a live `Scene`, the engine MUST make its `bounds` discoverable via `Scene.viewport`. The engine MUST resolve "the current camera" on read via pre-order tree-walk of the live scene picking the first `Camera2D` with `current = true`; the engine MAY cache this lookup but MUST invalidate on tree mutation or on `current` property changes.

`Scene.render(renderer)` SHALL consult the current camera at the start of the render traversal. When a current `Camera2D` exists with `bounds.size.x > 0f` and `bounds.size.y > 0f`, `Scene.render` MUST compute the view transform from `(camera.bounds, scene.size, camera.aspectMode)` and call `renderer.pushTransform(translation, scale)` BEFORE issuing any `_draw` walk, then call `renderer.popTransform()` AFTER the walk finishes (including via the `finally` of any traversal try/finally). When no current camera exists or its bounds are degenerate, `Scene.render` MUST NOT push any transform — the `_draw` walk runs against the identity transform (preserving the pre-change behavior of `pixels = world` for camera-less scenes).

`Scene` SHALL additionally expose two coordinate-conversion conveniences:

```kotlin
fun screenToWorld(screenPosition: Vec2): Vec2
fun worldToScreen(worldPosition: Vec2): Vec2
```

Both methods MUST delegate to the current `Camera2D`'s `screenToWorld` / `worldToScreen`, passing `scene.size` as the surface size argument. When no current camera exists (or its bounds are degenerate), both methods MUST return the input unchanged (identity fallback) — the same condition under which `Scene.render` skips its push, so nodes can read input pointer coordinates uniformly regardless of whether the scene has a camera.

#### Scenario: Toggling current updates viewport

- **GIVEN** a live scene with one `Camera2D` whose `current = false`, and `scene.viewport` returns `Rect(Vec2.ZERO, scene.size)`
- **WHEN** code sets `camera.current = true`
- **THEN** `scene.viewport` next read returns `camera.bounds`

#### Scenario: Scene.render with current camera pushes a transform

- **GIVEN** a live scene of `size = Vec2(1280f, 900f)` containing a `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))`, `current = true`, `aspectMode = AspectMode.FIT`, and a single `ColorRect` of `size = Vec2(800f, 600f)` at world `Vec2(0f, 0f)`
- **WHEN** `scene.render(renderer)` runs against a recording `Renderer` implementation
- **THEN** the first call observed is `pushTransform(...)` mapping `bounds` onto the surface via FIT
- **AND** the `ColorRect`'s `drawRect` call uses world coordinates `Rect(Vec2(0f, 0f), Vec2(800f, 600f))`
- **AND** the last call observed is `popTransform()`

#### Scenario: Scene.render without a current camera does not push a transform

- **GIVEN** a live scene with no `Camera2D` (or a `Camera2D` with `current = false`)
- **WHEN** `scene.render(renderer)` runs against a recording `Renderer` implementation
- **THEN** no `pushTransform` or `popTransform` call is observed during the traversal
- **AND** the `_draw` calls reach the renderer unchanged (identity transform)

#### Scenario: Scene.render with degenerate camera bounds falls back to identity

- **GIVEN** a live scene with a current `Camera2D` whose `bounds.size` has a zero or negative component
- **WHEN** `scene.render(renderer)` runs
- **THEN** no `pushTransform` or `popTransform` call is observed
- **AND** the `_draw` calls reach the renderer unchanged

#### Scenario: Scene.screenToWorld delegates to current camera

- **GIVEN** a live scene with `size = Vec2(1280f, 900f)` and a current `Camera2D` whose `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `aspectMode = AspectMode.FIT`
- **WHEN** code calls `scene.screenToWorld(Vec2(640f, 450f))` (the surface center)
- **THEN** the result equals `Vec2(400f, 300f)` (the world center inside `bounds`)
- **AND** `scene.worldToScreen(Vec2(400f, 300f))` round-trips back to `Vec2(640f, 450f)`

#### Scenario: Scene.screenToWorld identity without current camera

- **GIVEN** a live scene with no current `Camera2D` and `size = Vec2(800f, 600f)`
- **WHEN** code calls `scene.screenToWorld(Vec2(123f, 456f))` and `scene.worldToScreen(Vec2(123f, 456f))`
- **THEN** both calls return `Vec2(123f, 456f)` unchanged

### Requirement: Roadmap includes godot-style-foundation and game-snake

`CLAUDE.md` MUST list `godot-style-foundation` as a roadmap entry with status `Active`, and MUST list `game-snake` as a roadmap entry with status `Planned`. The `game-snake` description MUST mention that Snake is the validator for the foundation refactor (fixed-step, signals, Camera2D bounds, visual primitives without collision dependency).

#### Scenario: Roadmap table includes both entries

- **WHEN** `CLAUDE.md` is opened after this change is created
- **THEN** the roadmap table contains a row for `godot-style-foundation` with status `Active`
- **AND** the roadmap table contains a row for `game-snake` with status `Planned`
- **AND** the `game-snake` row summarizes that it validates the godot-style foundation without depending on the collision overhaul
