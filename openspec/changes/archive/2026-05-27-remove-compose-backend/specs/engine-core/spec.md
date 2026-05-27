## RENAMED Requirements

- FROM: `### Requirement: Engine module has zero Compose dependency`
- TO: `### Requirement: Engine module has zero UI framework dependency`

## MODIFIED Requirements

### Requirement: Renderer SPI

The engine SHALL define a `Renderer` interface used by `onDraw` hooks. The interface MUST cover the operations needed by the sample games in this change: clearing the surface, drawing filled and outlined rectangles, drawing filled and outlined circles, drawing line segments, drawing filled polygons via `drawPolygon(points: List<Vec2>, color: Color)`, drawing text, and measuring text. The interface MUST NOT expose types from `androidx.compose.*`, `org.jetbrains.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, or any backend-specific package. The interface MUST be implementable without reflection or service loaders. The `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` operation MUST draw a straight segment between the two points (interpreted under the current transform stack) with the given stroke thickness. The `drawPolygon(points: List<Vec2>, color: Color)` operation MUST fill the polygon described by the vertex list (interpreted under the current transform stack) as a closed path; the implementation MAY assume the polygon is simple (non-self-intersecting) and convex-or-concave-without-holes. The `measureText(text: String, size: Float): Vec2` operation MUST return the bounding box (`Vec2(width, height)`) that `drawText` would produce for the same text and size, allowing callers to align text without backend-specific measurement. The `Color` type used by the renderer MUST be annotated with `@Serializable` (kotlinx.serialization) so it can be embedded as a property value in serialized scene files.

The interface SHALL additionally expose a 2D affine transform stack via two operations:

```kotlin
fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2)
fun popTransform()
```

`pushTransform(translation, rotation, scale)` MUST push a new entry onto an internal LIFO stack representing the composition `translate(translation) ∘ rotate(rotation) ∘ scale(scale)` applied to all subsequent `draw*` calls until the matching `popTransform()`. `rotation` MUST be expressed in radians and applied around the new origin (post-translation). Pushes MUST nest (composition order is parent-then-child: a deeper push composes with the current top). `popTransform()` MUST restore the top to the previous entry and SHALL throw `IllegalStateException` if the stack is empty.

The stack state SHALL start as identity at every backend-defined frame boundary (e.g. when `SkikoRenderer.bind()` runs). Every `pushTransform` issued during a frame MUST be matched by a `popTransform` before the renderer's frame boundary ends; the engine MUST NOT rely on cross-frame stack state.

#### Scenario: Engine module has no UI framework dependency

- **WHEN** the `:engine` module is compiled
- **THEN** its build classpath contains no `androidx.compose.*` artifact
- **AND** its build classpath contains no `org.jetbrains.compose.*` artifact
- **AND** its build classpath contains no `org.jetbrains.skia.*` or `org.jetbrains.skiko.*` artifact

#### Scenario: Renderer is consumed only via the interface

- **WHEN** any class in `:engine` references `Renderer`
- **THEN** it depends only on the `Renderer` interface, not on any concrete backend implementation

#### Scenario: drawLine accepts arbitrary endpoints

- **WHEN** a node calls `renderer.drawLine(Vec2(0f, 0f), Vec2(100f, 100f), thickness = 2f, color = Color.WHITE)`
- **THEN** the backend draws a diagonal stroke between the two points (under the current transform stack) with the requested thickness and color

#### Scenario: drawPolygon fills the polygon described by vertices

- **WHEN** a node calls `renderer.drawPolygon(listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(5f, 10f)), Color.WHITE)`
- **THEN** the backend renders a filled triangle covering those three vertices (under the current transform stack)
- **AND** subsequent calls with different vertex lists produce independent shapes (no state leakage)

#### Scenario: measureText reports the bounding box

- **WHEN** a node calls `renderer.measureText("hello", size = 22f)`
- **THEN** the result is a `Vec2` whose `x` is the rendered width and `y` is the rendered height of `drawText("hello", _, 22f, _)` in the same frame

#### Scenario: Color is serializable

- **WHEN** code serializes `Color(0.5f, 0.25f, 0.125f, 0.75f)` via `kotlinx.serialization` JSON
- **THEN** the resulting JSON document contains the four channel values
- **AND** deserializing yields a `Color` equal (by `equals`) to the original

#### Scenario: pushTransform translates subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2(100f, 50f), rotation = 0f, scale = Vec2(1f, 1f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the rendered rectangle appears at surface position `(100, 50)` with size `(10, 10)`

#### Scenario: pushTransform scales subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2.ZERO, rotation = 0f, scale = Vec2(2f, 2f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the rendered rectangle appears at surface position `(0, 0)` with size `(20, 20)`

#### Scenario: pushTransform rotates subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2.ZERO, rotation = (PI / 2f).toFloat(), scale = Vec2(1f, 1f))` then `renderer.drawLine(from = Vec2(0f, 0f), to = Vec2(10f, 0f), thickness = 1f, color = Color.WHITE)` then `renderer.popTransform()`
- **THEN** the rendered line endpoint that was `(10, 0)` in local space appears at surface position approximately `(0, 10)` within floating-point tolerance

#### Scenario: pushTransform composes translate, rotate, and scale in order

- **WHEN** code calls `renderer.pushTransform(translation = Vec2(50f, 0f), rotation = (PI / 2f).toFloat(), scale = Vec2(2f, 2f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the local origin `(0, 0)` maps to surface position `(50, 0)` (translation only)
- **AND** the local point `(10, 0)` maps to surface position approximately `(50, 20)` (scaled to `(20, 0)`, then rotated 90° around the new origin)

#### Scenario: popTransform restores the previous transform

- **WHEN** code calls `renderer.pushTransform(Vec2(100f, 0f), 0f, Vec2(1f, 1f))`, draws a rect at `(0, 0)`, calls `renderer.popTransform()`, then draws another rect at `(0, 0)`
- **THEN** the first rect appears at surface position `(100, 0)`
- **AND** the second rect appears at surface position `(0, 0)`

#### Scenario: popTransform on empty stack fails fast

- **WHEN** code calls `renderer.popTransform()` without a preceding `pushTransform`
- **THEN** an `IllegalStateException` is raised naming the empty-stack precondition

#### Scenario: Transform stack starts as identity each frame

- **WHEN** a new frame begins on the backend (e.g. `SkikoRenderer.bind(canvas)`)
- **THEN** a `drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, true)` issued before any `pushTransform` renders at surface position `(0, 0)` with size `(10, 10)`

### Requirement: Engine module has zero UI framework dependency

The `:engine` Gradle module SHALL declare no dependency on any UI or render framework artifact, directly or transitively. The prohibited list includes at minimum: `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, AWT/Swing types (`java.awt.*`, `javax.swing.*`) beyond what the standard library guarantees, and any future render backend (e.g. LWJGL, OpenGL, Vulkan bindings). This invariant SHALL be enforced by the module's `build.gradle.kts` and verified during code review. Backend-specific types only appear in their respective backend modules (`:engine-skiko` today; `engine-lwjgl` planned).

#### Scenario: Adding a Compose dependency to :engine is rejected

- **WHEN** a contributor adds `androidx.compose.foundation` or any `org.jetbrains.compose.*` artifact to `:engine`'s dependencies
- **THEN** code review blocks the change
- **AND** the contributor is directed to use the `Renderer`/`Input`/`GameHost` SPI in a separate backend module

#### Scenario: Adding a Skiko dependency to :engine is rejected

- **WHEN** a contributor adds `org.jetbrains.skiko:skiko-awt` or any `org.jetbrains.skia.*` artifact to `:engine`'s dependencies
- **THEN** code review blocks the change
- **AND** the contributor is directed to use the `Renderer`/`Input`/`GameHost` SPI; Skiko-specific code lives in `:engine-skiko`

### Requirement: GameHost SPI

The engine SHALL define a `GameHost` interface that represents the host of execution of a game: it owns a window/surface, drives the per-frame pulse, wires `Input` events from the platform into the engine, and runs the game loop until the host is closed. The interface MUST expose a single `run(tree: SceneTree, config: GameConfig)` operation. The operation MUST be blocking: it returns only after the host's window/surface has been closed by the user or by code. The interface MUST NOT expose backend-specific types in its method signatures; both inputs (`SceneTree`, `GameConfig`) and the return type live in `:engine`. The interface MUST be implementable without reflection or service loaders.

#### Scenario: Engine module reads no backend type to declare GameHost

- **WHEN** the `:engine` module is compiled
- **THEN** `GameHost` is declared with parameters whose types come only from `:engine`
- **AND** no import in the file declaring `GameHost` begins with `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, or `javax.swing.*`

#### Scenario: run blocks until the host is closed

- **WHEN** code calls `host.run(tree, config)` and the host opens a window
- **THEN** the call does not return while the window remains open
- **AND** the call returns after the window is closed by the user or by code

#### Scenario: SkikoHost implements GameHost

- **WHEN** code in `:games:pong` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is assignable to `GameHost`
- **AND** when code in `:games:tictactoe` instantiates `SkikoHost()` from `:engine-skiko`
- **THEN** the result is also assignable to `GameHost`
- **AND** every game in the project (Pong, Demos, Hello-World, Tic Tac Toe) obtains its `GameHost` implementation from `:engine-skiko`
