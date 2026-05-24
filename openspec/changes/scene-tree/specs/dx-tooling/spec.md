## MODIFIED Requirements

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
