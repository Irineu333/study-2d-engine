## ADDED Requirements

### Requirement: Control is an abstract Node2D base for UI widgets

`Control` SHALL be an `abstract class Control : Node2D` living in
`com.neoutils.engine.scene`. Because `Control` extends `Node2D` it inherits the
existing `transform` (`position`/`rotation`/`scale`), `world()`, and the
render-stack contract (`pushTransform`/`popTransform`) **unchanged** — Control
adds UI-layout concerns on top of that model rather than replacing it.

`Control` SHALL own a `size: Vec2` field (the control's local rect dimensions)
and SHALL implement `localBounds()` as `Rect(Vec2.ZERO, size)`, so that
`worldBounds()`/`screenRect()` keep working for every Control leaf without each
leaf re-implementing bounds.

Because `Control` is abstract it SHALL NOT be registered in `NodeRegistry` and
SHALL NOT be instantiable from a scene file directly; only concrete leaves
(`Panel`, `Button`, `Label`, …) are registered.

`size` SHALL be a `@Inspect` property.

#### Scenario: Control is abstract and extends Node2D

- **WHEN** the class `Control` is declared
- **THEN** `Control` SHALL be `abstract` and `Control : Node2D` SHALL be true, so a Control contributes its local `transform` to the render stack exactly like any other `Node2D`.

#### Scenario: Control provides localBounds from size

- **WHEN** a concrete `Control` has `size = Vec2(120f, 40f)`
- **THEN** `localBounds()` SHALL return `Rect(Vec2.ZERO, Vec2(120f, 40f))` without the leaf overriding `localBounds()`.

#### Scenario: Control is not registered as an instantiable node type

- **WHEN** a scene file references `"type": "engine.Control"`
- **THEN** `NodeRegistry` SHALL NOT resolve it (Control is abstract); only concrete subclasses such as `engine.Panel` resolve.

### Requirement: Anchors and offsets define the control rect

`Control` SHALL expose four anchors and four offsets as `@Inspect` properties:
`anchorLeft`, `anchorTop`, `anchorRight`, `anchorBottom` (`Float`, each a
fraction `0.0..1.0` of the **parent rect**) and `offsetLeft`, `offsetTop`,
`offsetRight`, `offsetBottom` (`Float`, pixel offsets added to the anchored
point). The control's resolved rect edges SHALL be computed Godot 4-style:

```
left   = anchorLeft   * parentWidth  + offsetLeft
top    = anchorTop    * parentHeight + offsetTop
right  = anchorRight  * parentWidth  + offsetRight
bottom = anchorBottom * parentHeight + offsetBottom
```

yielding `position = Vec2(left, top)` and `size = Vec2(right - left, bottom - top)`.

The anchors + offsets SHALL be the **source of truth**; `position` and `size`
are derived by the anchor layout pass. The default anchors SHALL be all `0.0`
(top-left preset), so a Control whose offsets mirror a fixed position/size keeps
a fixed screen rect pinned to the parent's top-left — preserving the current
behavior of widgets that set `position`/`size` directly.

#### Scenario: Fixed top-left anchors keep a constant rect

- **WHEN** a `Control` has all four anchors `= 0.0`, `offsetLeft = 10`, `offsetTop = 20`, `offsetRight = 110`, `offsetBottom = 70`, resolved against a parent rect of any size
- **THEN** the resolved `position` SHALL equal `Vec2(10, 20)` and `size` SHALL equal `Vec2(100, 50)`, independent of the parent rect dimensions.

#### Scenario: Right anchor grows the rect with the parent

- **WHEN** a `Control` has `anchorLeft = 0`, `anchorRight = 1`, `offsetLeft = 10`, `offsetRight = -10`, `anchorTop = 0`, `anchorBottom = 0`, `offsetTop = 0`, `offsetBottom = 30`, resolved against a parent rect of width `800`
- **THEN** the resolved `size.x` SHALL equal `780` (`1*800 - 10 - 10`); resolving the same Control against a parent of width `400` SHALL yield `size.x = 380`.

#### Scenario: Centered anchors track the parent center

- **WHEN** a `Control` has all four anchors `= 0.5`, `offsetLeft = -50`, `offsetRight = 50`, `offsetTop = -20`, `offsetBottom = 20`, resolved against a parent rect of `Vec2(800, 600)`
- **THEN** the resolved rect SHALL be centered on `(400, 300)` with `size = Vec2(100, 40)` and `position = Vec2(350, 280)`.

### Requirement: Anchor layout pass resolves rect from the parent rect

The engine SHALL run an **anchor layout pass** that resolves every `Control`'s
`position`/`size` from its anchors/offsets against its **parent rect** before
the control is rendered or hit-tested in a given tick. The pass SHALL walk
Controls **top-down** so a parent Control's resolved rect is available before its
Control children resolve.

The **parent rect** of a `Control` SHALL be:

1. the resolved rect (`Rect(position, size)`) of its nearest **ancestor
   `Control`**, if any; otherwise
2. the surface rect `Rect(Vec2.ZERO, tree.size)` (the screen-space surface),
   which is the case for a Control that is a direct child of a `CanvasLayer`.

After the pass, each Control's `position`/`size` SHALL reflect the current
parent rect, so a surface resize or a parent-rect change reflows descendants
**without per-frame script code**.

#### Scenario: Control under CanvasLayer resolves against the surface

- **WHEN** a `Control` with all anchors `= 1.0`, `offsetLeft = -110`, `offsetTop = -60`, `offsetRight = -10`, `offsetBottom = -10` is a direct child of a `CanvasLayer`, on a `tree.size = Vec2(800, 600)` surface
- **THEN** after the anchor layout pass the resolved `position` SHALL equal `Vec2(690, 540)` and `size` SHALL equal `Vec2(100, 50)` — pinned to the bottom-right of the surface.

#### Scenario: Nested Control resolves against its ancestor Control rect

- **WHEN** a parent `Control` resolves to `Rect(Vec2(100, 100), Vec2(400, 300))` and contains a child `Control` with all anchors `= 0.5`, `offsetLeft = -10`, `offsetRight = 10`, `offsetTop = -10`, `offsetBottom = 10`
- **THEN** the child SHALL resolve centered on the parent rect center `(300, 250)`, i.e. `position = Vec2(290, 240)`, `size = Vec2(20, 20)`.

#### Scenario: Surface resize reflows anchored controls without script

- **WHEN** a bottom-right-anchored `Control` is laid out on `tree.size = Vec2(800, 600)` and then `tree.size` changes to `Vec2(1024, 768)`
- **THEN** the next anchor layout pass SHALL re-resolve the control against the new surface rect with no `onProcess`/`_process` code running.

### Requirement: Anchor presets set the four anchors together

`Control` SHALL expose a `LayoutPreset` enum and a method to apply a preset that
sets the four anchors at once (Godot 4-style). The preset set SHALL include at
least: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`, `CENTER_LEFT`,
`CENTER_TOP`, `CENTER_RIGHT`, `CENTER_BOTTOM`, `CENTER`, and `FULL_RECT`.
Applying a preset SHALL set the four anchors to the canonical fractions for that
preset; offsets SHALL be left to the caller (a fixed-size widget keeps its
offsets, a stretch preset like `FULL_RECT` is typically paired with zero offsets).

#### Scenario: FULL_RECT preset stretches anchors to the parent edges

- **WHEN** `control.applyPreset(LayoutPreset.FULL_RECT)` is called
- **THEN** `anchorLeft = 0`, `anchorTop = 0`, `anchorRight = 1`, `anchorBottom = 1`, so with zero offsets the control fills the entire parent rect.

#### Scenario: CENTER preset anchors to the parent center

- **WHEN** `control.applyPreset(LayoutPreset.CENTER)` is called
- **THEN** all four anchors SHALL equal `0.5`, so symmetric offsets place the control centered on the parent rect.

### Requirement: position and size are derived but remain settable

Writing `Control.position` or `Control.size` directly SHALL update the offsets so
the next anchor layout pass reproduces the written rect under the **current**
anchors (Godot-style write-back). This preserves the ergonomic, imperative API
(`button.position = Vec2(x, y)`) and keeps backward compatibility for code and
scene files that set `position`/`size` without touching anchors.

#### Scenario: Writing position updates offsets under top-left anchors

- **WHEN** a `Control` with default anchors (all `0.0`) and `size = Vec2(80, 24)` has `position` set to `Vec2(50, 60)`
- **THEN** `offsetLeft` SHALL become `50`, `offsetTop` SHALL become `60`, `offsetRight` SHALL become `130`, `offsetBottom` SHALL become `84`, and the next layout pass SHALL resolve `position = Vec2(50, 60)`, `size = Vec2(80, 24)`.

#### Scenario: Writing size preserves position under top-left anchors

- **WHEN** a `Control` with default anchors at `position = Vec2(10, 10)` has `size` set to `Vec2(200, 50)`
- **THEN** the next layout pass SHALL resolve `position = Vec2(10, 10)`, `size = Vec2(200, 50)`.

### Requirement: visible hides a Control and its subtree from render and hit-test

`Control` SHALL expose `visible: Boolean = true` as a `@Inspect` property. A
`Control` with `visible = false` SHALL be skipped — together with its entire
subtree — by both the UI render pass and the UI hit-test phase. Effective
visibility SHALL be conjunctive: a descendant draws and is hit-tested only when
it and every ancestor `Control` up to the `CanvasLayer` are visible.

#### Scenario: Invisible Control is not drawn

- **WHEN** a `Control` `P` containing a child `Control` `C` has `P.visible = false`
- **THEN** neither `P` nor `C` SHALL be drawn during the UI render pass.

#### Scenario: Invisible Control is not hit-tested

- **WHEN** a `Button` has `visible = false` and a click lands inside its screen rect
- **THEN** the hit-test phase SHALL NOT register the click against that button, its `pressed` signal SHALL NOT emit, and the click SHALL NOT be consumed by it.

#### Scenario: Visibility toggles without color hacks

- **WHEN** a `Label` (a Control) is hidden by setting `visible = false` and later shown with `visible = true`
- **THEN** the label SHALL disappear and reappear at full color, with no need to zero `color.a`.

### Requirement: mouse_filter controls hit-test participation

`Control` SHALL expose `mouseFilter: MouseFilter` with values `STOP`, `PASS`, and
`IGNORE`. In the UI hit-test phase:

- `STOP` — the control occupies the point: a press inside its screen rect is
  registered against it and **consumes** the click (gameplay/picker do not see
  it). This is the opaque-UI behavior.
- `IGNORE` — the control is transparent to the hit-test: it is never tested and a
  press passes through it to whatever is behind.
- `PASS` — the control registers the hover/press at that point but does **not**
  consume the click, letting it continue to nodes behind. (Full queued-event
  semantics are deferred to `ui-input-events`; in this change `PASS` means
  "observed, not consumed".)

Default `mouseFilter` SHALL be `STOP` for `Button` and `Panel` (opaque widgets)
and `IGNORE` for non-interactive leaves such as `Label` and `ColorRect`.

#### Scenario: STOP consumes the click

- **WHEN** a press lands inside a `Panel` whose `mouseFilter = STOP`
- **THEN** the hit-test phase SHALL consume the click (`input.mouseClickConsumed = true`) and gameplay SHALL NOT see it.

#### Scenario: IGNORE passes the click through

- **WHEN** a press lands inside a `Label` whose `mouseFilter = IGNORE`, with no other STOP control under the pointer
- **THEN** the hit-test phase SHALL NOT consume the click and gameplay SHALL see `wasMouseClicked(Left) = true`.

#### Scenario: Default filters match widget intent

- **WHEN** a `Button` and a `ColorRect` are created without overriding `mouseFilter`
- **THEN** `Button.mouseFilter` SHALL default to `STOP` and `ColorRect.mouseFilter` SHALL default to `IGNORE`.

### Requirement: focus and size flags are declared but inert in this change

`Control` SHALL declare `focusMode` (with at least `NONE`/`CLICK`/`ALL`),
`focusNeighborLeft/Top/Right/Bottom` (node-path-like references),
`sizeFlagsHorizontal`, and `sizeFlagsVertical` as `@Inspect` properties so the
base is complete and need not be re-opened later. In **this** change these fields
SHALL have **no behavioral effect**: setting them SHALL change nothing observable
about render, layout, or hit-test. Their behavior is reserved — `focusMode`/
`focusNeighbor_*` are activated by the future `ui-focus` change, and
`sizeFlags*` by the future `ui-layout` change.

#### Scenario: Setting focusMode changes nothing observable now

- **WHEN** a `Button` has `focusMode` set to any value and a normal click cycle occurs
- **THEN** the button SHALL behave identically to a button whose `focusMode` is unset — no focus state, no keyboard navigation, no visual change.

#### Scenario: Setting sizeFlags changes nothing observable now

- **WHEN** a `Control` inside a `CanvasLayer` (no container) has `sizeFlagsHorizontal` set to any value
- **THEN** the anchor layout pass SHALL resolve its rect from anchors/offsets exactly as if `sizeFlagsHorizontal` were unset.

#### Scenario: Declared-inert fields still serialize

- **WHEN** a scene file declares a `Button` with `focusMode` and `sizeFlagsHorizontal` set in its `properties` bag
- **THEN** the values SHALL round-trip (load and save) like any other `@Inspect` property, even though they have no runtime effect yet.
