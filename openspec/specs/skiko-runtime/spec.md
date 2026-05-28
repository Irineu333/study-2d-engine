# skiko-runtime Specification

## Purpose

Backend Skiko puro da engine (sem Compose) — implementações de `Renderer`, `Input` e `GameHost` sobre `SkiaLayer` + `JFrame`. É o backend padrão para novos jogos e o único módulo de engine autorizado a depender diretamente de Skiko.

## Requirements

### Requirement: Skiko-based Renderer implementation

The `:engine-skiko` module SHALL provide a concrete `Renderer` implementation, `SkikoRenderer`, that translates engine drawing calls into `org.jetbrains.skia.Canvas` operations. `SkikoRenderer` MUST implement every method declared by the `Renderer` SPI in `:engine`, including `drawLine`, `measureText`, `pushTransform`, and `popTransform`. `SkikoRenderer` MUST NOT expose `Canvas`, `Paint`, or any other Skia type through the `Renderer` interface surface. `measureText` MUST use Skia's `Font` + `TextLine` so the reported width and height match what `drawText` will actually rasterize in the same frame.

The renderer MUST follow the same `bind(canvas) / unbind()` pattern as `ComposeRenderer`, so a single instance can be reused across frames without allocations and the engine's `Renderer.required()` guarantee survives. Color conversion from engine `Color(r, g, b, a)` to packed ARGB `Int` MUST round each channel to its 8-bit representation.

`SkikoRenderer.pushTransform(translation, rotation, scale)` MUST issue `canvas.save()` then `canvas.translate(translation.x, translation.y)` then `canvas.rotate(degrees)` (where `degrees = rotation * 180f / PI`) then `canvas.scale(scale.x, scale.y)` so the cumulative transform composes with any previously pushed transform on the Skia canvas's own state stack. The conversion to degrees is required because `org.jetbrains.skia.Canvas.rotate` expects degrees while the engine's `Transform.rotation` is in radians. `SkikoRenderer.popTransform()` MUST issue `canvas.restore()`. The implementation MAY track a depth counter for `IllegalStateException` on empty-stack pop, but MUST otherwise delegate the stack to Skia's `save`/`restore` semantics so backend-native culling and clipping behave correctly. `unbind()` MUST be invoked with the transform stack empty (every `pushTransform` matched by a `popTransform`); if not, the implementation MAY raise `IllegalStateException` to surface the imbalance early.

#### Scenario: drawRect issues a Skia draw call

- **WHEN** `skikoRenderer.drawRect(rect, color, filled = true)` is called inside a bound frame
- **THEN** the underlying Skia `Canvas` receives a filled rectangle of matching position, size, and color

#### Scenario: drawLine issues a stroke

- **WHEN** `skikoRenderer.drawLine(Vec2(10f, 20f), Vec2(110f, 120f), thickness = 3f, color = Color.WHITE)` is called inside a bound frame
- **THEN** the underlying Skia `Canvas` receives a line segment between the two points with stroke width approximately 3 pixels and the requested color

#### Scenario: drawText renders text at the requested position

- **WHEN** `skikoRenderer.drawText("42", Vec2(100f, 50f), size = 24f, color)` is called inside a bound frame
- **THEN** the rendered output displays `"42"` near `(100, 50)` and approximate point size 24

#### Scenario: measureText matches drawText output

- **WHEN** `skikoRenderer.measureText("score", size = 18f)` is called and `skikoRenderer.drawText("score", position, 18f, color)` runs in the same frame
- **THEN** the returned `Vec2.x` equals the rendered glyph run's width
- **AND** the returned `Vec2.y` equals the rendered glyph run's height
- **AND** both values are measured by the same Skia `Font` used by `drawText`

#### Scenario: Using the renderer outside a bound frame fails fast

- **WHEN** any `Renderer` method is called on `skikoRenderer` without a prior `bind(canvas)`
- **THEN** an `IllegalStateException` is raised with a message that names the missing `bind()` precondition

#### Scenario: pushTransform translates, rotates, and scales draws via Skia save/translate/rotate/scale

- **WHEN** `skikoRenderer.pushTransform(Vec2(100f, 50f), rotation = 0f, Vec2(2f, 2f))` is called and then `skikoRenderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` runs
- **THEN** the underlying Skia `Canvas` has received a `save()` followed by a `translate(100f, 50f)`, then a `rotate(0f)` (or equivalent no-op), then `scale(2f, 2f)` before the `drawRect`
- **AND** the rasterized rectangle occupies surface area equivalent to a rect at `(100, 50)` with size `(20, 20)`

#### Scenario: pushTransform with rotation rotates draws via canvas.rotate in degrees

- **WHEN** `skikoRenderer.pushTransform(Vec2.ZERO, rotation = (PI / 2f).toFloat(), Vec2(1f, 1f))` is called and then `skikoRenderer.drawLine(from = Vec2(0f, 0f), to = Vec2(10f, 0f), thickness = 1f, color = Color.WHITE)` runs
- **THEN** the underlying Skia `Canvas` has received a `save()` then `translate(0f, 0f)` then `rotate(90f)` (degrees, equal to `(PI / 2) * 180 / PI`) then `scale(1f, 1f)` before the `drawLine`
- **AND** the rasterized line endpoint that was `(10, 0)` in local space appears on the surface at approximately `(0, 10)` within floating-point tolerance

#### Scenario: popTransform issues canvas.restore

- **WHEN** `skikoRenderer.popTransform()` is called after a matching `pushTransform`
- **THEN** the underlying Skia `Canvas` receives a `restore()` call
- **AND** subsequent draws use the transform that was active before the matching `pushTransform`

### Requirement: Skiko-based Input implementation

The `:engine-skiko` module SHALL provide a concrete `Input` implementation, `SkikoInput`, that aggregates AWT `KeyEvent`, `MouseEvent`, and `MouseMotionEvent` callbacks into snapshot state queryable by the engine each tick. `SkikoInput` MUST translate AWT virtual key codes (`KeyEvent.VK_*`) into the engine's `Key` enum, mapping at minimum every key currently declared in `Key`, including `Key.F1` and `Key.F2`. `SkikoInput` MUST translate AWT mouse buttons (`MouseEvent.BUTTON1`/`BUTTON2`/`BUTTON3`) to `MouseButton.Left`/`Middle`/`Right` respectively, noting that AWT's button-number ordering differs from Compose's. `SkikoInput` MUST report pointer position in the same coordinate space used by `SkikoRenderer` for that frame. `SkikoInput` MUST use the same per-tick snapshot pattern as `ComposeInput`: a press observed between ticks is reported by `wasKeyPressed` / `wasMouseClicked` for exactly one tick.

#### Scenario: Key press is visible to the next tick

- **WHEN** the user presses a key registered with `SkikoInput`
- **THEN** the next `Input.isKeyDown(key)` query returns `true`
- **AND** continues to return `true` until the user releases the key

#### Scenario: F1 and F2 are mapped

- **WHEN** the user presses AWT `VK_F1` and AWT `VK_F2` in sequence
- **THEN** `Input.isKeyDown(Key.F1)` then `Input.isKeyDown(Key.F2)` return `true` respectively
- **AND** `wasKeyPressed(Key.F1)` and `wasKeyPressed(Key.F2)` return `true` on exactly one tick each following the press

#### Scenario: Left mouse button uses AWT BUTTON1

- **WHEN** the user presses the left mouse button (AWT `MouseEvent.BUTTON1`)
- **THEN** `Input.wasMouseClicked(MouseButton.Left)` returns `true` on the next tick
- **AND** `Input.isMouseDown(MouseButton.Left)` returns `true` until release

#### Scenario: Pointer position tracks the surface

- **WHEN** the user moves the pointer to surface coordinates `(x, y)`
- **THEN** `Input.pointerPosition` returns approximately `Vec2(x, y)` on the next tick
- **AND** the same coordinate maps to the same on-screen pixel that `Renderer.drawRect(Rect(Vec2(x, y), ...), ...)` would target

### Requirement: SkikoHost implements GameHost over SkiaLayer + JFrame

The `:engine-skiko` module SHALL provide a concrete `GameHost` implementation, `SkikoHost`, that hosts an `org.jetbrains.skiko.SkiaLayer` inside a Swing `JFrame`. The host's `run(tree, config)` MUST:

1. Create a `JFrame` with `title = config.title`, `setSize(config.width, config.height)`, `setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)`, and request focus.
2. Add a `SkiaLayer` to the frame's content pane and set its `skikoView` to an object that, on each `onRender(canvas, width, height, nanoTime)` callback:
   a. Calls `input.beginTick()`.
   b. Updates the `FpsCounter` and writes the result where `DebugOverlayLayer` (auto-inserted into `tree.root`) can read it for its `FpsLabel`.
   c. Calls `tree.resize(width.toFloat(), height.toFloat())`.
   d. Reads `Input.wasKeyPressed(config.toggleFpsKey)`, `Input.wasKeyPressed(config.toggleCollidersKey)`, and `Input.wasKeyPressed(config.toggleMomentumOverlayKey)` and flips `tree.debug.showFps`, `tree.debug.showColliders`, and `tree.debug.showMomentum` respectively.
   e. Binds `renderer` to `canvas`.
   f. Calls `loop.tick(dtNanos)` — which itself runs `tree.hitTestUI(input)` before the physics/process/render phases.
   g. Unbinds the renderer.
   h. Calls `skiaLayer.needRedraw()` to drive the next frame.

   The host SHALL NOT call any helper such as `renderDebugOverlay(renderer, tree)` after `loop.tick`. All debug overlay output flows through `tree.render(renderer)` via the auto-inserted `DebugOverlayLayer`.
3. Register AWT `KeyListener`, `MouseListener`, and `MouseMotionListener` on the `JFrame` (or the `SkiaLayer`) that delegate to the `SkikoInput`.
4. Block via a `CountDownLatch` (or equivalent) released by a `WindowListener.windowClosed` callback, so `run(...)` returns when the frame disposes.

`SkikoHost` MUST NOT call `System.exit(...)`, so callers that embed `SkikoHost.run(...)` inside a larger JVM process keep the JVM alive after the window closes.

#### Scenario: SkikoHost.run opens a Skiko window and blocks

- **WHEN** code calls `SkikoHost().run(SceneTree(root = pongRoot), GameConfig("Pong", 800, 600))`
- **THEN** a desktop window opens with title `"Pong"` and size 800 by 600
- **AND** the call does not return while the window remains open
- **AND** when the user closes the window, the call returns

#### Scenario: SkikoHost.run signature accepts SceneTree

- **WHEN** the source of `SkikoHost.run` is inspected
- **THEN** the parameter type is `SceneTree`, not `Scene`
- **AND** no reference to the symbol `Scene` exists in the file

#### Scenario: SkikoHost drives the loop via needRedraw

- **WHEN** `SkikoHost` is running and `tree.debug.showFps` is enabled
- **THEN** the FPS counter feeding `DebugOverlayLayer.FpsLabel` updates at least once per second to a value greater than zero, indicating per-frame ticks are happening continuously

#### Scenario: SkikoHost honors toggle keys via Input SPI

- **WHEN** `SkikoHost().run(tree, GameConfig(toggleFpsKey = Key.F1, toggleCollidersKey = Key.F2))` is running and the user presses F1
- **THEN** `tree.debug.showFps` flips by the next rendered frame
- **AND** the next frame's UI pass produces (or stops producing) the FPS overlay via `DebugOverlayLayer`
- **AND** no AWT `KeyListener` outside the engine is required for this behavior

#### Scenario: SkikoHost does not draw debug overlays directly

- **WHEN** the source of `SkikoHost.kt` (and any helper it transitively calls during its `onRender` callback) is grep'd for `renderer.drawText`, `renderer.drawRect`, `renderer.drawLine`, `renderer.drawCircle`, `renderer.drawPolygon`, or any private `renderDebugOverlay(...)` helper
- **THEN** the only draw calls are issued from inside `loop.tick(...)` → `tree.render(renderer)` → walks of the scene graph
- **AND** the host's `onRender` body itself contains zero `renderer.draw*` calls before or after `loop.tick`

#### Scenario: SkikoHost releases the JVM

- **WHEN** `SkikoHost.run(...)` returns after the user closes the window
- **THEN** no non-daemon thread launched by `SkikoHost` keeps the JVM alive
- **AND** `System.exit(...)` is not called by `SkikoHost`

### Requirement: Skiko-runtime module is a Compose-free engine boundary

The `:engine-skiko` Gradle module SHALL declare a dependency on Skiko (`org.jetbrains.skiko:skiko-awt` + a platform-classifier runtime artifact such as `skiko-awt-runtime-macos-arm64`) and on `:engine`, and SHALL NOT depend on any `org.jetbrains.compose.*` or `androidx.compose.*` artifact, directly or transitively. The platform-classifier runtime artifact MUST be resolved at build time based on `System.getProperty("os.name")` and `System.getProperty("os.arch")`. The Skiko version MUST be pinned in `gradle/libs.versions.toml` and consumed via the version catalog (the previous version-match constraint against `:engine-compose` no longer applies because that module is removed).

Game modules MAY depend on `:engine-skiko` to obtain the runtime they need; they MUST NOT re-export Skiko types in their own public API.

#### Scenario: Module graph respects the boundary

- **WHEN** `./gradlew :engine-skiko:dependencies` is run
- **THEN** no `org.jetbrains.compose.*` artifact appears in the resolved graph
- **AND** `org.jetbrains.skiko:skiko-awt` appears in the graph
- **AND** a `skiko-awt-runtime-<osArch>` artifact matching the build machine appears in the runtime classpath

#### Scenario: Skiko version comes from the version catalog

- **WHEN** the Skiko version resolved by `:engine-skiko` is inspected
- **THEN** it matches the version declared in `gradle/libs.versions.toml`
- **AND** there is no second `:engine-compose` module in the build whose transitive Skiko version could conflict
