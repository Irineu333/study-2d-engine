## 1. Coluna compartilhada na Row.Kv

- [ ] 1.1 Em `InspectorRow.kt`, adicionar à `Row.Kv` um `var valueCol` (default `KEY_COL`) e fazer `draw()` desenhar o valor em `x + valueCol` e `width()` retornar `valueCol + measureText(value)`.
- [ ] 1.2 Atualizar o KDoc da `Kv` (e a nota do `Row`) registrando que a `Kv` alinha o valor numa coluna compartilhada pelo painel, resolvida no layout.

## 2. Pré-passe de layout no NodeInspectorWidget

- [ ] 2.1 Em `NodeInspectorWidget.computeLayout`, antes do cálculo de largura, filtrar as `Row.Kv`, medir cada `key`, calcular `valueCol = max(KEY_COL, INDENT + maxKeyWidth + GAP)` e atribuí-lo a todas as `Kv`.
- [ ] 2.2 Confirmar que `panelWidth` (via `rows.maxOf { width(measurer) }`) passa a refletir nome + valor sem estourar a borda.

## 3. Testes de regressão

- [ ] 3.1 Teste: propriedade com nome longo → valor desenhado após o nome (sem sobreposição) e largura do painel cresce para acomodar nome + valor.
- [ ] 3.2 Teste: várias `Kv` no mesmo painel → todas começam o valor na mesma coluna (alinhamento compartilhado), com piso em `KEY_COL` preservado para nomes curtos.

## 4. Verificação

- [ ] 4.1 Rodar o build/testes do `:engine` e validar visualmente no `:games:platformer` (selecionar um nó com propriedade de nome longo no Inspector).
