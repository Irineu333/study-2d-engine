## Context

`Row.Kv` (em `InspectorRow.kt`) desenha `key value` com o valor numa coluna fixa
`KEY_COL = 64f` e o nome em `x + INDENT (8f)`. Quando o nome excede
`KEY_COL - INDENT ≈ 56px`, o texto do nome pinta por cima do valor. O sizing do
painel (`NodeInspectorWidget.computeLayout`, via `rows.maxOf { width(measurer) }`)
herda o mesmo defeito: `Kv.width()` é `KEY_COL + valueWidth`, ignorando a largura
do nome, então um nome longo pode também estourar a borda direita.

As demais rows (`Title`, `TreeNode`) já posicionam o segundo texto após medir o
primeiro (`x + typeW + GAP`); só a `Kv` usa coluna fixa. `Row`/`Kv` são
`internal`; o único outro consumidor do vocabulário (`SceneTreeWidget`) usa
apenas `Row.TreeNode`, então a mudança fica contida no `NodeInspectorWidget` + `Kv`.

## Goals / Non-Goals

**Goals:**

- Nome e valor nunca se sobrepõem no painel de detalhe.
- Valores alinhados numa coluna única do painel (estética de inspector real).
- Largura do painel reflete nome + valor de cada linha.
- Preservar o visual atual de nós com nomes curtos.

**Non-Goals:**

- Editar propriedades (segue read-only).
- Truncar/elidir nomes ou valores muito longos (fora de escopo).
- Mudar o `SceneTreeWidget` ou as demais rows (`Title`/`Section`/`TreeNode`).
- Quebrar texto em múltiplas linhas.

## Decisions

**Coluna de valores compartilhada pelo painel (opção C).** A coluna onde os
valores começam é calculada por painel a partir do nome mais largo entre as
`Row.Kv`:

```
valueCol = max(KEY_COL, INDENT + maxKeyWidth + GAP)
```

`computeLayout` faz um pré-passe: filtra as `Row.Kv`, mede cada `key` com o
`TextMeasurer`, calcula `valueCol` e atribui a todas elas. `Kv.draw` desenha o
valor em `x + valueCol`; `Kv.width()` retorna `valueCol + valueWidth`.

- *Alternativa A (clamp por linha):* coluna independente por linha
  (`max(KEY_COL, INDENT+keyW+GAP)` por `Kv`). Resolve a sobreposição, mas perde
  o alinhamento de tabela entre linhas — rejeitada por ser menos correta
  visualmente.
- *Alternativa B (flow inline):* valor logo após o nome, como `TreeNode`.
  Consistente com as outras rows, mas vira "escada" sem coluna alinhada —
  rejeitada pelo mesmo motivo.

**Piso em `KEY_COL`.** A coluna nunca fica menor que o `KEY_COL` atual (64f),
então painéis de nomes curtos (`pos`/`rot`/`scale`) ficam idênticos a hoje; a
coluna só expande quando algum nome é longo.

**`var valueCol` na `Kv` (mutável, resolvido no layout).** A `Kv` deixa de ser
self-sizing pura porque passa a depender dos irmãos. Em vez de poluir a
interface `Row` com um parâmetro de contexto em `width`/`draw` (só uma variante
precisa), a `Kv` carrega um `var valueCol` setado pelo `computeLayout`. As rows
são reconstruídas a cada frame em `buildRows`, então a mutabilidade é barata e
local. O KDoc do `Row`/`Kv` é atualizado para registrar que a `Kv` alinha numa
coluna compartilhada pelo painel.

## Risks / Trade-offs

- [Nome ou valor extremamente longos ainda podem exceder a área visível] →
  o painel base já tem viewport com scroll quando o conteúdo transborda; sem
  regressão (continua igual ao comportamento atual de overflow).
- [`Kv` mutável arranha o invariante "each variant owns its own layout" do
  KDoc do `Row`] → mitigado documentando explicitamente que a `Kv` é a exceção
  por alinhar em coluna compartilhada; mudança contida a uma única row interna.
- [Custo do pré-passe de medição] → desprezível: poucas linhas por painel,
  uma medição de texto por `Kv`, uma vez por frame (mesma ordem do `maxOf`
  já existente).
