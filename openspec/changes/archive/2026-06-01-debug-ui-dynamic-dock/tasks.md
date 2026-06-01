## 1. Estado de posição (DOCKED ↔ FLOATING)

- [x] 1.1 Introduzir em `ScreenDebugWidget` o `defaultSlot` (val) e o estado runtime de atribuição (`currentSlot` + `orderInSlot` quando dockado, `position` quando flutuante), substituindo o par `customOrigin ?: dockOrigin`
- [x] 1.2 Inicializar todo widget como `DOCKED(defaultSlot)`; garantir que `currentSlot` parte do `defaultSlot` e que mover de slot preserva o `defaultSlot`
- [x] 1.3 Atualizar o cálculo de `origin` para derivar do estado (posição livre se flutuante, senão o origin dado pelo dock)
- [x] 1.4 Atualizar disciplina `@Inspect`/`@Transient` dos novos campos conforme convenção (estado runtime nunca persiste)

## 2. Geometria de regiões e resolução de drop target

- [x] 2.1 Adicionar `dockBandThickness` (e cor `insertionIndicatorColor`) ao `DebugTheme`
- [x] 2.2 Em `DockSlot`/`DebugDock`, mapear `pointer -> (slot, índice de inserção) | floating`: faixas topo/base fatiadas em três terços; miolo = floating
- [x] 2.3 Derivar o índice de inserção comparando o Y do ponteiro com os painéis empilhados do slot alvo, excluindo o painel arrastado
- [x] 2.4 Testes de resolução de drop target: cada terço de cada faixa, miolo, e fronteiras (faixa↔miolo, terço↔terço)

## 3. Ordem mutável por slot no DebugDock

- [x] 3.1 `DebugDock` mantém, por slot, lista ordenada de painéis dockados por `orderInSlot`; empilhar segue essa lista (não a ordem de registro/DFS)
- [x] 3.2 Implementar inserção num índice com renumeração estável dos `orderInSlot` do slot
- [x] 3.3 Excluir o painel sob arrasto do empilhamento do seu slot enquanto o arrasto dura
- [x] 3.4 Testes: empilhamento segue `orderInSlot`; reorder dentro do slot desloca os demais de forma estável

## 4. Terminação do arrasto (re-dock / reordenar / flutuar)

- [x] 4.1 Reescrever `updateDrag()` para, ao soltar, consultar o drop target e aplicar: dockar em `(slot, índice)` ou flutuar na posição solta
- [x] 4.2 Preservar as regras de pega existentes (recorte de grip e controles, polling de `Input`, consumo de arrasto)
- [x] 4.3 Testes: soltar sobre faixa docka no slot/índice resolvido; soltar no miolo flutua; arrasto sobre faixa não vira flutuante por default

## 5. Indicador de inserção visual

- [x] 5.1 Desenhar o indicador de inserção no passo de overlay quando há drop target de dock, no gap correspondente ao slot/índice alvo, usando `insertionIndicatorColor`
- [x] 5.2 Não desenhar indicador quando o drop target é flutuar (miolo)
- [x] 5.3 Teste/checagem visual: indicador aparece no gap correto e some no miolo

## 6. Reset e memória de sessão

- [x] 6.1 Estender `resetAllPanelPositions()` (e variante por painel) para restaurar `currentSlot = defaultSlot`, ordem default do slot, limpar flutuante e expandir collapsed
- [x] 6.2 Garantir que estado dockado/flutuante e ordem sobrevivem a toggle on/off e são re-clampados no `tree.resize`, sem persistir entre execuções
- [x] 6.3 Confirmar que `isOverScreenPanel` (bloqueio de clique sob painel) usa a posição corrente (dockada ou flutuante), inclusive durante o arrasto
- [x] 6.4 Testes: reset volta ao slot/ordem default e des-flutua; posição sobrevive ao toggle; re-clamp no resize com mix de dockados e flutuante

## 7. Verificação final

- [x] 7.1 Exercitar manualmente as duas dores: re-dockar em outro canto e reordenar dentro do canto, num jogo Skiko com debug HUD ativo
- [x] 7.2 Rodar a suíte de testes do `:engine` e garantir verde
- [x] 7.3 `openspec validate debug-ui-dynamic-dock --strict`
