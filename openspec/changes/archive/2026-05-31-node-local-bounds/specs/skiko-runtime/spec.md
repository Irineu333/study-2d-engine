## ADDED Requirements

### Requirement: Skiko provides a TextMeasurer implementation wired at startup

`:engine-skiko` SHALL provide a concrete `TextMeasurer` implementation backed by Skia's `Font` + `TextLine`, reporting width and height consistent with `SkikoRenderer.measureText` for the same `(text, size)`. The implementation MUST NOT require a bound `Canvas` or an active render frame — it measures off-frame. The Skiko `GameHost`/startup path SHALL assign this measurer to `SceneTree.textMeasurer` before the first frame, so `Label.localBounds()` resolves correctly.

#### Scenario: Skiko measurer matches the renderer

- **WHEN** the Skiko `TextMeasurer.measureText(text, size)` and `SkikoRenderer.measureText(text, size)` are called with identical arguments
- **THEN** they SHALL return equal `Vec2` dimensions

#### Scenario: Startup wires the measurer onto the tree

- **WHEN** a game is launched on the Skiko backend
- **THEN** `tree.textMeasurer` SHALL be non-null before the first `render`, and a `Label` in the tree SHALL report a non-null `localBounds()`

#### Scenario: Measurement works without a bound canvas

- **WHEN** the Skiko `TextMeasurer.measureText` is called while no frame is bound
- **THEN** it SHALL return valid dimensions without throwing
