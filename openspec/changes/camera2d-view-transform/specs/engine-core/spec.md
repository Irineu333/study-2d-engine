## MODIFIED Requirements

### Requirement: Renderer SPI

The engine SHALL define a `Renderer` interface used by `onDraw` hooks. The interface MUST cover the operations needed by the sample games in this change: clearing the surface, drawing filled and outlined rectangles, drawing filled and outlined circles, drawing line segments, drawing filled polygons via `drawPolygon(points: List<Vec2>, color: Color)`, drawing text, and measuring text. The interface MUST NOT expose types from `androidx.compose.*` or any backend-specific package. The interface MUST be implementable without reflection or service loaders. The `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` operation MUST draw a straight segment between the two world-space points with the given stroke thickness. The `drawPolygon(points: List<Vec2>, color: Color)` operation MUST fill the polygon described by the vertex list interpreted as a closed path; the implementation MAY assume the polygon is simple (non-self-intersecting) and convex-or-concave-without-holes. The `measureText(text: String, size: Float): Vec2` operation MUST return the bounding box (`Vec2(width, height)`) that `drawText` would produce for the same text and size, allowing callers to align text without backend-specific measurement. The `Color` type used by the renderer MUST be annotated with `@Serializable` (kotlinx.serialization) so it can be embedded as a property value in serialized scene files.

The interface SHALL additionally expose a 2D affine transform stack via two operations:

```kotlin
fun pushTransform(translation: Vec2, scale: Vec2)
fun popTransform()
```

`pushTransform(translation, scale)` MUST push a new entry onto an internal LIFO stack representing the composition `translate(translation) ∘ scale(scale)` applied to all subsequent `draw*` calls until the matching `popTransform()`. Pushes MUST nest (composition order is parent-then-child: a deeper push composes with the current top). `popTransform()` MUST restore the top to the previous entry and SHALL throw `IllegalStateException` if the stack is empty.

The stack state SHALL start as identity at every backend-defined frame boundary (e.g. when `SkikoRenderer.bind()` runs or when a new `DrawScope` is entered in `ComposeRenderer`). Every `pushTransform` issued during a frame MUST be matched by a `popTransform` before the renderer's frame boundary ends; the engine MUST NOT rely on cross-frame stack state.

#### Scenario: Engine module has no Compose dependency

- **WHEN** the `:engine` module is compiled
- **THEN** its build classpath contains no `androidx.compose.*` artifact

#### Scenario: Renderer is consumed only via the interface

- **WHEN** any class in `:engine` references `Renderer`
- **THEN** it depends only on the `Renderer` interface, not on any concrete backend implementation

#### Scenario: drawLine accepts arbitrary endpoints

- **WHEN** a node calls `renderer.drawLine(Vec2(0f, 0f), Vec2(100f, 100f), thickness = 2f, color = Color.WHITE)`
- **THEN** the backend draws a diagonal stroke between the two points with the requested thickness and color

#### Scenario: drawPolygon fills the polygon described by vertices

- **WHEN** a node calls `renderer.drawPolygon(listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(5f, 10f)), Color.WHITE)`
- **THEN** the backend renders a filled triangle covering those three vertices
- **AND** subsequent calls with different vertex lists produce independent shapes (no state leakage)

#### Scenario: measureText reports the bounding box

- **WHEN** a node calls `renderer.measureText("hello", size = 22f)`
- **THEN** the result is a `Vec2` whose `x` is the rendered width and `y` is the rendered height of `drawText("hello", _, 22f, _)` in the same frame

#### Scenario: Color is serializable

- **WHEN** code serializes `Color(0.5f, 0.25f, 0.125f, 0.75f)` via `kotlinx.serialization` JSON
- **THEN** the resulting JSON document contains the four channel values
- **AND** deserializing yields a `Color` equal (by `equals`) to the original

#### Scenario: pushTransform translates subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2(100f, 50f), scale = Vec2(1f, 1f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the rendered rectangle appears at surface position `(100, 50)` with size `(10, 10)`

#### Scenario: pushTransform scales subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2.ZERO, scale = Vec2(2f, 2f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the rendered rectangle appears at surface position `(0, 0)` with size `(20, 20)`

#### Scenario: popTransform restores the previous transform

- **WHEN** code calls `renderer.pushTransform(Vec2(100f, 0f), Vec2(1f, 1f))`, draws a rect at `(0, 0)`, calls `renderer.popTransform()`, then draws another rect at `(0, 0)`
- **THEN** the first rect appears at surface position `(100, 0)`
- **AND** the second rect appears at surface position `(0, 0)`

#### Scenario: popTransform on empty stack fails fast

- **WHEN** code calls `renderer.popTransform()` without a preceding `pushTransform`
- **THEN** an `IllegalStateException` is raised naming the empty-stack precondition

#### Scenario: Transform stack starts as identity each frame

- **WHEN** a new frame begins on the backend (e.g. `SkikoRenderer.bind(canvas)` or a new `DrawScope` invocation)
- **THEN** a `drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, true)` issued before any `pushTransform` renders at surface position `(0, 0)` with size `(10, 10)`

### Requirement: Camera2D viewport carrier

The engine SHALL provide a `Camera2D : Node2D` class with `@Inspect var bounds: Rect` (the visible-world region in world coordinates), `@Inspect var current: Boolean = false` (whether this is the active camera), and `@Inspect var aspectMode: AspectMode = AspectMode.FIT` (how the world bounds map onto the surface when the aspect ratios differ). `AspectMode` SHALL be an enum with members `FIT`, `FILL`, and `STRETCH`. Setting `current = true` while live MUST cause `Scene.viewport` to reflect this camera's `bounds` until either `current` is set back to `false` or another `Camera2D` becomes current later in the tree. When multiple `Camera2D` nodes have `current = true`, the engine MUST pick the first one in pre-order traversal. `Camera2D` MUST be `@Serializable` and instantiable via no-args constructor, like every other `Node` shipped by `:engine`.

`Camera2D` SHALL additionally expose two pure coordinate-conversion helpers:

```kotlin
fun screenToWorld(screenPosition: Vec2, sceneSize: Vec2): Vec2
fun worldToScreen(worldPosition: Vec2, sceneSize: Vec2): Vec2
```

Both helpers MUST honor `bounds` and `aspectMode`: for `FIT` they use the uniform scale `min(sceneSize.x / bounds.size.x, sceneSize.y / bounds.size.y)` with centered offsets; for `FILL` they use `max(...)`; for `STRETCH` they use independent per-axis scales. The two helpers MUST be true inverses on the world rectangle covered by the visible viewport: `worldToScreen(screenToWorld(p, s), s)` MUST equal `p` within floating-point tolerance for any `p` inside the visible region. When `bounds.size.x` or `bounds.size.y` is `<= 0f`, both helpers MUST fall back to identity (return their argument unchanged) so caller code does not encounter division by zero.

#### Scenario: Camera2D is a Node2D with bounds, current, and aspect mode

- **WHEN** code instantiates `Camera2D()`
- **THEN** the result is a valid `Camera2D` with `current == false`, `bounds == Rect(Vec2.ZERO, Vec2.ZERO)`, and `aspectMode == AspectMode.FIT`
- **AND** assignable to `Node2D`

#### Scenario: First current Camera2D in pre-order wins

- **GIVEN** a scene with two `Camera2D` nodes both having `current = true`, one at root child position 0 and another deeper at position 2
- **WHEN** code reads `scene.viewport`
- **THEN** the bounds returned are from the camera at child position 0

#### Scenario: AspectMode FIT maps the world fully inside the surface

- **GIVEN** a `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `aspectMode = AspectMode.FIT`
- **WHEN** code calls `camera.worldToScreen(Vec2(0f, 0f), Vec2(1280f, 900f))` and `camera.worldToScreen(Vec2(800f, 600f), Vec2(1280f, 900f))`
- **THEN** both results lie inside the rectangle `Rect(Vec2.ZERO, Vec2(1280f, 900f))`
- **AND** the uniform scale applied is `min(1280f / 800f, 900f / 600f) = 1.5f`

#### Scenario: screenToWorld inverts worldToScreen

- **GIVEN** a `Camera2D` with `bounds = Rect(Vec2(0f, 0f), Vec2(800f, 600f))` and `aspectMode = AspectMode.FIT` and `sceneSize = Vec2(1280f, 900f)`
- **WHEN** code calls `camera.worldToScreen(p, sceneSize)` for any `p` inside `bounds`, then feeds the result back through `camera.screenToWorld(_, sceneSize)`
- **THEN** the round-trip returns a `Vec2` equal to `p` within `0.001f` tolerance

#### Scenario: Degenerate bounds fall back to identity

- **WHEN** code calls `camera.screenToWorld(Vec2(50f, 50f), Vec2(800f, 600f))` and `camera.worldToScreen(Vec2(50f, 50f), Vec2(800f, 600f))` on a `Camera2D` whose `bounds.size` has a zero or negative component
- **THEN** both calls return `Vec2(50f, 50f)` unchanged
- **AND** no exception is raised

### Requirement: Camera2D registers as the scene's current camera

When a `Camera2D` has `current = true` and is attached to a live `Scene`, the engine MUST make its `bounds` discoverable via `Scene.viewport`. The engine MUST resolve "the current camera" on read via pre-order tree-walk of the live scene picking the first `Camera2D` with `current = true`; the engine MAY cache this lookup but MUST invalidate on tree mutation or on `current` property changes.

`Scene.render(renderer)` SHALL consult the current camera at the start of the render traversal. When a current `Camera2D` exists with `bounds.size.x > 0f` and `bounds.size.y > 0f`, `Scene.render` MUST compute the view transform from `(camera.bounds, scene.size, camera.aspectMode)` and call `renderer.pushTransform(translation, scale)` BEFORE issuing any `_draw` walk, then call `renderer.popTransform()` AFTER the walk finishes (including via the `finally` of any traversal try/finally). When no current camera exists or its bounds are degenerate, `Scene.render` MUST NOT push any transform — the `_draw` walk runs against the identity transform (preserving the pre-change behavior of `pixels = world` for camera-less scenes).

`Scene` SHALL additionally expose two coordinate-conversion conveniences:

```kotlin
fun screenToWorld(screenPosition: Vec2): Vec2
fun worldToScreen(worldPosition: Vec2): Vec2
```

Both methods MUST delegate to the current `Camera2D`'s `screenToWorld` / `worldToScreen`, passing `scene.size` as the surface size argument. When no current camera exists (or its bounds are degenerate), both methods MUST return the input unchanged (identity fallback) — the same condition under which `Scene.render` skips its push, so nodes can read input pointer coordinates uniformly regardless of whether the scene has a camera.

#### Scenario: Toggling current updates viewport

- **GIVEN** a live scene with one `Camera2D` whose `current = false`, and `scene.viewport` returns `Rect(Vec2.ZERO, scene.size)`
- **WHEN** code sets `camera.current = true`
- **THEN** `scene.viewport` next read returns `camera.bounds`

#### Scenario: Scene.render with current camera pushes a transform

- **GIVEN** a live scene of `size = Vec2(1280f, 900f)` containing a `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))`, `current = true`, `aspectMode = AspectMode.FIT`, and a single `ColorRect` of `size = Vec2(800f, 600f)` at world `Vec2(0f, 0f)`
- **WHEN** `scene.render(renderer)` runs against a recording `Renderer` implementation
- **THEN** the first call observed is `pushTransform(...)` mapping `bounds` onto the surface via FIT
- **AND** the `ColorRect`'s `drawRect` call uses world coordinates `Rect(Vec2(0f, 0f), Vec2(800f, 600f))`
- **AND** the last call observed is `popTransform()`

#### Scenario: Scene.render without a current camera does not push a transform

- **GIVEN** a live scene with no `Camera2D` (or a `Camera2D` with `current = false`)
- **WHEN** `scene.render(renderer)` runs against a recording `Renderer` implementation
- **THEN** no `pushTransform` or `popTransform` call is observed during the traversal
- **AND** the `_draw` calls reach the renderer unchanged (identity transform)

#### Scenario: Scene.render with degenerate camera bounds falls back to identity

- **GIVEN** a live scene with a current `Camera2D` whose `bounds.size` has a zero or negative component
- **WHEN** `scene.render(renderer)` runs
- **THEN** no `pushTransform` or `popTransform` call is observed
- **AND** the `_draw` calls reach the renderer unchanged

#### Scenario: Scene.screenToWorld delegates to current camera

- **GIVEN** a live scene with `size = Vec2(1280f, 900f)` and a current `Camera2D` whose `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `aspectMode = AspectMode.FIT`
- **WHEN** code calls `scene.screenToWorld(Vec2(640f, 450f))` (the surface center)
- **THEN** the result equals `Vec2(400f, 300f)` (the world center inside `bounds`)
- **AND** `scene.worldToScreen(Vec2(400f, 300f))` round-trips back to `Vec2(640f, 450f)`

#### Scenario: Scene.screenToWorld identity without current camera

- **GIVEN** a live scene with no current `Camera2D` and `size = Vec2(800f, 600f)`
- **WHEN** code calls `scene.screenToWorld(Vec2(123f, 456f))` and `scene.worldToScreen(Vec2(123f, 456f))`
- **THEN** both calls return `Vec2(123f, 456f)` unchanged
