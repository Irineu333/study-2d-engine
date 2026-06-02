## Context

Os painéis de debug screen-space (`ScreenDebugWidget`) vivem como filhos diretos
do `ScreenDebugCanvas`, um `CanvasLayer` fixado em `layer = Int.MAX_VALUE - 1`.
Dentro desse único canvas, a ordem de pintura é a ordem da lista de filhos (DFS,
último por cima) — estática, semeada pela ordem de registro. Painéis docked são
posicionados pelo `DebugDock` por `orderInSlot` e nunca se sobrepõem; apenas
painéis flutuantes (largados no miolo) podem se sobrepor.

Dois problemas decorrem disso:

1. **Sem z-order interativo**: o painel com que você interage não vem à frente;
   pode-se arrastá-lo por baixo de outro flutuante.
2. **Press vaza no overlap**: cada `ScreenDebugWidget.updateDrag` pollia o input
   sozinho e, na borda de press, lê `wasMouseClickedRaw` cru sem consultar
   consumo. Dois painéis sobrepostos podem ambos armar arrasto no mesmo press.

Restrições relevantes:
- Invariante #6: a arbitragem de UI é centralizada no pré-passe
  `SceneTree.hitTestUI`, que já caminha top-most-first (`layer desc, dfs-order
  desc`) e consome cliques de `Button`. Ele roda **fora** de `runTraversal`
  (logo `isMutationDeferred == false`), no início do tick.
- Mutação da lista de filhos durante traversal é proibida/adiada (requisito
  "Safe mutation during scene traversal"): `addChild`/`removeChild` enfileiram
  quando `isMutationDeferred`, aplicam imediato fora dele, e são dropados em
  `onDraw`.
- `DebugDock.add` semeia `defaultOrder` **uma única vez no registro**; o layout
  dockado lê `orderInSlot`, nunca a ordem de filhos. Reordenar filhos depois do
  registro não afeta o dock.

## Goals / Non-Goals

**Goals:**
- Z-order determinístico entre painéis de debug sobrepostos, materializado pela
  ordem dos filhos do `ScreenDebugCanvas`.
- Bring-to-front em qualquer press (header ou corpo), uniforme para todos os
  painéis, resolvido no `hitTestUI` antes de `process` e `render`.
- Dono de press único no overlap: só o painel do topo arma o arrasto.
- Persistência do z-order na sessão (resize, enable/disable) sem estado extra.

**Non-Goals:**
- Z-order entre `CanvasLayer`s distintos (já resolvido por `layer: Int`).
- Empilhamento de painéis docked (não se sobrepõem por construção).
- Reordenação por API de jogo ou drag-handle de "send to back"; só bring-to-front
  por press.
- Persistência em disco do z-order (estado de sessão apenas).
- Mudança no modelo de drag/drop, dock, magnetismo ou window controls existentes.

## Decisions

### 1. Z-order = ordem dos filhos do `ScreenDebugCanvas`; bring-to-front = move-to-end

**Decisão**: não introduzir um campo `zOrder: Int`. O z-order é a posição na lista
de filhos do canvas; trazer à frente é mover o painel para o fim.

**Por quê**: a pintura do UI pass já é DFS-último-por-cima, então a lista de
filhos *já é* o z-order — um campo paralelo seria estado redundante a manter
sincronizado com a pintura. A persistência através de resize/toggle sai de graça
porque a lista de filhos não é reconstruída nesses eventos.

**Alternativas consideradas**:
- `zOrder: Int` no widget, dono = `DebugDock`, render ordenando por ele. Espelha
  `orderInSlot`, mas exige o render ordenar filhos por z (toca o traversal do
  canvas) e duplica a verdade da pintura. Rejeitado por custo/redundância.
- `DebugDock` mantém lista floating própria + sub-passada ordenada. Concentra no
  dock, mas adiciona uma sub-passada extra no UI pass. Rejeitado.

### 2. Nova primitiva `Node.raiseChildToTop(child)`

**Decisão**: adicionar `raiseChildToTop` ao `Node`, irmã de `addChild`/`removeChild`,
sob o mesmo contrato de mutação segura: imediato fora de traversal, adiado para o
drain quando `isMutationDeferred`, no-op se `child` não for filho direto, sem
disparar `onEnter`/`onExit`.

**Por quê**: reordenar filhos é uma mutação da árvore viva; deixá-la fora do
contrato existente criaria uma exceção silenciosa às regras de traversal. Como o
bring-to-front roda no `hitTestUI` (fora de traversal), o caminho quente é o
imediato; o caminho adiado existe por coerência e segurança caso alguém reordene
de dentro de um hook.

**Mecânica do defer**: reusar a infra de pending. A reordenação pode ser modelada
como um `pendingRaise: MutableList<Node>` drenado junto com add/remove em
`drainPending`, aplicado após removes e adds para manter a ordem coerente, ou — se
mais simples — como remove+add do mesmo nó (que a regra "removes antes de adds"
já resolve para net-add no fim da lista). A primeira é preferível por não disparar
lifecycle; decidir na implementação.

### 3. Arbitragem de press sobe para o `hitTestUI` (dono de press)

**Decisão**: estender o `hitTestUI` para, quando nenhum `Button` absorve, resolver
o painel de debug do topo sob o ponteiro (mesma ordem reverse-DFS top-most-first),
gravar esse painel como dono do press num campo do `DebugRegistry`
(ex.: `debug.pressOwner`), chamar `raiseChildToTop` nele e consumir o clique.
`ScreenDebugWidget.updateDrag` deixa de armar a partir do clique cru e passa a
armar **somente se `this === debug.pressOwner`**. O `pressOwner` é limpo no início
de cada tick (como os flags de consumo).

**Por quê**: alinha com o invariante #6 (arbitragem central no `hitTestUI`) e
resolve de uma vez paint + hit-test, que são governados pela mesma ordenação.
Fazer os painéis checarem `mouseDragConsumed` em `process` não funcionaria: a
ordem de `process` é DFS-forward (o painel do topo processa por **último**), então
o de baixo consumiria primeiro — dono errado. Resolver no pré-passe top-most-first
elege o dono correto antes de qualquer `process`.

**Ponto de cuidado**: hoje o `hitTestUI` faz `if (debug.isOverScreenPanel(pointer))
input.mouseClickConsumed = true`. Essa cláusula é substituída pela resolução do
dono: achar o painel do topo (não só "algum painel"), gravá-lo, levantá-lo,
consumir. O caminho "nenhum painel sob o ponteiro" continua não consumindo.

### 4. Bring-to-front uniforme (sem guarda floating/docked)

**Decisão**: `raiseChildToTop` é chamado para qualquer painel pressionado, docked
ou flutuante.

**Por quê**: é o caminho mais simples e sem efeito colateral. O `DebugDock` lê
`orderInSlot`, não a ordem de filhos; painéis docked não se sobrepõem. Logo
reordenar um docked é visualmente inócuo e não altera seu layout. Uma guarda
"floating only" seria complexidade sem ganho.

## Risks / Trade-offs

- **[Reorder durante drag muda a base do `dragOrigin`?]** O bring-to-front ocorre
  na borda de press (uma vez), não a cada frame; o `dragOrigin`/`grabOffset` são
  capturados em seguida no `updateDrag` do mesmo tick. → Garantir que `hitTestUI`
  roda antes de `process` (já é o caso) e que o reorder imediato não invalida o
  `grabOffset` (ele é relativo ao `origin`, independente da posição na lista).
- **[`pressOwner` apontando para painel removido/desabilitado]** Um painel pode
  fechar (`enabled = false`) no mesmo tick. → `updateDrag` já checa `enabled` e
  `contentSize()` antes de armar; a checagem `this === pressOwner` é adicional, não
  substitui as guardas existentes. Limpar `pressOwner` no início do tick evita
  carry-over.
- **[Defer de reorder raramente exercitado]** O caminho adiado de `raiseChildToTop`
  quase nunca roda (o uso real é fora de traversal). → Cobrir com teste unitário
  direto (chamar de dentro de um hook simulado com `isMutationDeferred`), não
  depender do fluxo de debug para exercê-lo.
- **[Regressão na arbitragem de `Button` sobre painel]** A nova cláusula de painel
  roda só quando nenhum `Button` absorve, preservando a precedência atual de
  `Button`. → Teste cobrindo press sobre `Button` dentro de painel sobreposto.

## Open Questions

Nenhuma pendente — os dois pontos levantados no explore (tratamento de docked e
persistência no resize) foram resolvidos: bring-to-front uniforme e persistência
natural via lista de filhos. A escolha entre `pendingRaise` dedicado e
remove+add para o caminho adiado de `raiseChildToTop` fica para a implementação,
sem impacto no contrato observável.
