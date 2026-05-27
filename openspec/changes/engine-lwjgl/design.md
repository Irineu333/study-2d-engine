## Context

A engine roda hoje em dois backends: `:engine-skiko` (padrão, sobre `org.jetbrains.skia.Canvas` + AWT/Swing `JFrame`) e `:engine-compose` (segundo, sobre Compose Desktop). A change `remove-compose-backend` (em paralelo, no worktree `remove-compose-backend`) deleta `:engine-compose` inteiro porque ele nunca foi um segundo backend de verdade — Compose Desktop renderiza sobre Skia, então `:engine-compose` é Skia com plumbing diferente do `:engine-skiko`. O custo (Compose Compiler atrelado à versão de Kotlin, +1 plugin, ~410 LoC de tradução) deixou de valer a pena.

Quando `remove-compose-backend` aterrissar, o invariante #4 (`Renderer`/`Input`/`GameHost` são SPIs com múltiplas implementações) fica sem cobertura: um único cliente da SPI volta a tornar "interface" indistinguível de "código bem nomeado". `add-skiko-runtime` na origem identificou esse vácuo como causa raiz suficiente para introduzir Skiko; a mesma lógica se aplica agora invertida — sem segundo backend genuíno, o invariante é promessa não-testada.

A direção firmada na exploração:

- **Backend gráfico**: LWJGL (`org.lwjgl:lwjgl-glfw` + `lwjgl-opengl` + `lwjgl-nanovg`) sobre OpenGL 3.3 core. NanoVG escolhido como ferramenta pragmaticamente correta para 2D vector — alternativa bare-metal OpenGL foi considerada e rejeitada por não pagar 1-2 semanas extras de implementação (text atlas, ear-clipping, line thickness, AA) sem trazer valor arquitetural além do que NanoVG já entrega.
- **Validator**: `:games:demos`, via segundo entrypoint `MainLwjgl.kt`. Cenas existentes (`1` Solar System até `6` Tumbling Swarm) compartilhadas — mesmo módulo, mesma compilação, dois `Main` distintos. Diferenças visuais aceitas dentro de tolerância (AA, fontstash vs Skia text); divergência semântica não aceita.
- **Sequência**: esta change pressupõe que `remove-compose-backend` já aplicou. Se ambas tramitarem em paralelo, esta deve fazer rebase sobre a outra antes de merge para evitar reintroduzir Compose ou tocar arquivos já deletados.

Restrições e premissas:

- **JVM Desktop apenas.** Multiplataforma não-JVM segue fora de escopo.
- **Validação manual em macOS** (máquina do autor). LWJGL resolve `natives-macos-arm64`/`natives-macos`/`natives-linux`/`natives-windows` por classifier, mas verificação visual é só localmente.
- **macOS exige `-XstartOnFirstThread`.** GLFW liga em Cocoa, que exige NSApp no main thread. Sem essa JVM flag, `glfwInit()` crasha. Skiko/Compose escapam disso porque AWT já faz a ponte; LWJGL não tem essa cortesia.
- **NanoVG não substitui o `Renderer` SPI.** A SPI da engine permanece exata: `clear`, `drawRect`, `drawCircle`, `drawLine`, `drawText`, `measureText`, `drawPolygon`, `pushTransform`, `popTransform`. `LwjglRenderer` traduz cada uma para `nvg*` calls; NanoVG fica como detalhe interno do módulo.

## Goals / Non-Goals

**Goals:**

- `:engine-lwjgl` como o **único** módulo além de `:engine-skiko` autorizado a depender de runtime gráfico específico após `remove-compose-backend`.
- `LwjglHost`, `LwjglRenderer`, `LwjglInput` implementando as SPIs sem vazar nenhum tipo `org.lwjgl.*` no `:engine`.
- Entrypoint LWJGL em `:games:demos` rodando as 6 cenas existentes com mesmas key-bindings e mesma semântica observável. Skiko permanece backend padrão de `:games:demos:run` sem mudança.
- Constraint macOS `-XstartOnFirstThread` resolvida via Gradle (`tasks.named("runLwjgl") { jvmArgs("-XstartOnFirstThread") }` quando em macOS), não delegada ao usuário.
- Invariante #4 restaurado a status testado.

**Non-Goals:**

- Bare-metal OpenGL sem NanoVG. Rejeitado conscientemente na exploração.
- Migrar `:games:pong`, `:games:tictactoe`, `:games:hello-world` para LWJGL. Skiko permanece padrão.
- Editor visual sobre LWJGL.
- `ServiceLoader` ou troca de backend em runtime.
- HiDPI byte-perfect entre Skiko e LWJGL.
- Validação em Linux/Windows.
- Testes unitários do `LwjglRenderer` (exige GL context, GLFW window, framebuffer — irreproduzível em CI sem `xvfb` + GPU virtual). Validação é visual no entrypoint.
- `SkikoWindow`-equivalent: o `LwjglHost` usa GLFW como toolkit de janela; não tentamos uma variante AWT-Swing-com-GL.

## Decisions

### Decisão 1: NanoVG como motor de render 2D, não OpenGL bare-metal

`LwjglRenderer` mantém um handle `long nvgContext` criado via `NanoVGGL3.nvgCreate(NVG_ANTIALIAS or NVG_STENCIL_STROKES)` durante `init()`, destruído via `NanoVGGL3.nvgDelete(nvgContext)` durante `shutdown()`. Cada tick chama `nvgBeginFrame(nvgContext, windowWidth, windowHeight, devicePixelRatio)` no início e `nvgEndFrame(nvgContext)` no fim. Os 9 métodos do `Renderer` SPI mapeiam diretamente:

| Renderer SPI                             | NanoVG implementation                                                              |
| ---------------------------------------- | ---------------------------------------------------------------------------------- |
| `clear(color)`                           | `glClearColor` + `glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)` (antes de `nvgBeginFrame`) |
| `drawRect(rect, color, filled)`          | `nvgBeginPath` + `nvgRect` + `nvgFillColor`/`nvgStrokeColor` + `nvgFill`/`nvgStroke` |
| `drawCircle(center, radius, ...)`        | `nvgBeginPath` + `nvgCircle` + idem                                                |
| `drawLine(from, to, thickness, color)`   | `nvgBeginPath` + `nvgMoveTo` + `nvgLineTo` + `nvgStrokeWidth(thickness)` + `nvgStrokeColor` + `nvgStroke` |
| `drawText(text, position, size, color)`  | `nvgFontFaceId` + `nvgFontSize` + `nvgTextAlign(NVG_ALIGN_LEFT or NVG_ALIGN_TOP)` + `nvgFillColor` + `nvgText(ctx, x, y, text)` |
| `measureText(text, size) → Vec2`         | `nvgFontFaceId` + `nvgFontSize` + `nvgTextBounds(ctx, 0, 0, text, bounds[4])` → `Vec2(width, fontHeight)` onde `fontHeight` vem de `nvgTextMetrics` |
| `drawPolygon(points, color)`             | `nvgBeginPath` + `nvgMoveTo(p0)` + `nvgLineTo(pN)` repetido + `nvgClosePath` + `nvgFillColor` + `nvgFill` (NanoVG faz tessellation interna; concavidade ok, self-intersection undefined — mesmo contrato da SPI) |
| `pushTransform(translation, rotation, scale)` | `nvgSave` + `nvgTranslate(tx, ty)` + `nvgRotate(rotation)` + `nvgScale(sx, sy)`; incrementar `transformDepth` |
| `popTransform()`                         | check `transformDepth > 0` + `nvgRestore` + decrementar                          |

**Por quê:**

- NanoVG existe exatamente porque OpenGL é 3D-first e 2D-naive. Reimplementar font atlas (STB), polygon tessellator (ear-clipping ou earcut), line thickness (quad expansion), e edge-AA gastaria 1-2 semanas sem mudança no shape do `Renderer` SPI. O ponto da change é exercer a SPI por outro pipeline, não ensinar OpenGL bare-metal.
- LWJGL ships `lwjgl-nanovg` como módulo first-class. Sem build extra, sem GLue manual.
- NanoVG tem AA built-in (edge-expand sem MSAA), economiza `glfwWindowHint(GLFW_SAMPLES, ...)`. Se ficar feio em alguma cena, MSAA é uma linha extra.

**Alternativas consideradas:**

- **Bare-metal GL 3.3 core**. Educacional, mas o `purpose` do projeto não compensa o custo. NanoVG escolhido conscientemente pela usuária na exploração com instrução "não precisa forçar dificuldade".
- **lwjgl-yoga + bare-metal GL**: Yoga é layout, não render. Não cobre nada do que a SPI precisa.
- **LWJGL + Skia via bindings JNI próprios**: reinventar Skiko. Não.

### Decisão 2: GLFW como toolkit de janela e input, não AWT-embedded

`LwjglHost.run(tree, config)` segue este fluxo (ordem importa):

```
fun run(tree: SceneTree, config: GameConfig) {
    // 1. errorCallback ANTES de glfwInit para capturar erros de init
    GLFWErrorCallback.createPrint(System.err).set()
    check(glfwInit()) { "Failed to initialize GLFW" }
    try {
        // 2. window hints (GL 3.3 core, forward-compatible para macOS)
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)   // macOS exige
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)                // mostra só após config

        val window = glfwCreateWindow(config.width, config.height, config.title, NULL, NULL)
        check(window != NULL) { "Failed to create GLFW window" }

        glfwMakeContextCurrent(window)
        GL.createCapabilities()   // LWJGL: liga GLCapabilities ao thread
        glfwSwapInterval(1)        // vsync

        // 3. input callbacks (key, mousebutton, cursorpos)
        val input = LwjglInput()
        glfwSetKeyCallback(window) { _, key, _, action, _ -> input.onGlfwKey(key, action) }
        glfwSetMouseButtonCallback(window) { _, btn, action, _ -> input.onGlfwMouseButton(btn, action) }
        glfwSetCursorPosCallback(window) { _, x, y -> input.onGlfwCursorPos(x.toFloat(), y.toFloat()) }

        // 4. renderer (boota NanoVG context após GL.createCapabilities)
        val renderer = LwjglRenderer()
        renderer.init()
        val physics = PhysicsSystem()
        val loop = GameLoop(tree, renderer, input, physics, physicsHz = config.physicsHz)
        val fps = FpsCounter()
        var lastNanos = 0L

        glfwShowWindow(window)

        // 5. main-thread render loop
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            input.beginTick()
            val nanoTime = System.nanoTime()
            val pendingDt = if (lastNanos == 0L) 16_666_666L else nanoTime - lastNanos
            lastNanos = nanoTime
            Debug.currentFps = fps.record(nanoTime)

            val (winW, winH) = queryWindowSize(window)
            val (fbW, fbH) = queryFramebufferSize(window)
            val pxRatio = if (winW > 0) fbW.toFloat() / winW.toFloat() else 1f
            tree.resize(winW.toFloat(), winH.toFloat())

            glViewport(0, 0, fbW, fbH)
            renderer.bind(winW, winH, pxRatio)
            try {
                renderer.clear(Color.BLACK)
                loop.tick(pendingDt)
                if (input.wasKeyPressed(config.toggleFpsKey)) Debug.showFps = !Debug.showFps
                if (input.wasKeyPressed(config.toggleCollidersKey)) Debug.colliderVisualization = !Debug.colliderVisualization
                if (input.wasKeyPressed(config.toggleMomentumOverlayKey)) {
                    Debug.showMomentumOverlay = !Debug.showMomentumOverlay
                    if (Debug.showMomentumOverlay) com.neoutils.engine.dx.MomentumOverlay.reset()
                }
                renderDebugOverlay(renderer, tree)
            } finally {
                renderer.unbind()
            }
            glfwSwapBuffers(window)
        }

        // 6. teardown
        tree.stop()
        renderer.shutdown()
        Callbacks.glfwFreeCallbacks(window)
        glfwDestroyWindow(window)
    } finally {
        glfwTerminate()
        GLFWErrorCallback.set(null)?.free()
    }
}
```

**Por quê:**

- GLFW é o toolkit de janela que LWJGL ships nativamente; criar uma janela com GL context em outro toolkit (AWT/Swing com `GLJPanel` JOGL-style) reintroduziria as duas coisas que esta change quer evitar: AWT (já temos em Skiko) e uma pilha alternativa de bindings.
- `glViewport` em framebuffer-pixels (`fbW`, `fbH`) garante render correto em Retina/HiDPI; `nvgBeginFrame` recebe `winW, winH, pxRatio` e NanoVG faz a conta internamente — o `Renderer` SPI continua falando "pixels lógicos".
- `GLFW_OPENGL_FORWARD_COMPAT` é exigência do macOS para criar context 3.3 core; em Linux/Windows é no-op.
- Erros GLFW vão pro stderr via `GLFWErrorCallback.createPrint(System.err)` antes de `glfwInit()` para capturar erros do próprio init.

**Alternativas consideradas:**

- **GLFW + AWT bridge (`org.lwjgl.opengl.awt.AWTGLCanvas`)**: existe via `lwjgl-jawt`, mas reintroduz AWT e o thread-juggling correspondente. Pior dos dois mundos.
- **Construir janela com `org.jetbrains.skiko.SkikoWindow` e usar OpenGL via Skiko**: vazaria Skiko para o `:engine-lwjgl`, transformando-o em "Skiko backend nº 2 disfarçado".

### Decisão 3: Constraint macOS `-XstartOnFirstThread` resolvida no `build.gradle.kts`

`:games:demos/build.gradle.kts` ganha uma task customizada `runLwjgl` ao lado do `application` default, com jvmArgs específico:

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(projects.engine)
    implementation(projects.engineBundle)
    implementation(projects.engineSkiko)     // entrypoint default (Skiko)
    implementation(projects.engineLwjgl)     // entrypoint alternativo (LWJGL)
    // ...
}

application {
    mainClass.set("com.neoutils.games.demos.MainKt")   // Skiko, runs as ./gradlew :games:demos:run
}

private val isMacOs = OperatingSystem.current().isMacOsX

tasks.register<JavaExec>("runLwjgl") {
    group = "application"
    description = "Runs :games:demos using the LWJGL backend"
    mainClass.set("com.neoutils.games.demos.MainLwjglKt")
    classpath = sourceSets["main"].runtimeClasspath
    if (isMacOs) jvmArgs("-XstartOnFirstThread")
}
```

**Por quê:**

- Usuário roda `./gradlew :games:demos:runLwjgl` sem saber/lembrar da flag JVM. Esquecer `-XstartOnFirstThread` em macOS crasha; resolver no build é o caminho menos surpreendente.
- `tasks.register<JavaExec>` é o padrão Gradle quando se quer um segundo entrypoint runnable num módulo `application` plugin (o plugin só configura uma `run` task; outras viram `JavaExec` custom).
- O entrypoint Skiko continua sendo "o entrypoint" via `application.mainClass`, preservando `./gradlew :games:demos:run` sem mudança.

**Alternativas consideradas:**

- **Documentar a flag no README e exigir do usuário**: trade-off ruim — esquecer a flag é silenciosamente fatal.
- **Detectar runtime e relançar JVM com a flag** (jvmAgent-style): hacky, frágil em macOS com Apple Silicon.
- **Mover o entrypoint LWJGL para um módulo novo (`:games:demos-lwjgl`)**: duplica `Main.kt`, build files, e o setup gradle de scripting host; ganha pouco.

### Decisão 4: `LwjglInput` mantém o mesmo contrato `Input` SPI, com mapeamento próprio Key/MouseButton

```kotlin
class LwjglInput : Input {
    private val keysDown = HashSet<Key>()
    private val keysPressedThisTick = HashSet<Key>()
    private val mouseDown = HashSet<MouseButton>()
    private val mouseClickedThisTick = HashSet<MouseButton>()
    @Volatile private var pointer: Vec2 = Vec2(0f, 0f)

    fun beginTick() {
        keysPressedThisTick.clear()
        mouseClickedThisTick.clear()
    }

    fun onGlfwKey(glfwKey: Int, action: Int) {
        val key = glfwKeyToEngineKey(glfwKey) ?: return
        when (action) {
            GLFW_PRESS -> { if (keysDown.add(key)) keysPressedThisTick.add(key) }
            GLFW_RELEASE -> keysDown.remove(key)
            // GLFW_REPEAT ignorado: SPI não tem semântica de repeat
        }
    }

    fun onGlfwMouseButton(glfwButton: Int, action: Int) {
        val button = glfwMouseToEngineMouse(glfwButton) ?: return
        when (action) {
            GLFW_PRESS -> { if (mouseDown.add(button)) mouseClickedThisTick.add(button) }
            GLFW_RELEASE -> mouseDown.remove(button)
        }
    }

    fun onGlfwCursorPos(x: Float, y: Float) {
        pointer = Vec2(x, y)
    }

    override val pointerPosition: Vec2 get() = pointer
    override fun isKeyDown(key: Key) = key in keysDown
    override fun wasKeyPressed(key: Key) = key in keysPressedThisTick
    override fun isMouseDown(button: MouseButton) = button in mouseDown
    override fun wasMouseClicked(button: MouseButton) = button in mouseClickedThisTick
}
```

Mapeamento `glfwKeyToEngineKey` cobre o subset do enum `Key` em `:engine`: WASD, setas, F1/F2/F3, espaço, enter, escape, dígitos 0–9, letras A–Z (na medida em que o enum os define). Falhar uma key (return `null`) é silencioso — outras teclas simplesmente não disparam events na engine. `MouseButton`: `GLFW_MOUSE_BUTTON_LEFT/RIGHT/MIDDLE` → `MouseButton.Left/Right/Middle`.

**Por quê:**

- Tabela de mapeamento separada por backend é a mesma escolha que `SkikoInput` faz para AWT. Aceito (e justificado no `add-skiko-runtime` design).
- `HashSet` simples (não `ConcurrentHashMap.newKeySet()`): GLFW callbacks chamam de dentro de `glfwPollEvents()`, que roda no mesmo thread do render loop. Não há concorrência.
- `@Volatile var pointer`: por simetria/safety futura caso GLFW começe a despachar cursor events em outro thread. Custo zero.

### Decisão 5: Versão LWJGL alinhada ao último estável (3.3.x), sem alinhamento com outros módulos

`libs.versions.toml`:

```toml
lwjgl = "3.3.6"     # versão estável atual; sem necessidade de alinhar com Skiko (não compartilha classpath)

[libraries]
lwjgl-core   = { module = "org.lwjgl:lwjgl",         version.ref = "lwjgl" }
lwjgl-glfw   = { module = "org.lwjgl:lwjgl-glfw",    version.ref = "lwjgl" }
lwjgl-opengl = { module = "org.lwjgl:lwjgl-opengl",  version.ref = "lwjgl" }
lwjgl-nanovg = { module = "org.lwjgl:lwjgl-nanovg",  version.ref = "lwjgl" }
```

E em `:engine-lwjgl/build.gradle.kts`:

```kotlin
private val nativesClassifier: String = run {
    val os = OperatingSystem.current()
    val arch = System.getProperty("os.arch").orEmpty()
    val isAarch64 = arch == "aarch64" || arch == "arm64"
    when {
        os.isMacOsX && isAarch64 -> "natives-macos-arm64"
        os.isMacOsX -> "natives-macos"
        os.isWindows -> "natives-windows"
        else -> "natives-linux"
    }
}

dependencies {
    implementation(projects.engine)
    api(libs.lwjgl.core)
    api(libs.lwjgl.glfw)
    api(libs.lwjgl.opengl)
    api(libs.lwjgl.nanovg)
    runtimeOnly("org.lwjgl:lwjgl:${libs.versions.lwjgl.get()}:$nativesClassifier")
    runtimeOnly("org.lwjgl:lwjgl-glfw:${libs.versions.lwjgl.get()}:$nativesClassifier")
    runtimeOnly("org.lwjgl:lwjgl-opengl:${libs.versions.lwjgl.get()}:$nativesClassifier")
    runtimeOnly("org.lwjgl:lwjgl-nanovg:${libs.versions.lwjgl.get()}:$nativesClassifier")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
```

**Por quê:**

- LWJGL não tem conflito de classpath com Skiko (são módulos diferentes; `:games:demos` carrega ambos mas não cruzam classes). Sem necessidade de alinhar versões como fizemos Skiko/Compose.
- 3.3.x é a release line estável atual.
- `runtimeOnly` em vez de `implementation` para os nativos: o JAR principal traz as bindings Kotlin/Java; os nativos são `.dylib`/`.so`/`.dll` carregados em runtime via `System.loadLibrary` interno do LWJGL.

### Decisão 6: Font default carregada de fonte do sistema, sem fallback empacotado

`LwjglRenderer.init()` registra a fonte default via `nvgCreateFont(ctx, "default", pathToTtf)`. Caminho resolvido por uma lista de candidatos por OS (`/System/Library/Fonts/Helvetica.ttc` em macOS, `/usr/share/fonts/...` em Linux, `C:\Windows\Fonts\...` em Windows). Se nada resolver, `LwjglRenderer.init()` lança `IllegalStateException` com a lista de paths tentados — fail-fast.

**Por quê:**

- Empacotar uma TTF no módulo evita esse risco mas adiciona 200 KB+ por fonte e levanta dúvidas de licença (Roboto vs DejaVu vs OFL). Adiar até precisar.
- Skiko resolve a default typeface via `FontMgr.default.matchFamilyStyle` com lista de candidatos similar (`SF Pro Display`, `Helvetica Neue`, `Arial`, etc.). LWJGL não tem FontMgr; resolver paths por OS é a aproximação mais simples.
- Diferenças de tipo entre Skiko e LWJGL **aceitas** — o contrato `measureText` se mantém *para o tipo que cada backend carregou*; não há promessa de pixel-perfeição entre backends.

**Alternativas consideradas:**

- **Empacotar Roboto Regular como `resources/fonts/default.ttf`** lido via `getResourceAsStream`: portável e determinístico. Trade-off é tamanho + licença. Postergado; se `nvgCreateFont` falhar em ambientes do mundo real (Docker minimal, CI sem fontes), refazer com fonte empacotada.

### Decisão 7: Apenas `:games:demos` ganha entrypoint LWJGL nesta change

`:games:pong`, `:games:tictactoe`, `:games:hello-world` permanecem inalterados. Skiko continua backend padrão de todos os jogos shipped.

**Por quê:**

- Demos cobre o maior superset de capabilities do `Renderer` SPI (transforms aninhados em até 4 níveis em Solar System, mutação durante traversal em Spawner, AABB stress em Collision/Tumbling, overlay com `drawText` em F1/F3). Se o entrypoint LWJGL roda os 6 demos corretamente, o backend está exercido o suficiente.
- Spread cuidadoso: spread fino demais (todo jogo ganha dois entrypoints) infla maintenance burden por pouco ganho marginal.
- Demos é o lugar natural pra "rodar tudo" — `1`–`6` já é um regime de teste exploratório informal.

### Decisão 8: Renderer não cacheia `Paint`/equivalent — NanoVG é fillStyle-by-call

`SkikoRenderer` poola `Paint` porque cada `Paint` é handle nativo Skia. NanoVG funciona diferente: state lives in the nvgContext; `nvgFillColor(ctx, color)` muta state global do path corrente, depois `nvgFill(ctx)` consome. Não há objeto reutilizável.

Isso significa que `LwjglRenderer` não tem o equivalente de `sharedPaint`. Cada chamada do SPI faz `nvgBeginPath` → set state → primitive → `nvgFill`/`nvgStroke`. Custo per-call é parecido ou inferior ao Skia pooling.

**Por quê:**

- Fightar contra a API NanoVG (tentar batchar paths cross-call) não vale a complexidade. NanoVG já batcha drawcalls internamente quando o state não muda significativamente.
- `measureText` cache (equivalente ao `TextLineKey` em Skiko) pode ser adicionado depois se profiling apontar — NanoVG `nvgTextBounds` allocs `float[4]` e calcula via fontstash, o que não é zero-cost. Postergar.

## Risks / Trade-offs

- **[Risco] `-XstartOnFirstThread` esquecido em macOS crasha.** → Mitigação: resolvido no `build.gradle.kts` (Decisão 3); `./gradlew :games:demos:runLwjgl` injeta a flag automaticamente. Documentado no `CLAUDE.md` como gotcha conhecido para quem rodar manualmente via `java -cp`.

- **[Risco] `nvgCreateFont` falha (fonte default não encontrada).** → Mitigação: lista de candidatos por OS (Decisão 6), fail-fast com mensagem listando os paths tentados. Se aparecer no mundo real, adiar para sub-change que empacota fonte fallback.

- **[Risco] Diferença visual significativa entre Skiko e LWJGL nas mesmas cenas.** → Mitigação: aceito explicitamente (NanoVG faz edge-AA, Skia faz GPU AA; fontstash vs Skia text shaping). Tolerância: cena identificável como "a mesma cena", `1`–`6` rodam, overlays legíveis. Validação visual no demos lado a lado.

- **[Risco] `glfwSwapInterval(1)` (vsync) trava input lag em Mac mini com display 144Hz.** → Mitigação: aceito por enquanto; se aparecer, expor via `GameConfig.vsync` (não escopo desta change).

- **[Risco] `transformDepth` checks em `LwjglRenderer.unbind` espelham `SkikoRenderer.unbind` mas dependem de cada `pushTransform` ter `popTransform` casado.** → Mitigação: SPI já documenta o contrato; bug seria no caller, não no LwjglRenderer. Reportado via `IllegalStateException` igual Skiko.

- **[Risco] Esta change e `remove-compose-backend` co-existem em worktrees paralelas. Merge order importa.** → Mitigação: documentar no PR desta change que requer `remove-compose-backend` mergeado primeiro (já que o invariante #4 referencia ausência de Compose). Se ordem inverter, rebase resolve.

- **[Trade-off] LWJGL ~20MB de dependências (com nativos).** Aceito. Demos é jogo de validação, não distribuído como produto.

- **[Trade-off] Sem testes unitários do `LwjglRenderer`.** Aceito. GL context exige GPU; CI Docker sem GPU não roda. Validação manual no demos é o único caminho prático. Mesmo trade-off que `SkikoRenderer` (que também não tem unit tests do render).

- **[Trade-off] Duplicação de mapeamento `glfwKeyToEngineKey` vs `SkikoInput.awtKeyToEngineKey`.** Aceito. Cada backend traduz da sua fonte nativa; centralizar exigiria abstrair `int glfwKey` e `int awtKeyCode` numa enum intermediária que vazaria para `:engine`.

- **[Trade-off] `LwjglHost` toma conta do main thread do processo; testes que tentem `LwjglHost().run(...)` num thread filho crasham em macOS.** Aceito. `GameHost.run()` já é documentado como blocking; LWJGL apenas torna a blocking-ness mais estrita (não-EDT, main-thread-only).

## Migration Plan

Esta change é aditiva — não modifica comportamento existente:

1. `remove-compose-backend` aplicada primeiro (não é parte desta change). Quando ela mergear, invariante #4 fica sem cobertura — vácuo conhecido.
2. Esta change adiciona `:engine-lwjgl` + entrypoint LWJGL em `:games:demos`. Skiko não muda. Jogos shipped não mudam. Invariante #4 volta a ter cobertura.
3. Rollback: se `:engine-lwjgl` apresentar bug bloqueante após merge, basta apagar `:games:demos/.../MainLwjgl.kt` e a task `runLwjgl` no `build.gradle.kts`; `:engine-lwjgl` pode permanecer no repo sem efeito.

## Open Questions

- **Versão exata do LWJGL** (`3.3.6` no design — confirmar último estável no momento da implementação consultando https://github.com/LWJGL/lwjgl3/releases).
- **NanoVG `NVG_ANTIALIAS or NVG_STENCIL_STROKES` é a flag default certa?** Testar em demos #6 Tumbling Swarm (squares rotacionados com bordas) e ajustar se necessário. `NVG_STENCIL_STROKES` aumenta qualidade em strokes overlapping ao custo de stencil buffer extra; default-on parece seguro.
- **`measureText` retorna `Vec2(width, fontHeight)`** — mas `fontHeight` em NanoVG vem de `nvgTextMetrics(ctx, ascender, descender, lineh)`, onde `lineh = ascender - descender + extra`. Confirmar se isso bate visualmente com `Skia Font.metrics.height` no overlay de FPS (`renderDebugOverlay` usa `measureText` para posicionamento? checar). Se não bater, ajustar a conta.
- **Resize de janela**: `glfwSetWindowSizeCallback` ou ler `glfwGetWindowSize` por tick? Por tick é mais simples; callback evita query per-frame mas adiciona estado. Decidir na implementação; default ao mais simples (`glfwGetWindowSize` no início do tick) como o pseudo-código mostra.
