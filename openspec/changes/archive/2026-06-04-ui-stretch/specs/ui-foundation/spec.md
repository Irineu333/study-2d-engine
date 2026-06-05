## MODIFIED Requirements

### Requirement: CanvasLayer renders children in screen-space

`CanvasLayer` SHALL extend `Node` (not `Node2D`) and serve as a scope inside the scene tree whose direct and indirect descendants render decoupled from any `Camera2D` view transform applied to the world pass. `CanvasLayer` SHALL expose `layer: Int = 0` and `followStretch: Boolean = true` as `@Inspect` properties.

When `followStretch = false`, descendants SHALL render in **raw screen pixels** (`tree.size`), starting each subtree from identity transform. When `followStretch = true`, descendants SHALL render in **design-space** (`tree.designSize`) with the tree's UI stretch transform applied, so UI authored against the design resolution scales and letterboxes onto the surface in step with the world pass (its position, size, and `fontSize` all scaling by the same factor).

#### Scenario: stretched CanvasLayer child tracks the design resolution

- **WHEN** a scene contains a `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(400f, 300f))` set as current (so `designSize = Vec2(400f, 300f)`), `uiStretchMode = FIT`, and a `CanvasLayer` (`followStretch = true`) containing a `Panel` at `position = Vec2(50f, 50f)` with `size = Vec2(100f, 100f)`, rendered onto a `tree.size = Vec2(800f, 600f)` surface
- **THEN** the `Panel` SHALL render at screen pixels `(100, 100)` to `(300, 300)` — scaled `2x` along with the world content under the same bounds (the UI stretch transform matching the camera's resolution-fit scale, without its pan).

#### Scenario: raw CanvasLayer child renders in screen pixels

- **WHEN** the same scene uses a `CanvasLayer` with `followStretch = false` containing a `Panel` at `position = Vec2(50f, 50f)` with `size = Vec2(100f, 100f)` on a `tree.size = Vec2(800f, 600f)` surface
- **THEN** the `Panel` SHALL render at screen pixels `(50, 50)` to `(150, 150)`, regardless of `designSize`, `uiStretchMode`, or the camera.

#### Scenario: CanvasLayer is a Node, not a Node2D

- **WHEN** `CanvasLayer` is declared
- **THEN** `CanvasLayer : Node` SHALL be true (not `Node2D`), so that `CanvasLayer` does not contribute a local transform to the render stack — instead it resets the stack to identity (raw) or to the UI stretch transform (followStretch) for the screen-space pass.

#### Scenario: CanvasLayer is a registered Node type

- **WHEN** a scene file references `"type": "engine.CanvasLayer"`
- **THEN** `NodeRegistry` SHALL resolve it to the `CanvasLayer` class and instantiate it via no-args constructor.

### Requirement: Render walk uses two passes — world then canvas layers

`SceneTree.render(renderer)` SHALL execute exactly two passes per frame:

1. **World pass**: collect the current `Camera2D` (if any), push the corresponding view transform via `renderer.pushTransform`, walk the tree DFS skipping any `CanvasLayer` subtree entirely, then pop the view transform.
2. **UI pass**: collect every `CanvasLayer` reachable from the root in DFS pre-order, sort them by `(layer ascending, dfs-discovery-order ascending)`, and for each `CanvasLayer` in that order: if `followStretch = true` and the tree's UI stretch transform is non-null, push that transform via `renderer.pushTransform`, walk DFS into its subtree, then pop; otherwise walk DFS into its subtree starting from identity transform.

Within a `CanvasLayer` subtree, `Node2D` children SHALL receive standard `Renderer.pushTransform`/`popTransform` for their local transform — composing on top of the transform (identity or UI stretch) established at the layer boundary.

#### Scenario: World nodes are not drawn during the UI pass

- **WHEN** a tree contains a world `Node2D` `A` and a `CanvasLayer` `L` containing a `Panel` `P`
- **THEN** during render, `A` SHALL be drawn during the world pass, and `P` SHALL be drawn during the UI pass; the two passes SHALL NOT visit each other's nodes.

#### Scenario: CanvasLayers draw in (layer, dfs-order) order

- **WHEN** a tree contains three CanvasLayers in DFS-discovery order `L1 (layer=0)`, `L2 (layer=10)`, `L3 (layer=0)`
- **THEN** the UI pass SHALL render in the order `L1, L3, L2` (layer 0 first, with DFS order as tie-break; layer 10 last).

#### Scenario: stretched UI pass composes the stretch transform, raw layer does not

- **WHEN** a scene has a current `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(400f, 300f))` and `tree.size = Vec2(800f, 600f)` (so the UI stretch is `scale 2x`, `translation 0`), plus a world `Node2D` at `position = Vec2(100f, 100f)`, a `followStretch = true` `CanvasLayer` with a `Panel` at `position = Vec2(100f, 100f)`, and a `followStretch = false` `CanvasLayer` with a `Panel` at `position = Vec2(100f, 100f)`
- **THEN** the world `Node2D` SHALL be drawn around `(200, 200)`, the stretched `Panel` SHALL be drawn at `(200, 200)`, and the raw `Panel` SHALL be drawn at `(100, 100)`.

### Requirement: UI hit-test consumes mouse clicks before gameplay tick

`SceneTree.hitTestUI(input)` SHALL run as a new tick phase between `input.beginTick()` and `tree.process(dt)`. When `input.wasMouseClickedRaw(MouseButton.Left)` is true the phase SHALL first resolve the top-most enabled debug screen panel (`ScreenDebugWidget`) whose panel rect contains `input.pointerPosition` (resolved top-most-first in reverse-DFS order) and, if one exists, raise it to the top of its sibling order (`raiseChildToTop`, bringing it in front of the other debug panels). This bring-to-front SHALL happen on **any** press landing on the panel rect — including a press that a `Button` inside the panel goes on to absorb — so interacting with a panel's controls also raises it.

The phase SHALL then walk every reachable `CanvasLayer` sorted descending by `(layer, dfs-order)` (i.e. top-most first), for each `CanvasLayer` walk its subtree in reverse DFS order, and for the first `Button` whose `disabled = false` and whose screen-space rect contains the pointer: set `input.mouseClickConsumed = true` and stop. For a `CanvasLayer` with `followStretch = true` the phase SHALL map `input.pointerPosition` into the layer's design-space (inverse of the tree's UI stretch transform) before testing `Button` rects, so the pointer hits the `Button` where it is drawn; for `followStretch = false` the raw pointer SHALL be used.

When no `Button` absorbs the click, the top-most panel resolved above (if any) SHALL additionally be recorded as the owner of the current press (read by `ScreenDebugWidget.updateDrag` so that only the owner arms its drag) and the click SHALL be consumed (`input.mouseClickConsumed = true`). Panels are opaque UI, so a press over any debug panel SHALL be consumed and SHALL NOT reach the scene picker nor gameplay. The press owner SHALL be cleared at the start of each tick; a press absorbed by a `Button` SHALL NOT set a press owner (so it arms no drag), even when it raised the panel.

`Input.wasMouseClicked(button)` SHALL return `false` when `mouseClickConsumed = true` for that button (left only in MVP). `Input.wasMouseClickedRaw(button)` SHALL always return the raw, unconsumed signal.

#### Scenario: Click on Button consumes input

- **WHEN** the mouse clicks at the screen position covered by an enabled `Button`
- **THEN** the hit-test phase SHALL set `input.mouseClickConsumed = true`, the button's `pressed` signal SHALL emit, and any gameplay script calling `tree.input.wasMouseClicked(MouseButton.Left)` SHALL receive `false` for that tick.

#### Scenario: Click outside any Button does not consume

- **WHEN** the mouse clicks at a screen position not covered by any enabled `Button` in any `CanvasLayer`, and not over any enabled debug panel
- **THEN** `input.mouseClickConsumed` SHALL remain `false`, and gameplay scripts SHALL see `wasMouseClicked(Left) = true`.

#### Scenario: Top-most CanvasLayer wins overlap

- **WHEN** two enabled `Button`s overlap at the click position, one in `CanvasLayer A (layer=0)` and one in `CanvasLayer B (layer=10)`
- **THEN** only the `Button` in `B` SHALL receive `pressed` (top-most layer wins).

#### Scenario: Click on a Button inside a stretched layer maps through the stretch

- **WHEN** a `CanvasLayer` with `followStretch = true` (UI stretch `scale 2x`, `translation 0`) contains an enabled `Button` whose design rect is `Rect(Vec2(50f, 50f), Vec2(80f, 24f))`, and the mouse clicks at screen pixel `(120, 120)`
- **THEN** the phase SHALL map the pointer to design `(60, 60)`, find it inside the `Button` rect, set `input.mouseClickConsumed = true`, and emit `pressed`.

#### Scenario: Raw click is observable even when consumed

- **WHEN** the click is consumed by a `Button`
- **THEN** any code calling `input.wasMouseClickedRaw(MouseButton.Left)` SHALL return `true` for the same tick.

#### Scenario: Press over debug panel elects owner and raises it

- **WHEN** the mouse clicks over a region covered by two overlapping enabled debug panels and not over any `Button`
- **THEN** the phase SHALL record the top-most panel under the pointer as the press owner, raise it to the top of its sibling order, set `input.mouseClickConsumed = true`, and the lower panel SHALL NOT become the press owner.

#### Scenario: Press on a Button inside a panel raises the panel

- **WHEN** the mouse clicks a `Button` that lives inside a debug panel covered by no other panel at that point
- **THEN** the phase SHALL raise the panel to the top of its sibling order, the `Button` SHALL absorb the click (`input.mouseClickConsumed = true`) and its `pressed` SHALL emit normally, and the panel SHALL NOT become the press owner (it arms no drag).

### Requirement: Button screen-space rect derives from local bounds and world transform

`Button.screenRect()` SHALL derive from `localBounds()` composed with `world()`, accounting for the full world transform including **rotation**. It SHALL return the axis-aligned bounding box enclosing `world().apply(c)` for `c in localBounds().corners()` — equivalent to `worldBounds()`. This rect is expressed in the `Button`'s own `CanvasLayer` coordinate space (design-space under `followStretch = true`, raw pixels otherwise); the UI hit-test phase composes the layer's UI stretch transform when comparing against `input.pointerPosition`. This supersedes the prior scale-only computation that ignored rotation.

#### Scenario: Unrotated button rect matches translated, scaled size

- **WHEN** a `Button` with `size = Vec2(80f, 24f)` sits at world `position = Vec2(10f, 5f)`, `scale = Vec2(1f, 1f)`, `rotation = 0`
- **THEN** `screenRect()` SHALL equal `Rect(Vec2(10f, 5f), Vec2(80f, 24f))`

#### Scenario: Rotated button rect accounts for rotation

- **WHEN** a `Button` is rotated 90° about its origin
- **THEN** `screenRect()` SHALL be the axis-aligned box enclosing the rotated rectangle (width and height swapped relative to the unrotated rect), NOT the scale-only rect that ignored rotation

#### Scenario: Hit-test uses the rotation-aware rect

- **WHEN** a click lands at a screen position inside a rotated `Button`'s oriented rect but outside the old scale-only rect
- **THEN** the UI hit-test phase SHALL register the click against that `Button`
