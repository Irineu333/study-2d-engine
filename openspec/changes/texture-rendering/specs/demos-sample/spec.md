## ADDED Requirements

### Requirement: Demos includes a static Sprite2D scene as the texture-rendering sentinel

The `:games:demos` module SHALL include a scene that displays at least one static `Sprite2D` loading a PNG asset from `games/demos/src/main/resources/`. This scene is the living sentinel for the `texture-rendering` capability: it MUST render the same texture **identically (semantically)** on both backends — Skiko (default entrypoint) and LWJGL (`runLwjgl` task) — proving `Renderer.drawImage` + `tree.textures` work end-to-end before any animation or tilemap is built on top. The sprite MUST be sampled with nearest-neighbor (crisp pixel-art when scaled by camera/zoom).

#### Scenario: Sprite scene renders on the Skiko backend

- **WHEN** the demos app runs on Skiko and the sprite scene is shown
- **THEN** the PNG appears on screen, centered at its node position, with crisp (non-blurred) pixels when scaled

#### Scenario: Sprite scene renders identically on the LWJGL backend

- **WHEN** the demos app runs via the `runLwjgl` task and the sprite scene is shown
- **THEN** the same PNG appears in the same place with the same nearest-neighbor crispness as on Skiko
