# Roadmap

Plano de evolução do `nengine`. **Active** = changes OpenSpec em andamento; **Planned** = intenção firmada mas sem change aberta ainda. Histórico de changes concluídas vive em [`openspec/changes/archive/`](./openspec/changes/archive/) — não duplicar aqui.

## Active

| Change              | Resumo                                                                                                                                                                                              |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `game-asteroids`    | Validador final da fundação Godot-style: `:games:asteroids` (Skiko + Python) com nave `CharacterBody2D` e wireframe `Polygon2D` rotacionando em tempo real, balas `Area2D`, N asteróides kinematic simultâneos e cascade de spawn dirigido por signal handler — sem código novo em `:engine`. |
| `game-pool8`        | Validador do impulso elástico multi-corpo + damping calibrado: `:games:pool8` (Skiko + Lua) com 16 bolas `RigidBody2D` numa mesa de 4 `StaticBody2D` + 6 caçapas `Area2D`, input "puxar e soltar", FSM de turno por quiescência e remoção dinâmica de nodes durante gameplay. |
| `trim-debug-tools`  | Enxuga o catálogo de debug: funde `ShapeGizmoWidget` no `ColliderWidget` (modo `ColliderDrawMode { AABB, REAL }`), embute a leitura de `fps` no `ProfilerWidget` e remove o `MomentumWidget` — o HUD cai de 12 → 8 linhas togláveis. `MomentumDiagnostics` permanece como API pública de física. |
| `debug-inspector`   | Promove o picker a um **Inspector** de scene graph: a view tree navegável (`SceneTreeWidget`) vira o mestre (navegação + dona da seleção + toggle), o painel de detalhe (`NodeInspectorWidget`, ex-`ScenePickerWidget`, sem breadcrumb/window controls) e o `SelectionGizmoWidget` viram escravos, e o `ScreenDebugWidget` ganha supressão de window controls (`closable`/`collapsible`). Supera `debug-scene-picker`. |
| `ui-controls-base`  | Promove `Control : Node2D` como base abstrata de `Panel`/`Button`/`Label`/`ColorRect` e entrega **anchors** como primeira capability ativa: anchors/offsets/presets Godot 4-style num anchor layout pass (mata o relayout-por-frame), `visible` (render + hit-test) e `mouse_filter` (`STOP`/`PASS`/`IGNORE`). Nasce com `focus_mode`/`size_flags` declarados-inertes. Absorve a antiga `ui-anchors`. |
| `audio-foundation`  | Fundação mínima de áudio (SFX curto, fire-and-forget): SPI `AudioBackend`/`Sound` Kotlin-pura em `:engine`, campo `tree.audio` nullable (espelha `textMeasurer`, disposto no `stop`), módulo backend host-agnóstico `:engine-audio-javasound` (JDK `javax.sound.sampled`, WAV-only, vozes sobrepostas) wirado por `SkikoHost`/`LwjglHost`, e `:games:pong` como sentinela viva tocando rebatida e gol via Python. |
| `texture-rendering` | Fundação mínima de texturas (1ª de 4 changes pró demo de plataforma): SPI `TextureBackend`/`Texture` (só `width`/`height`) Kotlin-pura em `:engine`, campo `tree.textures` nullable (espelha `audio`/`textMeasurer`, cache por path, disposto no `stop`), `Renderer.drawImage(src, dst, flipH)` nearest-neighbor, node `Sprite2D` centrado, backends por módulo de render (`SkikoTextureBackend`/`LwjglTextureBackend`), cena sentinela cross-backend em `:games:demos` e binding Lua `nengine.Sprite2D`. |
| `sprite-animation`  | Animação de sprite sobre `texture-rendering` (2ª de 4 changes pró demo de plataforma): node `AnimatedSprite2D : Node2D` ciclando um **sheet horizontal** de `frameCount` quadros, com avanço de quadro dirigido pela engine no `onProcess` (`fps`/`loop`/`playing`/`currentFrame`/`flipH`) e desenho do quadro corrente reusando `drawImage` (sem op nova de `Renderer`), cena sentinela cross-backend em `:games:demos` e binding Lua `nengine.AnimatedSprite2D`. |
| `tilemap-visual`    | Tilemap **visual-only** sobre `texture-rendering` (3ª de 4 changes pró demo de plataforma): descritor `TileSet` (`@Serializable` não-Node: `texturePath`/`tileWidth`/`tileHeight`, `columns` derivado do atlas, `src(index)` row-major) e node `TileMap : Node2D` (grade `columns`/`rows`/`tiles: List<Int>`, `-1` vazio, origem no canto, `drawImage` por célula). **Sem colisão** — terreno sólido é `StaticBody2D` à mão (invariante #3 intacto). Cena sentinela cross-backend em `:games:demos` e bindings Lua `nengine.TileMap`/`nengine.TileSet`. |

## Planned

| Change           | Resumo                                                                                                                                                                                                                                                                                                                                                                                   |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `surface-units-spec` | Formaliza o espaço de coordenadas das SPIs: `tree.size`, `Renderer` e `Input.pointerPosition` em unidades lógicas (= surface AWT); HiDPI absorvido pelo backend via `canvas.scale(contentScale)`. Hoje a engine roda em pixels físicos por convenção tácita do `SkikoHost` — funciona, mas vaza `contentScale` como abstração da Skiko para `:engine`. Resolve o débito introduzido pelo fix de hit-test em monitores HiDPI. |
| `editor-visual`  | Vai dirigir decisões sobre serialização de cena, inspetor de propriedades e potencialmente composição.                                                                                                                                                                                                                                                                                   |
| `ui-layout`      | Layout containers (`HBoxContainer`, `VBoxContainer`, `GridContainer`, `MarginContainer`) com `minimum_size`. **Acende** os campos `size_flags_horizontal`/`size_flags_vertical` que `Control` já declara (inertes hoje). Substitui o posicionamento manual de Buttons em menus reais. |
| `ui-focus`       | Focus + keyboard navigation (`grab_focus`, Tab/Shift+Tab) e signals `focusEntered`/`focusExited`. **Acende** os campos `focus_mode`/`focus_neighbor_*` que `Control` já declara (inertes hoje). Pré-requisito pra TextEdit. |
| `ui-theme`       | Theme/StyleBox/font system: define cor/borda/font/padding por widget type via override aninhado. |
| `ui-input-events` | Modelo de eventos enfileirados estilo Godot `_input`/`_gui_input` com `event.accept()`. Só se o polling+consumed atual da `ui-foundation` virar dolorido. |

## Dívidas conhecidas

Bugs/limitações **pré-existentes** já mapeados, a endereçar numa change futura.

_Nenhuma dívida aberta no momento._ A dívida "HUD screen-space não acompanha o resize (snake, tictactoe)" foi fechada pela change `ui-stretch`: `CanvasLayer` ganhou `followStretch` (default `true`) e a `SceneTree` passou a esticar a UI de design-space (`designSize`) sobre a superfície, alinhando e escalando o HUD junto com o mundo.

## Como manter

- **Nova change criada** (`/opsx:propose <name>`) → adiciona linha em **Active** com resumo de uma frase.
- **Change archived** (`/opsx:archive <name>`) → remove a linha daqui. O histórico passa a viver em `openspec/changes/archive/<date>-<name>/`.
- **Ideia firmada sem change ainda** → linha em **Planned**. Quando virar change, promove para Active.
- Resumos devem caber numa linha do tipo "o que muda + por quê", não a lista completa de tasks.
