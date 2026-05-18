## ADDED Requirements

### Requirement: Global RenderMode override for debugging

The engine SHALL provide a `Debug.renderModeOverride: RenderMode?` configuration. When non-null, this value MUST take precedence over each scene's own `renderMode` at the runtime level. A runtime-accessible toggle (keyboard shortcut bound by the runtime, default `F8`) SHALL cycle the override between `null`, `Continuous`, and `OnDemand`.

#### Scenario: Override forces Continuous globally

- **WHEN** `Debug.renderModeOverride = RenderMode.Continuous` is set while a scene's own mode is `OnDemand`
- **THEN** the runtime ticks that scene every frame
- **AND** clearing the override (`null`) restores the scene's own behavior on the next frame

#### Scenario: F8 cycles override

- **WHEN** the user presses `F8` repeatedly within a running game window
- **THEN** the override cycles through `null → Continuous → OnDemand → null`
- **AND** the current value is logged at `Info` level under tag `DX`

### Requirement: Signal emission logging

The engine SHALL provide `Debug.logSignals: Boolean` (default `false`). When true, every `Signal.emit(value)` call MUST produce a `Log.d(tag = "Events", ...)` entry containing the signal's identifier (best-effort name), the payload's `toString()`, and the emitter node's name when available. When false, signal emission MUST produce no additional logging overhead beyond a single flag check.

#### Scenario: Enabled logging records emissions

- **WHEN** `Debug.logSignals = true` and a signal emits a payload
- **THEN** a `Debug`-level log line is produced under tag `Events`
- **AND** the line contains the payload's string representation

#### Scenario: Disabled logging produces no entries

- **WHEN** `Debug.logSignals = false` and signals are emitted
- **THEN** no `Events`-tagged log entries are produced for those emissions

### Requirement: Debug surface includes the new toggles

The `Debug` configuration object SHALL expose `renderModeOverride` and `logSignals` alongside the existing `showFps` and `colliderVisualization` flags. Changes MUST take effect by the next frame.

#### Scenario: All toggles visible on Debug

- **WHEN** code inspects the `Debug` configuration object
- **THEN** the properties `showFps`, `colliderVisualization`, `renderModeOverride`, and `logSignals` are all reachable through the same surface
