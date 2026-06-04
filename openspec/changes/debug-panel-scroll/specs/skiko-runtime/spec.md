## ADDED Requirements

### Requirement: SkikoRenderer implements the clip stack

`SkikoRenderer` SHALL implement the `Renderer` SPI clip stack over the Skia canvas's native save/restore. `pushClip(rect)` MUST issue `canvas.save()` then `canvas.clipRect(...)` for the given rect (interpreted under the current transform, i.e. on the same canvas matrix state), so the clip composes with any previously pushed clip on the canvas's own state stack. `popClip()` MUST issue `canvas.restore()`. The implementation MAY track a depth counter to raise `IllegalStateException` on empty-stack pop, but MUST otherwise delegate the stack to Skia's `save`/`restore` so backend-native culling and clipping behave correctly. Clip and transform pushes MUST share the same `save`/`restore` discipline so interleaved pushes nest correctly. `unbind()` MUST be invoked with the clip stack empty.

#### Scenario: Clipped draw is restricted to the rect

- **WHEN** `skikoRenderer.pushClip(Rect(Vec2(0f, 0f), Vec2(100f, 50f)))` is issued, then `skikoRenderer.drawRect(Rect(Vec2(0f, 0f), Vec2(200f, 200f)), Color.WHITE, true)`, then `skikoRenderer.popClip()` inside a bound frame
- **THEN** only pixels within `(0,0)..(100,50)` are written on the Skia canvas

#### Scenario: popClip without pushClip fails fast

- **WHEN** `skikoRenderer.popClip()` is called with an empty clip stack inside a bound frame
- **THEN** the call throws `IllegalStateException`

### Requirement: SkikoInput ingests the mouse wheel

`SkikoInput` SHALL populate `Input.scrollDelta` from AWT mouse-wheel events, and `SkikoHost` SHALL register a `java.awt.event.MouseWheelListener` on the Skiko component routing events into `SkikoInput`. Because the wheel callback fires on the AWT thread, the accumulator MUST be thread-safe (atomic or `@Volatile`), mirroring the existing key/button handling. The accumulated wheel motion MUST be drained into `scrollDelta` at `beginTick()` so it is observable for exactly the following tick, with positive `y` meaning scroll-down. `SkikoInput` SHALL reset `scrollDelta` to `Vec2.ZERO` and `scrollConsumed` to `false` at the start of each tick.

#### Scenario: Wheel-down produces a positive y delta for one tick

- **WHEN** the user rolls the wheel down and the AWT `MouseWheelListener` fires before tick `N`
- **THEN** `skikoInput.scrollDelta.y` is positive for every call within tick `N`
- **AND** `skikoInput.scrollDelta` reads `Vec2.ZERO` in tick `N+1` absent further wheel motion

#### Scenario: scrollConsumed resets each tick

- **WHEN** `scrollConsumed` was set to `true` during tick `N` and no wheel motion occurs in tick `N+1`
- **THEN** at the start of tick `N+1`, `skikoInput.scrollConsumed` equals `false`
