## 1. Prerequisites

- [x] 1.1 Confirmar que `remove-compose-backend` foi mergeado em `main` (este change pressupõe `:engine-compose` removido). Se não, esperar ou fazer rebase.
- [x] 1.2 Confirmar versão estável atual do LWJGL em https://github.com/LWJGL/lwjgl3/releases e travar em `libs.versions.toml` (design assume `3.3.6`, ajustar se newer).
- [x] 1.3 Verificar localmente em macOS que `glfwInit()` funciona com `-XstartOnFirstThread` (smoke test rápido com um `Main.kt` throwaway, antes de mexer no build do projeto).

## 2. Module scaffolding

- [x] 2.1 Criar diretório `engine-lwjgl/` na raiz do projeto, com subdirs `src/main/kotlin/com/neoutils/engine/lwjgl/` e `src/test/kotlin/com/neoutils/engine/lwjgl/`.
- [x] 2.2 Adicionar `include(":engine-lwjgl")` em `settings.gradle.kts`.
- [x] 2.3 Adicionar entradas no `gradle/libs.versions.toml`:
      - versão `lwjgl = "3.3.6"` (ou versão confirmada em 1.2)
      - libs: `lwjgl-core`, `lwjgl-glfw`, `lwjgl-opengl`, `lwjgl-nanovg`
- [x] 2.4 Criar `engine-lwjgl/build.gradle.kts` espelhando o padrão de `engine-skiko/build.gradle.kts`: plugin `kotlinJvm`, helper `nativesClassifier` (macOS-arm64, macOS-x64, linux, windows), `implementation(projects.engine)`, `api(libs.lwjgl.*)`, `runtimeOnly(...:nativesClassifier)` para cada lib. Sem testes além dos padrões.
- [x] 2.5 Rodar `./gradlew :engine-lwjgl:dependencies` e confirmar que os JARs LWJGL aparecem (sanity check antes de escrever código).

## 3. LwjglInput (sem GL context, dá pra testar puro)

- [x] 3.1 Criar `LwjglInput.kt` implementando `Input` SPI: campos `keysDown`, `keysPressedThisTick`, `mouseDown`, `mouseClickedThisTick`, `pointer: Vec2`. Métodos `beginTick()`, `onGlfwKey(int, int)`, `onGlfwMouseButton(int, int)`, `onGlfwCursorPos(float, float)`, e overrides do `Input`.
- [x] 3.2 Criar tabela de mapeamento `glfwKeyToEngineKey(int): Key?` cobrindo WASD, setas, F1/F2/F3, espaço, enter, escape, dígitos 0–9, letras A–Z (na medida que o enum `Key` em `:engine` os define).
- [x] 3.3 Criar `glfwMouseButtonToEngineButton(int): MouseButton?` para `GLFW_MOUSE_BUTTON_LEFT/RIGHT/MIDDLE`.
- [x] 3.4 Escrever testes unitários em `LwjglInputTest.kt` cobrindo: press registra em `down` + `wasPressed`; release limpa `down` mantém `wasPressed` do tick; `beginTick` limpa `wasPressed`; `pointerPosition` reflete último cursor; unmapped key retorna silenciosamente.
- [x] 3.5 `./gradlew :engine-lwjgl:test` verde.

## 4. LwjglRenderer (precisa GL context; teste é manual via demos)

- [x] 4.1 Criar `LwjglRenderer.kt` com campos `nvgContext: Long = NULL`, `transformDepth: Int = 0`, `defaultFontId: Int = -1`.
- [x] 4.2 Implementar `init()`: validar que GL context é current (`GL.getCapabilities()` non-null); chamar `NanoVGGL3.nvgCreate(NVG_ANTIALIAS or NVG_STENCIL_STROKES)`; armazenar handle; resolver e registrar fonte default via `nvgCreateFont(ctx, "default", path)` com lista de candidatos por OS (macOS: `/System/Library/Fonts/Helvetica.ttc`; linux: tentar `/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf` e outras; windows: `C:\Windows\Fonts\arial.ttf`). Falhar com `IllegalStateException` listando paths tentados se nenhum resolver.
- [x] 4.3 Implementar `bind(windowWidth, windowHeight, pixelRatio)`: chamar `nvgBeginFrame(...)`; resetar `transformDepth = 0`.
- [x] 4.4 Implementar `unbind()`: capturar `transformDepth`; chamar `nvgEndFrame(ctx)`; check `transformDepth == 0` lançando `IllegalStateException` se não.
- [x] 4.5 Implementar `shutdown()`: `NanoVGGL3.nvgDelete(ctx)`; zerar `nvgContext`.
- [x] 4.6 Implementar `clear(color)`: traduzir `Color` para 4 floats e chamar `GL11.glClearColor` + `GL11.glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)`. Nota: clear roda ANTES de `nvgBeginFrame` no host; verificar contrato vs. SPI (se SPI exige `clear` dentro do frame, ajustar — talvez `clear` aqui apenas faz `nvgBeginPath` + `nvgRect` cobrindo full window com fillColor — mas SkikoRenderer.clear chama `canvas.clear(...)` direto, então spelling here é `glClear`).
- [x] 4.7 Implementar `drawRect(rect, color, filled)`: `nvgBeginPath` + `nvgRect(x, y, w, h)` + fillOrStroke helper.
- [x] 4.8 Implementar `drawCircle(center, radius, color, filled, thickness)`: `nvgBeginPath` + `nvgCircle(cx, cy, r)` + fillOrStroke; usar `thickness` em `nvgStrokeWidth` quando stroke.
- [x] 4.9 Implementar `drawLine(from, to, thickness, color)`: `nvgBeginPath` + `nvgMoveTo` + `nvgLineTo` + `nvgStrokeWidth(thickness)` + `nvgStrokeColor` + `nvgStroke`.
- [x] 4.10 Implementar helpers `setNvgFillColor(color)` e `setNvgStrokeColor(color)` que alocam `NVGColor.calloc(stack)` em `MemoryStack.stackPush()` e devolvem (ou usam pre-allocated stack-scoped) — confirmar padrão LWJGL para evitar leaks.
- [x] 4.11 Implementar `drawText(text, position, size, color)`: `nvgFontFaceId(ctx, defaultFontId)` + `nvgFontSize(ctx, size)` + `nvgTextAlign(ctx, NVG_ALIGN_LEFT or NVG_ALIGN_TOP)` + `setNvgFillColor(color)` + `nvgText(ctx, position.x, position.y, text)`.
- [x] 4.12 Implementar `measureText(text, size)`: `nvgFontFaceId` + `nvgFontSize`; alocar `FloatArray(4)` bounds; `nvgTextBounds(ctx, 0f, 0f, text, bounds)`; chamar `nvgTextMetrics(ctx, ascender, descender, lineh)` (passando `FloatArray(1)` para cada); retornar `Vec2(bounds[2] - bounds[0], lineh)`.
- [x] 4.13 Implementar `drawPolygon(points, color)`: early-return se `points.size < 3`; `nvgBeginPath` + `nvgMoveTo(points[0])` + loop `nvgLineTo(points[i])` para `i = 1..n-1` + `nvgClosePath` + fill.
- [x] 4.14 Implementar `pushTransform(translation, rotation, scale)`: `nvgSave(ctx)` + `nvgTranslate(ctx, tx, ty)` + `nvgRotate(ctx, rotation)` + `nvgScale(ctx, sx, sy)`; incrementar `transformDepth`.
- [x] 4.15 Implementar `popTransform()`: check `transformDepth > 0`; `nvgRestore(ctx)`; decrementar.
- [x] 4.16 `./gradlew :engine-lwjgl:build` verde (compila sem rodar testes que exijam GL).

## 5. LwjglHost (precisa GL context; teste manual)

- [x] 5.1 Criar `LwjglHost.kt` implementando `GameHost` SPI.
- [x] 5.2 Implementar `run(tree, config)` seguindo a ordem do design (passos 1–11): error callback, init, hints, createWindow, makeCurrent, GL.createCapabilities, swapInterval, callbacks, renderer.init, instâncias loop/physics/fps, showWindow, render loop, teardown.
- [x] 5.3 Dentro do loop: `glfwPollEvents`, `input.beginTick`, calcular `pendingDt`, `Debug.currentFps = fps.record(...)`, ler `glfwGetWindowSize` (buffer `int[1] + int[1]`) e `glfwGetFramebufferSize`, calcular `pixelRatio`, `tree.resize(winW, winH)`, `glViewport(0, 0, fbW, fbH)`, `renderer.bind(winW, winH, pixelRatio)`, try-finally com `clear`/`loop.tick`/toggles F1/F2/F3 + `MomentumOverlay.reset()` no enable + `renderDebugOverlay`, finally `renderer.unbind()`, depois `glfwSwapBuffers`.
- [x] 5.4 Teardown: `tree.stop()`, `renderer.shutdown()`, `Callbacks.glfwFreeCallbacks(window)`, `glfwDestroyWindow(window)`; em `finally` externo `glfwTerminate()` + `GLFWErrorCallback.set(null)?.free()` (substituído por `GLFW.glfwSetErrorCallback(null)?.free()` pois `set` é instance method).
- [x] 5.5 `./gradlew :engine-lwjgl:build` verde.

## 6. Demos LWJGL entrypoint

- [x] 6.1 Criar `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/MainLwjgl.kt` (pacote real do módulo é `com.neoutils.engine.games.demos`, não `com.neoutils.games.demos` como o spec inicial sugeria — segui o pacote do `Main.kt` Skiko existente) espelhando `Main.kt`: instancia `LwjglHost()` e chama `host.run(SceneTree(root = DemoSwitcherRoot()), GameConfig(title = "engine-consistency demos", width = 800, height = 600))`.
- [x] 6.2 Em `games/demos/build.gradle.kts`: adicionar `implementation(projects.engineLwjgl)` (sem remover `implementation(projects.engineSkiko)`).
- [x] 6.3 Registrar task `runLwjgl` do tipo `JavaExec` no `games/demos/build.gradle.kts`: group `application`, descrição "Runs :games:demos using the LWJGL backend", `mainClass.set("com.neoutils.engine.games.demos.MainLwjglKt")`, `classpath = sourceSets["main"].runtimeClasspath`, `if (OperatingSystem.current().isMacOsX) jvmArgs("-XstartOnFirstThread")`.
- [x] 6.4 `./gradlew :games:demos:tasks --all` mostra `runLwjgl` em Application tasks. `./gradlew :games:demos:run` ainda funciona (Skiko).

## 7. Manual validation (em macOS, requer máquina do autor)

- [x] 7.1 `./gradlew :games:demos:run` (Skiko) — smoke test (25s) confirmou que a janela Skiko abre, `GameLoop` ticka (warns de spiral-of-death do physics são esperados em primeiros frames). Cenas/F-keys/mouse/resize são validação visual a cargo do autor.
- [x] 7.2 `./gradlew :games:demos:runLwjgl` — smoke test (30s) confirmou que GLFW inicializa, janela é criada, `GameLoop` ticka, processo encerra limpo. Sem `-XstartOnFirstThread` (gerenciado pela task) o subprocesso crasharia em `glfwInit()` no macOS.
- [ ] 7.3 No entrypoint LWJGL: cena `1` Solar System — Sun, planetas, luas, anel de Saturno aparecem; órbitas evoluem; transforms aninhados em 4 níveis funcionam. *(Validação visual pendente — sessão headless do harness.)*
- [ ] 7.4 No entrypoint LWJGL: cena `2` Scale hierarchy — composição de scale do pai propaga ao filho. *(Validação visual pendente.)*
- [ ] 7.5 No entrypoint LWJGL: cena `3` Spawner — clique adiciona bolinha em `(x, y)` aproximado; trap remove bolinha quando ela entra. *(Validação visual pendente.)*
- [ ] 7.6 No entrypoint LWJGL: cena `4` Collision stress — bolinhas quicam, paredes acompanham resize, F3 mostra overlay de momento (texto + sparklines). *(Validação visual pendente.)*
- [ ] 7.7 No entrypoint LWJGL: cena `5` Rotating box — bolinhas dentro de caixa rotacionada não tunelam. *(Validação visual pendente.)*
- [ ] 7.8 No entrypoint LWJGL: cena `6` Tumbling swarm — squares com rotação quicam com spin em hits glancing; F3 overlay funciona. *(Validação visual pendente.)*
- [ ] 7.9 Comparar Skiko vs LWJGL lado a lado nas 6 cenas: tolerar diferenças visuais (AA, fontes); falhar em divergência semântica (cena errada, F-key não togglando, mouse fora). *(Validação visual pendente.)*

## 8. Docs

- [x] 8.1 Atualizar `CLAUDE.md`:
      - Invariante #2: adicionar `org.lwjgl.*` à lista proibida.
      - Invariante #4: reformular — Skiko default, LWJGL active second backend (não mais "planned"), sentinela via `:games:demos`'s runLwjgl.
      - Seção "Module Structure": adicionar linha `:engine-lwjgl` com descrição correta.
      - Seção "Para rodar Demos": documentar `./gradlew :games:demos:runLwjgl` ao lado do `:run` existente, com caveat macOS sobre `-XstartOnFirstThread` (auto-injetado pela task).
- [x] 8.2 Atualizar `ROADMAP.md`: `engine-lwjgl` removido de Planned e promovido para Active (já que a change está em flight; sai quando arquivada).
- [x] 8.3 Sanity check final: `openspec validate engine-lwjgl --strict` verde.

## 9. Wrap-up

- [x] 9.1 Rodar `./gradlew build` completo — todos os módulos compilam, testes verdes.
- [ ] 9.2 Commit/PR. PR description nota a dependência de `remove-compose-backend` ter aterrissado primeiro.
- [ ] 9.3 Quando merged, rodar `/opsx:archive engine-lwjgl` para sincronizar specs principais e mover para `openspec/changes/archive/`.
