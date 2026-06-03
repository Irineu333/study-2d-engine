## 1. Base: supressão de window controls no ScreenDebugWidget

- [x] 1.1 Adicionar `open val closable: Boolean = true` e `open val collapsible: Boolean = true` em `ScreenDebugWidget`.
- [x] 1.2 Em `drawChrome`, condicionar o desenho do `[x]` a `closable` e do `[_]` a `collapsible` (não desenhar quando suprimido).
- [x] 1.3 Em `updateDrag`, guardar o hit-test de `closeRect()` atrás de `closable` e de `collapseRect()` atrás de `collapsible`, de modo que o press caia no caminho de drag/header normal quando o controle não existe.
- [x] 1.4 Garantir que `inHeader` continua coerente quando um ou ambos controles estão suprimidos (a faixa do header arrasta normalmente sobre a área antes ocupada pelo controle).
- [x] 1.5 Testes: painel com `closable = false` não desabilita ao clicar onde estaria o `[x]`; painel com `collapsible = false` não colapsa ao clicar onde estaria o `[_]`; defaults `true` preservam o comportamento atual.

## 2. Renomear picker → inspector no registry

- [x] 2.1 Renomear `DebugRegistry.scenePicker: ScenePickerWidget` para `inspector` apontando para a nova `SceneTreeWidget` (mestre); ajustar KDoc.
- [x] 2.2 Atualizar `SceneTree.hitTestPick` para ler `debug.inspector` em vez de `debug.scenePicker` (sem mudança de comportamento do pick).
- [x] 2.3 Atualizar referências em testes/jogos a `scenePicker`/`ScenePickerWidget` (verificar que nenhum jogo shipped referencia esses símbolos).

## 3. NodeInspectorWidget (renomeia ScenePickerWidget, vira escravo)

- [x] 3.1 Renomear `ScenePickerWidget` → `NodeInspectorWidget`; `defaultSlot` herdado.
- [x] 3.2 Tornar `enabled` derivado do mestre (`get() = tree?.debug?.inspector?.enabled ?: false`, `set(_) {}`), como `SelectionGizmoWidget`/`ColliderModePanel`.
- [x] 3.3 Declarar `closable = false` e `collapsible = false`.
- [x] 3.4 Remover a posse da seleção: `selected`, `applyPick`, `select`, o cleanup por `isLive` e o estado de cycling migram para o mestre (`SceneTreeWidget`); o detalhe passa a ler `tree.debug.inspector.selected`.
- [x] 3.5 Remover o breadcrumb: dropar `Row.Crumb` e o helper `breadcrumb()`; `bodySize` retorna `ZERO` quando não há seleção (mantém o comportamento de não ocupar espaço).
- [x] 3.6 Manter `Row.Title` + seção Transform + seção Properties (via `inspectProperties`), read-only.

## 4. SceneTreeWidget (mestre, view tree, dono da seleção)

- [x] 4.1 Criar `SceneTreeWidget : ScreenDebugWidget` (`title = "Inspector"`), chrome completo (`closable`/`collapsible` default `true`).
- [x] 4.2 Mover para cá a posse da seleção: `var selected: Node?` (setter privado), `applyPick(point, frontToBack)` (cycling geométrico preservado) e o cleanup por `isLive` no `onProcess`.
- [x] 4.3 Adicionar `fun select(node: Node)` interno: seta `selected` e reseta `lastPickPoint`/`cycleIndex` (seleção explícita ≠ pick geométrico).
- [x] 4.4 Enumerar a hierarquia em DFS a partir de `root` em `bodySize`, indentando por profundidade, **pulando** o nó `"__debug"` e descendentes; produzir uma lista de `Row.TreeNode(node, depth, selected)`.
- [x] 4.5 Adicionar `Row.TreeNode` ao `sealed interface Row`, reusando `TYPE_COLOR`/`NAME_COLOR`; destacar a linha do `selected`.
- [x] 4.6 Aplicar overflow vertical reusando o padrão "… (+N more)" já existente quando a árvore não cabe na altura disponível.
- [x] 4.7 Hit-test das linhas no `onProcess`: mapear `pointer.y → índice de linha` (estilo polling do `updateDrag`), chamar `select(node)` da linha atingida e consumir o clique (`mouseClickConsumed = true`); não criar `Button` por nó.

## 5. Wiring no registry/layer e gizmo

- [x] 5.1 Em `DebugRegistry`: campo `inspector: SceneTreeWidget` (mestre), `nodeInspector: NodeInspectorWidget` e `selectionGizmo: SelectionGizmoWidget` (escravos).
- [x] 5.2 Em `bindLayer`/auto-insert: registrar `inspector` (entra em `widgets` + HUD); adicionar `nodeInspector` ao screen canvas e `selectionGizmo` ao world container **fora** de `widgets`/HUD (como os demais braços escravos).
- [x] 5.3 `SelectionGizmoWidget`: ler `tree.debug.inspector.selected` e derivar `enabled` de `inspector.enabled`.

## 6. Specs, docs e testes finais

- [ ] 6.1 Remover `openspec/specs/debug-scene-picker/` no archive (a capability foi superada por `debug-inspector`).
- [x] 6.2 Atualizar `CLAUDE.md` invariante #6 (citações a `scenePicker`/`SelectionGizmoWidget` → catálogo do Inspector) e a seção de debug em `README.md`/`ROADMAP.md`.
- [x] 6.3 Renomear/estender testes: `ScenePickerWidgetTest`→`NodeInspectorWidgetTest`, `ScenePickerRegistrationTest`→registro do Inspector, `SelectionGizmoWidgetTest`, `SceneTreeHitTestPickTest`, `DebugRegistryTest`, `DebugLayerTest`.
- [x] 6.4 Novos testes da árvore: build de linhas em DFS com indentação, filtro de `__debug`, hit-test de linha → `select`, destaque do `selected`, overflow "+N more", e `select` resetando o cycling.
- [x] 6.5 Rodar a suíte de `:engine` e `openspec validate debug-inspector --strict`.

## 7. Correções pós-apply

- [x] 7.1 Bug de magnetismo do dock: o `nodeInspector` escravo fica `enabled` (deriva do mestre) mas com `contentSize` zero enquanto não há seleção; entrava no `DebugDock.stacked()` e seu `dockOrigin` stale (≈0) corrompia `stackTop`/`stackBottom`, deixando todo o terço inferior direito magnético em qualquer altura. Fix: `stacked()` (e o filtro de reordenação em `dockWidget`) passam a excluir painéis que não ocupam espaço via o helper `occupiesSpace()`. Teste de regressão em `DebugDynamicDockTest` (painel `enabled` de tamanho zero não atrai um drop no miolo).
