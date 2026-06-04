# ui-foundation Specification

## Purpose

Provides the minimum viable in-game UI surface for the engine: `CanvasLayer`
as a screen-space scope inside the scene tree, `Panel` for filled rectangle
widgets with optional outline, and `Button` for pushable widgets with a
built-in `pressed` signal. Adds a UI hit-test phase to the tick that lets
`Button` consume mouse clicks before gameplay sees them, integrates UI nodes
into the standard `scene.json` v2 properties bag, and exposes the bindings
in both Python and Lua scripting hosts.

Containers (HBox/VBox/Grid/Margin), anchors, focus, theme, additional
widgets (TextEdit, Slider, etc.) and a Godot-style event-queue input model
are explicitly out of scope — each lives in a follow-up capability spec
(`ui-controls-base`, `ui-anchors`, `ui-layout`, `ui-focus`, `ui-theme`,
`ui-input-events`).

## Requirements

### Requirement: CanvasLayer renders children in screen-space

`CanvasLayer` SHALL extend `Node` (not `Node2D`) and serve as a scope inside the scene tree whose direct and indirect descendants render in **screen-space**, decoupled from any `Camera2D` view transform applied to the world pass. `CanvasLayer` SHALL expose `layer: Int = 0` as a `@Inspect` property.

#### Scenario: CanvasLayer child renders ignoring Camera2D

- **WHEN** a scene contains a `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(400f, 300f))` set as current and a `CanvasLayer` containing a `Panel` at `position = Vec2(50f, 50f)` with `size = Vec2(100f, 100f)`, rendered onto a `tree.size = Vec2(800f, 600f)` surface
- **THEN** the `Panel` SHALL render at screen pixels `(50, 50)` to `(150, 150)`, regardless of the camera's view transform (the camera would otherwise scale `x2` to fit `400` width into `800` pixels)

#### Scenario: CanvasLayer is a Node, not a Node2D

- **WHEN** `CanvasLayer` is declared
- **THEN** `CanvasLayer : Node` SHALL be true (not `Node2D`), so that `CanvasLayer` does not contribute a local transform to the render stack — instead it resets the stack to identity for the screen-space pass.

#### Scenario: CanvasLayer is a registered Node type

- **WHEN** a scene file references `"type": "engine.CanvasLayer"`
- **THEN** `NodeRegistry` SHALL resolve it to the `CanvasLayer` class and instantiate it via no-args constructor.

### Requirement: Render walk uses two passes — world then canvas layers

`SceneTree.render(renderer)` SHALL execute exactly two passes per frame:

1. **World pass**: collect the current `Camera2D` (if any), push the corresponding view transform via `renderer.pushTransform`, walk the tree DFS skipping any `CanvasLayer` subtree entirely, then pop the view transform.
2. **UI pass**: collect every `CanvasLayer` reachable from the root in DFS pre-order, sort them by `(layer ascending, dfs-discovery-order ascending)`, and for each `CanvasLayer` in that order: walk DFS into its subtree starting from identity transform.

Within a `CanvasLayer` subtree, `Node2D` children SHALL receive standard `Renderer.pushTransform`/`popTransform` for their local transform — composing on top of the identity established at the layer boundary.

#### Scenario: World nodes are not drawn during the UI pass

- **WHEN** a tree contains a world `Node2D` `A` and a `CanvasLayer` `L` containing a `Panel` `P`
- **THEN** during render, `A` SHALL be drawn during the world pass, and `P` SHALL be drawn during the UI pass; the two passes SHALL NOT visit each other's nodes.

#### Scenario: CanvasLayers draw in (layer, dfs-order) order

- **WHEN** a tree contains three CanvasLayers in DFS-discovery order `L1 (layer=0)`, `L2 (layer=10)`, `L3 (layer=0)`
- **THEN** the UI pass SHALL render in the order `L1, L3, L2` (layer 0 first, with DFS order as tie-break; layer 10 last).

#### Scenario: World pass uses Camera2D view transform, UI pass starts from identity

- **WHEN** a scene has a current `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(400f, 300f))` and `tree.size = Vec2(800f, 600f)`, plus a world `Node2D` at `position = Vec2(100f, 100f)` and a `CanvasLayer` child containing a `Panel` at `position = Vec2(100f, 100f)`
- **THEN** the world `Node2D` SHALL be drawn at screen pixels around `(200, 200)` (after view scale 2x), while the `Panel` SHALL be drawn at screen pixels `(100, 100)`.

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

A click cycle SHALL be: `pressed` emits exactly once when `mouse-up` occurs inside the button rect AND the most recent `mouse-down` also occurred inside the same button. If the user drags out after `mouse-down` and releases outside, `pressed` SHALL NOT emit and the internal `armed` state SHALL clear.

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

### Requirement: UI hit-test consumes mouse clicks before gameplay tick

`SceneTree.hitTestUI(input)` SHALL run as a new tick phase between `input.beginTick()` and `tree.process(dt)`. When `input.wasMouseClickedRaw(MouseButton.Left)` is true the phase SHALL first resolve the top-most enabled debug screen panel (`ScreenDebugWidget`) whose panel rect contains `input.pointerPosition` (resolved top-most-first in reverse-DFS order) and, if one exists, raise it to the top of its sibling order (`raiseChildToTop`, bringing it in front of the other debug panels). This bring-to-front SHALL happen on **any** press landing on the panel rect — including a press that a `Button` inside the panel goes on to absorb — so interacting with a panel's controls also raises it.

The phase SHALL then walk every reachable `CanvasLayer` sorted descending by `(layer, dfs-order)` (i.e. top-most first), for each `CanvasLayer` walk its subtree in reverse DFS order, and for the first `Button` whose `disabled = false` and whose screen-space rect contains `input.pointerPosition`: set `input.mouseClickConsumed = true` and stop.

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

#### Scenario: Raw click is observable even when consumed

- **WHEN** the click is consumed by a `Button`
- **THEN** any code calling `input.wasMouseClickedRaw(MouseButton.Left)` SHALL return `true` for the same tick.

#### Scenario: Press over debug panel elects owner and raises it

- **WHEN** the mouse clicks over a region covered by two overlapping enabled debug panels and not over any `Button`
- **THEN** the phase SHALL record the top-most panel under the pointer as the press owner, raise it to the top of its sibling order, set `input.mouseClickConsumed = true`, and the lower panel SHALL NOT become the press owner.

#### Scenario: Press on a Button inside a panel raises the panel

- **WHEN** the mouse clicks a `Button` that lives inside a debug panel covered by no other panel at that point
- **THEN** the phase SHALL raise the panel to the top of its sibling order, the `Button` SHALL absorb the click (`input.mouseClickConsumed = true`) and its `pressed` SHALL emit normally, and the panel SHALL NOT become the press owner (it arms no drag).

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

### Requirement: UI nodes serialize via standard scene.json properties bag

`CanvasLayer`, `Panel`, and `Button` SHALL participate in the standard scene.json v2 `properties` bag and respect the same `@Inspect` vs `@Transient` discipline as other scene nodes. Loading and saving SHALL roundtrip the values.

#### Scenario: Button loads from scene.json with all properties

- **WHEN** a `scene.json` contains:
  ```json
  {
    "type": "engine.Button",
    "properties": {
      "transform": { "position": { "x": 300, "y": 200 } },
      "size": { "x": 200, "y": 50 },
      "text": "Start",
      "normalColor": { "r": 0.3, "g": 0.3, "b": 0.3, "a": 1.0 },
      "hoverColor": { "r": 0.4, "g": 0.4, "b": 0.4, "a": 1.0 },
      "pressedColor": { "r": 0.2, "g": 0.2, "b": 0.2, "a": 1.0 },
      "disabledColor": { "r": 0.1, "g": 0.1, "b": 0.1, "a": 1.0 },
      "disabled": false
    }
  }
  ```
- **THEN** `BundleLoader`/`SceneLoader` SHALL instantiate a `Button` with all those properties applied, ready to be added to a `SceneTree`.

#### Scenario: CanvasLayer with layer ordering loads from scene.json

- **WHEN** a `scene.json` declares a `CanvasLayer` with `"properties": { "layer": 10 }`
- **THEN** the loaded `CanvasLayer.layer` SHALL equal `10` and the UI pass SHALL respect that layer for ordering.

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
