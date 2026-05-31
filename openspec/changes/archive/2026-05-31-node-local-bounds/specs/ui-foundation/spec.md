## ADDED Requirements

### Requirement: UI and visual leaves report local bounds

`Panel`, `ColorRect`, `Circle2D`, `Button`, and `Label` SHALL override `Node2D.localBounds()` to report the `Rect` they actually draw in their local frame:

- `Panel` and `ColorRect` SHALL return `Rect(Vec2.ZERO, size)`.
- `Button` SHALL return `Rect(Vec2.ZERO, size)`.
- `Circle2D` SHALL return `Rect(Vec2(-radius, -radius), Vec2(2*radius, 2*radius))`.
- `Label` SHALL return `Rect(Vec2.ZERO, tree.textMeasurer.measureText(text, size))` when a `TextMeasurer` is reachable via `tree`, and `null` otherwise.

#### Scenario: Panel local bounds matches its drawn rect

- **WHEN** a `Panel` has `size = Vec2(100f, 50f)`
- **THEN** `localBounds()` SHALL return `Rect(Vec2.ZERO, Vec2(100f, 50f))`

#### Scenario: Circle2D local bounds is centered

- **WHEN** a `Circle2D` has `radius = 8f`
- **THEN** `localBounds()` SHALL return `Rect(Vec2(-8f, -8f), Vec2(16f, 16f))`

#### Scenario: Label local bounds uses the text measurer

- **WHEN** a `Label` with `text = "Hi"`, `size = 12f` is attached to a `SceneTree` whose `textMeasurer` reports `Vec2(14f, 12f)` for that text
- **THEN** `localBounds()` SHALL return `Rect(Vec2.ZERO, Vec2(14f, 12f))`, even if the label has never been drawn

#### Scenario: Label without a measurer has null bounds

- **WHEN** a `Label` is detached from any tree, or its tree has `textMeasurer = null`
- **THEN** `localBounds()` SHALL return `null`

### Requirement: Button screen-space rect derives from local bounds and world transform

`Button.screenRect()` SHALL derive from `localBounds()` composed with `world()`, accounting for the full world transform including **rotation**. It SHALL return the axis-aligned bounding box enclosing `world().apply(c)` for `c in localBounds().corners()` — equivalent to `worldBounds()`. This supersedes the prior scale-only computation that ignored rotation. The UI hit-test phase SHALL use this rect when testing whether `input.pointerPosition` falls inside a `Button`.

#### Scenario: Unrotated button rect matches translated, scaled size

- **WHEN** a `Button` with `size = Vec2(80f, 24f)` sits at world `position = Vec2(10f, 5f)`, `scale = Vec2(1f, 1f)`, `rotation = 0`
- **THEN** `screenRect()` SHALL equal `Rect(Vec2(10f, 5f), Vec2(80f, 24f))`

#### Scenario: Rotated button rect accounts for rotation

- **WHEN** a `Button` is rotated 90° about its origin
- **THEN** `screenRect()` SHALL be the axis-aligned box enclosing the rotated rectangle (width and height swapped relative to the unrotated rect), NOT the scale-only rect that ignored rotation

#### Scenario: Hit-test uses the rotation-aware rect

- **WHEN** a click lands at a screen position inside a rotated `Button`'s oriented rect but outside the old scale-only rect
- **THEN** the UI hit-test phase SHALL register the click against that `Button`
