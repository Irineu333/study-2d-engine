# Roadmap

Plano de evolução do `nengine`. **Active** = changes OpenSpec em andamento; **Planned** = intenção firmada mas sem change aberta ainda. Histórico de changes concluídas vive em [`openspec/changes/archive/`](./openspec/changes/archive/) — não duplicar aqui.

## Active

| Change              | Resumo                                                                                                                                                                                              |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `game-asteroids`    | Validador final da fundação Godot-style: `:games:asteroids` (Skiko + Python) com nave `CharacterBody2D` e wireframe `Polygon2D` rotacionando em tempo real, balas `Area2D`, N asteróides kinematic simultâneos e cascade de spawn dirigido por signal handler — sem código novo em `:engine`. |
| `game-pool8`        | Validador do impulso elástico multi-corpo + damping calibrado: `:games:pool8` (Skiko + Lua) com 16 bolas `RigidBody2D` numa mesa de 4 `StaticBody2D` + 6 caçapas `Area2D`, input "puxar e soltar", FSM de turno por quiescência e remoção dinâmica de nodes durante gameplay. |

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
| `debug-log-overlay` | Ponte entre os dois pilares de debug: `LogOverlayWidget` (screen-space) que também é um `LogSink`, mostrando as últimas N entradas do `Log` na tela com cor por nível e filtro por tag. Mais barata do bloco de debug — quase zero arquitetura nova sobre `debug-overlay` + `dx-tooling`. |
| `debug-immediate-draw` | Primitiva immediate-mode `tree.debug.draw.line/circle/text(...)` que acumula gizmos por frame e limpa no fim, exposta a Kotlin e a Python/Lua — substitui o boilerplate de subclassar `WorldDebugWidget` para "só quero ver essa linha esse frame". Destrava `debug-physics-gizmos`. |
| `debug-physics-gizmos` | Expõe o que o `PhysicsSystem` calcula mas esconde: forma real do collider (círculo, retângulo rotacionado) em vez de só AABB, vetores de velocidade, e pontos/normais de contato resolvidos. Maior retorno didático. Auto-contida — contatos exigem captura durante o TOI loop (buffer por-tree no `tree.debug`), não só desenho, então não depende de `debug-immediate-draw` (decisão revista na proposta). |
| `debug-time-controls` | `tree.timeScale`, pause e step-frame controlados por row no HUD / teclas — cirúrgico no `GameLoop.tick`. Decisão de design: qual `dt` o `timeScale` afeta (`_physics_process`, `_process`, timers?). Toca o mesmo `tick` que `debug-profiler`. |
| `debug-profiler` | Profiler por fase do frame (physics step, script process, render, hitTestUI) com ms por fase — ensina onde o tempo vai, além do FPS agregado. Reusa os hooks de timing instrumentados no `GameLoop` por `debug-time-controls`. |
| `debug-scene-inspector` | Remote scene tree inspector (screen-space): lista a hierarquia viva de Nodes e expande um node mostrando transform/velocity/properties via a disciplina `@Inspect` existente. Mais pesado do bloco; feito por último com a primitiva de draw e os controles de tempo no lugar. |

## Como manter

- **Nova change criada** (`/opsx:propose <name>`) → adiciona linha em **Active** com resumo de uma frase.
- **Change archived** (`/opsx:archive <name>`) → remove a linha daqui. O histórico passa a viver em `openspec/changes/archive/<date>-<name>/`.
- **Ideia firmada sem change ainda** → linha em **Planned**. Quando virar change, promove para Active.
- Resumos devem caber numa linha do tipo "o que muda + por quê", não a lista completa de tasks.
