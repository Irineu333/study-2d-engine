# engine-core Specification

## Purpose

Scene graph estilo Godot em Kotlin puro — `Node` hierarchy, lifecycle, `Scene`, math primitives, SPI de `Renderer`/`Input`, `Collider` + `PhysicsSystem`, `GameLoop`. Invariante: zero dependência em `androidx.compose.*` ou `org.jetbrains.compose.*`.

## Requirements

### Requirement: Scene graph node hierarchy

The engine SHALL provide a base abstract class `Node` from which all scene elements derive. Each `Node` MUST hold an optional reference to a parent `Node` and a list of children `Node`s, forming a tree. Each `Node` MUST have a mutable `name: String` attribute. The engine SHALL provide a concrete subclass `Node2D` that adds a `Transform` with position, scale and rotation. The engine SHALL provide additional concrete `Node2D` subclasses for primitive visuals: `Shape` (rect or circle), and `Text`.

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

### Requirement: Node lifecycle hooks

The engine SHALL invoke four lifecycle hooks on each `Node` in deterministic order: `onEnter()` when the node is first attached to a live `Scene`, `onUpdate(dt: Float)` once per tick while the node is in a live scene, `onRender(renderer: Renderer)` once per frame after all updates, and `onExit()` when the node is removed from a live scene. Hooks SHALL be open methods on `Node` with empty default implementations so subclasses can override only what they need.

#### Scenario: onEnter fires once when node enters a live scene

- **WHEN** a node is added as a child of a node already in a live `Scene`
- **THEN** the engine invokes `onEnter()` on that node exactly once
- **AND** the engine invokes `onEnter()` recursively on its descendants in pre-order

#### Scenario: onUpdate receives delta time in seconds

- **WHEN** the game loop ticks with a frame delta of 16 milliseconds
- **THEN** the engine invokes `onUpdate(dt)` on every node in a live scene with `dt` approximately equal to `0.016`

#### Scenario: onExit fires once when node leaves the scene

- **WHEN** a node currently in a live scene is removed via `removeChild`
- **THEN** the engine invokes `onExit()` on that node exactly once
- **AND** the engine invokes `onExit()` recursively on its descendants in post-order

#### Scenario: onRender runs after onUpdate within the same frame

- **WHEN** a frame is being processed
- **THEN** all `onUpdate(dt)` calls for that frame complete before any `onRender(renderer)` call begins

### Requirement: Scene as root container

The engine SHALL provide a `Scene` class that acts as the root of a live node tree. A `Scene` MUST be marked "live" once registered with a `GameLoop`. Adding nodes to a live `Scene` (directly or transitively) MUST trigger their `onEnter()`; removing them MUST trigger `onExit()`. The engine SHALL expose `Scene.update(dt: Float)` and `Scene.render(renderer: Renderer)` operations that traverse the tree and invoke the corresponding lifecycle hooks.

#### Scenario: Update traverses the entire live tree

- **WHEN** `scene.update(dt)` is called on a `Scene` containing nodes A, B (child of A), and C
- **THEN** the engine invokes `onUpdate(dt)` on A, B, and C

#### Scenario: Render order follows tree pre-order

- **WHEN** `scene.render(renderer)` is called
- **THEN** parents are rendered before their children
- **AND** siblings are rendered in the order they appear in `children`

### Requirement: Math primitives

The engine SHALL provide value-type math primitives sufficient for 2D gameplay: `Vec2` (x, y as `Float`), `Rect` (origin and size), and `Transform` (position as `Vec2`, scale as `Vec2`, rotation as `Float` in radians). All primitives MUST be immutable data classes or equivalent; operations that "modify" them MUST return new instances. `Rect` MUST expose a `contains(point: Vec2): Boolean` operation that returns `true` when the given point lies inside the rectangle's axis-aligned bounds (inclusive on the origin edges, exclusive on the far edges).

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

### Requirement: Renderer SPI

The engine SHALL define a `Renderer` interface used by `onRender` hooks. The interface MUST cover the operations needed by the sample games in this change: clearing the surface, drawing filled and outlined rectangles, drawing filled and outlined circles, drawing line segments, drawing text, and measuring text. The interface MUST NOT expose types from `androidx.compose.*` or any backend-specific package. The interface MUST be implementable without reflection or service loaders. The `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` operation MUST draw a straight segment between the two world-space points with the given stroke thickness. The `measureText(text: String, size: Float): Vec2` operation MUST return the bounding box (`Vec2(width, height)`) that `drawText` would produce for the same text and size, allowing callers to align text without backend-specific measurement.

#### Scenario: Engine module has no Compose dependency

- **WHEN** the `:engine` module is compiled
- **THEN** its build classpath contains no `androidx.compose.*` artifact

#### Scenario: Renderer is consumed only via the interface

- **WHEN** any class in `:engine` references `Renderer`
- **THEN** it depends only on the `Renderer` interface, not on any concrete backend implementation

#### Scenario: drawLine accepts arbitrary endpoints

- **WHEN** a node calls `renderer.drawLine(Vec2(0f, 0f), Vec2(100f, 100f), thickness = 2f, color = Color.WHITE)`
- **THEN** the backend draws a diagonal stroke between the two points with the requested thickness and color

#### Scenario: measureText reports the bounding box

- **WHEN** a node calls `renderer.measureText("hello", size = 22f)`
- **THEN** the result is a `Vec2` whose `x` is the rendered width and `y` is the rendered height of `drawText("hello", _, 22f, _)` in the same frame

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

The engine SHALL provide an abstract `Collider` subclass of `Node2D` exposing a `bounds(): Rect` method computed in world space. The engine SHALL provide at least one concrete subclass, `BoxCollider`, whose bounds are derived from the node's transform and a configurable size. `Collider` MUST expose an open `onCollide(other: Collider)` hook invoked by the physics system; default implementation MUST be empty.

#### Scenario: BoxCollider bounds reflect transform

- **WHEN** a `BoxCollider` is created with size `(10, 20)` and its node's transform position is set to `(5, 7)`
- **THEN** `bounds()` returns a `Rect` covering position `(5, 7)` and size `(10, 20)` (axis-aligned)

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

The engine SHALL provide a `GameLoop` class that, given a `Scene`, a `Renderer`, an `Input`, and a `PhysicsSystem`, exposes a `tick(dtNanos: Long)` operation. Each `tick` MUST: (1) compute `dt` in seconds, (2) propagate `onUpdate(dt)` to the scene tree, (3) call `physics.step(scene)`, (4) call `scene.render(renderer)`. The `GameLoop` itself MUST NOT spawn threads or impose its own pacing; the host runtime supplies the pulse.

#### Scenario: Tick order is deterministic

- **WHEN** `gameLoop.tick(dtNanos)` is called
- **THEN** updates complete before physics
- **AND** physics completes before render

#### Scenario: Tick converts nanoseconds to seconds

- **WHEN** `gameLoop.tick(16_666_666L)` is called
- **THEN** scene nodes receive `onUpdate(dt)` with `dt` approximately `0.01667`

### Requirement: Engine module has zero Compose dependency

The `:engine` Gradle module SHALL declare no dependency on any `org.jetbrains.compose.*` or `androidx.compose.*` artifact, directly or transitively. This invariant SHALL be enforced by the module's `build.gradle.kts` and verified during code review.

#### Scenario: Adding a Compose dependency to :engine is rejected

- **WHEN** a contributor adds `androidx.compose.foundation` or similar to `:engine`'s dependencies
- **THEN** code review blocks the change
- **AND** the contributor is directed to use the `Renderer`/`Input` SPI or extend the `:engine-compose` runtime instead
