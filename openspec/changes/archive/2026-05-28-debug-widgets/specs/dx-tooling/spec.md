## MODIFIED Requirements

### Requirement: Structured log with per-subsystem tags

The engine SHALL provide a logging facility usable from `:engine` and dependent modules, supporting four levels (`Debug`, `Info`, `Warn`, `Error`) and a `tag: String` per call site to identify the subsystem. The logger MUST emit timestamped output. The logger MUST allow the minimum effective level to be configured per tag and globally via `Log.config` (a `LogConfig` instance), without going through a separate `Debug` surface.

The `com.neoutils.engine.dx` package SHALL contain **only** logging utilities (`Log`, `LogConfig`, `LogLevel`, `LogSink`, `ConsoleLogSink`) after this change. All other DX surfaces (FPS counters, collider visualization, momentum overlay) live in `com.neoutils.engine.debug` and are covered by the `debug-overlay` capability.

#### Scenario: Log entries include tag, level, and timestamp

- **WHEN** code calls `Log.d(tag = "Physics", "step took 0.4ms")`
- **THEN** the emitted line contains the tag, the level, a timestamp, and the message

#### Scenario: Level filtering hides lower-priority entries

- **WHEN** `Log.config.globalLevel = LogLevel.Info` is set
- **THEN** subsequent `Log.d(...)` calls produce no output
- **AND** subsequent `Log.i(...)` calls produce output

#### Scenario: Per-tag filter overrides global level

- **WHEN** `Log.config.setTagLevel("Physics", LogLevel.Debug)` is set while `Log.config.globalLevel = LogLevel.Warn`
- **THEN** `Log.d(tag = "Physics", ...)` produces output
- **AND** `Log.d(tag = "Render", ...)` produces no output

#### Scenario: No standalone Debug object exists

- **WHEN** the source under `engine/src/main/kotlin/com/neoutils/engine/dx/` is grep'd for `object Debug`
- **THEN** no match SHALL be returned
- **AND** the only configuration surface for logging SHALL be `Log.config`

## REMOVED Requirements

### Requirement: FPS overlay togglable at runtime

**Reason**: Moved to the `debug-overlay` capability. The FPS overlay is now a `FpsWidget : ScreenDebugWidget` registered in `tree.debug` rather than a standalone DX surface. Its toggle lives in the `DebugHud` row labeled "FPS"; its rendering happens inside `tree.render(renderer)` via the auto-inserted `DebugLayer`. The `dx-tooling` capability no longer carries FPS-related requirements.

### Requirement: Collider debug visualization

**Reason**: Moved to the `debug-overlay` capability. Collider outlines are now drawn by `ColliderWidget : WorldDebugWidget` registered in `tree.debug` and toggled via the `DebugHud` row labeled "Colliders". The `Debug.colliderVisualization` flag no longer exists; the equivalent runtime access is `tree.debug.colliders.enabled`.

### Requirement: DX features togglable via a single debug surface

**Reason**: The `Debug` object is deleted. Logging configuration moves to `Log.config`; all visual debug toggles move to per-widget `enabled` flags surfaced through `tree.debug` (the `DebugRegistry`). There is no longer a single shared configuration surface across logging and visual debug — they are decoupled into two capabilities (`dx-tooling` for logging, `debug-overlay` for visual debug).

### Requirement: Unified debug overlay rendering utility

**Reason**: `renderDebugOverlay(renderer, tree)` was already retired by the `ui-foundation` change (which moved overlays into the scene graph as nodes inside `DebugOverlayLayer`). This delta finalizes the cleanup by removing the requirement from the spec. The current model — debug widgets as `Node`s under the auto-inserted `DebugLayer`, each owning its own `drawDebug` — replaces the host-callable utility entirely.
