## ADDED Requirements

### Requirement: PhysicsContactBuffer stages kinematic contacts

`PhysicsContactBuffer` SHALL expose, alongside its persistent `records`, a
staging area for contacts captured **before** `PhysicsSystem.step` runs in a
physics substep. It SHALL provide a way to stage a `(point, normal)` pair and
a way for `PhysicsSystem.step` to fold every staged pair into `records` and
empty the staging area in one operation. The staging area SHALL be runtime
only (never `@Serializable`, never shared across trees) and SHALL be cleared
by the fold so staged contacts are consolidated exactly once.

#### Scenario: Staged contacts fold into records

- **GIVEN** a `PhysicsContactBuffer` with one staged `(point, normal)` pair and empty `records`
- **WHEN** the fold operation runs
- **THEN** `records` SHALL contain that pair
- **AND** the staging area SHALL be empty

#### Scenario: Fold clears prior records first

- **GIVEN** a `PhysicsContactBuffer` whose `records` already holds a pair from a previous step and whose staging area holds a new pair
- **WHEN** the fold operation runs (the start-of-step clear+consolidate)
- **THEN** `records` SHALL contain only the newly staged pair, not the prior one

### Requirement: moveAndCollide records the resolved kinematic contact

`CharacterBody2D.moveAndCollide` SHALL stage the resolved contact — the
`point` and unit `normal` of the `KinematicCollision2D` it returns — into the
tree's `PhysicsContactBuffer` when contact recording is enabled (driven by
`ContactGizmoWidget.enabled`, mirrored onto `PhysicsContactBuffer.recording`).
A `moveAndCollide` call that does not hit (returns `null`) SHALL stage nothing.
When recording is disabled, `moveAndCollide` SHALL stage nothing and SHALL
incur no recording cost. The staged `point`/`normal` are expressed in the
frame `moveAndCollide` operates in (the body's parent frame), matching the
frame the `RigidBody2D` contact path already records in.

#### Scenario: Kinematic contact staged only while enabled

- **GIVEN** a `CharacterBody2D` that hits a `StaticBody2D` during `moveAndCollide`, with contact recording disabled
- **WHEN** `moveAndCollide` resolves the contact
- **THEN** the contact buffer's staging area SHALL be empty
- **WHEN** recording is then enabled and another colliding `moveAndCollide` runs
- **THEN** the staging area SHALL hold one `(point, normal)` matching the returned `KinematicCollision2D`

#### Scenario: A miss stages nothing

- **GIVEN** contact recording enabled and a `CharacterBody2D` whose motion hits nothing
- **WHEN** `moveAndCollide` returns `null`
- **THEN** the contact buffer's staging area SHALL remain empty

### Requirement: Step consolidates kinematic and rigid contacts in one buffer

When contact recording is enabled, `PhysicsSystem.step` SHALL, at the start of
the step, fold the contacts staged since the previous step into `records`
(replacing the previous step's records) before resolving and appending the
`RigidBody2D` contacts of this step. The resulting `records` SHALL therefore
contain both this substep's kinematic contacts (from `moveAndCollide` during
`_physics_process`) and its rigid contacts (from the impulse solver), so the
`ContactGizmoWidget` draws both. When recording is disabled, `step` SHALL fold
nothing and incur no recording cost.

#### Scenario: Kinematic and rigid contacts coexist after a step

- **GIVEN** contact recording enabled, a `CharacterBody2D` that staged a contact via `moveAndCollide`, and a `RigidBody2D` that resolves a contact during the same `step`
- **WHEN** `PhysicsSystem.step(tree, dt)` runs
- **THEN** the buffer's `records` SHALL contain both the kinematic contact and the rigid contact

#### Scenario: A purely kinematic game populates the buffer

- **GIVEN** a tree whose only colliding bodies are `CharacterBody2D` (no `RigidBody2D`), contact recording enabled
- **WHEN** a `_physics_process` runs `moveAndCollide` into a contact and the following `PhysicsSystem.step` runs
- **THEN** the buffer's `records` SHALL contain the kinematic contact (so `ContactGizmoWidget` shows it in games like Pong)

#### Scenario: Buffer stays single-substep

- **GIVEN** contact recording enabled and `records` populated by a prior step
- **WHEN** a subsequent substep runs in which no contacts occur (no `moveAndCollide` hit, no rigid contact)
- **THEN** `records` SHALL be empty after that step
