## ADDED Requirements

### Requirement: Node2D exposes a polymorphic local bounds query

`Node2D` SHALL declare `open fun localBounds(): Rect?` returning the node's spatial extent in its **own local coordinate frame** (before its own `transform` is applied). The base `Node2D` implementation SHALL return `null`, meaning "pure transform node (pivot) with no intrinsic extent". Shipped leaf nodes with visual extent SHALL override `localBounds()`; game subclasses MAY override it.

`localBounds()` SHALL return a full `Rect` (origin + size), NOT a bare size, and SHALL NOT impose an origin convention: each node SHALL return the `Rect` it actually draws. The query SHALL be pure — it MUST NOT take a `Renderer` parameter and MUST NOT depend on a render pass being active.

#### Scenario: Plain Node2D has no local bounds

- **WHEN** `localBounds()` is called on a plain `Node2D` used as a transform pivot (no visual override)
- **THEN** it SHALL return `null`

#### Scenario: Leaf reports its own drawn rect in local frame

- **WHEN** `localBounds()` is called on a leaf whose `onDraw` fills `Rect(Vec2.ZERO, Vec2(100f, 50f))`
- **THEN** it SHALL return `Rect(Vec2.ZERO, Vec2(100f, 50f))`, independent of the node's `position`, `rotation`, or `scale`

#### Scenario: Origin convention is not normalized

- **WHEN** `localBounds()` is called on a top-left-anchored node and on a centered node of the same size
- **THEN** the top-left-anchored node SHALL return a `Rect` with `origin = Vec2.ZERO` and the centered node SHALL return a `Rect` with `origin = -size/2`

### Requirement: worldBounds derives an axis-aligned bounds in world space

`Node2D` SHALL provide a `final fun worldBounds(): Rect?` derived from `localBounds()`. When `localBounds()` is `null`, `worldBounds()` SHALL be `null`. Otherwise it SHALL return the axis-aligned bounding box (AABB) enclosing the four corners of `localBounds()` after each is transformed by `world()` — i.e. `AABB( world().apply(c) for c in localBounds().corners() )`. The result SHALL account for the node's full world rotation and scale.

#### Scenario: worldBounds composes translation and scale

- **WHEN** a leaf with `localBounds() = Rect(Vec2.ZERO, Vec2(10f, 10f))` sits at world `position = Vec2(5f, 5f)`, `scale = Vec2(2f, 2f)`, `rotation = 0`
- **THEN** `worldBounds()` SHALL equal `Rect(Vec2(5f, 5f), Vec2(20f, 20f))`

#### Scenario: worldBounds of a rotated node is the enclosing AABB

- **WHEN** a leaf with `localBounds() = Rect(Vec2(-5f, -5f), Vec2(10f, 10f))` has world `rotation = 45°` and `position = Vec2.ZERO`
- **THEN** `worldBounds()` SHALL be the axis-aligned box enclosing the rotated square — a `Rect` larger than the local rect (half-extent `≈ 7.07` on each axis)

#### Scenario: null local bounds yields null world bounds

- **WHEN** `worldBounds()` is called on a node whose `localBounds()` is `null`
- **THEN** it SHALL return `null`

### Requirement: treeBounds unions descendant bounds and stops at CanvasLayer

`Node2D` SHALL provide a `final fun treeBounds(): Rect?` returning the AABB union, in world space, of this node's `worldBounds()` and the `worldBounds()` of every descendant reached by a depth-first walk — **except** that the walk SHALL NOT descend into any `CanvasLayer` subtree (a `CanvasLayer` breaks the world transform chain per the screen-space UI invariant). When neither the node nor any included descendant has bounds, `treeBounds()` SHALL be `null`. Nodes whose own `localBounds()` is `null` SHALL still contribute their descendants' bounds.

#### Scenario: treeBounds unions children

- **WHEN** a pivot `Node2D` (`localBounds() = null`) has two leaf children whose `worldBounds()` are `Rect(Vec2(0f,0f), Vec2(10f,10f))` and `Rect(Vec2(20f,20f), Vec2(10f,10f))`
- **THEN** `treeBounds()` SHALL equal `Rect(Vec2(0f,0f), Vec2(30f,30f))`

#### Scenario: treeBounds does not descend into CanvasLayer

- **WHEN** a node's subtree contains a `CanvasLayer` with screen-space children
- **THEN** `treeBounds()` SHALL exclude the `CanvasLayer` and all its descendants from the union

#### Scenario: empty subtree yields null

- **WHEN** `treeBounds()` is called on a pivot node whose whole subtree has no bounded node
- **THEN** it SHALL return `null`

### Requirement: Oriented bounds are composed by consumers, not returned as a method

The engine SHALL NOT expose an oriented-bounding-box (OBB) method. A consumer needing a tight oriented box (e.g. a selection highlight for a single node) SHALL compose `localBounds()` with `world()` directly: the four oriented corners are `world().apply(c)` for `c in localBounds().corners()`. `worldBounds()` and `treeBounds()` are axis-aligned aggregates and SHALL be used for marquee selection, zoom-to-fit, and group boxes.

#### Scenario: Oriented corners hug a rotated node

- **WHEN** a consumer computes `world().apply(c)` for each `c in localBounds().corners()` on a node rotated 30°
- **THEN** the four resulting points SHALL form the rotated rectangle that tightly hugs the node, distinct from the looser axis-aligned `worldBounds()`

### Requirement: Math primitives Transform.apply and Rect.corners

`Transform` SHALL provide `fun apply(p: Vec2): Vec2` mapping a point in the transform's local frame to its parent frame, computed as `position + rotate(Vec2(scale.x * p.x, scale.y * p.y), rotation)`. `Rect` SHALL provide `fun corners(): List<Vec2>` returning its four corners in stable order `[top-left, top-right, bottom-right, bottom-left]`. The private corner math in `Shape2D` (`obbCorners`/`worldCorners`) SHALL be refactored to reuse `Transform.apply` without changing collision behavior.

#### Scenario: Transform.apply maps a local point

- **WHEN** `Transform(position = Vec2(10f, 0f), rotation = 0f, scale = Vec2(2f, 2f)).apply(Vec2(3f, 4f))` is called
- **THEN** it SHALL return `Vec2(16f, 8f)`

#### Scenario: Rect.corners returns four corners in order

- **WHEN** `Rect(Vec2(0f, 0f), Vec2(4f, 2f)).corners()` is called
- **THEN** it SHALL return `[Vec2(0f,0f), Vec2(4f,0f), Vec2(4f,2f), Vec2(0f,2f)]`

#### Scenario: Physics corner math is unchanged after refactor

- **WHEN** the collision suite runs after `obbCorners`/`worldCorners` are refactored to use `Transform.apply`
- **THEN** all existing collision and sweep tests SHALL still pass

### Requirement: Shape2D and CollisionShape2D report local bounds

`Shape2D` SHALL provide `fun localBounds(): Rect` returning the shape's extent in its own local frame **without applying any world scale** (scale enters later through `world()`): `RectangleShape2D` SHALL return `Rect(-size/2, size)` and `CircleShape2D` SHALL return `Rect(Vec2(-radius, -radius), Vec2(2*radius, 2*radius))`. `CollisionShape2D.localBounds()` (overriding `Node2D`) SHALL return its `shape`'s `localBounds()`, or `null` when `shape` is `null` or the node is `disabled`. The existing `Shape2D.bounds(world, localOffset)` (world-space AABB for broad-phase) SHALL remain unchanged and distinct.

#### Scenario: RectangleShape2D local bounds is centered

- **WHEN** a `RectangleShape2D` has `size = Vec2(10f, 6f)`
- **THEN** its `localBounds()` SHALL equal `Rect(Vec2(-5f, -3f), Vec2(10f, 6f))`

#### Scenario: CollisionObject2D bounds fall out of recursion

- **WHEN** an `Area2D` has a single `CollisionShape2D` child carrying a `CircleShape2D(radius = 4f)` at the body origin
- **THEN** the `Area2D`'s `treeBounds()` SHALL enclose the circle's world AABB without any bounds method defined on `CollisionObject2D`

#### Scenario: Disabled or shapeless collision shape has null local bounds

- **WHEN** `localBounds()` is called on a `CollisionShape2D` whose `shape` is `null` or whose `disabled` is `true`
- **THEN** it SHALL return `null`
