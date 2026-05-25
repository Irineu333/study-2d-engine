## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Scene as root container

**Reason**: The `Scene : Node()` design conflated two responsibilities (being a Node and owning the live tree). Replaced by `SceneTree` (non-Node, non-`@Serializable`) — see ADDED "SceneTree owns the live tree". The root of a `SceneTree` is now any `Node` subclass, decoupled from the tree owner.

**Migration**: Replace `Scene` instantiations with `SceneTree(root = someNode)`. Replace `Scene` subclasses used for setup (e.g. `TicTacToeScene : Scene()`) with a plain `Node` subclass that populates the tree in `onEnter()`. Replace `scene.process(dt)`/`scene.physicsProcess(dt)`/`scene.render(renderer)` calls with `tree.process(...)` etc. The serialized scene file's `root.type` field — previously `com.neoutils.engine.scene.Scene` — must be migrated to whatever concrete `Node` subtype is appropriate (`com.neoutils.engine.scene.Node` is the safe default).

### Requirement: Scene reference cached on Node

**Reason**: Superseded by ADDED "Node caches its SceneTree". The new property is `Node.tree: SceneTree?` (cached field), not `Node.rootScene(): Scene?` (helper function). The cached field semantics (set before `onEnter`, cleared after `onExit`) are preserved; the type and access shape change.

**Migration**: Replace all `node.rootScene()` calls with `node.tree`. Casts of the form `rootScene() as? MySceneSubclass` become `tree?.root as? MyRootNodeSubclass` if the consumer still needs the root node specifically; otherwise the consumer reads tree state (`tree?.width`, `tree?.viewport`, etc.) directly.

## MODIFIED Requirements

### Requirement: Scene rendering decoupled from DX surface

The `SceneTree.render(renderer: Renderer)` traversal SHALL NOT depend on or consult any symbol from `com.neoutils.engine.dx.*`. Visualization of debug artifacts (collider bounds, FPS overlay, etc.) SHALL be the responsibility of the integrating runtime (e.g. `:engine-compose`), not of the core tree owner. The `:engine.tree` package MUST compile without `:engine.dx` being on the classpath as far as `SceneTree.render` is concerned, even if other parts of `:engine` continue to expose the `Debug` surface.

#### Scenario: SceneTree.kt has no import from engine.dx

- **WHEN** the source file `engine/src/main/kotlin/com/neoutils/engine/tree/SceneTree.kt` is parsed
- **THEN** it contains no import statement beginning with `com.neoutils.engine.dx`

#### Scenario: SceneTree.render does not draw collider bounds

- **WHEN** `tree.render(renderer)` is invoked
- **THEN** no `Renderer.drawRect(_, _, filled = false)` call is issued by `SceneTree` itself for the purpose of debug visualization
- **AND** the only draw calls during the traversal originate from `Node.onDraw` overrides on user nodes

### Requirement: Game loop coordination

The engine SHALL provide a `GameLoop` class that, given a `SceneTree`, a `Renderer`, an `Input`, a `PhysicsSystem`, and a `physicsHz: Int` (default `60`), exposes a `tick(dtNanos: Long)` operation. Each `tick` MUST: (1) compute `dtFrame` in seconds; (2) accumulate `dtFrame` into an internal `accumulator` and, while `accumulator >= 1f / physicsHz` and steps `< maxStepsPerFrame` (`= 5`), execute a physics step: drain pending → `tree.physicsProcess(physicsDt)` → drain pending → `physics.step(tree)` → decrement accumulator; (3) drain pending and call `tree.process(dtFrame)`; (4) drain pending and call `tree.render(renderer)`. When the inner loop hits `maxStepsPerFrame` and accumulator is still above `physicsDt`, the engine MUST clamp `accumulator` to `0f` to prevent spiral-of-death. The `GameLoop` itself MUST NOT spawn threads or impose its own pacing; the host runtime supplies the pulse.

#### Scenario: Tick order is deterministic

- **WHEN** `gameLoop.tick(dtNanos)` is called and `dtNanos >= physicsDtNanos`
- **THEN** at least one physics step runs before `tree.process(dtFrame)`
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

#### Scenario: Multiple physics steps run in a long frame

- **GIVEN** `physicsHz = 60` and an empty accumulator
- **WHEN** `gameLoop.tick(50_000_000L)` is called (50ms frame)
- **THEN** between 2 and 3 physics steps execute in that tick
- **AND** `tree.process(dt)` still runs exactly once at the end with `dt ≈ 0.050`

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

#### Scenario: removeChild during onCollide does not crash

- **WHEN** a `Collider`'s `onCollide(other)` calls `parent.removeChild(self)` and the physics step is in progress
- **THEN** no exception is raised
- **AND** the collider continues to receive any remaining `onCollide` callbacks already in flight for the current pair iteration without crash

#### Scenario: Mutation outside traversal applies immediately

- **WHEN** game code calls `parent.addChild(node)` from outside any lifecycle hook
- **THEN** `node` appears in `parent.children` immediately
- **AND** if the parent is live, `onEnter()` is invoked synchronously before `addChild` returns

#### Scenario: Pending removes drain before pending adds

- **WHEN** within a single traversal, code calls `parent.removeChild(a)` then `parent.addChild(a)`
- **THEN** at the next drain point, `a` ends up still attached (remove then add nets to add)
- **AND** the lifecycle hooks are coherent: `a` receives `onExit` then `onEnter` exactly once each, in that order

### Requirement: Physics step exposes a naive O(N²) broad phase

The engine SHALL provide a `PhysicsSystem` that, on each `step(tree: SceneTree)` call, enumerates all `Collider` nodes reachable from `tree.root` and tests every pair for intersection using axis-aligned bounding boxes. For each intersecting pair, it MUST invoke `onCollide` on both colliders exactly once per tick. The current implementation MAY use a naive O(N²) algorithm; this MUST be documented as a known evolution point.

#### Scenario: Each pair is tested exactly once per tick

- **WHEN** `physics.step(tree)` runs with N active colliders
- **THEN** at most `N * (N - 1) / 2` intersection tests are performed in that tick

#### Scenario: Non-overlapping colliders never receive onCollide

- **WHEN** `physics.step(tree)` runs and colliders A and B do not intersect
- **THEN** A does not receive `onCollide(B)` for that tick
- **AND** B does not receive `onCollide(A)` for that tick

### Requirement: GameHost SPI

The engine SHALL define a `GameHost` interface that represents the host of execution of a game: it owns a window/surface, drives the per-frame pulse, wires `Input` events from the platform into the engine, and runs the game loop until the host is closed. The interface MUST expose a single `run(tree: SceneTree, config: GameConfig)` operation. The operation MUST be blocking: it returns only after the host's window/surface has been closed by the user or by code. The interface MUST NOT expose backend-specific types in its method signatures; both inputs (`SceneTree`, `GameConfig`) and the return type live in `:engine`. The interface MUST be implementable without reflection or service loaders.

#### Scenario: Engine module reads no backend type to declare GameHost

- **WHEN** the `:engine` module is compiled
- **THEN** `GameHost` is declared with parameters whose types come only from `:engine`
- **AND** no import in the file declaring `GameHost` begins with `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, or `javax.swing.*`

#### Scenario: run blocks until the host is closed

- **WHEN** code calls `host.run(tree, config)` and the host opens a window
- **THEN** the call does not return while the window remains open
- **AND** the call returns after the window is closed by the user or by code

#### Scenario: Compose and Skiko hosts implement GameHost

- **WHEN** code in `:games:tictactoe` instantiates `ComposeHost()` from `:engine-compose`
- **THEN** the result is assignable to `GameHost`
- **AND** when code in `:games:pong` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is assignable to `GameHost`

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

### Requirement: Camera2D registers as the scene's current camera

When a `Camera2D` has `current = true` and is attached to a live `SceneTree`, the engine MUST make its `bounds` discoverable via `SceneTree.viewport`. The engine MUST resolve "the current camera" on read via pre-order tree-walk of the live tree from `root` picking the first `Camera2D` with `current = true`; the engine MAY cache this lookup but MUST invalidate on tree mutation or on `current` property changes.

`SceneTree.render(renderer)` SHALL consult the current camera at the start of the render traversal. When a current `Camera2D` exists with `bounds.size.x > 0f` and `bounds.size.y > 0f`, `SceneTree.render` MUST compute the view transform from `(camera.bounds, tree.size, camera.aspectMode)` and call `renderer.pushTransform(translation, scale)` BEFORE issuing any `_draw` walk, then call `renderer.popTransform()` AFTER the walk finishes (including via the `finally` of any traversal try/finally). When no current camera exists or its bounds are degenerate, `SceneTree.render` MUST NOT push any transform — the `_draw` walk runs against the identity transform (preserving the pre-change behavior of `pixels = world` for camera-less trees).

`SceneTree` SHALL additionally expose two coordinate-conversion conveniences:

```kotlin
fun screenToWorld(screenPosition: Vec2): Vec2
fun worldToScreen(worldPosition: Vec2): Vec2
```

Both methods MUST delegate to the current `Camera2D`'s `screenToWorld` / `worldToScreen`, passing `tree.size` as the surface size argument. When no current camera exists (or its bounds are degenerate), both methods MUST return the input unchanged (identity fallback) — the same condition under which `SceneTree.render` skips its push, so nodes can read input pointer coordinates uniformly regardless of whether the tree has a camera.

#### Scenario: Toggling current updates viewport

- **GIVEN** a live `SceneTree` with one `Camera2D` whose `current = false`, and `tree.viewport` returns `Rect(Vec2.ZERO, tree.size)`
- **WHEN** code sets `camera.current = true`
- **THEN** `tree.viewport` next read returns `camera.bounds`

#### Scenario: SceneTree.render with current camera pushes a transform

- **GIVEN** a live `SceneTree` of `size = Vec2(1280f, 900f)` containing a `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))`, `current = true`, `aspectMode = AspectMode.FIT`, and a single `ColorRect` of `size = Vec2(800f, 600f)` at world `Vec2(0f, 0f)`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer` implementation
- **THEN** the first call observed is `pushTransform(...)` mapping `bounds` onto the surface via FIT
- **AND** the `ColorRect`'s `drawRect` call uses world coordinates `Rect(Vec2(0f, 0f), Vec2(800f, 600f))`
- **AND** the last call observed is `popTransform()`

#### Scenario: SceneTree.render without a current camera does not push a transform

- **GIVEN** a live `SceneTree` with no `Camera2D` (or a `Camera2D` with `current = false`)
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer` implementation
- **THEN** no `pushTransform` or `popTransform` call is observed during the traversal
- **AND** the `_draw` calls reach the renderer unchanged (identity transform)

#### Scenario: SceneTree.render with degenerate camera bounds falls back to identity

- **GIVEN** a live `SceneTree` with a current `Camera2D` whose `bounds.size` has a zero or negative component
- **WHEN** `tree.render(renderer)` runs
- **THEN** no `pushTransform` or `popTransform` call is observed
- **AND** the `_draw` calls reach the renderer unchanged

#### Scenario: SceneTree.screenToWorld delegates to current camera

- **GIVEN** a live `SceneTree` with `size = Vec2(1280f, 900f)` and a current `Camera2D` whose `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `aspectMode = AspectMode.FIT`
- **WHEN** code calls `tree.screenToWorld(Vec2(640f, 450f))` (the surface center)
- **THEN** the result equals `Vec2(400f, 300f)` (the world center inside `bounds`)
- **AND** `tree.worldToScreen(Vec2(400f, 300f))` round-trips back to `Vec2(640f, 450f)`

#### Scenario: SceneTree.screenToWorld identity without current camera

- **GIVEN** a live `SceneTree` with no current `Camera2D` and `size = Vec2(800f, 600f)`
- **WHEN** code calls `tree.screenToWorld(Vec2(123f, 456f))` and `tree.worldToScreen(Vec2(123f, 456f))`
- **THEN** both calls return `Vec2(123f, 456f)` unchanged
