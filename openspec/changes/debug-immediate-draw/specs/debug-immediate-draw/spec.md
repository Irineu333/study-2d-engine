## ADDED Requirements

### Requirement: DebugDraw facade with world and screen canvases

`DebugRegistry` SHALL expose `val draw: DebugDraw`, a per-`SceneTree`
immediate-mode drawing facade. `DebugDraw` SHALL expose two symmetric
`DebugCanvas` surfaces: `val world: DebugCanvas` (drawn under the active
`Camera2D` view transform) and `val screen: DebugCanvas` (drawn in screen
pixels). Each `DebugCanvas` SHALL provide immediate-mode verbs mirroring
`Renderer`:

- `fun line(from: Vec2, to: Vec2, color: Color, thickness: Float = 1f)`
- `fun rect(rect: Rect, color: Color, filled: Boolean = false)`
- `fun circle(center: Vec2, radius: Float, color: Color, filled: Boolean = false, thickness: Float = 1f)`
- `fun polygon(points: List<Vec2>, color: Color)`
- `fun text(position: Vec2, text: String, color: Color, size: Float = 14f)`

Each verb call (when drawing is enabled — see "no-op when disabled") SHALL
enqueue a single immutable draw command into that canvas's per-frame
buffer. `DebugDraw` SHALL NOT be a `Node`, SHALL NOT be `@Serializable`,
and SHALL NOT be shared across `SceneTree` instances.

#### Scenario: world and screen are distinct buffers

- **GIVEN** `tree.debug.draw.enabled = true`
- **WHEN** `tree.debug.draw.world.line(a, b, c)` and `tree.debug.draw.screen.text(p, "hi", c)` are called
- **THEN** the world buffer SHALL hold exactly one command and the screen buffer exactly one command
- **AND** the two commands SHALL be in their respective canvas buffers, not mixed

#### Scenario: each verb enqueues one command

- **GIVEN** `tree.debug.draw.enabled = true`
- **WHEN** `circle`, `rect`, and `polygon` are each called once on `tree.debug.draw.world`
- **THEN** the world buffer SHALL hold exactly three commands in call order

### Requirement: Commands are single-frame — flushed during render, cleared at render tail

The engine SHALL auto-insert two internal backing nodes under the
`DebugLayer` during `SceneTree.start()`: an `ImmediateWorldDrawNode`
(`Node2D`) child of the `WorldDebugContainer`, and an
`ImmediateScreenDrawNode` (`Node`) child of the `ScreenDebugCanvas`. During
`SceneTree.render`, the world backing node SHALL emit every command in
`draw.world` onto the `Renderer` within the world pass (already under the
`Camera2D` view transform, issuing no `pushTransform` of its own), and the
screen backing node SHALL emit every command in `draw.screen` within the UI
pass.

After both passes complete, `SceneTree.render` SHALL clear both buffers
(`draw.clearFrame()`), so a command enqueued during `process` /
`physicsProcess` of a tick is drawn exactly once — on that tick's render —
and then discarded. Commands SHALL NOT accumulate across frames, and no
manual cleanup SHALL be required. The clearing SHALL happen inside
`SceneTree.render`; `GameHost` and `GameLoop` SHALL NOT be involved.

#### Scenario: command enqueued before render is drawn that frame

- **GIVEN** `tree.debug.draw.enabled = true`
- **WHEN** a node enqueues `tree.debug.draw.world.line(a, b, color)` during `physicsProcess`, then the tree renders against a recording `Renderer`
- **THEN** exactly one `drawLine(a, b, _, color)` SHALL be observed in the world pass

#### Scenario: buffers are empty after render

- **GIVEN** `tree.debug.draw.enabled = true` with several commands enqueued
- **WHEN** `SceneTree.render` completes
- **THEN** both `draw.world` and `draw.screen` buffers SHALL be empty

#### Scenario: no accumulation across frames

- **GIVEN** `tree.debug.draw.enabled = true`
- **WHEN** one command is enqueued and rendered, then a second frame renders with no new command enqueued
- **THEN** the second frame SHALL emit zero draw commands

### Requirement: Verbs are no-ops when drawing is disabled

`DebugDraw` SHALL expose `var enabled: Boolean` defaulting to `false`. When
`enabled` is `false`, every `DebugCanvas` verb SHALL be a no-op that
enqueues nothing and returns immediately, so calling draw verbs every frame
from game or script code SHALL carry negligible cost. When `enabled` is
`true`, verbs SHALL enqueue as specified.

A single internal `DebugWidget` proxy titled `"Debug Draw"` SHALL be
registered so the `DebugHud` lists exactly one togglable row that flips
`tree.debug.draw.enabled`. This proxy SHALL itself draw nothing.

#### Scenario: disabled verbs enqueue nothing

- **GIVEN** `tree.debug.draw.enabled = false`
- **WHEN** `tree.debug.draw.world.line(a, b, color)` is called
- **THEN** the world buffer SHALL remain empty
- **AND** a subsequent render SHALL emit zero draw commands for the backing nodes

#### Scenario: HUD shows a single Debug Draw row

- **GIVEN** the engine has finished auto-inserting `DebugLayer`
- **WHEN** the `DebugHud` is opened
- **THEN** exactly one row titled `"Debug Draw"` SHALL be present
- **AND** clicking it SHALL flip `tree.debug.draw.enabled`

### Requirement: Immediate-draw is callable from Python and Lua scripts

Scripts SHALL be able to enqueue immediate-draw commands via
`self.tree.debug.draw.world.<verb>(...)` and
`self.tree.debug.draw.screen.<verb>(...)`, using the `Vec2` and `Color`
values already bound in each script host. The Python host SHALL ship `.pyi`
stubs and the Lua host SHALL ship LuaCATS stubs covering the `DebugDraw`,
`DebugCanvas`, and verb signatures.

#### Scenario: Python script enqueues a world line

- **GIVEN** `tree.debug.draw.enabled = true` and a Python-scripted node
- **WHEN** the script calls `self.tree.debug.draw.world.line(Vec2(0, 0), Vec2(10, 10), Color(1, 0, 0, 1))` during `_physics_process`
- **THEN** the world buffer SHALL hold one line command after that tick's physics phase

#### Scenario: Lua script enqueues a screen circle

- **GIVEN** `tree.debug.draw.enabled = true` and a Lua-scripted node
- **WHEN** the script calls `self.tree.debug.draw.screen:circle(...)` during `_process`
- **THEN** the screen buffer SHALL hold one circle command after that tick's process phase
