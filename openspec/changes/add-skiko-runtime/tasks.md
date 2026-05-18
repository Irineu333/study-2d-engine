## 1. Engine-core: GameHost, GameConfig and toggle keys

- [x] 1.1 Add `Key.F1` and `Key.F2` to `com.neoutils.engine.input.Key`
- [x] 1.2 Create package `com.neoutils.engine.runtime` and add `GameHost.kt` declaring `interface GameHost { fun run(scene: Scene, config: GameConfig = GameConfig()) }`
- [x] 1.3 Add `GameConfig.kt` in the same package with `title: String = "Game"`, `width: Int = 800`, `height: Int = 600`, `toggleFpsKey: Key = Key.F1`, `toggleCollidersKey: Key = Key.F2`; data class
- [x] 1.4 Verify no import in `runtime/*.kt` begins with `org.jetbrains.compose.*`, `androidx.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, or `javax.swing.*`

## 2. DX: unified debug overlay utility

- [x] 2.1 Add `renderDebugOverlay(renderer: Renderer, scene: Scene)` in `com.neoutils.engine.dx.DebugOverlay.kt`: consults `Debug.colliderVisualization` (then `collectColliders(scene)` + `renderer.drawRect(_, _, filled = false)`) and `Debug.showFps` (then `renderer.drawText("fps ${Debug.currentFps.toInt()}", Vec2(8f, 24f), 18f, Color.WHITE)`)
- [x] 2.2 Move the `DEBUG_COLLIDER_COLOR` constant from `GameSurface.kt` into `DebugOverlay.kt` (same RGBA: green 0.8 alpha)
- [x] 2.3 Add unit test `DebugOverlayTest.kt` in `:engine/src/test/`: a `RecordingRenderer : Renderer` collects calls; assert (a) both flags off → zero calls; (b) only FPS on → one `drawText`; (c) only colliders on → one `drawRect(_, _, filled = false)` per collider; (d) both on → both

## 3. Compose-runtime: ComposeHost and overlay delegation

- [ ] 3.1 Add `Compose.Key.F1` / `Compose.Key.F2` entries to `ComposeInput.toEngineKey()` mapping to `Key.F1` / `Key.F2`
- [ ] 3.2 Edit `GameSurface.kt`: replace the inline collider loop and inline FPS `drawText` block with a single call to `renderDebugOverlay(renderer, scene)`. Verify no `DEBUG_COLLIDER_COLOR` or `collectColliders` remains in this file
- [ ] 3.3 Inside `GameSurface`, after `loop.tick(...)` and before `renderDebugOverlay`, read `input.wasKeyPressed(toggleFpsKey)` / `input.wasKeyPressed(toggleCollidersKey)` from currently-active `GameConfig` (passed via parameter) and toggle `Debug.showFps` / `Debug.colliderVisualization`. Default the parameter to `GameConfig()` so existing callers keep working
- [ ] 3.4 Add `ComposeHost.kt` in `:engine-compose`: implements `GameHost`. `run(scene, config)` calls `application { Window(title = config.title, state = rememberWindowState(width = config.width.dp, height = config.height.dp), onCloseRequest = ::exitApplication) { Box(Modifier.fillMaxSize().background(Color.Black)) { GameSurface(scene, config = config, modifier = Modifier.fillMaxSize()) } } }`
- [ ] 3.5 Verify `application { }` blocking semantics return from `ComposeHost.run(...)` after window close (Compose Multiplatform's `application` is already blocking)

## 4. Skiko-runtime: new module

- [ ] 4.1 Confirm the exact Skiko version Compose Multiplatform 1.11.0 pulls transitively. Run `./gradlew :engine-compose:dependencies --configuration runtimeClasspath | grep skiko` and record the version
- [ ] 4.2 Add `skiko` version to `gradle/libs.versions.toml`; add `[libraries] skiko-awt = { module = "org.jetbrains.skiko:skiko-awt", version.ref = "skiko" }`
- [ ] 4.3 Add `:engine-skiko` to `settings.gradle.kts`
- [ ] 4.4 Create `engine-skiko/build.gradle.kts`: `plugins { alias(libs.plugins.kotlinJvm) }`. Resolve `osArch` from `org.gradle.internal.os.OperatingSystem.current()` + `System.getProperty("os.arch")` (values: `macos-arm64`, `macos-x64`, `linux-x64`, `windows-x64`). `dependencies { implementation(projects.engine); api(libs.skiko.awt); runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$osArch:${libs.versions.skiko.get()}") }`
- [ ] 4.5 Create `engine-skiko/src/main/kotlin/com/neoutils/engine/skiko/SkikoRenderer.kt`: implements `Renderer`. Hold a private `org.jetbrains.skia.Canvas?` via `bind()`/`unbind()`. Methods translate to Skia using `Paint().apply { color = color.toSkiaArgb(); mode = if (filled) PaintMode.FILL else PaintMode.STROKE; strokeWidth = thickness }`. Text uses `Font(Typeface.makeDefault(), size)` + `canvas.drawTextLine(TextLine.make(text, font), x, y + font.metrics.ascent.unaryMinus(), paint)`. `measureText` uses the same `Font` + `TextLine.width` / `font.metrics.height`
- [ ] 4.6 Create `engine-skiko/src/main/kotlin/com/neoutils/engine/skiko/SkikoInput.kt`: implements `Input`. Same `ConcurrentHashMap.newKeySet()` shape as `ComposeInput`. Provide `onAwtKey(event: java.awt.event.KeyEvent, pressed: Boolean)`, `onAwtMouseMoved(event: java.awt.event.MouseEvent)`, `onAwtMouseButton(event: java.awt.event.MouseEvent, pressed: Boolean)`. Mapping function `Int.awtVkToEngineKey(): Key?` covers all current `Key` entries plus `F1` (`VK_F1`) and `F2` (`VK_F2`). Mouse buttons: `MouseEvent.BUTTON1 → Left`, `BUTTON2 → Middle`, `BUTTON3 → Right`
- [ ] 4.7 Add unit test `SkikoInputKeyMappingTest.kt`: instantiate `SkikoInput`, synthesize AWT `KeyEvent`s for `VK_F1`/`VK_F2`/`VK_A`/`VK_UP`, drive `beginTick()` + `onAwtKey(...)`, assert `isKeyDown` / `wasKeyPressed`
- [ ] 4.8 Create `engine-skiko/src/main/kotlin/com/neoutils/engine/skiko/SkikoHost.kt`: implements `GameHost`. `run(scene, config)` builds `JFrame` (title, size, `DISPOSE_ON_CLOSE`, `isVisible = true`), attaches a `SkiaLayer` with `skikoView = object : SkikoView { override fun onRender(canvas, w, h, ns) { ... } }`. Inside `onRender`: `input.beginTick()` (first frame seeds `pendingDt = 16_666_666L`, else `now - lastNanos`), `Debug.currentFps = fps.record(now)`, `scene.resize(w.toFloat(), h.toFloat())`, `renderer.bind(canvas)`, `loop.tick(pendingDt)`, then check `input.wasKeyPressed(config.toggleFpsKey)`/`input.wasKeyPressed(config.toggleCollidersKey)` and toggle, then `renderDebugOverlay(renderer, scene)`, `renderer.unbind()`, `skiaLayer.needRedraw()`. Register `KeyListener`/`MouseListener`/`MouseMotionListener` delegating to `SkikoInput`. Block via `CountDownLatch(1)` released by `WindowListener.windowClosed`. Never call `System.exit`
- [ ] 4.9 Verify `./gradlew :engine-skiko:build` succeeds on macOS

## 5. Migrate `:games:pong` to Skiko

- [ ] 5.1 Replace plugins in `games/pong/build.gradle.kts` with `plugins { alias(libs.plugins.kotlinJvm); application }`. Drop `composeMultiplatform`, `composeCompiler`, the `compose.desktop {}` block, and the `compose.desktop.currentOs` + `kotlinx-coroutines-swing` dependencies
- [ ] 5.2 Add `application { mainClass.set("com.neoutils.engine.games.pong.MainKt") }` and `dependencies { implementation(projects.engine); implementation(projects.engineSkiko) }`
- [ ] 5.3 Rewrite `games/pong/src/main/kotlin/com/neoutils/engine/games/pong/Main.kt` to: `fun main() { SkikoHost().run(PongScene(), GameConfig("Pong", 800, 600)) }`. Remove all Compose imports
- [ ] 5.4 Verify `./gradlew :games:pong:dependencies | grep compose` returns empty
- [ ] 5.5 Verify `./gradlew :games:pong:run` launches the game

## 6. Migrate `:games:demos` to Skiko

- [ ] 6.1 Same plugin/dependency surgery in `games/demos/build.gradle.kts` as Pong
- [ ] 6.2 Rewrite `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/Main.kt`: `fun main() { SkikoHost().run(DemoSwitcherScene(), GameConfig("engine-consistency demos", 800, 600)) }`
- [ ] 6.3 Verify `./gradlew :games:demos:dependencies | grep compose` returns empty
- [ ] 6.4 Verify `./gradlew :games:demos:run` launches the demos

## 7. Adapt `:games:tictactoe` to use ComposeHost (keep Compose backend)

- [ ] 7.1 Edit `games/tictactoe/src/main/kotlin/com/neoutils/engine/games/tictactoe/Main.kt`: replace the `application { Window { ... GameSurface(...) } }` body with `fun main() { ComposeHost().run(TicTacToeScene(), GameConfig("Tic Tac Toe", 600, 600)) }`. Remove the F1/F2 `onKeyEvent` handler (now lives in the host)
- [ ] 7.2 Verify `./gradlew :games:tictactoe:dependencies | grep skiko` does NOT show `:engine-skiko` (Tic Tac Toe stays Compose-only as the sentinel)
- [ ] 7.3 Verify `./gradlew :games:tictactoe:run` launches the game and F1/F2 still toggle overlays

## 8. CLAUDE.md updates

- [ ] 8.1 Reword invariant 4: "`Renderer`, `Input` and `GameHost` são SPIs. Skiko é o backend padrão (`:engine-skiko`); Compose é o segundo backend (`:engine-compose`). Jogos novos devem usar Skiko por default."
- [ ] 8.2 Update the "Module Structure & How to Run" section: add `:engine-skiko` between `:engine-compose` and `:games:pong`; explain that Pong and Demos run on Skiko, Tic Tac Toe on Compose
- [ ] 8.3 Update the F1/F2 documentation in each game's "Durante o jogo" block to clarify the toggles now come from `GameHost`, not from a window-level `onKeyEvent` handler
- [ ] 8.4 Add a new row to the Roadmap table: `add-skiko-runtime | Archived | Runtime Skiko puro (sem Compose) como backend padrão; ComposeHost/SkikoHost implementando o novo GameHost SPI; overlay de debug unificado.`

## 9. Manual validation (macOS)

- [ ] 9.1 Pong on Skiko: launch via `./gradlew :games:pong:run`. Verify (a) W/S move left paddle, (b) AI plays the right paddle, (c) ball collides with paddles and walls, (d) F1 toggles FPS overlay, (e) F2 toggles collider outlines, (f) resizing the window keeps the game responsive
- [ ] 9.2 Demos on Skiko: launch via `./gradlew :games:demos:run`. Verify (a) keys 1/2/3 switch demos, (b) Spawner demo: clicking adds balls, trap removes them, (c) F2 shows colliders rendered by host (not by scene), (d) F1 shows FPS counter
- [ ] 9.3 Tic Tac Toe on Compose: launch via `./gradlew :games:tictactoe:run`. Verify (a) left click on empty cells alternates X / O, (b) endgame announcement appears, (c) next click after end-of-game restarts (only restarts, doesn't play), (d) F1 toggles FPS overlay, (e) F2 toggles collider overlay (still empty since the game has no colliders)
- [ ] 9.4 No-stutter sanity check: run Pong for 60 seconds, watch FPS overlay stay above 50

## 10. Final cleanup

- [ ] 10.1 Run `./gradlew build` from the repo root; all tests pass and all modules compile
- [ ] 10.2 Run `openspec validate add-skiko-runtime --strict` and resolve any reported issues
