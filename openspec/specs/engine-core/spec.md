# engine-core Specification

## Purpose

Scene graph estilo Godot em Kotlin puro — `Node` hierarchy, lifecycle, `SceneTree` (live tree owner, não-Node), math primitives, SPI de `Renderer`/`Input`, `Collider` + `PhysicsSystem`, `GameLoop`. Invariante: zero dependência em `androidx.compose.*` ou `org.jetbrains.compose.*`.
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

The engine SHALL invoke five lifecycle hooks on each `Node` in deterministic order: `onEnter()` when the node is first attached to a live tree (via `SceneTree`), `onProcess(dt: Float)` once per frame while the node is in a live tree (variable `dt` from the host pulse), `onPhysicsProcess(dt: Float)` zero-or-more times per frame at a fixed step (driven by the loop accumulator), `onDraw(renderer: Renderer)` once per frame after all process calls of that frame, and `onExit()` when the node is removed from a live tree. Hooks SHALL be open methods on `Node` with empty default implementations so subclasses can override only what they need. The hooks SHALL be named exactly `onEnter`, `onProcess`, `onPhysicsProcess`, `onDraw`, and `onExit` — the names `onUpdate` and `onRender` SHALL NOT exist on `Node` after this change.

#### Scenario: onEnter fires once when node enters a live scene

- **WHEN** a node is added as a child of a node already in a live `SceneTree`
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

### Requirement: Node base class supports non-visual logical nodes

The engine SHALL support `Node` subclasses that do NOT extend `Node2D` as first-class scene members. Such nodes MUST receive `onEnter`, `onProcess`, `onPhysicsProcess`, and `onExit` lifecycle callbacks like any other `Node`, MUST be registrable in `NodeRegistry`, and MUST be loadable from `scene.json`. They MUST NOT participate in transform composition, draw traversal, or collision iteration since they carry no spatial state. `Timer` is the first such node introduced; the engine SHALL preserve this capability as a precedent for future logical nodes (e.g. `AudioPlayer`, `AnimationPlayer`).

#### Scenario: A non-Node2D subclass receives lifecycle hooks

- **GIVEN** a `Node` subclass that does NOT extend `Node2D` (such as `Timer`) added as a child of a live scene root
- **WHEN** the game loop runs one frame
- **THEN** the subclass receives `onEnter`, then `onProcess(dt)`, then `onPhysicsProcess(dt)` at the fixed step

#### Scenario: A non-Node2D subclass is skipped by draw traversal

- **GIVEN** a `Timer` instance attached as a child of the scene root
- **WHEN** the engine performs the draw pass for the frame
- **THEN** no `onDraw` invocation happens on the `Timer`
- **AND** no transform composition is attempted for it

### Requirement: SceneTree owns the live tree

The engine SHALL provide a `com.neoutils.engine.tree.SceneTree` class that owns a live node tree. `SceneTree` MUST NOT extend `Node` and MUST NOT be annotated `@Serializable`. `SceneTree` MUST be constructible as `SceneTree(root: Node)`, where `root` is any concrete `Node` subclass shipped by `:engine` (or a user-defined subclass). `SceneTree` MUST expose:

- `val root: Node` — the root of the live tree.
- `@Volatile var input: Input?` — populated by the runtime (`GameLoop`) at the start of each tick.
- `var size: Vec2` (private setter) and `width: Float` / `height: Float` derived getters — kept current by the host via `resize(width: Float, height: Float)`.
- `val viewport: Rect` — computed: `currentCamera()?.bounds ?: Rect(Vec2.ZERO, size)`.
- `var onResize: ((Float, Float) -> Unit)? = null` — single-listener slot invoked by `resize` when the size changes (no-op when null).
- `val isMutationDeferred: Boolean` and `val isRendering: Boolean` (private setters) — phase flags consulted by `Node.addChild`/`removeChild` to decide between immediate mutation and deferral.
- `fun start()` / `fun stop()` — attach/detach the root to/from the live tree.
- `fun process(dt: Float)` / `fun physicsProcess(dt: Float)` / `fun render(renderer: Renderer)` — frame, fixed, and draw traversals starting from `root`.
- `fun applyPending()` — drains pending child mutations enqueued during the previous traversal (removals before additions, post-order).
- `fun getNodesInGroup(name: String): List<Node>` — pre-order walk from `root`, returns every live node whose `groups` contains `name`.
- `fun currentCamera(): Camera2D?` — pre-order tree-walk from `root` returning the first `Camera2D` with `current = true`.
- `fun screenToWorld(screenPosition: Vec2): Vec2` and `fun worldToScreen(worldPosition: Vec2): Vec2` — delegate to the current `Camera2D` with `size` as surface size; identity fallback when no current camera or degenerate bounds.

`SceneTree.start()` MUST call `root.attachToLiveTree(tree = this)`, which propagates the tree pointer (see "Node caches its SceneTree") recursively to all descendants and invokes `onEnter` on each in pre-order. `SceneTree.stop()` MUST call `root.detachFromLiveTree()`, propagating `onExit` in post-order and clearing the tree pointer. The traversals (`process`/`physicsProcess`/`render`) MUST set `isMutationDeferred = true` before the walk and reset to `false` after, and `render` MUST additionally set `isRendering = true` for its duration.

#### Scenario: SceneTree is not a Node and not @Serializable

- **WHEN** the source file `engine/src/main/kotlin/com/neoutils/engine/tree/SceneTree.kt` is inspected
- **THEN** the declared class `SceneTree` does NOT extend `Node` (directly or transitively)
- **AND** the class is NOT annotated with `@Serializable`

#### Scenario: SceneTree.start attaches the root to the live tree

- **GIVEN** a `SceneTree(root = node)` where `node` is detached and `node.isLive == false`
- **WHEN** code calls `tree.start()`
- **THEN** `node.isLive` becomes `true`
- **AND** `node.onEnter()` has been invoked exactly once

#### Scenario: SceneTree.stop detaches the root from the live tree

- **GIVEN** a `SceneTree(root = node)` after `tree.start()` ran
- **WHEN** code calls `tree.stop()`
- **THEN** `node.isLive` becomes `false`
- **AND** `node.onExit()` has been invoked exactly once

#### Scenario: SceneTree.process traverses the entire live tree from root

- **WHEN** `tree.process(dt)` is called on a `SceneTree` whose `root` contains nodes A (root), B (child of A), and C (child of A)
- **THEN** the engine invokes `onProcess(dt)` on A, B, and C in pre-order

#### Scenario: SceneTree.physicsProcess traverses the entire live tree

- **WHEN** `tree.physicsProcess(dt)` is called on a `SceneTree` whose `root` contains nodes A, B, and C
- **THEN** the engine invokes `onPhysicsProcess(dt)` on each of A, B, C in pre-order

#### Scenario: Render order follows tree pre-order

- **WHEN** `tree.render(renderer)` is called
- **THEN** parents are rendered before their children
- **AND** siblings are rendered in the order they appear in `children`

#### Scenario: SceneTree size reflects host resize

- **GIVEN** a `SceneTree` whose host runtime invokes `tree.resize(800f, 600f)`
- **WHEN** code reads `tree.size`
- **THEN** the result is `Vec2(800f, 600f)`

#### Scenario: SceneTree.onResize listener fires on size change

- **GIVEN** a `SceneTree` with `onResize = { w, h -> capturedSize = Vec2(w, h) }` and a prior size of `Vec2(800f, 600f)`
- **WHEN** code calls `tree.resize(1024f, 768f)`
- **THEN** the listener has been invoked with `(1024f, 768f)`
- **AND** `tree.size` is `Vec2(1024f, 768f)`

#### Scenario: SceneTree.onResize listener does not fire when size is unchanged

- **GIVEN** a `SceneTree` with size `Vec2(800f, 600f)` and a non-null `onResize` listener
- **WHEN** code calls `tree.resize(800f, 600f)`
- **THEN** the listener is NOT invoked

#### Scenario: viewport falls back to scene tree size when no Camera2D is current

- **GIVEN** a `SceneTree` of size `Vec2(800f, 600f)` with no `Camera2D` in the tree (or none with `current = true`)
- **WHEN** code reads `tree.viewport`
- **THEN** the result is `Rect(Vec2.ZERO, Vec2(800f, 600f))`

#### Scenario: viewport reflects current Camera2D bounds

- **GIVEN** a `SceneTree` containing a `Camera2D` with `bounds = Rect(Vec2(100f, 100f), Vec2(400f, 300f))` and `current = true`
- **WHEN** code reads `tree.viewport`
- **THEN** the result is `Rect(Vec2(100f, 100f), Vec2(400f, 300f))`

#### Scenario: getNodesInGroup returns every node with the group

- **GIVEN** a live `SceneTree` with nodes `A`, `B`, `C` where `A.addToGroup("paddles")` and `C.addToGroup("paddles")`
- **WHEN** code calls `tree.getNodesInGroup("paddles")`
- **THEN** the result is `[A, C]` in pre-order
- **AND** the result does not contain `B`

#### Scenario: SceneTree cannot be subclassed to customize setup

- **WHEN** the declaration of `SceneTree` is inspected
- **THEN** the class is declared `class SceneTree` (not `open class`, not `abstract class`)
- **AND** no engine-shipped game module declares a subclass of `SceneTree`

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

The engine SHALL define a `Renderer` interface used by `onDraw` hooks. The interface MUST cover the operations needed by the sample games in this change: clearing the surface, drawing filled and outlined rectangles, drawing filled and outlined circles, drawing line segments, drawing filled polygons via `drawPolygon(points: List<Vec2>, color: Color)`, drawing text, and measuring text. The interface MUST NOT expose types from `androidx.compose.*`, `org.jetbrains.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, or any backend-specific package. The interface MUST be implementable without reflection or service loaders. The `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` operation MUST draw a straight segment between the two points (interpreted under the current transform stack) with the given stroke thickness. The `drawPolygon(points: List<Vec2>, color: Color)` operation MUST fill the polygon described by the vertex list (interpreted under the current transform stack) as a closed path; the implementation MAY assume the polygon is simple (non-self-intersecting) and convex-or-concave-without-holes. The `measureText(text: String, size: Float): Vec2` operation MUST return the bounding box (`Vec2(width, height)`) that `drawText` would produce for the same text and size, allowing callers to align text without backend-specific measurement. The `Color` type used by the renderer MUST be annotated with `@Serializable` (kotlinx.serialization) so it can be embedded as a property value in serialized scene files.

The interface SHALL additionally expose a 2D affine transform stack via two operations:

```kotlin
fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2)
fun popTransform()
```

`pushTransform(translation, rotation, scale)` MUST push a new entry onto an internal LIFO stack representing the composition `translate(translation) ∘ rotate(rotation) ∘ scale(scale)` applied to all subsequent `draw*` calls until the matching `popTransform()`. `rotation` MUST be expressed in radians and applied around the new origin (post-translation). Pushes MUST nest (composition order is parent-then-child: a deeper push composes with the current top). `popTransform()` MUST restore the top to the previous entry and SHALL throw `IllegalStateException` if the stack is empty.

The stack state SHALL start as identity at every backend-defined frame boundary (e.g. when `SkikoRenderer.bind()` runs). Every `pushTransform` issued during a frame MUST be matched by a `popTransform` before the renderer's frame boundary ends; the engine MUST NOT rely on cross-frame stack state.

#### Scenario: Engine module has no UI framework dependency

- **WHEN** the `:engine` module is compiled
- **THEN** its build classpath contains no `androidx.compose.*` artifact
- **AND** its build classpath contains no `org.jetbrains.compose.*` artifact
- **AND** its build classpath contains no `org.jetbrains.skia.*` or `org.jetbrains.skiko.*` artifact

#### Scenario: Renderer is consumed only via the interface

- **WHEN** any class in `:engine` references `Renderer`
- **THEN** it depends only on the `Renderer` interface, not on any concrete backend implementation

#### Scenario: drawLine accepts arbitrary endpoints

- **WHEN** a node calls `renderer.drawLine(Vec2(0f, 0f), Vec2(100f, 100f), thickness = 2f, color = Color.WHITE)`
- **THEN** the backend draws a diagonal stroke between the two points (under the current transform stack) with the requested thickness and color

#### Scenario: drawPolygon fills the polygon described by vertices

- **WHEN** a node calls `renderer.drawPolygon(listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(5f, 10f)), Color.WHITE)`
- **THEN** the backend renders a filled triangle covering those three vertices (under the current transform stack)
- **AND** subsequent calls with different vertex lists produce independent shapes (no state leakage)

#### Scenario: measureText reports the bounding box

- **WHEN** a node calls `renderer.measureText("hello", size = 22f)`
- **THEN** the result is a `Vec2` whose `x` is the rendered width and `y` is the rendered height of `drawText("hello", _, 22f, _)` in the same frame

#### Scenario: Color is serializable

- **WHEN** code serializes `Color(0.5f, 0.25f, 0.125f, 0.75f)` via `kotlinx.serialization` JSON
- **THEN** the resulting JSON document contains the four channel values
- **AND** deserializing yields a `Color` equal (by `equals`) to the original

#### Scenario: pushTransform translates subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2(100f, 50f), rotation = 0f, scale = Vec2(1f, 1f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the rendered rectangle appears at surface position `(100, 50)` with size `(10, 10)`

#### Scenario: pushTransform scales subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2.ZERO, rotation = 0f, scale = Vec2(2f, 2f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the rendered rectangle appears at surface position `(0, 0)` with size `(20, 20)`

#### Scenario: pushTransform rotates subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2.ZERO, rotation = (PI / 2f).toFloat(), scale = Vec2(1f, 1f))` then `renderer.drawLine(from = Vec2(0f, 0f), to = Vec2(10f, 0f), thickness = 1f, color = Color.WHITE)` then `renderer.popTransform()`
- **THEN** the rendered line endpoint that was `(10, 0)` in local space appears at surface position approximately `(0, 10)` within floating-point tolerance

#### Scenario: pushTransform composes translate, rotate, and scale in order

- **WHEN** code calls `renderer.pushTransform(translation = Vec2(50f, 0f), rotation = (PI / 2f).toFloat(), scale = Vec2(2f, 2f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the local origin `(0, 0)` maps to surface position `(50, 0)` (translation only)
- **AND** the local point `(10, 0)` maps to surface position approximately `(50, 20)` (scaled to `(20, 0)`, then rotated 90° around the new origin)

#### Scenario: popTransform restores the previous transform

- **WHEN** code calls `renderer.pushTransform(Vec2(100f, 0f), 0f, Vec2(1f, 1f))`, draws a rect at `(0, 0)`, calls `renderer.popTransform()`, then draws another rect at `(0, 0)`
- **THEN** the first rect appears at surface position `(100, 0)`
- **AND** the second rect appears at surface position `(0, 0)`

#### Scenario: popTransform on empty stack fails fast

- **WHEN** code calls `renderer.popTransform()` without a preceding `pushTransform`
- **THEN** an `IllegalStateException` is raised naming the empty-stack precondition

#### Scenario: Transform stack starts as identity each frame

- **WHEN** a new frame begins on the backend (e.g. `SkikoRenderer.bind(canvas)`)
- **THEN** a `drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, true)` issued before any `pushTransform` renders at surface position `(0, 0)` with size `(10, 10)`

### Requirement: Input SPI

The engine SHALL define an `Input` interface providing read-only access to current input state at tick time. The interface MUST allow querying whether a given key is currently pressed and the current pointer position. The interface MUST allow querying whether a given mouse button is currently pressed (`isMouseDown`) and whether it was pressed during the current tick (`wasMouseClicked`). The interface MUST define a `MouseButton` enum covering at least `Left`, `Right`, and `Middle`. The interface MUST NOT expose backend-specific event types. The interface MAY expose pressed/released edge events as boolean queries valid for the current tick.

To support UI hit-testing, the interface MUST additionally expose:

- `wasMouseClickedRaw(button: MouseButton): Boolean` — returns the same value `wasMouseClicked` would have returned if no UI consumed the click. The raw query SHALL always reflect the bare hardware event.
- `var mouseClickConsumed: Boolean` — a writable flag set by the `SceneTree.hitTestUI(input)` phase when a `Button` (or other UI widget) absorbs the click. The flag SHALL be reset to `false` at the start of every tick (during `beginTick()` or equivalent).
- `wasMouseClicked(button)` SHALL return `false` whenever `mouseClickConsumed` is `true` for the same tick (left button in MVP; future buttons may follow the same pattern).

#### Scenario: Engine module reads input only via the interface

- **WHEN** a node in `:engine` queries input state inside `onUpdate`
- **THEN** it does so through the `Input` interface
- **AND** no backend type leaks into the node's source

#### Scenario: Key state reflects current frame

- **WHEN** the game loop polls input at the start of a tick and the underlying backend reports key `K` as pressed
- **THEN** every `Input.isKeyDown(K)` call within that tick returns `true`

#### Scenario: Mouse button click is observable for exactly one tick

- **WHEN** the user presses the left mouse button between tick `N-1` and tick `N`
- **THEN** `Input.wasMouseClicked(MouseButton.Left)` returns `true` for every call within tick `N` (assuming the click was not consumed by UI)
- **AND** returns `false` from tick `N+1` onward unless a new press occurs

#### Scenario: Mouse button held reads as down across ticks

- **WHEN** the user presses and holds the left mouse button across multiple ticks without releasing
- **THEN** `Input.isMouseDown(MouseButton.Left)` returns `true` for every tick during the hold

#### Scenario: Consumed click is invisible to wasMouseClicked

- **WHEN** the user clicks the left mouse button and `SceneTree.hitTestUI(input)` sets `input.mouseClickConsumed = true` during the same tick
- **THEN** `input.wasMouseClicked(MouseButton.Left)` SHALL return `false` for every subsequent call within that tick
- **AND** `input.wasMouseClickedRaw(MouseButton.Left)` SHALL return `true`

#### Scenario: mouseClickConsumed resets each tick

- **WHEN** a click was consumed during tick `N` (setting `mouseClickConsumed = true`) and no click occurs in tick `N+1`
- **THEN** at the start of tick `N+1`, `input.mouseClickConsumed` SHALL equal `false`

### Requirement: Physics primitives are Godot-style nodes

The engine SHALL provide collision support via a `CollisionObject2D` hierarchy rather than a single `Collider` class. The hierarchy MUST be:

```
CollisionObject2D (abstract, : Node2D)
├── Area2D                                    (trigger; does not block)
└── PhysicsBody2D (abstract)
    ├── StaticBody2D                          (solid, position moved by script)
    ├── CharacterBody2D                       (solid, exposes velocity slot)
    └── RigidBody2D                           (solid, engine-integrated dynamic body)
```

Every concrete subclass (`Area2D`, `StaticBody2D`, `CharacterBody2D`, `RigidBody2D`) MUST be `@Serializable` and instantiable with a public no-args constructor. `CollisionObject2D` MUST expose `@Inspect var disabled: Boolean = false`. `CharacterBody2D` MUST expose `@Inspect var velocity: Vec2 = Vec2.ZERO`. The engine MUST NOT integrate `CharacterBody2D.velocity` automatically — integration is the script's responsibility (Godot-style).

`RigidBody2D` is integrated by the engine (see `rigid-body-2d` capability) and is the third path alongside Static (immovable) and Character (script-moved). The properties, integrator, impulse solver, and conservation diagnostics for `RigidBody2D` are specified in `rigid-body-2d`.

#### Scenario: Each collision class is instantiable with no args

- **WHEN** code evaluates `Area2D()`, `StaticBody2D()`, `CharacterBody2D()`, `RigidBody2D()`
- **THEN** each call returns a valid instance assignable to `CollisionObject2D`

#### Scenario: CharacterBody2D velocity slot exists and is mutable

- **WHEN** code creates `CharacterBody2D()` and sets `body.velocity = Vec2(100f, 0f)`
- **THEN** reading `body.velocity` returns `Vec2(100f, 0f)`
- **AND** the engine does NOT automatically integrate `transform.position` from `velocity` between ticks

#### Scenario: PhysicsBody2D is not directly instantiable

- **WHEN** code attempts to call `PhysicsBody2D()`
- **THEN** the compiler rejects the call (`abstract`)

#### Scenario: RigidBody2D is the third concrete PhysicsBody2D

- **WHEN** code inspects the `:engine` source tree
- **THEN** `RigidBody2D` exists alongside `StaticBody2D` and `CharacterBody2D` under `com.neoutils.engine.physics`
- **AND** `RigidBody2D` extends `PhysicsBody2D` (and transitively `CollisionObject2D`)

### Requirement: CollisionShape2D holds a Shape2D resource

The engine SHALL provide a `CollisionShape2D : Node2D` class with `@Inspect var shape: Shape2D? = null` and `@Inspect var disabled: Boolean = false`. `CollisionShape2D` MUST be `@Serializable` and instantiable with no args. The `Shape2D` type MUST be a `@Serializable sealed class` with at least two concrete subtypes: `RectangleShape2D(@Inspect var size: Vec2 = Vec2(10f, 10f))` and `CircleShape2D(@Inspect var radius: Float = 5f)`. `Shape2D` MUST expose a method `bounds(world: Transform, localOffset: Vec2): Rect` returning the axis-aligned bounding box in world space.

`RectangleShape2D` SHALL be **centered on its local origin**: its extent is `[-size/2, +size/2]` in the local frame, so under a world transform it occupies `[world.position - size/2·scale, world.position + size/2·scale]` (before rotation). This matches `CircleShape2D` (centered on `radius`) and `Node2D.localBounds()` (`Rect(-size/2, size)`), so for any `RectangleShape2D` a `CollisionShape2D`'s inherited `worldBounds()` and its `broadPhaseBounds()` agree. The local origin is the geometric center, NOT a corner.

`CollisionShape2D` is meaningful only as a direct child of a `CollisionObject2D`; placing one elsewhere SHALL NOT crash but SHALL be ignored by `PhysicsSystem`.

#### Scenario: CollisionShape2D defaults

- **WHEN** code evaluates `CollisionShape2D()`
- **THEN** `shape` is `null`, `disabled` is `false`

#### Scenario: RectangleShape2D is centered on its local origin

- **GIVEN** a `RectangleShape2D` with `size = Vec2(10f, 6f)`
- **WHEN** its local extent is evaluated at identity transform
- **THEN** it spans `[-5f, 5f]` on x and `[-3f, 3f]` on y (centered), NOT `[0f, 10f] × [0f, 6f]`

#### Scenario: RectangleShape2D bounds reflect transform scale

- **GIVEN** a `RectangleShape2D` with `size = Vec2(10f, 20f)`
- **WHEN** `bounds(Transform(position = Vec2(50f, 50f), scale = Vec2(2f, 2f), rotation = 0f), Vec2.ZERO)` is computed
- **THEN** the resulting `Rect` has `origin = Vec2(40f, 30f)` and `size = Vec2(20f, 40f)` — the AABB is centered on `world.position`, with half-extents `Vec2(10f, 20f)`

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

The engine SHALL provide a `PhysicsSystem` whose `step(tree: SceneTree, dt: Float)` operation:

1. **Integrates and resolves RigidBody2D motion** (see `rigid-body-2d` capability): applies accumulated forces and gravity to `linearVelocity` / `angularVelocity`, runs the swept TOI loop with bilateral impulse resolution against other `PhysicsBody2D`, then commits `position` and `transform.rotation`. This stage produces no enter/exit signals — only motion.
2. Enumerates every `CollisionObject2D` with `disabled == false` in the live scene tree.
3. Collects each object's active `CollisionShape2D` children (those whose `shape != null` and `disabled == false`).
4. For every unordered pair `(A, B)` of objects (A ≠ B), tests whether **any** pair `(shapeA, shapeB)` overlaps. Overlap MUST be exact for axis-aligned cases (rect-rect AABB, circle-circle distance, rect-circle closest-point) and for rotated rect-rect pairs (SAT); other rotated combinations MAY be approximated by their AABB.
5. Maintains an internal `Set<UnorderedPair<CollisionObject2D>>` of currently overlapping pairs. Pairs new this step → dispatch enter. Pairs gone this step → dispatch exit.
6. Filters out pairs whose endpoints are no longer in the live scene before dispatching (cleanup of detached nodes).

The `step` method also exposes a public mutable `gravity: Vec2` property on `PhysicsSystem`, defaulting to `Vec2.ZERO`, consumed in stage 1.

Order: enter dispatches MUST run after exit dispatches within the same step. Both MUST run after the per-pair overlap test (no interleaving). Integration + impulse resolution (stage 1) MUST run before overlap detection (stages 2-6), so dispatched signals reflect post-resolution positions. The system MUST NOT crash if a hook removes a node from the scene mid-dispatch — mutation deferral applies.

#### Scenario: PhysicsSystem.step accepts dt and runs integration before dispatch

- **WHEN** the source of `PhysicsSystem.kt` is inspected
- **THEN** the public signature is `fun step(tree: SceneTree, dt: Float)`
- **AND** within `step`, the RigidBody2D integration + impulse resolution stage runs before `computeOverlapping` and the enter/exit dispatch

#### Scenario: PhysicsSystem.gravity defaults to zero

- **WHEN** code constructs a new `PhysicsSystem()` (or accesses the tree's `PhysicsSystem`)
- **THEN** `system.gravity` equals `Vec2.ZERO`
- **AND** the property is mutable (`var`)

#### Scenario: Detached nodes are removed from pair set

- **GIVEN** two `CollisionObject2D` A and B that were overlapping last step
- **WHEN** A is detached from the scene (via `parent.removeChild(A)`) before the next step
- **THEN** the next `step(tree, dt)` does NOT invoke `B.onBodyExited(A)` for A
- **AND** the pair (A, B) is no longer tracked

#### Scenario: Multiple shapes per object — overlap is union

- **GIVEN** a `CollisionObject2D` with two `CollisionShape2D` children at different positions
- **AND** another `CollisionObject2D` whose single shape overlaps only the second of the first object's shapes
- **WHEN** a physics step runs
- **THEN** the pair is treated as overlapping
- **AND** exactly one `*Entered` event is dispatched per side (not one per shape pair)

#### Scenario: Step runs after physicsProcess each fixed step

- **WHEN** `GameLoop.tick` runs one physics step (configured in `godot-style-foundation`)
- **THEN** `tree.physicsProcess(dt)` (dispatching `onPhysicsProcess` to every Node) runs before `physics.step(tree, dt)` in that step
- **AND** the engine drains pending mutations before each phase

### Requirement: Rectangle-rectangle overlap is exact under rotation

The pure function `overlap(a: Shape2D, aWorld: Transform, b: Shape2D, bWorld: Transform): Boolean` defined in `com.neoutils.engine.physics` MUST return `true` if and only if the two oriented rectangles described by `(a, aWorld)` and `(b, bWorld)` geometrically intersect in world space, when both `a` and `b` are `RectangleShape2D` and at least one of `aWorld.rotation` or `bWorld.rotation` is non-zero.

When **both** rotations are exactly `0f`, the implementation MAY take a faster axis-aligned path (intersecting `bounds()` AABBs) — that path is equivalent to the rotated test for axis-aligned inputs.

The exact test MUST be implemented via the Separating Axis Theorem on the four candidate axes (two per OBB, perpendicular to their sides). Both rectangles are **centered on their local origin** (see "CollisionShape2D holds a Shape2D resource"), so each OBB's half-extent along its own edge normal is `size/2·scale` (the apothem). `RectangleShape2D.bounds(world, localOffset)` continues to return the axis-aligned envelope of the rotated corners — this requirement does NOT change the `bounds()` contract; it only changes the `overlap()` semantics for the rect-rect rotated case.

`PhysicsSystem` MAY continue to use `bounds()` AABB intersection as a cheap broad-phase rejection step before calling `overlap()`.

#### Scenario: Two rotated rectangles whose AABB envelopes overlap but whose OBBs do not are reported as not overlapping

- **GIVEN** `RectangleShape2D` A with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(0f, 0f), rotation = π/4)`
- **AND** `RectangleShape2D` B with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(15f, 15f), rotation = π/4)`
- **AND** because both squares are centered, along their shared edge normal `(1, 1)/√2` the center separation `~21.21` exceeds the combined apothems `10 + 10 = 20`, so the OBBs are separated
- **AND** the AABB envelopes (each `~28.28 × 28.28`, A spanning `[-14.14, 14.14]²` and B `[0.86, 29.14]²`) still overlap on the rectangle `[0.86, 14.14] × [0.86, 14.14]`
- **WHEN** `overlap(A, aWorld, B, bWorld)` is computed
- **THEN** the result is `false`

#### Scenario: Two rotated rectangles whose OBBs touch are reported as overlapping

- **GIVEN** `RectangleShape2D` A with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(0f, 0f), rotation = π/4)`
- **AND** `RectangleShape2D` B with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(10f, 10f), rotation = π/4)`
- **AND** the rotated rectangles geometrically overlap (center separation `~14.14` is below the combined apothems `20`)
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

### Requirement: PhysicsSystem iterates dispatch until convergence within a step

`PhysicsSystem.step(tree)` MUST repeatedly recompute the set of currently overlapping `CollisionObject2D` pairs and dispatch the `_entered` / `_exited` events that emerge between iterations, **within the same step**, until no new event would be dispatched (the set stabilises) or a fail-safe maximum number of iterations is reached.

The semantics observed by user-level scripts MUST be: for every real begin-of-overlap that exists at the end of the step, exactly one `_entered` event was dispatched on each side during the step; for every real end-of-overlap, exactly one `_exited`. The internal iteration is invisible to scripts and to signal subscribers.

When the fail-safe maximum is reached and the set is still changing, the engine MUST log a warning naming the iteration count and the pair count still in transition. It MUST NOT crash.

Pre-existing invariants from `collision-overhaul` MUST hold per iteration: exits are dispatched before enters; deferred mutation rules continue to apply; detached endpoints are cleaned out of the tracked pair set once at the start of the step.

#### Scenario: Three-body pile-up resolves enter events for cascading overlaps

- **GIVEN** three `StaticBody2D` A, B, C in a live scene, each with a `CollisionShape2D` holding a `RectangleShape2D`
- **AND** initially (A, B) overlap but (B, C) and (A, C) do not
- **AND** `A.onBodyEntered` is overridden to set `B.transform = ...` such that after that mutation, (B, C) overlaps
- **WHEN** `PhysicsSystem.step(tree)` runs once
- **THEN** `A.onBodyEntered(B)` is called once during the step
- **AND** `B.onBodyEntered(C)` is also called during the same step (not on the next step)
- **AND** every `*Entered` callback fired in the step corresponds to a pair that truly overlaps at the moment of dispatch

#### Scenario: Steady-state overlap does not refire entered

- **GIVEN** two `CollisionObject2D` whose shapes overlap and remain overlapping through multiple iterations of the convergence loop within a single step
- **WHEN** `PhysicsSystem.step(tree)` runs
- **THEN** `*Entered` is dispatched at most once per side for that pair across the entire step

#### Scenario: Fail-safe iteration cap logs and exits cleanly

- **GIVEN** a script whose response to `_entered` reintroduces overlap that the next iteration's `_exited` removes, oscillating without converging
- **WHEN** `PhysicsSystem.step(tree)` runs
- **THEN** the engine logs a warning naming `MAX_RESOLUTION_ITERATIONS` and the count of pairs still in transition
- **AND** `step` returns normally (no exception, no infinite loop)

#### Scenario: No-pile-up step costs one iteration

- **GIVEN** a scene whose scripts do not mutate transforms in response to `_entered` / `_exited` (or whose mutations do not produce new overlap transitions)
- **WHEN** `PhysicsSystem.step(tree)` runs
- **THEN** the convergence loop executes exactly one iteration before exiting

### Requirement: Game loop coordination

The engine SHALL provide a `GameLoop` class that, given a `SceneTree`, a `Renderer`, an `Input`, a `PhysicsSystem`, and a `physicsHz: Int` (default `60`), exposes a `tick(dtNanos: Long)` operation. Each `tick` MUST: (1) compute `dtFrame` in seconds; (2) run the UI hit-test phase `tree.hitTestUI(input)` before any other tree traversal; (3) accumulate `dtFrame` into an internal `accumulator` and, while `accumulator >= 1f / physicsHz` and steps `< maxStepsPerFrame` (`= 5`), execute a physics step: drain pending → `tree.physicsProcess(physicsDt)` → drain pending → `physics.step(tree)` → decrement accumulator; (4) drain pending and call `tree.process(dtFrame)`; (5) drain pending and call `tree.render(renderer)`. When the inner loop hits `maxStepsPerFrame` and accumulator is still above `physicsDt`, the engine MUST clamp `accumulator` to `0f` to prevent spiral-of-death. The `GameLoop` itself MUST NOT spawn threads or impose its own pacing; the host runtime supplies the pulse.

The host SHALL call `input.beginTick()` and execute any toggle-key polling before invoking `gameLoop.tick(...)`, so the UI hit-test phase observes a clean, up-to-date input state.

#### Scenario: Tick order is deterministic

- **WHEN** `gameLoop.tick(dtNanos)` is called and `dtNanos >= physicsDtNanos`
- **THEN** `tree.hitTestUI(input)` runs before any other tree phase
- **AND** at least one physics step runs before `tree.process(dtFrame)`
- **AND** `tree.process(dtFrame)` runs before `tree.render(renderer)`

#### Scenario: Physics step uses fixed dt

- **GIVEN** `physicsHz = 60`
- **WHEN** `gameLoop.tick(dtNanos)` triggers `n` physics steps
- **THEN** each `tree.physicsProcess(dt)` call sees `dt == 1f / 60f`
- **AND** each `physics.step(tree)` is paired one-to-one with a `tree.physicsProcess` call

#### Scenario: Process phase uses variable dt

- **WHEN** `gameLoop.tick(dtNanos)` is called with `dtNanos = 16_666_666L`
- **THEN** `tree.process(dt)` runs with `dt ≈ 0.01667`

#### Scenario: Sub-physics-step frames accumulate without running physics

- **GIVEN** `physicsHz = 60` (physics dt ≈ 16.67ms) and an empty accumulator
- **WHEN** `gameLoop.tick(8_333_333L)` is called (8.33ms frame)
- **THEN** no physics step runs in that tick
- **AND** `tree.process(dt)` still runs once with `dt ≈ 0.00833`
- **AND** `tree.hitTestUI(input)` still runs exactly once

#### Scenario: Multiple physics steps run in a long frame

- **GIVEN** `physicsHz = 60` and an empty accumulator
- **WHEN** `gameLoop.tick(50_000_000L)` is called (50ms frame)
- **THEN** between 2 and 3 physics steps execute in that tick (3 if accumulator reaches threshold three times)
- **AND** `tree.process(dt)` still runs exactly once at the end with `dt ≈ 0.050`
- **AND** `tree.hitTestUI(input)` still runs exactly once at the start

#### Scenario: Spiral-of-death clamps accumulator

- **GIVEN** the loop has just executed `maxStepsPerFrame` physics steps
- **AND** `accumulator > physicsDt` still
- **WHEN** the loop exits the inner step loop
- **THEN** `accumulator` is reset to `0f`
- **AND** the next call to `tick` does not catastrophically queue more steps

#### Scenario: Pending mutations drain between phases

- **WHEN** during `tree.physicsProcess(dt)` a node enqueues `addChild(spawn)` and during `physics.step(tree)` another node enqueues `removeChild(victim)`
- **THEN** `spawn.onEnter()` runs before `physics.step` begins
- **AND** `victim.onExit()` runs before subsequent traversals see it
- **AND** `tree.render` sees `spawn` and does not see `victim`

### Requirement: Node2D leaf subclasses default to open

The engine SHALL declare every concrete `Node2D` subclass shipped by `:engine` as `open class` by default, so game code MAY extend any of them without restriction. The same default SHALL apply to concrete non-`Node2D` `Node` subclasses shipped by `:engine` (such as `Timer`). Declaring a shipped leaf as `final` (non-`open`) is permitted only when accompanied by a KDoc comment on the class explaining the invariant that herança quebraria; absent that justification, leaves MUST remain `open`.

#### Scenario: Camera2D is open

- **WHEN** game code declares `class FollowCamera : Camera2D()`
- **THEN** the declaration compiles
- **AND** `FollowCamera` inherits `bounds`, `current`, `aspectMode`, and the world/transform API

#### Scenario: Polygon2D, Circle2D, ColorRect, Line2D are open

- **WHEN** game code declares any of `class X : Polygon2D()`, `class X : Circle2D()`, `class X : ColorRect()`, `class X : Line2D()`
- **THEN** each declaration compiles
- **AND** the subclass inherits the parent's `@Inspect var` properties and `onDraw` behavior

#### Scenario: Timer is open

- **WHEN** game code declares `class IntervalTimer : Timer()`
- **THEN** the declaration compiles
- **AND** `IntervalTimer` inherits `waitTime`, `autostart`, `oneShot`, `processCallback`, and the `timeout: Signal<Unit>` field

### Requirement: Node2D exposes ergonomic local transform accessors

`Node2D` SHALL expose three `var` properties that read from and write to its local `transform`: `position: Vec2`, `rotation: Float`, and `scale: Vec2`. Each property's getter MUST return the corresponding field of `this.transform`. Each property's setter MUST assign a new `Transform` to `this.transform` via the existing `copy(...)` of the immutable `Transform` data class, preserving the other two fields. Because the `transform` setter is the single invalidation point for the world-transform cache (`invalidateWorldTransformRecursive()`), assigning through any of the three new accessors MUST invalidate the cache identically to a direct `transform = ...` assignment. `Transform` and `Vec2` MUST remain immutable value types; the accessors are pure sugar over `transform.copy(...)`.

#### Scenario: position getter mirrors transform.position

- **GIVEN** a `Node2D` with `transform.position = (10, 20)`
- **WHEN** code reads `node.position`
- **THEN** the result equals `Vec2(10, 20)`

#### Scenario: position setter reassigns transform with other fields preserved

- **GIVEN** a `Node2D` with `transform.position = (10, 20)`, `transform.scale = (2, 2)`, `transform.rotation = π`
- **WHEN** code assigns `node.position = Vec2(50, 60)`
- **THEN** `node.transform.position` equals `Vec2(50, 60)`
- **AND** `node.transform.scale` equals `Vec2(2, 2)`
- **AND** `node.transform.rotation` equals `π`

#### Scenario: rotation setter reassigns transform with other fields preserved

- **GIVEN** a `Node2D` with `transform.position = (10, 20)`, `transform.rotation = 0f`
- **WHEN** code assigns `node.rotation = 1.5f`
- **THEN** `node.transform.rotation` equals `1.5f`
- **AND** `node.transform.position` equals `Vec2(10, 20)`

#### Scenario: scale setter reassigns transform with other fields preserved

- **GIVEN** a `Node2D` with `transform.scale = (1, 1)`, `transform.position = (10, 20)`
- **WHEN** code assigns `node.scale = Vec2(3, 4)`
- **THEN** `node.transform.scale` equals `Vec2(3, 4)`
- **AND** `node.transform.position` equals `Vec2(10, 20)`

#### Scenario: Writing through any accessor invalidates world cache like a direct transform assignment

- **GIVEN** a parent `Node2D` with a child `Node2D`, both with cached `world()` already populated
- **WHEN** code assigns `parent.position = Vec2(99, 99)` (or `parent.rotation = ...` or `parent.scale = ...`)
- **THEN** the next `child.world()` reflects the new parent transform, identically to what `parent.transform = parent.transform.copy(position = Vec2(99, 99))` would produce

#### Scenario: Vec2 remains immutable — partial component write through accessor is impossible

- **WHEN** code writes `node.position = Vec2(node.position.x, 50f)`
- **THEN** the assignment compiles and `node.position.y` becomes `50f`
- **AND** attempting `node.position.y = 50f` does NOT compile in Kotlin (Vec2.y is val)
- **AND** the same expression in Python (`self.position.y = 50.0`) raises `AttributeError` at runtime

### Requirement: Transform composition by ancestry

The engine SHALL compose `Transform`s along the chain of `Node2D` ancestors so that `position`, `scale` and `rotation` of a parent affect the world-space transform of its descendants. `Node2D` MUST expose `world(): Transform` that returns the composed transform from the topmost `Node2D` ancestor down to `this`, applying `scale` and `rotation` of each ancestor to the local frame of the next descendant. `Transform` MUST expose a pure `compose(child: Transform): Transform` (or equivalent operator) that, given a parent transform, returns the world transform for a child described in the parent's local frame.

`Node2D` MUST cache the result of `world()` per-node so that consecutive reads without any intervening mutation return the cached value in O(1). The cache MUST be invalidated when (a) the node's own `transform` property is assigned a new value (including assignments via the `position`, `rotation`, `scale` accessor properties), (b) the node is attached to or detached from a parent (reparenting), or (c) any ancestor's `transform` is assigned a new value. Cache invalidation MUST propagate from the mutated node to all `Node2D` descendants, traversing through non-`Node2D` nodes in the chain. The cached value MUST NOT be persisted by `SceneLoader` — it is runtime-only state that begins unset after deserialization and is populated lazily on first read.

The cached value MUST be observably indistinguishable from a fresh computation: any sequence of mutations followed by a read MUST yield the same `Transform` as if `world()` had been computed from scratch. `Transform`, `Vec2` and `compose` MUST remain immutable value types; the cache is the only state added to support caching.

#### Scenario: Translation only composes additively

- **WHEN** a parent `Node2D` has `transform.position = (10, 20)`, no rotation and unit scale, and a child `Node2D` has `transform.position = (3, 4)` and unit transform otherwise
- **THEN** `child.world().position` equals `(13, 24)`
- **AND** `child.world().scale` equals `(1, 1)`
- **AND** `child.world().rotation` equals `0`

#### Scenario: Parent scale scales child position and size

- **WHEN** a parent `Node2D` has `transform.scale = (2, 3)` and the child has `transform.position = (10, 0)` and `transform.scale = (1, 1)`
- **THEN** `child.world().position` equals `(20, 0)`
- **AND** `child.world().scale` equals `(2, 3)`

#### Scenario: Parent rotation rotates child position

- **WHEN** a parent `Node2D` has `transform.rotation = π / 2` (90° counter-clockwise in engine convention) and the child has `transform.position = (10, 0)` and identity otherwise
- **THEN** `child.world().position` is approximately `(0, 10)` within floating-point tolerance
- **AND** `child.world().rotation` equals `π / 2`

#### Scenario: Composition is associative across three levels

- **WHEN** three nested `Node2D`s `a → b → c` each carry non-identity translation, scale and rotation
- **THEN** `c.world()` equals the composition `a.transform ∘ b.transform ∘ c.transform` as defined by `Transform.compose`

#### Scenario: Repeated reads without mutation return cached value

- **WHEN** `n.world()` is called twice in a row without any intervening mutation to `n.transform`, `n.parent`, or any ancestor's `transform`
- **THEN** both calls return a `Transform` equal in `position`, `scale` and `rotation`
- **AND** the second call MUST NOT walk the ancestor chain or compose any transforms

#### Scenario: Assigning a new local transform invalidates self and descendants

- **WHEN** `parent.transform = parent.transform.copy(position = (50, 50))` is executed after both `parent.world()` and `child.world()` have been called
- **THEN** the next `parent.world()` reflects the new position
- **AND** the next `child.world().position` reflects the parent's new position composed with the child's local transform

#### Scenario: Invalidation propagates through non-Node2D intermediates

- **WHEN** a hierarchy `grandparent: Node2D → middle: Node (not Node2D) → grandchild: Node2D` exists, both `grandparent.world()` and `grandchild.world()` have been called, and `grandparent.transform` is then reassigned
- **THEN** the next `grandchild.world()` reflects the grandparent's new transform composed with the grandchild's local transform

#### Scenario: Reparenting invalidates the moved subtree

- **WHEN** a node `child` whose `world()` has already been computed is removed from its current parent and added to a different parent with a different transform
- **THEN** the next `child.world()` reflects the composition with the new parent's transform, not the old one

#### Scenario: Loading a scene yields nodes whose cache is unpopulated

- **WHEN** a scene is loaded via `SceneLoader.load(...)` and `world()` is called on a freshly loaded node
- **THEN** the returned `Transform` is computed from the live ancestor chain (not from any persisted cache)
- **AND** the produced JSON from `SceneLoader.save(scene)` does not contain any field for the cached world transform

### Requirement: Safe mutation during scene traversal

The engine SHALL allow `addChild` and `removeChild` to be called from within `onProcess`, `onPhysicsProcess`, `onCollide` and other traversal-driven hooks without corrupting the children list or raising `ConcurrentModificationException`. When invoked while a `SceneTree` traversal is in progress (`tree.isMutationDeferred == true`), mutations MUST be enqueued onto pending queues on the affected `Node` and applied at deterministic drain points within the same tick. When invoked outside traversal, mutations MUST take effect immediately, preserving the current contract. Pending removals MUST be applied before pending additions to prevent re-adding a node scheduled for removal in the same drain. `onDraw` MUST NOT be used to mutate the scene tree; the engine MAY log a warning if such mutation is detected, but SHALL NOT crash.

#### Scenario: addChild during onProcess does not crash

- **WHEN** a `Node`'s `onProcess(dt)` calls `parent.addChild(other)` and the tree traversal is in progress
- **THEN** no exception is raised
- **AND** the children list visible to the remainder of the current process phase MAY or MAY NOT contain `other`, but is consistent (no partial state)

#### Scenario: addChild during onPhysicsProcess is visible to physics in the same step

- **WHEN** a `Node`'s `onPhysicsProcess(dt)` enqueues a new child `other` via `addChild`
- **THEN** `other` is part of the live tree during `physics.step(tree)` of the same physics step
- **AND** `other.onEnter()` has been invoked exactly once before `physics.step` begins

#### Scenario: removeChild during a collision hook does not crash

- **WHEN** a `CollisionObject2D`'s `onBodyEntered(other)` or `onAreaEntered(other)` calls `parent.removeChild(self)` and the physics step is in progress
- **THEN** no exception is raised
- **AND** the object continues to receive any remaining enter/exit callbacks already in flight for the current dispatch pass without crash

#### Scenario: Mutation outside traversal applies immediately

- **WHEN** game code calls `parent.addChild(node)` from outside any lifecycle hook
- **THEN** `node` appears in `parent.children` immediately
- **AND** if the parent is live, `onEnter()` is invoked synchronously before `addChild` returns

#### Scenario: Pending removes drain before pending adds

- **WHEN** within a single traversal, code calls `parent.removeChild(a)` then `parent.addChild(a)`
- **THEN** at the next drain point, `a` ends up still attached (remove then add nets to add)
- **AND** the lifecycle hooks are coherent: `a` receives `onExit` then `onEnter` exactly once each, in that order

### Requirement: Node caches its SceneTree

Every `Node` SHALL expose a property `tree: SceneTree?` that returns its owning `SceneTree` in constant time when the node is part of a live tree. When the node is detached, the property SHALL return `null`. The engine SHALL back this property with a cached field whose setter is `internal` (only the engine's `attachToLiveTree`/`detachFromLiveTree` code mutates it). The cached value MUST be set on the node BEFORE its `onEnter()` runs, and cleared AFTER its `onExit()` returns, so lifecycle hooks may rely on it. The field MUST be annotated `@Transient` (kotlinx.serialization) so the cache never persists in serialized scene files.

The engine SHALL NOT expose a `rootScene()` helper on `Node` after this change. Access to the owning tree goes through the `tree` property directly. Code that previously used `node.rootScene()?.width` SHALL read `node.tree?.width`. The previous `rootScene()` signature SHALL be removed from `Node`.

`Node.isLive` SHALL be derived from `tree != null` rather than tracked as an independent field, so the two states cannot diverge.

#### Scenario: tree returns the owning SceneTree in constant time

- **WHEN** any node `n` is part of a live tree
- **THEN** `n.tree` returns the `SceneTree` instance owning `n`
- **AND** the call performs no parent-chain walk

#### Scenario: tree returns null for detached node

- **WHEN** a node has no parent and is not attached to any `SceneTree`
- **THEN** `n.tree` returns `null`

#### Scenario: onEnter observes a non-null tree

- **WHEN** the engine invokes `onEnter()` on a node being attached to a live tree
- **THEN** `tree` inside that `onEnter()` returns the owning `SceneTree`

#### Scenario: onExit observes a non-null tree

- **WHEN** the engine invokes `onExit()` on a node being detached from a live tree
- **THEN** `tree` inside that `onExit()` still returns the owning `SceneTree`

#### Scenario: Detached node does not retain tree reference

- **WHEN** a node is removed from a live tree and the detach completes
- **THEN** subsequent reads of `tree` on the removed node return `null`

#### Scenario: isLive equals tree != null

- **WHEN** any node `n` is in any state (detached, attached, mid-attach, mid-detach is not observable)
- **THEN** `n.isLive` equals `n.tree != null` for every observable moment

#### Scenario: rootScene helper does not exist on Node

- **WHEN** the source file `engine/src/main/kotlin/com/neoutils/engine/scene/Node.kt` is inspected
- **THEN** no function named `rootScene` is declared on `Node`
- **AND** no property of type `Scene?` is declared on `Node`

#### Scenario: Node.tree is @Transient and not serialized

- **GIVEN** a serializable `Node` subclass with `n.tree` populated by a live `SceneTree`
- **WHEN** code calls `SceneLoader.save(...)` over the tree
- **THEN** the produced JSON contains no field named `tree` on any node

### Requirement: Scene rendering decoupled from DX surface

The `SceneTree.render(renderer: Renderer)` traversal SHALL NOT depend on or consult any symbol from `com.neoutils.engine.dx.*`. Visualization of debug artifacts (collider bounds, FPS overlay, etc.) SHALL be produced by nodes registered via `tree.debug.register(...)` and rendered as part of the standard scene graph walk (world pass for `WorldDebugWidget`, UI pass for `ScreenDebugWidget`). The `:engine.tree` package MUST compile without `:engine.dx` being on the classpath — only the `:engine.debug` package may be referenced by `SceneTree` for the purpose of instantiating `DebugRegistry` and auto-inserting `DebugLayer`.

#### Scenario: SceneTree.kt has no import from engine.dx

- **WHEN** the source file `engine/src/main/kotlin/com/neoutils/engine/tree/SceneTree.kt` is parsed
- **THEN** it contains no import statement beginning with `com.neoutils.engine.dx`

#### Scenario: SceneTree.kt may import from engine.debug

- **WHEN** the source file `engine/src/main/kotlin/com/neoutils/engine/tree/SceneTree.kt` is parsed
- **THEN** it MAY contain imports from `com.neoutils.engine.debug.*` (for `DebugRegistry`, `DebugLayer`)
- **AND** these imports SHALL be the only debug-related imports it carries

#### Scenario: SceneTree.render does not draw collider bounds

- **WHEN** `tree.render(renderer)` is invoked
- **THEN** no `Renderer.drawRect(_, _, filled = false)` call is issued by `SceneTree` itself for the purpose of debug visualization
- **AND** the only debug draw calls during the traversal originate from `DebugWidget.drawDebug` overrides reached via the standard scene-graph walk

### Requirement: Engine module has zero UI framework dependency

The `:engine` Gradle module SHALL declare no dependency on any UI or render framework artifact, directly or transitively. The prohibited list includes at minimum: `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, `org.lwjgl.*`, AWT/Swing types (`java.awt.*`, `javax.swing.*`) beyond what the standard library guarantees, and any future render backend (e.g. Vulkan bindings, WebGPU). This invariant SHALL be enforced by the module's `build.gradle.kts` and verified during code review. Backend-specific types only appear in their respective backend modules: `:engine-skiko` (Skia/Skiko, default backend) and `:engine-lwjgl` (NanoVG/GLFW/OpenGL via LWJGL, second backend).

#### Scenario: Adding a Compose dependency to :engine is rejected

- **WHEN** a contributor adds `androidx.compose.foundation` or any `org.jetbrains.compose.*` artifact to `:engine`'s dependencies
- **THEN** code review blocks the change
- **AND** the contributor is directed to use the `Renderer`/`Input`/`GameHost` SPI in a separate backend module

#### Scenario: Adding a Skiko dependency to :engine is rejected

- **WHEN** a contributor adds `org.jetbrains.skiko:skiko-awt` or any `org.jetbrains.skia.*` artifact to `:engine`'s dependencies
- **THEN** code review blocks the change
- **AND** the contributor is directed to use the `Renderer`/`Input`/`GameHost` SPI; Skiko-specific code lives in `:engine-skiko`

#### Scenario: Adding an LWJGL dependency to :engine is rejected

- **WHEN** a contributor adds `org.lwjgl:lwjgl` or any `org.lwjgl.*` artifact to `:engine`'s dependencies
- **THEN** code review blocks the change
- **AND** the contributor is directed to use the `Renderer`/`Input`/`GameHost` SPI; LWJGL-specific code lives in `:engine-lwjgl`

### Requirement: GameHost SPI

The engine SHALL define a `GameHost` interface that represents the host of execution of a game: it owns a window/surface, drives the per-frame pulse, wires `Input` events from the platform into the engine, and runs the game loop until the host is closed. The interface MUST expose a single `run(tree: SceneTree, config: GameConfig)` operation. The operation MUST be blocking: it returns only after the host's window/surface has been closed by the user or by code. The interface MUST NOT expose backend-specific types in its method signatures; both inputs (`SceneTree`, `GameConfig`) and the return type live in `:engine`. The interface MUST be implementable without reflection or service loaders.

`GameHost` implementations SHALL NOT issue any `renderer.draw*` calls outside of `tree.render(renderer)`. All visual output, including debug overlays, SHALL be produced by `SceneTree.render` walking the scene graph. `GameHost` implementations SHALL NOT poll input keys for the purpose of toggling debug visualization — the engine performs that polling internally via `DebugToggleNode`. The host's only debug-related responsibility SHALL be to set `tree.debugHudKey = config.debugHudKey` during startup so the engine knows which key opens the HUD; the host SHALL NOT read or write any other field of `tree.debug` on a per-frame basis.

#### Scenario: Engine module reads no backend type to declare GameHost

- **WHEN** the `:engine` module is compiled
- **THEN** `GameHost` is declared with parameters whose types come only from `:engine`
- **AND** no import in the file declaring `GameHost` begins with `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, `javax.swing.*`, or `org.lwjgl.*`

#### Scenario: run blocks until the host is closed

- **WHEN** code calls `host.run(tree, config)` and the host opens a window
- **THEN** the call does not return while the window remains open
- **AND** the call returns after the window is closed by the user or by code

#### Scenario: SkikoHost implements GameHost

- **WHEN** code in `:games:pong` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is assignable to `GameHost`
- **AND** when code in `:games:tictactoe` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is also assignable to `GameHost`
- **AND** every shipped game in the project (Pong, Demos, Hello-World, Tic Tac Toe) obtains its default `GameHost` implementation from `:engine-skiko`

#### Scenario: LwjglHost implements GameHost

- **WHEN** code in `:games:demos` (alternate entrypoint `MainLwjgl.kt`) instantiates `LwjglHost()` from `:engine-lwjgl`
- **THEN** the result is assignable to `GameHost`
- **AND** `:engine-lwjgl` is recognized as the second active render backend after `:engine-skiko`

#### Scenario: GameHost does not draw outside SceneTree.render

- **WHEN** the source of any `GameHost` implementation is inspected for direct uses of `renderer.drawText`, `renderer.drawRect`, `renderer.drawLine`, `renderer.drawCircle`, or `renderer.drawPolygon`
- **THEN** every such call SHALL occur transitively inside a `tree.render(renderer)` invocation, not in the host's frame body before or after the render call.

#### Scenario: GameHost does not poll debug toggle keys

- **WHEN** the source of any `GameHost` implementation is grep'd for `input.wasKeyPressed(`
- **THEN** the only matches SHALL be unrelated to debug visualization (e.g. user-defined game keys passed through via `Input`)
- **AND** no host file SHALL reference `FpsCounter`, `MomentumOverlay`, `tree.debug.show*`, or `tree.debug.current*`

#### Scenario: GameHost sets debugHudKey once during startup

- **WHEN** a `GameHost` implementation is observed across a full `run(tree, config)` invocation
- **THEN** exactly one assignment of `tree.debugHudKey` from `config.debugHudKey` SHALL be observed
- **AND** the assignment SHALL precede the first call to `loop.tick(...)`

### Requirement: GameConfig host configuration

The engine SHALL provide a `data class GameConfig` carrying the configuration a `GameHost` needs to open its window and behave consistently across backends. `GameConfig` MUST expose at minimum a `title: String`, a `width: Int`, a `height: Int`, a `debugHudKey: Key`, and a `physicsHz: Int`. All fields MUST have sensible defaults so that `GameConfig()` is a valid call site. The default value for `debugHudKey` MUST be `Key.F1` so that any host implementation honors a single conventional affordance for opening the debug HUD without per-game wiring. `GameConfig` MUST be a `data class` so equality, `copy()`, and component destructuring are available.

`GameConfig` SHALL NOT carry per-widget toggle keys (such as `toggleFpsKey`, `toggleCollidersKey`, `toggleMomentumOverlayKey`). Individual widgets are toggled via the HUD's clickable rows, not via per-flag keys.

#### Scenario: Default constructor is valid

- **WHEN** code calls `GameConfig()`
- **THEN** the result is a valid `GameConfig`
- **AND** `title` is a non-empty string
- **AND** `width` and `height` are positive integers
- **AND** `debugHudKey` is `Key.F1`

#### Scenario: debugHudKey is configurable

- **WHEN** code calls `GameConfig(debugHudKey = Key.GRAVE)`
- **THEN** the result reports `Key.GRAVE` for `debugHudKey`
- **AND** any `GameHost.run(tree, this)` SHALL set `tree.debugHudKey = Key.GRAVE` during startup

#### Scenario: Legacy toggle key fields do not exist

- **WHEN** code attempts to call `GameConfig(toggleFpsKey = Key.F1)` or read `GameConfig().toggleCollidersKey`
- **THEN** the call SHALL fail to compile because no such fields exist on `GameConfig`

### Requirement: SceneTree exposes debugHudKey

`SceneTree` SHALL expose a mutable `var debugHudKey: Key` property (default `Key.F1`). `GameHost` implementations SHALL set this property from `GameConfig.debugHudKey` during startup, before the first `loop.tick(...)`. The engine's internal `DebugToggleNode` (inside the auto-inserted `DebugLayer`) SHALL read this property each tick when checking for the HUD toggle.

#### Scenario: Default debugHudKey is F1

- **WHEN** a `SceneTree` is constructed
- **THEN** `tree.debugHudKey` SHALL equal `Key.F1`

#### Scenario: Host writes debugHudKey from config

- **GIVEN** a `GameConfig(debugHudKey = Key.GRAVE)`
- **WHEN** a `GameHost.run(tree, config)` invocation begins
- **THEN** `tree.debugHudKey` SHALL equal `Key.GRAVE` by the time the first frame is processed

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

Every concrete `Node` subclass shipped by `:engine` (i.e. `Node2D`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `Label`, `Camera2D`, `Area2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`) SHALL provide a public no-args primary constructor with sensible defaults. All initial configuration that previously lived in constructor parameters SHALL be exposed as `var` properties on the class instead. Each such property SHALL be annotated either with `@Inspect` (when it is part of the serialized contract) or with `@Transient` (when it is internal runtime state). The class itself SHALL be annotated with `@Serializable` (kotlinx.serialization). DX-oriented factory functions MAY exist on the side to reduce verbosity, but they MUST NOT be the only path to instantiate the class.

#### Scenario: Concrete Node classes can be instantiated with no arguments

- **WHEN** code evaluates `Node2D()`, `ColorRect()`, `Circle2D()`, `Line2D()`, `Polygon2D()`, `Label()`, `Camera2D()`, `Area2D()`, `StaticBody2D()`, `CharacterBody2D()`, `CollisionShape2D()`
- **THEN** each call returns a valid instance with default property values

#### Scenario: Configuration is set via mutable properties

- **WHEN** code instantiates `ColorRect()` and then sets `colorRect.size = Vec2(20f, 20f)` and `colorRect.color = Color.WHITE`
- **THEN** subsequent reads of those properties reflect the assignments

#### Scenario: Every var property on a serializable Node is annotated

- **WHEN** any class in `:engine` that extends `Node` and is annotated `@Serializable` is inspected
- **THEN** every `var` property on the class is annotated either with `@Inspect` or with `@Transient`

### Requirement: Camera2D viewport carrier

The engine SHALL provide a `Camera2D : Node2D` class with `@Inspect var bounds: Rect` (the visible-world region in world coordinates), `@Inspect var current: Boolean = false` (whether this is the active camera), and `@Inspect var aspectMode: AspectMode = AspectMode.FIT` (how the world bounds map onto the surface when the aspect ratios differ). `AspectMode` SHALL be an enum with members `FIT`, `FILL`, and `STRETCH`. Setting `current = true` while live MUST cause `SceneTree.viewport` to reflect this camera's `bounds` until either `current` is set back to `false` or another `Camera2D` becomes current later in the tree. When multiple `Camera2D` nodes have `current = true`, the engine MUST pick the first one in pre-order traversal. `Camera2D` MUST be `@Serializable` and instantiable via no-args constructor, like every other `Node` shipped by `:engine`.

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

- **GIVEN** a `SceneTree` with two `Camera2D` nodes both having `current = true`, one at root-child position 0 and another deeper at position 2
- **WHEN** code reads `tree.viewport`
- **THEN** the bounds returned are from the camera at root-child position 0

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

The engine SHALL provide concrete `Node2D` subclasses dedicated to common 2D visuals, each `@Serializable` with no-args public primary constructor and configuration via `@Inspect var` properties. Each visual primitive's `onDraw` SHALL operate in **local space** — the node's own coordinate frame, with origin at `(0, 0)` and no manual application of `world().position`, `world().rotation`, or `world().scale`. The world transform is supplied by `SceneTree.render` via `Renderer.pushTransform(node.transform.position, node.transform.rotation, node.transform.scale)` around each `Node2D`'s `onDraw` call (see "SceneTree.render applies Node2D local transform per draw").

- `ColorRect`: `size: Vec2`, `color: Color`. `onDraw` issues a filled `drawRect(Rect(Vec2.ZERO, size), color, filled = true)`.
- `Circle2D`: `radius: Float`, `color: Color`. `onDraw` issues a filled `drawCircle(Vec2.ZERO, radius, color, filled = true)`.
- `Line2D`: `points: List<Vec2>` (local-space), `thickness: Float`, `color: Color`. `onDraw` issues consecutive `drawLine(from = points[i-1], to = points[i], thickness, color)` calls between adjacent points, with NO world translation applied — ancestor rotation/scale now reach the line via the transform stack.
- `Polygon2D`: `points: List<Vec2>` (local-space), `color: Color`. `onDraw` issues `drawPolygon(points, color)` directly, with NO world translation applied.
- `Label`: `text: String`, `size: Float`, `color: Color`. `onDraw` issues `drawText(text, Vec2.ZERO, size, color)`. Alignment computations via `measureText` remain relative to the node's local origin.

All of these nodes SHALL inherit ancestor rotation and scale visually via the transform stack maintained by `SceneTree.render`. The previous limitation that "ancestor `rotation` is not applied visually" is REMOVED by this change.

#### Scenario: ColorRect renders a filled rectangle at local origin

- **WHEN** a `ColorRect` with `size = Vec2(40f, 20f)` and `color = Color.WHITE` is in a live scene at world position `Vec2(10f, 10f)`
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(10f, 10f), 0f, Vec2(1f, 1f))` around the node's draw
- **AND** the `onDraw` issues `drawRect(Rect(Vec2.ZERO, Vec2(40f, 20f)), Color.WHITE, filled = true)`

#### Scenario: Circle2D renders a filled circle at local origin

- **WHEN** a `Circle2D` with `radius = 10f` and `color = Color.WHITE` is in a live scene at world position `Vec2(50f, 50f)`
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(50f, 50f), 0f, Vec2(1f, 1f))` around the node's draw
- **AND** the `onDraw` issues `drawCircle(center = Vec2.ZERO, radius = 10f, color = Color.WHITE, filled = true)`

#### Scenario: Line2D renders consecutive segments in local space

- **WHEN** a `Line2D` with `points = listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(10f, 10f))` and `thickness = 2f` is in a live scene at world position `Vec2(0f, 0f)`
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(0f, 0f), 0f, Vec2(1f, 1f))` around the node's draw
- **AND** the `onDraw` issues exactly two `drawLine` calls — one from `Vec2(0f, 0f)` to `Vec2(10f, 0f)`, and one from `Vec2(10f, 0f)` to `Vec2(10f, 10f)`

#### Scenario: Polygon2D renders a filled polygon in local space

- **WHEN** a `Polygon2D` with `points = listOf(Vec2(0f, 0f), Vec2(20f, 0f), Vec2(10f, 20f))` and `color = Color.WHITE` is in a live scene at world position `Vec2(100f, 100f)`
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(100f, 100f), 0f, Vec2(1f, 1f))` around the node's draw
- **AND** the `onDraw` issues `drawPolygon(points = listOf(Vec2(0f, 0f), Vec2(20f, 0f), Vec2(10f, 20f)), Color.WHITE)`

#### Scenario: Polygon2D inherits ancestor rotation visually

- **WHEN** a `Polygon2D` with `points = listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(0f, 10f))` is in a live scene at local position `Vec2(0f, 0f)` with local rotation `(PI / 2f).toFloat()` (no parent rotation)
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(0f, 0f), (PI / 2f).toFloat(), Vec2(1f, 1f))` around the node's draw
- **AND** the local vertex `Vec2(10f, 0f)` therefore appears on the surface at approximately `Vec2(0f, 10f)` within floating-point tolerance (i.e. the polygon visually rotates 90°)

#### Scenario: Label renders text at local origin

- **WHEN** a `Label` with `text = "score"`, `size = 24f`, `color = Color.WHITE` is in a live scene at world position `Vec2(0f, 0f)`
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(0f, 0f), 0f, Vec2(1f, 1f))` around the node's draw
- **AND** the `onDraw` issues `drawText("score", Vec2(0f, 0f), 24f, Color.WHITE)` exactly once

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

When a `Camera2D` has `current = true` and is attached to a live `SceneTree`, the engine MUST make its `bounds` discoverable via `SceneTree.viewport`. The engine MUST resolve "the current camera" on read via pre-order tree-walk of the live tree from `root` picking the first `Camera2D` with `current = true`; the engine MAY cache this lookup but MUST invalidate on tree mutation or on `current` property changes.

`SceneTree.render(renderer)` SHALL consult the current camera at the start of the render traversal. When a current `Camera2D` exists with `bounds.size.x > 0f` and `bounds.size.y > 0f`, `SceneTree.render` MUST compute the view transform from `(camera.bounds, tree.size, camera.aspectMode)` and call `renderer.pushTransform(translation, rotation = 0f, scale)` BEFORE issuing any `_draw` walk, then call `renderer.popTransform()` AFTER the walk finishes (including via the `finally` of any traversal try/finally). When no current camera exists or its bounds are degenerate, `SceneTree.render` MUST NOT push the view transform — the `_draw` walk runs against identity at the view level (preserving the pre-change behavior of `pixels = world` for camera-less trees). The per-`Node2D` transform pushes defined by "SceneTree.render applies Node2D local transform per draw" SHALL still occur regardless of whether a current camera is present.

`SceneTree` SHALL additionally expose two coordinate-conversion conveniences:

```kotlin
fun screenToWorld(screenPosition: Vec2): Vec2
fun worldToScreen(worldPosition: Vec2): Vec2
```

Both methods MUST delegate to the current `Camera2D`'s `screenToWorld` / `worldToScreen`, passing `tree.size` as the surface size argument. When no current camera exists (or its bounds are degenerate), both methods MUST return the input unchanged (identity fallback) — the same condition under which `SceneTree.render` skips its view push, so nodes can read input pointer coordinates uniformly regardless of whether the tree has a camera.

#### Scenario: Toggling current updates viewport

- **GIVEN** a live `SceneTree` with one `Camera2D` whose `current = false`, and `tree.viewport` returns `Rect(Vec2.ZERO, tree.size)`
- **WHEN** code sets `camera.current = true`
- **THEN** `tree.viewport` next read returns `camera.bounds`

#### Scenario: SceneTree.render with current camera pushes a view transform

- **GIVEN** a live `SceneTree` of `size = Vec2(1280f, 900f)` containing a `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))`, `current = true`, `aspectMode = AspectMode.FIT`, and a single `ColorRect` of `size = Vec2(800f, 600f)` at world `Vec2(0f, 0f)`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer` implementation
- **THEN** the first call observed is `pushTransform(...)` mapping `bounds` onto the surface via FIT (with `rotation = 0f`)
- **AND** the last call observed is `popTransform()` (closing the view push)

#### Scenario: SceneTree.render without a current camera does not push a view transform

- **GIVEN** a live `SceneTree` with no `Camera2D` (or a `Camera2D` with `current = false`) and a single `ColorRect` at the root
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer` implementation
- **THEN** no view-level `pushTransform`/`popTransform` pair is observed at the traversal boundary
- **AND** the per-`Node2D` `pushTransform`/`popTransform` pair around the `ColorRect`'s draw IS observed

#### Scenario: SceneTree.render with degenerate camera bounds falls back to identity

- **GIVEN** a live `SceneTree` with a current `Camera2D` whose `bounds.size` has a zero or negative component
- **WHEN** `tree.render(renderer)` runs
- **THEN** no view-level `pushTransform`/`popTransform` pair is observed at the traversal boundary

#### Scenario: SceneTree.screenToWorld delegates to current camera

- **GIVEN** a live `SceneTree` with `size = Vec2(1280f, 900f)` and a current `Camera2D` whose `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `aspectMode = AspectMode.FIT`
- **WHEN** code calls `tree.screenToWorld(Vec2(640f, 450f))` (the surface center)
- **THEN** the result equals `Vec2(400f, 300f)` (the world center inside `bounds`)
- **AND** `tree.worldToScreen(Vec2(400f, 300f))` round-trips back to `Vec2(640f, 450f)`

#### Scenario: SceneTree.screenToWorld identity without current camera

- **GIVEN** a live `SceneTree` with no current `Camera2D` and `size = Vec2(800f, 600f)`
- **WHEN** code calls `tree.screenToWorld(Vec2(123f, 456f))` and `tree.worldToScreen(Vec2(123f, 456f))`
- **THEN** both calls return `Vec2(123f, 456f)` unchanged

### Requirement: SceneTree.render applies Node2D local transform per draw

`SceneTree.render(renderer)` SHALL execute exactly two passes per frame, in order:

1. **World pass** — collect the current `Camera2D` (if any), push the corresponding view transform via `renderer.pushTransform`, walk the tree DFS from `root` while skipping any `CanvasLayer` subtree entirely, and for each visited `Node2D` wrap its `onDraw` call within a matched `Renderer.pushTransform` / `Renderer.popTransform` pair derived from the node's **local** `Transform`. Pop the view transform at the end.
2. **UI pass** — collect every `CanvasLayer` reachable from `root` in DFS pre-order, sort them by `(layer ascending, dfs-discovery-order ascending)`, and for each `CanvasLayer` in that order: walk DFS into its subtree starting from identity transform, wrapping each `Node2D` descendant's `onDraw` in the same per-node push/pop pair.

Nodes that are NOT `Node2D` (e.g. `Timer`, the abstract `Node` base) SHALL NOT trigger any push/pop — they only forward to descendants. `CanvasLayer` itself, being a `Node` (not `Node2D`), SHALL NOT push a transform; it only establishes the identity baseline for the UI pass and its `transform`-less position in the tree. The push and pop MUST nest correctly around recursion into children; the implementation MUST use `try`/`finally` so a thrown exception inside `onDraw` or in a descendant still pops the stack.

#### Scenario: SceneTree.render pushes and pops local transform around each Node2D's draw

- **GIVEN** a live `SceneTree` whose `root` is a `ColorRect` named `R` with `transform.position = Vec2(50f, 50f)`, no children, no `CanvasLayer`, and no current camera
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** the recorded sequence contains exactly one `pushTransform(translation = Vec2(50f, 50f), rotation = 0f, scale = Vec2(1f, 1f))` and exactly one matching `popTransform()` enclosing R's draw call
- **AND** no other `pushTransform`/`popTransform` calls are observed for the frame (modulo the auto-inserted `DebugOverlayLayer`'s UI pass, which issues no draws when all `tree.debug.*` flags are false)

#### Scenario: Nested Node2D pushes compose via the transform stack

- **GIVEN** a live `SceneTree` whose `root` is a `Node2D` parent `P` with `transform.position = Vec2(100f, 0f)`, and `P` has one child `C` (a `ColorRect`) with `transform.position = Vec2(0f, 50f)`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** during the world pass the recorded sequence is: `pushTransform(Vec2(100f, 0f), 0f, Vec2(1f, 1f))`, `P.onDraw`, `pushTransform(Vec2(0f, 50f), 0f, Vec2(1f, 1f))`, `C.onDraw` issuing its `drawRect(...)`, `popTransform()`, `popTransform()`
- **AND** under that composed stack, C's `drawRect(Rect(Vec2.ZERO, ...))` appears on the surface at world position `Vec2(100f, 50f)`

#### Scenario: Non-Node2D nodes do not push a transform

- **GIVEN** a live `SceneTree` whose `root` is a `Node2D` `R`, and `R` has a child `T` which is a `Timer` (a non-`Node2D` `Node`)
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** exactly one `pushTransform`/`popTransform` pair appears (enclosing R's draw and subtree)
- **AND** no `pushTransform` is issued for `T`

#### Scenario: Per-Node2D push happens inside the camera view push during the world pass

- **GIVEN** a live `SceneTree` of `size = Vec2(800f, 600f)` containing a current `Camera2D` with valid bounds, and a single `ColorRect` `R` at world position `Vec2(0f, 0f)`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** the world pass recorded sequence is: `pushTransform(view, ...)` (camera), `pushTransform(Vec2(0f, 0f), 0f, Vec2(1f, 1f))` (R), `R.onDraw`, `popTransform()` (R), `popTransform()` (camera)

#### Scenario: CanvasLayer subtree is skipped during the world pass

- **GIVEN** a live `SceneTree` whose `root` is a `Node2D` `R` containing a child `CanvasLayer` `L`, which in turn contains a `Panel` `P`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** during the world pass the recording contains pushes/pops/draws for `R` but none for `P`
- **AND** during the UI pass the recording contains the push/pop/draw for `P` under identity (no camera view transform)

#### Scenario: CanvasLayer ordering respects layer then DFS discovery order

- **GIVEN** a live `SceneTree` containing three `CanvasLayer`s in DFS-discovery order `L1 (layer=0)`, `L2 (layer=10)`, `L3 (layer=0)`, each with a `Panel` child
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** during the UI pass the panels are drawn in order `L1.Panel`, `L3.Panel`, `L2.Panel` (layer 0 first, with DFS as tie-break; layer 10 last)

#### Scenario: Pop occurs even when onDraw throws

- **GIVEN** a `Node2D` subclass whose `onDraw` throws `RuntimeException` deterministically
- **WHEN** `tree.render(renderer)` runs and the exception propagates
- **THEN** the recording `Renderer` observes a matching `popTransform()` for every `pushTransform()` issued before the exception
- **AND** the renderer's transform stack is empty at the end of the frame

### Requirement: Roadmap includes godot-style-foundation and game-snake

`CLAUDE.md` MUST list `godot-style-foundation` as a roadmap entry with status `Active`, and MUST list `game-snake` as a roadmap entry with status `Planned`. The `game-snake` description MUST mention that Snake is the validator for the foundation refactor (fixed-step, signals, Camera2D bounds, visual primitives without collision dependency).

#### Scenario: Roadmap table includes both entries

- **WHEN** `CLAUDE.md` is opened after this change is created
- **THEN** the roadmap table contains a row for `godot-style-foundation` with status `Active`
- **AND** the roadmap table contains a row for `game-snake` with status `Planned`
- **AND** the `game-snake` row summarizes that it validates the godot-style foundation without depending on the collision overhaul

### Requirement: TextMeasurer SPI for off-frame font metrics

`:engine` SHALL declare a `TextMeasurer` interface with `fun measureText(text: String, size: Float): Vec2`, returning the width and height a string would occupy when drawn at the given font size. `TextMeasurer` SHALL live in `:engine` (Kotlin pure) and MUST NOT expose any render/UI framework type, preserving the engine purity invariant. It is distinct from `Renderer.measureText` in that it is reachable **outside** a render frame.

`SceneTree` SHALL expose a nullable `textMeasurer: TextMeasurer?` field, defaulting to `null` and set by the host at startup. Engine code that needs text metrics outside a draw pass (e.g. `Label.localBounds()`) SHALL read `tree?.textMeasurer`.

#### Scenario: TextMeasurer measures off-frame

- **WHEN** a `TextMeasurer` is set on a `SceneTree` and `measureText("Hi", 12f)` is called outside any render frame
- **THEN** it SHALL return a non-zero `Vec2` matching what `drawText("Hi", _, 12f, _)` would rasterize at the same size

#### Scenario: SceneTree defaults to no measurer

- **WHEN** a `SceneTree` is constructed without a host wiring a measurer
- **THEN** `tree.textMeasurer` SHALL be `null`

#### Scenario: TextMeasurer leaks no render type

- **WHEN** the `:engine` module is compiled
- **THEN** `TextMeasurer` SHALL reference only `:engine` math types (`Vec2`) and no Skiko/LWJGL/AWT/Compose type

### Requirement: Transform inverse point projection

`Transform` SHALL provide `applyInverse(p: Vec2): Vec2`, the exact inverse of
`apply`, mapping a point expressed in the parent frame back into this
transform's local frame: `rotate(p - position, -rotation)` divided
component-wise by `scale`. This lets consumers bring a world-space point into
a node's local frame for oriented hit-testing.

#### Scenario: Inverse of apply round-trips

- **WHEN** `applyInverse(apply(p))` is evaluated for any transform whose scale
  components are non-zero
- **THEN** it returns `p` within float tolerance

#### Scenario: Maps a parent-frame point into the local frame

- **WHEN** `applyInverse(p)` is called
- **THEN** it returns `rotate(p - position, -rotation)` divided component-wise
  by `scale`

### Requirement: Scene pick hit-testing

The engine SHALL run a scene-pick hit-test step in `GameLoop.tick`,
immediately after UI hit-testing and before gameplay processing, gated on the
scene picker being enabled, so an active picker can claim the pointer click
before gameplay reads it.

#### Scenario: Pick hit-test runs after UI and before gameplay process

- **WHEN** `GameLoop.tick` runs a frame
- **THEN** `SceneTree.hitTestPick(input)` is invoked after `hitTestUI` and
  before `tree.process(dt)`

#### Scenario: Disabled picker is a no-op

- **WHEN** the scene picker is disabled
- **THEN** `hitTestPick` performs no tree walk, does not change the selection,
  and does not touch `Input.mouseClickConsumed`

#### Scenario: Active picker claims the click

- **WHEN** the scene picker is enabled and a left click occurs that the UI did
  not already consume
- **THEN** `hitTestPick` resolves the selection and sets
  `Input.mouseClickConsumed` so gameplay does not also see the click

### Requirement: Pointer drag consumption

`Input` SHALL expose a per-tick drag-consumption signal, mirroring
`mouseClickConsumed`: it SHALL be reset to not-consumed at the start of each
tick (at the same pipeline point as the click signal) and set when a debug
panel captures a drag. Gameplay drag consumers SHALL be able to read it to
avoid acting on a pointer drag that the debug UI already owns.

#### Scenario: Drag signal is reset each tick

- **WHEN** a new tick begins
- **THEN** the drag-consumption signal reads not-consumed until something sets it

#### Scenario: Captured drag is observable as consumed

- **WHEN** a debug panel is being dragged during a tick
- **THEN** the drag-consumption signal reads consumed for that tick

### Requirement: Node supports raising a child to the top of the sibling order

`Node` SHALL provide `raiseChildToTop(child: Node)` that moves an existing
direct child to the end of the parent's children list — the top of the paint and
DFS order among its siblings — without changing `child.parent` nor firing
lifecycle hooks (`onEnter`/`onExit`). If `child` is not a direct child of the
node, the call SHALL be a no-op. The reorder SHALL preserve the relative order of
all other children. The method SHALL respect the same mutation-during-traversal
contract as `addChild`/`removeChild`: when invoked while a `SceneTree` traversal
is in progress (`tree.isMutationDeferred == true`) the reorder MUST be deferred to
a drain point within the same tick rather than mutating the list under iteration;
when invoked outside traversal it MUST take effect immediately. `onDraw` MUST NOT
be used to reorder children.

#### Scenario: Raising a child moves it to the end of the list

- **GIVEN** a parent `Node` with children `[a, b, c]`
- **WHEN** code calls `parent.raiseChildToTop(a)` outside any traversal
- **THEN** `parent.children` is `[b, c, a]`
- **AND** `a.parent` is unchanged and no lifecycle hook fired

#### Scenario: Raising a non-child is a no-op

- **WHEN** code calls `parent.raiseChildToTop(x)` where `x` is not a direct child of `parent`
- **THEN** `parent.children` is unchanged

#### Scenario: Raise during traversal does not corrupt the list

- **WHEN** a `Node`'s `onProcess(dt)` calls `parent.raiseChildToTop(sibling)` while the tree traversal is in progress
- **THEN** no exception is raised
- **AND** the children list is consistent (no partial state), with the reorder visible no later than the next drain point of the same tick

### Requirement: Renderer clip stack

The `Renderer` SPI SHALL expose a LIFO rectangular clip stack as the natural pair of the existing transform stack:

```
fun pushClip(rect: Rect)
fun popClip()
```

`pushClip(rect)` MUST push a clip region onto an internal LIFO stack; `rect` MUST be interpreted under the current transform stack (the same composition that `draw*` calls see). Every subsequent `draw*` call MUST be restricted to the intersection of all clip rects currently on the stack — a deeper `pushClip` MUST intersect with (never widen) the current clip. `popClip()` MUST restore the clip to the state before the matching `pushClip` and SHALL throw `IllegalStateException` if the clip stack is empty.

The clip stack SHALL start empty (no clip) at every backend-defined frame boundary, exactly like the transform stack. Every `pushClip` issued during a frame MUST be matched by a `popClip` before the frame boundary ends. Clip and transform pushes/pops MUST nest correctly when interleaved (e.g. `pushClip` → `pushTransform` → `popTransform` → `popClip`), since backends MAY share a single native save/restore stack for both. The interface MUST NOT expose backend-specific types.

#### Scenario: pushClip restricts subsequent draws to the rect

- **WHEN** code calls `renderer.pushClip(Rect(Vec2(0f, 0f), Vec2(100f, 50f)))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(200f, 200f)), Color.WHITE, filled = true)` then `renderer.popClip()`
- **THEN** only the portion of the rect within `(0,0)..(100,50)` is rasterized
- **AND** pixels outside the clip rect are untouched

#### Scenario: Nested clips intersect

- **WHEN** code pushes a clip of `(0,0)..(100,100)`, then pushes a clip of `(50,50)..(200,200)`, then draws a large rect
- **THEN** only the intersection `(50,50)..(100,100)` is rasterized

#### Scenario: Clip composes with the current transform

- **WHEN** code calls `renderer.pushTransform(Vec2(100f, 0f), 0f, Vec2(1f, 1f))` then `renderer.pushClip(Rect(Vec2(0f, 0f), Vec2(10f, 10f)))` then draws then `renderer.popClip()` then `renderer.popTransform()`
- **THEN** the clip is positioned under the active transform (covering surface `(100,0)..(110,10)`)

#### Scenario: Clip and transform pushes interleave without corruption

- **WHEN** code issues `pushClip(A)` → `pushTransform(T)` → draw → `popTransform()` → `popClip()`
- **THEN** after `popClip()` both the clip and the transform are restored to their pre-push state
- **AND** a subsequent draw is unaffected by `A` or `T`

#### Scenario: popClip on an empty stack fails fast

- **WHEN** code calls `renderer.popClip()` without a preceding `pushClip`
- **THEN** the call throws `IllegalStateException`

#### Scenario: Clip stack resets at the frame boundary

- **WHEN** a new frame begins on the backend (e.g. `SkikoRenderer.bind(canvas)`)
- **THEN** a draw issued before any `pushClip` is not restricted by any clip from a prior frame

### Requirement: Input scroll-wheel access

The `Input` SPI SHALL expose mouse-wheel state for the current tick:

- `val scrollDelta: Vec2` — the wheel delta accumulated during the current tick, where positive `y` MEANS scrolling down (toward later content) and positive `x` MEANS scrolling right. It SHALL read `Vec2.ZERO` on any tick with no wheel motion, and SHALL be reset at the start of every tick (during `beginTick()` or equivalent), exactly like the per-tick click state.
- `var scrollConsumed: Boolean` — a writable flag set by the `SceneTree.hitTestUI(input)` phase (or its scroll sibling) when a debug panel absorbs the wheel for the current tick. It SHALL be reset to `false` at the start of every tick. It MAY default to a no-op (always reads `false`, writes ignored) so an `Input` that never participates in scroll consumption needs no extra storage, mirroring `mouseDragConsumed`.

The interface MUST NOT expose backend-specific wheel event types.

#### Scenario: Wheel motion is reported for exactly one tick

- **WHEN** the user rolls the wheel down between tick `N-1` and tick `N`
- **THEN** `input.scrollDelta.y` is positive for every call within tick `N`
- **AND** `input.scrollDelta` reads `Vec2.ZERO` from tick `N+1` onward unless more wheel motion occurs

#### Scenario: No wheel motion reads as zero

- **WHEN** a tick passes with no wheel input
- **THEN** `input.scrollDelta` equals `Vec2.ZERO`

#### Scenario: scrollConsumed resets each tick

- **WHEN** the wheel was consumed during tick `N` (setting `scrollConsumed = true`) and no wheel motion occurs in tick `N+1`
- **THEN** at the start of tick `N+1`, `input.scrollConsumed` equals `false`

