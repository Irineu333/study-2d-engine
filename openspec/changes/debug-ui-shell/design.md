# Design — Debug UI Shell

## Context

A change `debug-widgets` estabeleceu o `DebugRegistry` + `DebugLayer` com dois
espaços (world/screen). Desde então o conjunto cresceu por changes incrementais
(`debug-profiler`, `debug-time-controls`, `debug-scene-picker`...), cada uma
posicionando seu widget screen-space na mão. O resultado é colisão de canto e
divergência de estilo (ver `proposal.md`). Esta change introduz a camada que
faltava: um tema e um coordenador de layout compartilhados, mantendo os widgets
existentes como folhas.

## Goals / Non-Goals

**Goals:**
- Zero sobreposição entre widgets built-in, e um lugar óbvio para o próximo.
- Um idioma de render e uma chrome únicos para todo painel de debug.
- Re-fluir no `tree.resize` sem cada widget reimplementar o re-anchor.

**Non-Goals:**
- Arrastar/recolher painéis em runtime (fica para `debug-ui-draggable`).
- Persistir layout (sessão ou disco) — idem.
- Containers genéricos de UI (`HBox`/`VBox`): o dock é específico de debug e
  não antecipa `ui-layout`; quando `ui-layout` chegar, o dock pode migrar.
- Tornar widgets de debug `@Serializable`.

## Decisions

### Decision: Layout por slot de canto, não por pixel
Cada `ScreenDebugWidget` declara um `DockSlot` (canto/centro). O `DebugDock`
empilha verticalmente os widgets de um slot, do canto para dentro, com gutter
do tema. Isso é determinístico, trivial de raciocinar, e resolve a sobreposição
sem introduzir input. Alternativa rejeitada: anchors estilo Godot
(`ui-anchors`) — mais poderoso, mas é uma change de UI de jogo, não de debug, e
seria over-engineering para 6 ancoragens fixas.

### Decision: Widget reporta tamanho; dock dá o origin
O contrato fino: o widget sabe **quanto** ocupa (a partir do seu conteúdo); o
dock sabe **onde** começa. Isso inverte o controle atual (widget cravava
`(8,24)`) sem obrigar o widget a conhecer os vizinhos. Widgets de altura
variável (picker, log) reportam o tamanho corrente a cada frame; o dock re-empilha.

### Decision: Migrar os 5 imediatos para Panel+Label
Hoje `DebugHud`/`TimeControlWidget` usam nós reais (`Panel`+`Button`) e os
outros desenham imediato. Unificar em `Panel`+`Label` (1) dá a todos a mesma
chrome via `DebugTheme`, (2) deixa o tamanho do painel medível pelo dock, e
(3) reusa a UI de jogo (decisão do explore: "reaproveitar UI de jogo"). Os que
precisam de desenho custom (sparklines do momentum) continuam desenhando dentro
do retângulo do painel, mas o painel em si é um `Panel` temado.

### Decision: DebugTheme absorve DebugColors
`DebugColors.kt` já é o ponto único das cores de gizmo/log. O `DebugTheme`
estende isso para a chrome de painel (fundo, borda, margens, escala de texto),
ficando como a fonte única de aparência de todo o subsistema. Um único objeto,
sem `@Serializable` (estado de aparência, não de cena).

## Risks / Trade-offs

- **Churn nos 5 widgets:** a migração toca bastante código. Mitigação: o
  contrato novo do `ScreenDebugWidget` (tamanho + origin) é pequeno, e a chrome
  vem pronta do tema — cada widget perde código de posicionamento.
- **Altura variável no slot:** picker e log mudam de tamanho com o conteúdo;
  empilhar pode "pular". Aceitável para debug; o dock re-flui por frame.
- **Centros (TOP/BOTTOM_CENTER):** menos usados; incluídos para dar saída ao
  crescimento futuro sem voltar a colidir nos cantos.
- **Interação com `debug-ui-draggable`:** o dock define a posição *default*;
  a change seguinte adiciona override arrastável por cima do slot. O contrato
  origin-pelo-dock é o gancho onde o override vai entrar.
