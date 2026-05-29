## 1. Estado de tempo na SceneTree

- [x] 1.1 Adicionar `var timeScale: Float = 1f` à `SceneTree` com setter coerçindo `>= 0f` (runtime puro, não `@Serializable`).
- [x] 1.2 Adicionar `var paused: Boolean = false` e o mecanismo de step (`fun requestStep()` + flag interno de uso único).
- [x] 1.3 Teste: defaults (1f, false, sem step); `timeScale = -2f` coerçido a `0f`.

## 2. GameLoop honra timeScale / paused / step

- [x] 2.1 No `tick`, computar `gameplayDt = if (paused) 0f else rawDt * tree.timeScale` e acumular `gameplayDt` (não `rawDt`); derivar `frameDt = gameplayDt.coerceAtMost(maxDt)`.
- [x] 2.2 Caminho de step: antes do laço normal, se step pendente E (paused || timeScale==0), drenar pending + `physicsProcess(physicsDt)` + `physics.step` + `process(physicsDt)` + `render`, limpar o flag e retornar; `requestStep()` é no-op quando rodando.
- [x] 2.3 Garantir que `process` roda sempre (com `0f` quando congelado), `hitTestUI` e `render` sempre; física só quando `gameplayDt` acumula.
- [x] 2.4 Confirmar que o clamp de spiral-of-death e `maxStepsPerFrame` seguem cobrindo `timeScale` alto.

## 3. Testes do loop

- [x] 3.1 Teste: `timeScale = 0.25f` roda ~¼ dos steps de física vs `1f` para o mesmo `rawDt`.
- [x] 3.2 Teste: `process` invocado com `d * timeScale` (abaixo de `maxDt`).
- [x] 3.3 Teste: `paused` → zero `physics.step`, `process(0f)`, `hitTestUI`+`render` ainda rodam.
- [x] 3.4 Teste: um `requestStep()` pausado → exatamente um `physics.step`; tick seguinte sem pedido → zero steps.
- [x] 3.5 Teste: default (1f, false, sem step) reproduz o comportamento atual do tick.

## 4. TimeControlWidget + atalhos

- [x] 4.1 Criar `TimeControlWidget : ScreenDebugWidget` (`title = "Time"`, `enabled = false`) mostrando `paused`/`timeScale`.
- [x] 4.2 Controles via `Button` (operáveis por `hitTestUI` sob pause): pause/resume, step, ciclo de presets de velocidade — mutando `tree.paused`/`tree.timeScale`/`requestStep()`.
- [x] 4.3 Node interno de polling de atalhos (estilo `DebugToggleNode`) rodando em `process` (vivo sob pause via `process(0)`); teclas configuráveis para pause/step/velocidade.
- [x] 4.4 Registrar como built-in no `DebugRegistry` + campo de conveniência.

## 5. Testes do widget

- [x] 5.1 Teste: widget built-in não-nulo após `start()`, row togglável no HUD.
- [x] 5.2 Teste: controle de resume sob pause seta `paused = false` via hit-test.
- [x] 5.3 Teste: controle de step sob pause chama `requestStep()` e o próximo tick avança um step.

## 6. Fechamento

- [x] 6.1 Rodar a suíte do `:engine`; garantir verde (regressão do loop com defaults).
- [x] 6.2 `openspec validate debug-time-controls --strict` e revisar coerência specs↔implementação.
