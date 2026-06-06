## ADDED Requirements

### Requirement: Demos includes an AnimatedSprite2D scene as the animation sentinel

The `:games:demos` module SHALL include a scene that displays at least one `AnimatedSprite2D` cycling a real multi-frame sheet from `games/demos/src/main/resources/` (e.g. a 17-frame fruit or the 12-frame Run sheet). This scene is the living sentinel for the `sprite-animation` capability: it MUST visibly advance frames over time and render **identically (semantically)** on both backends — Skiko (default) and LWJGL (`runLwjgl`).

#### Scenario: Animation advances over time on Skiko

- **WHEN** the demos app runs on Skiko and the animation scene is shown
- **THEN** the sprite cycles through its frames over time (not a frozen frame)

#### Scenario: Animation renders identically on LWJGL

- **WHEN** the demos app runs via `runLwjgl` and the animation scene is shown
- **THEN** the sprite cycles through the same frames at the same rate as on Skiko
