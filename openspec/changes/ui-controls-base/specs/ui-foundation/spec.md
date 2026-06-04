## MODIFIED Requirements

### Requirement: Panel is a screen-space rectangle

`Panel : Control` SHALL render a filled rectangle at `position` with dimensions
`size: Vec2` (the `size` field is **inherited from `Control`**), color
`color: Color`, and optionally a 1px-or-thicker border `border: Border?` where
`Border` is `{ color: Color, width: Float }`. Panel SHALL be `@Serializable` with
`@Inspect` annotations on `color` and `border` (`size`, anchors, offsets,
`visible`, and `mouseFilter` are inherited `@Inspect` properties from `Control`).
Panel's default `mouseFilter` SHALL be `STOP` (opaque). Panel SHALL be registered
in `NodeRegistry` under `"engine.Panel"`.

#### Scenario: Panel draws filled rect with no border

- **WHEN** a `Panel` has `size = Vec2(100f, 50f)`, `color = Color.RED`, `border = null` and is a direct child of a `CanvasLayer` at `position = Vec2(10f, 20f)`
- **THEN** during the UI pass the renderer SHALL receive `drawRect(Rect(Vec2.ZERO, Vec2(100f, 50f)), Color.RED, filled = true)` while under a `pushTransform` of `translation = Vec2(10f, 20f)`.

#### Scenario: Panel draws border on top of fill

- **WHEN** a `Panel` has `border = Border(color = Color.BLACK, width = 2f)`
- **THEN** after the fill draw, the renderer SHALL receive `drawRect(...same rect..., Color.BLACK, filled = false)` — the unfilled draw SHALL use the same backend convention as `Circle2D` (engine's `drawRect(..., filled=false)` contract).

### Requirement: Button raises pressed signal on click-release inside

`Button : Control` SHALL render as a `Panel`-like rectangle with a centered
`text: String` label and four state colors (`normalColor`, `hoverColor`,
`pressedColor`, `disabledColor: Color`). The button rect dimensions come from the
inherited `Control.size`. Button SHALL expose `disabled: Bool = false` and a
built-in `pressed: Signal<Unit>` instantiated per-instance during `attach`.
Button SHALL be `@Serializable` with `@Inspect` annotations on `text`, the four
colors, and `disabled` (`size`, anchors, offsets, `visible`, and `mouseFilter`
are inherited from `Control`). Button's default `mouseFilter` SHALL be `STOP`.
Button SHALL be registered in `NodeRegistry` under `"engine.Button"`.

A click cycle SHALL be: `pressed` emits exactly once when `mouse-up` occurs
inside the button rect AND the most recent `mouse-down` also occurred inside the
same button. If the user drags out after `mouse-down` and releases outside,
`pressed` SHALL NOT emit and the internal `armed` state SHALL clear.

`Button.pressed` SHALL NOT emit while `disabled = true`, nor while
`visible = false`.

#### Scenario: Click inside emits pressed once

- **WHEN** the mouse moves into a `Button` rect, `mouse-down` fires, and `mouse-up` fires (still inside the rect)
- **THEN** `Button.pressed` SHALL emit exactly once.

#### Scenario: Drag-out cancels press

- **WHEN** `mouse-down` fires inside a `Button`, the mouse moves outside the rect, then `mouse-up` fires
- **THEN** `Button.pressed` SHALL NOT emit.

#### Scenario: Disabled button ignores clicks

- **WHEN** `Button.disabled = true` and a full click cycle occurs over its rect
- **THEN** `Button.pressed` SHALL NOT emit and the button SHALL render with `disabledColor`.

#### Scenario: Hover updates visual color

- **WHEN** the mouse pointer moves into a `Button` rect (no click)
- **THEN** on the next `_process` tick the button SHALL render with `hoverColor`.

#### Scenario: Pressed state during hold

- **WHEN** `mouse-down` fires inside a `Button` and the pointer remains inside while held
- **THEN** the button SHALL render with `pressedColor` until the mouse-up resolves the click.

### Requirement: UI and visual leaves report local bounds

`Panel`, `ColorRect`, and `Button` SHALL inherit `localBounds()` from `Control`,
returning `Rect(Vec2.ZERO, size)` from the inherited `Control.size` field — they
no longer each override `localBounds()`. `Circle2D` (which remains a plain
`Node2D`, **not** a `Control`) and `Label` SHALL still report their own local
bounds:

- `Panel`, `ColorRect`, and `Button` (via `Control`) SHALL return `Rect(Vec2.ZERO, size)`.
- `Circle2D` SHALL return `Rect(Vec2(-radius, -radius), Vec2(2*radius, 2*radius))`.
- `Label` (a `Control`) SHALL return `Rect(Vec2.ZERO, tree.textMeasurer.measureText(text, size))` as its **min-size** when a `TextMeasurer` is reachable via `tree`, and `null` otherwise.

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

## ADDED Requirements

### Requirement: UI render pass skips invisible Control subtrees

The UI render pass (pass 2 of `SceneTree.render`) SHALL skip any `Control` whose
`visible = false`, together with that Control's entire subtree, drawing neither
the Control nor any of its descendants. Non-Control nodes inside a `CanvasLayer`
are unaffected by this rule (they have no `visible` flag in this change).

#### Scenario: Invisible Panel and its children are not drawn

- **WHEN** a `CanvasLayer` contains a `Panel` `P` with `visible = false`, and `P` contains a `Label` child `L`
- **THEN** during the UI render pass neither `P` nor `L` SHALL be drawn.

#### Scenario: Sibling of an invisible Control still draws

- **WHEN** a `CanvasLayer` contains a visible `Panel` `A` and an invisible `Panel` `B`
- **THEN** `A` SHALL be drawn and `B` (with its subtree) SHALL be skipped.

### Requirement: UI hit-test respects Control visibility and mouse_filter

The UI hit-test phase (`SceneTree.hitTestUI`) SHALL skip any `Control` whose
`visible = false` (with its subtree) and SHALL honor each tested `Control`'s
`mouseFilter`: `IGNORE` controls are never tested; `STOP` controls consume a
press that lands inside their screen rect; `PASS` controls register the press
without consuming it. A `Button` SHALL be eligible to absorb a click only when it
is `visible`, `disabled = false`, and its `mouseFilter` is not `IGNORE`.

#### Scenario: Click on an IGNORE Panel passes through to gameplay

- **WHEN** a press lands over a `Panel` with `mouseFilter = IGNORE` and no other STOP control or enabled Button is under the pointer
- **THEN** `input.mouseClickConsumed` SHALL remain `false` and gameplay scripts SHALL see `wasMouseClicked(Left) = true`.

#### Scenario: Click on an invisible Button is not absorbed

- **WHEN** a press lands over a `Button` with `visible = false`
- **THEN** the hit-test phase SHALL NOT absorb the click against that button and SHALL continue resolving as if the button were absent.

#### Scenario: STOP Panel consumes the press as opaque UI

- **WHEN** a press lands over a `Panel` with the default `mouseFilter = STOP` and no enabled Button is under the pointer
- **THEN** the press SHALL be consumed (`input.mouseClickConsumed = true`) and SHALL NOT reach the scene picker nor gameplay.
