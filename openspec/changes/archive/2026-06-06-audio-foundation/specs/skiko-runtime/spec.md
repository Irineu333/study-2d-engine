## MODIFIED Requirements

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

#### Scenario: SkikoHost wires the audio backend into the tree

- **WHEN** `SkikoHost().run(tree, config)` starts and a sound device is available
- **THEN** `tree.audio` is set to a `JavaSoundAudio` instance before the first frame
- **AND** scripts and nodes can reach it via `node.tree.audio`
- **AND** when the window closes, `tree.stop()` disposes the audio backend
