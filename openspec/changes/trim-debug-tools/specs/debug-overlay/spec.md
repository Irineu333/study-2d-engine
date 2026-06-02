## MODIFIED Requirements

### Requirement: SceneTree exposes a DebugRegistry

`SceneTree` SHALL expose `val debug: DebugRegistry` instantiated alongside the tree. `DebugRegistry` SHALL provide:

- `fun register(widget: DebugWidget)` — routes by subtype: `WorldDebugWidget` is added to the world container; `ScreenDebugWidget` is added to the screen container; both happen as live `addChild` operations and SHALL appear in the registry's `widgets` list. Registering a `ScreenDebugWidget` SHALL additionally enroll it in the per-tree `DebugDock`, which assigns its screen origin from its declared `DockSlot` — the widget SHALL NOT hardcode a corner.
- `fun unregister(widget: DebugWidget)` — removes the widget from its container and from the list.
- `val widgets: List<DebugWidget>` — read-only listing of currently registered widgets in registration order.
- `inline fun <reified T : DebugWidget> find(): T?` — first widget of the requested concrete type, or `null`.
- Convenience fields for the built-ins: `val colliders: ColliderWidget`, `val log: LogOverlayWidget`, `val hud: DebugHud`. These fields point at the engine-owned instances and exist solely as ergonomic shortcuts to flip `enabled`. `DebugRegistry` SHALL NOT expose `fps`, `momentum`, or `shapeGizmo` fields — those widgets no longer exist (fps is folded into the profiler, real-geometry drawing into `ColliderWidget`, and the momentum overlay is removed).

`DebugRegistry` SHALL NOT be a `Node`, SHALL NOT be `@Serializable`, and SHALL NOT persist across `SceneTree` lifetimes — pure runtime state. Each `SceneTree` instance SHALL own its own `DebugRegistry` (no static or singleton sharing across trees).

#### Scenario: Built-ins are accessible via convenience fields

- **WHEN** a `SceneTree` is constructed and `start()` is called
- **THEN** `tree.debug.colliders`, `tree.debug.log`, and `tree.debug.hud` SHALL all be non-null
- **AND** `tree.debug.widgets` SHALL contain at least these instances

#### Scenario: register routes by subtype

- **GIVEN** a `WorldDebugWidget` instance `axes` and a `ScreenDebugWidget` instance `hud2`
- **WHEN** `tree.debug.register(axes)` and `tree.debug.register(hud2)` are called
- **THEN** `axes.parent` SHALL be the world container child of `DebugLayer`
- **AND** `hud2.parent` SHALL be the screen container child of `DebugLayer`
- **AND** `tree.debug.widgets` SHALL include both, in registration order

#### Scenario: Screen widget origin comes from the dock

- **GIVEN** a `ScreenDebugWidget` instance `hud2` with a declared `DockSlot`
- **WHEN** `tree.debug.register(hud2)` is called
- **THEN** `hud2` SHALL be enrolled in the tree's `DebugDock`
- **AND** its screen origin SHALL be assigned by the dock from its `DockSlot`, not by a hardcoded corner constant in the widget

#### Scenario: find returns instance by type

- **GIVEN** a custom widget `AxesWidget : WorldDebugWidget` registered exactly once
- **WHEN** `tree.debug.find<AxesWidget>()` is called
- **THEN** the registered instance SHALL be returned

#### Scenario: Two SceneTrees do not share registry state

- **GIVEN** two distinct `SceneTree` instances `treeA` and `treeB`
- **WHEN** `treeA.debug.colliders.enabled = true` is set
- **THEN** `treeB.debug.colliders.enabled` SHALL remain `false`
- **AND** `treeA.debug.colliders` and `treeB.debug.colliders` SHALL be distinct instances

### Requirement: Engine auto-inserts DebugLayer with two sub-containers

The engine SHALL auto-insert a `DebugLayer` (a `Node`) as a child of `SceneTree.root` during `SceneTree.start()`, after the root's own `onEnter` has fired. The `DebugLayer` SHALL have a stable name `"__debug"` and SHALL contain exactly two child containers:

- `WorldDebugContainer` (a `Node2D` directly under `DebugLayer`) — hosts `WorldDebugWidget` instances. Participates in the world pass of `SceneTree.render`, receiving the active `Camera2D` view transform.
- `ScreenDebugCanvas` (a `CanvasLayer` with `layer = Int.MAX_VALUE - 1`) — hosts `ScreenDebugWidget` instances. Painted in the UI pass on top of any game UI.

The engine SHALL register the built-in widgets during the auto-insertion. The catalog SHALL NOT include `FpsWidget`, `MomentumWidget`, or `ShapeGizmoWidget` (removed/folded). The engine SHALL additionally insert an internal `DebugToggleNode` inside `ScreenDebugCanvas` that polls input each tick (see "DebugHud opens and closes via debugHudKey").

Re-inserting on a re-attached tree (stop → start) SHALL be idempotent — the engine SHALL skip the addition when a child named `"__debug"` is already present on root.

#### Scenario: DebugLayer is present in every started tree

- **WHEN** `SceneTree(root).start()` has been called on any bundle or programmatic root
- **THEN** `tree.root.findChild("__debug")` SHALL return a `DebugLayer` instance
- **AND** that `DebugLayer` SHALL contain exactly one `WorldDebugContainer` child and one `ScreenDebugCanvas` child

#### Scenario: Auto-insert is idempotent across re-start

- **WHEN** a `SceneTree` is started, stopped, and started again on the same root
- **THEN** root SHALL contain exactly one child named `"__debug"`

#### Scenario: Built-ins are hosted in the correct container

- **WHEN** the engine has finished auto-inserting `DebugLayer`
- **THEN** `tree.debug.colliders.parent` SHALL be the `WorldDebugContainer` instance
- **AND** `tree.debug.log.parent` and `tree.debug.hud.parent` SHALL both be the `ScreenDebugCanvas` instance
- **AND** no `FpsWidget` or `MomentumWidget` instance SHALL exist anywhere under `DebugLayer`

### Requirement: ColliderWidget draws world colliders without manual transform

`ColliderWidget` SHALL extend `WorldDebugWidget` and SHALL expose a draw mode `var mode: ColliderDrawMode` over `enum ColliderDrawMode { AABB, REAL, BOTH }`, defaulting to `REAL`. When `enabled = true`, it SHALL iterate `collectActiveCollisionShapes(tree)` and, per entry, draw according to `mode`:

- `AABB` — the shape's broad-phase axis-aligned bounds via `renderer.drawRect(bounds, color, filled = false)`.
- `REAL` — the shape's real geometry: a non-filled circle outline for `CircleShape2D` (world center and scaled radius) and the closed quad of `worldCorners` for `RectangleShape2D` (covering the rotated case).
- `BOTH` — the AABB first, then the real geometry on top.

Colors SHALL be green-ish (`Color(0f, 1f, 0f, 0.8f)`) for `Area2D` owners and red-ish (`Color(1f, 0.3f, 0.3f, 0.8f)`) for body owners. `ColliderWidget` SHALL NOT call `renderer.pushTransform` or `renderer.popTransform` — the active `Camera2D` view transform is applied by the world pass. `mode` SHALL be settable programmatically and cyclable at runtime via an engine-internal shortcut node (in the spirit of the existing time/layout shortcut nodes).

#### Scenario: REAL mode draws real geometry per active shape

- **GIVEN** a tree with one `Area2D` owning a `CircleShape2D` and one body owning a `RectangleShape2D`, with `tree.debug.colliders.mode = ColliderDrawMode.REAL`
- **WHEN** `tree.debug.colliders.enabled = true` and a frame is rendered against a recording `Renderer`
- **THEN** a non-filled `drawCircle` SHALL be observed for the circle and the four `worldCorners` edges SHALL be drawn for the rectangle
- **AND** zero `pushTransform`/`popTransform` calls SHALL be attributed to `ColliderWidget.drawDebug`

#### Scenario: AABB mode draws one rect per active shape

- **GIVEN** a tree with one `Area2D` and one `RigidBody2D`, each owning one `CollisionShape2D`, with `tree.debug.colliders.mode = ColliderDrawMode.AABB`
- **WHEN** `tree.debug.colliders.enabled = true` and a frame is rendered against a recording `Renderer`
- **THEN** exactly two `drawRect(_, _, filled = false)` calls SHALL be observed

#### Scenario: BOTH mode draws AABB and real geometry

- **GIVEN** a tree with one body owning a `RectangleShape2D`, with `tree.debug.colliders.mode = ColliderDrawMode.BOTH`
- **WHEN** `tree.debug.colliders.enabled = true` and a frame is rendered
- **THEN** both the broad-phase `drawRect` and the rotated `worldCorners` quad SHALL be observed
- **AND** the AABB SHALL be drawn before the real geometry

#### Scenario: Default mode is REAL

- **WHEN** a `SceneTree` is started and `tree.debug.colliders` is read
- **THEN** its `mode` SHALL be `ColliderDrawMode.REAL`

## REMOVED Requirements

### Requirement: MomentumWidget owns its ring buffer

**Reason**: O `MomentumWidget` (Σp/ΣL/ΣKE) só produz dados úteis com `RigidBody2D` e duplicava o eixo "movimento físico" já coberto, de forma mais geral, pelo `VelocityGizmoWidget`. Removido para enxugar o HUD.

**Migration**: Os helpers `SceneTree.totalLinearMomentum()`, `totalAngularMomentum()` e `totalKineticEnergy()` permanecem em `com.neoutils.engine.physics` (`MomentumDiagnostics.kt`) como API pública — um jogo que precise inspecionar conservação pode chamá-los e desenhar/logar por conta própria. Não há substituto de overlay built-in.
