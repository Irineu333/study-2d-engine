## ADDED Requirements

### Requirement: Demos includes a TileMap scene as the tilemap sentinel

The `:games:demos` module SHALL include a scene that displays a `TileMap` assembling a piece of terrain from a real atlas (e.g. `Terrain (16x16).png`) in `games/demos/src/main/resources/`. This scene is the living sentinel for the `tilemap-visual` capability: it MUST render the grid of tiles in the correct positions and **identically (semantically)** on both backends — Skiko (default) and LWJGL (`runLwjgl`).

#### Scenario: TileMap renders terrain on Skiko

- **WHEN** the demos app runs on Skiko and the tilemap scene is shown
- **THEN** the terrain tiles appear assembled in their grid positions with crisp pixels

#### Scenario: TileMap renders identically on LWJGL

- **WHEN** the demos app runs via `runLwjgl` and the tilemap scene is shown
- **THEN** the same tiles appear in the same grid positions as on Skiko
