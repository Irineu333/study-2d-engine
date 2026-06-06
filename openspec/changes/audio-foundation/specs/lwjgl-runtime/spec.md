## MODIFIED Requirements

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
9. Setar `tree.debugHudKey = config.debugHudKey` uma vez antes do primeiro frame. Wirar serviços de plataforma na tree antes do primeiro frame: setar `tree.audio = JavaSoundAudio()` (de `:engine-audio-javasound`). Falha ao inicializar o backend de áudio (ex.: headless/sem dispositivo de som) MUST ser tolerada — o host loga e deixa `tree.audio` como `null` (no-op), sem abortar `run`.
10. `glfwShowWindow(window)`.
11. Loop principal `while (!glfwWindowShouldClose(window))`: chamar `glfwPollEvents()`, `input.beginTick()`, ler `glfwGetWindowSize`/`glfwGetFramebufferSize` para calcular `pixelRatio`, chamar `tree.resize(winW, winH)`, chamar `glViewport(0, 0, fbW, fbH)`, chamar `renderer.bind(winW, winH, pixelRatio)`, dentro de `try { ... } finally { renderer.unbind() }` chamar `renderer.clear` + `loop.tick(dtNanos)` — e nada mais. Depois do `try/finally`, `glfwSwapBuffers(window)`.

    O host **NÃO** deve: instanciar `FpsCounter`, escrever em `tree.debug.*` por frame (exceto o set único em passo 9), chamar qualquer helper de debug overlay, ou pollar input para toggle de visualização de debug. Toda saída visual de debug (FPS, colliders, momentum, HUD) sai do `tree.render(renderer)` via `DebugLayer` auto-inserido pela engine.
12. Após o loop sair: `tree.stop()` (que dispõe o backend de áudio), `renderer.shutdown()`, `Callbacks.glfwFreeCallbacks(window)`, `glfwDestroyWindow(window)`, e em `finally` externo: `glfwTerminate()` + `GLFWErrorCallback.set(null)?.free()`.

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

#### Scenario: LwjglHost wires the audio backend into the tree

- **WHEN** `LwjglHost().run(tree, config)` starts and a sound device is available
- **THEN** `tree.audio` is set to a `JavaSoundAudio` instance before the first frame
- **AND** the same `:engine-audio-javasound` module serves both `SkikoHost` and `LwjglHost`
- **AND** when the loop exits, `tree.stop()` disposes the audio backend
