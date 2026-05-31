## ADDED Requirements

### Requirement: Transform inverse point projection

`Transform` SHALL provide `applyInverse(p: Vec2): Vec2`, the exact inverse of
`apply`, mapping a point expressed in the parent frame back into this
transform's local frame: `rotate(p - position, -rotation)` divided
component-wise by `scale`. This lets consumers bring a world-space point into
a node's local frame for oriented hit-testing.

#### Scenario: Inverse of apply round-trips

- **WHEN** `applyInverse(apply(p))` is evaluated for any transform whose scale
  components are non-zero
- **THEN** it returns `p` within float tolerance

#### Scenario: Maps a parent-frame point into the local frame

- **WHEN** `applyInverse(p)` is called
- **THEN** it returns `rotate(p - position, -rotation)` divided component-wise
  by `scale`

### Requirement: Scene pick hit-testing

The engine SHALL run a scene-pick hit-test step in `GameLoop.tick`,
immediately after UI hit-testing and before gameplay processing, gated on the
scene picker being enabled, so an active picker can claim the pointer click
before gameplay reads it.

#### Scenario: Pick hit-test runs after UI and before gameplay process

- **WHEN** `GameLoop.tick` runs a frame
- **THEN** `SceneTree.hitTestPick(input)` is invoked after `hitTestUI` and
  before `tree.process(dt)`

#### Scenario: Disabled picker is a no-op

- **WHEN** the scene picker is disabled
- **THEN** `hitTestPick` performs no tree walk, does not change the selection,
  and does not touch `Input.mouseClickConsumed`

#### Scenario: Active picker claims the click

- **WHEN** the scene picker is enabled and a left click occurs that the UI did
  not already consume
- **THEN** `hitTestPick` resolves the selection and sets
  `Input.mouseClickConsumed` so gameplay does not also see the click
