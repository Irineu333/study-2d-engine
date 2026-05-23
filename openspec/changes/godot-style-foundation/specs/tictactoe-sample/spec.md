## MODIFIED Requirements

### Requirement: Tic-tac-toe uses Godot-style lifecycle names

The Kotlin source under `:games:tictactoe` SHALL override the Godot-style hook names (`onProcess`, `onDraw`, `onEnter`, `onExit`) and SHALL NOT override the legacy names (`onUpdate`, `onRender`). The text display SHALL use `Label`, not `Text`. The hover/click logic that currently lives in `onUpdate` SHALL move to `onProcess`. The drawing logic that currently lives in `onRender` SHALL move to `onDraw`.

#### Scenario: No legacy hook overrides exist

- **WHEN** any `.kt` file under `games/tictactoe/src/main/kotlin/` is inspected
- **THEN** no `override fun onUpdate` or `override fun onRender` exists

#### Scenario: Board overrides onProcess and onDraw

- **WHEN** `Board.kt` is inspected
- **THEN** it overrides `onProcess(dt: Float)` (covering the current hover + click handling)
- **AND** it overrides `onDraw(renderer: Renderer)` (covering the current draw)

#### Scenario: StatusText is a Label

- **WHEN** `StatusText.kt` (or equivalent) is inspected
- **THEN** the class is declared as `class StatusText : Label()` (or composes a `Label` field)
- **AND** there is no reference to a removed `Text` class

#### Scenario: Tic-tac-toe still runs on Compose backend

- **WHEN** a developer runs `./gradlew :games:tictactoe:run`
- **THEN** a desktop window opens displaying the tic-tac-toe scene rendered by `ComposeHost`
- **AND** click handling and grid rendering behave identically to before the rename
