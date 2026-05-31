## ADDED Requirements

### Requirement: TextMeasurer SPI for off-frame font metrics

`:engine` SHALL declare a `TextMeasurer` interface with `fun measureText(text: String, size: Float): Vec2`, returning the width and height a string would occupy when drawn at the given font size. `TextMeasurer` SHALL live in `:engine` (Kotlin pure) and MUST NOT expose any render/UI framework type, preserving the engine purity invariant. It is distinct from `Renderer.measureText` in that it is reachable **outside** a render frame.

`SceneTree` SHALL expose a nullable `textMeasurer: TextMeasurer?` field, defaulting to `null` and set by the host at startup. Engine code that needs text metrics outside a draw pass (e.g. `Label.localBounds()`) SHALL read `tree?.textMeasurer`.

#### Scenario: TextMeasurer measures off-frame

- **WHEN** a `TextMeasurer` is set on a `SceneTree` and `measureText("Hi", 12f)` is called outside any render frame
- **THEN** it SHALL return a non-zero `Vec2` matching what `drawText("Hi", _, 12f, _)` would rasterize at the same size

#### Scenario: SceneTree defaults to no measurer

- **WHEN** a `SceneTree` is constructed without a host wiring a measurer
- **THEN** `tree.textMeasurer` SHALL be `null`

#### Scenario: TextMeasurer leaks no render type

- **WHEN** the `:engine` module is compiled
- **THEN** `TextMeasurer` SHALL reference only `:engine` math types (`Vec2`) and no Skiko/LWJGL/AWT/Compose type
