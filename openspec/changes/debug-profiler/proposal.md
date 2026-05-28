## Why

O `FpsWidget` dá um número agregado — quadros por segundo — mas não diz
**onde o tempo do frame vai**. Quando um jogo engasga, a pergunta didática é
"foi a física? o `_process` dos scripts? o render? o hit-test da UI?". O
`GameLoop.tick` já tem fases bem demarcadas (hitTest → laço fixed-step de
física → process → render), mas nada as cronometra. Um profiler por fase
transforma "está lento" em "a física custou 8 ms em 4 steps" — exatamente o
tipo de visibilidade que casa com o propósito da engine (aprender
arquitetura observando o custo de cada subsistema).

## What Changes

- **`FrameProfile` por-`SceneTree`** (em `tree.debug`): guarda a duração em
  nanos da última medição de cada fase do tick — `hitTest`, `physics`
  (somada sobre as iterações do laço fixed-step, com a contagem de steps),
  `process`, `render` — mais o total do tick. Runtime puro, não
  `@Serializable`.
- **`GameLoop.tick` instrumenta as fases quando o profiling está
  habilitado**: cerca cada fase com `System.nanoTime()` e grava no
  `FrameProfile`. Quando desabilitado, nenhuma medição roda (overhead zero).
- **`ProfilerWidget`** (novo `ScreenDebugWidget`, built-in): mostra ms por
  fase com média móvel (janela deslizante, no espírito do `FpsCounter`) e a
  contagem de steps de física do último frame; o `enabled` dirige a
  instrumentação no loop. Aparece como row togglável no `DebugHud`.

## Capabilities

### New Capabilities

- `debug-profiler`: o `FrameProfile` por-tree, o seam de instrumentação
  gated no `GameLoop.tick`, e o `ProfilerWidget` com média móvel por fase.

### Modified Capabilities
<!-- Nenhuma. A instrumentação do tick é aditiva e gated: com o profiling
     desabilitado (default), o tick roda exatamente como hoje. Descrita como
     requirements ADICIONADAS na nova capability, sem delta no loop/
     engine-core. O widget segue o padrão de built-in das changes anteriores. -->

## Impact

- **Código afetado:**
  - `:engine` `com.neoutils.engine.loop.GameLoop` — `tick` mede as fases
    via `nanoTime` e grava no `FrameProfile` quando habilitado (early-out
    total quando não).
  - `:engine` `com.neoutils.engine.debug` — `FrameProfile`,
    `ProfilerWidget`; campos no `DebugRegistry`.
- **Interação com `debug-time-controls`:** ambas tocam `GameLoop.tick`.
  Ortogonais — o profiler mede as fases que de fato rodam (sob pause a fase
  `physics` mede ~0 e `process` mede o `process(0f)`). Coordenação de merge,
  sem conflito de spec. Esta change consome as fases nomeáveis que a
  time-controls deixou explícitas.
- **Custo em produção:** zero com o profiling desabilitado (default). Mesmo
  habilitado, são alguns `nanoTime` por frame.
- **Não-objetivos (ver design):** contagem de nodes/draw calls, profiling
  por-node ou por-script, alocação/GC tracking, e flame graphs ficam fora do
  MVP — só o tempo por fase do tick.
- **Testes:** `FrameProfile` recebe durações por fase só quando habilitado e
  zera/early-out quando não; soma de física agrega as iterações do laço com
  a contagem de steps; média móvel do widget; widget como built-in e row no
  HUD.
