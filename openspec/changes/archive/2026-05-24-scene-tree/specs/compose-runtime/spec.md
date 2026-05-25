## MODIFIED Requirements

### Requirement: GameSurface composable drives the game loop

The `:engine-compose` module SHALL provide a `GameSurface(tree: SceneTree)` composable that hosts the rendering canvas and drives ticks via `withFrameNanos`. Each frame, `GameSurface` MUST: (1) snapshot input state, (2) invoke `GameLoop.tick(dtNanos)`, (3) trigger redraw of its canvas with the produced visual state. `GameSurface` MUST stop ticking when removed from composition.

#### Scenario: GameSurface accepts a SceneTree

- **WHEN** the source of `GameSurface` is inspected
- **THEN** the parameter type is `SceneTree`, not `Scene`
- **AND** no reference to the symbol `Scene` exists in the file

#### Scenario: GameSurface ticks once per Compose frame

- **WHEN** `GameSurface(tree)` is placed in a Compose tree displayed at 60 frames per second
- **THEN** `GameLoop.tick` is invoked approximately 60 times per second

#### Scenario: Ticks stop on composition removal

- **WHEN** the `GameSurface` composable leaves composition
- **THEN** no further `GameLoop.tick` calls occur for that tree

#### Scenario: First tick uses sensible delta

- **WHEN** `GameSurface(tree)` is composed for the first time
- **THEN** the first `tick` receives a `dtNanos` value greater than zero
- **AND** does not propagate a spike caused by initial frame timing (clamped to a reasonable maximum)

### Requirement: GameSurface applies collider debug overlay

The `:engine-compose` `GameSurface` SHALL be responsible for rendering the debug overlay when `Debug.colliderVisualization` or `Debug.showFps` is enabled. After invoking `loop.tick(...)` for a frame, and before releasing the `DrawScope`, `GameSurface` MUST invoke `renderDebugOverlay(renderer, tree)` from `:engine` so the overlay drawing is centralized and identical across backends. `GameSurface` MUST NOT duplicate the drawing logic locally. When both `Debug` flags are `false`, `GameSurface` MUST issue no extra draw calls beyond the tree's own render output and a single flag-check inside `renderDebugOverlay`.

#### Scenario: Overlay appears when collider flag is enabled

- **WHEN** `Debug.colliderVisualization = true` and `GameSurface` renders a frame for a tree containing at least one active `BoxCollider`
- **THEN** the rendered frame includes an outlined rectangle matching the collider's `bounds()`
- **AND** the rectangle is drawn after the tree's own `onDraw` calls (i.e., on top of game visuals)

#### Scenario: Overlay disappears when both flags are disabled

- **WHEN** `Debug.colliderVisualization = false` and `Debug.showFps = false`
- **THEN** `GameSurface` issues no overlay draw calls for that frame
- **AND** the tree renders identically to a build without the DX surface

#### Scenario: Toggle key flips state by the next frame

- **WHEN** the user presses the configured FPS or colliders toggle key between ticks
- **THEN** the next frame rendered by `GameSurface` reflects the new flag state

#### Scenario: Drawing logic is not duplicated

- **WHEN** the source of `GameSurface.kt` is inspected
- **THEN** the only overlay-drawing code is a single call to `renderDebugOverlay(renderer, tree)`
- **AND** no inline `for (collider in collectColliders(tree)) renderer.drawRect(...)` or inline FPS `drawText` survives in this file

### Requirement: ComposeHost implements GameHost over GameSurface

The `:engine-compose` module SHALL provide a concrete `GameHost` implementation, `ComposeHost`, that opens a Compose `application { Window { } }`, hosts a `GameSurface` composable inside it, and returns from `run(...)` only after the Window is closed. `ComposeHost.run(tree: SceneTree, config: GameConfig)` MUST honor `GameConfig.title`, `GameConfig.width`, and `GameConfig.height` to size and label the Compose `Window`. `ComposeHost` MUST consume `GameConfig.toggleFpsKey` and `GameConfig.toggleCollidersKey` via the `Input` SPI inside the running tree, so toggling debug overlays does not require game code to install a Compose `onKeyEvent` handler. The existing `GameSurface(tree)` composable MUST remain exported for callers that need to embed the engine inside a larger Compose tree, but `ComposeHost` SHALL be the recommended entry point.

#### Scenario: ComposeHost.run opens a Compose window and blocks

- **WHEN** code calls `ComposeHost().run(SceneTree(root = ticTacToeRoot), GameConfig("Tic Tac Toe", 600, 600))`
- **THEN** a Compose desktop Window opens with title `"Tic Tac Toe"` and size 600 by 600
- **AND** the call does not return while the Window remains open
- **AND** when the user closes the Window, the call returns

#### Scenario: ComposeHost.run signature accepts SceneTree

- **WHEN** the source of `ComposeHost.run` is inspected
- **THEN** the parameter type is `SceneTree`, not `Scene`
- **AND** no reference to the symbol `Scene` exists in the file

#### Scenario: ComposeHost honors toggle keys via Input SPI

- **WHEN** `ComposeHost().run(tree, GameConfig(toggleFpsKey = Key.F1, toggleCollidersKey = Key.F2))` is running and the user presses F1
- **THEN** `Debug.showFps` flips by the next rendered frame
- **AND** no game-side Compose `onKeyEvent` handler is required for this behavior

#### Scenario: GameSurface stays exported for advanced embedding

- **WHEN** code in `:games:tictactoe` (or any module) imports `com.neoutils.engine.compose.GameSurface`
- **THEN** the import resolves
- **AND** `GameSurface(tree, modifier)` can be placed inside a custom Compose tree
