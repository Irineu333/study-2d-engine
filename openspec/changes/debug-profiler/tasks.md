## 1. FrameProfile

- [x] 1.1 Criar `FrameProfile` em `com.neoutils.engine.debug` com `hitTestNanos`, `physicsNanos`, `processNanos`, `renderNanos`, `totalNanos` (Long) e `physicsSteps` (Int). Runtime puro, não `@Serializable`.
- [x] 1.2 Expor via `tree.debug` (ex.: `tree.debug.profiler` ou `tree.debug.frameProfile`); instanciado por-tree.
- [x] 1.3 Teste: dois trees têm `FrameProfile` independentes.

## 2. Instrumentação do GameLoop

- [x] 2.1 No `tick`, consultar o flag de profiling (dirigido pelo `ProfilerWidget.enabled`); early-out total das medições quando off.
- [x] 2.2 Quando on: cercar `hitTestUI`, somar os intervalos de cada iteração do laço fixed-step em `physicsNanos` (contando `physicsSteps`), cercar `process` e `render`; medir `totalNanos` do início ao fim; gravar no `FrameProfile`.
- [x] 2.3 Confirmar que com profiling off o `tick` não chama `nanoTime` (sem overhead).

## 3. Testes do loop

- [x] 3.1 Teste: fases não gravadas quando off; gravadas (hitTest/process/render/total populados) quando on.
- [x] 3.2 Teste: frame que drena 3 steps → `physicsSteps == 3` e `physicsNanos` = soma dos 3.
- [x] 3.3 Teste: `totalNanos >= Σ` das quatro fases.

## 4. ProfilerWidget

- [x] 4.1 Criar `ProfilerWidget : ScreenDebugWidget` (`title = "Profiler"`, `enabled = false`) com ring buffer de média móvel por fase (~60 amostras, estilo `MomentumWidget`).
- [x] 4.2 `enabled` dirige a instrumentação do loop; reset dos buffers no flip `false→true`.
- [x] 4.3 `onProcess` amostra o `FrameProfile` do frame quando habilitado; `drawDebug` mostra ms suavizados por fase (+ opcional % do total e ms cru do último frame) e `physicsSteps`.
- [x] 4.4 Registrar como built-in no `DebugRegistry` + campo de conveniência.

## 5. Testes do widget

- [x] 5.1 Teste: built-in não-nulo após `start()`, row togglável no HUD; habilitar liga a medição no loop.
- [x] 5.2 Teste: flip `false→true` reseta as janelas de média (sem stale).
- [x] 5.3 Teste: desabilitado → zero draws e zero medições no loop.

## 6. Fechamento

- [x] 6.1 Rodar a suíte do `:engine`; garantir verde (tick com profiling off idêntico ao atual).
- [x] 6.2 `openspec validate debug-profiler --strict` e revisar coerência specs↔implementação.
