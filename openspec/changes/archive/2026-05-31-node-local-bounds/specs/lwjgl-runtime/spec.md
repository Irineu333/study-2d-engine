## ADDED Requirements

### Requirement: LWJGL provides a TextMeasurer implementation wired at startup

`:engine-lwjgl` SHALL provide a concrete `TextMeasurer` implementation backed by NanoVG (`nvgTextBounds` + `nvgTextMetrics`), reporting width and height consistent with `LwjglRenderer.measureText` for the same `(text, size)`. Because NanoVG text APIs require the `nvgContext` and the registered font, the implementation MAY share the renderer's context but MUST be callable outside `nvgBeginFrame`/`nvgEndFrame` (off-frame). The LWJGL `GameHost`/startup path SHALL assign this measurer to `SceneTree.textMeasurer` before the first frame.

#### Scenario: LWJGL measurer matches the renderer

- **WHEN** the LWJGL `TextMeasurer.measureText(text, size)` and `LwjglRenderer.measureText(text, size)` are called with identical arguments
- **THEN** they SHALL return equal `Vec2` dimensions

#### Scenario: Startup wires the measurer onto the tree

- **WHEN** a game is launched on the LWJGL backend (e.g. the `:games:demos` LWJGL entrypoint)
- **THEN** `tree.textMeasurer` SHALL be non-null before the first `render`, and a `Label` in the tree SHALL report a non-null `localBounds()`

#### Scenario: Measurement works outside a frame

- **WHEN** the LWJGL `TextMeasurer.measureText` is called while no `nvgBeginFrame` is active
- **THEN** it SHALL return valid dimensions without corrupting frame state
