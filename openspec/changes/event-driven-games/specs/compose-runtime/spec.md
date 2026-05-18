## ADDED Requirements

### Requirement: GameSurface enqueues pointer events for engine dispatch

The `GameSurface` composable SHALL convert Compose pointer interactions into engine `PointerEvent` instances and enqueue them on the active scene's input system. Click events MUST be emitted on pointer release within the surface bounds when the press began inside the surface. Hover events SHOULD be enqueued only when the pointer position changes since the previous frame.

#### Scenario: Compose click produces engine click event

- **WHEN** the user presses and releases the primary mouse button at canvas position `(x, y)` within `GameSurface`
- **THEN** the scene receives a `PointerEvent.Click(Primary, Vec2(x, y))` on the next dispatch pass

#### Scenario: Secondary button click produces secondary engine event

- **WHEN** the user clicks the secondary mouse button at `(x, y)`
- **THEN** the scene receives `PointerEvent.Click(Secondary, Vec2(x, y))`

#### Scenario: Click outside surface is ignored

- **WHEN** the user clicks outside `GameSurface` bounds
- **THEN** no `PointerEvent.Click` is enqueued for that scene

### Requirement: Mouse button mapping is consistent with engine PointerButton enum

The Compose runtime SHALL map mouse button identifiers to `PointerButton` as follows: primary mouse button → `PointerButton.Primary`, secondary mouse button → `PointerButton.Secondary`, middle / tertiary → `PointerButton.Tertiary`. The mapping MUST be defined in a single place to avoid drift.

#### Scenario: Mapping is exhaustive for the three buttons

- **WHEN** any of the three buttons produces a Compose pointer event
- **THEN** it is translated to the corresponding `PointerButton`
- **AND** no engine code branches on Compose-specific button identifiers

### Requirement: GameSurface honors Scene.renderMode

The `GameSurface` composable SHALL consult `scene.renderMode` each frame. When the scene is `OnDemand`, `GameSurface` MUST skip the `GameLoop.tick` call for that frame if no wake condition holds (no queued input, no pending `requestRender`, no active animation). When the scene is `Continuous`, `GameSurface` MUST tick every frame as defined in the `engine-foundation` capability. `GameSurface` MUST still consume `withFrameNanos` to remain responsive to wake-ups produced between frames.

#### Scenario: OnDemand idle frame skips tick

- **WHEN** the scene is `OnDemand` with no wake condition for the current frame
- **THEN** `GameLoop.tick` is not invoked for that frame
- **AND** the canvas content from the previous frame remains displayed

#### Scenario: Continuous mode ticks every frame

- **WHEN** the scene is `Continuous` and the surface is visible
- **THEN** `GameLoop.tick` is invoked on every `withFrameNanos` callback

#### Scenario: Wake-up between frames triggers exactly one tick

- **WHEN** an input event arrives while an `OnDemand` scene is idle
- **THEN** the next `withFrameNanos` callback invokes `GameLoop.tick` exactly once
- **AND** subsequent idle frames do not tick until the next wake condition

### Requirement: GameSurface tracks hover position for the active scene

The `GameSurface` composable SHALL forward the latest hover/pointer position to the input system on each frame so that the engine's hit-test dispatch can deliver `PointerEvent.Hover` to the topmost interactive node when the position changes.

#### Scenario: Hover delivery aligns with renderer coordinates

- **WHEN** the pointer is at canvas coordinates `(x, y)` and the topmost interactive node at that position is `node`
- **THEN** `node` receives `onHover` with position approximately `Vec2(x, y)` on the next dispatch pass

### Requirement: Backward compatibility of GameSurface

`GameSurface(scene)` SHALL behave identically to the `engine-foundation` definition when the scene's `renderMode` is `Continuous` and no `Interactive` nodes are present. No existing call sites of `GameSurface` MUST need source changes to keep working.

#### Scenario: Pong's GameSurface usage is unchanged

- **WHEN** Pong's existing `main()` runs after this change
- **THEN** no source modification is required and Pong runs identically
