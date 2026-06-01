# Tasks — Debug UI Draggable

## 1. Consumo de drag no Input
- [x] 1.1 Adicionar ao `Input` um flag de consumo de arrasto (espelhando
  `mouseClickConsumed`): default não-consumido, resetado por tick no mesmo
  ponto do pipeline
- [x] 1.2 KDoc do flag; documentar o contrato "checar antes de pan/arraste de
  gameplay"
- [x] 1.3 Varrer os jogos shipped (ex.: pool8 "puxar e soltar") e adotar o
  flag onde houver arraste de mundo
  > Nenhum jogo shipped faz arraste de mundo: `SpawnerDemo` usa
  > `wasMouseClicked` (clique, não drag) e `board.lua` só clica. Não há
  > consumidor de arraste a adaptar — o flag fica disponível com default
  > não-consumido para consumidores futuros (pan de câmera, drag-and-drop).

## 2. Arrasto de painel via polling
- [x] 2.1 Lógica de arrasto no painel base de debug: pega no topo/título,
  `grabOffset` ao iniciar, `origin = pointer - grabOffset` enquanto
  `isMouseDown`, encerrar ao soltar
- [x] 2.2 Hit-test da zona de pega via `screenRect()` do painel
- [x] 2.3 Setar o flag de drag-consumido enquanto arrastando
- [x] 2.4 Distinguir pega-de-arrasto de clique-de-conteúdo (botões do Time HUD
  continuam clicáveis)
  > Cada painel ganhou uma barra de título (`DebugTheme.headerHeight`) desenhada
  > pela chrome central na base; a zona de pega é o header. O corpo (incl. os
  > botões, abaixo do header) roteia clique normalmente.

## 3. Override de posição sobre o slot
- [x] 3.1 Painel guarda override de posição custom (default: ausente → segue slot)
- [x] 3.2 `DebugDock` respeita o override quando presente; empilha no slot só
  os painéis sem override
- [x] 3.3 Gesto de reset: limpa o override de um painel (e variante "resetar
  todos") → volta a fluir no slot
  > `ScreenDebugWidget.resetPosition()` (um painel) +
  > `DebugRegistry.resetAllPanelPositions()` (todos), acionado pelo
  > `DebugLayoutShortcutNode` (tecla `Backspace`, gated em `hud.enabled`).

## 4. Memória de sessão + re-clamp
- [x] 4.1 Override sobrevive ao toggle on/off do widget
- [x] 4.2 Override sobrevive ao `tree.resize`, re-clampado para dentro do viewport
- [x] 4.3 Confirmar que não há persistência em disco (escopo Fase 3)
  > `customOrigin` é estado runtime no widget (sem `@Inspect`/serialização);
  > nunca é escrito em disco.

## 5. Testes + validação
- [x] 5.1 Teste: arrastar um painel atualiza sua posição e seta o flag de drag
- [x] 5.2 Teste: arrasto consumido não vaza para um consumidor de arraste de gameplay
- [x] 5.3 Teste: posição custom sobrevive ao toggle e é re-clampada no resize
- [x] 5.4 Teste: reset limpa o override e o painel volta ao slot do dock
- [x] 5.5 Teste: botão do Time HUD continua clicável (pega só no topo)
- [x] 5.6 Validação visual manual: arrastar, soltar, redimensionar janela, resetar
  > Validado à mão: arrasto pela barra de título, soltar, redimensionar e
  > reset funcionando; painel do picker arrastável sem sumir.
