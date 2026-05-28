## ADDED Requirements

### Requirement: Demos scene 7 validates ui-foundation in both backends

The `:games:demos` module SHALL include a scene `7` "UI playground" accessible via the same `DemoSwitcherRoot` keybinding scheme as scenes `1`–`6` (pressing `7` switches to it). Scene 7 SHALL contain at minimum:

- Two `CanvasLayer` children of the demo root, with different `layer` values (e.g. `layer = 0` for the HUD layer, `layer = 10` for the menu layer), proving the z-order requirement.
- In the **menu layer** (top-most): three `Button` instances centered on the screen labeled "Start", "Settings", "Quit", each with a Python script attached that connects to `pressed` and writes a known string (`"start clicked"`, etc.) via `println` (or equivalent observable mechanism). The buttons SHALL exercise all four color states (`normalColor`, `hoverColor`, `pressedColor`, `disabledColor`); at least one of the three SHALL be `disabled = true` at startup to validate the disabled visual.
- In the **HUD layer** (below the menu): a `Panel` and two `Label`s rendering `"Score: 0"` and `"Lives: 3"` at bottom-left, statically positioned, proving HUDs do not zoom with `Camera2D`.
- A background world (e.g. one of the existing scenes 1–3 rendered behind the UI, OR a single `ColorRect` filling the canvas) — its sole purpose is to make screen-space UI visibly distinct from world-space content.

Scene 7 SHALL behave semantically identically in both `:games:demos:run` (Skiko) and `:games:demos:runLwjgl` (LWJGL) entrypoints: same buttons in same positions, same hover/press visuals, same `pressed` signal emission, same HUD layout. Purely visual differences (AA, text rendering) SHALL be accepted within tolerance.

#### Scenario: Pressing 7 switches to the UI playground

- **WHEN** the user presses `7` on either entrypoint
- **THEN** the displayed scene contains the menu (3 buttons centered) and the HUD (Score/Lives at bottom-left)

#### Scenario: Clicking Start emits pressed signal

- **WHEN** the user clicks the "Start" button rect on either entrypoint
- **THEN** the attached Python script's `pressed` handler runs exactly once
- **AND** the click is consumed (any gameplay script checking `tree.input.wasMouseClicked(Left)` sees `false`)

#### Scenario: Disabled button does not respond

- **WHEN** one button is `disabled = true` at startup (e.g. "Settings") and the user clicks its rect
- **THEN** the button renders with `disabledColor`
- **AND** `pressed` does NOT emit
- **AND** the click is NOT consumed (passes through to the world / any other UI below)

#### Scenario: HUD layer remains in screen position when window resized

- **WHEN** the user drags the window border to resize the surface
- **THEN** the "Score: 0" and "Lives: 3" labels remain at the bottom-left corner with constant pixel offsets
- **AND** the buttons remain centered horizontally relative to the new surface width

#### Scenario: Menu layer renders on top of HUD layer

- **WHEN** the menu layer (`layer = 10`) Button overlaps the HUD layer (`layer = 0`) Panel at some screen position
- **THEN** the menu Button is visible (drawn on top)
- **AND** clicking that overlap region triggers the menu Button's `pressed`, not the HUD

#### Scenario: Scene 7 runs in both backends

- **WHEN** the user opens scene 7 via `./gradlew :games:demos:run` and then via `./gradlew :games:demos:runLwjgl`
- **THEN** the menu, HUD, button states, and signal emissions behave identically (modulo backend-specific AA/text rendering differences)

## MODIFIED Requirements

### Requirement: Demos scenes 1–6 behave identically (semantically) on both backends

As cenas `1` Solar System, `2` Scale hierarchy, `3` Spawner, `4` Collision stress, `5` Rotating box, `6` Tumbling swarm SHALL produzir comportamento de gameplay semanticamente idêntico em ambos os entrypoints (`:games:demos:run` e `:games:demos:runLwjgl`). "Semanticamente idêntico" MUST ser interpretado como: mesmas key-bindings (`1`–`6` trocam de cena; `F1`/`F2`/`F3` togglam overlays via `tree.debug.showFps`/`tree.debug.showColliders`/`tree.debug.showMomentum`), mesma resposta a input (clique do mouse no Spawner adiciona bolinhas na posição esperada; arena boundaries reagem a resize), mesma evolução de física (mesmas trajetórias dado mesmo `physicsHz`), mesmas árvores de Nodes (cenas compartilham o mesmo código `DemoSwitcherRoot`).

Diferenças puramente visuais (anti-aliasing edge-expand do NanoVG vs Skia GPU AA; fontstash vs Skia text shaping; sub-pixel positioning) MUST ser aceitas dentro de tolerância — não constituem regressão. Qualquer divergência semântica (cena rodando errado, F-key não togglando, mouse fora de posição, arena não acompanhando resize) MUST ser tratada como bug do backend e investigada antes do merge.

#### Scenario: Switching scenes works identically on both backends

- **WHEN** o usuário pressiona `1` … `6` em qualquer dos dois entrypoints
- **THEN** a cena correspondente é exibida em ambos
- **AND** o conjunto de cenas disponíveis é o mesmo

#### Scenario: F1/F2/F3 toggles apply identically on both backends

- **WHEN** o usuário pressiona `F1`, `F2` ou `F3` em qualquer dos dois entrypoints
- **THEN** `tree.debug.showFps`, `tree.debug.showColliders`, `tree.debug.showMomentum` togglam respectivamente
- **AND** o overlay correspondente aparece/desaparece via os widgets do `DebugOverlayLayer` auto-inserido pela engine (não via helper do host)

#### Scenario: Spawner mouse click adds a ball at the click position on both backends

- **GIVEN** a cena `3` Spawner está ativa
- **WHEN** o usuário clica com o botão esquerdo em `(x, y)` em pixels da janela (e nenhum `Button` da UI está sob o ponteiro)
- **THEN** uma nova bolinha aparece com `position ≈ (x, y)` em ambos os backends
- **AND** o trap central remove a bolinha quando ela entra via `onAreaEntered`

#### Scenario: BoundaryWalls follow window resize on both backends

- **GIVEN** as cenas `4` Collision stress, `5` Rotating box ou `6` Tumbling swarm estão ativas
- **WHEN** o usuário redimensiona a janela
- **THEN** as 4 paredes (`topWall`/`bottomWall`/`leftWall`/`rightWall`) acompanham o novo `tree.size` no próximo `onPhysicsProcess` em ambos os backends
