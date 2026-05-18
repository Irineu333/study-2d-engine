## MODIFIED Requirements

### Requirement: Math primitives

The engine SHALL provide value-type math primitives sufficient for 2D gameplay: `Vec2` (x, y as `Float`), `Rect` (origin and size), and `Transform` (position as `Vec2`, scale as `Vec2`, rotation as `Float` in radians). All primitives MUST be immutable data classes or equivalent; operations that "modify" them MUST return new instances. `Rect` MUST expose a `contains(point: Vec2): Boolean` operation that returns `true` when the given point lies inside the rectangle's axis-aligned bounds (inclusive on the origin edges, exclusive on the far edges).

#### Scenario: Vec2 arithmetic returns a new instance

- **WHEN** code evaluates `Vec2(1f, 2f) + Vec2(3f, 4f)`
- **THEN** the result is `Vec2(4f, 6f)`
- **AND** neither operand has been mutated

#### Scenario: Rect intersection detection

- **WHEN** two `Rect` instances overlap on both axes
- **THEN** `rectA.intersects(rectB)` returns `true`

#### Scenario: Rect non-intersection

- **WHEN** two `Rect` instances are disjoint on at least one axis
- **THEN** `rectA.intersects(rectB)` returns `false`

#### Scenario: Rect contains a point strictly inside

- **WHEN** a `Rect` at position `(10, 20)` with size `(30, 40)` is queried with `contains(Vec2(15f, 25f))`
- **THEN** the result is `true`

#### Scenario: Rect does not contain a point outside

- **WHEN** the same `Rect` is queried with `contains(Vec2(5f, 25f))`
- **THEN** the result is `false`

### Requirement: Renderer SPI

The engine SHALL define a `Renderer` interface used by `onRender` hooks. The interface MUST cover the operations needed by the sample games in this change: clearing the surface, drawing filled and outlined rectangles, drawing filled and outlined circles, drawing line segments, drawing text, and measuring text. The interface MUST NOT expose types from `androidx.compose.*` or any backend-specific package. The interface MUST be implementable without reflection or service loaders. The `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` operation MUST draw a straight segment between the two world-space points with the given stroke thickness. The `measureText(text: String, size: Float): Vec2` operation MUST return the bounding box (`Vec2(width, height)`) that `drawText` would produce for the same text and size, allowing callers to align text without backend-specific measurement.

#### Scenario: Engine module has no Compose dependency

- **WHEN** the `:engine` module is compiled
- **THEN** its build classpath contains no `androidx.compose.*` artifact

#### Scenario: Renderer is consumed only via the interface

- **WHEN** any class in `:engine` references `Renderer`
- **THEN** it depends only on the `Renderer` interface, not on any concrete backend implementation

#### Scenario: drawLine accepts arbitrary endpoints

- **WHEN** a node calls `renderer.drawLine(Vec2(0f, 0f), Vec2(100f, 100f), thickness = 2f, color = Color.WHITE)`
- **THEN** the backend draws a diagonal stroke between the two points with the requested thickness and color

#### Scenario: measureText reports the bounding box

- **WHEN** a node calls `renderer.measureText("hello", size = 22f)`
- **THEN** the result is a `Vec2` whose `x` is the rendered width and `y` is the rendered height of `drawText("hello", _, 22f, _)` in the same frame

### Requirement: Input SPI

The engine SHALL define an `Input` interface providing read-only access to current input state at tick time. The interface MUST allow querying whether a given key is currently pressed and the current pointer position. The interface MUST allow querying whether a given mouse button is currently pressed (`isMouseDown`) and whether it was pressed during the current tick (`wasMouseClicked`). The interface MUST define a `MouseButton` enum covering at least `Left`, `Right`, and `Middle`. The interface MUST NOT expose backend-specific event types. The interface MAY expose pressed/released edge events as boolean queries valid for the current tick.

#### Scenario: Engine module reads input only via the interface

- **WHEN** a node in `:engine` queries input state inside `onUpdate`
- **THEN** it does so through the `Input` interface
- **AND** no backend type leaks into the node's source

#### Scenario: Key state reflects current frame

- **WHEN** the game loop polls input at the start of a tick and the underlying backend reports key `K` as pressed
- **THEN** every `Input.isKeyDown(K)` call within that tick returns `true`

#### Scenario: Mouse button click is observable for exactly one tick

- **WHEN** the user presses the left mouse button between tick `N-1` and tick `N`
- **THEN** `Input.wasMouseClicked(MouseButton.Left)` returns `true` for every call within tick `N`
- **AND** returns `false` from tick `N+1` onward unless a new press occurs

#### Scenario: Mouse button held reads as down across ticks

- **WHEN** the user presses and holds the left mouse button across multiple ticks without releasing
- **THEN** `Input.isMouseDown(MouseButton.Left)` returns `true` for every tick during the hold
