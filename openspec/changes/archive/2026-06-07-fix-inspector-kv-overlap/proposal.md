## Why

No painel de detalhe do Inspector (`NodeInspectorWidget`), as linhas de propriedade `key value` (`Row.Kv`) desenham o valor numa coluna fixa de 64px. Quando o nome da propriedade é mais largo que ~56px, o nome pinta por cima do valor — ilegível — e o cálculo de largura do painel (que ignora a largura do nome) pode até estourar a borda direita.

## What Changes

- A coluna onde os valores começam deixa de ser fixa e passa a ser **compartilhada pelo painel**: posicionada após o nome de propriedade mais largo entre as `Row.Kv` daquele painel (`INDENT + maxKeyWidth + GAP`), com piso na constante atual (`KEY_COL`) para preservar o visual de nós com nomes curtos.
- `Row.Kv.width()`/`draw()` passam a usar essa coluna resolvida em vez da constante `KEY_COL`, eliminando a sobreposição e fazendo a largura do painel refletir nome + valor.
- Teste de regressão cobrindo nome de propriedade longo (valor começa após o nome, sem sobreposição) e o alinhamento compartilhado entre `Kv`.

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `debug-inspector`: a requirement do **Node Detail Panel** ganha a garantia de que nome e valor de cada propriedade nunca se sobrepõem — os valores alinham numa coluna compartilhada posicionada após o nome mais largo do painel.

## Impact

- `engine/src/main/kotlin/com/neoutils/engine/debug/InspectorRow.kt` — `Row.Kv` (campo de coluna resolvida; `width`/`draw` usando-o).
- `engine/src/main/kotlin/com/neoutils/engine/debug/NodeInspectorWidget.kt` — `computeLayout` faz o pré-passe que mede os keys e atribui a coluna compartilhada.
- Sem mudança de API pública (`Row`/`Kv` são `internal`); sem impacto no `SceneTreeWidget` (só usa `Row.TreeNode`); nenhum invariante arquitetural tocado.
