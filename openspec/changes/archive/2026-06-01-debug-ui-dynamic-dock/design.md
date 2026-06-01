## Context

O overlay de debug já tem um `DebugDock` (layout por 6 slots de canto/centro,
recalculado todo frame em `SceneTree.render`) e painéis arrastáveis. Hoje o estado
de posição é binário: `origin = customOrigin ?: dockOrigin`. Arrastar seta
`customOrigin` e o painel rompe com o dock para sempre, retornando só via reset global
(BACKSPACE). A ordem de empilhamento num slot é implícita (ordem de registro/DFS) e não
editável.

Peças relevantes (todas em `engine/src/main/kotlin/com/neoutils/engine/debug/`):
- `ScreenDebugWidget.kt` — chrome, header, grip, controles, `customOrigin`, `updateDrag()`.
- `DebugDock.kt` — coleta painéis por slot (apenas sem `customOrigin`), empilha do canto
  para dentro com `gutter`/`margin`, `relayout(surface)` e re-clamp de arrastados.
- `DockSlot.kt` — enum dos 6 slots, `slotX`/`slotY` de posicionamento.
- `DebugRegistry.kt` — registro de widgets, `resetAllPanelPositions()`, `isOverScreenPanel`.
- `DebugTheme.kt` — cores e métricas (`headerHeight`, `margin`, `gutter`, `padding`).

Restrições: nada disso vaza para `:engine`-consumidores (é interno ao subsistema de
debug); o `GameHost` não toca em debug (invariante #4); o arrasto continua via polling
de `Input`, sem modelo de eventos; nenhuma persistência entre execuções.

## Goals / Non-Goals

**Goals:**
- Tornar o arrasto reversível: re-dockar em qualquer slot e reordenar dentro do slot.
- Substituir o modelo binário por estado explícito `DOCKED(slot, order)` ↔ `FLOATING(pos)`.
- Desencaixe por região (faixas de borda dockam, miolo flutua), espacialmente intuitivo.
- Indicador de inserção visual durante o arrasto.
- Reset que restaura slot + ordem default e des-flutua.

**Non-Goals:**
- Persistência de layout entre execuções (segue session-only).
- Edge-docking lateral com faixas redimensionáveis que empurram o gameplay.
- Agrupamento de painéis em abas/tabs.
- Novos slots além dos 6 existentes (as laterais do meio vertical seguem caindo no miolo).
- Qualquer mudança em widgets world-space (`WorldDebugWidget`).

## Decisions

### 1. Estado de posição: enum selado em vez de `Vec2?` nulável
Modelar o estado como `DOCKED(currentSlot, orderInSlot)` ou `FLOATING(position)` —
internamente um par de campos ou um pequeno sealed type no `ScreenDebugWidget`. O
`defaultSlot` vira uma `val` da classe do widget (hoje o `dockSlot` já é fixo por
widget); `currentSlot` é a `var` runtime; `orderInSlot` é a `var` de ordenação.

- **Por quê**: o binário `customOrigin ?: dockOrigin` não expressa "dockado em outro
  slot que não o default" nem "ordem editada". Estado explícito remove a ambiguidade e
  torna o reset trivial (voltar para `defaultSlot`).
- **Alternativa descartada**: manter `customOrigin` e adicionar um `slotOverride`
  paralelo — gera dois eixos de verdade (posição livre + slot) que precisam ser
  reconciliados a cada frame; mais frágil.

### 2. `DebugDock` dono da ordem por slot (lista ordenada), não a ordem de registro
O dock mantém, por slot, a lista de painéis dockados ordenada por `orderInSlot`. O
empilhamento percorre essa lista. Inserir num índice reatribui os `orderInSlot` dos
painéis daquele slot de forma estável.

- **Por quê**: a dor (2) — reordenar manualmente — exige uma ordem mutável; a ordem de
  registro/DFS é imutável pelo usuário.
- **Alternativa descartada**: ordenar por `orderInSlot` "lazy" sem o dock manter lista —
  funciona para empilhar, mas a resolução do índice de inserção durante o drag precisa
  da lista atual do slot de qualquer modo, então centralizar no dock evita recomputação.

### 3. Resolução de drop target derivada da geometria das regiões
`DockSlot` (ou o dock) ganha um mapeamento `pointer -> (slot, índice) | floating`:
- Faixas de dock = `topBand` e `bottomBand`, espessura = constante de tema
  (`dockBandThickness`), cada faixa fatiada nos três terços horizontais.
- Miolo = tudo fora das faixas → floating.
- Índice de inserção: dentro do slot alvo, comparar o Y do ponteiro com os centros (ou
  topos) dos painéis já empilhados (excluindo o painel arrastado) para achar o gap.

- **Por quê**: "perto da borda docka, miolo flutua" é a decisão de UX já tomada; deriva
  diretamente das regiões de tela, sem modificador de teclado nem toggle de header.
- **Alternativa descartada**: zonas de drop ao redor de cada painel individual
  (hover-target por painel) — mais preciso mas muito mais complexo e ruidoso para 6 slots.

### 4. Painel arrastado é excluído do layout do seu slot durante o drag
Enquanto arrasta, o painel não entra no empilhamento (não reserva espaço para si),
espelhando o comportamento atual de painéis com `customOrigin`. O drop target é
calculado contra os painéis restantes.

- **Por quê**: evita o painel "empurrar a si mesmo" e dá um índice de inserção estável.

### 5. Indicador de inserção desenhado pelo dock/overlay, não pelo painel
Como o indicador depende do slot alvo e do gap entre painéis (informação do dock), ele é
desenhado num passo do overlay com a geometria do slot alvo, usando uma cor de tema
(`insertionIndicatorColor`). Sem drop target de dock (miolo), não desenha.

- **Por quê**: o painel individual não conhece os vizinhos do slot; o dock conhece.

### 6. Reset estende a semântica existente
`resetAllPanelPositions()` (e a variante por painel) passa a: setar `currentSlot =
defaultSlot`, restaurar a ordem default do slot, limpar floating e expandir collapsed.
O atalho (BACKSPACE) e o ponto de chamada (`DebugLayoutShortcutNode`) não mudam.

- **Por quê**: reaproveita o gesto já documentado; só amplia o que ele restaura.

## Risks / Trade-offs

- **Ambiguidade na fronteira terço-a-terço / faixa-miolo** → mitigação: derivar os
  limites de constantes de tema explícitas (`dockBandThickness`, e divisão em terços por
  largura), cobertas por testes de resolução de drop target nos limites.
- **Re-clamp de floating no resize precisa coexistir com reflow de slots** → mitigação:
  manter o caminho de `relayout(surface)` já existente, que re-clampa flutuantes e
  re-empilha dockados; adicionar testes do caso misto (uns dockados, um flutuante).
- **Estabilidade do reorder ao reatribuir `orderInSlot`** → mitigação: usar inserção em
  lista com renumeração estável (sem reordenar irmãos não afetados), testada.
- **Regressão no `isOverScreenPanel`** (cliques bloqueados sob painéis) durante o drag →
  mitigação: o hit-test continua sobre a posição corrente do painel (dockada ou
  flutuante); cobrir com teste de que arrastar não fura o bloqueio de clique.
- **Custo didático**: o estado vira um pouco mais rico (slot + ordem + floating). Aceito
  — é o mínimo para cobrir as duas dores; alternativas mais simples não as resolvem.

## Open Questions

- Forma exata do indicador de inserção (linha fina no gap vs placeholder "fantasma" do
  tamanho do painel). Default proposto: linha fina no gap; refinar na implementação.
- Espessura default de `dockBandThickness` em relação a `headerHeight`/`margin` —
  calibrar visualmente durante o apply.
