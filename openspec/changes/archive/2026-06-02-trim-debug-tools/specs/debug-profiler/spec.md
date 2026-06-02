## MODIFIED Requirements

### Requirement: ProfilerWidget shows smoothed per-phase timings

The engine SHALL register a built-in `ProfilerWidget` (a `ScreenDebugWidget`,
default `enabled = false`) as a togglable HUD row, exposed as a convenience
field on `DebugRegistry`. Its `enabled` SHALL drive the `GameLoop`'s
profiling measurement. When enabled, it SHALL sample the `FrameProfile` each
frame into a per-phase moving-average window and SHALL display the smoothed
milliseconds per phase together with the latest frame's `physicsSteps`.
Flipping `enabled` from `false` to `true` SHALL reset the moving-average
windows so no stale averages from a previous enabled window are shown.

The `ProfilerWidget` SHALL additionally own a frame-rate counter sampled from
`System.nanoTime()` in `onProcess` (independent of the `FrameProfile`
instrumentation) and SHALL display an `fps NN` line at the top of its panel.
The fps line SHALL be shown as soon as the widget is enabled — including
before any per-phase sample has accumulated (the moving-average window is
empty) — so that opening the profiler immediately surfaces the frame rate
without requiring the heavy phase instrumentation to warm up. The engine SHALL
NOT ship a separate `FpsWidget`.

#### Scenario: Widget is a registered built-in driving measurement

- **WHEN** `SceneTree.start()` has completed
- **THEN** the `DebugRegistry` convenience field for the profiler SHALL be non-null
- **AND** it SHALL appear as a togglable row in the `DebugHud`
- **AND** enabling it SHALL cause `GameLoop.tick` to begin measuring phases

#### Scenario: fps line is shown independent of phase instrumentation

- **GIVEN** the profiler was just enabled and no `FrameProfile` sample has accumulated yet
- **WHEN** a frame is rendered against a recording `Renderer`
- **THEN** an `fps` line SHALL be drawn from the widget's own `nanoTime` counter
- **AND** the fps reading SHALL NOT depend on the per-phase moving-average window being non-empty

#### Scenario: No standalone FpsWidget exists

- **WHEN** `SceneTree.start()` has completed
- **THEN** no `FpsWidget` instance SHALL exist under `DebugLayer`
- **AND** `DebugRegistry` SHALL NOT expose an `fps` convenience field

#### Scenario: Enabling resets the moving-average windows

- **GIVEN** the profiler was previously enabled and accumulated samples, then disabled
- **WHEN** it is enabled again
- **THEN** the first displayed averages SHALL reflect only samples gathered since the latest enable, not the prior window

#### Scenario: Disabled profiler draws nothing and measures nothing

- **GIVEN** `ProfilerWidget.enabled = false`
- **WHEN** a frame is rendered against a recording `Renderer`
- **THEN** zero draw calls SHALL be attributed to the widget
- **AND** `GameLoop.tick` SHALL take no timing measurements
