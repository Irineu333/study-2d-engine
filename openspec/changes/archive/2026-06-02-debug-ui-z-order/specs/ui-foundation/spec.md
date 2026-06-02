## MODIFIED Requirements

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
