## Why

A change `remove-compose-backend` deixa o invariante #4 (`Renderer`/`Input`/`GameHost` são SPIs com múltiplas implementações) sem cobertura: Skiko vira o único cliente real. Sem um segundo backend exercendo a SPI, "interface" e "código bem nomeado" voltam a ser indistinguíveis — exatamente o vácuo que motivou `add-skiko-runtime` na origem. Compose nunca foi diferenciado o bastante (Compose Desktop renderiza sobre Skia), então quando ele sai, a engine fica devendo um backend arquiteturalmente distinto. Esta change preenche esse vácuo introduzindo `:engine-lwjgl` — um backend sobre GLFW (windowing/input) + NanoVG (render 2D) sobre OpenGL 3.3 core — que valida a SPI por um pipeline gráfico **fundamentalmente** diferente: outro event loop (main-thread vs EDT), outro toolkit de janela (GLFW vs AWT/Swing), outro ciclo de contexto gráfico (GL context + swap buffers vs Skia canvas bound per frame).

## What Changes

- Adicionar capability `lwjgl-runtime`: novo módulo `:engine-lwjgl` com `LwjglRenderer` (sobre `org.lwjgl.nanovg.NanoVG` + backend GL3), `LwjglInput` (sobre callbacks GLFW de teclado/mouse), `LwjglHost` (GLFW window + GL context + main-thread render loop).
- Adicionar entrypoint alternativo em `:games:demos`: novo `MainLwjgl.kt` que monta as mesmas cenas do `Main.kt` (Skiko) sob o `LwjglHost`. Build expõe `./gradlew :games:demos:runLwjgl` como segunda task `application`. O `Main.kt` original (Skiko) permanece intacto e continua sendo `./gradlew :games:demos:run`.
- Atualizar invariante #4 em `CLAUDE.md`: passa de "Skiko é o backend padrão; LWJGL planejado" (linguagem deixada por `remove-compose-backend`) para "Skiko é o backend padrão; LWJGL é o segundo backend experimental, ativado no entrypoint LWJGL do `:games:demos`". Atualizar também a seção "Para rodar Demos" documentando os dois entrypoints e a constraint macOS `-XstartOnFirstThread`.
- Atualizar `ROADMAP.md`: remover `engine-lwjgl` da seção Planned (será adicionado em `Active` automaticamente pelo workflow OpenSpec via tasks.md).
- Estender `:games:demos/build.gradle.kts` para suportar dois mainClass (via `application { mainClass.set(...) }` na task `run` padrão + nova task customizada `runLwjgl` apontando para `MainLwjglKt`, com `applicationDefaultJvmArgs` ou jvmArgs específicos por task incluindo `-XstartOnFirstThread` em macOS).
- Estender `libs.versions.toml`: adicionar versão `lwjgl` e libs `lwjgl-core`, `lwjgl-glfw`, `lwjgl-opengl`, `lwjgl-nanovg`, mais runtimes nativos resolvidos por classifier (`natives-macos`, `natives-macos-arm64`, `natives-linux`, `natives-windows`) seguindo o mesmo padrão `osArch` que `:engine-skiko` usa.
- Não migrar `:games:pong`, `:games:tictactoe` nem `:games:hello-world`: Skiko permanece backend padrão de todos os jogos shipped. LWJGL fica reservado ao entrypoint LWJGL de `:games:demos` como sentinela viva do invariante #4.

## Capabilities

### New Capabilities

- `lwjgl-runtime`: backend LWJGL+NanoVG+GLFW da engine — `LwjglRenderer` (Renderer SPI sobre `NanoVG.nvg*` + `NanoVGGL3.nvgCreate(...)`), `LwjglInput` (Input SPI sobre callbacks `glfwSetKeyCallback`/`glfwSetMouseButtonCallback`/`glfwSetCursorPosCallback`), `LwjglHost` (GameHost SPI hospedando uma `glfwCreateWindow` + main-thread render loop com `glfwPollEvents`/`glfwSwapBuffers`). Único módulo (além de `:engine-skiko`) autorizado a depender de runtime gráfico específico após `remove-compose-backend`.

### Modified Capabilities

- `engine-core`: reformula o invariante #4 — substitui a redação "LWJGL planejado como segundo backend" (que `remove-compose-backend` deixa) por "LWJGL é o segundo backend experimental, sentinela do invariante #4, ativado via entrypoint LWJGL de `:games:demos`". Sem mudança de SPI; o invariante volta a ter cobertura real.
- `demos-sample`: ganha requisito de **segundo entrypoint** rodando o mesmo conjunto de cenas (`1`–`6`) sob `LwjglHost`. Mesmas tecla-bindings (`1`–`6`, `F1`, `F2`, `F3`), mesmas cenas, mesma sentinela de gameplay; o que muda é o caminho de render/input/host. O entrypoint Skiko permanece o entrypoint padrão (`./gradlew :games:demos:run`).
- `project-conventions`: atualiza CLAUDE.md (invariante #4, estrutura de módulos ganhando `:engine-lwjgl`, seção "Para rodar Demos" documentando `runLwjgl` + constraint macOS) e remove `engine-lwjgl` de `ROADMAP.md`.

## Impact

**Código**:
- Novo: `engine-lwjgl/` (módulo, ~300-400 LoC: `LwjglHost.kt`, `LwjglRenderer.kt`, `LwjglInput.kt`, mapeamento `Key`/`MouseButton`).
- Novo: `games/demos/src/main/kotlin/.../MainLwjgl.kt` (~30 LoC; espelha `Main.kt` trocando `SkikoHost` por `LwjglHost`).
- Modificado: `games/demos/build.gradle.kts` (segunda task `run`-like apontando para `MainLwjglKt`, jvmArgs específicos de plataforma).
- Modificado: `settings.gradle.kts` (`include(":engine-lwjgl")`).
- Modificado: `gradle/libs.versions.toml` (entradas LWJGL).

**Docs**:
- `CLAUDE.md`: invariante #4, estrutura de módulos, seção "Para rodar Demos".
- `ROADMAP.md`: remove `engine-lwjgl` de Planned.
- `openspec/specs/`: nova `lwjgl-runtime`, deltas em `engine-core`, `demos-sample`, `project-conventions`.

**Dependências externas**: adiciona LWJGL 3.3.x (core + glfw + opengl + nanovg) com runtimes nativos por classifier. Tamanho do classpath de `:games:demos` cresce em ~20 MB quando o entrypoint LWJGL é usado; o entrypoint Skiko padrão não é afetado.

**Build**:
- `./gradlew :engine-lwjgl:build` deve ser verde após implementação.
- `./gradlew :games:demos:run` (Skiko) permanece idêntico ao comportamento atual — zero regressão esperada.
- `./gradlew :games:demos:runLwjgl` é novo, abre janela GLFW exibindo as mesmas cenas; requer `-XstartOnFirstThread` em macOS (cuidado: rodar a task sem essa flag crasha no `glfwInit()`).
- Tempo de build sobe ligeiramente pela compilação adicional de `:engine-lwjgl`; tempo de teste não muda (sem testes novos exigidos por validar SPI nesta change — validação é visual no entrypoint LWJGL do demos).

**Runtime**:
- Comportamento visual no entrypoint LWJGL deve casar com Skiko nas cenas `1`–`6`, modulando diferenças de AA e text rendering (NanoVG fontstash vs Skia TextLine). Diferenças de pixel **aceitas**; diferenças semânticas (cena rodando errado, F1 não togglando, mouse fora de posição) **não aceitas**.
- macOS: precisa `-XstartOnFirstThread` (sem isso, `glfwInit()` lança `GLFWError` ou crash silencioso). Linux/Windows não exigem. Documentado em CLAUDE.md.

**Validação**:
- Manual em macOS (máquina do autor): rodar os dois entrypoints lado a lado, comparar cenas `1`–`6`, F1, F2, F3, resize de janela, mouse click no `3` Spawner (verifica `LwjglInput.pointerPosition` + conversão screen→world).
- Linux/Windows: não bloqueante, herda mesmo padrão que `:engine-skiko` (resolve por classifier; sem validação manual nesta change).

**Não-objetivos**:
- Backends adicionais (Vulkan, bare-metal OpenGL sem NanoVG): rejeitados via decisão consciente na exploração (NanoVG é pragmaticamente correto para 2D).
- Migrar jogos shipped (Pong, TTT, hello-world) para LWJGL: Skiko permanece padrão.
- Editor visual sobre LWJGL: editor segue em Skiko conforme roadmap.
- Troca de backend em runtime via `ServiceLoader`: cada entrypoint escolhe estaticamente, como hoje.
- HiDPI byte-perfect entre Skiko e LWJGL: aceita divergência (NanoVG faz edge-expand AA, Skia faz GPU AA via Skia internals).
- Testes unitários do `LwjglRenderer`: difícil sem GL context em CI; validação é visual no demos.
