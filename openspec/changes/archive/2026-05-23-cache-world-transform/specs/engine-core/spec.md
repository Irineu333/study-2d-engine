## MODIFIED Requirements

### Requirement: Transform composition by ancestry

The engine SHALL compose `Transform`s along the chain of `Node2D` ancestors so that `position`, `scale` and `rotation` of a parent affect the world-space transform of its descendants. `Node2D` MUST expose `worldTransform(): Transform` that returns the composed transform from the topmost `Node2D` ancestor down to `this`, applying `scale` and `rotation` of each ancestor to the local frame of the next descendant. `Node2D.worldPosition()` SHALL be equivalent to `worldTransform().position`. `Transform` MUST expose a pure `compose(child: Transform): Transform` (or equivalent operator) that, given a parent transform, returns the world transform for a child described in the parent's local frame.

`Node2D` MUST cache the result of `worldTransform()` per-node so that consecutive reads without any intervening mutation return the cached value in O(1). The cache MUST be invalidated when (a) the node's own `transform` property is assigned a new value, (b) the node is attached to or detached from a parent (reparenting), or (c) any ancestor's `transform` is assigned a new value. Cache invalidation MUST propagate from the mutated node to all `Node2D` descendants, traversing through non-`Node2D` nodes in the chain. The cached value MUST NOT be persisted by `SceneLoader` — it is runtime-only state that begins unset after deserialization and is populated lazily on first read.

The cached value MUST be observably indistinguishable from a fresh computation: any sequence of mutations followed by a read MUST yield the same `Transform` as if `worldTransform()` had been computed from scratch. `Transform`, `Vec2` and `compose` MUST remain immutable value types; the cache is the only state added to support caching.

#### Scenario: Translation only composes additively

- **WHEN** a parent `Node2D` has `transform.position = (10, 20)`, no rotation and unit scale, and a child `Node2D` has `transform.position = (3, 4)` and unit transform otherwise
- **THEN** `child.worldTransform().position` equals `(13, 24)`
- **AND** `child.worldTransform().scale` equals `(1, 1)`
- **AND** `child.worldTransform().rotation` equals `0`

#### Scenario: Parent scale scales child position and size

- **WHEN** a parent `Node2D` has `transform.scale = (2, 3)` and the child has `transform.position = (10, 0)` and `transform.scale = (1, 1)`
- **THEN** `child.worldTransform().position` equals `(20, 0)`
- **AND** `child.worldTransform().scale` equals `(2, 3)`

#### Scenario: Parent rotation rotates child position

- **WHEN** a parent `Node2D` has `transform.rotation = π / 2` (90° counter-clockwise in engine convention) and the child has `transform.position = (10, 0)` and identity otherwise
- **THEN** `child.worldTransform().position` is approximately `(0, 10)` within floating-point tolerance
- **AND** `child.worldTransform().rotation` equals `π / 2`

#### Scenario: Composition is associative across three levels

- **WHEN** three nested `Node2D`s `a → b → c` each carry non-identity translation, scale and rotation
- **THEN** `c.worldTransform()` equals the composition `a.transform ∘ b.transform ∘ c.transform` as defined by `Transform.compose`

#### Scenario: worldPosition delegates to worldTransform

- **WHEN** any `Node2D` `n` is queried via `n.worldPosition()`
- **THEN** the result equals `n.worldTransform().position`

#### Scenario: Repeated reads without mutation return cached value

- **WHEN** `n.worldTransform()` is called twice in a row without any intervening mutation to `n.transform`, `n.parent`, or any ancestor's `transform`
- **THEN** both calls return a `Transform` equal in `position`, `scale` and `rotation`
- **AND** the second call MUST NOT walk the ancestor chain or compose any transforms

#### Scenario: Assigning a new local transform invalidates self and descendants

- **WHEN** `parent.transform = parent.transform.copy(position = (50, 50))` is executed after both `parent.worldTransform()` and `child.worldTransform()` have been called
- **THEN** the next `parent.worldTransform()` reflects the new position
- **AND** the next `child.worldTransform().position` reflects the parent's new position composed with the child's local transform

#### Scenario: Invalidation propagates through non-Node2D intermediates

- **WHEN** a hierarchy `grandparent: Node2D → middle: Node (not Node2D) → grandchild: Node2D` exists, both `grandparent.worldTransform()` and `grandchild.worldTransform()` have been called, and `grandparent.transform` is then reassigned
- **THEN** the next `grandchild.worldTransform()` reflects the grandparent's new transform composed with the grandchild's local transform

#### Scenario: Reparenting invalidates the moved subtree

- **WHEN** a node `child` whose `worldTransform()` has already been computed is removed from its current parent and added to a different parent with a different transform
- **THEN** the next `child.worldTransform()` reflects the composition with the new parent's transform, not the old one

#### Scenario: Loading a scene yields nodes whose cache is unpopulated

- **WHEN** a scene is loaded via `SceneLoader.load(...)` and `worldTransform()` is called on a freshly loaded node
- **THEN** the returned `Transform` is computed from the live ancestor chain (not from any persisted cache)
- **AND** the produced JSON from `SceneLoader.save(scene)` does not contain any field for the cached world transform
