## Context

A change `debug-scene-picker` entregou uma ferramenta de pick por click no mundo
com um painel screen-space que mostra o nó selecionado: `type "name"`, breadcrumb
`root → selected`, transform world e props `@Inspect`, mais um gizmo world-space
(`SelectionGizmoWidget`). O padrão consolidado no shell de debug é **"uma
ferramenta, vários braços, um toggle"**: `scenePicker` é a HUD row e dona da
seleção; `selectionGizmo` é o braço world-space cujo `enabled` é derivado
(setter no-op); `colliders` + `colliderModePanel` repetem a mesma estrutura.

O breadcrumb já é, na prática, uma visualização de nós — só que achatada no
caminho até o selecionado. Esta change promove esse conceito a uma **view tree**
navegável e reorganiza a ferramenta num **Inspector**: a árvore vira o mestre
(navegação + dona da seleção + toggle), o painel atual de detalhe vira um braço
escravo, e o gizmo e o pick no mundo seguem inalterados.

Restrições herdadas: invariante #6 (debug widgets roteados por subtipo em
`WorldDebugContainer`/`ScreenDebugCanvas`, `tree.debug` per-tree); `:engine` puro
Kotlin; a árvore pode mutar todo tick (snake); seleção por identidade de
instância (sem IDs estáveis).

## Goals / Non-Goals

**Goals:**
- View tree navegável do scene graph, com a linha do selecionado destacada,
  reusando o vocabulário de linha do painel atual.
- Selecionar clicando numa linha da árvore, além do pick por click no mundo.
- Reorganizar em mestre (árvore) + escravo (detalhe) + gizmo + pick, sob uma
  única HUD row "Inspector".
- Dar ao `ScreenDebugWidget` a capacidade de suprimir os controles de janela
  (fechar/colapsar) para o painel escravo.

**Non-Goals:**
- Scroll real em painel de debug (nenhum widget tem hoje; fica para change
  futura).
- Collapse/expand por subtree dentro da árvore (a árvore é sempre-expandida no
  MVP; overflow reusa o "… (+N more)").
- Editar propriedades pela árvore ou pelo detalhe (o detalhe segue read-only).
- IDs estáveis de nó / persistência de seleção entre execuções.

## Decisions

### A árvore é o mestre; o detalhe e o gizmo são escravos

A `SceneTreeWidget` passa a ser a janela registrada na HUD (uma row "Inspector"),
dona do estado `selected` e do `select(node)`, e dona do `enabled` real. A
`NodeInspectorWidget` (ex-`ScenePickerWidget`) e o `SelectionGizmoWidget` derivam
seu `enabled` do mestre (getter no `inspector.enabled`, setter no-op), exatamente
como `SelectionGizmoWidget` já fazia sobre `scenePicker.enabled` e como
`ColliderModePanel` faz sobre `colliders.enabled`.

**Por que a árvore e não o detalhe?** O "off" da ferramenta precisa de um único
botão de fechar inequívoco. A árvore é a janela de entrada (navego antes de ter
seleção), então é natural que o `[x]` dela desligue tudo; o detalhe é
consequência da seleção, não o ponto de partida. Alternativa descartada: manter
o detalhe como mestre (status quo) e pendurar a árvore como escravo — inverte a
hierarquia conceitual (o detalhe só existe quando há seleção, a árvore existe
sempre que o Inspector está ligado).

### Duas janelas, não um painel único

A árvore (lista vertical longa, navegável) e o detalhe (bloco denso de chave/valor
do selecionado) têm formas e ritmos diferentes, e o usuário quer fechar/colapsar
só a árvore. Mantê-las como dois `ScreenDebugWidget` independentes no dock deixa
cada uma arrastável e empilhável por si, e a assimetria de chrome (mestre com
controles, escravo sem) cai naturalmente. Alternativa descartada: um painel só
com duas regiões — exigiria collapse interno por região (território novo) e
amarraria os dois tamanhos no mesmo dock slot.

### Seleção: duas fontes de escrita, um dono

`selected` continua com setter privado na janela dona (agora a árvore). Duas
entradas o escrevem:
- **Pick no mundo** → `SceneTree.hitTestPick` → `applyPick(point, frontToBack)`,
  preservando o cycling geométrico (`lastPickPoint`/`cycleIndex`) intacto.
- **Click na linha** → `select(node)`, que seta `selected` e **reseta** o estado
  de cycling (`lastPickPoint = null`, `cycleIndex = 0`) — uma seleção explícita
  não é um pick geométrico e não deve herdar o ciclo anterior.

Ambos os caminhos disparam o mesmo efeito: detalhe e gizmo (que leem `selected`
do mestre) se atualizam. A limpeza por `isLive` (selecionado saiu da árvore →
seleção zera) continua no `onProcess` do dono.

### Hit-test das linhas por polling, sem `Button` por nó

A árvore muta todo tick (snake), então criar/destruir um `Button` filho por nó
seria caro e frágil. Em vez disso a `SceneTreeWidget` computa as linhas visíveis
uma vez por frame (em `bodySize`, como o painel atual já faz) e, no `onProcess`,
mapeia `pointer.y → índice de linha` para resolver o nó clicado — mesmo estilo de
polling que o `updateDrag` da base já usa. Sem nós-filhos, sem participação no
`findHitButton` da `hitTestUI`. O click numa linha é consumido como UI
(`mouseClickConsumed = true`) para não vazar pro pick do mundo nem pro gameplay.

### Vocabulário de linha compartilhado

O `sealed interface Row` do painel atual ganha uma variante `Row.TreeNode(node,
depth, selected)` que desenha indentação por profundidade + `type`/`name` com o
mesmo esquema de cor (`TYPE_COLOR`/`NAME_COLOR`) já usado por `Row.Title`. O
`Row.Crumb` e o helper `breadcrumb()` são removidos (a árvore mostra a linhagem).
A árvore destaca a linha do `selected` (fundo/realce).

### `ScreenDebugWidget` ganha `closable`/`collapsible`

A base passa a expor `open val closable: Boolean = true` e `open val collapsible:
Boolean = true`. Quando `false`, `drawChrome` não desenha o glifo correspondente
e o `updateDrag` não hit-testa aquele controle (o press cai no caminho de
drag/header normal). A `NodeInspectorWidget` declara ambos `false`. Todos os
demais painéis mantêm o default `true`, sem mudança de comportamento. Isso
mantém a regra existente "controles só em painéis screen-space" e apenas a
refina ("...que não os tenham suprimido").

### Filtrar o subtree `__debug`

A árvore enumera a partir do `root` mas **pula** o nó `"__debug"` (a `DebugLayer`)
e seus descendentes — é encanamento da engine e poluiria a visão do conteúdo do
jogo. Análogo a como o world-pick pula `CanvasLayer`.

### Rename/supersessão no OpenSpec

`debug-inspector` entra como **New Capability** com o conjunto completo de
requirements (as ainda-válidas migradas de `debug-scene-picker`, restruturadas,
mais as novas de árvore e mestre/escravo). `debug-scene-picker` recebe um delta
`## REMOVED Requirements` esvaziando-a (migração apontando para
`debug-inspector`). No archive, o diretório `openspec/specs/debug-scene-picker/`
é removido.

## Risks / Trade-offs

- **[Árvore funda/longa estoura a tela]** → MVP reusa o overflow "… (+N more)"
  por altura que o painel já implementa; scroll e collapse-por-subtree ficam
  documentados como changes futuras (sem cap silencioso — o "+N more" diz quantas
  linhas ficaram de fora).
- **[Hit-test de linha por polling diverge do layout]** → linhas computadas uma
  vez por frame em `bodySize` e lidas tanto no draw quanto no hit-test (mesma
  fonte), como o painel atual já faz para `layout`.
- **[Rename quebra referências internas]** → `scenePicker`/`ScenePickerWidget`
  aparecem em testes e em `SceneTree.hitTestPick`; a change reescreve essas
  referências e os testes correspondentes. Nenhum jogo shipped referencia esses
  símbolos (verificar no apply).
- **[Dois painéis ocupam mais tela por default]** → ambos herdam o dock; o
  detalhe escravo só reporta tamanho quando há seleção (segue o
  `bodySize == ZERO` atual), então sem seleção ele não ocupa espaço.
- **[Coexistência com `ScreenDebugWidget` chrome]** → a supressão é aditiva
  (flags com default `true`); o risco é o `updateDrag` hit-testar um controle
  inexistente — mitigado guardando cada teste de rect atrás da flag.

## Open Questions

- Destaque da linha selecionada: fundo cheio vs. barra lateral vs. só cor do
  texto — decisão estética, resolvível no apply sem mexer em spec.
- Slot default do par (mestre + escravo): manter `BOTTOM_RIGHT` herdado ou mover
  a árvore para um slot mais alto/lateral — não afeta requirements (o dock é
  livre), decidir no apply.
