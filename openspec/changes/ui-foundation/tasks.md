## 1. Engine — CanvasLayer + render passes

- [ ] 1.1 Adicionar `CanvasLayer : Node` em `engine/src/main/kotlin/com/neoutils/engine/scene/CanvasLayer.kt` com `@Inspect var layer: Int = 0`, `@Serializable`, e public no-args constructor.
- [ ] 1.2 Registrar `CanvasLayer` em `NodeRegistry` sob `"engine.CanvasLayer"`.
- [ ] 1.3 Refatorar `SceneTree.render(renderer)` para executar dois passes: (a) world pass que pula CanvasLayer subtrees inteiras, (b) UI pass que coleta CanvasLayers em DFS pre-order, sort por `(layer asc, dfs-order asc)`, e walka cada subtree a partir de identity transform.
- [ ] 1.4 Garantir que `Renderer.pushTransform`/`popTransform` continuam sendo emitidos para cada `Node2D` descendente de `CanvasLayer`.
- [ ] 1.5 Testes unitários em `:engine` cobrindo: (a) world pass pula CanvasLayer; (b) UI pass desenha CanvasLayer em screen-space (sem view transform); (c) ordering `(layer, dfs-order)` é estável; (d) `try/finally` pop em exceção dentro de `onDraw`.

## 2. Engine — Input consumed flag + UI hit-test phase

- [ ] 2.1 Adicionar `var mouseClickConsumed: Boolean` e método `wasMouseClickedRaw(button: MouseButton): Boolean` à interface `Input`.
- [ ] 2.2 Atualizar `wasMouseClicked(button)` para retornar `false` quando `mouseClickConsumed = true` (left button MVP).
- [ ] 2.3 Resetar `mouseClickConsumed = false` em `Input.beginTick()` (ou método equivalente).
- [ ] 2.4 Adicionar método `SceneTree.hitTestUI(input: Input)` que: se `wasMouseClickedRaw(Left)`, coleta CanvasLayers sort desc por `(layer, dfs-order)`, walka cada um em reverse DFS, e para o primeiro `Button` habilitado cujo rect screen-space contém `input.pointerPosition` seta `mouseClickConsumed = true`.
- [ ] 2.5 Atualizar `GameLoop.tick(...)` para invocar `tree.hitTestUI(input)` entre `input.beginTick()` (que continua sendo responsabilidade do host) e o primeiro `tree.physicsProcess(...)`.
- [ ] 2.6 Testes unitários em `:engine` cobrindo: (a) click consumido inverte retorno de `wasMouseClicked`; (b) `wasMouseClickedRaw` sempre vê o bruto; (c) `mouseClickConsumed` reseta a cada tick; (d) top-most CanvasLayer ganha em overlap.

## 3. Engine — Panel and Button nodes

- [ ] 3.1 Criar `Panel : Node2D` em `engine/src/main/kotlin/com/neoutils/engine/scene/Panel.kt` com `@Inspect var size: Vec2`, `color: Color`, `border: Border?`; `Border` é `data class Border(val color: Color, val width: Float)`; registrar em `NodeRegistry` sob `"engine.Panel"`.
- [ ] 3.2 Implementar `Panel.onDraw` desenhando `drawRect(rect, color, filled = true)` e, se `border != null`, `drawRect(rect, border.color, filled = false)` em seguida.
- [ ] 3.3 Criar `Button : Node2D` em `engine/src/main/kotlin/com/neoutils/engine/scene/Button.kt` com `@Inspect var size: Vec2`, `text: String`, `normalColor`/`hoverColor`/`pressedColor`/`disabledColor: Color`, `disabled: Boolean`; `@Transient` internal state (`hovered`, `armed`, `pressedFlag`); built-in `val pressed = Signal<Unit>()`; registrar em `NodeRegistry` sob `"engine.Button"`.
- [ ] 3.4 Implementar `Button._process(dt)`: ler `input.pointerPosition`, atualizar `hovered` via `Rect(screenPosition, size).contains(...)`; gerenciar `armed` em mouse-down dentro do rect e mouse-up dentro (emite `pressed`) ou fora (cancela). Respeitar `disabled`.
- [ ] 3.5 Implementar `Button.onDraw`: desenhar Panel-like com cor por estado (`disabledColor` se `disabled`, `pressedColor` se `armed`, `hoverColor` se `hovered`, senão `normalColor`); desenhar `text` centralizado via `measureText`.
- [ ] 3.6 Wirear `Button` na fase `SceneTree.hitTestUI` para que o consume seja consistente com o estado interno (armed).
- [ ] 3.7 Testes unitários cobrindo: (a) click cycle emite `pressed` uma vez; (b) drag-out cancela; (c) `disabled` ignora; (d) hover/press atualiza visual; (e) `pressed` reseta após emit.

## 4. Engine — tree.debug + DebugOverlayLayer + auto-insert

- [ ] 4.1 Criar `DebugFlags` em `engine/src/main/kotlin/com/neoutils/engine/tree/DebugFlags.kt` com `var showFps`, `showColliders`, `showMomentum`, defaults `false`. Não-`Serializable`, não-`Node`.
- [ ] 4.2 Adicionar `val debug: DebugFlags = DebugFlags()` a `SceneTree`.
- [ ] 4.3 Criar `DebugOverlayLayer : CanvasLayer` em `engine/src/main/kotlin/com/neoutils/engine/scene/DebugOverlayLayer.kt` com `layer = Int.MAX_VALUE - 1`, nome estável `"__debug"`.
- [ ] 4.4 Implementar nodes filhos `FpsLabel` (lê FPS de um canal compartilhado com o host), `ColliderOverlay` (walk world em `_process` desenhando AABBs de `CollisionObject2D`), `MomentumOverlay` (consulta `PhysicsSystem` para `Σp`, `ΣL`, `ΣKE`).
- [ ] 4.5 Fazer `SceneTree` auto-inserir `DebugOverlayLayer` como último filho de `root` na construção/attach (idempotente — se `__debug` já existe, skip).
- [ ] 4.6 Definir canal por onde o host alimenta FPS para `FpsLabel` (ex.: `tree.debug.currentFps: Float`).
- [ ] 4.7 Testes unitários cobrindo: (a) `tree.root.findChild("__debug")` retorna `DebugOverlayLayer`; (b) toggle `showFps=true` resulta em draws via `FpsLabel`; (c) `ColliderOverlay` ignora `Button`/`Panel` (não são `CollisionObject2D`).

## 5. Engine — GameConfig + invariantes de host

- [ ] 5.1 Adicionar `toggleMomentumOverlayKey: Key = Key.F3` em `GameConfig`.
- [ ] 5.2 Atualizar a Requirement "Toggle keys" da spec engine-core para refletir que flips vão para `tree.debug.*` (já refletido nos deltas).
- [ ] 5.3 Adicionar teste/checagem de invariante: grep de `:engine-skiko` e `:engine-lwjgl` por `renderer.draw*` fora de `tree.render` (ou via inspeção arquitetural) — falha se encontrar.

## 6. Skiko backend — esvaziar host render

- [ ] 6.1 Remover de `SkikoHost` qualquer chamada a `renderer.drawText`/`drawRect`/`drawLine`/`drawCircle`/`drawPolygon` no callback `onRender` fora de `loop.tick`.
- [ ] 6.2 Remover helper `renderDebugOverlay(renderer, tree)` (e qualquer função análoga) de `:engine-skiko`.
- [ ] 6.3 No callback `onRender`: após `input.beginTick()`, poll de `config.toggleFpsKey`/`config.toggleCollidersKey`/`config.toggleMomentumOverlayKey` flippando `tree.debug.*` correspondentes; alimentar `tree.debug.currentFps` via `FpsCounter`; depois apenas `loop.tick(dtNanos)` + `skiaLayer.needRedraw()`.
- [ ] 6.4 Atualizar testes/instrumentação do `:engine-skiko` para verificar que `onRender` não chama `renderer.draw*` direto.

## 7. LWJGL backend — esvaziar host render

- [ ] 7.1 Remover de `LwjglHost` qualquer chamada a `renderer.drawText`/`drawRect`/`drawLine`/`drawCircle`/`drawPolygon` no main loop fora de `loop.tick`.
- [ ] 7.2 Remover helper `renderDebugOverlay(renderer, tree)` (e qualquer função análoga) de `:engine-lwjgl`.
- [ ] 7.3 No main loop: após `glfwPollEvents` + `input.beginTick()`, poll dos três toggle keys flippando `tree.debug.*`; alimentar `tree.debug.currentFps`; depois apenas `renderer.bind` → `renderer.clear` → `loop.tick(dtNanos)` → `renderer.unbind` → `glfwSwapBuffers`.
- [ ] 7.4 Garantir que `MomentumOverlay.reset()` continua sendo chamado quando `tree.debug.showMomentum` flipa de `false` para `true` (responsabilidade pode migrar do host para o `DebugOverlayLayer` observando a flag).

## 8. Demos — cena 7 (UI playground)

- [ ] 8.1 Adicionar `UiPlaygroundScene` (Kotlin code-only) em `games/demos/src/main/kotlin/com/neoutils/games/demos/UiPlaygroundScene.kt` com a estrutura: dois `CanvasLayer`s (HUD `layer=0`, Menu `layer=10`); HUD com Panel + 2 Labels (`Score: 0`, `Lives: 3`); Menu com 3 Buttons (`Start`, `Settings` (disabled), `Quit`) centralizados.
- [ ] 8.2 Anexar scripts Python aos 3 buttons (em `games/demos/src/main/resources/demos/scripts/`) que conectam a `pressed` e imprimem strings reconhecíveis.
- [ ] 8.3 Wirear `UiPlaygroundScene` no `DemoSwitcherRoot` sob a tecla `7`.
- [ ] 8.4 Validação manual (Skiko): `./gradlew :games:demos:run` → `7` → clica botões, verifica hover/press/disabled, resize confirma layout.
- [ ] 8.5 Validação manual (LWJGL): `./gradlew :games:demos:runLwjgl` → mesmas verificações; comparar com Skiko para divergência semântica.

## 9. Migração — hello-world

- [ ] 9.1 Atualizar `:games:hello-world/Main.kt` para montar `CanvasLayer().apply { addChild(CenteredLabel().apply { ... }) }` como root.
- [ ] 9.2 Atualizar `CenteredLabel.onDraw` conforme delta (continua usando `tree?.size` e `measureText`; agora roda em screen-space via UI pass).
- [ ] 9.3 Validação manual: `./gradlew :games:hello-world:run` → texto centralizado, resize mantém centralizado.

## 10. Migração — pong

- [ ] 10.1 Editar `games/pong/src/main/resources/pong/scene.json`: extrair os 2 `Score` labels do root world e inseri-los dentro de um novo `CanvasLayer` chamado `Hud`; posicionar em screen pixels (top-left e top-right).
- [ ] 10.2 Atualizar `pong_scene.py` (se necessário) para resolver os scores via novo path através do `Hud`.
- [ ] 10.3 Validação manual: `./gradlew :games:pong:run` → placar visível em screen-space; resize mantém placar no canto; gameplay (paddles, ball, goals) inalterado.

## 11. Migração — snake

- [ ] 11.1 Editar `games/snake/src/main/resources/snake/scene.json`: criar `CanvasLayer` chamado `Hud`; mover `ScoreLabel` e `GameOverLabel` para serem filhos do `Hud`; posicionar `ScoreLabel` em screen pixels (top-left) e `GameOverLabel` em centro de screen.
- [ ] 11.2 Atualizar `scripts/score.py` e `scripts/gameover.py` para resolver `Snake` corretamente do novo path (e.g. `NodeRef("../../Snake")` ou `tree.root.findChild("Snake")`).
- [ ] 11.3 Validação manual: `./gradlew :games:snake:run` → score atualiza em screen-space; game over aparece centralizado; restart funciona.

## 12. Migração — tictactoe

- [ ] 12.1 Editar `games/tictactoe/src/main/resources/tictactoe/scene.json`: criar `CanvasLayer` chamado `Hud`; mover `status` Label para ser filho do `Hud`; posicionar em screen pixels (top center).
- [ ] 12.2 Atualizar `scripts/board.lua` para resolver `status` Label via novo path (e.g. `findChild("Hud"):findChild("status")`).
- [ ] 12.3 Validação manual: `./gradlew :games:tictactoe:run` → status visível em screen-space; cliques na grade ainda funcionam (sem consumo indevido pela UI); ghost hover e linha vencedora inalterados.

## 13. Stubs Python e Lua

- [ ] 13.1 Atualizar `engine-bundle-python/src/main/resources/stubs/engine/__init__.pyi` (ou stubs equivalentes) declarando `CanvasLayer`, `Panel`, `Button` com seus `@Inspect` fields públicos e `Button.pressed: Signal` por instância.
- [ ] 13.2 Atualizar `engine-bundle-lua/src/main/resources/stubs/engine/nengine.lua` para listar `CanvasLayer`, `Panel`, `Button` como fields da tabela `nengine`.
- [ ] 13.3 Adicionar (ou estender) stub LuaCATS em `engine-bundle-lua/src/main/resources/stubs/engine/ui.lua` cobrindo `---@class CanvasLayer`, `---@class Panel`, `---@class Button` com seus fields, incluindo `---@field pressed Signal` em Button.
- [ ] 13.4 Verificar manualmente em Pyright/sumneko-lua que autocomplete pega os novos tipos.

## 14. Bindings Python e Lua no host

- [ ] 14.1 Registrar `CanvasLayer`, `Panel`, `Button` como bindings no Polyglot Context de `PythonScriptHost`.
- [ ] 14.2 Registrar `nengine.CanvasLayer`, `nengine.Panel`, `nengine.Button` em `LuaScriptHost`.
- [ ] 14.3 Verificar que `# extends Button` (Python) e `extends = "Button"` (Lua) resolvem corretamente contra `NodeRegistry`.

## 15. Documentação

- [ ] 15.1 Atualizar `CLAUDE.md` com seção sobre CanvasLayer no contrato de rendering, `tree.debug`, e o invariante explícito "GameHost.render é noop — toda saída visual passa por SceneTree.render".
- [ ] 15.2 Atualizar `CLAUDE.md` listando os novos Nodes (CanvasLayer, Panel, Button) entre os Nodes shipped.
- [ ] 15.3 Atualizar `ROADMAP.md`: adicionar `ui-foundation` em Active; adicionar entradas em Planned para `ui-anchors`, `ui-layout`, `ui-focus`, `ui-theme`, `ui-input-events`, `ui-controls-base` (Control base abstrata).
- [ ] 15.4 Atualizar `README.md` (se contém menção a `:games:hello-world` ou ao DebugOverlayLayer) refletindo a nova arquitetura.

## 16. Validação cruzada e fechamento

- [ ] 16.1 Rodar todos os jogos shipped em Skiko: `:games:hello-world:run`, `:games:pong:run`, `:games:snake:run`, `:games:tictactoe:run`, `:games:demos:run` (todas as 7 cenas).
- [ ] 16.2 Rodar `:games:demos:runLwjgl` validando cenas 1–7 com paridade semântica.
- [ ] 16.3 Rodar `openspec validate ui-foundation` e confirmar `Change 'ui-foundation' is valid`.
- [ ] 16.4 Rodar `openspec verify ui-foundation` (via `/opsx:verify`) e iterar nos gaps encontrados.
