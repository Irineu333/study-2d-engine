# Roadmap

Plano de evolução do `nengine`. **Active** = changes OpenSpec em andamento; **Planned** = intenção firmada mas sem change aberta ainda. Histórico de changes concluídas vive em [`openspec/changes/archive/`](./openspec/changes/archive/) — não duplicar aqui.

## Active

| Change              | Resumo                                                                                                                                                                                              |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `engine-lwjgl`      | Segundo backend ativo de render via LWJGL (NanoVG + GLFW + OpenGL 3.3 core); sentinela do invariante #4 através do entrypoint `runLwjgl` de `:games:demos`. Substitui o papel anterior do `:engine-compose`. |
| `ui-foundation`     | UI in-game base: `CanvasLayer` (Node screen-space) + `Panel` + `Button` (com signal `pressed` built-in, hit-test geométrico, `wasMouseClicked` consumido por UI), `DebugOverlayLayer` auto-inserido pela engine (FPS/colliders/momentum), `GameHost.render` proibido, demos cena 7 valida nos dois backends, jogos shipped (hello-world/pong/snake/tictactoe) migrados pra HUD em `CanvasLayer`. |
| `debug-widgets`     | Refatora infra de debug: `DebugWidget` (interface) + `ScreenDebugWidget`/`WorldDebugWidget` (bases sob Node/Node2D), `DebugRegistry` per-tree, `DebugLayer` com world + screen sub-containers, HUD `Panel`+`Button` listando widgets como rows toggláveis num único keybind (`GameConfig.debugHudKey`), `MomentumOverlay` singleton e flags em `tree.debug.*` substituídos por widgets que ownam o próprio estado, hosts param de tocar em debug por frame. Adicionar widget novo num jogo = 1 arquivo + 1 chamada `tree.debug.register(...)`. |

## Planned

| Change           | Resumo                                                                                                                                                                                                                                                                                                                                                                                   |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `game-asteroids` | Validador da `collision-overhaul` + integração com a fundação Godot-style: `Area2D` para balas, `CharacterBody2D` para nave/asteróides, `CollisionShape2D` + `CircleShape2D`, múltiplas shapes por objeto, signal cascade (asteróide quebra em pedaços), `Camera2D.bounds` para wrap-around, `Polygon2D`/`Line2D` wireframe; vai puxar `Renderer.withTransform` quando for implementado. |
| `game-billiards` | Jogo de bilhar como validador do impulso elástico + transferência angular da `TumblingSwarm`: 16 `CharacterBody2D` circulares numa mesa de 4 `StaticBody2D`, taco aplicando impulso linear no clique, 6 caçapas como `Area2D` removendo bolas no enter; exercita pair-hit simétrico, fricção Coulomb contra as tabelas e regras de turno em script Python.                               |
| `surface-units-spec` | Formaliza o espaço de coordenadas das SPIs: `tree.size`, `Renderer` e `Input.pointerPosition` em unidades lógicas (= surface AWT); HiDPI absorvido pelo backend via `canvas.scale(contentScale)`. Hoje a engine roda em pixels físicos por convenção tácita do `SkikoHost` — funciona, mas vaza `contentScale` como abstração da Skiko para `:engine`. Resolve o débito introduzido pelo fix de hit-test em monitores HiDPI. |
| `editor-visual`  | Vai dirigir decisões sobre serialização de cena, inspetor de propriedades e potencialmente composição.                                                                                                                                                                                                                                                                                   |
| `ui-controls-base` | Promove `Control` como base abstrata de Panel/Button/futuros widgets com `mouse_filter`, `focus`, `size_flags`. Adiado da `ui-foundation` por ser over-engineering num MVP de 2 widgets; promovido quando o conjunto crescer ou `ui-anchors`/`ui-focus` chegar. |
| `ui-anchors`     | Anchors/presets Godot 4-style (`anchor_left/top/right/bottom` + `offset_*`) pra Control posicionar relativo ao parent rect. Resolve o "buttons no canto que seguem o resize". |
| `ui-layout`      | Layout containers (`HBoxContainer`, `VBoxContainer`, `GridContainer`, `MarginContainer`) com `minimum_size`. Substitui o posicionamento manual de Buttons em menus reais. |
| `ui-focus`       | Focus + keyboard navigation (`grab_focus`, Tab/Shift+Tab) e signals `focusEntered`/`focusExited`. Pré-requisito pra TextEdit. |
| `ui-theme`       | Theme/StyleBox/font system: define cor/borda/font/padding por widget type via override aninhado. |
| `ui-input-events` | Modelo de eventos enfileirados estilo Godot `_input`/`_gui_input` com `event.accept()`. Só se o polling+consumed atual da `ui-foundation` virar dolorido. |

## Como manter

- **Nova change criada** (`/opsx:propose <name>`) → adiciona linha em **Active** com resumo de uma frase.
- **Change archived** (`/opsx:archive <name>`) → remove a linha daqui. O histórico passa a viver em `openspec/changes/archive/<date>-<name>/`.
- **Ideia firmada sem change ainda** → linha em **Planned**. Quando virar change, promove para Active.
- Resumos devem caber numa linha do tipo "o que muda + por quê", não a lista completa de tasks.
