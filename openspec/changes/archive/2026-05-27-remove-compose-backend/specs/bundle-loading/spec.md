## MODIFIED Requirements

### Requirement: Bundle loading is backend-agnostic

The `Node` produced by `BundleLoader.fromResources(name)` or `BundleLoader.fromPath(dir)` (wrapped by the caller in `SceneTree(root = ...)`) MUST be consumable by any `GameHost` implementation without ajuste — currently `SkikoHost` (in `:engine-skiko`) is the only active `GameHost` and MUST be able to receive the resulting `SceneTree` and run it via its normal `run(tree, config)` entry point. The bundle pipeline MUST NOT depend on backend-specific types or assumptions, so that future `GameHost` implementations (e.g. the planned LWJGL backend) work without changes to `:engine-bundle`. Bundles MUST NOT reference any module from `org.jetbrains.compose.*`, `androidx.compose.*`, or other backend-specific symbol space.

#### Scenario: Pong tree runs in SkikoHost

- **GIVEN** the Pong bundle at `games/pong/src/main/resources/pong/`
- **WHEN** `BundleLoader.fromResources("pong", scripting = python)` is wrapped in `SceneTree(root = ...)` and consumed by `SkikoHost().run(tree, config)`
- **THEN** the game runs as expected (existing behavior, regression sentinel)

#### Scenario: Tic-tac-toe tree runs in SkikoHost

- **GIVEN** the Tic-tac-toe bundle at `games/tictactoe/src/main/resources/tictactoe/`
- **WHEN** `BundleLoader.fromResources("tictactoe", scripting = lua)` is wrapped in `SceneTree(root = ...)` and consumed by `SkikoHost().run(tree, config)`
- **THEN** the game runs as expected (X plays first, alternation works, click-to-restart works after end)
- **AND** the consumer (`:games:tictactoe`) does not call into `:engine-compose` (that module is removed)

#### Scenario: No backend-specific imports in BundleLoader

- **WHEN** the `:engine-bundle` source tree is inspected
- **THEN** no class references `androidx.compose.*`, `org.jetbrains.compose.*`, `org.jetbrains.skia.*`, or `org.jetbrains.skiko.*`
