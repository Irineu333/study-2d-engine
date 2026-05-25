## ADDED Requirements

### Requirement: Node2D leaf subclasses default to open

The engine SHALL declare every concrete `Node2D` subclass shipped by `:engine` as `open class` by default, so game code MAY extend any of them without restriction. The same default SHALL apply to concrete non-`Node2D` `Node` subclasses shipped by `:engine` (such as `Timer`). Declaring a shipped leaf as `final` (non-`open`) is permitted only when accompanied by a KDoc comment on the class explaining the invariant that herança quebraria; absent that justification, leaves MUST remain `open`.

#### Scenario: Camera2D is open

- **WHEN** game code declares `class FollowCamera : Camera2D()`
- **THEN** the declaration compiles
- **AND** `FollowCamera` inherits `bounds`, `current`, `aspectMode`, and the world/transform API

#### Scenario: Polygon2D, Circle2D, ColorRect, Line2D are open

- **WHEN** game code declares any of `class X : Polygon2D()`, `class X : Circle2D()`, `class X : ColorRect()`, `class X : Line2D()`
- **THEN** each declaration compiles
- **AND** the subclass inherits the parent's `@Inspect var` properties and `onDraw` behavior

#### Scenario: Timer is open

- **WHEN** game code declares `class IntervalTimer : Timer()`
- **THEN** the declaration compiles
- **AND** `IntervalTimer` inherits `waitTime`, `autostart`, `oneShot`, `processCallback`, and the `timeout: Signal<Unit>` field

### Requirement: Node2D exposes ergonomic local transform accessors

`Node2D` SHALL expose three `var` properties that read from and write to its local `transform`: `position: Vec2`, `rotation: Float`, and `scale: Vec2`. Each property's getter MUST return the corresponding field of `this.transform`. Each property's setter MUST assign a new `Transform` to `this.transform` via the existing `copy(...)` of the immutable `Transform` data class, preserving the other two fields. Because the `transform` setter is the single invalidation point for the world-transform cache (`invalidateWorldTransformRecursive()`), assigning through any of the three new accessors MUST invalidate the cache identically to a direct `transform = ...` assignment. `Transform` and `Vec2` MUST remain immutable value types; the accessors are pure sugar over `transform.copy(...)`.

#### Scenario: position getter mirrors transform.position

- **GIVEN** a `Node2D` with `transform.position = (10, 20)`
- **WHEN** code reads `node.position`
- **THEN** the result equals `Vec2(10, 20)`

#### Scenario: position setter reassigns transform with other fields preserved

- **GIVEN** a `Node2D` with `transform.position = (10, 20)`, `transform.scale = (2, 2)`, `transform.rotation = π`
- **WHEN** code assigns `node.position = Vec2(50, 60)`
- **THEN** `node.transform.position` equals `Vec2(50, 60)`
- **AND** `node.transform.scale` equals `Vec2(2, 2)`
- **AND** `node.transform.rotation` equals `π`

#### Scenario: rotation setter reassigns transform with other fields preserved

- **GIVEN** a `Node2D` with `transform.position = (10, 20)`, `transform.rotation = 0f`
- **WHEN** code assigns `node.rotation = 1.5f`
- **THEN** `node.transform.rotation` equals `1.5f`
- **AND** `node.transform.position` equals `Vec2(10, 20)`

#### Scenario: scale setter reassigns transform with other fields preserved

- **GIVEN** a `Node2D` with `transform.scale = (1, 1)`, `transform.position = (10, 20)`
- **WHEN** code assigns `node.scale = Vec2(3, 4)`
- **THEN** `node.transform.scale` equals `Vec2(3, 4)`
- **AND** `node.transform.position` equals `Vec2(10, 20)`

#### Scenario: Writing through any accessor invalidates world cache like a direct transform assignment

- **GIVEN** a parent `Node2D` with a child `Node2D`, both with cached `world()` already populated
- **WHEN** code assigns `parent.position = Vec2(99, 99)` (or `parent.rotation = ...` or `parent.scale = ...`)
- **THEN** the next `child.world()` reflects the new parent transform, identically to what `parent.transform = parent.transform.copy(position = Vec2(99, 99))` would produce

#### Scenario: Vec2 remains immutable — partial component write through accessor is impossible

- **WHEN** code writes `node.position = Vec2(node.position.x, 50f)`
- **THEN** the assignment compiles and `node.position.y` becomes `50f`
- **AND** attempting `node.position.y = 50f` does NOT compile in Kotlin (Vec2.y is val)
- **AND** the same expression in Python (`self.position.y = 50.0`) raises `AttributeError` at runtime

## MODIFIED Requirements

### Requirement: Transform composition by ancestry

The engine SHALL compose `Transform`s along the chain of `Node2D` ancestors so that `position`, `scale` and `rotation` of a parent affect the world-space transform of its descendants. `Node2D` MUST expose `world(): Transform` that returns the composed transform from the topmost `Node2D` ancestor down to `this`, applying `scale` and `rotation` of each ancestor to the local frame of the next descendant. `Transform` MUST expose a pure `compose(child: Transform): Transform` (or equivalent operator) that, given a parent transform, returns the world transform for a child described in the parent's local frame.

`Node2D` MUST cache the result of `world()` per-node so that consecutive reads without any intervening mutation return the cached value in O(1). The cache MUST be invalidated when (a) the node's own `transform` property is assigned a new value (including assignments via the `position`, `rotation`, `scale` accessor properties), (b) the node is attached to or detached from a parent (reparenting), or (c) any ancestor's `transform` is assigned a new value. Cache invalidation MUST propagate from the mutated node to all `Node2D` descendants, traversing through non-`Node2D` nodes in the chain. The cached value MUST NOT be persisted by `SceneLoader` — it is runtime-only state that begins unset after deserialization and is populated lazily on first read.

The cached value MUST be observably indistinguishable from a fresh computation: any sequence of mutations followed by a read MUST yield the same `Transform` as if `world()` had been computed from scratch. `Transform`, `Vec2` and `compose` MUST remain immutable value types; the cache is the only state added to support caching.

#### Scenario: Translation only composes additively

- **WHEN** a parent `Node2D` has `transform.position = (10, 20)`, no rotation and unit scale, and a child `Node2D` has `transform.position = (3, 4)` and unit transform otherwise
- **THEN** `child.world().position` equals `(13, 24)`
- **AND** `child.world().scale` equals `(1, 1)`
- **AND** `child.world().rotation` equals `0`

#### Scenario: Parent scale scales child position and size

- **WHEN** a parent `Node2D` has `transform.scale = (2, 3)` and the child has `transform.position = (10, 0)` and `transform.scale = (1, 1)`
- **THEN** `child.world().position` equals `(20, 0)`
- **AND** `child.world().scale` equals `(2, 3)`

#### Scenario: Parent rotation rotates child position

- **WHEN** a parent `Node2D` has `transform.rotation = π / 2` (90° counter-clockwise in engine convention) and the child has `transform.position = (10, 0)` and identity otherwise
- **THEN** `child.world().position` is approximately `(0, 10)` within floating-point tolerance
- **AND** `child.world().rotation` equals `π / 2`

#### Scenario: Composition is associative across three levels

- **WHEN** three nested `Node2D`s `a → b → c` each carry non-identity translation, scale and rotation
- **THEN** `c.world()` equals the composition `a.transform ∘ b.transform ∘ c.transform` as defined by `Transform.compose`

#### Scenario: Repeated reads without mutation return cached value

- **WHEN** `n.world()` is called twice in a row without any intervening mutation to `n.transform`, `n.parent`, or any ancestor's `transform`
- **THEN** both calls return a `Transform` equal in `position`, `scale` and `rotation`
- **AND** the second call MUST NOT walk the ancestor chain or compose any transforms

#### Scenario: Assigning a new local transform invalidates self and descendants

- **WHEN** `parent.transform = parent.transform.copy(position = (50, 50))` is executed after both `parent.world()` and `child.world()` have been called
- **THEN** the next `parent.world()` reflects the new position
- **AND** the next `child.world().position` reflects the parent's new position composed with the child's local transform

#### Scenario: Invalidation propagates through non-Node2D intermediates

- **WHEN** a hierarchy `grandparent: Node2D → middle: Node (not Node2D) → grandchild: Node2D` exists, both `grandparent.world()` and `grandchild.world()` have been called, and `grandparent.transform` is then reassigned
- **THEN** the next `grandchild.world()` reflects the grandparent's new transform composed with the grandchild's local transform

#### Scenario: Reparenting invalidates the moved subtree

- **WHEN** a node `child` whose `world()` has already been computed is removed from its current parent and added to a different parent with a different transform
- **THEN** the next `child.world()` reflects the composition with the new parent's transform, not the old one

#### Scenario: Loading a scene yields nodes whose cache is unpopulated

- **WHEN** a scene is loaded via `SceneLoader.load(...)` and `world()` is called on a freshly loaded node
- **THEN** the returned `Transform` is computed from the live ancestor chain (not from any persisted cache)
- **AND** the produced JSON from `SceneLoader.save(scene)` does not contain any field for the cached world transform

## REMOVED Requirements

### Requirement: worldPosition delegates to worldTransform

**Reason**: `Node2D.worldPosition()` is removed as a redundant accessor once `Node2D.world(): Transform` exists. Keeping both creates two ways to spell the same query and inflates the binding surface (Python stubs, KDoc, mental model).

**Migration**: replace every call of `node.worldPosition()` with `node.world().position`. The renaming is mechanical and applies to Kotlin call sites in `:engine`, `:engine-skiko`, `:engine-compose`, `:games:*`, and to Python scripts (`self.worldPosition()` → `self.world().position`). PyI stubs in `:engine-bundle-python` are updated accordingly.
