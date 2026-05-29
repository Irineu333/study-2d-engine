## Why

Debugar uma colisão que dá errado em 2 frames, ou observar a evolução de um
impulso, é quase impossível em tempo real a 60 Hz. Falta o controle de tempo
mais básico de qualquer engine/debugger de jogo: **pausar**, **avançar um
frame por vez**, e rodar em **câmera lenta / acelerado**. O `GameLoop.tick`
hoje sempre consome o `dt` real cru — não há como congelar o mundo mantendo
o HUD e a UI vivos, nem desacelerar a simulação para enxergar o que o solver
faz passo a passo.

## What Changes

- **`SceneTree.timeScale: Float = 1f`** — multiplica o `dt` de gameplay antes
  do `GameLoop` acumular para a física e antes do `_process`. `0.25` = câmera
  lenta, `2.0` = acelerado, `0` = congelado. First-class na `SceneTree`
  (como `debugHudKey`), então gameplay também pode usar (slow-mo como
  feature), com o debug só fornecendo a UI.
- **`SceneTree.paused: Boolean = false`** — freeze duro independente do
  `timeScale`. Quando pausado, o `GameLoop` **não roda física** e chama
  `process(0f)` em vez de pular o `process` — nodes de gameplay não avançam,
  mas nodes de debug que pollam input (toggle do HUD, controles de tempo) e
  o `hitTestUI` continuam vivos, então o HUD permanece operável pausado.
- **Step-frame** via `SceneTree.requestStep()` — quando pausado, executa
  exatamente **um** step de física (`physicsProcess` + `physics.step` em
  `physicsDt`) + um `process(physicsDt)` nesse tick, depois auto-limpa o
  pedido. Permite avançar a simulação clique a clique / tecla a tecla.
- **`TimeControlWidget`** (novo `ScreenDebugWidget`, built-in): mostra o
  estado (paused / timeScale) e oferece controles — Pause/Resume, Step,
  ciclo de presets de velocidade — operáveis via `hitTestUI` mesmo pausado.
  Teclas de atalho são pollada por um node de debug que roda sob
  `process(0)` (vivo no pause).
- **`GameLoop.tick` honra os três.** Default (`timeScale = 1`,
  `paused = false`, sem step pendente) preserva o comportamento atual byte a
  byte; a física fixed-step e o clamp de spiral-of-death seguem intactos.

## Capabilities

### New Capabilities

- `debug-time-controls`: as properties `timeScale`/`paused` + o mecanismo de
  step na `SceneTree`, a semântica do `GameLoop.tick` sob elas, o
  `TimeControlWidget` e o polling de atalhos vivo durante o pause.

### Modified Capabilities
<!-- Nenhuma. A mudança no GameLoop.tick é aditiva: com os defaults
     (timeScale=1, paused=false, sem step) o comportamento é idêntico ao
     atual, então é descrita como requirements ADICIONADAS na nova
     capability, sem delta nas specs do loop/engine-core. O widget e seu
     registro seguem o padrão das changes anteriores (built-in novo, row no
     HUD automática). -->

## Impact

- **Código afetado:**
  - `:engine` `com.neoutils.engine.tree.SceneTree` — properties
    `timeScale`, `paused`, e o flag/método de step (runtime puro, não
    `@Serializable`).
  - `:engine` `com.neoutils.engine.loop.GameLoop` — `tick` lê os três:
    escala o `rawDt` por `timeScale`, trata `paused` como `dt=0` de gameplay
    + sem física, e consome `requestStep()` injetando um step fixo.
  - `:engine` `com.neoutils.engine.debug` — `TimeControlWidget` + node de
    polling de atalhos; campos no `DebugRegistry`.
- **Interação com `debug-profiler`:** ambas tocam `GameLoop.tick`. Concerns
  ortogonais (escala/pause vs medição de fases); sem conflito de spec, só
  coordenação de merge. Esta change deixa o `tick` legível para o profiler
  instrumentar depois.
- **Custo em produção:** zero com os defaults. As leituras são um
  multiplicador e dois branches por tick.
- **Não-objetivos (ver design):** `process_mode` por-node estilo Godot
  (exemção seletiva do pause), interpolação visual entre steps, e
  determinismo de replay ficam fora do MVP.
- **Testes:** `timeScale` escala steps de física e `frameDt`; `paused`
  congela gameplay mas mantém `process` rodando (dt=0) e a física parada;
  `requestStep` avança exatamente um step e auto-limpa; default preserva o
  tick atual; widget como built-in e operável via hit-test sob pause.
