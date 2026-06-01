## Why

O conjunto de widgets de debug cresceu de 5 para ~12 (fps, momentum, log, hud,
timeControls, profiler, scenePicker + gizmos), e a UI deles tem duas dores
concretas — ambas já visíveis no código de hoje:

1. **Sobreposição.** Cada `ScreenDebugWidget` escolhe um canto na mão, com
   pixels hardcoded. O `ScenePickerWidget` chega a documentar o mapa de cantos
   ("FPS/TimeControls top-left, Debug HUD top-right, Momentum/Log/Profiler
   bottom-left") — mas o mapa já está errado: três widgets colidem no top-left
   (`FpsWidget` em `(8,24)`, `TimeControlWidget` painel em `(12,12)`,
   `ProfilerWidget` em `pad 6` top-left), dois no bottom-left, e os quatro
   cantos estão tomados. O próximo widget não tem onde nascer.

2. **Inconsistência.** Convivem dois idiomas de render — uns desenham via
   `Panel`+`Button` (nós reais: `DebugHud`, `TimeControlWidget`), outros via
   `drawText`/`drawRect` imediato (`FpsWidget`, `MomentumWidget`,
   `ProfilerWidget`, `LogOverlayWidget`, `ScenePickerWidget`). A "chrome" de
   painel está duplicada e já divergiu: o fundo é `Color(0.10,0.10,0.12,0.85)`
   no HUD/Time mas `Color(0.08,0.08,0.10,0.88)` no picker; a borda
   `Color(0.55,0.55,0.60,1)` está copiada em três arquivos; as margens são
   `12f`/`8f`/`6f` para a mesma intenção; os tamanhos de texto vão de 18 a 10
   sem escala. `DebugColors.kt` centraliza cores de gizmo/log mas não a chrome
   dos painéis.

Esta change ataca **as duas dores na raiz**, sem exigir input novo nenhum:
um tema único + um coordenador de layout por slot. Drag e persistência de
posição ficam para a change seguinte (`debug-ui-draggable`), que depende desta.

## What Changes

- **`DebugTheme`** (novo) — fonte única de chrome: cor de fundo, cor/espessura
  de borda, margens, paddings e uma escala de texto nomeada (title/body/small).
  `DebugColors` (gizmo/log) passa a ser parte coerente do tema.
- **`DebugDock` + `DockSlot`** (novos) — coordenador de layout per-tree. Cada
  `ScreenDebugWidget` declara um `DockSlot` (`TOP_LEFT`, `TOP_RIGHT`,
  `BOTTOM_LEFT`, `BOTTOM_RIGHT`, `TOP_CENTER`, `BOTTOM_CENTER`). O dock empilha
  verticalmente os widgets de cada slot a partir do canto, com gutter do tema,
  re-fluindo a cada `tree.resize`. Acaba o hardcode de pixel por widget.
- **Contrato de tamanho/origin no `ScreenDebugWidget`** — cada widget reporta
  o `Vec2` que ocupa (medido a partir do conteúdo) e desenha a partir de um
  origin dado pelo dock, não de pixels absolutos próprios.
- **Migração dos cinco widgets de render imediato** (`FpsWidget`,
  `MomentumWidget`, `ProfilerWidget`, `LogOverlayWidget`, `ScenePickerWidget`)
  para o painel comum `Panel`+`Label` (nós reais, sob `ScreenDebugCanvas`) com
  a chrome do `DebugTheme` — unificando o idioma de render com
  `DebugHud`/`TimeControlWidget`.
- **Slots default por widget** escolhidos para zero colisão entre os built-ins,
  agora empilhados pelo dock em vez de hardcoded.

## Capabilities

### New Capabilities
- `debug-ui-shell`: o `DebugTheme` (chrome única), o `DebugDock`/`DockSlot`
  (layout por slot de canto, empilhamento vertical, re-fluxo no resize), e o
  contrato "widget reporta tamanho / dock dá o origin" sobre o
  `ScreenDebugWidget`.

### Modified Capabilities
- `debug-overlay`: o posicionamento screen-space de cada `ScreenDebugWidget`
  passa a ser responsabilidade do `DebugDock` (por `DockSlot` declarado), não
  de pixels hardcoded no próprio widget.

## Impact

- **Affected specs:** `debug-ui-shell` (nova), `debug-overlay` (modificada).
- **Affected code:** `:engine` `com.neoutils.engine.debug` — novo
  `DebugTheme`, `DebugDock`, `DockSlot`; refactor de `ScreenDebugWidget`
  (contrato de tamanho/origin) e dos 5 widgets imediatos; `DebugRegistry`
  passa o dock aos widgets no bind.
- **Dependência:** nenhuma nova. Reusa `Panel`/`Label`/`CanvasLayer` já
  shipped (`ui-foundation`).
- **Invariantes:** mantém #4 (host não toca `tree.debug.*` por frame; o dock é
  engine-internal sob a `DebugLayer`) e #6 (widgets screen-space seguem no
  `ScreenDebugCanvas`).
- **Não-objetivos:** arrastar/recolher painéis, persistir layout (ambos em
  `debug-ui-draggable`); containers genéricos de UI (`ui-layout`); tornar
  widgets de debug `@Serializable`.
