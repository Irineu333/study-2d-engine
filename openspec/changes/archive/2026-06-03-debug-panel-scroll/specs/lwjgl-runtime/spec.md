## ADDED Requirements

### Requirement: LwjglRenderer implements the clip stack over NanoVG scissor

`LwjglRenderer` SHALL implement the `Renderer` SPI clip stack using NanoVG scissor. Since NanoVG has no scissor-pop, the implementation MUST emulate the LIFO stack with NanoVG's own state stack: `pushClip(rect)` MUST issue `nvgSave()` then `nvgIntersectScissor(...)` for the given rect (interpreted under the current transform), so a deeper clip intersects with the current one; `popClip()` MUST issue `nvgRestore()`. The implementation MAY track a depth counter to raise `IllegalStateException` on empty-stack pop. `pushTransform` and `pushClip` MUST share the same `nvgSave`/`nvgRestore` discipline so interleaved pushes nest correctly.

#### Scenario: Clipped draw is restricted to the rect

- **WHEN** `lwjglRenderer.pushClip(Rect(Vec2(0f, 0f), Vec2(100f, 50f)))` is issued, then a large rect is drawn, then `lwjglRenderer.popClip()` inside a frame
- **THEN** only fragments within `(0,0)..(100,50)` are rasterized

#### Scenario: Nested clips intersect

- **WHEN** clips `(0,0)..(100,100)` and `(50,50)..(200,200)` are pushed in order and a large rect is drawn
- **THEN** only the intersection `(50,50)..(100,100)` is rasterized

#### Scenario: popClip without pushClip fails fast

- **WHEN** `lwjglRenderer.popClip()` is called with an empty clip stack
- **THEN** the call throws `IllegalStateException`

### Requirement: LwjglInput ingests the mouse wheel over a GLFW scroll callback

`LwjglInput` SHALL populate `Input.scrollDelta` from GLFW scroll events, and `LwjglHost` SHALL register `glfwSetScrollCallback` on the window routing `(xoffset, yoffset)` into `LwjglInput`. Since the callback fires synchronously from `glfwPollEvents()` on the render-loop thread, plain accumulation is race-free. The accumulated wheel motion MUST be drained into `scrollDelta` at `beginTick()` so it is observable for exactly the following tick, with positive `y` meaning scroll-down (GLFW reports wheel-up as positive `yoffset`, so the sign MUST be inverted to match the SPI contract). `LwjglInput` SHALL reset `scrollDelta` to `Vec2.ZERO` and `scrollConsumed` to `false` at the start of each tick.

#### Scenario: Wheel-down produces a positive y delta for one tick

- **WHEN** the GLFW scroll callback fires with a downward wheel motion before tick `N`
- **THEN** `lwjglInput.scrollDelta.y` is positive for every call within tick `N`
- **AND** `lwjglInput.scrollDelta` reads `Vec2.ZERO` in tick `N+1` absent further wheel motion

#### Scenario: scrollConsumed resets each tick

- **WHEN** `scrollConsumed` was set to `true` during tick `N` and no wheel motion occurs in tick `N+1`
- **THEN** at the start of tick `N+1`, `lwjglInput.scrollConsumed` equals `false`
