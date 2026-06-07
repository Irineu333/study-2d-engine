## MODIFIED Requirements

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

### Requirement: DebugHud lists registered widgets as togglable rows

`DebugHud` SHALL extend `ScreenDebugWidget` (initial `enabled = false`). When `enabled = true`, it SHALL render a `Panel` pinned to a screen corner (default: top-right, offset 12 px from edges), containing exactly one `Button` per `DebugWidget` currently in `tree.debug.widgets` (excluding `DebugHud` itself). Each `Button`'s label SHALL begin with `"[x] "` if its target widget's `enabled` is `true`, or `"[ ] "` otherwise, followed by the target widget's `title`. Clicking a `Button` SHALL flip the target widget's `enabled` and refresh the row label. The HUD SHALL re-evaluate the list each time `enabled` transitions to `true` (covering widgets registered after the previous HUD open), **and** whenever `tree.debug.widgets` changes while the HUD is already open — a widget registered or unregistered live (e.g. from a `Node`'s `onEnter`/`onExit`) SHALL add or remove its row by the next frame, without the user closing and reopening the HUD.

When `enabled = false`, the HUD SHALL emit zero draw calls and SHALL NOT consume any mouse click via `tree.hitTestUI` — `Input.wasMouseClicked` SHALL pass through unchanged.

The user-registered widget named in the scenario below SHALL be a hypothetical example (`ExampleWidget`, `title = "Example"`), not a class shipped by a specific game module — the HUD's one-row-per-widget contract is engine-level and independent of `:games:demos`.

#### Scenario: HUD lists one row per registered widget

- **GIVEN** the built-ins plus one user-registered `ExampleWidget` (with `title = "Example"`) are in `tree.debug.widgets`
- **WHEN** `tree.debug.hud.enabled = true` and a frame is rendered
- **THEN** the rendered HUD `Panel` SHALL contain exactly one `Button` child per registered widget (excluding the HUD itself), including an `Example` row
- **AND** the label order SHALL match registration order

#### Scenario: Open HUD reflects live register/unregister without reopening

- **GIVEN** `tree.debug.hud.enabled = true` with its `Panel` already built and showing rows
- **WHEN** a game registers a new widget via `tree.debug.register(...)`
- **THEN** by the next frame the HUD `Panel` SHALL contain a row for it, without the HUD's `enabled` transitioning
- **WHEN** that widget is later removed via `tree.debug.unregister(...)`
- **THEN** by the next frame its row SHALL be absent from the HUD `Panel`

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

### Requirement: register/unregister can be called from game code

`tree.debug.register(widget)` and `tree.debug.unregister(widget)` SHALL be callable from any non-engine call site (game `Main.kt`, a game `Node`'s `onEnter`/`onExit`, script bridge, test) after `tree.start()` has returned, without requiring any internal engine cooperation. Registering a widget SHALL make it appear in the HUD on the next time the HUD's `enabled` transitions to `true`. Unregistering a widget SHALL remove its row from the HUD on the next re-evaluation.

The custom widget named in the scenario below SHALL be a hypothetical example (`ExampleWidget`, `title = "Example"`), not a class shipped by a specific game module — the extension contract is engine-level and independent of `:games:demos`.

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
