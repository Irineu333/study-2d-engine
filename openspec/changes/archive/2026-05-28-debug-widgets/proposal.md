## Why

A infraestrutura de debug atual está arquiteturalmente errada e escala mal. Adicionar um novo gizmo (axes, normais, grid, console de log, inspetor de scene tree, etc.) hoje exige editar **cinco arquivos**: `DebugFlags`, `GameConfig`, `SkikoHost`, `LwjglHost` e `DebugOverlayLayer.init`. O `MomentumOverlay` é um singleton global com `FloatArray` mutável compartilhado entre instâncias de `SceneTree`, contaminando testes paralelos e re-starts. Os dois `GameHost`s carregam **12 linhas idênticas** de polling de toggle keys (F1/F2/F3) + recording de FPS — boilerplate que se multiplica a cada backend novo (futuro Vulkan, WebGPU, headless). O `DebugOverlayLayer.init { addChild(...) }` é fechado: a engine sempre instancia a classe base e o `open + @Serializable` que ela carrega é dead weight (nunca persiste, nunca subclassado). Cada flag de debug nova consome um keybind global (`F1/F2/F3/...`) sem espaço pra escalar.

A intenção é refatorar a infra de debug pra que (a) **adicionar widget de debug seja 1 arquivo, 0 toques em host**, (b) **estado de debug seja per-tree** sem singletons globais, (c) **hosts não tenham código de debug**, (d) **um único keybind** abra uma HUD com lista de checkboxes em vez de keybind-por-flag, e (e) **gizmos de mundo e de tela tenham seus próprios containers** alinhados ao invariante #6 (UI in-game em `CanvasLayer` ignora view transform; gizmo de mundo participa do world pass).

## What Changes

- Introduzir abstração `DebugWidget` — `Node` (ou `Node2D`) com `title: String`, `enabled: Boolean`, e `drawDebug(renderer)` chamado apenas quando habilitado.
- Duas subclasses base: `ScreenDebugWidget : Node` (vive na `CanvasLayer __debug`, desenha em pixels de tela) e `WorldDebugWidget : Node2D` (vive em container `Node2D` direto sob root, participa do world pass e recebe view transform automaticamente).
- Registry em `SceneTree`: `tree.debug.register(widget)`, `tree.debug.unregister(widget)`, `tree.debug.widgets: List<DebugWidget>` — método público pra jogos plugarem gizmos sem editar engine.
- Engine auto-registra três widgets default: `FpsWidget` (screen), `ColliderWidget` (world — desenha em mundo sem `pushTransform` manual, herda do passe), `MomentumWidget` (screen, **dona do seu ring buffer**).
- HUD de debug: `Panel` listando uma linha por widget registrado, cada linha um `Button` que toggla `enabled`. Visível só quando aberta; abre/fecha com **um único keybind**.
- `GameConfig`: remove `toggleFpsKey`, `toggleCollidersKey`, `toggleMomentumOverlayKey`; adiciona `debugHudKey: Key = Key.F1`.
- Hosts (`SkikoHost`, `LwjglHost`) deixam de ter código de debug. Sem `FpsCounter`, sem polling de F1/F2/F3, sem `MomentumOverlay.reset()`. Loop deles fica `input.beginTick()` → `loop.tick(dt)`.
- `MomentumOverlay` singleton **é eliminado**. `MomentumWidget` é dona do seu `FloatArray`. `GameLoop.tick` deixa de chamar `MomentumOverlay.recordSample(tree)`; o widget é um `Node` e ouve via `physicsProcess(dt)` herdado do scene graph.
- `FpsCounter` é dona do `FpsWidget` (instanciada como field). O widget chama `System.nanoTime()` direto; `DebugFlags.currentFps` desaparece junto com `DebugFlags`.
- Polling do `debugHudKey` vive num `Node` interno (`DebugToggleNode`) dentro do `__debug` layer, que lê `tree.input` em `process(dt)`.
- Reorganização de pacotes: tudo de debug consolidado em `com.neoutils.engine.debug.*`. `com.neoutils.engine.dx.*` permanece **só para logging** (`Log`, `LogConfig`, `LogLevel`, `LogSink`). `Debug` object (que só hospedava `log`) fold direto no `Log` companion.
- `:games:demos` ganha um widget custom (`AxesWidget` ou similar) demonstrando o padrão de extensão.

**BREAKING:**

- `GameConfig.toggleFpsKey/toggleCollidersKey/toggleMomentumOverlayKey` removidos. Quem setava esses keys migra pra `debugHudKey` (1 keybind só) ou aceita default `F1`. Sem deprecation shim — sem usuário externo.
- `SceneTree.debug` muda de tipo (`DebugFlags` → `DebugRegistry`). Acesso direto a `tree.debug.showFps` deixa de existir; jogos/scripts que queiram habilitar programaticamente fazem `tree.debug.fps.enabled = true` (alias conveniente para os 3 built-ins) ou `tree.debug.find<FpsWidget>()?.enabled = true`.
- `MomentumOverlay` object é deletado. Nenhum jogo shipped depende dele.
- `Debug` object é deletado; `Debug.log` migra pra `Log.config`.

## Capabilities

### Modified Capabilities

- `debug-overlay` — substitui o modelo "3 flags conhecidas em `DebugFlags` + 3 widgets fixos em `DebugOverlayLayer.init`" pelo modelo `DebugWidget` + registry + HUD. Hospedeira passa a ser um `DebugLayer` (rename de `DebugOverlayLayer`) com dois containers: screen-space `CanvasLayer` e world-space `Node2D`.
- `engine-core` — `GameConfig` perde 3 keys e ganha 1 (`debugHudKey`); `SceneTree.debug` muda de `DebugFlags` pra `DebugRegistry`; requisito "host polls toggle keys" é removido.
- `skiko-runtime` — `SkikoHost` deixa de instanciar `FpsCounter`, deixa de gravar `tree.debug.currentFps`, deixa de pollar 3 toggle keys, deixa de chamar `MomentumOverlay.reset()`. Loop fica `input.beginTick()` + `loop.tick(dt)`.
- `lwjgl-runtime` — `LwjglHost` idem.
- `dx-tooling` — escopo encolhe pra "só logging". `FpsCounter`, `DebugColors`, `Debug` object saem da capability `dx-tooling` e viram detalhe de implementação interno de `debug-overlay`.

### Added Capabilities

Nenhuma capability nova. Tudo cabe nas modificadas — HUD é parte natural do `debug-overlay` agora que ele engloba a UI de configuração.

## Impact

- Arquivos tocados em `:engine`:
  - **Novos**: `engine/debug/DebugWidget.kt`, `ScreenDebugWidget.kt`, `WorldDebugWidget.kt`, `DebugRegistry.kt`, `FpsWidget.kt`, `ColliderWidget.kt`, `MomentumWidget.kt`, `DebugHud.kt`, `DebugToggleNode.kt`, `DebugLayer.kt`, `WorldDebugContainer.kt`, `FpsCounter.kt` (movido de `dx/`), `DebugColors.kt` (movido de `dx/`).
  - **Removidos**: `engine/dx/MomentumOverlay.kt`, `engine/dx/Debug.kt`, `engine/dx/DebugColors.kt` (movido), `engine/dx/FpsCounter.kt` (movido), `engine/tree/DebugFlags.kt`, `engine/scene/DebugOverlayLayer.kt`.
  - **Modificados**: `engine/tree/SceneTree.kt` (substitui `debug: DebugFlags` por `debug: DebugRegistry`; `ensureDebugOverlay()` muda de `DebugOverlayLayer` pra `DebugLayer` com dois containers), `engine/loop/GameLoop.kt` (remove `MomentumOverlay.recordSample`), `engine/runtime/GameConfig.kt` (remove 3 keys, adiciona 1), `engine/dx/Log.kt` (absorve `Debug.log` como `Log.config`).
- Arquivos tocados em `:engine-skiko`: `SkikoHost.kt` (corta ~25 linhas de debug; loop simplifica).
- Arquivos tocados em `:engine-lwjgl`: `LwjglHost.kt` (idem).
- Arquivos tocados em `:games:demos`: adiciona widget custom (`AxesWidget` mostrando eixos do mundo) como exemplo do padrão. Cena de demonstração ou registrado no entrypoint.
- Outros jogos: **sem mudanças** se usavam só defaults de `GameConfig`.
- Testes: `DebugOverlayLayerTest.kt` é reescrito pra `DebugLayerTest.kt` cobrindo registry; novo `DebugWidgetTest.kt` cobre enable/disable; novo `DebugHudTest.kt` cobre que clicar a row alterna `enabled`; novo `DebugRegistryTest.kt` cobre register/unregister/find; testes existentes de "host frame contém zero drawText calls" continuam válidos e ganham asserts adicionais ("host source não menciona `tree.debug` nem `FpsCounter` nem `MomentumOverlay`").
- `CLAUDE.md` invariante #6 e seção "GameHost.render não desenha" atualizadas. Seção F1/F2/F3 do `README.md` atualiza pra "F1 abre HUD de debug".
- Sem efeito em scripting (Python/Lua) — `DebugRegistry` é Kotlin-side.
- Sem efeito em serialization — `DebugRegistry` é runtime puro como `DebugFlags` era.
