## Why

A `debug-ui-shell` resolve as duas dores nomeadas (sobreposição e
inconsistência) dando a cada painel de debug uma chrome única e um slot de
canto onde o `DebugDock` o empilha. Mas o layout ainda é **imposto**: o slot
default decide tudo, e o usuário não pode reorganizar a tela para a sessão de
debug que tem em mãos — afastar o painel do picker que cobre justo o objeto
sob inspeção, ou juntar fps+profiler num canto enquanto observa physics.

Esta change adiciona a camada de **controle direto** por cima do dock: arrastar
um painel de debug para onde quiser, e lembrar onde ele ficou. É a Fase 2 do
roteiro fechado no explore (shell → draggable → persistência), separada porque
tem pré-requisitos próprios — notavelmente o consumo de *drag* no `Input`, que
não existe (só há consumo de clique).

## What Changes

- **Consumo de drag no `Input`** — análogo ao `mouseClickConsumed` existente:
  um `mouseDragConsumed` (ou equivalente) setado quando um painel de debug
  captura o arrasto, para que arrastar o painel **não** arraste a câmera/o
  gameplay junto. Resetado a cada tick como o de clique.
- **Arrasto de painel via polling** — sobre o `isMouseDown` +
  `pointerPosition` já existentes (sem depender de `ui-input-events`): pegar a
  barra/topo do painel inicia o arrasto, mover atualiza o offset, soltar
  encerra. Hit-test reusa o `screenRect()` do painel.
- **Override de posição sobre o slot** — o painel arrastado guarda um
  `offset`/posição custom que **sobrepõe** o origin que o `DebugDock` daria;
  enquanto não arrastado, segue o slot. O dock passa a respeitar o override
  quando presente (gancho deixado pela `debug-ui-shell`).
- **Memória de posição na sessão** — a posição custom sobrevive ao toggle
  on/off do widget e ao `tree.resize` (re-clampada para dentro do viewport),
  dentro da mesma execução. Persistência **entre execuções** (arquivo de
  layout) fica explicitamente fora — é uma Fase 3 separada.
- **Reset ao slot** — um gesto para devolver um painel (ou todos) ao layout
  default do dock, para sair de uma bagunça sem reiniciar.

## Capabilities

### New Capabilities
- `debug-ui-draggable`: arrasto de painéis de debug por polling de mouse, o
  override de posição sobre o `DockSlot`, a memória de posição na sessão (com
  re-clamp no resize) e o reset ao slot default.

### Modified Capabilities
- `engine-core`: nova superfície de consumo de drag no `Input`
  (`mouseDragConsumed` ou equivalente), espelhando o `mouseClickConsumed`
  já existente e resetada por tick no mesmo ponto do pipeline.

## Impact

- **Affected specs:** `debug-ui-draggable` (nova), `engine-core` (modificada),
  `debug-ui-shell` (o `DebugDock` passa a respeitar override de posição — a
  ser refletido como ajuste fino, não reescrita).
- **Affected code:** `:engine` `com.neoutils.engine.input` (`Input` ganha o
  flag de drag) e `com.neoutils.engine.debug` (lógica de arrasto no painel
  base de debug; o `DebugDock` consulta o override).
- **Dependência:** **requer `debug-ui-shell`** (precisa do `DebugDock`, da
  chrome de painel única e do contrato origin-pelo-dock onde o override entra).
- **Invariantes:** mantém #4 (o polling de arrasto vive em nós internos da
  `DebugLayer`, não no `GameHost`) e #6 (painéis seguem screen-space no
  `ScreenDebugCanvas`).
- **Não-objetivos:** persistir layout entre execuções (Fase 3, arquivo);
  redimensionar/recolher painéis; docking dinâmico (soltar para re-slotar);
  z-order/trazer-para-frente entre painéis de debug.
