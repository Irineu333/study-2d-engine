# debug-time-controls Specification

## Purpose

Provide the most basic time controls of any game engine/debugger — pause,
step one frame at a time, and slow-motion / fast-forward — so collisions and
solver behavior can be inspected step by step. `SceneTree` exposes
`timeScale` / `paused` / `requestStep()` as first-class runtime state; the
`GameLoop` honors all three (with defaults preserving current behavior); and a
built-in `TimeControlWidget` surfaces pause, step, and speed controls in the
debug HUD, operable even while paused.

## Requirements

### Requirement: SceneTree exposes timeScale, paused, and a step request

`SceneTree` SHALL expose `var timeScale: Float` (default `1f`, coerced to
`>= 0f` in the setter), `var paused: Boolean` (default `false`), and a
single-use step request via `fun requestStep()`. These SHALL be runtime-only
state — not `@Serializable`, not persisted, not shared across trees. With
the defaults (`timeScale == 1f`, `paused == false`, no pending step), engine
behavior SHALL be unchanged from a tree without time controls.

#### Scenario: Defaults leave behavior unchanged

- **GIVEN** a freshly constructed `SceneTree`
- **THEN** `timeScale` SHALL be `1f`, `paused` SHALL be `false`, and no step SHALL be pending

#### Scenario: timeScale is clamped non-negative

- **WHEN** `tree.timeScale = -2f` is set
- **THEN** `tree.timeScale` SHALL be `0f`

### Requirement: GameLoop scales gameplay time by timeScale

`GameLoop.tick` SHALL compute the gameplay delta as `rawDt * tree.timeScale`
(when not paused) and SHALL accumulate that scaled delta for the fixed-step
physics loop, and derive the `_process` frame delta from it (clamped to
`maxDt`). `physics.step` SHALL continue to advance by the fixed `physicsDt`;
only the number of steps taken per frame SHALL vary with `timeScale`.

#### Scenario: Slow-motion runs fewer physics steps

- **GIVEN** a `rawDt` that would drain exactly four physics steps at `timeScale = 1`
- **WHEN** `tree.timeScale = 0.25f` and a tick runs
- **THEN** the number of `physics.step` calls SHALL be roughly one quarter (per the accumulator), not four

#### Scenario: Process frame delta is scaled

- **GIVEN** `tree.timeScale = 0.5f`
- **WHEN** a tick runs with raw frame delta `d` (below `maxDt`)
- **THEN** `tree.process` SHALL be invoked with `d * 0.5f`

### Requirement: Pause freezes gameplay while keeping process and UI alive

`GameLoop.tick` SHALL, when `tree.paused` is `true` (or the effective
gameplay delta is `0`), run no physics steps and invoke `tree.process(0f)`
rather than skipping `process`. It SHALL still run `hitTestUI(input)` and
`render`. As a result, gameplay nodes SHALL NOT advance, while debug nodes
that poll input and screen-space UI (the HUD, time-control buttons) SHALL
remain operable, and the frozen frame plus HUD SHALL still be drawn.

#### Scenario: Paused tree runs no physics but still processes at dt 0

- **GIVEN** `tree.paused = true` and bodies that would otherwise collide
- **WHEN** a tick runs
- **THEN** zero `physics.step` calls SHALL occur
- **AND** `tree.process` SHALL be invoked with `0f`
- **AND** `hitTestUI` and `render` SHALL still run

#### Scenario: HUD remains operable while paused

- **GIVEN** `tree.paused = true` and the `DebugHud` open
- **WHEN** the user clicks a HUD row
- **THEN** the click SHALL be handled (via `hitTestUI`) as if the tree were not paused

### Requirement: Step request advances exactly one physics step while paused

`GameLoop.tick` SHALL, when a step has been requested via `requestStep()`
and the tree is paused (or `timeScale == 0`), advance exactly one fixed
physics step — draining pending mutations, running `physicsProcess` and
`physics.step` once at `physicsDt`, then `process(physicsDt)` and `render` —
and SHALL clear the step request so a single `requestStep()` advances exactly
one step. When the tree is not paused, `requestStep()` SHALL be a no-op.

#### Scenario: One requestStep advances one step

- **GIVEN** `tree.paused = true` and `tree.requestStep()` has been called once
- **WHEN** the next tick runs
- **THEN** exactly one `physics.step` SHALL occur at `physicsDt`
- **AND** a subsequent tick with no new request SHALL run zero `physics.step` calls

#### Scenario: requestStep is a no-op when running

- **GIVEN** `tree.paused = false`
- **WHEN** `tree.requestStep()` is called
- **THEN** the following ticks SHALL behave exactly as if no step had been requested

### Requirement: TimeControlWidget exposes pause, step, and speed in the HUD

The engine SHALL register a built-in `TimeControlWidget` (a
`ScreenDebugWidget`, default `enabled = false`) as a togglable HUD row,
exposed as a convenience field on `DebugRegistry`. When enabled, it SHALL
display the current `paused` state and `timeScale`, and provide controls —
pause/resume, step, and stepping through speed presets (clamped at the ends,
no wrap) — that mutate
`tree.paused` / `tree.timeScale` / `requestStep()`. These controls SHALL be
operable while paused (driven through `hitTestUI`). Keyboard shortcuts for
the same actions SHALL be polled by an internal debug node that runs under
`process`, so they remain responsive while paused. The shortcuts SHALL fire
only while the widget is enabled, so their default bindings do not collide
with gameplay input when debug is closed.

#### Scenario: Shortcuts are inert while the widget is disabled

- **GIVEN** the time-control widget disabled (the production default)
- **WHEN** a time-control shortcut key is pressed
- **THEN** `tree.paused` / `tree.timeScale` SHALL be unchanged

#### Scenario: Widget is a registered built-in

- **WHEN** `SceneTree.start()` has completed
- **THEN** the `DebugRegistry` convenience field for the time-control widget SHALL be non-null
- **AND** it SHALL appear as a togglable row in the `DebugHud`

#### Scenario: Pause control works while paused

- **GIVEN** `tree.paused = true` and the time-control widget enabled
- **WHEN** the user activates its resume control
- **THEN** `tree.paused` SHALL become `false`

#### Scenario: Step control advances one step while paused

- **GIVEN** `tree.paused = true` and the time-control widget enabled
- **WHEN** the user activates its step control once
- **THEN** `requestStep()` SHALL be invoked and the next tick SHALL advance exactly one physics step
