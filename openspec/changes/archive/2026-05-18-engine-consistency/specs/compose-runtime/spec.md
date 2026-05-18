## ADDED Requirements

### Requirement: GameSurface applies collider debug overlay

The `:engine-compose` `GameSurface` SHALL be responsible for rendering the collider debug overlay when `Debug.colliderVisualization` is enabled. After invoking `loop.tick(...)` for a frame, and before releasing the `DrawScope`, `GameSurface` MUST check `Debug.colliderVisualization` and, when `true`, enumerate active colliders in the scene (via the `:engine`-provided utility) and draw outlined rectangles using each collider's `bounds()`. The overlay MUST use a visually distinct color consistent with the previous in-engine implementation, so the user-visible behavior of the F2 toggle is preserved. When the flag is `false`, `GameSurface` MUST issue no extra draw calls beyond the scene's own render output.

#### Scenario: Overlay appears when flag is enabled

- **WHEN** `Debug.colliderVisualization = true` and `GameSurface` renders a frame for a scene containing at least one active `BoxCollider`
- **THEN** the rendered frame includes an outlined rectangle matching the collider's `bounds()`
- **AND** the rectangle is drawn after the scene's own `onRender` calls (i.e., on top of game visuals)

#### Scenario: Overlay disappears when flag is disabled

- **WHEN** `Debug.colliderVisualization = false`
- **THEN** `GameSurface` issues no overlay draw calls for that frame
- **AND** the scene renders identically to a build without the DX surface

#### Scenario: F2 toggle remains the user-facing affordance

- **WHEN** the user toggles `Debug.colliderVisualization` between ticks (e.g. via the F2 key handler already wired in the sample games)
- **THEN** the next frame rendered by `GameSurface` reflects the new flag state
