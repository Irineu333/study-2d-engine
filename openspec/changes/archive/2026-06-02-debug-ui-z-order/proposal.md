## Why

Painéis de debug flutuantes (largados no miolo) hoje pintam em ordem fixa de
registro/DFS e não há arbitragem de clique entre eles: um press na interseção de
dois painéis sobrepostos pode armar arrasto em ambos, e o painel que se arrasta
pode deslizar por baixo de outro. Falta um z-order entre painéis sobrepostos e o
gesto óbvio de "interagir traz para frente".

## What Changes

- Painéis de debug screen-space passam a ter **z-order** entre si: quando dois ou
  mais se sobrepõem, um pinta inequivocamente por cima do outro, e o de cima é
  quem recebe o clique.
- **Bring-to-front em qualquer press**: pressionar qualquer ponto de um painel
  (header ou corpo) o traz para a frente dos demais painéis de debug.
- O z-order é materializado pela **ordem dos filhos** do `ScreenDebugCanvas`:
  trazer à frente é mover o painel para o fim da lista de filhos. Como o
  `DebugDock` posiciona painéis docked por `orderInSlot` (não por ordem de
  filhos) e painéis docked nunca se sobrepõem, o bring-to-front é aplicado
  **uniformemente** a todos os painéis sem caso especial e sem efeito sobre o
  layout dockado.
- O z-order **persiste** dentro da sessão através de `tree.resize` e do toggle
  enable/disable — consequência natural de viver na lista de filhos, que não é
  reconstruída nesses eventos.
- **BREAKING (interno)** A arbitragem de press dos painéis sobe para o pré-passe
  `SceneTree.hitTestUI`: ele resolve qual painel está no topo sob o ponteiro
  (top-most-first), registra esse painel como dono do press e o traz à frente; o
  arrasto por polling em `ScreenDebugWidget.updateDrag` passa a armar **somente**
  para o painel dono, em vez de ler o clique cru. Corrige o vazamento de press no
  overlap. Sem mudança na API pública dos jogos.
- Nova primitiva pública no `Node`: `raiseChildToTop(child)` (move-to-end),
  irmã de `addChild`/`removeChild` e governada pelo mesmo contrato de mutação
  segura durante traversal.

## Capabilities

### New Capabilities
- `debug-ui-z-order`: z-order entre painéis de debug sobrepostos — ordem de
  pintura determinística, bring-to-front em qualquer press, dono de press único
  no overlap, e persistência da ordem dentro da sessão.

### Modified Capabilities
- `engine-core`: adiciona `Node.raiseChildToTop(child)` à API de manipulação de
  filhos, sob o contrato de mutação segura durante traversal.
- `ui-foundation`: o pré-passe `hitTestUI` passa a resolver, registrar e trazer à
  frente o painel de debug dono do press (top-most-first), em vez de apenas
  consumir o clique de forma cega.

## Impact

- `:engine` — pacote `com.neoutils.engine.scene` (`Node`: nova primitiva de
  reordenação), `com.neoutils.engine.tree` (`SceneTree.hitTestUI`), e
  `com.neoutils.engine.debug` (`ScreenDebugWidget.updateDrag`, `DebugRegistry`
  para o estado de dono de press; `ScreenDebugCanvas`/`DebugDock` inalterados no
  layout dockado).
- Backends (`:engine-skiko`, `:engine-lwjgl`) e jogos: sem mudança de API.
- Testes: novos testes de regressão para reordenação de filhos, eleição de dono
  de press no overlap e persistência de z-order no resize.
