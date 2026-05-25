# dx-tooling Specification

## Purpose

Ferramentas de developer experience embutidas na engine — overlay de FPS togglable, log estruturado com tags por subsistema, visualização de debug de colliders. Tudo agrupado sob uma única superfície de configuração `Debug`.

## Requirements

### Requirement: FPS overlay togglable at runtime

The engine SHALL provide an FPS overlay that, when enabled, renders the current frames-per-second value over the active scene without affecting the scene's own rendering output. The overlay MUST be togglable at runtime (default: disabled). The overlay MUST compute FPS from a moving average over a window of at least one second to avoid flicker.

#### Scenario: Enabling the overlay shows FPS

- **WHEN** the FPS overlay is enabled
- **THEN** the rendered frame includes a readable FPS value
- **AND** the value updates at least once per second

#### Scenario: Disabling the overlay removes it

- **WHEN** the FPS overlay is disabled
- **THEN** no FPS value is rendered
- **AND** scene rendering is unaffected

### Requirement: Structured log with per-subsystem tags

The engine SHALL provide a logging facility usable from `:engine` and dependent modules, supporting four levels (`Debug`, `Info`, `Warn`, `Error`) and a `tag: String` per call site to identify the subsystem. The logger MUST emit timestamped output. The logger MUST allow the minimum effective level to be configured per tag and globally.

#### Scenario: Log entries include tag, level, and timestamp

- **WHEN** code calls `Log.d(tag = "Physics", "step took 0.4ms")`
- **THEN** the emitted line contains the tag, the level, a timestamp, and the message

#### Scenario: Level filtering hides lower-priority entries

- **WHEN** the global minimum level is set to `Info`
- **THEN** subsequent `Log.d(...)` calls produce no output
- **AND** subsequent `Log.i(...)` calls produce output

#### Scenario: Per-tag filter overrides global level

- **WHEN** the `Physics` tag is configured to `Debug` while global level is `Warn`
- **THEN** `Log.d(tag = "Physics", ...)` produces output
- **AND** `Log.d(tag = "Render", ...)` produces no output

### Requirement: Collider debug visualization

The engine SHALL provide a debug-render mode in which all `Collider` nodes in the active scene have their `bounds()` drawn as outlined rectangles using a visually distinct color. The mode MUST be togglable at runtime through `Debug.colliderVisualization` (default: disabled). When disabled, no additional rendering overhead MUST be incurred beyond a single flag check per frame. The drawing itself SHALL be performed by the integrating runtime (e.g. `:engine-compose`'s `GameSurface`), not by `:engine.scene.Scene` or any other core scene-graph traversal. The `:engine` module SHALL expose a utility (e.g. `collectColliders(scene): List<Collider>` or equivalent) that the runtime can call to enumerate the colliders to outline, without requiring the runtime to walk the tree itself.

#### Scenario: Enabling collider debug draws bounds

- **WHEN** the collider debug mode is enabled and the scene contains a `BoxCollider`
- **THEN** the rendered frame includes a rectangle outline matching the collider's `bounds()`

#### Scenario: Disabling collider debug stops drawing bounds

- **WHEN** the collider debug mode is disabled
- **THEN** no collider outline is rendered
- **AND** scene rendering is otherwise unaffected

#### Scenario: Core scene-graph traversal does not draw collider bounds

- **WHEN** `Debug.colliderVisualization = true` and `scene.render(renderer)` is invoked **directly** without going through the runtime overlay
- **THEN** the rendered output contains no debug rectangles
- **AND** the overlay only appears when the integrating runtime applies it on top of `loop.tick`

### Requirement: DX features togglable via a single debug surface

The engine SHALL expose DX toggles (FPS overlay, collider debug) through a single `Debug` configuration object accessible from game code and runtime code. Changes to the configuration MUST take effect by the next rendered frame.

#### Scenario: Toggling debug state propagates to next frame

- **WHEN** game code sets `Debug.colliderVisualization = true` during a tick
- **THEN** the next rendered frame includes collider outlines

### Requirement: Unified debug overlay rendering utility

The engine SHALL expose a utility function in `:engine` (e.g. `renderDebugOverlay(renderer: Renderer, tree: SceneTree)`) that performs the debug overlay drawing in a single place, consulting `Debug.showFps` and `Debug.colliderVisualization` and issuing the corresponding `Renderer` calls. The utility MUST be backend-agnostic: it MUST accept the `Renderer` SPI and MUST NOT reference any concrete backend type. The utility MUST be invokable by an integrating runtime (e.g. `:engine-compose`'s `ComposeHost`, `:engine-skiko`'s `SkikoHost`) after `GameLoop.tick(...)` and before the renderer is unbound, so the overlay appears on top of the tree's own render output. When both flags are `false`, the utility MUST issue zero draw calls.

When `Debug.colliderVisualization = true`, collider bounds SHALL be drawn in **world space**: the utility MUST, prior to issuing the collider `drawRect` calls, push the same view transform that `SceneTree.render` would push for the current camera (using `Camera2D.bounds`, `tree.size`, and `Camera2D.aspectMode` when a current camera exists, or no push when no current camera exists or its bounds are degenerate). After the collider draws complete, the utility MUST pop the transform it pushed, restoring identity for any subsequent HUD draws. The FPS overlay SHALL be drawn in **screen space** (identity transform), so it appears at the same surface location regardless of camera bounds.

The host SHALL remain responsible for keeping `Debug.currentFps` up to date each frame, because the FPS counter requires `nanoTime` from the platform; the utility only **draws** the value already present in `Debug.currentFps`.

#### Scenario: Both flags off issues no draw calls

- **WHEN** `Debug.showFps = false` and `Debug.colliderVisualization = false` and `renderDebugOverlay(renderer, tree)` is invoked
- **THEN** the underlying `Renderer` receives no `drawText`, `drawRect`, `drawLine`, or `drawCircle` call from this function
- **AND** no `pushTransform` or `popTransform` call is issued by the function

#### Scenario: Utility signature accepts SceneTree

- **WHEN** the source of `renderDebugOverlay` is inspected
- **THEN** the second parameter type is `SceneTree`, not `Scene`
- **AND** no reference to the symbol `Scene` exists in the file

#### Scenario: FPS flag draws current FPS value in screen space

- **WHEN** `Debug.showFps = true`, `Debug.currentFps = 60f`, and `renderDebugOverlay(renderer, tree)` is invoked on a tree containing a current `Camera2D` whose `bounds` differ from `tree.size`
- **THEN** the renderer receives a `drawText` call whose text contains `"60"` and whose position places the overlay near the top-left corner of the surface
- **AND** the `drawText` runs under identity transform (no enclosing `pushTransform` from the utility wraps the FPS draw)

#### Scenario: Collider flag draws outlined bounds in world space

- **WHEN** `Debug.colliderVisualization = true`, the tree contains at least one active `BoxCollider`, the tree has a current `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `aspectMode = AspectMode.FIT`, the `tree.size = Vec2(1280f, 900f)`, and `renderDebugOverlay(renderer, tree)` is invoked against a recording `Renderer`
- **THEN** a `pushTransform(...)` call matching the camera's view transform is observed before any collider `drawRect`
- **AND** the renderer receives one `drawRect(_, _, filled = false)` call per active collider, each with the collider's world-space `bounds()` (un-scaled by the camera transform — the transform handles the scaling)
- **AND** a `popTransform()` call is observed after the last collider draw and before any HUD draw

#### Scenario: Collider flag with no current camera draws in identity space

- **WHEN** `Debug.colliderVisualization = true`, the tree has no current `Camera2D` (or a degenerate one), and `renderDebugOverlay(renderer, tree)` is invoked
- **THEN** no `pushTransform` or `popTransform` call is issued by the utility for the collider pass
- **AND** the collider `drawRect` calls still reach the renderer with each collider's world-space `bounds()`

#### Scenario: Utility is backend-agnostic

- **WHEN** the source file declaring `renderDebugOverlay` is inspected
- **THEN** the only `Renderer`-related symbol referenced is the `Renderer` SPI from `:engine`
- **AND** no import begins with `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, or `org.jetbrains.skiko.*`

#### Scenario: Both backends call the same utility

- **WHEN** `:engine-compose` and `:engine-skiko` are inspected
- **THEN** each integrating runtime calls `renderDebugOverlay(renderer, tree)` once per frame after `GameLoop.tick(...)`
- **AND** neither host duplicates the drawing logic locally
- **AND** neither host issues its own `pushTransform`/`popTransform` calls around the call (the utility manages its own transform stack)
