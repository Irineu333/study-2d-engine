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

- [x] 3.1 Add `Compose.Key.F1` / `Compose.Key.F2` entries to `ComposeInput.toEngineKey()` mapping to `Key.F1` / `Key.F2`
- [x] 3.2 Edit `GameSurface.kt`: replace the inline collider loop and inline FPS `drawText` block with a single call to `renderDebugOverlay(renderer, scene)`. Verify no `DEBUG_COLLIDER_COLOR` or `collectColliders` remains in this file
- [x] 3.3 Inside `GameSurface`, after `loop.tick(...)` and before `renderDebugOverlay`, read `input.wasKeyPressed(toggleFpsKey)` / `input.wasKeyPressed(toggleCollidersKey)` from currently-active `GameConfig` (passed via parameter) and toggle `Debug.showFps` / `Debug.colliderVisualization`. Default the parameter to `GameConfig()` so existing callers keep working
- [x] 3.4 Add `ComposeHost.kt` in `:engine-compose`: implements `GameHost`. `run(scene, config)` calls `application { Window(title = config.title, state = rememberWindowState(width = config.width.dp, height = config.height.dp), onCloseRequest = ::exitApplication) { Box(Modifier.fillMaxSize().background(Color.Black)) { GameSurface(scene, config = config, modifier = Modifier.fillMaxSize()) } } }`
- [x] 3.5 Verify `application { }` blocking semantics return from `ComposeHost.run(...)` after window close (Compose Multiplatform's `application` is already blocking)

## 4. Skiko-runtime: new module

- [x] 4.1 Confirm the exact Skiko version Compose Multiplatform 1.11.0 pulls transitively. Run `./gradlew :engine-compose:dependencies --configuration runtimeClasspath | grep skiko` and record the version
- [x] 4.2 Add `skiko` version to `gradle/libs.versions.toml`; add `[libraries] skiko-awt = { module = "org.jetbrains.skiko:skiko-awt", version.ref = "skiko" }`
- [x] 4.3 Add `:engine-skiko` to `settings.gradle.kts`
- [x] 4.4 Create `engine-skiko/build.gradle.kts`: `plugins { alias(libs.plugins.kotlinJvm) }`. Resolve `osArch` from `org.gradle.internal.os.OperatingSystem.current()` + `System.getProperty("os.arch")` (values: `macos-arm64`, `macos-x64`, `linux-x64`, `windows-x64`). `dependencies { implementation(projects.engine); api(libs.skiko.awt); runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$osArch:${libs.versions.skiko.get()}") }`
- [x] 4.5 Create `engine-skiko/src/main/kotlin/com/neoutils/engine/skiko/SkikoRenderer.kt`: implements `Renderer`. Hold a private `org.jetbrains.skia.Canvas?` via `bind()`/`unbind()`. Methods translate to Skia using `Paint().apply { color = color.toSkiaArgb(); mode = if (filled) PaintMode.FILL else PaintMode.STROKE; strokeWidth = thickness }`. Text uses `Font(Typeface.makeDefault(), size)` + `canvas.drawTextLine(TextLine.make(text, font), x, y + font.metrics.ascent.unaryMinus(), paint)`. `measureText` uses the same `Font` + `TextLine.width` / `font.metrics.height`
- [x] 4.6 Create `engine-skiko/src/main/kotlin/com/neoutils/engine/skiko/SkikoInput.kt`: implements `Input`. Same `ConcurrentHashMap.newKeySet()` shape as `ComposeInput`. Provide `onAwtKey(event: java.awt.event.KeyEvent, pressed: Boolean)`, `onAwtMouseMoved(event: java.awt.event.MouseEvent)`, `onAwtMouseButton(event: java.awt.event.MouseEvent, pressed: Boolean)`. Mapping function `Int.awtVkToEngineKey(): Key?` covers all current `Key` entries plus `F1` (`VK_F1`) and `F2` (`VK_F2`). Mouse buttons: `MouseEvent.BUTTON1 → Left`, `BUTTON2 → Middle`, `BUTTON3 → Right`
- [x] 4.7 Add unit test `SkikoInputKeyMappingTest.kt`: instantiate `SkikoInput`, synthesize AWT `KeyEvent`s for `VK_F1`/`VK_F2`/`VK_A`/`VK_UP`, drive `beginTick()` + `onAwtKey(...)`, assert `isKeyDown` / `wasKeyPressed`
- [x] 4.8 Create `engine-skiko/src/main/kotlin/com/neoutils/engine/skiko/SkikoHost.kt`: implements `GameHost`. `run(scene, config)` builds `JFrame` (title, size, `DISPOSE_ON_CLOSE`, `isVisible = true`), attaches a `SkiaLayer` with `skikoView = object : SkikoView { override fun onRender(canvas, w, h, ns) { ... } }`. Inside `onRender`: `input.beginTick()` (first frame seeds `pendingDt = 16_666_666L`, else `now - lastNanos`), `Debug.currentFps = fps.record(now)`, `scene.resize(w.toFloat(), h.toFloat())`, `renderer.bind(canvas)`, `loop.tick(pendingDt)`, then check `input.wasKeyPressed(config.toggleFpsKey)`/`input.wasKeyPressed(config.toggleCollidersKey)` and toggle, then `renderDebugOverlay(renderer, scene)`, `renderer.unbind()`, `skiaLayer.needRedraw()`. Register `KeyListener`/`MouseListener`/`MouseMotionListener` delegating to `SkikoInput`. Block via `CountDownLatch(1)` released by `WindowListener.windowClosed`. Never call `System.exit`
- [x] 4.9 Verify `./gradlew :engine-skiko:build` succeeds on macOS

## 5. Migrate `:games:pong` to Skiko

- [x] 5.1 Replace plugins in `games/pong/build.gradle.kts` with `plugins { alias(libs.plugins.kotlinJvm); application }`. Drop `composeMultiplatform`, `composeCompiler`, the `compose.desktop {}` block, and the `compose.desktop.currentOs` + `kotlinx-coroutines-swing` dependencies
- [x] 5.2 Add `application { mainClass.set("com.neoutils.engine.games.pong.MainKt") }` and `dependencies { implementation(projects.engine); implementation(projects.engineSkiko) }`
- [x] 5.3 Rewrite `games/pong/src/main/kotlin/com/neoutils/engine/games/pong/Main.kt` to: `fun main() { SkikoHost().run(PongScene(), GameConfig("Pong", 800, 600)) }`. Remove all Compose imports
- [x] 5.4 Verify `./gradlew :games:pong:dependencies | grep compose` returns empty
- [x] 5.5 Verify `./gradlew :games:pong:run` launches the game

## 6. Migrate `:games:demos` to Skiko

- [x] 6.1 Same plugin/dependency surgery in `games/demos/build.gradle.kts` as Pong
- [x] 6.2 Rewrite `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/Main.kt`: `fun main() { SkikoHost().run(DemoSwitcherScene(), GameConfig("engine-consistency demos", 800, 600)) }`
- [x] 6.3 Verify `./gradlew :games:demos:dependencies | grep compose` returns empty
- [x] 6.4 Verify `./gradlew :games:demos:run` launches the demos

## 7. Adapt `:games:tictactoe` to use ComposeHost (keep Compose backend)

- [x] 7.1 Edit `games/tictactoe/src/main/kotlin/com/neoutils/engine/games/tictactoe/Main.kt`: replace the `application { Window { ... GameSurface(...) } }` body with `fun main() { ComposeHost().run(TicTacToeScene(), GameConfig("Tic Tac Toe", 600, 600)) }`. Remove the F1/F2 `onKeyEvent` handler (now lives in the host)
- [x] 7.2 Verify `./gradlew :games:tictactoe:dependencies | grep skiko` does NOT show `:engine-skiko` (Tic Tac Toe stays Compose-only as the sentinel)
- [x] 7.3 Verify `./gradlew :games:tictactoe:run` launches the game and F1/F2 still toggle overlays

## 8. CLAUDE.md updates

- [x] 8.1 Reword invariant 4: "`Renderer`, `Input` and `GameHost` são SPIs. Skiko é o backend padrão (`:engine-skiko`); Compose é o segundo backend (`:engine-compose`). Jogos novos devem usar Skiko por default."
- [x] 8.2 Update the "Module Structure & How to Run" section: add `:engine-skiko` between `:engine-compose` and `:games:pong`; explain that Pong and Demos run on Skiko, Tic Tac Toe on Compose
- [x] 8.3 Update the F1/F2 documentation in each game's "Durante o jogo" block to clarify the toggles now come from `GameHost`, not from a window-level `onKeyEvent` handler
- [x] 8.4 Add a new row to the Roadmap table: `add-skiko-runtime | Archived | Runtime Skiko puro (sem Compose) como backend padrão; ComposeHost/SkikoHost implementando o novo GameHost SPI; overlay de debug unificado.`

## 9. Manual validation (macOS)

- [x] 9.1 Pong on Skiko: launch via `./gradlew :games:pong:run`. Verify (a) W/S move left paddle, (b) AI plays the right paddle, (c) ball collides with paddles and walls, (d) F1 toggles FPS overlay, (e) F2 toggles collider outlines, (f) resizing the window keeps the game responsive
- [x] 9.2 Demos on Skiko: launch via `./gradlew :games:demos:run`. Verify (a) keys 1/2/3 switch demos, (b) Spawner demo: clicking adds balls, trap removes them, (c) F2 shows colliders rendered by host (not by scene), (d) F1 shows FPS counter
- [x] 9.3 Tic Tac Toe on Compose: launch via `./gradlew :games:tictactoe:run`. Verify (a) left click on empty cells alternates X / O, (b) endgame announcement appears, (c) next click after end-of-game restarts (only restarts, doesn't play), (d) F1 toggles FPS overlay, (e) F2 toggles collider overlay (still empty since the game has no colliders)
- [x] 9.4 No-stutter sanity check: run Pong for 60 seconds, watch FPS overlay stay above 50

## 10. Final cleanup

- [x] 10.1 Run `./gradlew build` from the repo root; all tests pass and all modules compile
- [x] 10.2 Run `openspec validate add-skiko-runtime --strict` and resolve any reported issues

## 11. Validation regressions found in step 9.1 (Pong on Skiko)

- [x] 11.1 `SkikoRenderer` text uses an empty default typeface (`Font(null, size)`) whose metrics are zeroed, so `position.y` is treated as baseline and `Score` (48 px, `y = 24`) renders with its top at `y ≈ -24` — visible glyphs cross the top edge of the window. Resolve the default typeface by walking a prioritized list of well-known system families (`SF Pro Display`, `Helvetica Neue`, `Helvetica`, `Arial`), falling back to the first family enumerated by `FontMgr.default`, and only then to `Typeface.makeEmpty()`. With a real typeface, `font.metrics.ascent` returns the real negative value and the top-anchored coordinate the `Renderer` SPI promises is honored.
- [x] 11.2 `SkikoHost` never clears the canvas, so the OS-provided initial buffer (white) bleeds through, dropping contrast against the white scene content (a regression versus `ComposeHost`, where the wrapping `Box(Modifier.background(Color.Black))` paints black before `GameSurface` draws). Add `renderer.clear(Color.BLACK)` at the start of each frame in `SkikoHost.onRender` — after `renderer.bind(canvas)` and before `loop.tick(...)` — so background paint is the host's responsibility on both backends.

## 12. SkikoRenderer micro-optimization for step 9.4

Skiko-on-Pong measured 53-55 fps while Compose-on-Pong stayed at 59-60 (still above the 9.4 bar of 50 fps, but inconsistent enough to be noticeable). Both backends share the engine, scene, and game loop — the gap traces to per-call allocations in `SkikoRenderer`. Address the two hottest:

- [x] 12.1 Reuse a single `org.jetbrains.skia.Paint` per renderer instead of allocating one in every `drawRect`/`drawCircle`/`drawLine`/`drawText`. `Paint` is mutable; configure `color`/`mode`/`strokeWidth`/`isAntiAlias` before each call. Removes 30-40 native-handle allocations per frame on the Pong scene.
- [x] 12.2 Cache `TextLine` instances per `(text, size)` pair in `SkikoRenderer`. `TextLine.make` runs the Skia shaper each call and creates a native-handle `Managed`; both the score (changes only on goal) and the FPS overlay (changes once per second at most) are stable enough that a `HashMap<TextLineKey, TextLine>` lookup avoids redundant shaping. Cache is bounded by gameplay (Pong scores rarely exceed double digits) so no eviction is needed for this change.
- [x] 12.3 Re-run `:games:pong:run` with F1 enabled and confirm the overlay reads ≥58 fps (i.e. comparable to Compose). If it does not, document the remaining gap and stop here — further chasing requires profiling, which is out of scope for this change.
