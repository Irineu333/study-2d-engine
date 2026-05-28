## ADDED Requirements

### Requirement: SceneTree exposes debug flags

`SceneTree` SHALL expose a `debug: DebugFlags` member instantiated alongside the tree. `DebugFlags` SHALL contain at minimum:

- `var showFps: Boolean = false`
- `var showColliders: Boolean = false`
- `var showMomentum: Boolean = false`

`DebugFlags` SHALL NOT be a `Node`, SHALL NOT be `@Serializable`, and SHALL NOT persist across `SceneTree` lifetimes — it is pure runtime state.

#### Scenario: Default flags are all false

- **WHEN** a `SceneTree` is constructed
- **THEN** `tree.debug.showFps`, `tree.debug.showColliders`, and `tree.debug.showMomentum` SHALL all equal `false`.

#### Scenario: Flags are mutable from host and from scripts

- **WHEN** any code (host, script, test) reads or writes `tree.debug.showFps`
- **THEN** the access SHALL succeed without serialization or scene-graph hooks, and the value SHALL persist for the lifetime of the `SceneTree`.

### Requirement: Engine auto-inserts DebugOverlayLayer into every SceneTree

The engine SHALL auto-insert a `DebugOverlayLayer` (a `CanvasLayer` with `layer = Int.MAX_VALUE - 1`) as a last child of `SceneTree.root` during tree construction. `DebugOverlayLayer` SHALL contain three child widgets:

- `FpsLabel` — a `Label` placed at top-left corner, visible only when `tree.debug.showFps`. Shows current frames-per-second as `"FPS: <n>"`.
- `ColliderOverlay` — a `Node2D` that walks the world subtree on each `_process` and draws AABB outlines of every `CollisionObject2D`'s `CollisionShape2D`s; visible only when `tree.debug.showColliders`. Colors: distinct hues for `Area2D` (e.g. green) vs `PhysicsBody2D` (e.g. red).
- `MomentumOverlay` — a `Node2D` that queries the `PhysicsSystem` for `Σp`, `ΣL`, `ΣKE` totals each `_process` and renders text + sparklines, visible only when `tree.debug.showMomentum`.

The auto-inserted layer SHALL have a stable name (`"__debug"`) and SHALL be findable via `tree.root.findChild("__debug")` for testing.

#### Scenario: DebugOverlayLayer is present in every tree

- **WHEN** a `SceneTree` is built from any bundle or programmatic root
- **THEN** `tree.root.findChild("__debug")` SHALL return a `DebugOverlayLayer` instance, regardless of what the original root contained.

#### Scenario: ColliderOverlay walks world only, not UI

- **WHEN** `tree.debug.showColliders = true` and the tree contains both world `CollisionObject2D`s and a `Button` inside a `CanvasLayer`
- **THEN** `ColliderOverlay` SHALL draw AABBs only for the world `CollisionObject2D`s; the `Button` rect SHALL NOT be drawn (it is not a `CollisionObject2D`).

#### Scenario: FpsLabel hidden when flag is off

- **WHEN** `tree.debug.showFps = false`
- **THEN** the `FpsLabel` inside the `DebugOverlayLayer` SHALL NOT produce any draw calls.

### Requirement: GameHost.render does not draw

`GameHost` implementations (Skiko, LWJGL, future) SHALL NOT issue any `renderer.draw*` calls outside of `tree.render(renderer)`. All visual output, including debug overlays, SHALL be produced by `SceneTree.render` walking the scene graph.

#### Scenario: SkikoHost frame contains no extra draws

- **WHEN** a `SkikoHost` frame is observed (via instrumentation or grep of `:engine-skiko` for `renderer.drawText`, `renderer.drawRect`, etc. outside of `tree.render` paths)
- **THEN** there SHALL be no such calls — all drawing flows through `tree.render(renderer)`.

#### Scenario: LwjglHost frame contains no extra draws

- **WHEN** a `LwjglHost` frame is observed (same criteria)
- **THEN** there SHALL be no `renderer.draw*` calls outside `tree.render`.

### Requirement: Host polls toggle keys and writes to tree.debug

`GameHost` implementations SHALL, on each frame, after `input.beginTick()` and before `tree.hitTestUI(input)`, poll the three toggle keys configured in `GameConfig` (`toggleFpsKey`, `toggleCollidersKey`, `toggleMomentumOverlayKey`). On detection of `input.wasKeyPressed(...)` for each key, the host SHALL flip the corresponding boolean in `tree.debug`.

The host SHALL NOT cache or shadow these flags — `tree.debug` is the single source of truth read by `DebugOverlayLayer`.

#### Scenario: F1 toggles showFps

- **WHEN** `GameConfig.toggleFpsKey = Key.F1` and the user presses F1
- **THEN** at the start of the next frame, `tree.debug.showFps` SHALL flip its boolean value.

#### Scenario: F2 toggles showColliders

- **WHEN** `GameConfig.toggleCollidersKey = Key.F2` and the user presses F2
- **THEN** at the start of the next frame, `tree.debug.showColliders` SHALL flip.

#### Scenario: F3 toggles showMomentum

- **WHEN** `GameConfig.toggleMomentumOverlayKey = Key.F3` and the user presses F3
- **THEN** at the start of the next frame, `tree.debug.showMomentum` SHALL flip.
