## ADDED Requirements

### Requirement: Unified debug overlay rendering utility

The engine SHALL expose a utility function in `:engine` (e.g. `renderDebugOverlay(renderer: Renderer, scene: Scene)`) that performs the debug overlay drawing in a single place, consulting `Debug.showFps` and `Debug.colliderVisualization` and issuing the corresponding `Renderer` calls. The utility MUST be backend-agnostic: it MUST accept the `Renderer` SPI and MUST NOT reference any concrete backend type. The utility MUST be invokable by an integrating runtime (e.g. `:engine-compose`'s `ComposeHost`, `:engine-skiko`'s `SkikoHost`) after `GameLoop.tick(...)` and before the renderer is unbound, so the overlay appears on top of the scene's own render output. When both flags are `false`, the utility MUST issue zero draw calls.

The host SHALL remain responsible for keeping `Debug.currentFps` up to date each frame, because the FPS counter requires `nanoTime` from the platform; the utility only **draws** the value already present in `Debug.currentFps`.

#### Scenario: Both flags off issues no draw calls

- **WHEN** `Debug.showFps = false` and `Debug.colliderVisualization = false` and `renderDebugOverlay(renderer, scene)` is invoked
- **THEN** the underlying `Renderer` receives no `drawText`, `drawRect`, `drawLine`, or `drawCircle` call from this function

#### Scenario: FPS flag draws current FPS value

- **WHEN** `Debug.showFps = true` and `Debug.currentFps = 60f` and `renderDebugOverlay(renderer, scene)` is invoked
- **THEN** the renderer receives a `drawText` call whose text contains `"60"` and whose position places the overlay near the top-left corner of the surface

#### Scenario: Collider flag draws outlined bounds

- **WHEN** `Debug.colliderVisualization = true` and the scene contains at least one active `BoxCollider` and `renderDebugOverlay(renderer, scene)` is invoked
- **THEN** the renderer receives one `drawRect(_, _, filled = false)` call per active collider, each matching the collider's `bounds()`

#### Scenario: Utility is backend-agnostic

- **WHEN** the source file declaring `renderDebugOverlay` is inspected
- **THEN** the only `Renderer`-related symbol referenced is the `Renderer` SPI from `:engine`
- **AND** no import begins with `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, or `org.jetbrains.skiko.*`

#### Scenario: Both backends call the same utility

- **WHEN** `:engine-compose` and `:engine-skiko` are inspected
- **THEN** each integrating runtime calls `renderDebugOverlay(renderer, scene)` once per frame after `GameLoop.tick(...)`
- **AND** neither host duplicates the drawing logic locally
