## MODIFIED Requirements

### Requirement: Bundle loading is backend-agnostic

The `Node` produced by `BundleLoader.fromResources(name)` or `BundleLoader.fromPath(dir)` (wrapped by the caller in `SceneTree(root = ...)`) MUST be consumable by any `GameHost` implementation without ajuste — specifically, both `SkikoHost` (in `:engine-skiko`) and `ComposeHost` (in `:engine-compose`) MUST be able to receive the resulting `SceneTree` and run it via their normal `run(tree, config)` entry point. The bundle pipeline MUST NOT depend on backend-specific types or assumptions.

#### Scenario: Pong tree runs in SkikoHost

- **GIVEN** the Pong bundle at `games/pong/src/main/resources/pong/`
- **WHEN** `BundleLoader.fromResources("pong", scripting = python)` is wrapped in `SceneTree(root = ...)` and consumed by `SkikoHost().run(tree, config)`
- **THEN** the game runs as expected (existing behavior, regression sentinel)

#### Scenario: Tic-tac-toe tree runs in ComposeHost

- **GIVEN** the Tic-tac-toe bundle at `games/tictactoe/src/main/resources/tictactoe/`
- **WHEN** `BundleLoader.fromResources("tictactoe", scripting = python)` is wrapped in `SceneTree(root = ...)` and consumed by `ComposeHost().run(tree, config)`
- **THEN** the game runs as expected
- **AND** the consumer (`:games:tictactoe`) does not call into any Skiko-specific or backend-specific code path

#### Scenario: No backend-specific imports in BundleLoader

- **WHEN** the `:engine-bundle` source tree is inspected
- **THEN** no class references `androidx.compose.*`, `org.jetbrains.compose.*`, or `org.jetbrains.skiko.*`
