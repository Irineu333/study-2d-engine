## ADDED Requirements

### Requirement: FPS overlay togglable at runtime

The engine SHALL provide an FPS overlay that, when enabled, renders the current frames-per-second value over the active scene without affecting the scene's own rendering output. The overlay MUST be togglable at runtime (default: disabled). The overlay MUST compute FPS from a moving average over a window of at least one second to avoid flicker.

#### Scenario: Enabling the overlay shows FPS

- **WHEN** the FPS overlay is enabled
- **THEN** the rendered frame includes a readable FPS value
- **AND** the value updates at least once per second

#### Scenario: Disabling the overlay removes it

- **WHEN** the FPS overlay is disabled
- **THEN** no FPS value is rendered
- **AND** scene rendering is unaffected

### Requirement: Structured log with per-subsystem tags

The engine SHALL provide a logging facility usable from `:engine` and dependent modules, supporting four levels (`Debug`, `Info`, `Warn`, `Error`) and a `tag: String` per call site to identify the subsystem. The logger MUST emit timestamped output. The logger MUST allow the minimum effective level to be configured per tag and globally.

#### Scenario: Log entries include tag, level, and timestamp

- **WHEN** code calls `Log.d(tag = "Physics", "step took 0.4ms")`
- **THEN** the emitted line contains the tag, the level, a timestamp, and the message

#### Scenario: Level filtering hides lower-priority entries

- **WHEN** the global minimum level is set to `Info`
- **THEN** subsequent `Log.d(...)` calls produce no output
- **AND** subsequent `Log.i(...)` calls produce output

#### Scenario: Per-tag filter overrides global level

- **WHEN** the `Physics` tag is configured to `Debug` while global level is `Warn`
- **THEN** `Log.d(tag = "Physics", ...)` produces output
- **AND** `Log.d(tag = "Render", ...)` produces no output

### Requirement: Collider debug visualization

The engine SHALL provide a debug-render mode in which all `Collider` nodes in the active scene have their `bounds()` drawn as outlined rectangles using a visually distinct color. The mode MUST be togglable at runtime (default: disabled). When disabled, no additional rendering overhead MUST be incurred beyond a single flag check per frame.

#### Scenario: Enabling collider debug draws bounds

- **WHEN** the collider debug mode is enabled and the scene contains a `BoxCollider`
- **THEN** the rendered frame includes a rectangle outline matching the collider's `bounds()`

#### Scenario: Disabling collider debug stops drawing bounds

- **WHEN** the collider debug mode is disabled
- **THEN** no collider outline is rendered
- **AND** scene rendering is otherwise unaffected

### Requirement: DX features togglable via a single debug surface

The engine SHALL expose DX toggles (FPS overlay, collider debug) through a single `Debug` configuration object accessible from game code and runtime code. Changes to the configuration MUST take effect by the next rendered frame.

#### Scenario: Toggling debug state propagates to next frame

- **WHEN** game code sets `Debug.colliderVisualization = true` during a tick
- **THEN** the next rendered frame includes collider outlines
