## Why

Hoje a engine não tem como construir HUDs nem menus de forma declarativa: jogos shipped (Pong, Snake, Tic-tac-toe) hackeiam usando `Label` em world-space dentro de Camera2D estática, e os overlays de debug (FPS, colliders, momentum) são desenhados diretamente pelo `GameHost`, vazando responsabilidade de UI para a SPI de host. Jogos planejados (Asteroids, Pool8, Billiards) precisam de score/lives/turn-indicator que não podem zoomar com a câmera, e o roadmap aponta `editor-visual` adiante — sem nodes de UI, não há o que arrastar e largar nesse editor futuro.

## What Changes

- Introduz `CanvasLayer : Node` — node que reseta a view transform da `Camera2D` para seus filhos, renderizando-os em screen-space. Tem `layer: Int = 0` para z-order entre múltiplos CanvasLayers (sort estável: por `layer` ascendente, tie-break por ordem da árvore).
- Introduz `Panel : Node2D` — retângulo screen-space com `size`, `color`, e `border` opcional.
- Introduz `Button : Node2D` — `size`, `text`, paleta `normalColor`/`hoverColor`/`pressedColor`/`disabledColor`, `disabled: Bool`, signal built-in `pressed` (emite no mouse-up dentro do botão se o mouse-down também ocorreu nele). Hover/press visual é pull-based em `_process` lendo `tree.input.pointerPosition`.
- Introduz fase `tree.hitTestUI(input)` entre `input.beginTick()` e `tree.process(dt)`: CanvasLayers hit-testam seus filhos top-down em ordem reversa de z; primeiro Button habilitado cujo rect contém o ponteiro consome o clique. `Input.wasMouseClicked()` retorna `false` quando consumido; método novo `wasMouseClickedRaw()` expõe o evento bruto para casos que precisam vê-lo.
- Introduz `tree.debug` (flags `showFps`, `showColliders`, `showMomentum`) e `DebugOverlayLayer` — CanvasLayer auto-inserido pela engine na raiz da árvore com os widgets de overlay correspondentes.
- **BREAKING**: `GameHost.render` deixa de desenhar overlays — toda saída visual passa por `SceneTree.render`. O host só faz polling de F1/F2/F3 setando flags em `tree.debug`. Backends Skiko e LWJGL têm seu pipeline de overlay removido.
- Adiciona cena `7` em `:games:demos` — menu central (Start/Settings/Quit) + HUD bottom-left (Score/Lives) em CanvasLayers separados com `layer` diferente; validada em `run` (Skiko) e `runLwjgl`.
- Migra UI dos jogos shipped que se beneficiam: Pong (placar), Snake (ScoreLabel + GameOverLabel), Tic-tac-toe (status Label), Hello World (Label centralizado vira exemplo canônico mínimo de CanvasLayer + Label).
- Stubs Python (`.pyi`) e Lua LuaCATS ganham `CanvasLayer`, `Panel`, `Button` e o signal `pressed`.
- Atualiza `CLAUDE.md` (seção de rendering + nota explícita "GameHost.render é proibido") e `ROADMAP.md` (Active + entradas Planned para melhorias futuras `ui-anchors`, `ui-layout`, `ui-focus`, `ui-theme`, `ui-input-events`, `ui-controls-base`).

## Capabilities

### New Capabilities

- `ui-foundation`: nodes `CanvasLayer`, `Panel`, `Button`; render passes (world via `Camera2D` + canvas layers ordenados por `layer`); hit-test top-down de UI com consumed flag em `Input`; serialização e wiring declarativo via `scene.json` (formato v2 sem mudanças); ergonomia de signal `pressed` via Python e Lua.
- `debug-overlay`: contrato `tree.debug` (`showFps`/`showColliders`/`showMomentum`), `DebugOverlayLayer` auto-inserido pela engine como CanvasLayer especial, e regra de que `GameHost` não desenha overlays — só seta flags via polling das teclas configuradas em `GameConfig` (`toggleFpsKey`, `toggleCollidersKey`, `toggleMomentumOverlayKey`).

### Modified Capabilities

- `engine-core`: ordem do tick ganha fase `hitTestUI` entre `input.beginTick()` e `tree.process(dt)`; `SceneTree.render` passa a fazer dois passes (world via `Camera2D` view transform, depois CanvasLayers em screen-space ordenados por `layer`); `Input.wasMouseClicked()` retorna `false` quando consumido (novo método `wasMouseClickedRaw()` expõe o bruto); regra "GameHost.render não desenha" entra como invariante da SPI.
- `skiko-runtime`: `SkikoHost` não desenha overlays — só faz polling de F1/F2/F3 e seta `tree.debug.*`. Pipeline de FPS/colliders/momentum removido do host.
- `lwjgl-runtime`: idem — `LwjglHost` deixa de desenhar overlays; mesmo contrato de polling/flags.
- `demos-sample`: ganha cena `7` (menu + HUD) acessível via tecla `7` em ambos os entrypoints (`run` e `runLwjgl`); cena valida CanvasLayer, Panel, Button, hit-test consumed, signal `pressed` e z-order entre layers.
- `pong-sample`: placar migra para `CanvasLayer` em screen-space.
- `snake-sample`: `ScoreLabel` e `GameOverLabel` migram para `CanvasLayer`.
- `tictactoe-sample`: status Label migra para `CanvasLayer`.
- `hello-world-sample`: Label centralizado passa a viver em `CanvasLayer` — vira exemplo canônico mínimo do par CanvasLayer + Label.
- `python-scripting`: stubs `.pyi` expõem `CanvasLayer`, `Panel`, `Button`, `Signal pressed`; sem mudança no runtime.
- `lua-scripting`: stubs LuaCATS expõem o mesmo conjunto via `nengine.*`; sem mudança no runtime.

## Impact

- **Código `:engine`**: novos arquivos `CanvasLayer.kt`, `Panel.kt`, `Button.kt`; alterações em `SceneTree.kt` (render passes + `hitTestUI`), `Input.kt` (`mouseClickConsumed`, `wasMouseClickedRaw`), `NodeRegistry.kt` (registra novos tipos), `SceneLoader` (serialização dos novos campos via `@Inspect`), e introdução de `DebugOverlayLayer` + `tree.debug` flags.
- **Backends**: `:engine-skiko` e `:engine-lwjgl` perdem o código de desenho de overlays no host; ganham apenas o polling de teclas.
- **`scene.json`**: formato v2 inalterado; novos `type`s (`engine.CanvasLayer`, `engine.Panel`, `engine.Button`) ficam disponíveis.
- **Stubs**: `:engine-bundle-python` (`stubs/engine/*.pyi`) e `:engine-bundle-lua` (`stubs/engine/*.lua`) ganham os tipos novos; nenhuma dependência runtime adicional.
- **Jogos shipped**: Pong, Snake, Tic-tac-toe, Hello World têm `scene.json` ajustados; nenhum script gameplay precisa mudar significativamente.
- **Documentação**: `CLAUDE.md` ganha seção sobre CanvasLayer + `tree.debug` + invariante "GameHost.render é noop"; `ROADMAP.md` registra esta change em Active e adiciona entradas Planned para as 6 melhorias futuras.
- **Risco**: divergência sutil entre Skiko e LWJGL no novo render pass ordenado (z-order de CanvasLayers) — coberto pela validação dupla (Skiko + LWJGL) na cena 7.
