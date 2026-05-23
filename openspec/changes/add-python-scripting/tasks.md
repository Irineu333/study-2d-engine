## 1. E0 — Bootstrap :engine-bundle-python module

- [x] 1.1 Create `engine-bundle-python/` directory and `engine-bundle-python/build.gradle.kts` with `:engine` and `:engine-bundle` deps plus GraalPy 24.x (`org.graalvm.polyglot:polyglot` and `org.graalvm.polyglot:python`) — pin exact versions in `libs.versions.toml`.
- [x] 1.2 Add `include(":engine-bundle-python")` to `settings.gradle.kts`.
- [x] 1.3 Add a smoke test `engine-bundle-python/src/test/kotlin/.../GraalPySmokeTest.kt` that builds a `Context.newBuilder("python").build()`, evaluates `1 + 1`, and asserts the result is `2`. This validates that the GraalPy runtime is on the classpath and bootable.
- [x] 1.4 Run `./gradlew :engine-bundle-python:test` and confirm the smoke test passes.
- [x] 1.5 **Gate**: run `./gradlew :games:pong:run` and confirm Pong still works exactly as before (it still uses the old Kotlin Scripting path; `:engine-bundle-python` is not wired in yet). Measure the JAR size delta on `:engine-bundle-python` and note it in the change folder for future reference.

## 2. E1 — Introduce ScriptHost SPI in :engine-bundle

- [x] 2.1 Create package `com.neoutils.engine.bundle.script` in `:engine-bundle`.
- [x] 2.2 Define interface `BundleSource` with `fun read(path: String): String` and `fun exists(path: String): Boolean`.
- [x] 2.3 Implement `ClasspathBundleSource(bundleRoot: String)` and `DirectoryBundleSource(bundleDir: File)` inside `:engine-bundle` (private to package). These replace the old `ScriptSource` variants.
- [x] 2.4 Define interfaces `ScriptHost`, `Script`, `ScriptInstance` and data class `ExportedProperty` per the `script-host` spec.
- [x] 2.5 Define `object ScriptHostRegistry` with `register`, `clear`, `hostFor`, and `loadAll`.
- [x] 2.6 In `:engine` module, add `internal var scriptInstance: ScriptInstance?` to `Node` and wire hook delegation in `onEnter`, `onUpdate`, `onRender`, `onCollide`. **Caveat**: `:engine` cannot reference `ScriptInstance` directly without a circular dep — introduce a tiny interface `com.neoutils.engine.scene.ScriptInstanceContract` in `:engine` with the same four methods, and have `ScriptInstance` in `:engine-bundle` extend it. The slot on `Node` is typed against the `:engine` contract.
- [x] 2.7 Write unit tests in `:engine-bundle` for `ScriptHostRegistry` (registration by extension, dispatch, unknown extension error).
- [x] 2.8 Write a unit test in `:engine` showing that `Node.onUpdate` with a mock `scriptInstance` correctly dispatches to it.
- [x] 2.9 Build the project and confirm no compile errors. Pong continues to run via the old path.

## 3. E2 — Implement PythonScriptHost

- [x] 3.1 Create `PythonScriptHost` class in `:engine-bundle-python` implementing `ScriptHost` with `extension = ".py"`.
- [x] 3.2 Build the `Context` Polyglot eagerly in the host constructor: `Context.newBuilder("python").allowAllAccess(true).option("python.PosixModuleBackend", "java").build()`.
- [x] 3.3 Inject pre-bindings into the Context: `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`, `BoxCollider`, `Node2D`.
- [x] 3.4 Implement `load(path, bundle)`:
  - Read source via `bundle.read(path)`.
  - Parse the first non-empty line to extract `extends <NodeType>` (docstring or `# extends ...` form).
  - Resolve `<NodeType>` against `NodeRegistry`; fail-fast with named exception if unknown.
  - Run an internal Python AST inspector (a small helper `inspector.py` shipped as a resource) to extract `AnnAssign` top-level nodes and produce a `List<ExportedProperty>`.
  - Evaluate the module source itself in the Context (with `Source.named(path).build()`) so its top-level executes once.
  - Cache the resulting `Value` (module object) keyed by path.
  - Return a `Script` containing `path`, `extendsType`, and `exports`.
- [x] 3.5 Implement `attach(node, script)`:
  - Look up the cached module `Value` by `script.path`.
  - Build a Python-side instance object that proxies `self` to the Kotlin `Node` (using GraalPy host interop).
  - Return a `ScriptInstance` whose hook methods call into Python (`module.on_update(self, dt)`).
  - Missing hook methods become no-ops (check `Value.hasMember("on_update")` etc.).
- [x] 3.6 Implement `ScriptInstance.setExport(name, value)` that assigns the converted value onto the Python instance object.
- [x] 3.7 Add JSON → Kotlin coercion table for prop values (`JsonPrimitive(360.0)` → `Float`, `JsonObject({x, y})` → `Vec2`, etc.). Helper lives in `:engine-bundle` since it depends only on engine types.
- [x] 3.8 Add a static call `PythonScriptHost.install()` that constructs the host and registers it in `ScriptHostRegistry`. Document that callers (or `BundleLoader`) trigger this.
- [x] 3.9 Write unit tests:
  - Loading a trivial `.py` with `# extends Node2D` and `speed: float = 360.0` produces a `Script` whose `extendsType` is `Node2D::class` and `exports` contains the speed entry.
  - Missing `extends` declaration fails fast with a named exception.
  - Unknown `extends` type fails fast.
  - Unsupported export type (`cache: dict = {}`) is silently dropped from `exports`.
  - `Optional[Key]` is detected as nullable.
  - Attaching a script to a `Node2D` and calling `onUpdate` invokes `on_update` in Python.
  - Missing `on_collide` does not throw when the node receives a collision.
- [x] 3.10 Confirm test suite passes.

## 4. E3 — Wire ScriptHost into BundleLoader and SceneLoader

- [x] 4.1 In `:engine` (or `:engine-bundle`, wherever `NodeEntry` lives), add optional fields `script: String? = null` and `props: JsonObject? = null` to `NodeEntry`, with `@SerialName` for backward-friendly JSON keys.
- [x] 4.2 Update `SceneLoader.load` to accept an optional `scripts: Map<String, Script>` parameter. When `NodeEntry.script` is non-null:
  1. Build the node via existing `NodeRegistry.create(type)`.
  2. Look up the `Script` in the provided map.
  3. Resolve the appropriate `ScriptHost` via `ScriptHostRegistry.hostFor(path)`.
  4. Call `host.attach(node, script)`.
  5. Apply `props` via `scriptInstance.setExport(name, coerce(json, exportType))`.
  6. Set `node.scriptInstance = instance`.
- [x] 4.3 Reject `NodeEntry.props != null && script == null` with a clear exception at load time.
- [x] 4.4 Update `BundleLoader.fromResources` and `fromPath` to:
  - Read scene JSON via the appropriate `BundleSource`.
  - Tree-walk the parsed JSON to collect `scriptPaths = entries.mapNotNull { it.script }.toSet()`.
  - Call `ScriptHostRegistry.loadAll(scriptPaths, bundle)` to get `Map<String, Script>`.
  - Pass the map to `SceneLoader.load`.
- [x] 4.5 Remove the old branch in `SceneLoader`/`NodeRegistry` that treated `type` ending in `.kts` as a script reference. Type resolution becomes single-purpose: always a native Node type. _(SceneLoader/NodeRegistry never had a `.kts` branch — both treat `type` uniformly. The legacy `.nengine.kts` path lives entirely inside `BundleLoader` and is removed in E8 along with `KotlinScriptingHost`. NodeRegistry KDoc updated to no longer suggest scripts as identifiers.)_
- [x] 4.6 `NodeRegistry` loses its script-path-aware registration overload; it goes back to mapping only native Node types and their factories. _(The `register(identifier, klass, factory)` overload is generic — used by the legacy KTS path until E8. Will be removed in E8 along with `KotlinScriptingHost`; the surviving overload `register(klass, factory)` already maps native types only.)_
- [x] 4.7 Update `BundleLoaderTest`:
  - Replace `.nengine.kts` fixtures with `.py` fixtures.
  - Add a test that loads a bundle containing a single `Node2D` with `script: "scripts/dummy.py"` and `props: { "value": 42 }`, asserts the node has a non-null `scriptInstance` and the export was applied.
  - Add a test that `script` referencing an unknown extension fails fast.
  - Add a test that `props` without `script` fails fast.
- [x] 4.8 Build and run all module tests. Pong itself still uses the legacy `.nengine.kts` files — they should still work for now because `KotlinScriptingHost` has not been deleted yet, but the new code path also works for Python scripts.

## 5. E4 — Migrate first Pong leaves (CenterLine + Score)

- [ ] 5.1 Create `games/pong/src/main/resources/pong/scripts/center_line.py` translating `center-line.nengine.kts` line for line. Use `# extends Node2D` header. Move `@Inspect` vars to top-level annotated assignments. Move `onRender` to `def on_render(self, renderer)`.
- [ ] 5.2 Create `games/pong/src/main/resources/pong/scripts/score.py` translating `score.nengine.kts` the same way.
- [ ] 5.3 Update `pong/scene.json`:
  - For the center-line and the two score nodes, change `type` to a native Node type (e.g., `engine.Node2D`) and add `script` field pointing to the new `.py` file.
  - Move the previous `@Inspect` field values (size, color, score, etc.) into a `props` JSON object on each entry.
- [ ] 5.4 Delete `center-line.nengine.kts` and `score.nengine.kts` from `pong/scripts/`.
- [ ] 5.5 Make sure `:games:pong/build.gradle.kts` adds dependency on `:engine-bundle-python`.
- [ ] 5.6 Make `:games:pong/src/main/kotlin/.../Main.kt` call `PythonScriptHost.install()` once before `BundleLoader.fromResources("pong")` (or rely on a `companion object` init pattern documented in `CLAUDE.md`).
- [ ] 5.7 **Gate**: run `./gradlew :games:pong:run` and play 30 seconds. The center line and both scores should render exactly like before. The paddle/ball/walls/goals continue to use the old `.nengine.kts` path.

## 6. E5 — Migrate Walls, Goal, Ball

- [ ] 6.1 Translate `goal.nengine.kts` to `goal.py` (renaming `GoalSide` references as needed; the enum stays on the Kotlin side in `:engine`-or-`pong`).
- [ ] 6.2 Translate `ball.nengine.kts` to `ball.py`. The Ball **extends BoxCollider** — the `# extends BoxCollider` header must resolve correctly. Confirm `BoxCollider` is in the pre-bound Context bindings.
- [ ] 6.3 Update `pong/scene.json` to point the ball and the two goal nodes to their new `.py` files via `script` and `props`. The wall nodes already use `BoxCollider` by FQN with no script and stay as-is.
- [ ] 6.4 Delete `goal.nengine.kts` and `ball.nengine.kts`.
- [ ] 6.5 **Gate**: run `./gradlew :games:pong:run` and play 30 seconds. The ball should physics correctly off the walls and the goals should still trigger score updates. Visual is unchanged.

## 7. E6 — Migrate Paddle

- [ ] 7.1 Translate `paddle.nengine.kts` to `paddle.py`. Confirm the `target: NodeRef = NodeRef("")` export survives the round trip and AI tracking works.
- [ ] 7.2 Confirm that the BoxCollider child the paddle creates in `on_enter` still works (use `self._collider = BoxCollider(size=Vec2(...))` and `self.add_child(self._collider)`).
- [ ] 7.3 Update `pong/scene.json` for both paddles (`paddleLeft`, `paddleRight`): switch to `engine.Node2D` + `script: "scripts/paddle.py"` and migrate all `@Inspect` properties (`size`, `playFieldHeight`, `upKey`, `downKey`, `ai`, `speed`, `aiMaxSpeed`, `aiTolerance`, `target`) into the `props` object.
- [ ] 7.4 Delete `paddle.nengine.kts`.
- [ ] 7.5 **Gate**: run `./gradlew :games:pong:run` and play 30 seconds. Confirm W/S move the left paddle, AI tracks the ball on the right, and collisions look correct.

## 8. E7 — Migrate PongScene

- [ ] 8.1 Translate `pong-scene.nengine.kts` to `pong_scene.py`. This script orchestrates the cross-references (`paddle.target = ball` etc.). Confirm those still work via `NodeRef.resolve` from the Python side.
- [ ] 8.2 Update `pong/scene.json` to use `script: "scripts/pong_scene.py"` for the root scene node.
- [ ] 8.3 Delete `pong-scene.nengine.kts`. The `pong/scripts/` directory now contains only `.py` files.
- [ ] 8.4 **Gate**: run `./gradlew :games:pong:run` and play 60 seconds end-to-end. Goal scoring, ball reset, AI behavior, paddle input — all identical to before.

## 9. E8 — Remove Kotlin Scripting and finalize

- [ ] 9.1 Delete `engine-bundle/src/main/kotlin/com/neoutils/engine/bundle/scripting/` directory entirely (`KotlinScriptingHost.kt`, `NEngineScript.kt`, `ScriptSource.kt`, `CyclicScriptDependencyError.kt`).
- [ ] 9.2 Delete `engine-bundle/src/test/kotlin/com/neoutils/engine/bundle/scripting/KotlinScriptingHostTest.kt`.
- [ ] 9.3 Remove `kotlin-scripting-common`, `kotlin-scripting-jvm`, and `kotlin-scripting-jvm-host` from `engine-bundle/build.gradle.kts` and from `libs.versions.toml`.
- [ ] 9.4 Update `BundleLoader.kt` to remove any remaining direct reference to `KotlinScriptingHost` (should already be gone after E4 but double-check).
- [ ] 9.5 Delete the `openspec/specs/scripting/` directory (the capability is removed by this change; its content is replaced by `script-host` and `python-scripting`).
- [ ] 9.6 Run `./gradlew clean build` and confirm the project compiles cleanly with no warnings about deleted classes.
- [ ] 9.7 Run all module tests (`./gradlew test`) and confirm the suite passes.
- [ ] 9.8 **Gate**: run `./gradlew :games:pong:run` and confirm everything still works. Measure startup time and compare to the legacy build for the design notes.

## 10. Documentation and conventions

- [ ] 10.1 Update `CLAUDE.md` module table to include `:engine-bundle-python` and remove any remaining mention of `.nengine.kts` as the active scripting mechanism.
- [ ] 10.2 Rewrite the "Scripting contract" section in `CLAUDE.md` to describe the Python conventions: `# extends`, top-level annotated assignments for exports, snake_case hooks, runtime state in `self._private`.
- [ ] 10.3 Add a short "Inspecting Python scripts" subsection mentioning the published `.pyi` stubs in `:engine-bundle-python/src/main/resources/stubs/` and how to configure an IDE (Pyright/Pylance `extraPaths`).
- [ ] 10.4 Add `add-python-scripting` row to the roadmap table in `CLAUDE.md` with status moving from `Active` to `Archived` at the end of the change.

## 11. Publish .pyi stubs

- [ ] 11.1 Write `engine-bundle-python/src/main/resources/stubs/engine/__init__.pyi` re-exporting the surfaces.
- [ ] 11.2 Write `stubs/engine/math.pyi` with `Vec2` and `Rect`.
- [ ] 11.3 Write `stubs/engine/render.pyi` with `Color` and `Renderer` (just the methods scripts use: `draw_rect`, `draw_line`, `measure_text`).
- [ ] 11.4 Write `stubs/engine/input.pyi` with `Key` enum constants and the `Input` interface.
- [ ] 11.5 Write `stubs/engine/scene.pyi` with `Node` and `Node2D` (only the public surface scripts touch: `transform`, `world_position()`, `add_child`, `root_scene()`, etc.).
- [ ] 11.6 Write `stubs/engine/physics.pyi` with `BoxCollider`.
- [ ] 11.7 Write `stubs/engine/serialization.pyi` with `NodeRef`.
- [ ] 11.8 Confirm the stubs land in the built jar at `stubs/engine/*.pyi`.

## 12. Final verification

- [ ] 12.1 Confirm `:games:tictactoe` and `:games:demos` still run unchanged via `./gradlew :games:tictactoe:run` and `./gradlew :games:demos:run`. Neither should depend transitively on `:engine-bundle-python`.
- [ ] 12.2 Confirm `openspec validate add-python-scripting` reports the change as valid.
- [ ] 12.3 Confirm no `*.nengine.kts` file remains anywhere in the repo under `games/`.
- [ ] 12.4 Confirm `:engine-bundle` runtime classpath contains no `kotlin-scripting-*` artifact.
- [ ] 12.5 Run `./gradlew :games:pong:run --args="<path-to-fs-copy-of-pong-bundle>"` to confirm the filesystem path of `BundleLoader.fromPath` also works with Python scripts.
