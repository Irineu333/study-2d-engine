## ADDED Requirements

### Requirement: Typed signals as engine primitive

The engine SHALL provide a `Signal<T>` type that nodes use to emit typed events. Each signal MUST support `emit(value: T)` and `connect(handler: (T) -> Unit): Connection`. `connect` MUST return a `Connection` instance exposing `disconnect()` that removes the handler. Signals SHALL invoke connected handlers synchronously, on the same thread, in the order they were connected. Signals with no payload MUST be representable as `Signal<Unit>` (or an equivalent `SignalUnit` alias).

#### Scenario: Connected handler receives emitted payload

- **WHEN** code calls `signal.connect { value -> received = value }` and later `signal.emit(42)`
- **THEN** `received` equals `42`

#### Scenario: Disconnect stops further deliveries

- **WHEN** a handler is connected, then its returned `Connection.disconnect()` is called, and `emit(x)` runs afterwards
- **THEN** the disconnected handler is not invoked
- **AND** any other still-connected handler is invoked

#### Scenario: Handlers run in connection order

- **WHEN** handlers A, B, C are connected to a signal in that order and `emit(x)` runs
- **THEN** A runs before B, and B runs before C

#### Scenario: Signal lookup is type-checked at the call site

- **WHEN** a developer attempts `signalOfInt.emit("text")`
- **THEN** the code does not compile

### Requirement: Scene render mode controls tick scheduling

The engine SHALL expose `enum class RenderMode { Continuous, OnDemand }` and a mutable property `Scene.renderMode` with default value `Continuous`. When the scene's mode is `OnDemand`, the engine MUST NOT advance ticks unless at least one of the following is true since the last tick: (a) at least one input event is queued for delivery to the scene, (b) `scene.requestRender()` has been called, (c) at least one node has registered an in-progress animation. When the mode is `Continuous`, ticks MUST advance every frame as before. Changing `renderMode` at runtime MUST take effect by the next frame.

#### Scenario: OnDemand idle scene does not tick

- **WHEN** a scene has `renderMode = OnDemand`, no queued input, no `requestRender` call, and no active animation
- **THEN** the engine does not invoke `update` or `render` on that scene for that frame

#### Scenario: OnDemand wakes on input

- **WHEN** an input event is delivered to an `OnDemand` scene
- **THEN** the engine performs exactly one tick for that scene
- **AND** returns to idle if no further wake condition holds afterwards

#### Scenario: requestRender forces a single tick

- **WHEN** code calls `scene.requestRender()` while the scene is `OnDemand`
- **THEN** the engine performs exactly one tick on the next frame
- **AND** the dirty flag is cleared at the start of that tick

#### Scenario: Continuous behaves as before

- **WHEN** a scene has `renderMode = Continuous`
- **THEN** the engine ticks every frame regardless of input or dirty state

### Requirement: Pointer event dispatch with hit-test

The engine SHALL define `PointerEvent` as a sealed type covering at least `Click(button: PointerButton, position: Vec2)`, `Hover(position: Vec2)`. The engine SHALL define `enum class PointerButton { Primary, Secondary, Tertiary }`. The engine SHALL define an `Interactive` interface implementable by `Node2D` subclasses, with hooks `onClick(event: PointerEvent.Click)`, `onRightClick(event: PointerEvent.Click)`, and `onHover(event: PointerEvent.Hover)`, each with empty default implementations. Before propagating `onUpdate` each tick, the engine MUST dispatch queued pointer events: for each event, the engine MUST iterate the scene tree in reverse render order (topmost visible first) and deliver the event to the first `Interactive` node whose world-space `bounds()` contains the event position. Invisible nodes (`Node2D.visible = false`, where applicable) MUST be skipped.

#### Scenario: Click hits topmost interactive node

- **WHEN** a `PointerEvent.Click(Primary, p)` is queued and two interactive nodes A (lower in render order) and B (higher) both contain `p`
- **THEN** B receives `onClick` for that event
- **AND** A does not receive `onClick`

#### Scenario: Right-click routes to onRightClick

- **WHEN** a `PointerEvent.Click(Secondary, p)` reaches an interactive node
- **THEN** `onRightClick` is invoked
- **AND** `onClick` is not invoked for that event

#### Scenario: No interactive hit produces no callback

- **WHEN** a click happens at a position not contained by any interactive node's bounds
- **THEN** no `onClick`/`onRightClick` callback is invoked
- **AND** the event is dropped silently

#### Scenario: Hover delivered to topmost interactive node

- **WHEN** a `PointerEvent.Hover(p)` is queued
- **THEN** the topmost interactive node containing `p` receives `onHover`

### Requirement: Input SPI exposes mouse buttons distinctly

The `Input` SPI SHALL allow consumers to query both mouse-button-aware events (via the pointer event dispatch above) and continuous button state when needed (e.g., `Input.isButtonDown(button: PointerButton): Boolean`). The default `Input` query methods MUST distinguish at least primary and secondary buttons.

#### Scenario: Primary and secondary button states are independent

- **WHEN** the primary button is held and the secondary button is not
- **THEN** `Input.isButtonDown(Primary)` returns `true`
- **AND** `Input.isButtonDown(Secondary)` returns `false`

### Requirement: Grid<T> utility for board-style games

The engine SHALL provide a `Grid<T>` data structure with constructor `Grid(rows: Int, cols: Int, init: (Int, Int) -> T)` and operations: `get(row: Int, col: Int): T`, `set(row: Int, col: Int, value: T)`, `forEachIndexed((row, col, value) -> Unit)`, and `neighbors(row: Int, col: Int, includeDiagonals: Boolean): Sequence<Triple<Int, Int, T>>`. Out-of-bounds access on `get`/`set` MUST throw a clear, named exception.

#### Scenario: Grid initializes via lambda

- **WHEN** a `Grid<Int>(2, 3) { r, c -> r * 10 + c }` is created
- **THEN** `grid.get(1, 2)` equals `12`

#### Scenario: neighbors excludes the center cell

- **WHEN** `grid.neighbors(row, col, includeDiagonals = true)` is called for an interior cell
- **THEN** the returned sequence contains 8 cells
- **AND** does not contain `(row, col)`

#### Scenario: neighbors with diagonals false returns 4 cardinals

- **WHEN** `grid.neighbors(row, col, includeDiagonals = false)` is called for an interior cell
- **THEN** the returned sequence contains 4 cells
- **AND** they are the up, down, left, and right neighbors

#### Scenario: Out-of-bounds access throws

- **WHEN** `grid.get(-1, 0)` is called
- **THEN** a named exception (e.g., `GridOutOfBoundsException`) is thrown

### Requirement: Backward compatibility for existing engine API

All additions described above SHALL be aditive. Code written against the `engine-foundation` engine API MUST continue to compile and behave identically when no new feature is opted into. In particular: scenes without an explicit `renderMode` MUST default to `Continuous`; nodes that do not implement `Interactive` MUST NOT receive pointer event callbacks; signals MUST NOT be created or emitted implicitly on existing nodes.

#### Scenario: Pong continues to run unchanged

- **WHEN** the project containing this change is built and `./gradlew :games:pong:run` is invoked
- **THEN** Pong runs identically to its `engine-foundation` behavior, with no source modifications required
