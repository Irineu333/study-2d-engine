## ADDED Requirements

### Requirement: Sibling node names are unique with auto-suffix

The engine SHALL enforce uniqueness of `Node.name` among the children of any given parent. When `parent.addChild(child)` is invoked (either directly or via pending-drain) and `parent` already has a child whose `name` equals `child.name`, the engine MUST mutate `child.name` by appending `_2`, `_3`, ... until the resulting name is unique among `parent.children`. The mutated name MUST become the canonical name of `child` for the remainder of its lifetime, including subsequent `findChild` lookups and `NodeRef` resolutions. Removal of a child SHALL NOT renumber surviving siblings.

#### Scenario: Auto-suffix on first conflict

- **GIVEN** a parent already containing a child named `Ball`
- **WHEN** code calls `parent.addChild(other)` where `other.name == "Ball"`
- **THEN** `other.name` becomes `Ball_2`
- **AND** both children are present in `parent.children`

#### Scenario: Auto-suffix increments past existing suffixed siblings

- **GIVEN** a parent containing children named `Ball`, `Ball_2`
- **WHEN** code calls `parent.addChild(another)` where `another.name == "Ball"`
- **THEN** `another.name` becomes `Ball_3`

#### Scenario: Names without conflict are preserved

- **GIVEN** a parent with no child named `Paddle`
- **WHEN** code calls `parent.addChild(paddle)` where `paddle.name == "Paddle"`
- **THEN** `paddle.name` remains `Paddle`

#### Scenario: Suffix survives detach

- **GIVEN** a child whose name was auto-suffixed to `Ball_2`
- **WHEN** the child is removed from its parent
- **THEN** the child's `name` remains `Ball_2`

#### Scenario: Removing a child does not renumber siblings

- **GIVEN** a parent with children named `Ball`, `Ball_2`, `Ball_3`
- **WHEN** code calls `parent.removeChild(ballChild)` (the one named `Ball`)
- **THEN** the surviving children's names remain `Ball_2` and `Ball_3`

### Requirement: findChild looks up a direct child by name

The engine SHALL expose `Node.findChild(name: String): Node?` that returns the direct child of the receiver whose `name` equals `name`, or `null` if no such child exists. The lookup MUST be a single-level search (not recursive). The lookup MAY be `O(n)` in the number of children.

#### Scenario: findChild returns the matching child

- **GIVEN** a parent with children named `A`, `B`, `C`
- **WHEN** code calls `parent.findChild("B")`
- **THEN** the result is the child named `B`

#### Scenario: findChild returns null for missing name

- **GIVEN** a parent with children named `A`, `B`
- **WHEN** code calls `parent.findChild("Z")`
- **THEN** the result is `null`

#### Scenario: findChild does not descend into grandchildren

- **GIVEN** a parent with child `A` that itself has a child named `Target`
- **WHEN** code calls `parent.findChild("Target")`
- **THEN** the result is `null`

### Requirement: Serializable Node classes have no-args public constructors

Every concrete `Node` subclass shipped by `:engine` (i.e. `Node2D`, `Shape`, `Text`, `BoxCollider`) SHALL provide a public no-args primary constructor with sensible defaults. All initial configuration that previously lived in constructor parameters SHALL be exposed as `var` properties on the class instead. Each such property SHALL be annotated either with `@Inspect` (when it is part of the serialized contract) or with `@Transient` (when it is internal runtime state). The class itself SHALL be annotated with `@Serializable` (kotlinx.serialization). DX-oriented factory functions (e.g. `fun shape(kind: Kind, ...)`) MAY exist on the side to reduce verbosity, but they MUST NOT be the only path to instantiate the class.

#### Scenario: Concrete Node2D classes can be instantiated with no arguments

- **WHEN** code evaluates `Shape()`, `Text()`, `BoxCollider()`, `Node2D()`
- **THEN** each call returns a valid instance with default property values

#### Scenario: Configuration is set via mutable properties

- **WHEN** code instantiates `Shape()` and then sets `shape.kind = Shape.Kind.Circle` and `shape.size = Vec2(20f, 20f)`
- **THEN** subsequent reads of `shape.kind` and `shape.size` reflect those values

#### Scenario: Every var property on a serializable Node is annotated

- **WHEN** any class in `:engine` that extends `Node` and is annotated `@Serializable` is inspected
- **THEN** every `var` property on the class is annotated either with `@Inspect` or with `@Transient`

## MODIFIED Requirements

### Requirement: Math primitives

The engine SHALL provide value-type math primitives sufficient for 2D gameplay: `Vec2` (x, y as `Float`), `Rect` (origin and size), and `Transform` (position as `Vec2`, scale as `Vec2`, rotation as `Float` in radians). All primitives MUST be immutable data classes or equivalent; operations that "modify" them MUST return new instances. All primitives MUST be annotated with `@Serializable` (kotlinx.serialization) so they can be transparently embedded as property values in serialized scene files. `Rect` MUST expose a `contains(point: Vec2): Boolean` operation that returns `true` when the given point lies inside the rectangle's axis-aligned bounds (inclusive on the origin edges, exclusive on the far edges). `Transform` MUST expose a pure `compose(child: Transform): Transform` (or equivalent operator) such that, given a parent transform `P` and a child transform `C` expressed in `P`'s local frame, `P.compose(C)` returns the world transform of the child, applying `P.scale` and `P.rotation` to `C.position` and composing `scale` (component-wise multiplication) and `rotation` (sum) accordingly.

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

#### Scenario: Transform.compose is identity-neutral

- **WHEN** `Transform()` (identity) is composed with any transform `t`
- **THEN** `Transform().compose(t)` equals `t`
- **AND** `t.compose(Transform())` equals `t`

#### Scenario: Transform.compose multiplies scale component-wise

- **WHEN** parent has `scale = (2, 3)` and child has `scale = (4, 5)`
- **THEN** `parent.compose(child).scale` equals `(8, 15)`

#### Scenario: Transform.compose sums rotation

- **WHEN** parent has `rotation = 0.5` and child has `rotation = 0.25`
- **THEN** `parent.compose(child).rotation` equals `0.75`

#### Scenario: Transform.compose rotates and scales child position

- **WHEN** parent has `position = (10, 20)`, `scale = (2, 2)`, `rotation = π / 2`, and child has `position = (5, 0)` and identity otherwise
- **THEN** `parent.compose(child).position` is approximately `(10, 30)` within floating-point tolerance, reflecting that `(5, 0)` is scaled to `(10, 0)` then rotated 90° to `(0, 10)` then translated by `(10, 20)`

#### Scenario: Math primitives are serializable

- **WHEN** code serializes `Vec2(1f, 2f)`, `Rect(Vec2(0f, 0f), Vec2(10f, 10f))`, or `Transform(position = Vec2(5f, 5f), scale = Vec2(2f, 2f), rotation = 1f)` via `kotlinx.serialization` JSON
- **THEN** each call returns a JSON document
- **AND** deserializing each document yields an instance equal (by `equals`) to the original

### Requirement: Renderer SPI

The engine SHALL define a `Renderer` interface used by `onRender` hooks. The interface MUST cover the operations needed by the sample games in this change: clearing the surface, drawing filled and outlined rectangles, drawing filled and outlined circles, drawing line segments, drawing text, and measuring text. The interface MUST NOT expose types from `androidx.compose.*` or any backend-specific package. The interface MUST be implementable without reflection or service loaders. The `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` operation MUST draw a straight segment between the two world-space points with the given stroke thickness. The `measureText(text: String, size: Float): Vec2` operation MUST return the bounding box (`Vec2(width, height)`) that `drawText` would produce for the same text and size, allowing callers to align text without backend-specific measurement. The `Color` type used by the renderer MUST be annotated with `@Serializable` (kotlinx.serialization) so it can be embedded as a property value in serialized scene files.

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

#### Scenario: Color is serializable

- **WHEN** code serializes `Color(0.5f, 0.25f, 0.125f, 0.75f)` via `kotlinx.serialization` JSON
- **THEN** the resulting JSON document contains the four channel values
- **AND** deserializing yields a `Color` equal (by `equals`) to the original
