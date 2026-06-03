## MODIFIED Requirements

### Requirement: Ball physics

The Ball SHALL be a `com.neoutils.engine.physics.CharacterBody2D` whose collision shape is a `CircleShape2D`. Each tick the Ball SHALL advance its motion with `moveAndCollide(velocity * dt)` (Godot-style kinematic sweep), so it stops exactly at the first contact with no tunneling at high speed; the engine does NOT integrate `velocity` automatically. On a resolved contact the Ball SHALL react in script by classifying the contact:

- **Paddle face** — the contact is a paddle (group `paddles`) AND the ball's center lies **beside** the paddle (its center-x is outside the paddle's horizontal span, i.e. the ball is pressing on a vertical face, front corners included). The Ball applies the angle-based "english" bounce: the horizontal direction flips to the side the ball is on (its center-x vs the paddle center-x) and the vertical component is derived from where the ball struck the face, with the speed allowed to increase modestly per hit up to a configured maximum.
- **Paddle top/bottom edge, or wall** — every other contact (including a contact whose ball center-x is within the paddle's horizontal span, i.e. a true top/bottom edge). The Ball reflects its velocity across the contact normal (`v' = v - 2(v·n)n`).

Classification MUST be geometric by the ball center's **horizontal** position relative to the paddle (beside the paddle ⇒ face; within the paddle's horizontal span ⇒ top/bottom edge), NOT a comparison of the contact normal's dominant axis (`abs(n.x) > abs(n.y)`). The diagonal normal at a front corner (`|n.x| ≈ |n.y|`) is then resolved deterministically as a face hit (x reversed decisively), never as a weak diagonal reflection that the chasing paddle could pin. The Ball script MUST NOT manually advance its own `position` to escape a starting overlap: `moveAndCollide` now applies outward (separating) motion on a starting overlap itself, so a paddle pressing a marginal overlap cannot freeze the ball without any script-side nudge. Walls and goals keep their current roles: walls are solid bodies picked up by the sweep; goals are `Area2D` sensors handled on the `_on_area_entered` path.

#### Scenario: Ball reflects off top wall

- **WHEN** the ball's swept motion contacts the top wall while moving upward
- **THEN** its velocity is reflected across the wall's (vertical-facing) contact normal, negating the Y component
- **AND** the X component is unchanged

#### Scenario: Ball gets the english bounce on a paddle face

- **WHEN** the ball's swept motion contacts a paddle and the ball's center is beside the paddle's horizontal span
- **THEN** the horizontal direction flips to the side the ball is on (away from the paddle)
- **AND** the vertical component is set from the ball's offset along the face (the angle-based bounce)
- **AND** the ball does not pass through the paddle on the next tick

#### Scenario: Ball corner hit does not trap

- **WHEN** the ball contacts a paddle front corner, producing a near-diagonal contact normal, while the AI paddle chases and re-presses the contact
- **THEN** the contact is classified by the ball center's horizontal position (beside the paddle ⇒ face), not by `abs(n.x) > abs(n.y)`, so the horizontal velocity reverses decisively
- **AND** `moveAndCollide` applies the ball's outward bounce velocity on the starting overlap, so the ball leaves the corner, never oscillating or freezing in place — with no manual `position` nudge in the script
