## ADDED Requirements

### Requirement: SceneTree carries a design resolution and UI stretch mode

`SceneTree` SHALL expose a `designSize: Vec2` (the reference resolution UI is authored against) and a `uiStretchMode` with values `FIT`, `FILL`, `STRETCH`, and `DISABLED`. `designSize` SHALL default to the `bounds` size of the current `Camera2D` at startup when one exists, otherwise to the initial surface size (`GameConfig` width/height). Both properties SHALL be settable. `uiStretchMode` SHALL default to `FIT`.

When derived from a `Camera2D` (or set explicitly), `designSize` SHALL be a **stable** tree property: it SHALL NOT be recomputed per frame from the live camera, so a panning or zooming gameplay `Camera2D` does not disturb the UI. When derived from the surface because **no** `Camera2D` exists at startup, there is no fixed reference resolution, so `designSize` SHALL instead track the live surface size on resize — keeping the UI stretch identity (raw screen-space) at every window size rather than freezing the initial size and letterboxing the UI on resize. An explicit write to `designSize` freezes the value and stops surface tracking.

#### Scenario: no-camera designSize tracks the surface on resize

- **WHEN** a tree without any `Camera2D` is started on an `800x600` surface and the surface is then resized to `1024x768`
- **THEN** `tree.designSize` SHALL become `Vec2(1024f, 768f)` and the UI stretch transform SHALL remain `null` (identity), so the UI keeps behaving exactly as raw screen-space across the resize.

#### Scenario: designSize defaults from the current camera bounds

- **WHEN** a tree is built with a current `Camera2D` whose `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and the host starts it
- **THEN** `tree.designSize` SHALL equal `Vec2(800f, 600f)`.

#### Scenario: designSize defaults from the surface when no camera

- **WHEN** a tree without any `Camera2D` is started on an `800x600` surface
- **THEN** `tree.designSize` SHALL equal `Vec2(800f, 600f)`, so the stretch transform is identity and UI behaves exactly as raw screen-space.

#### Scenario: design resolution is stable under camera motion

- **WHEN** the gameplay `Camera2D` pans or changes zoom after startup
- **THEN** `tree.designSize` SHALL remain unchanged.

### Requirement: UI stretch transform maps design-space onto the surface without camera pan

`SceneTree` SHALL compute a **UI stretch transform** `(translation, scale)` that maps `Rect(Vec2.ZERO, designSize)` onto the surface `Rect(Vec2.ZERO, size)` under `uiStretchMode`, derived purely from `designSize`, `size`, and the mode. The transform SHALL include the resolution-adaptation **scale** and the **centering offset** of the letterbox, and SHALL NOT include any `Camera2D` position, pan, or zoom.

The `scale` SHALL be:
- `FIT`: uniform `min(size.x / designSize.x, size.y / designSize.y)`,
- `FILL`: uniform `max(size.x / designSize.x, size.y / designSize.y)`,
- `STRETCH`: per-axis `(size.x / designSize.x, size.y / designSize.y)`.

The `translation` SHALL be `((size.x - designSize.x * scale.x) / 2, (size.y - designSize.y * scale.y) / 2)` (centering the projected design rect on the surface). The transform SHALL be `null` (treated as identity, no push) when `uiStretchMode = DISABLED`, when `designSize` is degenerate (any component `<= 0`), or when it would equal identity (`designSize == size`).

#### Scenario: FIT letterboxes horizontally and centers

- **WHEN** `designSize = Vec2(800f, 600f)`, `size = Vec2(1200f, 600f)`, `uiStretchMode = FIT`
- **THEN** the UI stretch transform SHALL be `translation = Vec2(200f, 0f)`, `scale = Vec2(1f, 1f)`, so a UI child authored at design `(320, 24)` is drawn at screen `(520, 24)` — its letterbox bars matching the world pass under the same bounds.

#### Scenario: FIT scales content down on a smaller surface

- **WHEN** `designSize = Vec2(800f, 600f)`, `size = Vec2(400f, 300f)`, `uiStretchMode = FIT`
- **THEN** the UI stretch transform SHALL be `translation = Vec2(0f, 0f)`, `scale = Vec2(0.5f, 0.5f)`, so a `Label` with `fontSize = 48` is drawn at `24` px tall.

#### Scenario: identity when surface equals design

- **WHEN** `designSize == size`
- **THEN** the UI stretch transform SHALL be `null` (no transform pushed), preserving raw screen-space rendering.

#### Scenario: transform ignores camera pan

- **WHEN** the current `Camera2D` is panned so its view translation changes, with `designSize`, `size`, and `uiStretchMode` unchanged
- **THEN** the UI stretch transform SHALL be unchanged (no camera position term enters the computation).

### Requirement: CanvasLayer opts into the stretch via followStretch

`CanvasLayer` SHALL expose `followStretch: Boolean = true` as an `@Inspect` property. When `followStretch = true`, the layer's subtree SHALL be laid out, rendered, and hit-tested in **design-space** with the UI stretch transform applied. When `followStretch = false`, the layer SHALL render in **raw screen pixels** (identity), preserving the pre-change behavior for pixel-locked overlays.

#### Scenario: stretched layer follows the design resolution

- **WHEN** a `CanvasLayer` with `followStretch = true` contains a `Panel` at design `position = Vec2(50f, 50f)`, with `designSize = Vec2(400f, 300f)` and `size = Vec2(800f, 600f)` (FIT)
- **THEN** the `Panel` SHALL be drawn at screen `(100, 100)` (scaled 2x), tracking the world content under the same bounds.

#### Scenario: raw layer ignores the stretch

- **WHEN** a `CanvasLayer` with `followStretch = false` contains a `Panel` at `position = Vec2(50f, 50f)` on any surface
- **THEN** the `Panel` SHALL be drawn at raw screen pixels `(50, 50)`, regardless of `designSize`/`uiStretchMode`.

### Requirement: Debug screen canvas is immune to the stretch

The engine-inserted `DebugLayer`'s screen-space sub-container (`ScreenDebugCanvas`, the `CanvasLayer` at `layer = Int.MAX_VALUE - 1`) SHALL have `followStretch = false`, so the inspector, debug HUD, and debug panels remain in raw screen pixels and are unaffected by `designSize`/`uiStretchMode`.

#### Scenario: debug panels stay pixel-locked under a non-trivial stretch

- **WHEN** `designSize = Vec2(400f, 300f)` and `size = Vec2(800f, 600f)` (FIT scale 2x), with the debug HUD visible
- **THEN** the debug HUD and panels SHALL render at their raw screen pixel positions and sizes (no 2x scaling), unlike game `CanvasLayer`s.
