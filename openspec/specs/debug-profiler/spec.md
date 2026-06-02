# debug-profiler Specification

## Purpose

Per-phase frame profiler for the engine's tick. A frames-per-second number
gives an aggregate reading but not **where the frame's time goes**. This
capability times the four observable phases of `GameLoop.tick` (hitTest →
fixed-step physics loop → process → render) plus the whole-tick total, exposing
them through a per-`SceneTree` `FrameProfile` and a built-in `ProfilerWidget`
that smooths them with per-phase moving averages. The `ProfilerWidget` also
owns the engine's only frame-rate readout (an `fps` line sampled from its own
`nanoTime` counter), so there is no standalone `FpsWidget`. Instrumentation is
gated on the widget's `enabled` flag: disabled (the default), the tick runs its
original un-instrumented path with zero timing overhead.

## Requirements

### Requirement: SceneTree.debug owns a per-frame FrameProfile

`SceneTree.debug` SHALL expose a `FrameProfile` holding the most recent
measured duration (in nanoseconds) of each tick phase — `hitTestNanos`,
`physicsNanos`, `processNanos`, `renderNanos`, `totalNanos` — and
`physicsSteps: Int` (the number of fixed physics steps taken that frame).
The `FrameProfile` SHALL be runtime-only — not `@Serializable`, not
persisted, not shared across trees.

#### Scenario: FrameProfile exists per tree

- **WHEN** two distinct `SceneTree` instances are constructed
- **THEN** each SHALL own its own `FrameProfile`
- **AND** writing one tree's profile SHALL NOT affect the other's

### Requirement: GameLoop measures tick phases when profiling is enabled

`GameLoop.tick` SHALL, when profiling is enabled, wrap each phase with
`System.nanoTime()` and write the measured durations into the tree's
`FrameProfile`: the `hitTest`, `physics` (summed across every fixed-step
iteration that frame, with `physicsSteps` counting them), `process`, and
`render` phases, plus the `total` tick duration. When profiling is disabled,
`GameLoop.tick` SHALL take no timing measurements and SHALL incur no timing
overhead.

#### Scenario: Phases are recorded only when enabled

- **GIVEN** profiling disabled
- **WHEN** a tick runs
- **THEN** the `FrameProfile` SHALL NOT be updated with new timings
- **WHEN** profiling is then enabled and a tick runs
- **THEN** `hitTestNanos`, `processNanos`, `renderNanos`, and `totalNanos` SHALL all be populated for that frame

#### Scenario: Physics phase aggregates the fixed-step loop

- **GIVEN** profiling enabled and a frame whose accumulator drains three fixed physics steps
- **WHEN** the tick runs
- **THEN** `physicsSteps` SHALL be `3`
- **AND** `physicsNanos` SHALL be the summed duration across those three steps

#### Scenario: Total covers the whole tick

- **GIVEN** profiling enabled
- **WHEN** a tick runs
- **THEN** `totalNanos` SHALL be greater than or equal to the sum of the four phase durations (the remainder being per-tick overhead)

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
