## MODIFIED Requirements

### Requirement: Compose-based Renderer implementation

The `:engine-compose` module SHALL provide a concrete `Renderer` implementation, `ComposeRenderer`, that translates engine drawing calls into Compose `DrawScope` operations. `ComposeRenderer` MUST implement every method declared by the `Renderer` SPI in `:engine`, including `drawLine` and `measureText`. `ComposeRenderer` MUST NOT expose `DrawScope` or any other Compose type through the `Renderer` interface surface. `measureText` MUST use Compose's `TextMeasurer` so the reported width and height match what `drawText` will actually rasterize.

#### Scenario: drawRect issues a Compose draw call

- **WHEN** `composeRenderer.drawRect(rect, color, filled = true)` is called inside a frame
- **THEN** the underlying `DrawScope` receives a filled rectangle of matching position, size, and color

#### Scenario: drawText renders text at the requested position

- **WHEN** `composeRenderer.drawText("42", Vec2(100f, 50f), size = 24f, color)` is called
- **THEN** the rendered output displays "42" with its baseline-anchored position near `(100, 50)` and approximate point size 24

#### Scenario: drawLine issues a Compose stroke

- **WHEN** `composeRenderer.drawLine(Vec2(10f, 20f), Vec2(110f, 120f), thickness = 3f, color = Color.WHITE)` is called inside a frame
- **THEN** the underlying `DrawScope` receives a line segment between the two points with stroke width approximately 3 pixels and the requested color

#### Scenario: measureText matches drawText output

- **WHEN** `composeRenderer.measureText("score", size = 18f)` is called and `composeRenderer.drawText("score", position, 18f, color)` runs in the same frame
- **THEN** the returned `Vec2` width and height equal the rendered glyph run's width and height in pixels (modulo subpixel rounding)

### Requirement: Compose-based Input implementation

The `:engine-compose` module SHALL provide a concrete `Input` implementation, `ComposeInput`, that aggregates Compose `KeyEvent` and `PointerEvent` callbacks into snapshot state queryable by the engine each tick. `ComposeInput` MUST translate Compose key codes into the engine's `Key` representation. `ComposeInput` MUST report mouse/pointer position in the same coordinate space used by the renderer. `ComposeInput` MUST track press/release events for at least the primary mouse button and expose them through `Input.isMouseDown(button)` and `Input.wasMouseClicked(button)`, using the same per-tick snapshot pattern already used for keys (a press observed between ticks is reported by `wasMouseClicked` for exactly one tick).

#### Scenario: Key press is visible to the next tick

- **WHEN** the user presses a key registered with `ComposeInput`
- **THEN** the next `Input.isKeyDown(key)` query returns `true`
- **AND** continues to return `true` until the user releases the key

#### Scenario: Pointer position tracks the canvas

- **WHEN** the user moves the pointer to canvas coordinates `(x, y)`
- **THEN** `Input.pointerPosition` returns approximately `Vec2(x, y)` on the next tick

#### Scenario: Left mouse button press surfaces as wasMouseClicked

- **WHEN** the user presses the left mouse button over the game canvas
- **THEN** `Input.wasMouseClicked(MouseButton.Left)` returns `true` on the next tick
- **AND** returns `false` on subsequent ticks until another press occurs

#### Scenario: Click coordinates match render coordinates

- **WHEN** the user clicks at canvas coordinate `(x, y)` and the same frame's render draws a marker at `(x, y)` via `Renderer.drawRect`
- **THEN** `Input.pointerPosition` read on that tick is approximately `Vec2(x, y)`
- **AND** a hit-test using `Rect.contains(input.pointerPosition)` on the marker's rect returns `true`
