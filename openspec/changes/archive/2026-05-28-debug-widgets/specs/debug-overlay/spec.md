## ADDED Requirements

### Requirement: DebugWidget abstraction

The engine SHALL expose a `DebugWidget` abstract type in `com.neoutils.engine.debug` representing a single, self-contained debug visualization. Every `DebugWidget` SHALL carry:

- `val title: String` — short label shown in the debug HUD row that toggles the widget.
- `var enabled: Boolean` — initial value `false` unless the widget overrides it; flipping to `false` SHALL cause `drawDebug` to emit no draw calls.
- `abstract fun drawDebug(renderer: Renderer)` — only invoked when `enabled` is `true`.

`DebugWidget` itself SHALL NOT be a `Node`. Two concrete base subclasses SHALL be shipped:

- `abstract class ScreenDebugWidget : Node()` — lives in the screen-space `CanvasLayer` (no view transform applied); `onDraw(renderer)` calls `drawDebug(renderer)` only when `enabled`.
- `abstract class WorldDebugWidget : Node2D()` — lives in the world-space container directly under `tree.root` (participates in the world pass; view transform from the current `Camera2D` is applied by `SceneTree.render`); `onDraw(renderer)` calls `drawDebug(renderer)` only when `enabled`.

A subclass author SHALL choose the appropriate base for the gizmo they are drawing — world-space gizmos (collider outlines, axes, normals, debug rays) extend `WorldDebugWidget`; screen-space HUD-like widgets (text counters, sparklines, log overlays) extend `ScreenDebugWidget`. The engine SHALL NOT provide a single flag-on-`DebugWidget` to switch spaces at runtime.

#### Scenario: ScreenDebugWidget draw is gated by enabled

- **GIVEN** a `ScreenDebugWidget` subclass `MyWidget` whose `drawDebug` records every draw call
- **WHEN** `MyWidget` is added to the screen container with `enabled = false` and a frame is rendered
- **THEN** zero recorded draw calls SHALL be observed

#### Scenario: WorldDebugWidget inherits view transform from world pass

- **GIVEN** a `Camera2D` with non-identity `computeViewTransform(tree.size)` is current
- **AND** a `WorldDebugWidget` is registered and `enabled = true`
- **WHEN** a frame is rendered
- **THEN** the widget's `drawDebug` SHALL receive a `Renderer` whose top-of-stack already reflects the camera view transform
- **AND** the widget SHALL NOT itself call `renderer.pushTransform(view)` to align with world coordinates

### Requirement: SceneTree exposes a DebugRegistry

`SceneTree` SHALL expose `val debug: DebugRegistry` instantiated alongside the tree. `DebugRegistry` SHALL provide:

- `fun register(widget: DebugWidget)` — routes by subtype: `WorldDebugWidget` is added to the world container; `ScreenDebugWidget` is added to the screen container; both happen as live `addChild` operations and SHALL appear in the registry's `widgets` list.
- `fun unregister(widget: DebugWidget)` — removes the widget from its container and from the list.
- `val widgets: List<DebugWidget>` — read-only listing of currently registered widgets in registration order.
- `inline fun <reified T : DebugWidget> find(): T?` — first widget of the requested concrete type, or `null`.
- Convenience fields for the three built-ins: `val fps: FpsWidget`, `val colliders: ColliderWidget`, `val momentum: MomentumWidget`, `val hud: DebugHud`. These fields point at the engine-owned instances and exist solely as ergonomic shortcuts to flip `enabled`.

`DebugRegistry` SHALL NOT be a `Node`, SHALL NOT be `@Serializable`, and SHALL NOT persist across `SceneTree` lifetimes — pure runtime state. Each `SceneTree` instance SHALL own its own `DebugRegistry` (no static or singleton sharing across trees).

#### Scenario: Built-ins are accessible via convenience fields

- **WHEN** a `SceneTree` is constructed and `start()` is called
- **THEN** `tree.debug.fps`, `tree.debug.colliders`, `tree.debug.momentum`, and `tree.debug.hud` SHALL all be non-null
- **AND** `tree.debug.widgets` SHALL contain at least these four instances

#### Scenario: register routes by subtype

- **GIVEN** a `WorldDebugWidget` instance `axes` and a `ScreenDebugWidget` instance `hud2`
- **WHEN** `tree.debug.register(axes)` and `tree.debug.register(hud2)` are called
- **THEN** `axes.parent` SHALL be the world container child of `DebugLayer`
- **AND** `hud2.parent` SHALL be the screen container child of `DebugLayer`
- **AND** `tree.debug.widgets` SHALL include both, in registration order

#### Scenario: find returns instance by type

- **GIVEN** a custom widget `AxesWidget : WorldDebugWidget` registered exactly once
- **WHEN** `tree.debug.find<AxesWidget>()` is called
- **THEN** the registered instance SHALL be returned

#### Scenario: Two SceneTrees do not share registry state

- **GIVEN** two distinct `SceneTree` instances `treeA` and `treeB`
- **WHEN** `treeA.debug.momentum.enabled = true` is set
- **THEN** `treeB.debug.momentum.enabled` SHALL remain `false`
- **AND** `treeA.debug.momentum` and `treeB.debug.momentum` SHALL be distinct instances

### Requirement: Engine auto-inserts DebugLayer with two sub-containers

The engine SHALL auto-insert a `DebugLayer` (a `Node`) as a child of `SceneTree.root` during `SceneTree.start()`, after the root's own `onEnter` has fired. The `DebugLayer` SHALL have a stable name `"__debug"` and SHALL contain exactly two child containers:

- `WorldDebugContainer` (a `Node2D` directly under `DebugLayer`) — hosts `WorldDebugWidget` instances. Participates in the world pass of `SceneTree.render`, receiving the active `Camera2D` view transform.
- `ScreenDebugCanvas` (a `CanvasLayer` with `layer = Int.MAX_VALUE - 1`) — hosts `ScreenDebugWidget` instances. Painted in the UI pass on top of any game UI.

The engine SHALL register the four built-in widgets — `FpsWidget`, `ColliderWidget`, `MomentumWidget`, `DebugHud` — during the auto-insertion. The engine SHALL additionally insert an internal `DebugToggleNode` inside `ScreenDebugCanvas` that polls input each tick (see "DebugHud opens and closes via debugHudKey").

Re-inserting on a re-attached tree (stop → start) SHALL be idempotent — the engine SHALL skip the addition when a child named `"__debug"` is already present on root.

#### Scenario: DebugLayer is present in every started tree

- **WHEN** `SceneTree(root).start()` has been called on any bundle or programmatic root
- **THEN** `tree.root.findChild("__debug")` SHALL return a `DebugLayer` instance
- **AND** that `DebugLayer` SHALL contain exactly one `WorldDebugContainer` child and one `ScreenDebugCanvas` child

#### Scenario: Auto-insert is idempotent across re-start

- **WHEN** a `SceneTree` is started, stopped, and started again on the same root
- **THEN** root SHALL contain exactly one child named `"__debug"`

#### Scenario: ColliderWidget is hosted in world container, not screen

- **WHEN** the engine has finished auto-inserting `DebugLayer`
- **THEN** `tree.debug.colliders.parent` SHALL be the `WorldDebugContainer` instance
- **AND** `tree.debug.fps.parent`, `tree.debug.momentum.parent`, and `tree.debug.hud.parent` SHALL all be the `ScreenDebugCanvas` instance

### Requirement: ColliderWidget draws world collider AABBs without manual transform

`ColliderWidget` SHALL extend `WorldDebugWidget` and SHALL, when `enabled = true`, iterate `collectActiveCollisionShapes(tree)` and call `renderer.drawRect(shape.worldBounds(), color, filled = false)` for each entry. Colors SHALL be: green-ish (`Color(0f, 1f, 0f, 0.8f)`) for `Area2D` owners, red-ish (`Color(1f, 0.3f, 0.3f, 0.8f)`) for `PhysicsBody2D` owners. `ColliderWidget` SHALL NOT call `renderer.pushTransform` or `renderer.popTransform` — the active `Camera2D` view transform is applied by the world pass.

#### Scenario: ColliderWidget calls drawRect once per active CollisionShape2D

- **GIVEN** a tree with one `Area2D` and one `RigidBody2D`, each owning one `CollisionShape2D`
- **WHEN** `tree.debug.colliders.enabled = true` and a frame is rendered against a recording `Renderer`
- **THEN** exactly two `drawRect(_, _, filled = false)` calls SHALL be observed
- **AND** zero `pushTransform`/`popTransform` calls SHALL be attributed to `ColliderWidget.drawDebug`

#### Scenario: ColliderWidget rect aligns with projected world rect

- **GIVEN** a `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))`, `tree.size = Vec2(1280f, 900f)`, FIT aspect
- **AND** an `Area2D` with a `RectangleShape2D` whose `worldBounds()` is `Rect(Vec2(100f, 100f), Vec2(200f, 200f))`
- **WHEN** `tree.debug.colliders.enabled = true` and a frame is rendered
- **THEN** the `drawRect` call's rect argument SHALL be the world rect `Rect(Vec2(100f, 100f), Vec2(200f, 200f))` unchanged
- **AND** the renderer's transform stack top at the moment of the call SHALL hold the FIT view transform for the camera

### Requirement: MomentumWidget owns its ring buffer

`MomentumWidget` SHALL extend `ScreenDebugWidget` and SHALL own its sample storage as instance fields (four `FloatArray` of capacity 60: `pX`, `pY`, `angular`, `KE`; `head` and `size` indices). The widget SHALL override `physicsProcess(dt)` such that, when `enabled` is `true`, it records the current `tree.totalLinearMomentum()`, `tree.totalAngularMomentum()`, and `tree.totalKineticEnergy()` into the buffer. The widget SHALL override the `enabled` setter such that flipping from `false` to `true` resets `size = 0` and `head = 0` (cleared buffer), preventing stale sparklines from previous sessions.

The engine SHALL NOT expose a process-wide `MomentumOverlay` singleton. `GameLoop.tick` SHALL NOT call into any momentum overlay sample method — sample collection happens inside the widget via the scene graph's `physicsProcess` dispatch.

#### Scenario: Buffer is per-tree

- **GIVEN** `treeA` and `treeB` exist concurrently with `momentum.enabled = true`
- **WHEN** `physicsProcess(dt)` is dispatched on both for several ticks with distinct rigid body sets
- **THEN** `treeA.debug.momentum`'s sample buffer SHALL contain only `treeA`'s sums
- **AND** `treeB.debug.momentum`'s sample buffer SHALL contain only `treeB`'s sums

#### Scenario: Toggling on resets the buffer

- **GIVEN** `tree.debug.momentum.enabled = true` and several samples have been recorded
- **WHEN** `tree.debug.momentum.enabled = false` is set, then `tree.debug.momentum.enabled = true` again
- **THEN** the next `drawDebug` call SHALL see size == 1 (or 0 if no `physicsProcess` happened yet between the flip and the draw)
- **AND** no samples from the prior enabled window SHALL appear

### Requirement: DebugHud lists registered widgets as togglable rows

`DebugHud` SHALL extend `ScreenDebugWidget` (initial `enabled = false`). When `enabled = true`, it SHALL render a `Panel` pinned to a screen corner (default: top-right, offset 12 px from edges), containing exactly one `Button` per `DebugWidget` currently in `tree.debug.widgets` (excluding `DebugHud` itself). Each `Button`'s label SHALL begin with `"[x] "` if its target widget's `enabled` is `true`, or `"[ ] "` otherwise, followed by the target widget's `title`. Clicking a `Button` SHALL flip the target widget's `enabled` and refresh the row label. The HUD SHALL re-evaluate the list each time `enabled` transitions to `true` (covering widgets registered after the previous HUD open).

When `enabled = false`, the HUD SHALL emit zero draw calls and SHALL NOT consume any mouse click via `tree.hitTestUI` — `Input.wasMouseClicked` SHALL pass through unchanged.

#### Scenario: HUD lists one row per registered widget

- **GIVEN** the four built-ins plus one user-registered `AxesWidget` are in `tree.debug.widgets`
- **WHEN** `tree.debug.hud.enabled = true` and a frame is rendered
- **THEN** the rendered HUD `Panel` SHALL contain exactly four `Button` children (FPS, Colliders, Momentum, Axes — the HUD itself excluded)
- **AND** the label order SHALL match registration order

#### Scenario: Clicking a row flips the target widget's enabled

- **GIVEN** `tree.debug.hud.enabled = true` and `tree.debug.fps.enabled = false`
- **WHEN** the user clicks the row labeled `"[ ] FPS"` (simulated via `Input.wasMouseClicked(Left)` on the row's screen rect)
- **THEN** by the next frame `tree.debug.fps.enabled` SHALL equal `true`
- **AND** the row's label SHALL begin with `"[x] "`

#### Scenario: HUD off does not consume clicks

- **GIVEN** `tree.debug.hud.enabled = false` and a game `Button` at screen coordinate `(100, 100)`
- **WHEN** the user clicks at `(100, 100)`
- **THEN** the game `Button` SHALL receive the click as if the HUD were not present
- **AND** `Input.wasMouseClicked(Left)` SHALL behave as it would in a HUD-less tree

### Requirement: DebugHud opens and closes via debugHudKey

The engine SHALL provide an internal `DebugToggleNode` inside `ScreenDebugCanvas` that polls `tree.input.wasKeyPressed(tree.debugHudKey)` each `process(dt)` and, when a press is detected, flips `tree.debug.hud.enabled`. `tree.debugHudKey` SHALL be a mutable property on `SceneTree` (default `Key.F1`) set by `GameHost` implementations from `GameConfig.debugHudKey` before the first frame.

`DebugToggleNode` SHALL be internal and not surfaced as a public `DebugWidget` — it has no toggle row in the HUD and is not enumerated in `tree.debug.widgets`.

#### Scenario: Pressing debugHudKey toggles HUD

- **GIVEN** `tree.debugHudKey = Key.F1` and `tree.debug.hud.enabled = false`
- **WHEN** the user presses F1 during a tick
- **THEN** by the next frame `tree.debug.hud.enabled` SHALL equal `true`
- **WHEN** the user presses F1 again during a later tick
- **THEN** by the next frame `tree.debug.hud.enabled` SHALL equal `false`

#### Scenario: Custom debugHudKey is honored

- **GIVEN** `tree.debugHudKey = Key.GRAVE` set by the host from `GameConfig(debugHudKey = Key.GRAVE)`
- **WHEN** the user presses the backtick key during a tick
- **THEN** `tree.debug.hud.enabled` SHALL flip

### Requirement: register/unregister can be called from game code

`tree.debug.register(widget)` and `tree.debug.unregister(widget)` SHALL be callable from any non-engine call site (game `Main.kt`, script bridge, test) after `tree.start()` has returned, without requiring any internal engine cooperation. Registering a widget SHALL make it appear in the HUD on the next time the HUD's `enabled` transitions to `true`.

#### Scenario: Custom widget registered post-start appears in HUD

- **GIVEN** `tree.start()` has run
- **WHEN** a game registers `AxesWidget : WorldDebugWidget` via `tree.debug.register(AxesWidget())`
- **AND** the user opens the HUD via `debugHudKey`
- **THEN** a row labeled `"[ ] Axes"` SHALL appear in the HUD Panel
- **AND** clicking that row SHALL flip the widget's `enabled`

## MODIFIED Requirements

### Requirement: GameHost.render does not draw

`GameHost` implementations (Skiko, LWJGL, future) SHALL NOT issue any `renderer.draw*` calls outside of `tree.render(renderer)`. All visual output, including debug overlays, SHALL be produced by `SceneTree.render` walking the scene graph.

Additionally, `GameHost` implementations SHALL NOT instantiate `FpsCounter`, SHALL NOT write to any field on `tree.debug`, SHALL NOT call any momentum overlay or debug overlay helper, and SHALL NOT poll keys for the purpose of toggling debug widgets. The host's only debug-related responsibility is to set `tree.debugHudKey = config.debugHudKey` once during startup so the engine's internal `DebugToggleNode` reads the configured key.

#### Scenario: SkikoHost frame contains no extra draws

- **WHEN** a `SkikoHost` frame is observed (via instrumentation or grep of `:engine-skiko` for `renderer.drawText`, `renderer.drawRect`, etc. outside of `tree.render` paths)
- **THEN** there SHALL be no such calls — all drawing flows through `tree.render(renderer)`

#### Scenario: LwjglHost frame contains no extra draws

- **WHEN** a `LwjglHost` frame is observed (same criteria)
- **THEN** there SHALL be no `renderer.draw*` calls outside `tree.render`

#### Scenario: Hosts touch tree.debug only via debugHudKey

- **WHEN** the source of `SkikoHost.kt` or `LwjglHost.kt` is grep'd for `tree.debug`, `FpsCounter`, or `MomentumOverlay`
- **THEN** the only matches SHALL be `tree.debugHudKey =` (or equivalent property assignment during startup)
- **AND** no per-frame reads or writes of `tree.debug.*` SHALL appear

## REMOVED Requirements

### Requirement: SceneTree exposes debug flags

**Reason**: Replaced by "SceneTree exposes a DebugRegistry". The `DebugFlags` type with fixed `showFps`/`showColliders`/`showMomentum`/`currentFps` no longer exists — each widget owns its own `enabled` and its own derived state (FpsCounter inside `FpsWidget`, ring buffer inside `MomentumWidget`). Hosts no longer maintain `currentFps` from outside.

**Migration**: Game code that read `tree.debug.showFps` migrates to `tree.debug.fps.enabled`. Game code that wrote `tree.debug.currentFps` is dropped — the widget computes FPS itself.

### Requirement: Host polls toggle keys and writes to tree.debug

**Reason**: Hosts no longer poll toggle keys. The engine's internal `DebugToggleNode` polls `tree.debugHudKey` and toggles `tree.debug.hud.enabled`; individual widgets are toggled via the HUD's clickable rows, not via keys.

**Migration**: Hosts strip the three `if (input.wasKeyPressed(config.toggleXxxKey))` blocks. The lone host responsibility becomes setting `tree.debugHudKey = config.debugHudKey` once during startup.
