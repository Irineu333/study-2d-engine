# Roadmap

Plano de evolução do `nengine`. **Active** = changes OpenSpec em andamento; **Planned** = intenção firmada mas sem change aberta ainda. Histórico de changes concluídas vive em [`openspec/changes/archive/`](./openspec/changes/archive/) — não duplicar aqui.

## Active

| Change              | Resumo                                                                                                                                                                                              |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `game-asteroids`    | Validador final da fundação Godot-style: `:games:asteroids` (Skiko + Python) com nave `CharacterBody2D` e wireframe `Polygon2D` rotacionando em tempo real, balas `Area2D`, N asteróides kinematic simultâneos e cascade de spawn dirigido por signal handler — sem código novo em `:engine`. |
| `game-pool8`        | Validador do impulso elástico multi-corpo + damping calibrado: `:games:pool8` (Skiko + Lua) com 16 bolas `RigidBody2D` numa mesa de 4 `StaticBody2D` + 6 caçapas `Area2D`, input "puxar e soltar", FSM de turno por quiescência e remoção dinâmica de nodes durante gameplay. |
| `debug-time-controls` | `timeScale`/`paused`/`requestStep` first-class na `SceneTree`; `GameLoop.tick` escala o `gameplayDt` e trata pause como `dt=0` (mantendo `process`/`hitTestUI`/`render` vivos p/ o HUD operar pausado). `TimeControlWidget` + atalhos vivos sob pause. Default preserva o tick. |
| `debug-profiler` | `FrameProfile` por-tree com ms por fase do tick (hitTest/physics/process/render/total) + contagem de steps; `GameLoop.tick` instrumenta via `nanoTime` quando habilitado (overhead zero off). `ProfilerWidget` com média móvel. Compõe com `debug-time-controls` no mesmo `tick`. |
| `debug-scene-inspector` | `SceneInspectorWidget` (remote scene tree): lista a hierarquia viva, seleção por clique self-contained, painel das `@Inspect` + transform world do selecionado. Reflexão só do selecionado por frame; helper público reusando o padrão do `SceneLoader`. Read-only no MVP. |
| `debug-ui-shell` | Mata sobreposição + inconsistência da UI de debug: `DebugTheme` (chrome única — fundo/borda/margens/escala de texto, absorve `DebugColors`) + `DebugDock`/`DockSlot` (layout por canto, empilhamento vertical, re-fluxo no resize) sobre `ScreenDebugWidget` ("widget reporta tamanho, dock dá o origin"); migra os 5 widgets de render imediato para `Panel`+`Label` temados. Sem input novo. |

## Planned

| Change           | Resumo                                                                                                                                                                                                                                                                                                                                                                                   |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
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
