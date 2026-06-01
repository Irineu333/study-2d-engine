## Why

Hoje arrastar um painel de debug é uma porta de mão única: pegar a header seta um
`customOrigin` que rompe permanentemente com o `DebugDock`, e o painel só volta ao
canto via reset global (BACKSPACE). Não há como reencaixar um painel num canto, nem
reordenar painéis empilhados num mesmo slot — a ordem é fixada pela ordem de registro.
Resultado: o usuário arrasta, "perde" o painel pro espaço livre e não tem controle
fino sobre o layout do overlay de debug.

## What Changes

- O modelo de posição deixa de ser binário (`customOrigin ?: dockOrigin`) e passa a
  ser **híbrido com estado explícito**: cada painel está `DOCKED(slot, order)` ou
  `FLOATING(pos)`. **BREAKING** no contrato interno do `DebugDock`/`ScreenDebugWidget`
  (não há API pública de jogo afetada — debug UI é interno à engine).
- O arrasto passa a **resolver um drop target a cada frame** em vez de só setar uma
  posição livre. A mesma gesture cobre três efeitos: arrastar entre slots **re-docka**;
  arrastar dentro do slot **reordena**; soltar no miolo da tela **flutua**.
- **Desencaixe por região**: as bordas superior e inferior viram faixas de dock
  (cada uma fatiada nos três terços = 6 slots existentes); o miolo central é zona livre.
  Flutuar passa a ser uma escolha espacial deliberada, não o default acidental de hoje.
- **Ordem do slot vira mutável**: o `DebugDock` mantém uma lista ordenada por slot,
  editável pelo drag, no lugar da ordem implícita de registro/DFS.
- **Indicador de inserção** desenhado durante o arrasto: mostra o slot alvo e o gap
  onde o painel cairá ao soltar.
- O **reset** ganha semântica nova: devolve cada painel ao seu `defaultSlot` + ordem
  default **e** des-flutua quem estava no miolo (hoje só limpa o `customOrigin`).
- **Fora de escopo**: persistência de layout entre execuções (o estado novo —
  `currentSlot`, `orderInSlot`, floating — permanece session-only, como o `customOrigin`
  já era); edge-docking lateral com faixas redimensionáveis; agrupamento em abas.

## Capabilities

### New Capabilities
- `debug-ui-dynamic-dock`: modelo de atribuição de slot mutável (`defaultSlot` +
  `currentSlot` + `orderInSlot`), reordenação de painéis dentro de um slot, resolução
  de drop target por região durante o arrasto e indicador de inserção visual.

### Modified Capabilities
- `debug-ui-draggable`: a terminação do arrasto muda — em vez de sempre setar uma
  posição custom que rompe com o dock, o arrasto resolve um drop target (re-dock /
  reordenar / flutuar por região); o "override de posição" passa a ser o estado
  `FLOATING` explícito (entrado só ao soltar no miolo); o reset estende sua semântica
  para também restaurar slot e ordem default.

## Impact

- **Código**: `engine/src/main/kotlin/com/neoutils/engine/debug/` — `DebugDock.kt`
  (lista ordenada por slot, resolução de drop target, reflow), `ScreenDebugWidget.kt`
  (estado dock/floating, `defaultSlot`/`currentSlot`/`orderInSlot`, lógica de drag
  reescrita, draw do indicador de inserção), `DockSlot.kt` (regiões de tela),
  `DebugRegistry.kt` (reset estendido), possivelmente `DebugTheme.kt` (cor do indicador).
- **APIs**: nenhuma API pública de jogo muda; tudo é interno ao subsistema de debug.
- **Invariantes**: respeita o invariante #4 — o `GameHost` segue sem tocar em debug;
  a interação vive nos `Node` internos da `DebugLayer` e no `DebugDock`.
- **Testes**: resolução de drop target (slot × terço × índice de inserção), reorder
  dentro do slot, transição docked↔floating por região, reset restaurando slot/ordem,
  re-clamp de floating no resize.
