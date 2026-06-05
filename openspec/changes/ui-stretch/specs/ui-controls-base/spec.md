## MODIFIED Requirements

### Requirement: Anchor layout pass resolves rect from the parent rect

The engine SHALL run an **anchor layout pass** that resolves every `Control`'s
`position`/`size` from its anchors/offsets against its **parent rect** before
the control is rendered or hit-tested in a given tick. The pass SHALL walk
Controls **top-down** so a parent Control's resolved rect is available before its
Control children resolve.

The **parent rect** of a `Control` SHALL be:

1. the resolved rect (`Rect(position, size)`) of its nearest **ancestor
   `Control`**, if any; otherwise
2. at a `CanvasLayer` boundary, the **design rect** `Rect(Vec2.ZERO, tree.designSize)`
   when the layer's `followStretch = true`, or the surface rect
   `Rect(Vec2.ZERO, tree.size)` when `followStretch = false`.

After the pass, each Control's `position`/`size` SHALL reflect the current
parent rect, so a surface resize, a `designSize` change, or a parent-rect change
reflows descendants **without per-frame script code**. Under a `followStretch`
layer the resolved rect is in design-space; the UI stretch transform applied at
render and hit-test maps it onto the surface.

#### Scenario: Control under a raw CanvasLayer resolves against the surface

- **WHEN** a `Control` with all anchors `= 1.0`, `offsetLeft = -110`, `offsetTop = -60`, `offsetRight = -10`, `offsetBottom = -10` is a direct child of a `CanvasLayer` with `followStretch = false`, on a `tree.size = Vec2(800, 600)` surface
- **THEN** after the anchor layout pass the resolved `position` SHALL equal `Vec2(690, 540)` and `size` SHALL equal `Vec2(100, 50)` — pinned to the bottom-right of the surface in raw pixels.

#### Scenario: Control under a stretched CanvasLayer resolves against the design rect

- **WHEN** a `Control` with all anchors `= 1.0`, `offsetLeft = -110`, `offsetTop = -60`, `offsetRight = -10`, `offsetBottom = -10` is a direct child of a `CanvasLayer` with `followStretch = true`, with `tree.designSize = Vec2(400, 300)`
- **THEN** after the anchor layout pass the resolved `position` SHALL equal `Vec2(290, 240)` and `size` SHALL equal `Vec2(100, 50)` — pinned to the bottom-right of the **design** rect, then projected onto the surface by the UI stretch transform at render time.

#### Scenario: Nested Control resolves against its ancestor Control rect

- **WHEN** a parent `Control` resolves to `Rect(Vec2(100, 100), Vec2(400, 300))` and contains a child `Control` with all anchors `= 0.5`, `offsetLeft = -10`, `offsetRight = 10`, `offsetTop = -10`, `offsetBottom = 10`
- **THEN** the child SHALL resolve centered on the parent rect center `(300, 250)`, i.e. `position = Vec2(290, 240)`, `size = Vec2(20, 20)`.

#### Scenario: Surface resize reflows anchored controls without script

- **WHEN** a bottom-right-anchored `Control` under a `followStretch = false` layer is laid out on `tree.size = Vec2(800, 600)` and then `tree.size` changes to `Vec2(1024, 768)`
- **THEN** the next anchor layout pass SHALL re-resolve the control against the new surface rect with no `onProcess`/`_process` code running.

#### Scenario: Stretched controls are resize-stable in design-space

- **WHEN** a `Control` under a `followStretch = true` layer is laid out with `tree.designSize = Vec2(800, 600)`, and then `tree.size` changes (surface resize) while `designSize` is unchanged
- **THEN** the control's resolved design-space `position`/`size` SHALL be unchanged, and only the UI stretch transform SHALL change to re-letterbox it onto the new surface.
