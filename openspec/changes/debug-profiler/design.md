## Context

`GameLoop.tick` tem quatro fases observáveis: `hitTestUI(input)`; o laço
fixed-step (`physicsProcess` + `physics.step`, 0..`maxStepsPerFrame`
iterações por frame); `process(frameDt)`; `render(renderer)`. Nenhuma é
cronometrada hoje. O `FpsCounter` já estabelece o estilo de medição
(timestamps de `System.nanoTime`, janela deslizante) e o `MomentumWidget` o
de amostragem por hook + ring buffer + reset no enable. O `GameLoop` recebe
`tree`, então `tree.debug` é a superfície comum onde gravar medições para um
widget ler — mesmo padrão do buffer de contatos da `debug-physics-gizmos`.

A `debug-time-controls` reestruturou o topo do `tick` (escala de `dt`,
pause, step) mas manteve as fases nomeáveis intactas — esta change as
instrumenta.

## Goals / Non-Goals

**Goals:**

- Medir ms por fase do tick (hitTest, physics, process, render) + total.
- Agregar o tempo de física sobre as iterações do laço, com contagem de steps.
- Suavizar com média móvel (leitura estável, não números pulando por frame).
- Overhead zero quando desabilitado; gating consistente com o resto do debug.

**Non-Goals:**

- Profiling por-node ou por-script (quem dentro do `_process` custou).
- Contagem de draw calls / nodes / alocações / GC.
- Flame graphs, captura de traces, export.
- Medir sub-fases internas de `physics.step` (broad phase vs solver).

## Decisions

### D1 — `FrameProfile` por-tree em `tree.debug`, gravado pelo loop

`tree.debug.profiler` expõe um `FrameProfile` com a duração em nanos da
última medição de cada fase (`hitTestNanos`, `physicsNanos`, `processNanos`,
`renderNanos`, `totalNanos`) e `physicsSteps: Int`. Runtime puro, não
`@Serializable`, per-tree. O `GameLoop.tick`, quando o profiling está
habilitado, cerca cada fase com `System.nanoTime()` e grava no `FrameProfile`
ao fim do tick.

**Por que em `tree.debug` e não no `GameLoop`/widget:** o widget só alcança
`tree`; o loop tem `tree`. É a mesma superfície comum usada pelo buffer de
contatos. Mantém o widget como leitor puro.

### D2 — Fases medidas e agregação da física

Quatro fases + total:

- `hitTest`: ao redor de `hitTestUI(input)`.
- `physics`: **soma** dos intervalos de cada iteração do laço fixed-step
  (cada `physicsProcess` + `physics.step`), acumulada numa variável local e
  gravada uma vez por frame; `physicsSteps` conta as iterações.
- `process`: ao redor de `process(frameDt)`.
- `render`: ao redor de `render(renderer)`.
- `total`: do início ao fim do `tick`.

Um "overhead/other" derivado (`total - Σfases`) pode ser exibido pelo widget
sem ser gravado separadamente (cálculo no draw).

### D3 — `ProfilerWidget` com média móvel por fase

`ProfilerWidget : ScreenDebugWidget`. Mantém, por fase, uma média móvel
sobre uma janela (ring buffer por fase, ~60 amostras, no espírito do
`MomentumWidget`/`FpsCounter`). Amostra no `onProcess` (lê o `FrameProfile`
do frame corrente) quando habilitado; reseta os buffers ao flipar
`enabled` de `false` para `true` (sem médias stale). `drawDebug` mostra,
por fase, os ms suavizados (e opcionalmente % do total) e a contagem de
steps do último frame.

**Por que média móvel:** ms cru por frame pula demais para leitura; a média
estabiliza, como o FPS de janela de 1 s já faz.

### D4 — Gating pelo `enabled` do widget

O `GameLoop` consulta o flag de profiling (dirigido pelo
`ProfilerWidget.enabled`) e só mede quando on — early-out total quando off,
sem nenhum `nanoTime` no caminho de produção. Mesmo seam de gating do
`ContactGizmoWidget`.

### D5 — Composição com pause/step da time-controls

Sob pause (`process(0f)`, sem física), as fases ainda são medidas: `physics`
≈ 0 com `physicsSteps = 0`, `process` mede o `process(0f)`, `render` mede o
frame congelado. No caminho de step, o profiler mede o único step injetado.
Nenhuma lógica especial — o profiler mede o que rodar.

## Risks / Trade-offs

- **[Acoplar `GameLoop` a mais estado de `tree.debug`]** → o `tick` passa a
  gravar no `FrameProfile`. Mitigação: gated e early-out quando off; o loop
  já lê `tree` extensivamente; sem custo em produção.
- **[`nanoTime` em excesso distorce a própria medição]** → cercar cada fase
  adiciona chamadas. Mitigação: ~5 `nanoTime` por frame é desprezível
  ante fases de ms; o total inclui esse overhead honestamente.
- **[Média móvel esconde spikes]** → suavizar oculta o frame ruim isolado.
  Mitigação: gravar também o `*Nanos` cru do último frame no `FrameProfile`;
  o widget pode exibir cru + média (cru destaca o spike).
- **[Conflito de merge com `debug-time-controls` no `tick`]** → mesmas
  linhas. Mitigação: concerns ortogonais; aplicar as duas em sequência
  (a time-controls deixou as fases nomeadas justamente para isso).
