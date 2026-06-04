## Why

Painéis de debug screen-space (o Inspector `SceneTreeWidget`, o `NodeInspectorWidget`, o `DebugHud`) hoje **truncam** todo conteúdo que excede a altura da tela: o tail vira uma linha `… (+N more)` e os nós/props escondidos ficam **inalcançáveis**. Numa árvore de cena real isso inutiliza o Inspector justamente quando ele mais importa. A única saída correta é tornar o corpo dos painéis rolável — o que exige primitivas que a engine ainda não tem (clip no `Renderer`, delta de roda no `Input`).

## What Changes

- **Novo:** `Renderer.pushClip(rect)` / `popClip()` — pilha LIFO de recorte retangular, par natural do `pushTransform`/`popTransform` já existente, para recorte por GPU (scissor) do conteúdo que vaza do viewport.
- **Novo:** `Input.scrollDelta: Vec2` (delta de roda acumulado no tick, zerado por tick) e `Input.scrollConsumed: Boolean` (consumo per-tick, no-op por default — espelha `mouseClickConsumed`/`mouseDragConsumed`), para que rolar sobre um painel não vaze para gameplay (zoom de câmera etc.).
- **Novo:** corpo rolável no `ScreenDebugWidget` (base de todos os painéis screen-space): separação entre **extensão de conteúdo** (intrínseca, todas as linhas) e **viewport** (altura limitada exibida no dock); offset de scroll guardado como único estado e **clampado on-read**; grabber e barra **derivados** a cada frame de `(conteúdo, viewport, offset)` — nunca pixels guardados. Inclui scrollbar vertical com grabber proporcional arrastável e ingestão de roda roteada pelo painel sob o ponteiro.
- **Novo:** re-clamp automático do offset em resize de janela e troca de dock (cai de graça do modelo "tudo derivado").
- **Removido:** a lógica de truncamento `… (+N more)` do `SceneTreeWidget` e do `NodeInspectorWidget` — o scroll a substitui; o layout passa a conter todas as linhas.
- **Backends:** `:engine-skiko` e `:engine-lwjgl` implementam o clip (`canvas.clipRect` / NanoVG scissor) e a ingestão de roda (AWT `MouseWheelListener` / GLFW `glfwSetScrollCallback`).

Escopo deliberadamente fechado (sem overengineering): **scroll só vertical** (painéis crescem para baixo; largura auto-ajusta à linha mais longa), **sem** classe `Range`/`ScrollBar` reutilizável, **sem** scroll aninhado, **sem** scroll suave/inercial, **sem** auto-scroll até a seleção.

## Capabilities

### New Capabilities
- `debug-ui-scroll`: corpo rolável dos painéis de debug screen-space — separação conteúdo/viewport, offset clampado on-read, scrollbar vertical com grabber derivado e arrastável, ingestão de roda roteada pelo painel sob o ponteiro, e re-clamp em resize/redock. Substitui o truncamento por overflow.

### Modified Capabilities
- `engine-core`: a SPI `Renderer` ganha `pushClip`/`popClip` (pilha LIFO de recorte retangular sob o transform corrente); a SPI `Input` ganha `scrollDelta: Vec2` e `scrollConsumed: Boolean`.
- `skiko-runtime`: `SkikoRenderer` implementa `pushClip`/`popClip` via `canvas.save()/clipRect()/restore()`; `SkikoInput` ingere a roda AWT em `scrollDelta` e `SkikoHost` registra o `MouseWheelListener`.
- `lwjgl-runtime`: `LwjglRenderer` implementa `pushClip`/`popClip` via NanoVG scissor (emulando a pilha com `nvgSave`/`nvgIntersectScissor`/`nvgRestore`); `LwjglInput` ingere `glfwSetScrollCallback` em `scrollDelta` e `LwjglHost` registra o callback.

## Impact

- **APIs públicas (`:engine`):** `Renderer` (+2 métodos), `Input` (+1 val, +1 var com default). Aditivas — implementações que não rolam herdam defaults/no-op.
- **Código de engine:** `ScreenDebugWidget` (corpo rolável, scrollbar, roteamento de roda), `SceneTreeWidget` e `NodeInspectorWidget` (remoção do truncate, hit-test com offset), `SceneTree.hitTestUI` + `DebugRegistry` (roteamento e reset do scroll, reaproveitando `topPanelAt`).
- **Backends:** `SkikoRenderer`/`SkikoInput`/`SkikoHost`, `LwjglRenderer`/`LwjglInput`/`LwjglHost`.
- **Invariantes:** respeita #2 e #4 (SPIs em `:engine`, implementação nos backends; `:engine` não vaza tipos de backend). O clip toca os dois backends por #4.
- **Testes:** pilha LIFO de clip (incl. intercalada com transform), `scrollDelta` por-tick nos dois backends, clamp do offset, hit-test de linha com offset, `scrollConsumed` bloqueando gameplay, re-clamp em resize.
