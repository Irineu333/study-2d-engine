# lwjgl-runtime Specification

## Purpose

Segundo backend ativo da engine — implementações de `Renderer`, `Input` e `GameHost` sobre NanoVG (`org.lwjgl.nanovg`) + GLFW (`org.lwjgl.glfw`) + OpenGL 3.3 core (`org.lwjgl.opengl`). Serve de sentinela do invariante "`Renderer`/`Input`/`GameHost` são SPIs com múltiplas implementações" através do entrypoint LWJGL de `:games:demos`. Único módulo (além de `:engine-skiko`) autorizado a depender diretamente de `org.lwjgl.*`.

## Requirements

### Requirement: LwjglRenderer implements the Renderer SPI over NanoVG + OpenGL 3.3 core

O módulo `:engine-lwjgl` SHALL prover uma classe `LwjglRenderer` que implementa a interface `Renderer` definida em `:engine` usando NanoVG (via `org.lwjgl.nanovg.NanoVG` + `org.lwjgl.nanovg.NanoVGGL3`) sobre um contexto OpenGL 3.3 core. `LwjglRenderer` MUST manter um handle `nvgContext: Long` criado por `NanoVGGL3.nvgCreate(NVG_ANTIALIAS or NVG_STENCIL_STROKES)` durante `init()` e destruído por `NanoVGGL3.nvgDelete(nvgContext)` durante `shutdown()`. O renderer SHALL expor um par `bind(windowWidth: Int, windowHeight: Int, pixelRatio: Float)` / `unbind()` chamado pelo `LwjglHost` em torno de cada frame: `bind` MUST chamar `nvgBeginFrame(nvgContext, windowWidth, windowHeight, pixelRatio)` e resetar `transformDepth = 0`; `unbind` MUST chamar `nvgEndFrame(nvgContext)` e lançar `IllegalStateException` se `transformDepth != 0` ao final do frame.

A tradução para NanoVG MUST seguir este contrato:

- `clear(color)`: chamar `glClearColor` com componentes `(r, g, b, a)` de `color` e `glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)`. MUST ser chamado ANTES de `nvgBeginFrame` no frame loop (ou seja, o host invoca `glClear` separadamente; `Renderer.clear` é alias).
- `drawRect(rect, color, filled)`: `nvgBeginPath` + `nvgRect(x, y, w, h)` + (`nvgFillColor` + `nvgFill`) se `filled`, senão (`nvgStrokeColor` + `nvgStrokeWidth(1f)` + `nvgStroke`).
- `drawCircle(center, radius, color, filled, thickness)`: `nvgBeginPath` + `nvgCircle(cx, cy, radius)` + filled/stroke análogo (stroke usa `thickness`).
- `drawLine(from, to, thickness, color)`: `nvgBeginPath` + `nvgMoveTo(from.x, from.y)` + `nvgLineTo(to.x, to.y)` + `nvgStrokeWidth(thickness)` + `nvgStrokeColor(color)` + `nvgStroke`.
- `drawText(text, position, size, color)`: `nvgFontFaceId(nvgContext, defaultFontId)` + `nvgFontSize(size)` + `nvgTextAlign(NVG_ALIGN_LEFT or NVG_ALIGN_TOP)` + `nvgFillColor(color)` + `nvgText(nvgContext, position.x, position.y, text)`. Top-anchored, igual ao contrato de `SkikoRenderer.drawText`.
- `measureText(text, size)`: `nvgFontFaceId` + `nvgFontSize` + `nvgTextBounds(nvgContext, 0f, 0f, text, bounds: FloatArray(4))`, retornando `Vec2(bounds[2] - bounds[0], fontHeight)` onde `fontHeight` vem de `nvgTextMetrics`.
- `drawPolygon(points, color)`: early-return se `points.size < 3`; `nvgBeginPath` + `nvgMoveTo(points[0])` + `nvgLineTo(points[i])` para `i = 1..n-1` + `nvgClosePath` + `nvgFillColor` + `nvgFill`. NanoVG faz tessellation interna; o contrato do SPI sobre concavidade/self-intersection é honrado tal como Skiko.
- `pushTransform(translation, rotation, scale)`: `nvgSave` + `nvgTranslate(translation.x, translation.y)` + `nvgRotate(rotation)` + `nvgScale(scale.x, scale.y)`; incrementar `transformDepth`.
- `popTransform()`: lançar `IllegalStateException` se `transformDepth == 0`; senão `nvgRestore` + decrementar.

#### Scenario: LwjglRenderer is assignable to Renderer

- **WHEN** code instantiates `LwjglRenderer()` after a GL context is current
- **THEN** the result is assignable to `com.neoutils.engine.render.Renderer`

#### Scenario: pushTransform/popTransform check stack balance per frame

- **WHEN** `LwjglRenderer.bind(...)` is called and then 3 `pushTransform` calls happen without any `popTransform`
- **THEN** the subsequent `LwjglRenderer.unbind()` throws `IllegalStateException` naming the unmatched push count

#### Scenario: drawText is top-anchored

- **WHEN** code calls `drawText("hi", Vec2(10f, 20f), size = 16f, color = Color.WHITE)`
- **THEN** the rendered glyph's visual top edge aligns to `y = 20f` (within font metric tolerance)
- **AND** `measureText("hi", size = 16f).y` approximately equals the rendered glyph height

#### Scenario: nvgEndFrame is called even after a draw exception

- **WHEN** the host code wraps `loop.tick(...)` in `try { ... } finally { renderer.unbind() }` and one of the draws throws
- **THEN** `nvgEndFrame(nvgContext)` still runs before propagation

### Requirement: LwjglInput implements the Input SPI over GLFW callbacks

O módulo `:engine-lwjgl` SHALL prover uma classe `LwjglInput` que implementa a interface `Input` definida em `:engine`, ingerindo eventos por callbacks GLFW. A classe MUST expor três métodos internos (`onGlfwKey(glfwKey: Int, action: Int)`, `onGlfwMouseButton(glfwButton: Int, action: Int)`, `onGlfwCursorPos(x: Float, y: Float)`) que o `LwjglHost` conecta via `glfwSetKeyCallback`/`glfwSetMouseButtonCallback`/`glfwSetCursorPosCallback`, e um método `beginTick()` que o host chama no início de cada tick para limpar os sets `wasPressed`/`wasClicked`.

`onGlfwKey` MUST: traduzir `glfwKey` para o enum `Key` via tabela própria (cobrindo no mínimo WASD, setas, F1/F2/F3, espaço, enter, escape, dígitos 0–9, letras A–Z disponíveis no enum); ignorar (return) keys não-mapeadas; e tratar `action`: `GLFW_PRESS` adiciona ao set `down` e ao set `pressedThisTick` (mas só uma vez por transição), `GLFW_RELEASE` remove do set `down`, `GLFW_REPEAT` é ignorado.

`onGlfwMouseButton` MUST: traduzir `GLFW_MOUSE_BUTTON_LEFT/RIGHT/MIDDLE` para `MouseButton.Left/Right/Middle`; ignorar outros buttons; tratar `action` análogo ao key.

`onGlfwCursorPos` MUST: atualizar atomicamente `pointer = Vec2(x, y)`.

`pointerPosition` MUST retornar o último cursor reportado em pixels-da-janela (consistente com o input do `:engine-skiko`).

#### Scenario: LwjglInput is assignable to Input

- **WHEN** code instantiates `LwjglInput()`
- **THEN** the result is assignable to `com.neoutils.engine.input.Input`

#### Scenario: A key press registers in both isKeyDown and wasKeyPressed during the tick

- **GIVEN** a `LwjglInput` instance and `beginTick()` was called at the start of the tick
- **WHEN** GLFW dispatches `GLFW_PRESS` for `GLFW_KEY_W` mapped to `Key.W`
- **THEN** `isKeyDown(Key.W)` returns `true`
- **AND** `wasKeyPressed(Key.W)` returns `true`

#### Scenario: A key release clears isKeyDown but does not affect wasKeyPressed of the same tick

- **GIVEN** `Key.W` was pressed during this tick
- **WHEN** GLFW dispatches `GLFW_RELEASE` for `GLFW_KEY_W` during the same tick
- **THEN** `isKeyDown(Key.W)` returns `false`
- **AND** `wasKeyPressed(Key.W)` still returns `true` until the next `beginTick()`

#### Scenario: beginTick clears the press/click sets

- **GIVEN** `wasKeyPressed(Key.W)` returned `true` in a tick
- **WHEN** `beginTick()` is called at the start of the next tick (no new key events)
- **THEN** `wasKeyPressed(Key.W)` returns `false`

#### Scenario: pointerPosition reports the last cursor position in window pixels

- **WHEN** GLFW dispatches a cursor move to `(150.0, 220.0)`
- **THEN** `pointerPosition` returns `Vec2(150f, 220f)`

#### Scenario: Unmapped keys do not crash

- **WHEN** GLFW dispatches `GLFW_PRESS` for a key code that the engine `Key` enum does not include (e.g. `GLFW_KEY_HOME`)
- **THEN** the callback returns silently
- **AND** no entry is added to the down/pressed sets

### Requirement: LwjglHost implements the GameHost SPI driving a main-thread GLFW render loop

O módulo `:engine-lwjgl` SHALL prover uma classe `LwjglHost` que implementa a interface `GameHost` definida em `:engine`. Seu método `run(tree: SceneTree, config: GameConfig)` MUST seguir o seguinte fluxo de ciclo de vida em ordem estrita:

1. Registrar `GLFWErrorCallback.createPrint(System.err)` ANTES de qualquer `glfwInit()`.
2. Chamar `glfwInit()` e lançar exceção descritiva se retornar `false`.
3. Setar window hints: `GLFW_CONTEXT_VERSION_MAJOR = 3`, `GLFW_CONTEXT_VERSION_MINOR = 3`, `GLFW_OPENGL_PROFILE = GLFW_OPENGL_CORE_PROFILE`, `GLFW_OPENGL_FORWARD_COMPAT = GLFW_TRUE` (exigência de macOS), `GLFW_VISIBLE = GLFW_FALSE` (até `glfwShowWindow`).
4. Criar janela via `glfwCreateWindow(config.width, config.height, config.title, NULL, NULL)`; falhar com exceção descritiva se retornar `NULL`.
5. `glfwMakeContextCurrent(window)` + `org.lwjgl.opengl.GL.createCapabilities()` + `glfwSwapInterval(1)` (vsync).
6. Instanciar `LwjglInput` e conectar três callbacks GLFW (`glfwSetKeyCallback`/`glfwSetMouseButtonCallback`/`glfwSetCursorPosCallback`) que delegam para `input.onGlfwKey/onGlfwMouseButton/onGlfwCursorPos`.
7. Instanciar `LwjglRenderer`, chamar `renderer.init()` (que cria o NanoVG context, registra a fonte default).
8. Instanciar `PhysicsSystem`, `GameLoop(tree, renderer, input, physics, physicsHz = config.physicsHz)`. **Não instanciar `FpsCounter`** — `FpsWidget` é dona do seu próprio counter.
9. Setar `tree.debugHudKey = config.debugHudKey` uma vez antes do primeiro frame.
10. `glfwShowWindow(window)`.
11. Loop principal `while (!glfwWindowShouldClose(window))`: chamar `glfwPollEvents()`, `input.beginTick()`, ler `glfwGetWindowSize`/`glfwGetFramebufferSize` para calcular `pixelRatio`, chamar `tree.resize(winW, winH)`, chamar `glViewport(0, 0, fbW, fbH)`, chamar `renderer.bind(winW, winH, pixelRatio)`, dentro de `try { ... } finally { renderer.unbind() }` chamar `renderer.clear` + `loop.tick(dtNanos)` — e nada mais. Depois do `try/finally`, `glfwSwapBuffers(window)`.

    O host **NÃO** deve: instanciar `FpsCounter`, escrever em `tree.debug.*` por frame (exceto o set único em passo 9), chamar qualquer helper de debug overlay, ou pollar input para toggle de visualização de debug. Toda saída visual de debug (FPS, colliders, momentum, HUD) sai do `tree.render(renderer)` via `DebugLayer` auto-inserido pela engine.
12. Após o loop sair: `tree.stop()`, `renderer.shutdown()`, `Callbacks.glfwFreeCallbacks(window)`, `glfwDestroyWindow(window)`, e em `finally` externo: `glfwTerminate()` + `GLFWErrorCallback.set(null)?.free()`.

`run` MUST ser blocking — retorna somente quando o usuário fecha a janela ou código chama `glfwSetWindowShouldClose(window, true)`. `LwjglHost.run` MUST ser chamado a partir do main thread do processo; em macOS, o processo precisa ter sido iniciado com `-XstartOnFirstThread` (responsabilidade do `build.gradle.kts` que dispara a task `runLwjgl`).

#### Scenario: LwjglHost is assignable to GameHost

- **WHEN** code instantiates `LwjglHost()`
- **THEN** the result is assignable to `com.neoutils.engine.runtime.GameHost`

#### Scenario: run blocks until the window closes

- **WHEN** code calls `host.run(tree, config)` on the main thread (with `-XstartOnFirstThread` on macOS) and the window opens
- **THEN** the call does not return while the window remains open
- **AND** the call returns after the user closes the window

#### Scenario: debugHudKey toggles the HUD via the host

- **GIVEN** `tree.debug.hud.enabled == false`
- **WHEN** the user presses the key configured as `config.debugHudKey` (default `Key.F1`)
- **THEN** by the next frame `tree.debug.hud.enabled == true`
- **AND** the HUD `Panel` is drawn via `tree.render(renderer)` (no host-side draw)
- **AND** the toggle is driven by the engine's internal `DebugToggleNode`, not by code in `LwjglHost`

#### Scenario: LwjglHost does not draw debug overlays directly

- **WHEN** the source of `LwjglHost.kt` (and any helper it transitively calls during its render loop body) is grep'd for `renderer.drawText`, `renderer.drawRect`, `renderer.drawLine`, `renderer.drawCircle`, `renderer.drawPolygon`, or any private `renderDebugOverlay(...)` helper
- **THEN** the only draw calls are issued from inside `loop.tick(...)` → `tree.render(renderer)` → walks of the scene graph
- **AND** the host's main loop body itself contains zero `renderer.draw*` calls before or after `loop.tick`

#### Scenario: LwjglHost source has no debug references beyond debugHudKey setup

- **WHEN** the source of `LwjglHost.kt` is grep'd for `FpsCounter`, `MomentumOverlay`, `tree.debug.show`, `tree.debug.current`, `toggleFpsKey`, `toggleCollidersKey`, or `toggleMomentumOverlayKey`
- **THEN** zero matches SHALL be returned
- **AND** the only `tree.debug` reference in the file SHALL be the one-time `tree.debugHudKey = config.debugHudKey` assignment during startup

#### Scenario: Window close terminates the loop and disposes resources

- **WHEN** the user closes the window
- **THEN** the render loop exits
- **AND** `tree.stop()` is invoked
- **AND** `renderer.shutdown()` is invoked
- **AND** `glfwDestroyWindow` and `glfwTerminate` are called before `run` returns

#### Scenario: Viewport uses framebuffer pixels, NanoVG uses logical pixels

- **GIVEN** a HiDPI display where `glfwGetFramebufferSize` returns `(1600, 1200)` and `glfwGetWindowSize` returns `(800, 600)`
- **WHEN** the host issues `glViewport(0, 0, fbW, fbH)` and `renderer.bind(winW, winH, pixelRatio = fbW/winW)`
- **THEN** subsequent draws fill the entire framebuffer (no letterboxing)
- **AND** `Renderer` coordinates remain in logical pixels — drawing a rect at `(0, 0)` size `(800, 600)` covers the whole window

### Requirement: lwjgl-runtime is the only non-Skiko backend module allowed to depend on a graphics runtime

After this change applies, the project SHALL have exactly two render-backend modules: `:engine-skiko` (Skia/Skiko, default) and `:engine-lwjgl` (NanoVG/GLFW/OpenGL via LWJGL, second backend). No other module in the project SHALL declare a direct dependency on `org.lwjgl.*` artifacts; LWJGL types MUST stay encapsulated inside `:engine-lwjgl`. The `:engine` module MUST NOT depend on `org.lwjgl.*` (covered by the `engine-core` invariant "Engine module has zero UI framework dependency"). Games (`:games:*`) MAY depend on `:engine-lwjgl` to gain access to `LwjglHost`, but MUST NOT depend on `org.lwjgl.*` directly.

#### Scenario: :engine-lwjgl is the only module depending on org.lwjgl.*

- **WHEN** `./gradlew :engine:dependencies`, `./gradlew :engine-skiko:dependencies`, and `./gradlew :games:demos:dependencies` are inspected
- **THEN** only `:engine-lwjgl` lists `org.lwjgl:lwjgl`, `org.lwjgl:lwjgl-glfw`, `org.lwjgl:lwjgl-opengl`, `org.lwjgl:lwjgl-nanovg` as direct dependencies
- **AND** `:games:demos` depends on `:engine-lwjgl` (which transitively brings LWJGL into its classpath) but does not list any `org.lwjgl.*` artifact in its own `build.gradle.kts`

#### Scenario: :engine source tree has no org.lwjgl.* imports

- **WHEN** the `:engine/src/main` source tree is grepped for imports starting with `org.lwjgl.`
- **THEN** zero matches are found

#### Scenario: Both backend modules implement the SPIs

- **WHEN** the project is built
- **THEN** `:engine-skiko` exports `SkikoHost`, `SkikoRenderer`, `SkikoInput` implementing `GameHost`, `Renderer`, `Input`
- **AND** `:engine-lwjgl` exports `LwjglHost`, `LwjglRenderer`, `LwjglInput` implementing the same three SPIs

### Requirement: LWJGL provides a TextMeasurer implementation wired at startup

`:engine-lwjgl` SHALL provide a concrete `TextMeasurer` implementation backed by NanoVG (`nvgTextBounds` + `nvgTextMetrics`), reporting width and height consistent with `LwjglRenderer.measureText` for the same `(text, size)`. Because NanoVG text APIs require the `nvgContext` and the registered font, the implementation MAY share the renderer's context but MUST be callable outside `nvgBeginFrame`/`nvgEndFrame` (off-frame). The LWJGL `GameHost`/startup path SHALL assign this measurer to `SceneTree.textMeasurer` before the first frame.

#### Scenario: LWJGL measurer matches the renderer

- **WHEN** the LWJGL `TextMeasurer.measureText(text, size)` and `LwjglRenderer.measureText(text, size)` are called with identical arguments
- **THEN** they SHALL return equal `Vec2` dimensions

#### Scenario: Startup wires the measurer onto the tree

- **WHEN** a game is launched on the LWJGL backend (e.g. the `:games:demos` LWJGL entrypoint)
- **THEN** `tree.textMeasurer` SHALL be non-null before the first `render`, and a `Label` in the tree SHALL report a non-null `localBounds()`

#### Scenario: Measurement works outside a frame

- **WHEN** the LWJGL `TextMeasurer.measureText` is called while no `nvgBeginFrame` is active
- **THEN** it SHALL return valid dimensions without corrupting frame state
