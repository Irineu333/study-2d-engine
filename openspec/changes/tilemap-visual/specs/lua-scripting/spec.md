## ADDED Requirements

### Requirement: TileMap and TileSet are exposed to Lua scripts and stubs

The Lua `ScriptHost` SHALL register `TileMap` (e o descritor `TileSet`) no namespace `nengine` (`nengine.TileMap`, `nengine.TileSet`), via o mesmo mecanismo `put(name, klass)` dos demais tipos. Os stubs LuaCATS publicados SHALL incluir entradas `TileMap` (extends `Node2D`, com `columns`/`rows`/`tiles`/`tile_set`) e `TileSet` (com `texture_path`/`tile_width`/`tile_height`).

#### Scenario: nengine.TileMap is available to scripts

- **WHEN** a Lua script reads `nengine.TileMap`
- **THEN** it resolves to the `TileMap` node type (not nil)

#### Scenario: LuaCATS stubs document TileMap and TileSet

- **WHEN** the published LuaCATS stubs are inspected
- **THEN** a `TileMap` class entry (extending `Node2D`) and a `TileSet` entry exist with their fields
