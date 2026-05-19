## Context

A engine roda hoje em um único backend: `:engine-compose` provê `ComposeRenderer`, `ComposeInput` e o composable `GameSurface`, que internamente cria o `GameLoop` e dirige o pulso via `withFrameNanos`. Os `Main.kt` de cada jogo importam `compose.desktop` direto e tratam `F1`/`F2` no `onKeyEvent` da janela usando `Compose.Key.F1` — uma válvula de escape que existe porque o enum `Key` da engine não inclui F-keys.

A SPI `Renderer`/`Input` foi escrita prevendo múltiplos backends (invariante 4 do `CLAUDE.md`), mas nunca foi exercida por um segundo cliente. O `GameSurface` é, na prática, a tradução implícita de "host de execução do jogo" — um conceito que não tem nome no `:engine` e cuja forma é específica de Compose (composable + modifier + foco + Canvas).

Esta change introduz Skiko puro (sem Compose, via `SkiaLayer + JFrame`) como segundo backend, torna-o o padrão, e usa a fricção da migração pra promover esse conceito a SPI explícita.

Restrições e premissas:

- **JVM Desktop apenas.** Multiplataforma não-JVM segue fora de escopo.
- **Validação manual em macOS.** Skiko-awt-runtime resolve `macos-arm64`/`macos-x64`/`linux-x64`/`windows-x64` por classifier, mas a verificação visual é só na máquina do autor.
- **Compose continua vivo.** Velha (jogo discreto, hit-test, drawText/drawLine) é a sentinela do segundo backend. Pong e Demos migram pra Skiko.
- **Versão do Skiko alinhada com Compose Multiplatform 1.11.0.** Compose já traz Skiko transitivamente; usar versão distinta no `:engine-skiko` cria dois classpaths Skiko quando o módulo `:games:tictactoe` é resolvido junto com `:engine-skiko` em build agregada (não acontece hoje, mas pode acontecer em CI futura).

## Goals / Non-Goals

**Goals:**

- `GameHost` como SPI de primeira classe em `:engine`: um nome para o conceito que hoje vive só no `GameSurface`.
- Dois backends funcionais que implementam `GameHost`: `ComposeHost` (segundo backend, wrap do `GameSurface` existente) e `SkikoHost` (novo, padrão).
- `:engine-skiko` como o **único** módulo além de `:engine-compose` autorizado a depender de runtime gráfico específico.
- Toggles de debug (`F1`/`F2`) deixam de ser preocupação do `Main.kt` de cada jogo; viram parte do contrato `GameHost`, configurável via `GameConfig`.
- Overlay de debug (FPS + colliders) unificado em `:engine` numa função pura, chamada por ambos os hosts.
- Pong e Demos rodam em Skiko sem nenhuma dependência Compose no classpath dos seus módulos.

**Non-Goals:**

- `SkikoWindow` (host Skiko sem AWT). Documentado como evolução; rough edges em algumas plataformas.
- Backends adicionais (LWJGL, OpenGL puro). Skiko + Compose bastam pra exercer a SPI.
- Troca de backend em runtime / `ServiceLoader`. Cada jogo escolhe estaticamente no `Main.kt` + build.gradle.
- Refator do enum `Key` além de adicionar `F1`/`F2`.
- HiDPI explícito na SPI. Coordenadas continuam "pixels lógicos a contentScale 1.0"; cada backend escala internamente; comportamento em Retina pode divergir visualmente — aceitável.
- Validação em Linux e Windows. Pode funcionar (Skiko resolve por classifier), mas não é parte do critério de aceitação desta change.

## Decisions

### Decisão 1: `GameHost` como interface no `:engine`, com `GameConfig` data class

```kotlin
// :engine/com/neoutils/engine/runtime/GameHost.kt
interface GameHost {
    /** Runs the game blocking until the host window closes. */
    fun run(scene: Scene, config: GameConfig = GameConfig())
}

data class GameConfig(
    val title: String = "Game",
    val width: Int = 800,
    val height: Int = 600,
    val toggleFpsKey: Key = Key.F1,
    val toggleCollidersKey: Key = Key.F2,
)
```

**Por quê:**
- Promove o conceito "host de execução do jogo" a cidadão de primeira classe; hoje ele só existe no shape do `GameSurface` composable.
- `run()` blocking é a expectativa natural de `fun main()`. Compose já é blocking via `application {}`; Skiko/JFrame usa `CountDownLatch` em `WindowListener.windowClosed` pra emular.
- `GameConfig` carrega o que era responsabilidade do `Main.kt` de cada jogo (título, tamanho, F1/F2). Defaults razoáveis pra reduzir cerimônia.

**Alternativas consideradas:**
- Opção A do explore: cada backend expõe seu launcher próprio (`runSkiko(scene, config)`, `runCompose(scene, config)`). Mais simples, mas não dá nome ao conceito. Pra projeto didático perde a oportunidade pedagógica.
- Opção C do explore: resolução automática por classpath (`ServiceLoader`). Magia. Esconde a escolha de backend justamente quando ela está sendo destacada como decisão arquitetural.

### Decisão 2: Skiko via `SkiaLayer + JFrame` (Swing-based), não `SkikoWindow`

`SkikoHost` cria um `JFrame`, adiciona um `org.jetbrains.skiko.SkiaLayer` ao seu `contentPane`, registra `KeyListener` / `MouseListener` / `MouseMotionListener` / `ComponentListener` no `JFrame`, e implementa `SkikoView.onRender(canvas, w, h, nanoTime)` chamando `loop.tick(...)`.

```
JFrame
 └─ SkiaLayer (skikoView = anonymous SkikoView)
      └─ onRender(canvas, w, h, ns)
           ├─ input.beginTick()
           ├─ scene.resize(w, h)
           ├─ renderer.bind(canvas)
           ├─ loop.tick(dtNs)
           ├─ renderDebugOverlay(renderer, scene)
           ├─ renderer.unbind()
           └─ skiaLayer.needRedraw()    ← drive next frame
```

**Por quê:**
- `SkiaLayer + JFrame` é a forma estável e amplamente usada de embutir Skiko em desktop. Tem rendering OpenGL/Metal/D3D automático conforme OS.
- `WindowListener` dá um gancho natural pro shutdown blocking via `CountDownLatch`.
- AWT é blob conhecido — debugging, foco, resize comportam previsivelmente.

**Alternativas consideradas:**
- `SkikoWindow` (sem AWT, mais novo): tira a dependência Swing/AWT. Atrativo em princípio, mas tem rough edges em algumas plataformas (foco, resize, ciclo de vida da janela) e API menos estabilizada. **Documentado como evolução futura** — quando estabilizar, vira uma sub-change focada só em trocar o transporte.
- Swing `JFrame` com `Canvas` AWT direto + Skia via JNI manual: reinventar Skiko. Não.

### Decisão 3: Overlay de debug unificado no `:engine`

```kotlin
// :engine/com/neoutils/engine/dx/DebugOverlay.kt
fun renderDebugOverlay(renderer: Renderer, scene: Scene) {
    if (Debug.colliderVisualization) {
        for (collider in collectColliders(scene)) {
            renderer.drawRect(collider.bounds(), DEBUG_COLLIDER_COLOR, filled = false)
        }
    }
    if (Debug.showFps) {
        renderer.drawText("fps ${Debug.currentFps.toInt()}", Vec2(8f, 24f), size = 18f, color = Color.WHITE)
    }
}
```

Cada host (Compose e Skiko) chama essa função depois de `loop.tick(...)` e antes de `renderer.unbind()`. O update de `Debug.currentFps` continua sendo responsabilidade do host (precisa de `nanoTime` da plataforma), só o desenho é unificado.

**Por quê:**
- Hoje o desenho de overlay vive em `GameSurface.kt`. Replicá-lo no `SkikoHost` seria duplicação imediata; ao mover, o segundo backend nasce limpo.
- Sem violar a invariante "core scene-graph traversal não desenha overlay": `renderDebugOverlay` é chamado **fora** de `Scene.render`, pelo host.
- O cenário "Core scene-graph traversal does not draw collider bounds" da spec `dx-tooling` continua válido.

**Alternativas consideradas:**
- Manter duplicado em cada host: simples, mas cada melhoria futura no overlay teria que mexer em N lugares.
- Mover para um `Renderer.drawDebugOverlay(scene)` na própria SPI: vazaria o conceito `Scene` para a SPI de render. Não.

### Decisão 4: `Key.F1`/`Key.F2` adicionados ao enum + toggles no host

O enum `Key` ganha `F1` e `F2`. Cada backend (`ComposeInput`, `SkikoInput`) adiciona as duas linhas em seu mapeamento.

`GameHost` (em ambas as implementações) lê `config.toggleFpsKey` e `config.toggleCollidersKey` a cada tick e alterna `Debug.showFps`/`Debug.colliderVisualization` quando observa `wasKeyPressed` na chave configurada.

**Por quê:**
- Hoje o `Main.kt` de cada jogo trata F1/F2 fora do `GameSurface`, usando `Compose.Key.F1` na janela. Skiko não tem essa válvula — toggles têm que viver dentro do host. Pra simetria, Compose também passa a tratá-los dentro do host.
- Limpa significativamente o `Main.kt` dos jogos (de ~20 linhas de boilerplate Compose pra ~5 linhas).
- `Key.F1`/`F2` no enum não é breaking para consumidores existentes (apenas adiciona entries).

**Alternativas consideradas:**
- Manter F1/F2 nos `Main.kt` dos jogos via callback genérico (`GameConfig.extraKeyHandler`): mais flexível, mas pra dois toggles é overengineering. Quando precisar de mais, aí sim.

### Decisão 5: `:games:pong` e `:games:demos` perdem Compose completamente; `:games:tictactoe` permanece

Os dois jogos migrados:
- Trocam plugins: `composeMultiplatform` + `composeCompiler` + `compose.desktop` → `application` puro do Gradle.
- Removem `compose.desktop.currentOs`, `kotlinx-coroutines-swing` das `dependencies { }`.
- Dependem apenas de `:engine` + `:engine-skiko` (o último traz Skiko transitivamente).
- `Main.kt` vira `fun main() { SkikoHost().run(MyScene(), GameConfig("Title", 800, 600)) }`.

`:games:tictactoe` permanece em Compose com `ComposeHost` (em vez de `GameSurface` direto), exercitando o segundo backend ponta a ponta.

**Por quê:**
- A invariante "Compose vazou se um módulo migrado ainda depender de Compose" é verificável mecanicamente no `build.gradle.kts`.
- Reduz tamanho do classpath dos dois jogos significativamente (Compose runtime + ui + foundation são vários MB).
- Velha como sentinela: discreta o suficiente pra não exigir o pulso intenso de Skiko, complexa o suficiente pra exercitar a SPI (`drawText`, `drawLine`, `drawRect` outlined, `Input.wasMouseClicked` com hit-test).

**Alternativas consideradas:**
- Migrar os três de uma vez: maximiza estresse mas zera cobertura do Compose. Adiar Velha é mais barato e mais informativo.
- Migrar só Pong, deixar Demos em Compose: Demos é o jogo que exercita mutação durante traversal e spawn por clique — vale ter ele rodando no novo backend pra ver se a SPI sustenta.

### Decisão 6: Skiko alinhado com Compose Multiplatform 1.11.0

O `libs.versions.toml` ganha:

```toml
skiko = "0.9.6"   # versão exata que Compose 1.11.0 usa transitivamente — checar antes de implementar

[libraries]
skiko-awt = { module = "org.jetbrains.skiko:skiko-awt", version.ref = "skiko" }
```

O `build.gradle.kts` de `:engine-skiko` resolve o classifier nativo (`-runtime-macos-arm64`, etc.) com base em `System.getProperty("os.name")` e `System.getProperty("os.arch")`.

```kotlin
val osArch = when {
    Os.isFamily(Os.FAMILY_MAC) && System.getProperty("os.arch") == "aarch64" -> "macos-arm64"
    Os.isFamily(Os.FAMILY_MAC) -> "macos-x64"
    Os.isFamily(Os.FAMILY_WINDOWS) -> "windows-x64"
    else -> "linux-x64"
}

dependencies {
    api(libs.skiko.awt)
    implementation("org.jetbrains.skiko:skiko-awt-runtime-$osArch:${libs.versions.skiko.get()}")
    implementation(projects.engine)
}
```

**Por quê:**
- Skiko-awt-runtime vem com binários nativos por classifier. Não tem `-all` empacotado por padrão.
- Alinhar com a versão do Compose Multiplatform evita dois Skikos no classpath quando um build futuro combinar `:engine-skiko` e `:engine-compose` no mesmo módulo (ex.: testes que carregam ambos).

**Alternativas consideradas:**
- Bundle de runtimes (`-all`): aumenta tamanho do classpath significativamente, sem ganho real pra projeto local.
- Versão Skiko livre: corre risco de duplicação silenciosa.

### Decisão 7: `SkikoInput` mantém `ConcurrentHashMap.newKeySet()` por simetria

AWT entrega eventos em EDT (Event Dispatch Thread); `SkikoView.onRender` também roda em EDT. Tudo single-threaded — `HashSet` simples bastaria. Ainda assim, `SkikoInput` usa `ConcurrentHashMap.newKeySet()` como `ComposeInput`.

**Por quê:**
- Simetria entre backends reduz carga cognitiva ao ler/comparar os dois.
- Overhead é desprezível para os ~5 keys ativos em qualquer momento.
- Decisão revisável quando aparecer um terceiro backend (talvez padronizado de forma diferente).

## Risks / Trade-offs

- **[Risco] Versão Skiko desalinhada com Compose causa duplo classloader silencioso.** → Mitigação: confirmar versão exata antes de fechar `libs.versions.toml` (verificar `compose-1.11.0` POM); manter número de versão como única fonte de verdade.

- **[Risco] HiDPI diverge visualmente entre Compose e Skiko.** → Mitigação: documentar contrato "pixels lógicos a contentScale 1.0" no `design.md`; cada backend escala internamente; aceitamos diferença visual em Retina. Pong/Demos visualmente verificáveis na máquina do autor.

- **[Risco] `JFrame.requestFocusInWindow()` não pega foco automaticamente em macOS.** → Mitigação: chamar `frame.requestFocusInWindow()` depois de `frame.isVisible = true`; testar manualmente em macOS. Se falhar, registrar como `Open Question` e investigar.

- **[Risco] Toggles via host (consumo de `wasKeyPressed`) competem com nós que também escutam essa tecla.** → Mitigação: nada hoje usa F1/F2 dentro dos jogos; documentar que teclas de toggle do host são reservadas. Se aparecer conflito, `GameConfig` permite trocar para outra tecla.

- **[Risco] `CountDownLatch` em `windowClosed` mantém JVM viva por mais tempo se outro thread non-daemon não terminar.** → Mitigação: `JFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)` + `latch.countDown()` em `windowClosed`. Não chamar `System.exit` (libera testes futuros).

- **[Trade-off] `ComposeHost` é wrap fino sobre `GameSurface`, não substituto.** Aceito. `GameSurface` permanece exportado para quem precisar embutir a engine numa árvore Compose maior. O caminho "novo" recomendado é via host.

- **[Trade-off] Pong/Demos perdem `nativeDistributions` (instalador `.dmg`/`.msi`/`.deb`) ao deixar de usar `compose.desktop`.** Aceito. Distribuição não é foco; quando precisar voltar, `jpackage` ou `gradle-jpackage` cobrem.

- **[Trade-off] Duplicação de tabela de mapeamento de keys/mouse (Compose-side e Skiko-side).** Aceito. Cada backend traduz da sua fonte nativa para o enum `Key`. Centralizar exigiria tipos abstratos de event nativo no `:engine`, o que vazaria conceitos de backend.

## Open Questions

- **Versão exata do Skiko que Compose 1.11.0 traz.** Será confirmada na primeira tarefa (`./gradlew :engine-compose:dependencies | grep skiko`).
- **Foco inicial em macOS via `SkiaLayer`.** Se manifestar problema, decidir entre chamar `frame.requestFocus()` ou wrap manual em `SwingUtilities.invokeLater {}`.
- **Onde mora `SkikoHost.dt` accounting.** Se replicar a lógica de `lastNanos == 0L → 16_666_666L` do `GameSurface` ou se já dá pra extrair pro `GameLoop` (refator separado).
