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
2. Set `tree.debugHudKey = config.debugHudKey` once before the first frame so the engine's internal `DebugToggleNode` polls the configured key.
3. Wire platform services into the tree once before the first frame, alongside `tree.textMeasurer`: set `tree.audio = JavaSoundAudio()` (from `:engine-audio-javasound`). Failure to initialize the audio backend (e.g. headless/no sound device) MUST be tolerated — the host logs and leaves `tree.audio` as the no-op `null` rather than aborting `run`.
4. Add a `SkiaLayer` to the frame's content pane and set its `skikoView` to an object that, on each `onRender(canvas, width, height, nanoTime)` callback:
   a. Calls `input.beginTick()`.
   b. Calls `tree.resize(width.toFloat(), height.toFloat())`.
   c. Binds `renderer` to `canvas`.
   d. Calls `loop.tick(dtNanos)` — which itself runs `tree.hitTestUI(input)` before the physics/process/render phases.
   e. Unbinds the renderer.
   f. Calls `skiaLayer.needRedraw()` to drive the next frame.

   The host SHALL NOT instantiate `FpsCounter`. The host SHALL NOT write to `tree.debug.*` per frame (only the one-time `tree.debugHudKey` assignment in step 2). The host SHALL NOT call into any momentum overlay helper. The host SHALL NOT poll input for the purpose of toggling debug visualization. All debug visualization, including the FPS readout, the collider outlines, and the momentum sparklines, flows through `tree.render(renderer)` via the auto-inserted `DebugLayer` and its widgets.
5. Register AWT `KeyListener`, `MouseListener`, and `MouseMotionListener` on the `JFrame` (or the `SkiaLayer`) that delegate to the `SkikoInput`.
6. Block via a `CountDownLatch` (or equivalent) released by a `WindowListener.windowClosed` callback, so `run(...)` returns when the frame disposes. The `windowClosed` teardown calls `tree.stop()`, which disposes the audio backend.

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

- **WHEN** `SkikoHost` is running and `tree.debug.fps.enabled` is true
- **THEN** the FPS readout produced by `FpsWidget` updates at least once per second to a value greater than zero, indicating per-frame ticks are happening continuously
- **AND** the readout is produced inside `tree.render(renderer)`, not by the host

#### Scenario: SkikoHost honors debugHudKey via Input SPI

- **WHEN** `SkikoHost().run(tree, GameConfig(debugHudKey = Key.F1))` is running and the user presses F1
- **THEN** `tree.debug.hud.enabled` flips by the next rendered frame
- **AND** the HUD `Panel` appears (or disappears) accordingly
- **AND** no AWT `KeyListener` outside the engine wires the F1 behavior; the engine's `DebugToggleNode` polls input via the `Input` SPI

#### Scenario: SkikoHost does not draw debug overlays directly

- **WHEN** the source of `SkikoHost.kt` (and any helper it transitively calls during its `onRender` callback) is grep'd for `renderer.drawText`, `renderer.drawRect`, `renderer.drawLine`, `renderer.drawCircle`, `renderer.drawPolygon`, or any private `renderDebugOverlay(...)` helper
- **THEN** the only draw calls are issued from inside `loop.tick(...)` → `tree.render(renderer)` → walks of the scene graph
- **AND** the host's `onRender` body itself contains zero `renderer.draw*` calls before or after `loop.tick`

#### Scenario: SkikoHost source has no debug references beyond debugHudKey setup

- **WHEN** the source of `SkikoHost.kt` is grep'd for `FpsCounter`, `MomentumOverlay`, `tree.debug.show`, `tree.debug.current`, `toggleFpsKey`, `toggleCollidersKey`, or `toggleMomentumOverlayKey`
- **THEN** zero matches SHALL be returned
- **AND** the only `tree.debug` reference in the file SHALL be the one-time `tree.debugHudKey = config.debugHudKey` assignment during startup

#### Scenario: SkikoHost releases the JVM

- **WHEN** `SkikoHost.run(...)` returns after the user closes the window
- **THEN** no non-daemon thread launched by `SkikoHost` keeps the JVM alive
- **AND** `System.exit(...)` is not called by `SkikoHost`

#### Scenario: SkikoHost wires the audio backend into the tree

- **WHEN** `SkikoHost().run(tree, config)` starts and a sound device is available
- **THEN** `tree.audio` is set to a `JavaSoundAudio` instance before the first frame
- **AND** scripts and nodes can reach it via `node.tree.audio`
- **AND** when the window closes, `tree.stop()` disposes the audio backend

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

### Requirement: Skiko provides a TextMeasurer implementation wired at startup

`:engine-skiko` SHALL provide a concrete `TextMeasurer` implementation backed by Skia's `Font` + `TextLine`, reporting width and height consistent with `SkikoRenderer.measureText` for the same `(text, size)`. The implementation MUST NOT require a bound `Canvas` or an active render frame — it measures off-frame. The Skiko `GameHost`/startup path SHALL assign this measurer to `SceneTree.textMeasurer` before the first frame, so `Label.localBounds()` resolves correctly.

#### Scenario: Skiko measurer matches the renderer

- **WHEN** the Skiko `TextMeasurer.measureText(text, size)` and `SkikoRenderer.measureText(text, size)` are called with identical arguments
- **THEN** they SHALL return equal `Vec2` dimensions

#### Scenario: Startup wires the measurer onto the tree

- **WHEN** a game is launched on the Skiko backend
- **THEN** `tree.textMeasurer` SHALL be non-null before the first `render`, and a `Label` in the tree SHALL report a non-null `localBounds()`

#### Scenario: Measurement works without a bound canvas

- **WHEN** the Skiko `TextMeasurer.measureText` is called while no frame is bound
- **THEN** it SHALL return valid dimensions without throwing

### Requirement: SkikoRenderer implements the clip stack

`SkikoRenderer` SHALL implement the `Renderer` SPI clip stack over the Skia canvas's native save/restore. `pushClip(rect)` MUST issue `canvas.save()` then `canvas.clipRect(...)` for the given rect (interpreted under the current transform, i.e. on the same canvas matrix state), so the clip composes with any previously pushed clip on the canvas's own state stack. `popClip()` MUST issue `canvas.restore()`. The implementation MAY track a depth counter to raise `IllegalStateException` on empty-stack pop, but MUST otherwise delegate the stack to Skia's `save`/`restore` so backend-native culling and clipping behave correctly. Clip and transform pushes MUST share the same `save`/`restore` discipline so interleaved pushes nest correctly. `unbind()` MUST be invoked with the clip stack empty.

#### Scenario: Clipped draw is restricted to the rect

- **WHEN** `skikoRenderer.pushClip(Rect(Vec2(0f, 0f), Vec2(100f, 50f)))` is issued, then `skikoRenderer.drawRect(Rect(Vec2(0f, 0f), Vec2(200f, 200f)), Color.WHITE, true)`, then `skikoRenderer.popClip()` inside a bound frame
- **THEN** only pixels within `(0,0)..(100,50)` are written on the Skia canvas

#### Scenario: popClip without pushClip fails fast

- **WHEN** `skikoRenderer.popClip()` is called with an empty clip stack inside a bound frame
- **THEN** the call throws `IllegalStateException`

### Requirement: SkikoInput ingests the mouse wheel

`SkikoInput` SHALL populate `Input.scrollDelta` from AWT mouse-wheel events, and `SkikoHost` SHALL register a `java.awt.event.MouseWheelListener` on the Skiko component routing events into `SkikoInput`. Because the wheel callback fires on the AWT thread, the accumulator MUST be thread-safe (atomic or `@Volatile`), mirroring the existing key/button handling. The accumulated wheel motion MUST be drained into `scrollDelta` at `beginTick()` so it is observable for exactly the following tick, with positive `y` meaning scroll-down. `SkikoInput` SHALL reset `scrollDelta` to `Vec2.ZERO` and `scrollConsumed` to `false` at the start of each tick.

#### Scenario: Wheel-down produces a positive y delta for one tick

- **WHEN** the user rolls the wheel down and the AWT `MouseWheelListener` fires before tick `N`
- **THEN** `skikoInput.scrollDelta.y` is positive for every call within tick `N`
- **AND** `skikoInput.scrollDelta` reads `Vec2.ZERO` in tick `N+1` absent further wheel motion

#### Scenario: scrollConsumed resets each tick

- **WHEN** `scrollConsumed` was set to `true` during tick `N` and no wheel motion occurs in tick `N+1`
- **THEN** at the start of tick `N+1`, `skikoInput.scrollConsumed` equals `false`

### Requirement: SkikoRenderer implements drawImage with nearest-neighbor sampling

`SkikoRenderer` SHALL implement `Renderer.drawImage(texture, src, dst, flipH)` over the bound Skia `Canvas`. The implementation MUST treat `texture` as a `SkikoTexture` wrapping an `org.jetbrains.skia.Image`, drawing the `src` pixel rectangle into the `dst` rectangle under the current Skia matrix (the transform stack). Sampling MUST be **nearest-neighbor** (e.g. `SamplingMode` with `FilterMode.NEAREST`), never the default bilinear, so scaled pixel-art stays crisp. `flipH` MUST mirror the drawn region horizontally about the center of `dst`. A `Texture` that is not a `SkikoTexture` MUST fail fast with a descriptive exception.

#### Scenario: SkikoRenderer scales pixel-art crisply

- **WHEN** `SkikoRenderer.drawImage` draws a 16x16 texture region into a 64x64 `dst`
- **THEN** the result is a 4x nearest-neighbor upscale (hard pixel edges, no blur)

#### Scenario: SkikoRenderer rejects a foreign texture handle

- **WHEN** `SkikoRenderer.drawImage` is called with a `Texture` that is not a `SkikoTexture`
- **THEN** a descriptive exception is thrown

### Requirement: engine-skiko provides a Skia-backed TextureBackend wired at startup

The `:engine-skiko` module SHALL provide a `SkikoTextureBackend : TextureBackend` whose `Texture` implementation (`SkikoTexture`) wraps an `org.jetbrains.skia.Image` and exposes the image's `width`/`height`. `load(path)` MUST resolve the asset via the classpath, decode it once via Skia (e.g. `Image.makeFromEncoded`), cache the handle by path, and fail fast on a missing/unreadable asset. `dispose()` MUST close every cached Skia `Image`. `SkikoHost.run` MUST set `tree.textures = SkikoTextureBackend(...)` once before the first frame, alongside `tree.textMeasurer`/`tree.audio`; failure to initialize MUST be tolerated (log and leave `tree.textures` as `null`). The teardown path (`tree.stop()`) disposes it.

#### Scenario: SkikoHost wires the texture backend into the tree

- **WHEN** `SkikoHost().run(tree, config)` starts
- **THEN** `tree.textures` is set to a `SkikoTextureBackend` before the first frame
- **AND** nodes can reach it via `node.tree.textures`
- **AND** when the window closes, `tree.stop()` disposes the texture backend

#### Scenario: SkikoTexture reports the decoded image dimensions

- **WHEN** `SkikoTextureBackend.load` decodes a 352x32 PNG
- **THEN** the returned `Texture` reports `width == 352` and `height == 32`

### Requirement: engine-skiko keeps Skia confined to the backend module

`SkikoTexture`/`SkikoTextureBackend` MAY reference `org.jetbrains.skia.*` (they live in `:engine-skiko`), but `:engine` MUST NOT — the `Texture`/`TextureBackend` interfaces it consumes stay Skia-free.

#### Scenario: Skia image type does not leak into :engine

- **WHEN** the `:engine` module is compiled
- **THEN** no `:engine` source references `org.jetbrains.skia.Image` or any other Skia type for textures
