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

The engine SHALL define a `Renderer` interface used by `onRender` hooks. The interface MUST cover the operations needed by the sample games in this change: clearing the surface, drawing filled and outlined rectangles, drawing filled and outlined circles, drawing line segments, drawing text, and measuring text. The interface MUST NOT expose types from `androidx.compose.*` or any backend-specific package. The interface MUST be implementable without reflection or service loaders. The `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` operation MUST draw a straight segment between the two world-space points with the given stroke thickness. The `measureText(text: String, size: Float): Vec2` operation MUST return the bounding box (`Vec2(width, height)`) that `drawText` would produce for the same text and size, allowing callers to align text without backend-specific measurement. The `Color` type used by the renderer MUST be annotated with `@Serializable` (kotlinx.serialization) so it can be embedded as a property value in serialized scene files.

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

#### Scenario: Color is serializable

- **WHEN** code serializes `Color(0.5f, 0.25f, 0.125f, 0.75f)` via `kotlinx.serialization` JSON
- **THEN** the resulting JSON document contains the four channel values
- **AND** deserializing yields a `Color` equal (by `equals`) to the original

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

Every concrete `Node` subclass shipped by `:engine` (i.e. `Node2D`, `Shape`, `Text`, `BoxCollider`) SHALL provide a public no-args primary constructor with sensible defaults. All initial configuration that previously lived in constructor parameters SHALL be exposed as `var` properties on the class instead. Each such property SHALL be annotated either with `@Inspect` (when it is part of the serialized contract) or with `@Transient` (when it is internal runtime state). The class itself SHALL be annotated with `@Serializable` (kotlinx.serialization). DX-oriented factory functions (e.g. `fun shape(kind: Kind, ...)`) MAY exist on the side to reduce verbosity, but they MUST NOT be the only path to instantiate the class.

#### Scenario: Concrete Node2D classes can be instantiated with no arguments

- **WHEN** code evaluates `Shape()`, `Text()`, `BoxCollider()`, `Node2D()`
- **THEN** each call returns a valid instance with default property values

#### Scenario: Configuration is set via mutable properties

- **WHEN** code instantiates `Shape()` and then sets `shape.kind = Shape.Kind.Circle` and `shape.size = Vec2(20f, 20f)`
- **THEN** subsequent reads of `shape.kind` and `shape.size` reflect those values

#### Scenario: Every var property on a serializable Node is annotated

- **WHEN** any class in `:engine` that extends `Node` and is annotated `@Serializable` is inspected
- **THEN** every `var` property on the class is annotated either with `@Inspect` or with `@Transient`
