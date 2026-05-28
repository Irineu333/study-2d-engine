## MODIFIED Requirements

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
