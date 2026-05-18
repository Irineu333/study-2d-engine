## MODIFIED Requirements

### Requirement: Collider debug visualization

The engine SHALL provide a debug-render mode in which all `Collider` nodes in the active scene have their `bounds()` drawn as outlined rectangles using a visually distinct color. The mode MUST be togglable at runtime through `Debug.colliderVisualization` (default: disabled). When disabled, no additional rendering overhead MUST be incurred beyond a single flag check per frame. The drawing itself SHALL be performed by the integrating runtime (e.g. `:engine-compose`'s `GameSurface`), not by `:engine.scene.Scene` or any other core scene-graph traversal. The `:engine` module SHALL expose a utility (e.g. `collectColliders(scene): List<Collider>` or equivalent) that the runtime can call to enumerate the colliders to outline, without requiring the runtime to walk the tree itself.

#### Scenario: Enabling collider debug draws bounds

- **WHEN** the collider debug mode is enabled and the scene contains a `BoxCollider`
- **THEN** the rendered frame includes a rectangle outline matching the collider's `bounds()`

#### Scenario: Disabling collider debug stops drawing bounds

- **WHEN** the collider debug mode is disabled
- **THEN** no collider outline is rendered
- **AND** scene rendering is otherwise unaffected

#### Scenario: Core scene-graph traversal does not draw collider bounds

- **WHEN** `Debug.colliderVisualization = true` and `scene.render(renderer)` is invoked **directly** without going through the runtime overlay
- **THEN** the rendered output contains no debug rectangles
- **AND** the overlay only appears when the integrating runtime applies it on top of `loop.tick`
