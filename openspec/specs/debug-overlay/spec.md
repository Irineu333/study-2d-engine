# debug-overlay Specification

## Purpose

Defines the engine's debug visualization model: pluggable `DebugWidget`s
(screen-space or world-space) registered in a per-`SceneTree`
`DebugRegistry`, hosted under an auto-inserted `DebugLayer` with two
sub-containers, and toggled at runtime by a single HUD opened via a
configurable keybind (`GameConfig.debugHudKey`). Every visualization —
FPS, collider outlines, momentum diagnostics, and any custom gizmo a game
registers — flows through `SceneTree.render` as ordinary scene-graph
nodes; `GameHost` implementations issue no draws of their own and do not
poll input for debug purposes.

## Requirements

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

- `fun register(widget: DebugWidget)` — routes by subtype: `WorldDebugWidget` is added to the world container; `ScreenDebugWidget` is added to the screen container; both happen as live `addChild` operations and SHALL appear in the registry's `widgets` list. Registering a `ScreenDebugWidget` SHALL additionally enroll it in the per-tree `DebugDock`, which assigns its screen origin from its declared `DockSlot` — the widget SHALL NOT hardcode a corner.
- `fun unregister(widget: DebugWidget)` — removes the widget from its container and from the list.
- `val widgets: List<DebugWidget>` — read-only listing of currently registered widgets in registration order.
- `inline fun <reified T : DebugWidget> find(): T?` — first widget of the requested concrete type, or `null`.
- Convenience fields for the built-ins: `val colliders: ColliderWidget`, `val log: LogOverlayWidget`, `val hud: DebugHud`. These fields point at the engine-owned instances and exist solely as ergonomic shortcuts to flip `enabled`. `DebugRegistry` SHALL NOT expose `fps`, `momentum`, or `shapeGizmo` fields — those widgets no longer exist (fps is folded into the profiler, real-geometry drawing into `ColliderWidget`, and the momentum overlay is removed).

`DebugRegistry` SHALL NOT be a `Node`, SHALL NOT be `@Serializable`, and SHALL NOT persist across `SceneTree` lifetimes — pure runtime state. Each `SceneTree` instance SHALL own its own `DebugRegistry` (no static or singleton sharing across trees).

The illustrative custom widget named in the scenarios below SHALL be a hypothetical example (`ExampleWidget`), not any concrete widget shipped by a specific game module — the registry contract is engine-level and independent of `:games:demos`.

#### Scenario: Built-ins are accessible via convenience fields

- **WHEN** a `SceneTree` is constructed and `start()` is called
- **THEN** `tree.debug.colliders`, `tree.debug.log`, and `tree.debug.hud` SHALL all be non-null
- **AND** `tree.debug.widgets` SHALL contain at least these instances

#### Scenario: register routes by subtype

- **GIVEN** a `WorldDebugWidget` instance `world` and a `ScreenDebugWidget` instance `hud2`
- **WHEN** `tree.debug.register(world)` and `tree.debug.register(hud2)` are called
- **THEN** `world.parent` SHALL be the world container child of `DebugLayer`
- **AND** `hud2.parent` SHALL be the screen container child of `DebugLayer`
- **AND** `tree.debug.widgets` SHALL include both, in registration order

#### Scenario: Screen widget origin comes from the dock

- **GIVEN** a `ScreenDebugWidget` instance `hud2` with a declared `DockSlot`
- **WHEN** `tree.debug.register(hud2)` is called
- **THEN** `hud2` SHALL be enrolled in the tree's `DebugDock`
- **AND** its screen origin SHALL be assigned by the dock from its `DockSlot`, not by a hardcoded corner constant in the widget

#### Scenario: find returns instance by type

- **GIVEN** a custom widget `ExampleWidget : WorldDebugWidget` registered exactly once
- **WHEN** `tree.debug.find<ExampleWidget>()` is called
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

`ColliderWidget` SHALL extend `WorldDebugWidget` and SHALL expose a draw mode `var mode: ColliderDrawMode` over `enum ColliderDrawMode { AABB, REAL }`, defaulting to `REAL`. When `enabled = true`, it SHALL iterate `collectActiveCollisionShapes(tree)` and, per entry, draw according to `mode`:

- `AABB` — the shape's broad-phase axis-aligned bounds via `renderer.drawRect(bounds, color, filled = false)`.
- `REAL` — the shape's real geometry: a non-filled circle outline for `CircleShape2D` (world center and scaled radius) and the closed quad of `worldCorners` for `RectangleShape2D` (covering the rotated case).

Colors SHALL be green-ish (`Color(0f, 1f, 0f, 0.8f)`) for `Area2D` owners and red-ish (`Color(1f, 0.3f, 0.3f, 0.8f)`) for body owners. `ColliderWidget` SHALL NOT call `renderer.pushTransform` or `renderer.popTransform` — the active `Camera2D` view transform is applied by the world pass. `mode` SHALL be settable programmatically and selectable at runtime via an engine-internal screen-space control panel `ColliderModePanel` (a `ScreenDebugWidget`, segmented `AABB | REAL` with the active segment highlighted). The panel is the colliders tool's screen-space arm: its `enabled` SHALL proxy `colliders.enabled` (get and set, so toggling the HUD's "Colliders" row shows/hides the panel and the panel's close `[x]` disables the gizmo), and it SHALL be auto-inserted under `ScreenDebugCanvas` but kept out of `DebugRegistry.widgets`/HUD (no second "Colliders" row) — in the spirit of the `scenePicker` + `SelectionGizmoWidget` split.

#### Scenario: The mode panel is the colliders tool's screen-space arm

- **WHEN** `SceneTree.start()` has completed
- **THEN** `tree.debug.colliderModePanel` SHALL be a child of the `ScreenDebugCanvas`
- **AND** it SHALL NOT appear in `tree.debug.widgets` (no second HUD row)
- **AND** `tree.debug.colliderModePanel.enabled` SHALL track `tree.debug.colliders.enabled`

#### Scenario: Selecting a segment sets the mode; closing disables the gizmo

- **GIVEN** `tree.debug.colliders.enabled = true` with the panel built
- **WHEN** the `AABB` segment button is pressed
- **THEN** `tree.debug.colliders.mode` SHALL become `ColliderDrawMode.AABB`
- **AND** WHEN the panel's `enabled` is set to `false` (its close control), `tree.debug.colliders.enabled` SHALL become `false`

#### Scenario: REAL mode draws real geometry per active shape

- **GIVEN** a tree with one `Area2D` owning a `CircleShape2D` and one body owning a `RectangleShape2D`, with `tree.debug.colliders.mode = ColliderDrawMode.REAL`
- **WHEN** `tree.debug.colliders.enabled = true` and a frame is rendered against a recording `Renderer`
- **THEN** a non-filled `drawCircle` SHALL be observed for the circle and the four `worldCorners` edges SHALL be drawn for the rectangle
- **AND** zero `pushTransform`/`popTransform` calls SHALL be attributed to `ColliderWidget.drawDebug`

#### Scenario: AABB mode draws one rect per active shape

- **GIVEN** a tree with one `Area2D` and one `RigidBody2D`, each owning one `CollisionShape2D`, with `tree.debug.colliders.mode = ColliderDrawMode.AABB`
- **WHEN** `tree.debug.colliders.enabled = true` and a frame is rendered against a recording `Renderer`
- **THEN** exactly two `drawRect(_, _, filled = false)` calls SHALL be observed

#### Scenario: Default mode is REAL

- **WHEN** a `SceneTree` is started and `tree.debug.colliders` is read
- **THEN** its `mode` SHALL be `ColliderDrawMode.REAL`

### Requirement: LogOverlayWidget tails recent log entries on screen

`LogOverlayWidget` SHALL extend `ScreenDebugWidget` and SHALL implement
`LogSink`. It SHALL own a fixed-capacity ring buffer of the last `N`
entries (default capacity `12`), each stored as an immutable
`LogEntry(timestampMillis, level, tag, message)`. As a `LogSink`, its
`emit(...)` SHALL append the entry to the ring buffer (overwriting the
oldest when full); `emit` MAY be invoked from any thread.

`LogOverlayWidget` SHALL subscribe and unsubscribe from `Log` based on
`enabled`, by overriding the `enabled` setter:

- On transition `false → true`: it SHALL call `Log.addSink(this)` and
  SHALL clear the ring buffer (no stale entries from a prior enabled
  window).
- On transition `true → false`: it SHALL call `Log.removeSink(this)`.

While not subscribed (`enabled = false`), the widget SHALL record nothing;
opening it begins a live tail of subsequently emitted entries, not of past
history. `drawDebug` SHALL read a consistent snapshot of the buffer safely
with respect to concurrent `emit` calls (e.g. via synchronization), draw
the entries anchored to the bottom-left corner of `tree.size` with the
most recent at the bottom, re-anchored each frame so it follows
`tree.resize`, and color each line by `LogLevel` (Debug/Info neutral,
`Warn` amber, `Error` red).

`LogOverlayWidget` SHALL expose `var minLevel: LogLevel` (default
`LogLevel.Debug`) as a display-only filter: `drawDebug` SHALL skip entries
whose `level` is below `minLevel`. This filter SHALL be orthogonal to
`Log.config` — it can only restrict beyond what already reached `emit`,
never recover entries gated out by `Log.config`.

#### Scenario: Enabling subscribes and clears the buffer

- **GIVEN** `tree.debug.log.enabled = false` with stale entries from a prior window
- **WHEN** `tree.debug.log.enabled = true` is set
- **THEN** the widget SHALL be a registered `Log` sink
- **AND** the ring buffer SHALL be empty until the next `Log.*` call

#### Scenario: Disabling unsubscribes and stops recording

- **GIVEN** `tree.debug.log.enabled = true`
- **WHEN** `tree.debug.log.enabled = false` is set and then `Log.i(...)` is emitted
- **THEN** the widget SHALL NOT be a registered `Log` sink
- **AND** the emitted entry SHALL NOT appear in the widget's buffer

#### Scenario: Ring buffer keeps only the last N entries

- **GIVEN** `tree.debug.log.enabled = true` and capacity `N = 12`
- **WHEN** `N + 5` entries are emitted via `Log.*` above the gate
- **THEN** the buffer SHALL hold exactly `N` entries
- **AND** they SHALL be the `N` most recent, in emission order

#### Scenario: Lines are colored by level

- **GIVEN** `tree.debug.log.enabled = true` with one `Warn` and one `Error` entry buffered
- **WHEN** a frame is rendered against a recording `Renderer`
- **THEN** the `drawText` for the `Warn` entry SHALL use the amber color
- **AND** the `drawText` for the `Error` entry SHALL use the red color

#### Scenario: minLevel filters the display

- **GIVEN** `tree.debug.log.enabled = true`, `minLevel = LogLevel.Warn`, and buffered Debug, Info, Warn, Error entries
- **WHEN** a frame is rendered
- **THEN** only the `Warn` and `Error` entries SHALL be drawn

#### Scenario: Disabled overlay emits zero draws

- **GIVEN** `tree.debug.log.enabled = false`
- **WHEN** a frame is rendered against a recording `Renderer`
- **THEN** zero draw calls SHALL be attributed to `LogOverlayWidget`

### Requirement: DebugHud lists registered widgets as togglable rows

`DebugHud` SHALL extend `ScreenDebugWidget` (initial `enabled = false`). When `enabled = true`, it SHALL render a `Panel` pinned to a screen corner (default: top-right, offset 12 px from edges), containing exactly one `Button` per `DebugWidget` currently in `tree.debug.widgets` (excluding `DebugHud` itself). Each `Button`'s label SHALL begin with `"[x] "` if its target widget's `enabled` is `true`, or `"[ ] "` otherwise, followed by the target widget's `title`. Clicking a `Button` SHALL flip the target widget's `enabled` and refresh the row label. The HUD SHALL re-evaluate the list each time `enabled` transitions to `true` (covering widgets registered after the previous HUD open).

When `enabled = false`, the HUD SHALL emit zero draw calls and SHALL NOT consume any mouse click via `tree.hitTestUI` — `Input.wasMouseClicked` SHALL pass through unchanged.

The user-registered widget named in the scenario below SHALL be a hypothetical example (`ExampleWidget`, `title = "Example"`), not a class shipped by a specific game module — the HUD's one-row-per-widget contract is engine-level and independent of `:games:demos`.

#### Scenario: HUD lists one row per registered widget

- **GIVEN** the built-ins plus one user-registered `ExampleWidget` (with `title = "Example"`) are in `tree.debug.widgets`
- **WHEN** `tree.debug.hud.enabled = true` and a frame is rendered
- **THEN** the rendered HUD `Panel` SHALL contain exactly one `Button` child per registered widget (excluding the HUD itself), including an `Example` row
- **AND** the label order SHALL match registration order

#### Scenario: Clicking a row flips the target widget's enabled

- **GIVEN** `tree.debug.hud.enabled = true` and `tree.debug.colliders.enabled = false`
- **WHEN** the user clicks the row labeled `"[ ] Colliders"` (simulated via `Input.wasMouseClicked(Left)` on the row's screen rect)
- **THEN** by the next frame `tree.debug.colliders.enabled` SHALL equal `true`
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

`tree.debug.register(widget)` and `tree.debug.unregister(widget)` SHALL be callable from any non-engine call site (game `Main.kt`, a game `Node`'s `onEnter`/`onExit`, script bridge, test) after `tree.start()` has returned, without requiring any internal engine cooperation. Registering a widget SHALL make it appear in the HUD on the next time the HUD's `enabled` transitions to `true`. Unregistering a widget SHALL remove its row from the HUD on the next re-evaluation.

The custom widget named in the scenarios below SHALL be a hypothetical example (`ExampleWidget`, `title = "Example"`), not a class shipped by a specific game module — the extension contract is engine-level and independent of `:games:demos`.

#### Scenario: Custom widget registered post-start appears in HUD

- **GIVEN** `tree.start()` has run
- **WHEN** a game registers `ExampleWidget : WorldDebugWidget` (with `title = "Example"`) via `tree.debug.register(ExampleWidget())`
- **AND** the user opens the HUD via `debugHudKey`
- **THEN** a row labeled `"[ ] Example"` SHALL appear in the HUD Panel
- **AND** clicking that row SHALL flip the widget's `enabled`

#### Scenario: Custom widget unregistered from game code leaves the HUD

- **GIVEN** a custom widget registered via `tree.debug.register(...)` and showing a HUD row
- **WHEN** the game calls `tree.debug.unregister(widget)` (e.g. from a `Node`'s `onExit`)
- **AND** the HUD list is re-evaluated
- **THEN** the widget SHALL no longer appear in `tree.debug.widgets`
- **AND** its row SHALL be absent from the HUD Panel

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
