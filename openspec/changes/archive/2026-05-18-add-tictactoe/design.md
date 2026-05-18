## Context

A fundação da engine (change `engine-foundation`, arquivada) entregou scene graph, math, SPIs de `Renderer`/`Input`, física O(N²) e Pong como prova viva. Pong exercita o eixo "tempo real": loop contínuo, input contínuo (W/S sustentados), física e render simultâneos.

Velha (tic-tac-toe) é proposta como **segundo jogo de aceitação** porque cobre o eixo ortogonal — **interação discreta e máquina de estados**: um click define uma jogada, turnos alternam, a partida tem início/meio/fim explícitos. Nenhuma das primitivas atuais cobre isso de ponta a ponta: o `Input` SPI não captura eventos de mouse button (só `pointerPosition`), o `Renderer` SPI não tem `drawLine` (necessário para diagonais do `X` e para destacar a linha vencedora), e `Rect` ainda não expõe `contains(Vec2)` para hit-test ergonômico.

A change `event-driven-games` foi proposta e arquivada (commit `fcd748f`) sem implementação — sinal de que a abordagem "introduzir sinais/eventos junto com o primeiro jogo orientado a eventos" foi prematura. Esta change adota o caminho oposto: **mantém o estilo de referência direta de Pong** e evolui apenas o mínimo de SPI necessário para que o jogo funcione.

## Goals / Non-Goals

**Goals:**

- Velha jogável humano vs humano em `:games:tictactoe`, com X começando, alternância de turnos, detecção de vitória/empate, hover ghost, destaque da linha vencedora e reset por click pós-fim.
- Evolução mínima do `Input` SPI para captura de eventos de mouse button (`MouseButton`, `wasMouseClicked`, `isMouseDown`), seguindo o mesmo padrão tick-snapshot já usado para teclas.
- `Renderer.drawLine` adicionado à SPI e implementado no backend Compose.
- `Rect.contains(Vec2)` adicionado em `:engine` como helper de hit-test, com cobertura de teste unitário.
- Layout responsivo da cena: board centralizado, escala com `onResize`, status text legível em qualquer dimensão razoável de janela.
- `:games:tictactoe` exercita apenas API pública de `:engine` + `:engine-compose`, sem dependências entre módulos de jogos.

**Non-Goals:**

- IA (minimax ou similar) — postergada para change futura, se decidida.
- Sinais/eventos como primitiva da engine — referência direta continua sendo a forma de comunicação entre nós.
- Animação de transições (fade da marca, slide da linha vencedora). Render é estático tick-a-tick.
- Som / feedback háptico / temas.
- Tecla `R` ou botão "Reset" dedicado — o reset é por click após o fim da partida.
- Persistência de placar entre partidas ou entre execuções.
- Refatorar `Shape` para usar `transform.rotation` — fora de escopo; `drawLine` substitui a necessidade de rect rotacionado para o `X`.

## Decisions

### Decisão 1: Board monolítica (não Cell-por-Node)

`Board` é um único `Node2D` que carrega o estado completo (`cells: Array<Mark?>(9)`, `currentPlayer`, `winner`), faz hit-test no `onUpdate`, e renderiza grid + marcas + ghost + linha vencedora no `onRender`.

**Por quê:**

- O estado é trivial e uniforme — 9 slots idênticos. Dividir em 9 `CellNode` cria boilerplate sem expressar comportamento distinto por célula.
- Em Pong, cada nó tem comportamento qualitativamente diferente (Paddle move com input, Ball move com física, Wall reflete, Goal pontua). Em velha, todas as células fazem a mesma coisa.
- Verificação de vitória é uma operação sobre o array de células, não sobre nós isolados — natural de implementar em um lugar só.
- Um arquivo de leitura linear (`Board.kt`) é mais didático que 9 instâncias coordenando estado.

**Alternativas consideradas:**

- `Board` + 9 `CellNode` (cada um com `cellIndex`, `mark`, hit-test próprio e referência de volta à Board): mais "Godot-like", mas coordenação fica mais espalhada — Cell precisaria pedir à Board "é minha vez? posso aceitar?". Rejeitado por custo/benefício.
- Lógica direto na `Scene` sem Board intermediário: perde o agrupamento conceitual e mistura layout de scene com regras de jogo. Rejeitado.

### Decisão 2: Captura de mouse button via padrão pendingPresses (mesmo das teclas)

`ComposeInput` já implementa um padrão tick-coerente para teclas: três conjuntos — `downKeys` (estado contínuo), `pendingPresses` (acumulado entre ticks) e `pressedThisTick` (snapshot do tick atual). Mouse buttons replicam exatamente esse modelo com `downButtons`, `pendingButtonPresses`, `buttonsPressedThisTick`.

**Por quê:**

- Consistência conceitual: gameplay code lê `wasMouseClicked(LEFT)` igual lê `wasKeyPressed(W)`. Sem novo modelo mental.
- O `beginTick()` do `ComposeInput` já é chamado pelo `GameSurface` antes de cada tick — basta estendê-lo para também esvaziar/preencher o conjunto de botões.
- Compose entrega eventos de mouse via `pointerInput`/`awaitPointerEventScope` (já em uso para tracking de posição). Captura de press/release é leitura de `PointerEventType.Press`/`Release` no mesmo fluxo.

**Alternativas consideradas:**

- Callback-based API (`onMouseClicked { ... }` registrável no Input): quebra o estilo polling-only da SPI atual. Rejeitado por inconsistência.
- Apenas `isMouseDown(button)` sem edge detection: o gameplay precisaria filtrar a transição down→down→down manualmente, replicando o que `wasKeyPressed` já resolve. Rejeitado.

### Decisão 3: `Renderer.drawLine` como novo método da SPI

O `Renderer` ganha `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)`. Implementação Compose usa `DrawScope.drawLine` nativo.

**Por quê:**

- Necessário para desenhar o `X` (duas diagonais) e a linha vencedora (segmento sobre a trinca).
- Útil de forma genérica em qualquer jogo futuro: vetores de debug, sublinhados, traços decorativos, eixos.
- Alternativa de "X feito com duas `Text("X")` ou com rotação de rect" é mais frágil e amarra a engine a workarounds. `drawLine` é primitiva natural de 2D rendering.
- Aumento de superfície mínimo na SPI (um método).

**Alternativas consideradas:**

- Usar `drawText("X", ...)` para renderizar o X com fonte do sistema: ok como atalho, mas o visual depende da fonte e fica inconsistente com o `O` (que é círculo geométrico). Rejeitado por estética e por adiar a primitiva inevitável.
- Implementar rotação de `Shape` para permitir rect diagonal: refator maior, sai do escopo. Rejeitado.

### Decisão 4: Hover ghost via leitura direta de `pointerPosition` no `onUpdate` da Board

A cada tick, `Board` lê `input.pointerPosition`, calcula a célula sob o ponteiro (`hoveredCell: Int?`), e usa esse índice no `onRender` para desenhar uma marca com alpha reduzido se a célula estiver vazia e o jogo não tiver terminado.

**Por quê:**

- Não precisa de novo conceito de "evento de hover" — basta consultar o estado já exposto pelo `Input`.
- Mesma lógica de hit-test do click: `Rect(cellOrigin, cellSize).contains(pointerPosition)`. Reuso natural.

**Alternativas consideradas:**

- Hover como evento separado disparado pelo runtime: overengineering para 9 quadrados. Rejeitado.

### Decisão 5: Reset por click pós-fim, sem teclado

Quando `winner != null` ou `isDraw`, qualquer click esquerdo (em qualquer lugar da janela) reseta `cells`, zera `winner`, e define `currentPlayer = X`.

**Por quê:**

- UX mínimo viável: depois do "X venceu — clique para jogar de novo", o usuário sabe o que fazer.
- Sem teclado evita dependência de foco do teclado pós-fim (caso raro mas chato).

**Alternativas consideradas:**

- Tecla `R` adicional: redundante para o escopo. Pode ser adicionado depois se necessário.
- Botão "Reset" visual: vira mini-framework de UI. Rejeitado.

### Decisão 6: Layout responsivo via `Scene.onResize`

`TicTacToeScene.onResize(width, height)` recalcula tamanho de célula como `min(width, height - statusReserved) / 3`, recentraliza a Board, e atualiza dimensões internas. `StatusText` posiciona acima da Board.

**Por quê:**

- Espelha o padrão já adotado por `PongScene.onResize`.
- Garante "minimamente jogável" em qualquer tamanho de janela inicial ou após resize.

### Decisão 7: Linha vencedora desenhada como parte do render normal pós-vitória

Quando `winner != null`, a Board persiste qual trinca venceu (`winningLine: Triple<Int, Int, Int>?`) e o `onRender` desenha `drawLine` do centro da primeira célula ao centro da terceira, com cor de destaque, sobreposta às marcas.

**Por quê:**

- Detecção de vitória já precisa identificar qual linha venceu — armazenar isso é uma referência a mais, custo zero.
- Feedback visual de fim de partida fica inequívoco.

## Risks / Trade-offs

- **`pointerPosition` em coordenadas inesperadas** → o `ComposeInput.onPointerMove` recebe coordenadas do `pointerInput` do Canvas, e o `Renderer` desenha no mesmo `DrawScope`. Verificar manualmente que clicks acertam exatamente onde o ghost aparece; se houver discrepância (DPI scaling, padding), corrigir no backend.
- **Beligerância de foco entre Compose Window e Canvas** → cliques no Canvas em `:games:pong` já chegam ao `ComposeInput` (via `pointerInput`), mas o `Canvas` requer focus para teclado. Mouse não depende de focus do mesmo jeito; ainda assim, vale validar no jogo rodando.
- **Click "fantasma" no reset** → se o usuário clicar para resetar exatamente sobre uma célula, esse click só reseta (não joga). Documentar no scenario do spec; comportamento intencional para evitar partidas começando no meio de uma jogada acidental.
- **`drawLine` em DPI alto** → espessuras pequenas podem ficar inconsistentes em telas HiDPI. Escolher espessura proporcional ao tamanho da célula em vez de pixels absolutos.
- **Acúmulo de pressões entre ticks longos** → se um tick demorar mais que o intervalo de polling do mouse, múltiplos cliques podem cair no mesmo `pressedThisTick`. Comportamento aceitável (mesmo do `pressedThisTick` para teclas); documentar.

## Migration Plan

Não há migração — esta change apenas adiciona. As mudanças em `Input` e `Renderer` são aditivas (novos métodos com default ou implementação no backend único existente). Pong não é afetado e continua passando em todos os seus testes.

## Open Questions

- **Cor das marcas X e O**: padrão simples (X branco, O branco) ou diferenciado por jogador (X azul, O vermelho)? Decidir na implementação, sem impacto em spec.
- **Status text: localizado em português ou inglês?** CLAUDE.md permite texto in-game em português; manter consistência com Pong (que não tem texto além dos placares). Decidir na implementação.
