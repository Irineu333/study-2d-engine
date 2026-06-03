## Why

O picker hoje só sabe ir do mundo pra um nó: clico numa coisa e vejo o caminho
`root → selected` num breadcrumb achatado. Falta o inverso — **enxergar a árvore
inteira e navegar por ela**. O breadcrumb já é uma visualização de nós; promovê-lo
a uma _view tree_ navegável transforma a ferramenta de "tooltip do que cliquei"
num **inspector** de verdade (árvore + detalhe + pick no mundo), que é o que se
espera ao depurar scene graph em runtime — ainda mais com jogos que mutam a árvore
todo tick (snake).

## What Changes

- **Renomear a ferramenta `picker` → `Inspector`** (uma HUD row só, como hoje).
  A capability `debug-scene-picker` é **superada** por `debug-inspector`; o campo
  `DebugRegistry.scenePicker` vira `DebugRegistry.inspector`.
- **Nova janela `SceneTreeWidget` (screen) — o mestre.** Desenha a hierarquia
  navegável do scene graph (filtrando o subtree `__debug`), com a linha do
  selecionado destacada. É a janela com **chrome completo**: colapsar `[_]`
  (esconde a árvore) e fechar `[x]`. **Fechar a View Tree desliga o Inspector
  inteiro** (é o "off" da ferramenta). É a janela registrada na HUD e a **dona da
  seleção** (`selected` + `select(node)`).
- **`ScenePickerWidget` → `NodeInspectorWidget` (screen) — a escrava.** Continua
  mostrando o detalhe do selecionado (`type "name"` + transform world + props
  `@Inspect`), mas **perde o breadcrumb** (a árvore já mostra a linhagem) e
  **perde os controles de janela colapsar/fechar** — só grip + título. Seu
  `enabled` espelha o do mestre; vive e morre junto com o Inspector.
- **Selecionar tem duas fontes, um dono.** Click numa linha da árvore chama
  `select(node)` (reseta o cycling); click no mundo continua via `hitTestPick →
  applyPick`. A `NodeInspectorWidget` e o `SelectionGizmoWidget` apenas espelham
  o `selected` do mestre.
- **Vocabulário de linha compartilhado.** A `Row.TreeNode` da árvore e o
  `Row.Title` do detalhe desenham o nó com o mesmo esquema (`type` em cor de tipo,
  `name` em cor de nome) — "a mesma visualização de nós".
- **`ScreenDebugWidget` ganha supressão de window controls** (`closable` /
  `collapsible`, default `true`): a janela escrava declara ambos `false`. Os
  demais painéis ficam intocados.
- Escopo do MVP: árvore **sempre-expandida** com overflow reusando o
  "… (+N more)" que o painel já faz; **sem** scroll nem collapse-por-subtree
  (ficam pra changes futuras). O hit-test das linhas é por polling no
  `onProcess` (sem `Button` por nó — a árvore muta todo tick).
- **BREAKING (interno)** `DebugRegistry.scenePicker` deixa de existir (vira
  `inspector`); `ScenePickerWidget` é renomeada. Sem mudança na API pública dos
  jogos shipped (nenhum referencia esses símbolos).

## Capabilities

### New Capabilities

- `debug-inspector`: a ferramenta Inspector — View Tree mestre (navegação +
  dona da seleção + toggle da ferramenta), painel de detalhe escravo, gizmo de
  seleção world-space e pick por click no mundo. Supera `debug-scene-picker`.

### Modified Capabilities

- `debug-scene-picker`: **superada** por `debug-inspector` — todas as suas
  requirements são removidas (migram, restruturadas, pra `debug-inspector`).
- `debug-ui-window-controls`: um `ScreenDebugWidget` PODE declarar
  `closable = false` e/ou `collapsible = false` para suprimir os controles
  correspondentes do header; quando suprimidos, o header não desenha nem
  hit-testa aquele controle.

## Impact

- `:engine` — `com.neoutils.engine.debug`: nova `SceneTreeWidget`;
  `ScenePickerWidget` → `NodeInspectorWidget` (perde breadcrumb + window
  controls); `SelectionGizmoWidget` passa a ler `inspector.selected`;
  `DebugRegistry` (campo `scenePicker` → `inspector`, catálogo); `DebugLayer`
  (ordem/registro); `ScreenDebugWidget` (flags `closable`/`collapsible` + chrome
  condicional).
- `com.neoutils.engine.tree.SceneTree`: `hitTestPick` referencia
  `debug.inspector` em vez de `debug.scenePicker`; sem mudança de comportamento
  do pick.
- Documentação: `CLAUDE.md` invariante #6 cita `scenePicker`/`SelectionGizmoWidget`
  por nome — atualizar para o catálogo do Inspector. `README.md`/`ROADMAP.md` na
  seção de debug.
- Testes: `ScenePickerWidgetTest`, `ScenePickerRegistrationTest`,
  `SelectionGizmoWidgetTest`, `SceneTreeHitTestPickTest`, `DebugRegistryTest`,
  `DebugLayerTest` referenciam `scenePicker`/`ScenePickerWidget` — renomear e
  estender; novos testes para a árvore (build de linhas, filtro `__debug`,
  hit-test de linha → `select`, destaque do selecionado) e para a supressão de
  window controls.
