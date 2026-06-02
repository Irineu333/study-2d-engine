## 1. Primitiva de reordenação no Node

- [ ] 1.1 Adicionar `Node.raiseChildToTop(child: Node)` em `com.neoutils.engine.scene.Node`: move o filho direto para o fim de `_children`, no-op se não for filho direto, sem disparar `onEnter`/`onExit` nem alterar `child.parent`.
- [ ] 1.2 Integrar `raiseChildToTop` ao contrato de mutação segura: imediato quando `tree?.isMutationDeferred != true`; adiado para o drain (via `pendingRaise` dedicado ou remove+add net) quando em traversal; log+drop em `onDraw` coerente com `addChild`/`removeChild`.
- [ ] 1.3 Testes unitários em `:engine`: reordena para o fim preservando os demais; no-op para não-filho; chamada com `isMutationDeferred` simulado não corrompe a lista e aplica no drain; sem efeito sobre lifecycle.

## 2. Dono de press no hitTestUI

- [ ] 2.1 Adicionar estado de dono de press ao `DebugRegistry` (ex.: `var pressOwner: ScreenDebugWidget?`), limpo no início de cada tick junto com os flags de consumo.
- [ ] 2.2 Em `SceneTree.hitTestUI`: quando nenhum `Button` absorve, resolver o `ScreenDebugWidget` do topo sob o ponteiro (reverse-DFS top-most-first), gravar como `pressOwner`, chamar `raiseChildToTop` nele e setar `mouseClickConsumed = true`; manter "nenhum painel sob o ponteiro" sem consumir. Substituir a cláusula `isOverScreenPanel`-blind atual.
- [ ] 2.3 Em `ScreenDebugWidget.updateDrag`: armar o arrasto somente quando `this === tree.debug.pressOwner`, em vez de ler `wasMouseClickedRaw` cru; preservar as guardas existentes de `enabled`/`contentSize` e a precedência dos window controls (close/collapse).

## 3. Testes de comportamento

- [ ] 3.1 Teste: dois painéis flutuantes sobrepostos — press no overlap arma só o painel do topo; o de baixo não inicia arrasto.
- [ ] 3.2 Teste: press em qualquer ponto (header e corpo) de um painel coberto o traz à frente na ordem de filhos do `ScreenDebugCanvas`.
- [ ] 3.3 Teste: press num window control do painel do topo executa só a ação dele; o de baixo não reage.
- [ ] 3.4 Teste de persistência: z-order sobrevive a `tree.resize` e ao toggle enable/disable (ordem relativa na lista de filhos mantida).
- [ ] 3.5 Teste de regressão: `Button` dentro de painel sobreposto ainda tem precedência sobre a arbitragem de painel; layout dockado inalterado após reordenações.

## 4. Verificação e fechamento

- [ ] 4.1 Rodar a suíte de testes de `:engine` e o entrypoint de demos (Skiko) validando z-order e ausência de vazamento de press manualmente.
- [ ] 4.2 `openspec verify debug-ui-z-order` e revisão de coerência proposal/design/specs/implementação.
