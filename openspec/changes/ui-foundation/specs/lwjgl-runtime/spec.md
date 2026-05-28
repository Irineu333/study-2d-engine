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
8. Instanciar `PhysicsSystem`, `GameLoop(tree, renderer, input, physics, physicsHz = config.physicsHz)`, `FpsCounter`.
9. `glfwShowWindow(window)`.
10. Loop principal `while (!glfwWindowShouldClose(window))`: chamar `glfwPollEvents()`, `input.beginTick()`, atualizar `lastNanos`/`pendingDt`/`FpsCounter` (cujo valor alimenta `DebugOverlayLayer.FpsLabel` via canal compartilhado), ler `glfwGetWindowSize`/`glfwGetFramebufferSize` para calcular `pixelRatio`, chamar `tree.resize(winW, winH)`, observar `config.toggleFpsKey`/`config.toggleCollidersKey`/`config.toggleMomentumOverlayKey` flippando `tree.debug.showFps`/`tree.debug.showColliders`/`tree.debug.showMomentum`, chamar `glViewport(0, 0, fbW, fbH)`, chamar `renderer.bind(winW, winH, pixelRatio)`, dentro de `try { ... } finally { renderer.unbind() }` chamar `renderer.clear` + `loop.tick(dtNanos)` — e nada mais. O host NÃO deve chamar `renderDebugOverlay(renderer, tree)` nem qualquer outro helper de desenho após `loop.tick`. Depois do `try/finally`, `glfwSwapBuffers(window)`.
11. Após o loop sair: `tree.stop()`, `renderer.shutdown()`, `Callbacks.glfwFreeCallbacks(window)`, `glfwDestroyWindow(window)`, e em `finally` externo: `glfwTerminate()` + `GLFWErrorCallback.set(null)?.free()`.

`run` MUST ser blocking — retorna somente quando o usuário fecha a janela ou código chama `glfwSetWindowShouldClose(window, true)`. `LwjglHost.run` MUST ser chamado a partir do main thread do processo; em macOS, o processo precisa ter sido iniciado com `-XstartOnFirstThread` (responsabilidade do `build.gradle.kts` que dispara a task `runLwjgl`).

#### Scenario: LwjglHost is assignable to GameHost

- **WHEN** code instantiates `LwjglHost()`
- **THEN** the result is assignable to `com.neoutils.engine.runtime.GameHost`

#### Scenario: run blocks until the window closes

- **WHEN** code calls `host.run(tree, config)` on the main thread (with `-XstartOnFirstThread` on macOS) and the window opens
- **THEN** the call does not return while the window remains open
- **AND** the call returns after the user closes the window

#### Scenario: F1 toggles tree.debug.showFps via the host

- **GIVEN** `tree.debug.showFps == false`
- **WHEN** the user presses the key configured as `config.toggleFpsKey` (default `Key.F1`)
- **THEN** by the next frame `tree.debug.showFps == true`
- **AND** the FPS overlay is drawn via the `DebugOverlayLayer`'s `FpsLabel`, not via a host-side helper

#### Scenario: F2 toggles tree.debug.showColliders via the host

- **GIVEN** `tree.debug.showColliders == false`
- **WHEN** the user presses the key configured as `config.toggleCollidersKey` (default `Key.F2`)
- **THEN** by the next frame `tree.debug.showColliders == true`

#### Scenario: F3 toggles tree.debug.showMomentum via the host

- **GIVEN** `tree.debug.showMomentum == false`
- **WHEN** the user presses the key configured as `config.toggleMomentumOverlayKey`
- **THEN** by the next frame `tree.debug.showMomentum == true`
- **AND** the `MomentumOverlay` node inside `DebugOverlayLayer` resets its sparkline buffers as part of becoming visible

#### Scenario: LwjglHost does not draw debug overlays directly

- **WHEN** the source of `LwjglHost.kt` (and any helper it transitively calls during its render loop body) is grep'd for `renderer.drawText`, `renderer.drawRect`, `renderer.drawLine`, `renderer.drawCircle`, `renderer.drawPolygon`, or any private `renderDebugOverlay(...)` helper
- **THEN** the only draw calls are issued from inside `loop.tick(...)` → `tree.render(renderer)` → walks of the scene graph
- **AND** the host's main loop body itself contains zero `renderer.draw*` calls before or after `loop.tick`

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
