# debug-ui-scroll Specification

## Purpose

Defines scrollable bodies for screen-space debug panels: a `ScreenDebugWidget`
renders its body inside a bounded vertical viewport, clips overflowing content,
and reveals the remainder by scrolling (mouse wheel or a draggable scrollbar
grabber) instead of collapsing it into an overflow line. The scroll offset is
derived state — a single logical value clamped on read — so resizes and dock
changes re-clamp for free. Wheel routing is resolved during
`SceneTree.hitTestUI` so a debug panel can absorb the wheel before gameplay
sees it.

## Requirements

### Requirement: Scrollable panel body

A `ScreenDebugWidget` SHALL render its body within a bounded vertical viewport and scroll its content when the content exceeds that viewport. The widget MUST distinguish:

- **content extent** — the intrinsic height of all body rows (what the subclass measures);
- **viewport height** — `min(contentExtent, maxBodyHeight)`, where `maxBodyHeight` leaves room for the header and screen margins; this is the height the dock measures via `contentSize()`.

When content extent does not exceed the viewport, the panel SHALL auto-size to its content exactly as before (small panels are unaffected). When it does exceed, the panel SHALL claim only the viewport height in the dock and the remaining content SHALL be reachable by scrolling. The body content MUST be clipped to the viewport via the renderer clip stack, and SHALL be drawn offset by the current scroll amount (clip outside, scroll-translate inside).

The truncation behavior that previously collapsed overflowing rows into a `… (+N more)` line SHALL be removed: the body layout now includes every row, revealed by scrolling.

#### Scenario: Small panel auto-sizes and does not scroll

- **WHEN** a panel's content extent is less than `maxBodyHeight`
- **THEN** the dock measures the panel at its content height
- **AND** no scrollbar is shown and the content is not clipped away

#### Scenario: Large panel claims a bounded viewport

- **WHEN** a panel's content extent exceeds `maxBodyHeight`
- **THEN** the dock measures the panel at the viewport height (not the full content height)
- **AND** rows beyond the viewport are reachable by scrolling rather than collapsed into an overflow line

#### Scenario: Body content is clipped to the viewport

- **WHEN** a scrolled panel draws its body
- **THEN** content is rendered only within the viewport rectangle (clipped via `pushClip`/`popClip`)
- **AND** content above or below the viewport is not painted over neighboring panels or the header

### Requirement: Scroll offset is derived state, clamped on read

The only scroll state a `ScreenDebugWidget` SHALL store is a single logical scroll offset. The offset MUST be clamped on read to `0 .. max(0, contentExtent - viewport)` against the current frame's content extent and viewport — never stored as absolute pixels. The scrollbar grabber size and position MUST be derived per frame from `(contentExtent, viewport, offset)` and not stored. As a consequence, a window resize or a dock change SHALL re-clamp the offset and re-derive the grabber automatically, with no resize-specific code path.

#### Scenario: Offset clamps to content bounds

- **WHEN** the scroll offset would exceed `contentExtent - viewport` (e.g. after scrolling past the end)
- **THEN** the effective offset reads as `contentExtent - viewport`
- **AND** scrolling before the start clamps the effective offset to `0`

#### Scenario: Resize re-clamps the offset

- **WHEN** a panel is scrolled near its bottom and the window is then enlarged so the viewport grows
- **THEN** the effective offset re-clamps so content stays within bounds (no empty gap below the last row)
- **AND** no resize-specific branch is needed to achieve this

#### Scenario: Grabber is proportional to the visible fraction

- **WHEN** the viewport shows half of the content extent
- **THEN** the grabber height is half of the scrollbar track height
- **AND** the grabber position reflects the offset as a fraction of the scrollable range

### Requirement: Scrollbar visible only when scrollable

A `ScreenDebugWidget` SHALL draw a vertical scrollbar (track plus proportional grabber) only when content extent exceeds the viewport. When content fits, no scrollbar SHALL be drawn and no horizontal space SHALL be reserved for it. Scrolling SHALL be vertical only; the panel width auto-fits its longest row and there is no horizontal scrollbar.

#### Scenario: No scrollbar when content fits

- **WHEN** content extent is within the viewport
- **THEN** no scrollbar track or grabber is drawn

#### Scenario: Scrollbar appears when content overflows

- **WHEN** content extent exceeds the viewport
- **THEN** a vertical scrollbar with a proportional grabber is drawn at the panel's right edge

### Requirement: Mouse-wheel scrolling routed by the panel under the pointer

Wheel scrolling SHALL be resolved during the `SceneTree.hitTestUI(input)` phase (before `tree.process`), not inside per-widget `onProcess`, so a gameplay node that processes earlier cannot react to a wheel that a debug panel absorbed. The phase MUST resolve the top-most panel under the pointer (via `DebugRegistry.topPanelAt`); if it is a scrollable `ScreenDebugWidget` whose viewport contains the pointer and `input.scrollDelta` is non-zero, it MUST apply the wheel delta to that panel's offset and set `input.scrollConsumed = true`. The scroll offset itself MUST remain owned by the widget. When no scrollable panel is under the pointer, the wheel SHALL pass through unconsumed.

#### Scenario: Wheel over a scrollable panel scrolls it and is consumed

- **WHEN** the pointer is over a scrollable panel's viewport and the user rolls the wheel down
- **THEN** that panel's content scrolls down
- **AND** `input.scrollConsumed` is `true` for that tick

#### Scenario: Consumed wheel does not reach gameplay

- **WHEN** a debug panel consumes the wheel during tick `N`
- **THEN** gameplay code observing `input.scrollDelta` while honoring `input.scrollConsumed` does not act on the wheel for tick `N`

#### Scenario: Wheel outside any panel passes through

- **WHEN** the pointer is not over any scrollable panel and the user rolls the wheel
- **THEN** `input.scrollConsumed` stays `false`
- **AND** no debug panel scrolls

### Requirement: Scrollbar grabber is draggable

When a scrollbar is shown, dragging its grabber SHALL set the scroll offset proportionally to the pointer's position along the track, reusing the panel's existing press/hold/release drag handling. A grabber drag MUST consume the drag (`input.mouseDragConsumed = true`) so it does not also pan the camera or drag world objects, and MUST be distinguishable from the header drag (only a press landing on the grabber rectangle starts a grabber drag).

#### Scenario: Dragging the grabber scrolls the content

- **WHEN** the user presses on the grabber and drags downward along the track
- **THEN** the content scrolls down proportionally to the drag distance
- **AND** `input.mouseDragConsumed` is `true` during the drag

#### Scenario: Header drag is not a grabber drag

- **WHEN** the user presses on the header (not the grabber) and drags
- **THEN** the panel moves/redocks as before and the scroll offset is unchanged

### Requirement: Scrolled rows hit-test at their drawn position

A panel whose body rows are interactive (e.g. the Inspector tree's node rows) SHALL hit-test those rows at their on-screen position after the scroll offset is applied, so the row a click selects is the row drawn under the pointer. Drawing and hit-testing MUST use the same offset for a given frame.

#### Scenario: Clicking a scrolled-into-view row selects it

- **WHEN** the tree is scrolled so a previously hidden node row is now visible, and the user clicks that row
- **THEN** that node is selected (the row under the pointer), not the node that occupied that screen position before scrolling
