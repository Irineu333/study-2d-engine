## ADDED Requirements

### Requirement: AnimatedSprite2D is exposed to Lua scripts and stubs

The Lua `ScriptHost` SHALL register `AnimatedSprite2D` in the `nengine` namespace (`nengine.AnimatedSprite2D`), via the same `put(name, klass)` mechanism used for the other shipped node types. The published LuaCATS stubs SHALL include an `AnimatedSprite2D` class entry (extending `Node2D`) declaring its `texture_path`/`frame_count`/`fps`/`loop`/`playing`/`current_frame`/`flip_h` fields so scripts get completion when driving animation state.

#### Scenario: nengine.AnimatedSprite2D is available to scripts

- **WHEN** a Lua script reads `nengine.AnimatedSprite2D`
- **THEN** it resolves to the `AnimatedSprite2D` node type (not nil)

#### Scenario: LuaCATS stub documents AnimatedSprite2D

- **WHEN** the published LuaCATS stubs are inspected
- **THEN** an `AnimatedSprite2D` class entry exists extending `Node2D` with its animation fields
