## MODIFIED Requirements

### Requirement: Input SPI

The engine SHALL define an `Input` interface providing read-only access to current input state at tick time. The interface MUST allow querying whether a given key is currently pressed and the current pointer position. The interface MUST allow querying whether a given mouse button is currently pressed (`isMouseDown`) and whether it was pressed during the current tick (`wasMouseClicked`). The interface MUST define a `MouseButton` enum covering at least `Left`, `Right`, and `Middle`. The interface MUST NOT expose backend-specific event types. The interface MAY expose pressed/released edge events as boolean queries valid for the current tick.

To support UI hit-testing, the interface MUST additionally expose:

- `wasMouseClickedRaw(button: MouseButton): Boolean` — returns the same value `wasMouseClicked` would have returned if no UI consumed the click. The raw query SHALL always reflect the bare hardware event.
- `var mouseClickConsumed: Boolean` — a writable flag set by the `SceneTree.hitTestUI(input)` phase when a `Button` (or other UI widget) absorbs the click. The flag SHALL be reset to `false` at the start of every tick (during `beginTick()` or equivalent).
- `wasMouseClicked(button)` SHALL return `false` whenever `mouseClickConsumed` is `true` for the same tick (left button in MVP; future buttons may follow the same pattern).

#### Scenario: Engine module reads input only via the interface

- **WHEN** a node in `:engine` queries input state inside `onUpdate`
- **THEN** it does so through the `Input` interface
- **AND** no backend type leaks into the node's source

#### Scenario: Key state reflects current frame

- **WHEN** the game loop polls input at the start of a tick and the underlying backend reports key `K` as pressed
- **THEN** every `Input.isKeyDown(K)` call within that tick returns `true`

#### Scenario: Mouse button click is observable for exactly one tick

- **WHEN** the user presses the left mouse button between tick `N-1` and tick `N`
- **THEN** `Input.wasMouseClicked(MouseButton.Left)` returns `true` for every call within tick `N` (assuming the click was not consumed by UI)
- **AND** returns `false` from tick `N+1` onward unless a new press occurs

#### Scenario: Mouse button held reads as down across ticks

- **WHEN** the user presses and holds the left mouse button across multiple ticks without releasing
- **THEN** `Input.isMouseDown(MouseButton.Left)` returns `true` for every tick during the hold

#### Scenario: Consumed click is invisible to wasMouseClicked

- **WHEN** the user clicks the left mouse button and `SceneTree.hitTestUI(input)` sets `input.mouseClickConsumed = true` during the same tick
- **THEN** `input.wasMouseClicked(MouseButton.Left)` SHALL return `false` for every subsequent call within that tick
- **AND** `input.wasMouseClickedRaw(MouseButton.Left)` SHALL return `true`

#### Scenario: mouseClickConsumed resets each tick

- **WHEN** a click was consumed during tick `N` (setting `mouseClickConsumed = true`) and no click occurs in tick `N+1`
- **THEN** at the start of tick `N+1`, `input.mouseClickConsumed` SHALL equal `false`

### Requirement: Game loop coordination

The engine SHALL provide a `GameLoop` class that, given a `SceneTree`, a `Renderer`, an `Input`, a `PhysicsSystem`, and a `physicsHz: Int` (default `60`), exposes a `tick(dtNanos: Long)` operation. Each `tick` MUST: (1) compute `dtFrame` in seconds; (2) run the UI hit-test phase `tree.hitTestUI(input)` before any other tree traversal; (3) accumulate `dtFrame` into an internal `accumulator` and, while `accumulator >= 1f / physicsHz` and steps `< maxStepsPerFrame` (`= 5`), execute a physics step: drain pending → `tree.physicsProcess(physicsDt)` → drain pending → `physics.step(tree)` → decrement accumulator; (4) drain pending and call `tree.process(dtFrame)`; (5) drain pending and call `tree.render(renderer)`. When the inner loop hits `maxStepsPerFrame` and accumulator is still above `physicsDt`, the engine MUST clamp `accumulator` to `0f` to prevent spiral-of-death. The `GameLoop` itself MUST NOT spawn threads or impose its own pacing; the host runtime supplies the pulse.

The host SHALL call `input.beginTick()` and execute any toggle-key polling before invoking `gameLoop.tick(...)`, so the UI hit-test phase observes a clean, up-to-date input state.

#### Scenario: Tick order is deterministic

- **WHEN** `gameLoop.tick(dtNanos)` is called and `dtNanos >= physicsDtNanos`
- **THEN** `tree.hitTestUI(input)` runs before any other tree phase
- **AND** at least one physics step runs before `tree.process(dtFrame)`
- **AND** `tree.process(dtFrame)` runs before `tree.render(renderer)`

#### Scenario: Physics step uses fixed dt

- **GIVEN** `physicsHz = 60`
- **WHEN** `gameLoop.tick(dtNanos)` triggers `n` physics steps
- **THEN** each `tree.physicsProcess(dt)` call sees `dt == 1f / 60f`
- **AND** each `physics.step(tree)` is paired one-to-one with a `tree.physicsProcess` call

#### Scenario: Process phase uses variable dt

- **WHEN** `gameLoop.tick(dtNanos)` is called with `dtNanos = 16_666_666L`
- **THEN** `tree.process(dt)` runs with `dt ≈ 0.01667`

#### Scenario: Sub-physics-step frames accumulate without running physics

- **GIVEN** `physicsHz = 60` (physics dt ≈ 16.67ms) and an empty accumulator
- **WHEN** `gameLoop.tick(8_333_333L)` is called (8.33ms frame)
- **THEN** no physics step runs in that tick
- **AND** `tree.process(dt)` still runs once with `dt ≈ 0.00833`
- **AND** `tree.hitTestUI(input)` still runs exactly once

#### Scenario: Multiple physics steps run in a long frame

- **GIVEN** `physicsHz = 60` and an empty accumulator
- **WHEN** `gameLoop.tick(50_000_000L)` is called (50ms frame)
- **THEN** between 2 and 3 physics steps execute in that tick (3 if accumulator reaches threshold three times)
- **AND** `tree.process(dt)` still runs exactly once at the end with `dt ≈ 0.050`
- **AND** `tree.hitTestUI(input)` still runs exactly once at the start

#### Scenario: Spiral-of-death clamps accumulator

- **GIVEN** the loop has just executed `maxStepsPerFrame` physics steps
- **AND** `accumulator > physicsDt` still
- **WHEN** the loop exits the inner step loop
- **THEN** `accumulator` is reset to `0f`
- **AND** the next call to `tick` does not catastrophically queue more steps

#### Scenario: Pending mutations drain between phases

- **WHEN** during `tree.physicsProcess(dt)` a node enqueues `addChild(spawn)` and during `physics.step(tree)` another node enqueues `removeChild(victim)`
- **THEN** `spawn.onEnter()` runs before `physics.step` begins
- **AND** `victim.onExit()` runs before subsequent traversals see it
- **AND** `tree.render` sees `spawn` and does not see `victim`

### Requirement: GameHost SPI

The engine SHALL define a `GameHost` interface that represents the host of execution of a game: it owns a window/surface, drives the per-frame pulse, wires `Input` events from the platform into the engine, and runs the game loop until the host is closed. The interface MUST expose a single `run(tree: SceneTree, config: GameConfig)` operation. The operation MUST be blocking: it returns only after the host's window/surface has been closed by the user or by code. The interface MUST NOT expose backend-specific types in its method signatures; both inputs (`SceneTree`, `GameConfig`) and the return type live in `:engine`. The interface MUST be implementable without reflection or service loaders.

`GameHost` implementations SHALL NOT issue any `renderer.draw*` calls outside of `tree.render(renderer)`. All visual output, including debug overlays, SHALL be produced by `SceneTree.render` walking the scene graph. Hosts MAY observe input state for toggle-key handling but SHALL write the result into `tree.debug.*` rather than maintaining their own draw pipeline.

#### Scenario: Engine module reads no backend type to declare GameHost

- **WHEN** the `:engine` module is compiled
- **THEN** `GameHost` is declared with parameters whose types come only from `:engine`
- **AND** no import in the file declaring `GameHost` begins with `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, `javax.swing.*`, or `org.lwjgl.*`

#### Scenario: run blocks until the host is closed

- **WHEN** code calls `host.run(tree, config)` and the host opens a window
- **THEN** the call does not return while the window remains open
- **AND** the call returns after the window is closed by the user or by code

#### Scenario: SkikoHost implements GameHost

- **WHEN** code in `:games:pong` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is assignable to `GameHost`
- **AND** when code in `:games:tictactoe` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is also assignable to `GameHost`
- **AND** every shipped game in the project (Pong, Demos, Hello-World, Tic Tac Toe) obtains its default `GameHost` implementation from `:engine-skiko`

#### Scenario: LwjglHost implements GameHost

- **WHEN** code in `:games:demos` (alternate entrypoint `MainLwjgl.kt`) instantiates `LwjglHost()` from `:engine-lwjgl`
- **THEN** the result is assignable to `GameHost`
- **AND** `:engine-lwjgl` is recognized as the second active render backend after `:engine-skiko`

#### Scenario: GameHost does not draw outside SceneTree.render

- **WHEN** the source of any `GameHost` implementation is inspected for direct uses of `renderer.drawText`, `renderer.drawRect`, `renderer.drawLine`, `renderer.drawCircle`, or `renderer.drawPolygon`
- **THEN** every such call SHALL occur transitively inside a `tree.render(renderer)` invocation, not in the host's frame body before or after the render call.

### Requirement: Toggle keys flip debug flags through the host

Every `GameHost` implementation SHALL, on each tick, observe `Input.wasKeyPressed(config.toggleFpsKey)`, `Input.wasKeyPressed(config.toggleCollidersKey)`, and `Input.wasKeyPressed(config.toggleMomentumOverlayKey)` and flip `tree.debug.showFps`, `tree.debug.showColliders`, and `tree.debug.showMomentum` respectively when a press is observed. This responsibility lives in the host so that game `Main.kt` files do not need to wire keyboard handlers outside the engine to control debug overlays.

The host SHALL NOT maintain a parallel `Debug` singleton or cache the flag state outside `tree.debug` — `tree.debug` is the single source of truth read by `DebugOverlayLayer` during `_process`.

#### Scenario: Pressing the configured FPS toggle flips tree.debug.showFps

- **WHEN** the user presses the key configured as `toggleFpsKey` while a `GameHost` is running a scene
- **THEN** `tree.debug.showFps` is flipped to its negation by the time the next frame is rendered
- **AND** the next frame either shows or hides the FPS overlay accordingly (driven by `DebugOverlayLayer`)

#### Scenario: Pressing the configured colliders toggle flips tree.debug.showColliders

- **WHEN** the user presses the key configured as `toggleCollidersKey` while a `GameHost` is running a scene
- **THEN** `tree.debug.showColliders` is flipped to its negation by the time the next frame is rendered

#### Scenario: Pressing the configured momentum toggle flips tree.debug.showMomentum

- **WHEN** the user presses the key configured as `toggleMomentumOverlayKey` while a `GameHost` is running a scene
- **THEN** `tree.debug.showMomentum` is flipped to its negation by the time the next frame is rendered

#### Scenario: Toggles never live in game code

- **WHEN** any `Main.kt` under `:games:` is inspected after this change
- **THEN** no file installs a keyboard handler outside the engine for the purpose of toggling `tree.debug.showFps`, `tree.debug.showColliders`, or `tree.debug.showMomentum`
- **AND** game code relies on the host to perform those toggles

### Requirement: SceneTree.render applies Node2D local transform per draw

`SceneTree.render(renderer)` SHALL execute exactly two passes per frame, in order:

1. **World pass** — collect the current `Camera2D` (if any), push the corresponding view transform via `renderer.pushTransform`, walk the tree DFS from `root` while skipping any `CanvasLayer` subtree entirely, and for each visited `Node2D` wrap its `onDraw` call within a matched `Renderer.pushTransform` / `Renderer.popTransform` pair derived from the node's **local** `Transform`. Pop the view transform at the end.
2. **UI pass** — collect every `CanvasLayer` reachable from `root` in DFS pre-order, sort them by `(layer ascending, dfs-discovery-order ascending)`, and for each `CanvasLayer` in that order: walk DFS into its subtree starting from identity transform, wrapping each `Node2D` descendant's `onDraw` in the same per-node push/pop pair.

Nodes that are NOT `Node2D` (e.g. `Timer`, the abstract `Node` base) SHALL NOT trigger any push/pop — they only forward to descendants. `CanvasLayer` itself, being a `Node` (not `Node2D`), SHALL NOT push a transform; it only establishes the identity baseline for the UI pass and its `transform`-less position in the tree. The push and pop MUST nest correctly around recursion into children; the implementation MUST use `try`/`finally` so a thrown exception inside `onDraw` or in a descendant still pops the stack.

#### Scenario: SceneTree.render pushes and pops local transform around each Node2D's draw

- **GIVEN** a live `SceneTree` whose `root` is a `ColorRect` named `R` with `transform.position = Vec2(50f, 50f)`, no children, no `CanvasLayer`, and no current camera
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** the recorded sequence contains exactly one `pushTransform(translation = Vec2(50f, 50f), rotation = 0f, scale = Vec2(1f, 1f))` and exactly one matching `popTransform()` enclosing R's draw call
- **AND** no other `pushTransform`/`popTransform` calls are observed for the frame (modulo the auto-inserted `DebugOverlayLayer`'s UI pass, which issues no draws when all `tree.debug.*` flags are false)

#### Scenario: Nested Node2D pushes compose via the transform stack

- **GIVEN** a live `SceneTree` whose `root` is a `Node2D` parent `P` with `transform.position = Vec2(100f, 0f)`, and `P` has one child `C` (a `ColorRect`) with `transform.position = Vec2(0f, 50f)`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** during the world pass the recorded sequence is: `pushTransform(Vec2(100f, 0f), 0f, Vec2(1f, 1f))`, `P.onDraw`, `pushTransform(Vec2(0f, 50f), 0f, Vec2(1f, 1f))`, `C.onDraw` issuing its `drawRect(...)`, `popTransform()`, `popTransform()`
- **AND** under that composed stack, C's `drawRect(Rect(Vec2.ZERO, ...))` appears on the surface at world position `Vec2(100f, 50f)`

#### Scenario: Non-Node2D nodes do not push a transform

- **GIVEN** a live `SceneTree` whose `root` is a `Node2D` `R`, and `R` has a child `T` which is a `Timer` (a non-`Node2D` `Node`)
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** exactly one `pushTransform`/`popTransform` pair appears (enclosing R's draw and subtree)
- **AND** no `pushTransform` is issued for `T`

#### Scenario: Per-Node2D push happens inside the camera view push during the world pass

- **GIVEN** a live `SceneTree` of `size = Vec2(800f, 600f)` containing a current `Camera2D` with valid bounds, and a single `ColorRect` `R` at world position `Vec2(0f, 0f)`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** the world pass recorded sequence is: `pushTransform(view, ...)` (camera), `pushTransform(Vec2(0f, 0f), 0f, Vec2(1f, 1f))` (R), `R.onDraw`, `popTransform()` (R), `popTransform()` (camera)

#### Scenario: CanvasLayer subtree is skipped during the world pass

- **GIVEN** a live `SceneTree` whose `root` is a `Node2D` `R` containing a child `CanvasLayer` `L`, which in turn contains a `Panel` `P`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** during the world pass the recording contains pushes/pops/draws for `R` but none for `P`
- **AND** during the UI pass the recording contains the push/pop/draw for `P` under identity (no camera view transform)

#### Scenario: CanvasLayer ordering respects layer then DFS discovery order

- **GIVEN** a live `SceneTree` containing three `CanvasLayer`s in DFS-discovery order `L1 (layer=0)`, `L2 (layer=10)`, `L3 (layer=0)`, each with a `Panel` child
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** during the UI pass the panels are drawn in order `L1.Panel`, `L3.Panel`, `L2.Panel` (layer 0 first, with DFS as tie-break; layer 10 last)

#### Scenario: Pop occurs even when onDraw throws

- **GIVEN** a `Node2D` subclass whose `onDraw` throws `RuntimeException` deterministically
- **WHEN** `tree.render(renderer)` runs and the exception propagates
- **THEN** the recording `Renderer` observes a matching `popTransform()` for every `pushTransform()` issued before the exception
- **AND** the renderer's transform stack is empty at the end of the frame
