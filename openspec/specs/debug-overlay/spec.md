# debug-overlay Specification

## Purpose

Defines how the engine renders its built-in debug overlays — frames-per-second
counter, collision-shape outlines, and momentum/energy diagnostics — as
first-class nodes inside the scene graph instead of out-of-band draws issued
by the host. A dedicated `DebugOverlayLayer` (a `CanvasLayer` auto-inserted
by `SceneTree`) hosts the widgets; `SceneTree.debug` exposes the mutable
flags; `GameHost` implementations only flip the flags in response to the
configured toggle keys (`F1` / `F2` / `F3` by default) and otherwise do
no drawing themselves.

## Requirements

### Requirement: SceneTree exposes debug flags

`SceneTree` SHALL expose a `debug: DebugFlags` member instantiated alongside the tree. `DebugFlags` SHALL contain at minimum:

- `var showFps: Boolean = false`
- `var showColliders: Boolean = false`
- `var showMomentum: Boolean = false`
- `var currentFps: Float = 0f` — written by the host each frame, read by `FpsLabel`.

`DebugFlags` SHALL NOT be a `Node`, SHALL NOT be `@Serializable`, and SHALL NOT persist across `SceneTree` lifetimes — it is pure runtime state.

#### Scenario: Default flags are all false

- **WHEN** a `SceneTree` is constructed
- **THEN** `tree.debug.showFps`, `tree.debug.showColliders`, and `tree.debug.showMomentum` SHALL all equal `false`.

#### Scenario: Flags are mutable from host and from scripts

- **WHEN** any code (host, script, test) reads or writes `tree.debug.showFps`
- **THEN** the access SHALL succeed without serialization or scene-graph hooks, and the value SHALL persist for the lifetime of the `SceneTree`.

### Requirement: Engine auto-inserts DebugOverlayLayer into every SceneTree

The engine SHALL auto-insert a `DebugOverlayLayer` (a `CanvasLayer` with `layer = Int.MAX_VALUE - 1`) as a child of `SceneTree.root` during `SceneTree.start()` — after `root.attachToLiveTree(...)` has dispatched the root's own `onEnter`. Deferring the insertion until after `onEnter` avoids surprising roots that key off `children.isEmpty()` as a first-run setup signal. `DebugOverlayLayer` SHALL contain three child widgets:

- `FpsLabel` — draws `"fps <n>"` at the top-left corner reading `tree.debug.currentFps`, visible only when `tree.debug.showFps`.
- `ColliderOverlay` — walks the world subtree on each draw and outlines every `CollisionObject2D`'s `CollisionShape2D` AABB; visible only when `tree.debug.showColliders`. Colors: green for `Area2D`, red for `PhysicsBody2D`. Pushes the current `Camera2D` view transform locally so outlines align with the projected world.
- `MomentumOverlayNode` — wraps the `MomentumOverlay` singleton (ring buffer + sparklines) and renders `Σp`, `ΣL`, `ΣKE`; visible only when `tree.debug.showMomentum`.

The auto-inserted layer SHALL have the stable name `"__debug"` and SHALL be findable via `tree.root.findChild("__debug")` once `start()` has returned. Re-inserting on a re-attached tree (stop → start) SHALL be idempotent — `ensureDebugOverlay()` skips the addition when the named child is already present.

#### Scenario: DebugOverlayLayer is present in every started tree

- **WHEN** `SceneTree(root).start()` has been called on any bundle or programmatic root
- **THEN** `tree.root.findChild("__debug")` SHALL return a `DebugOverlayLayer` instance, regardless of what the original root contained.

#### Scenario: Auto-insert is idempotent across re-start

- **WHEN** a `SceneTree` is started, stopped, and started again on the same root
- **THEN** the root SHALL contain exactly one child named `"__debug"`.

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

`GameHost` implementations SHALL, on each frame, after `input.beginTick()` and before invoking the engine loop, poll the three toggle keys configured in `GameConfig` (`toggleFpsKey`, `toggleCollidersKey`, `toggleMomentumOverlayKey`). On detection of `input.wasKeyPressed(...)` for each key, the host SHALL flip the corresponding boolean in `tree.debug`. When enabling `showMomentum`, the host MAY also call `MomentumOverlay.reset()` to clear the ring buffer.

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
