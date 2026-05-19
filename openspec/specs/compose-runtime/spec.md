# compose-runtime Specification

## Purpose

Backend Compose Multiplatform da engine — implementações de `Renderer` e `Input` sobre Compose, `GameSurface` composable que integra o game loop em `withFrameNanos`. Único módulo de engine autorizado a depender de Compose.

## Requirements

### Requirement: Compose-based Renderer implementation

The `:engine-compose` module SHALL provide a concrete `Renderer` implementation, `ComposeRenderer`, that translates engine drawing calls into Compose `DrawScope` operations. `ComposeRenderer` MUST implement every method declared by the `Renderer` SPI in `:engine`, including `drawLine` and `measureText`. `ComposeRenderer` MUST NOT expose `DrawScope` or any other Compose type through the `Renderer` interface surface. `measureText` MUST use Compose's `TextMeasurer` so the reported width and height match what `drawText` will actually rasterize.

#### Scenario: drawRect issues a Compose draw call

- **WHEN** `composeRenderer.drawRect(rect, color, filled = true)` is called inside a frame
- **THEN** the underlying `DrawScope` receives a filled rectangle of matching position, size, and color

#### Scenario: drawText renders text at the requested position

- **WHEN** `composeRenderer.drawText("42", Vec2(100f, 50f), size = 24f, color)` is called
- **THEN** the rendered output displays "42" with its baseline-anchored position near `(100, 50)` and approximate point size 24

#### Scenario: drawLine issues a Compose stroke

- **WHEN** `composeRenderer.drawLine(Vec2(10f, 20f), Vec2(110f, 120f), thickness = 3f, color = Color.WHITE)` is called inside a frame
- **THEN** the underlying `DrawScope` receives a line segment between the two points with stroke width approximately 3 pixels and the requested color

#### Scenario: measureText matches drawText output

- **WHEN** `composeRenderer.measureText("score", size = 18f)` is called and `composeRenderer.drawText("score", position, 18f, color)` runs in the same frame
- **THEN** the returned `Vec2` width and height equal the rendered glyph run's width and height in pixels (modulo subpixel rounding)

### Requirement: Compose-based Input implementation

The `:engine-compose` module SHALL provide a concrete `Input` implementation, `ComposeInput`, that aggregates Compose `KeyEvent` and `PointerEvent` callbacks into snapshot state queryable by the engine each tick. `ComposeInput` MUST translate Compose key codes into the engine's `Key` representation. `ComposeInput` MUST report mouse/pointer position in the same coordinate space used by the renderer. `ComposeInput` MUST track press/release events for at least the primary mouse button and expose them through `Input.isMouseDown(button)` and `Input.wasMouseClicked(button)`, using the same per-tick snapshot pattern already used for keys (a press observed between ticks is reported by `wasMouseClicked` for exactly one tick).

#### Scenario: Key press is visible to the next tick

- **WHEN** the user presses a key registered with `ComposeInput`
- **THEN** the next `Input.isKeyDown(key)` query returns `true`
- **AND** continues to return `true` until the user releases the key

#### Scenario: Pointer position tracks the canvas

- **WHEN** the user moves the pointer to canvas coordinates `(x, y)`
- **THEN** `Input.pointerPosition` returns approximately `Vec2(x, y)` on the next tick

#### Scenario: Left mouse button press surfaces as wasMouseClicked

- **WHEN** the user presses the left mouse button over the game canvas
- **THEN** `Input.wasMouseClicked(MouseButton.Left)` returns `true` on the next tick
- **AND** returns `false` on subsequent ticks until another press occurs

#### Scenario: Click coordinates match render coordinates

- **WHEN** the user clicks at canvas coordinate `(x, y)` and the same frame's render draws a marker at `(x, y)` via `Renderer.drawRect`
- **THEN** `Input.pointerPosition` read on that tick is approximately `Vec2(x, y)`
- **AND** a hit-test using `Rect.contains(input.pointerPosition)` on the marker's rect returns `true`

### Requirement: GameSurface composable drives the game loop

The `:engine-compose` module SHALL provide a `GameSurface(scene: Scene)` composable that hosts the rendering canvas and drives ticks via `withFrameNanos`. Each frame, `GameSurface` MUST: (1) snapshot input state, (2) invoke `GameLoop.tick(dtNanos)`, (3) trigger redraw of its canvas with the produced visual state. `GameSurface` MUST stop ticking when removed from composition.

#### Scenario: GameSurface ticks once per Compose frame

- **WHEN** `GameSurface(scene)` is placed in a Compose tree displayed at 60 frames per second
- **THEN** `GameLoop.tick` is invoked approximately 60 times per second

#### Scenario: Ticks stop on composition removal

- **WHEN** the `GameSurface` composable leaves composition
- **THEN** no further `GameLoop.tick` calls occur for that scene

#### Scenario: First tick uses sensible delta

- **WHEN** `GameSurface(scene)` is composed for the first time
- **THEN** the first `tick` receives a `dtNanos` value greater than zero
- **AND** does not propagate a spike caused by initial frame timing (clamped to a reasonable maximum)

### Requirement: Compose-runtime module is the only Compose-aware engine boundary

The `:engine-compose` Gradle module SHALL be the only engine-level module that declares a dependency on Compose Multiplatform. Game modules MAY depend on `:engine-compose` to obtain the runtime they need; they MUST NOT re-export Compose types in their own public API.

#### Scenario: Module graph respects the boundary

- **WHEN** the project graph is inspected
- **THEN** `:engine` has no Compose dependency
- **AND** `:engine-compose` depends on `:engine` and on Compose Multiplatform
- **AND** game modules depend on `:engine` and `:engine-compose` only

### Requirement: GameSurface applies collider debug overlay

The `:engine-compose` `GameSurface` SHALL be responsible for rendering the debug overlay when `Debug.colliderVisualization` or `Debug.showFps` is enabled. After invoking `loop.tick(...)` for a frame, and before releasing the `DrawScope`, `GameSurface` MUST invoke `renderDebugOverlay(renderer, scene)` from `:engine` so the overlay drawing is centralized and identical across backends. `GameSurface` MUST NOT duplicate the drawing logic locally. When both `Debug` flags are `false`, `GameSurface` MUST issue no extra draw calls beyond the scene's own render output and a single flag-check inside `renderDebugOverlay`.

#### Scenario: Overlay appears when collider flag is enabled

- **WHEN** `Debug.colliderVisualization = true` and `GameSurface` renders a frame for a scene containing at least one active `BoxCollider`
- **THEN** the rendered frame includes an outlined rectangle matching the collider's `bounds()`
- **AND** the rectangle is drawn after the scene's own `onRender` calls (i.e., on top of game visuals)

#### Scenario: Overlay disappears when both flags are disabled

- **WHEN** `Debug.colliderVisualization = false` and `Debug.showFps = false`
- **THEN** `GameSurface` issues no overlay draw calls for that frame
- **AND** the scene renders identically to a build without the DX surface

#### Scenario: Toggle key flips state by the next frame

- **WHEN** the user presses the configured FPS or colliders toggle key between ticks
- **THEN** the next frame rendered by `GameSurface` reflects the new flag state

#### Scenario: Drawing logic is not duplicated

- **WHEN** the source of `GameSurface.kt` is inspected
- **THEN** the only overlay-drawing code is a single call to `renderDebugOverlay(renderer, scene)`
- **AND** no inline `for (collider in collectColliders(scene)) renderer.drawRect(...)` or inline FPS `drawText` survives in this file

### Requirement: ComposeHost implements GameHost over GameSurface

The `:engine-compose` module SHALL provide a concrete `GameHost` implementation, `ComposeHost`, that opens a Compose `application { Window { } }`, hosts a `GameSurface` composable inside it, and returns from `run(...)` only after the Window is closed. `ComposeHost` MUST honor `GameConfig.title`, `GameConfig.width`, and `GameConfig.height` to size and label the Compose `Window`. `ComposeHost` MUST consume `GameConfig.toggleFpsKey` and `GameConfig.toggleCollidersKey` via the `Input` SPI inside the running scene, so toggling debug overlays does not require game code to install a Compose `onKeyEvent` handler. The existing `GameSurface(scene)` composable MUST remain exported for callers that need to embed the engine inside a larger Compose tree, but `ComposeHost` SHALL be the recommended entry point.

#### Scenario: ComposeHost.run opens a Compose window and blocks

- **WHEN** code calls `ComposeHost().run(scene, GameConfig("Tic Tac Toe", 600, 600))`
- **THEN** a Compose desktop Window opens with title `"Tic Tac Toe"` and size 600 by 600
- **AND** the call does not return while the Window remains open
- **AND** when the user closes the Window, the call returns

#### Scenario: ComposeHost honors toggle keys via Input SPI

- **WHEN** `ComposeHost().run(scene, GameConfig(toggleFpsKey = Key.F1, toggleCollidersKey = Key.F2))` is running and the user presses F1
- **THEN** `Debug.showFps` flips by the next rendered frame
- **AND** no game-side Compose `onKeyEvent` handler is required for this behavior

#### Scenario: GameSurface stays exported for advanced embedding

- **WHEN** code in `:games:tictactoe` (or any module) imports `com.neoutils.engine.compose.GameSurface`
- **THEN** the import resolves
- **AND** `GameSurface(scene, modifier)` can be placed inside a custom Compose tree
