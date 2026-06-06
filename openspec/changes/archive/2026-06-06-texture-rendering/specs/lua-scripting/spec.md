## ADDED Requirements

### Requirement: Sprite2D is exposed to Lua scripts and stubs

The Lua `ScriptHost` SHALL register `Sprite2D` in the `nengine` namespace (`nengine.Sprite2D`), via the same `put(name, klass)` mechanism used for the other shipped node types, so scripts may reference it as an `extends` string and via `nengine.Sprite2D`. The published LuaCATS stubs SHALL include a `Sprite2D` class entry (extending `Node2D`) declaring its `texture_path`/`flip_h` fields so authoring tools get completion.

#### Scenario: nengine.Sprite2D is available to scripts

- **WHEN** a Lua script reads `nengine.Sprite2D`
- **THEN** it resolves to the `Sprite2D` node type (not nil)

#### Scenario: LuaCATS stub documents Sprite2D

- **WHEN** the published LuaCATS stubs are inspected
- **THEN** a `Sprite2D` class entry exists extending `Node2D`
