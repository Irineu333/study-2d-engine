## ADDED Requirements

### Requirement: Renderer clip stack

The `Renderer` SPI SHALL expose a LIFO rectangular clip stack as the natural pair of the existing transform stack:

```
fun pushClip(rect: Rect)
fun popClip()
```

`pushClip(rect)` MUST push a clip region onto an internal LIFO stack; `rect` MUST be interpreted under the current transform stack (the same composition that `draw*` calls see). Every subsequent `draw*` call MUST be restricted to the intersection of all clip rects currently on the stack — a deeper `pushClip` MUST intersect with (never widen) the current clip. `popClip()` MUST restore the clip to the state before the matching `pushClip` and SHALL throw `IllegalStateException` if the clip stack is empty.

The clip stack SHALL start empty (no clip) at every backend-defined frame boundary, exactly like the transform stack. Every `pushClip` issued during a frame MUST be matched by a `popClip` before the frame boundary ends. Clip and transform pushes/pops MUST nest correctly when interleaved (e.g. `pushClip` → `pushTransform` → `popTransform` → `popClip`), since backends MAY share a single native save/restore stack for both. The interface MUST NOT expose backend-specific types.

#### Scenario: pushClip restricts subsequent draws to the rect

- **WHEN** code calls `renderer.pushClip(Rect(Vec2(0f, 0f), Vec2(100f, 50f)))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(200f, 200f)), Color.WHITE, filled = true)` then `renderer.popClip()`
- **THEN** only the portion of the rect within `(0,0)..(100,50)` is rasterized
- **AND** pixels outside the clip rect are untouched

#### Scenario: Nested clips intersect

- **WHEN** code pushes a clip of `(0,0)..(100,100)`, then pushes a clip of `(50,50)..(200,200)`, then draws a large rect
- **THEN** only the intersection `(50,50)..(100,100)` is rasterized

#### Scenario: Clip composes with the current transform

- **WHEN** code calls `renderer.pushTransform(Vec2(100f, 0f), 0f, Vec2(1f, 1f))` then `renderer.pushClip(Rect(Vec2(0f, 0f), Vec2(10f, 10f)))` then draws then `renderer.popClip()` then `renderer.popTransform()`
- **THEN** the clip is positioned under the active transform (covering surface `(100,0)..(110,10)`)

#### Scenario: Clip and transform pushes interleave without corruption

- **WHEN** code issues `pushClip(A)` → `pushTransform(T)` → draw → `popTransform()` → `popClip()`
- **THEN** after `popClip()` both the clip and the transform are restored to their pre-push state
- **AND** a subsequent draw is unaffected by `A` or `T`

#### Scenario: popClip on an empty stack fails fast

- **WHEN** code calls `renderer.popClip()` without a preceding `pushClip`
- **THEN** the call throws `IllegalStateException`

#### Scenario: Clip stack resets at the frame boundary

- **WHEN** a new frame begins on the backend (e.g. `SkikoRenderer.bind(canvas)`)
- **THEN** a draw issued before any `pushClip` is not restricted by any clip from a prior frame

### Requirement: Input scroll-wheel access

The `Input` SPI SHALL expose mouse-wheel state for the current tick:

- `val scrollDelta: Vec2` — the wheel delta accumulated during the current tick, where positive `y` MEANS scrolling down (toward later content) and positive `x` MEANS scrolling right. It SHALL read `Vec2.ZERO` on any tick with no wheel motion, and SHALL be reset at the start of every tick (during `beginTick()` or equivalent), exactly like the per-tick click state.
- `var scrollConsumed: Boolean` — a writable flag set by the `SceneTree.hitTestUI(input)` phase (or its scroll sibling) when a debug panel absorbs the wheel for the current tick. It SHALL be reset to `false` at the start of every tick. It MAY default to a no-op (always reads `false`, writes ignored) so an `Input` that never participates in scroll consumption needs no extra storage, mirroring `mouseDragConsumed`.

The interface MUST NOT expose backend-specific wheel event types.

#### Scenario: Wheel motion is reported for exactly one tick

- **WHEN** the user rolls the wheel down between tick `N-1` and tick `N`
- **THEN** `input.scrollDelta.y` is positive for every call within tick `N`
- **AND** `input.scrollDelta` reads `Vec2.ZERO` from tick `N+1` onward unless more wheel motion occurs

#### Scenario: No wheel motion reads as zero

- **WHEN** a tick passes with no wheel input
- **THEN** `input.scrollDelta` equals `Vec2.ZERO`

#### Scenario: scrollConsumed resets each tick

- **WHEN** the wheel was consumed during tick `N` (setting `scrollConsumed = true`) and no wheel motion occurs in tick `N+1`
- **THEN** at the start of tick `N+1`, `input.scrollConsumed` equals `false`
