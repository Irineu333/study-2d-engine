## Context

A `engine-foundation` é tick-based por padrão: `withFrameNanos` dispara o loop a ~60Hz e cada nó tem chance de atualizar em todo frame. Pong se encaixa porque seu estado é função contínua de `dt`. Jogo da Velha e Campo Minado têm estado discreto que só muda em resposta a entrada do usuário — entre dois cliques, a tela é estritamente a mesma. Manter o loop a 60Hz aqui custa CPU, energia e ruído de profiling sem retorno funcional.

Além disso, em jogos event-driven o padrão "Ball segura referência pra LeftGoal" perde o charme: o tabuleiro precisa avisar que uma célula foi clicada, mas o tabuleiro não deveria conhecer o controlador de jogo. Sinais resolvem isso sem trazer reflexão ou anotações.

Restrições levantadas no explore mode:
- Manter scene graph estilo Godot (herança em `Node`).
- Não quebrar `engine-foundation`: tudo aditivo, default backward-compatible.
- `:engine` continua sem dependência em `androidx.compose.*`.
- Sinais e `RenderMode` vivem em `:engine`; suas integrações vivem em `:engine-compose`.

## Goals / Non-Goals

**Goals:**

- Sinais tipados que tornem comunicação desacoplada barata e descobrível na API pública (autocomplete diz o que um nó pode emitir).
- `RenderMode.OnDemand` que zere CPU quando o jogo está parado e desperte instantaneamente em input ou em `requestRender()` explícito.
- Eventos de pointer (`onClick`, `onRightClick`, `onHover`) entregues ao nó certo via hit-test em `bounds()`, sem que o desenvolvedor escreva lógica de hit-test.
- Distinção entre botão esquerdo e direito do mouse no `Input` SPI.
- Jogo da Velha jogável dois humanos + reset.
- Campo Minado completo (vitória/derrota, timer, contador, flood fill, first-click safe).
- DX: capacidade de forçar `Continuous` em runtime pra ver o loop "ao vivo" durante debug.

**Non-Goals:**

- Animações por tween/keyframe.
- Som / áudio.
- Save state / persistência.
- Editor visual (próxima change).
- "Area2D" estilo Godot — hit-test mora no input system, não no `PhysicsSystem`. Áreas físicas continuam sendo só `Collider`.
- Refatorar Pong para usar sinais. Pong continua como está, prova de não-regressão.
- Suporte a touch / multi-touch. Mouse + teclado são suficientes.
- Drag, double-click, gestos compostos. Só clique simples e hover.
- Configurador de dificuldade do Minado em UI. Constante de código por enquanto.

## Decisions

### Decisão 1: Sinais tipados via parâmetro genérico, não via `Any`

Sinais são declarados como `val onCellClick = Signal<CellCoord>()` no nó emissor, onde `Signal<T>` é uma classe simples com `emit(value: T)` e `connect(handler: (T) -> Unit): Connection`. O parâmetro genérico é o payload. Sinais sem payload usam `Signal<Unit>()` ou um alias `SignalUnit`.

**Por quê:**
- Mantém tipo no callsite (autocomplete, refactor seguro).
- Não exige reflexão nem anotações; alinhado com a postura "Kotlin idiomático".
- `connect` retorna uma `Connection` com `disconnect()` para limpeza explícita (Godot tem o mesmo padrão).

**Alternativas consideradas:**
- Bus global tipo EventBus: desacopla demais, custa rastreabilidade. Rejeitado.
- Sinais por reflexão sobre nome de método (estilo Godot puro, `signal "cell_clicked"` + `connect("cell_clicked", target, "_on_cell_clicked")`): excelente pro editor visual depois, mas ruim em código Kotlin puro. Pode coexistir no futuro se o editor exigir, sem afetar a API atual.
- `Flow<T>` do `kotlinx.coroutines`: poderoso, mas traz coroutines pra hot path da engine e complica a API; sinais síncronos são mais simples e suficientes pros jogos do roadmap.

### Decisão 2: Sinais são síncronos, single-threaded, emitidos no thread do loop

`signal.emit(x)` chama handlers conectados imediatamente, na mesma thread, em ordem de conexão. Não há fila, prioridade ou async.

**Por quê:**
- Simplicidade. Toda a engine roda single-threaded; introduzir async aqui criaria nova classe de bugs sem necessidade real.
- Em jogos event-driven, o handler é tipicamente "marca a cena como dirty e atualiza estado" — não há valor em assincronia.

**Trade-off aceito:** se um handler emitir outro sinal recursivamente, o stack pode crescer. Documentado; nenhum caso real do roadmap reentra mais de 1–2 níveis.

### Decisão 3: `RenderMode` no `Scene`, com wake-up explícito por evento ou por `requestRender()`

`enum class RenderMode { Continuous, OnDemand }`. `Scene` tem `var renderMode: RenderMode = Continuous`. O `GameLoop` (ou o `GameSurface` no runtime Compose) consulta o modo:

- `Continuous`: tick a cada `withFrameNanos`, como hoje.
- `OnDemand`: tick somente se uma das condições for verdadeira:
  1. há evento de input pendente entregue ao próximo tick;
  2. `scene.requestRender()` foi chamado desde o último tick (marca dirty);
  3. uma animação registrada como "em curso" está ativa.

Quando ocioso, o runtime não força frame — em Compose isso significa não chamar `withFrameNanos` (ou consumir o frame sem fazer trabalho).

**Por quê:**
- API mínima: dois enum cases e um método `requestRender()`.
- Backward-compatible: default é `Continuous`, Pong inalterado.
- `requestRender()` explícito é menos mágico do que detectar mutação automaticamente; o jogo sabe quando mudou de estado.

**Alternativas consideradas:**
- Detecção automática de dirty via snapshots / Compose-like reactive: poderoso, mas amarra a engine num modelo reativo que prefiro não introduzir agora. Documentado como evolução possível para o editor.
- Modo "render-once" sob demanda: confunde "redesenhar a tela" com "rodar um tick". Manter a abstração de tick é mais previsível.

### Decisão 4: Eventos de pointer entregues por hit-test no input system, não no PhysicsSystem

`Input` SPI ganha um canal de eventos discretos (`PointerEvent.Click(button, position)`, `PointerEvent.Hover(position)`). O runtime Compose os enfileira por frame. O `GameLoop`, antes de propagar `onUpdate`, executa um **InputDispatch**: percorre a árvore (ordem reversa de render — topo primeiro) e entrega o evento ao primeiro nó interativo cujos `bounds()` contêm a posição.

Nós interativos declaram interesse implementando uma interface `Interactive` (ou herdando de `Control` se a hierarquia fizer sentido) com hooks `onClick(event)`, `onRightClick(event)`, `onHover(event)`. Hit-test em `bounds()` é axis-aligned; consistente com `Collider`.

**Por quê:**
- Mantém separação de responsabilidades: `PhysicsSystem` trata colisão entre nós; input trata pessoa contra cena.
- Reuso de `bounds()` já existente; nenhum nó precisa duplicar geometria.
- Topo-primeiro respeita ordem visual: um botão sobreposto a um fundo recebe o clique.

**Alternativas consideradas:**
- Tornar todo `Node` interativo por padrão: poluição da API; nem todo nó tem `bounds`.
- Z-order explícito separado de ordem de filhos: prematuro. Ordem de filhos basta enquanto não houver overlay complexo.
- Consumir/propagar eventos (`event.consume()`): nem implementado nesta change — primeiro hit ganha. Documentado como evolução se um caso real aparecer.

### Decisão 5: Botões do mouse como enum `PointerButton`

`enum class PointerButton { Primary, Secondary, Tertiary }`. Naming neutro a left/right (acomoda destros e canhotos sem mudar API).

**Por quê:**
- Tradição em APIs modernas (Web, Android) usa "primary/secondary"; Compose também.
- Mantém porta aberta pra `Tertiary` (scroll-click) sem mexer no enum depois.

### Decisão 6: `Grid<T>` mora em `:engine` como utilitário

`class Grid<T>(val rows: Int, val cols: Int, init: (Int, Int) -> T)` com `get(row, col)`, `set(row, col, value)`, `forEachIndexed`, `neighbors(row, col, includeDiagonals)`.

**Por quê:**
- É genuinamente reutilizável entre Velha, Minado e jogos futuros (puzzles, board games, level grids).
- Não introduz dependência nem complica a API da engine.
- Custo de manutenção marginal (~40 linhas).

**Alternativas consideradas:**
- Replicar em cada jogo: viola DRY já no segundo uso.
- Componente complexo (cellSize, conversão tela↔grid, render embutido): postergado. Mantemos `Grid<T>` como estrutura de dados pura; conversão de coordenadas fica nos jogos.

### Decisão 7: Pong intocado; nenhuma migração de jogo existente

Pong não é refatorado pra usar sinais nem `OnDemand`. Continua exatamente como ficou após `engine-foundation`. Serve como prova de não-regressão: se sinais quebrassem algo em `Node`/`Scene`/`GameLoop`, Pong falharia ao rodar.

**Por quê:**
- Reduz superfície de risco da change.
- Demonstra que adições são realmente aditivas, não exigem migração.

### Decisão 8: Tag de log "Events" para emissões de sinais; opt-in via Debug

Toda emissão de sinal, quando `Debug.logSignals = true`, gera `Log.d(tag = "Events", message)` com nome do sinal, payload (toString) e nó emissor. Default off pra não poluir logs em desenvolvimento normal.

**Por quê:**
- Sinais são a parte mais "ação à distância" da arquitetura; observabilidade barata vale ouro pra debugar comportamentos emergentes.
- Custo zero quando desligado (um `if (Debug.logSignals)` no `emit`).

## Risks / Trade-offs

- **[Risco] Tick-on-demand mascara bugs onde o jogo "deveria mudar" mas não chamou `requestRender()`** → Mitigação: DX permite forçar `Continuous` em runtime via atalho global. Documentado em `CLAUDE.md`. Em jogos curtos do roadmap, o sintoma é óbvio (tela trava em estado intermediário) e o fix é localizado.

- **[Risco] Sinais síncronos podem causar reentrância profunda se mal usados** → Mitigação: documentar; detectar via log se profundidade exceder N (~32). Casos reais do roadmap não chegam a 3 níveis.

- **[Risco] Hit-test axis-aligned em `bounds()` é simplista para nós rotacionados** → Mitigação: nenhum jogo do roadmap usa rotação significativa em nós clicáveis. Documentado como ponto de evolução (oriented bounding box quando rotação em UI aparecer).

- **[Risco] Adicionar dois jogos novos triplica o número de módulos executáveis (Pong, Velha, Minado)** → Aceito. Cada um se autocontém; quando o editor entrar, ele vira launcher.

- **[Risco] `Grid<T>` pode parecer escopo creep ("e se virar uma biblioteca de estruturas de dados?")** → Mitigação: limitado a `Grid` 2D. Qualquer estrutura nova precisa de proposta própria.

- **[Trade-off] Pong fica como exceção arquitetural (não usa sinais, não usa OnDemand)** → Aceito. Refatorar Pong agora aumentaria escopo sem agregar aprendizado novo; pode entrar em change de "cleanup" futuro se incomodar.

- **[Trade-off] Hit-test invoca `bounds()` em N nós por evento** → Aceito. N pequeno; mesma ordem de grandeza do `PhysicsSystem` O(N²) que já aceitamos.

## Migration Plan

Não há produção. Migração é puramente de código:

1. Implementar primitivos novos em `:engine` (sinais, `RenderMode`, eventos de pointer, `PointerButton`, `Grid<T>`).
2. Estender `:engine-compose` (hit-test no `GameSurface`, despacho de pointer events, mouse buttons, controle de pulso por `RenderMode`).
3. Estender DX (toggle global de `RenderMode`, log de sinais).
4. Implementar `:games:tictactoe`. Verificar manualmente vitória/empate/reset.
5. Implementar `:games:minesweeper`. Verificar manualmente revelar/flag/flood-fill/first-click safe/vitória/derrota/timer.
6. Atualizar `CLAUDE.md` (sinais, RenderMode, roadmap progredido).
7. Verificar Pong continua intocado e jogável (`./gradlew :games:pong:run`).

Rollback: `git revert` da change. Como nada externo depende, é puramente local.

## Open Questions

- **`Interactive` interface vs `Control` base class para nós que recebem eventos de pointer**: interface composa melhor em Kotlin (sem herança múltipla artificial), mas Godot usa `Control` como classe base (`Button : Control`). Decisão atual: **interface** `Interactive` no `Node2D`; subclasses como `Cell` em Velha implementam diretamente. Revisitar se o editor exigir hierarquia mais formal.
- **`Connection.disconnect()` vs `WeakReference` automática**: começar com disconnect explícito (idêntico a Godot). Sinais em jogos curtos do roadmap não vivem além da cena.
- **Comportamento de hit-test em nó invisível**: se `Node2D.visible = false`, deve receber clique? Decisão atual: **não**. Documentar.
- **First-click safe no Minado: garantir a célula clicada ou também as 8 vizinhas estarem sem mina?** Decisão atual: a versão clássica garante célula + vizinhas (cantos podem ter 0 minas adjacentes). Implementar essa.
- **Timer do Minado: pausa ao desfocar janela?** Decisão atual: **não pausa** (jogos clássicos não pausam); documentar e revisitar se incomodar.
