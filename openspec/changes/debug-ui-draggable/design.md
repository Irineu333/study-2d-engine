## Context

A `debug-ui-shell` (pré-requisito) entrega: `DebugTheme` (chrome única),
`DebugDock`/`DockSlot` (layout por canto), e o contrato em que o
`ScreenDebugWidget` reporta seu tamanho e desenha a partir de um origin dado
pelo dock. O `Input` hoje expõe `pointerPosition`, `isMouseDown`,
`wasMouseClickedRaw`/`wasMouseClicked` e o `mouseClickConsumed` (resetado no
início do `SceneTree.hitTestUI`, setado quando um `Button` absorve o clique).
Há consumo de **clique**, mas não de **arrasto** — segurar-e-mover atravessa
para o gameplay/câmera. Esta change adiciona o controle direto (arrastar +
lembrar) por cima do dock.

## Goals / Non-Goals

**Goals:**
- Arrastar um painel de debug para qualquer posição na tela.
- Arrastar não vaza para gameplay/câmera (consumo de drag).
- A posição custom sobrevive ao toggle on/off e ao resize (re-clampada).
- Gesto de reset para voltar um painel (ou todos) ao slot default.

**Non-Goals:**
- Persistir layout entre execuções (Fase 3 — arquivo de layout).
- Redimensionar, recolher/minimizar painéis.
- Docking dinâmico (soltar perto de um canto para re-slotar).
- Z-order / trazer-para-frente entre painéis de debug.
- Modelo de eventos enfileirados (`ui-input-events`): aqui é polling.

## Decisions

### Decision: Arrasto por polling, não por evento
Reusar `isMouseDown(Left)` + `pointerPosition` num `onProcess` do painel:
pressionar dentro da zona de pega (topo do painel) com nenhum arrasto ativo
inicia o arrasto e guarda `grabOffset = pointer - panelOrigin`; enquanto
`isMouseDown`, `origin = pointer - grabOffset`; soltar encerra. **Por quê:**
não depende de `ui-input-events` (change futura, não aberta); o polling já é o
idioma do `DebugToggleNode`/`TimeControlShortcutNode`. Alternativa rejeitada:
esperar o modelo de eventos — bloquearia esta change por algo fora de escopo.

### Decision: Consumo de drag espelhando `mouseClickConsumed`
Adicionar ao `Input` um `mouseDragConsumed` (nome a confirmar na implementação)
setado quando um painel está arrastando, resetado por tick no mesmo ponto em
que `mouseClickConsumed` é resetado. Consumidores de arrasto no gameplay
(ex.: pan de câmera, "puxar e soltar" do pool8) passam a checá-lo. **Por quê:**
sem isso, arrastar o painel arrasta o mundo junto — o usuário pediu painéis
arrastáveis úteis, não um que briga com a cena. Espelhar o flag de clique
mantém o modelo de consumo coerente e previsível. Alternativa rejeitada:
capturar o mouse globalmente — opaco e frágil; o flag explícito é testável.

### Decision: Override de posição sobre o slot, não substituindo-o
O painel mantém o `DockSlot` como **default**; arrastar grava um override de
posição que o `DebugDock` respeita quando presente. Resetar limpa o override e
o painel volta a fluir no slot. **Por quê:** o slot continua sendo o
comportamento são por default e o destino do reset; o override é a camada de
controle direto por cima — exatamente o gancho que a `debug-ui-shell` deixou
("o dock define o default; esta change adiciona override por cima do slot").
Alternativa rejeitada: descartar o slot ao arrastar — perderia o alvo do reset
e o re-fluxo são dos painéis não arrastados.

### Decision: Memória de sessão; re-clamp no resize
O override vive no widget durante a execução (sobrevive ao toggle e ao resize),
re-clampado para dentro do viewport quando `tree.size` encolhe, para um painel
não sumir fora da tela. **Não** persiste em disco. **Por quê:** resolve o uso
real (organizar a sessão atual) com custo mínimo e sem decidir formato de
arquivo/escopo de persistência — isso é uma Fase 3 deliberadamente separada.

## Risks / Trade-offs

- **[Pega vs. clique de conteúdo]** painéis com botões (Time HUD) precisam
  distinguir "arrastar a barra" de "clicar um botão". Mitigação (implementada):
  cada painel ganhou uma **barra de título** (`DebugTheme.headerHeight`, com o
  `title` do widget sobre `headerBackground`) desenhada pela chrome central no
  `ScreenDebugWidget`; a zona de pega é exatamente essa barra. O corpo abaixo
  (linhas do HUD, steppers do Time, readouts) continua roteando clique normal.
  A chrome (fundo + header + borda) virou responsabilidade da base: widgets
  imediatos desenham só o corpo a partir de `bodyOrigin`; os baseados em
  `Panel` (HUD, Time) tornam o `Panel` um container invisível posicionado em
  `bodyOrigin`.
- **[Consumo de drag retroativo]** gameplay existente que faz pan/arraste não
  conhece o flag novo. Mitigação: default não-consumido; varrer os jogos
  shipped (pool8 "puxar e soltar") e adotar o flag onde fizer sentido.
- **[Empilhamento misto]** num slot com painéis arrastados e não-arrastados, o
  re-fluxo do dock deve ignorar os que têm override. Aceitável; o dock só
  empilha os sem override.
- **[Re-clamp surpreende]** encolher a janela move painéis. Mitigação: clamp
  só quando sairiam do viewport; sessão, então é recuperável com o reset.

## Migration Plan

Aditivo. O flag de drag no `Input` tem default não-consumido (sem efeito em
quem não o lê). O override de posição é opcional; sem arrasto, o layout é
idêntico ao da `debug-ui-shell`. Sem breaking changes.

## Open Questions (resolvidas no apply)

- Nome do flag: **`Input.mouseDragConsumed`**, alinhado ao `mouseClickConsumed`.
  Difere num ponto: tem getter/setter default no-op na interface (lê `false`,
  ignora escrita) para que `Input`s que não lidam com arrasto de painel não
  precisem de storage; os dois backends shipped o sobrescrevem com estado real.
- Gesto de reset: **tecla** (`Backspace`) num nó interno
  (`DebugLayoutShortcutNode`, gated em `hud.enabled`) chamando
  `DebugRegistry.resetAllPanelPositions()`; `ScreenDebugWidget.resetPosition()`
  reseta um painel só. Sem botão no HUD por ora (escopo mínimo).
