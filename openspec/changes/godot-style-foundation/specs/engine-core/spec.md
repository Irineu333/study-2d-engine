## MODIFIED Requirements

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

### Requirement: Renderer SPI

The engine SHALL define a `Renderer` interface used by `onDraw` hooks. The interface MUST cover the operations needed by the sample games in this change: clearing the surface, drawing filled and outlined rectangles, drawing filled and outlined circles, drawing line segments, drawing filled polygons via `drawPolygon(points: List<Vec2>, color: Color)`, drawing text, and measuring text. The interface MUST NOT expose types from `androidx.compose.*` or any backend-specific package. The interface MUST be implementable without reflection or service loaders. The `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` operation MUST draw a straight segment between the two world-space points with the given stroke thickness. The `drawPolygon(points: List<Vec2>, color: Color)` operation MUST fill the polygon described by the vertex list interpreted as a closed path; the implementation MAY assume the polygon is simple (non-self-intersecting) and convex-or-concave-without-holes. The `measureText(text: String, size: Float): Vec2` operation MUST return the bounding box (`Vec2(width, height)`) that `drawText` would produce for the same text and size, allowing callers to align text without backend-specific measurement. The `Color` type used by the renderer MUST be annotated with `@Serializable` (kotlinx.serialization) so it can be embedded as a property value in serialized scene files.

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

### Requirement: Camera2D viewport carrier

The engine SHALL provide a `Camera2D : Node2D` class with `@Inspect var bounds: Rect` (the visible-world region in world coordinates) and `@Inspect var current: Boolean = false` (whether this is the active camera). Setting `current = true` while live MUST cause `Scene.viewport` to reflect this camera's `bounds` until either `current` is set back to `false` or another `Camera2D` becomes current later in the tree. When multiple `Camera2D` nodes have `current = true`, the engine MUST pick the first one in pre-order traversal. `Camera2D` MUST be `@Serializable` and instantiable via no-args constructor, like every other `Node` shipped by `:engine`.

#### Scenario: Camera2D is a Node2D with bounds and current

- **WHEN** code instantiates `Camera2D()`
- **THEN** the result is a valid `Camera2D` with `current == false` and `bounds == Rect(Vec2.ZERO, Vec2.ZERO)`
- **AND** assignable to `Node2D`

#### Scenario: First current Camera2D in pre-order wins

- **GIVEN** a scene with two `Camera2D` nodes both having `current = true`, one at root child position 0 and another deeper at position 2
- **WHEN** code reads `scene.viewport`
- **THEN** the bounds returned are from the camera at child position 0

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

## REMOVED Requirements

### Requirement: Shape primitive node

**Reason:** The single `Shape` class with `Kind.Rect | Kind.Circle` is replaced by dedicated primitive nodes (`ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`). Godot-style vocabulary: each visual primitive is its own node type. `Kind` was a hidden discriminator that the inspector future would have to special-case.

**Migration:** Replace every `Shape` instance:
- `Shape(kind = Kind.Rect, size = s, color = c)` → `ColorRect().also { it.size = s; it.color = c }`
- `Shape(kind = Kind.Circle, size = s, color = c)` → `Circle2D().also { it.radius = s.x / 2f; it.color = c }`

## ADDED Requirements

### Requirement: Camera2D registers as the scene's current camera

When a `Camera2D` has `current = true` and is attached to a live `Scene`, the engine MUST make its `bounds` discoverable via `Scene.viewport`. The engine MUST resolve "the current camera" on read via pre-order tree-walk of the live scene picking the first `Camera2D` with `current = true`; the engine MAY cache this lookup but MUST invalidate on tree mutation or on `current` property changes.

#### Scenario: Toggling current updates viewport

- **GIVEN** a live scene with one `Camera2D` whose `current = false`, and `scene.viewport` returns `Rect(Vec2.ZERO, scene.size)`
- **WHEN** code sets `camera.current = true`
- **THEN** `scene.viewport` next read returns `camera.bounds`

### Requirement: Roadmap includes godot-style-foundation and game-snake

`CLAUDE.md` MUST list `godot-style-foundation` as a roadmap entry with status `Active`, and MUST list `game-snake` as a roadmap entry with status `Planned`. The `game-snake` description MUST mention that Snake is the validator for the foundation refactor (fixed-step, signals, Camera2D bounds, visual primitives without collision dependency).

#### Scenario: Roadmap table includes both entries

- **WHEN** `CLAUDE.md` is opened after this change is created
- **THEN** the roadmap table contains a row for `godot-style-foundation` with status `Active`
- **AND** the roadmap table contains a row for `game-snake` with status `Planned`
- **AND** the `game-snake` row summarizes that it validates the godot-style foundation without depending on the collision overhaul
