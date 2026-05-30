# debug-worldspace-contacts Specification

## Purpose

Garantir que todo `ContactRecord` no `PhysicsContactBuffer` por-`SceneTree`
esteja em **world space**, independentemente do aninhamento do corpo no scene
graph. O `ContactGizmoWidget` desenha em world pass; sem essa garantia, corpos
aninhados (ex.: bolas dentro de um container que gira) teriam os marcadores de
contato deslocados, porque coordenadas do frame do pai seriam interpretadas
como world. A normalização roda nos dois caminhos de gravação (`RigidBody2D`
via `PhysicsSystem.advanceAndResolve` e `CharacterBody2D` via `moveAndCollide`)
com um único helper, custo zero quando a gravação está desabilitada.

## Requirements

### Requirement: Contacts are recorded in world space regardless of body nesting

Every `ContactRecord` written to the per-tree `PhysicsContactBuffer` SHALL be
expressed in **world space**, regardless of the body's nesting in the scene
graph. Both recording paths — the `RigidBody2D` path
(`PhysicsSystem.advanceAndResolve` appending the impulse solver's contact) and
the `CharacterBody2D` path (`moveAndCollide` staging the resolved
`KinematicCollision2D`) — SHALL normalize the contact `point` and `normal`
from the frame the sweep operates in (the body's parent frame) into world
space before writing to the buffer, using the body's parent world transform.
The `point` SHALL be transformed as a position (parent world transform
composed with the local point) and the `normal` SHALL be transformed as a unit
direction (rotated by the parent's world rotation and kept unit-length). When
the parent frame is world (a top-level body whose parent applies no rotation,
translation, or scale), the normalization SHALL be the identity, leaving the
recorded `point`/`normal` unchanged. Both paths SHALL apply the **same**
normalization, so they never diverge.

#### Scenario: Nested body's contact is recorded in world space

- **GIVEN** contact recording enabled and a body whose parent has a non-zero world rotation and translation (e.g. a `CharacterBody2D` inside a rotating container)
- **WHEN** the body resolves a contact at a point/normal expressed in the parent frame and that contact is recorded
- **THEN** the `ContactRecord` in the buffer SHALL hold the world-space point (parent world transform applied to the local point)
- **AND** the recorded normal SHALL be the local normal rotated by the parent's world rotation, still unit-length

#### Scenario: Top-level body's contact is unchanged

- **GIVEN** contact recording enabled and a top-level body whose parent applies no rotation, translation, or scale
- **WHEN** the body resolves a contact and it is recorded
- **THEN** the recorded `point` and `normal` SHALL equal the local contact values (the normalization is the identity)

#### Scenario: Both paths normalize identically

- **GIVEN** contact recording enabled
- **WHEN** a `RigidBody2D` and a `CharacterBody2D` sharing an identically transformed parent each resolve a contact with the same local point/normal
- **THEN** both recorded `ContactRecord`s SHALL carry the same world-space point/normal (one shared normalization, no per-path divergence)
