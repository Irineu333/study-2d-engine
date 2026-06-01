## Why

Os painéis de debug (`ScreenDebugWidget`) já podem ser arrastados pelo header, mas só dá para escondê-los indo até a `DebugHud` e desmarcando a linha — e não há como reduzir um painel grande (Momentum, Profiler, Log) ocupando tela sem perdê-lo de vista. Faltam os dois gestos de janela mais básicos diretamente no painel: **fechar** e **colapsar**.

## What Changes

- O header de todo `ScreenDebugWidget` ganha dois controles desenhados no canto direito: **`[x]` fechar** e **`[_]` colapsar**.
  - `[x]` fechar = `enabled = false` (soft close). O painel reabre pela `DebugHud`; a própria HUD reabre pelo `debugHudKey` (F10). Não há dismiss permanente nem unregister.
  - `[_]` colapsar = alterna um novo estado `collapsed`. O header permanece visível, o corpo some e o `DebugDock` re-flui os demais painéis.
- O **grip de arraste** (grade 2×3 de pontos) migra da direita para a **esquerda do título**, liberando o canto direito para os controles. O título desloca à direita para não colidir com o grip.
- Novo estado de sessão `collapsed` no `ScreenDebugWidget`, espelhando a semântica de `customOrigin`: sobrevive ao toggle de `enabled` e ao `tree.resize`, nunca persiste em disco.
- Widgets que montam nós-filhos (`DebugHud`, `TimeControlWidget`) passam a observar a visibilidade efetiva do corpo (`enabled && !collapsed`) em vez de só `enabled`, de modo que colapsar desmonte os `Button`s filhos — zero draw e zero hit-test — reusando o teardown que já existe.
- A **zona de arraste** do header passa a recortar os três retângulos interativos (grip à esquerda, `[_]` e `[x]` à direita): pressionar qualquer um deles não inicia arraste nem vaza para o scene-picker.
- O **reset de layout** (atalho BACKSPACE) passa a, além de limpar `customOrigin`, **expandir** todos os painéis colapsados — "restaurar layout padrão" devolve posição e expande tudo.
- **Escopo**: apenas `ScreenDebugWidget` (painéis com chrome/header). Gizmos `WorldDebugWidget` não têm header e seguem controlados só pelo toggle da `DebugHud`.

## Capabilities

### New Capabilities
- `debug-ui-window-controls`: controles de janela no header de painéis de debug — botão fechar (`enabled = false`), botão colapsar e o estado de sessão `collapsed` que esconde o corpo (incluindo o teardown dos widgets de nós-filhos) mantendo o header e re-fluindo o dock.

### Modified Capabilities
- `debug-ui-draggable`: o grip de arraste muda da direita para a esquerda do título; a zona de arraste do header (`inHeader`) passa a recortar os retângulos do grip e dos dois controles; o reset ao slot default passa a também expandir os painéis colapsados.

## Impact

- `engine/src/main/kotlin/com/neoutils/engine/debug/ScreenDebugWidget.kt`: estado `collapsed` + `bodyVisible`, `contentSize()` colapsado, `onDraw` (chrome sempre que `enabled`, `drawDebug` só se `bodyVisible`), `drawChrome`/`drawDragGrip` (grip à esquerda, glifos `[_]`/`[x]`), `inHeader` (recorte dos três rects), `updateDrag` (controles disparam ação e consomem o clique).
- `engine/src/main/kotlin/com/neoutils/engine/debug/DebugHud.kt` e `TimeControlWidget.kt`: gatilho de build/teardown passa de `enabled` para `bodyVisible`.
- `DebugLayoutShortcutNode` / `resetAllPanelPositions`: reset também expande (`collapsed = false`).
- Possíveis novas constantes de ícone em `DebugTheme.kt` (glifos via `drawRect`/`drawLine`, sem fonte nova).
- Testes de regressão em `engine/src/test/kotlin/com/neoutils/engine/debug/` cobrindo fechar, colapsar (incl. hit-test do corpo desmontado), persistência de `collapsed` e o recorte da zona de arraste.
- Sem impacto fora de `:engine`; nenhuma API pública de jogo muda. Estende o header estabelecido pela change arquivada `2026-06-01-debug-ui-draggable`.
