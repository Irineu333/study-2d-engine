## 1. Engine SPI (E0a)

- [ ] 1.1 Add `ScriptHost` interface in `:engine` under `com.neoutils.engine.scripting` (or analogous package) with `compile(path: String): KClass<out Node>`, `factoryFor(path: String): () -> Node`, and `pathFor(klass: KClass<out Node>): String?`.
- [ ] 1.2 Add `ScriptHosts` object in `:engine` with `register(host: ScriptHost)`, `current(): ScriptHost?`, and internal `clear()` for tests.
- [ ] 1.3 Verify by inspection that the file declaring `ScriptHost` imports only `com.neoutils.engine.*`, `kotlin.*`, and `kotlin.reflect.*`.
- [ ] 1.4 Add unit tests covering `ScriptHosts.register`/`current`/`clear` lifecycle.

## 2. Scripting backend module (E0b)

- [ ] 2.1 Create the `:engine-scripting` Gradle module with `build.gradle.kts` depending on `:engine` and `org.jetbrains.kotlin:kotlin-scripting-jvm-host` (pinned version aligned with the project's Kotlin version).
- [ ] 2.2 Register the module in `settings.gradle.kts`.
- [ ] 2.3 Implement `KotlinScriptingHost(manifest: List<String>, cacheDir: File)` implementing `ScriptHost`.
- [ ] 2.4 Define a custom `ScriptDefinition` for the `.nengine.kts` extension with default imports for `com.neoutils.engine.scene.*`, `math.*`, `render.*`, `input.*`, `serialization.*`, `physics.*`.
- [ ] 2.5 Implement compilation that fails fast when the script contains zero or more than one top-level class extending `Node`.
- [ ] 2.6 Implement disk cache keyed by `sha256(scriptSource) + engineScriptingVersion`, persisted under the configured `cacheDir`.
- [ ] 2.7 Implement manifest-ordered compilation: pre-compile every script listed in `manifest` (in order) so each later script sees earlier ones on its classpath.
- [ ] 2.8 Implement reverse mapping `pathFor(klass)`, populated as each script compiles.
- [ ] 2.9 Add a smoke test that compiles a trivial `Hello : Node()` script, verifies the returned `KClass`, instantiates it, and asserts it is assignable to `Node`.
- [ ] 2.10 Add tests covering: missing script throws, syntax error throws, zero-class throws, two-class throws, non-Node top-level throws, cache hit avoids recompile, cache deletion forces recompile, manifest order makes earlier scripts visible to later ones.

## 3. SceneLoader routing (E1)

- [ ] 3.1 Modify `SceneLoader.entryToNode` to inspect `entry.type` and route to `ScriptHosts.current()?.factoryFor(type)` when `type.endsWith(".kts")`, otherwise keep the current `NodeRegistry.create(type)` path.
- [ ] 3.2 When `type` ends with `.kts` and no `ScriptHost` is registered, throw an exception whose message names the offending path and explains that no `ScriptHost` is registered.
- [ ] 3.3 Modify `SceneLoader.nodeToEntry` to consult `ScriptHosts.current()?.pathFor(node::class)` first, falling back to `node::class.qualifiedName` only when the reverse lookup returns `null`.
- [ ] 3.4 Add `SceneLoaderTest` cases that exercise both routing paths using a mock `ScriptHost` that returns a hand-rolled class for a `.kts` path.
- [ ] 3.5 Add `SceneLoaderTest` cases for the save side: a scene whose root was produced by a mock `ScriptHost` saves with the script path as `type`, not the runtime FQN.
- [ ] 3.6 **GATE E1**: Run `./gradlew :engine:test :engine-scripting:test`. Run `./gradlew :games:pong:run` and manually verify Pong is unchanged. Manual verification by the user before proceeding.

## 4. Pong wiring (E2-prep)

- [ ] 4.1 Add `:engine-scripting` as a dependency of `:games:pong`.
- [ ] 4.2 In `:games:pong/Main.kt`, instantiate `KotlinScriptingHost` with an initially empty manifest and `cacheDir = File("build/scripting-cache").absoluteFile`, then call `ScriptHosts.register(host)` before `SceneLoader.load`.
- [ ] 4.3 Create the empty `:games:pong/src/main/resources/scripts/` directory and ensure Gradle bundles it.
- [ ] 4.4 **GATE E2-prep**: Run `./gradlew :games:pong:run`. Manual verification by the user that Pong still behaves identically (no scripts loaded yet).

## 5. Migrate CenterLine (E2)

- [ ] 5.1 Create `scripts/center-line.nengine.kts` declaring a single top-level class equivalent to the current `CenterLine` Kotlin class.
- [ ] 5.2 Add the script path to the Pong manifest in `Main.kt`.
- [ ] 5.3 Update `pong.scene.json` so the entry that previously referenced `com.neoutils.engine.games.pong.CenterLine` now uses `"type": "scripts/center-line.nengine.kts"`.
- [ ] 5.4 Delete `:games:pong/src/main/kotlin/com/neoutils/engine/games/pong/CenterLine.kt`.
- [ ] 5.5 Remove the `CenterLine` registration from `NodeRegistry` (if any) in `Main.kt`.
- [ ] 5.6 **GATE E2**: Run `./gradlew :games:pong:run`. Manual verification by the user that the center dashed line still renders identically.

## 6. Migrate Score (E2-bis)

- [ ] 6.1 Create `scripts/score.nengine.kts` equivalent to `Score.kt`.
- [ ] 6.2 Update the manifest in `Main.kt` to include the new script (after `center-line`, before any script that depends on `Score`).
- [ ] 6.3 Update `pong.scene.json` entries for Score nodes to reference `"scripts/score.nengine.kts"`.
- [ ] 6.4 Delete `Score.kt` and remove its `NodeRegistry` registration.
- [ ] 6.5 **GATE E2-bis**: Run `./gradlew :games:pong:run`. Manual verification: HUD scores still update when balls cross goals.

## 7. Migrate Walls (E3a)

- [ ] 7.1 Create `scripts/walls.nengine.kts` equivalent to `Walls.kt` (and any `Goal` colliders if currently part of `Walls`).
- [ ] 7.2 Add to manifest before any script that depends on it.
- [ ] 7.3 Update `pong.scene.json` to reference the script path.
- [ ] 7.4 Delete `Walls.kt` and remove its `NodeRegistry` registration.
- [ ] 7.5 **GATE E3a**: Run `./gradlew :games:pong:run`. Manual verification: ball reflects off top/bottom walls and goal collisions trigger scoring.

## 8. Migrate Ball (E3b)

- [ ] 8.1 Create `scripts/ball.nengine.kts` equivalent to `Ball.kt`. Confirm the script can still reference `BoxCollider` from `:engine` (pre-imported via the `physics.*` default import).
- [ ] 8.2 Add to manifest after `walls`/`score` (`Ball` does not depend on `Paddle` directly; verify).
- [ ] 8.3 Update `pong.scene.json` to reference the script.
- [ ] 8.4 Delete `Ball.kt` and remove its `NodeRegistry` registration.
- [ ] 8.5 **GATE E3b**: Run `./gradlew :games:pong:run`. Manual verification: ball moves, reflects off walls and paddles, emits scoring signal correctly.

## 9. Migrate Paddle (E4)

- [ ] 9.1 Create `scripts/paddle.nengine.kts` equivalent to the current `Paddle.kt`. Note: this script still references `PaddleCollider` which remains a Kotlin class, on the `:games:pong` classpath.
- [ ] 9.2 Add to manifest after `ball` (`Paddle.target: NodeRef<Node2D>` resolves at runtime so manifest order does not require `Ball` first, but place it after for clarity).
- [ ] 9.3 Update `pong.scene.json` paddle entries to reference the script.
- [ ] 9.4 Delete `Paddle.kt` and remove its `NodeRegistry` registration.
- [ ] 9.5 **GATE E4**: Run `./gradlew :games:pong:run`. Manual verification: W/S move the left paddle; AI paddle tracks the ball; collisions trigger ball reflection.

## 10. Migrate PaddleCollider (E5 — first script-to-script reference)

- [ ] 10.1 Create `scripts/paddle-collider.nengine.kts` equivalent to `PaddleCollider.kt` (if it exists as a separate file) or extracted from `Paddle.kt`.
- [ ] 10.2 Add `paddle-collider` to the manifest **before** `paddle` to satisfy compilation order.
- [ ] 10.3 Update `paddle.nengine.kts` to reference the script-defined `PaddleCollider` class. This is the first time a script references a class from another script.
- [ ] 10.4 Delete `PaddleCollider.kt` and remove any `NodeRegistry` registration.
- [ ] 10.5 If applicable, update `pong.scene.json` to reference the script (collider may be added programmatically in `Paddle.onEnter`, in which case no JSON change is needed — verify).
- [ ] 10.6 Reorder the manifest so it always lists every dependency before its dependents (paddle-collider, walls, goal, score, center-line, ball, paddle, pong-scene).
- [ ] 10.7 **GATE E5**: Run `./gradlew :games:pong:run`. Manual verification: gameplay is identical, no collision regressions.

## 11. Migrate PongScene (E6 — final boss)

- [ ] 11.1 Create `scripts/pong-scene.nengine.kts` equivalent to `PongScene.kt`. The script's class extends `Scene` (or whatever Pong currently uses as the root type) and constructs the tree in `onEnter`/`init` if any — or, more cleanly, leaves all child wiring to the JSON.
- [ ] 11.2 Add `pong-scene` as the **last** entry in the manifest.
- [ ] 11.3 Update `pong.scene.json` so its top-level `root.type` is `"scripts/pong-scene.nengine.kts"`.
- [ ] 11.4 Delete `PongScene.kt` and remove its `NodeRegistry` registration.
- [ ] 11.5 Verify that `:games:pong/src/main/kotlin/com/neoutils/engine/games/pong/` contains only `Main.kt` (and any non-Node helper, e.g. `Goal.Side` enum if used; consider relocating the enum into the engine or into a script).
- [ ] 11.6 **GATE E6**: Run `./gradlew :games:pong:run`. Manual verification: full Pong session — human vs AI, scoring, debug toggles (F1/F2), all behave identically to the pre-migration build.

## 12. Documentation and bookkeeping (E7)

- [ ] 12.1 Update `CLAUDE.md` "Module Structure" section to add `:engine-scripting` and a one-line description.
- [ ] 12.2 Add a "Scripting" subsection to `CLAUDE.md` describing the `.nengine.kts` contract (one top-level Node subclass, default imports, manifest, no hot reload, fail-fast).
- [ ] 12.3 Add `:engine-scripting` to the module table where appropriate.
- [ ] 12.4 Update the Roadmap table in `CLAUDE.md` adding the `add-scripting` change as Archived (filled at archival time).
- [ ] 12.5 Run `./gradlew build` from project root and confirm clean build of all modules.
- [ ] 12.6 Run `./gradlew :games:tictactoe:run` briefly to confirm tictactoe still works without scripting dependencies pulled in.
- [ ] 12.7 Run `./gradlew :games:demos:run` briefly to confirm demos still works.
- [ ] 12.8 Run `./gradlew :games:pong:run` for a final manual playtest.

## 13. Cache and resilience checks

- [ ] 13.1 Delete `:games:pong/build/scripting-cache/`, run Pong, verify the cache is rebuilt and the game runs normally.
- [ ] 13.2 Modify a single script (e.g. tweak `Paddle.speed` default), run Pong, verify only the changed script recompiles and the new default takes effect.
- [ ] 13.3 Verify that `./gradlew clean` removes the cache (or alternatively that the cache is robust to staleness — document the chosen behavior).
- [ ] 13.4 Add a unit test or integration test asserting that the cache hit path does not invoke the kotlin compiler (e.g. by counting calls to a wrapped `BasicJvmScriptingHost`).

## 14. Verify and archive

- [ ] 14.1 Run `/opsx:verify add-scripting` to confirm implementation matches specs.
- [ ] 14.2 Run `/opsx:archive add-scripting` to freeze the change and sync main specs.
