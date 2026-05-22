## 1. Create :engine-bundle module skeleton

- [x] 1.1 Add `engine-bundle/` directory with `build.gradle.kts` mirroring `:engine-scripting` (Kotlin JVM, depends on `:engine` and `kotlin-scripting-jvm-host`).
- [x] 1.2 Register `:engine-bundle` in `settings.gradle.kts` and remove `include(":engine-scripting")`.
- [x] 1.3 Create base package `com.neoutils.engine.bundle` and an internal sub-package `com.neoutils.engine.bundle.scripting` for migrated scripting code.
- [x] 1.4 Verify `:engine-bundle` is not on the resolved classpath of `:engine`, `:engine-skiko`, or `:engine-compose` (no reverse dependency).

## 2. Move scripting backend into :engine-bundle

- [x] 2.1 Move `KotlinScriptingHost.kt` and `NEngineScript.kt` from `:engine-scripting` into `:engine-bundle` under `bundle/scripting/` and mark them `internal`.
- [x] 2.2 Move `KotlinScriptingHostTest.kt` from `:engine-scripting` into `:engine-bundle` test source set; adjust visibility helpers as needed.
- [x] 2.3 Delete the `engine-scripting/` directory in its entirety.
- [x] 2.4 Confirm `./gradlew :engine-bundle:build` compiles after the move (tests will still fail at this step — that's expected).

## 3. Refactor NodeRegistry to bidirectional

- [x] 3.1 In `:engine`, change `NodeRegistry` to store `(identifier, KClass, factory)` triples in a single internal map keyed by identifier, plus a reverse map `KClass -> identifier`.
- [x] 3.2 Replace the existing `register(type: KClass, factory)` with the explicit `register(identifier: String, klass: KClass<out Node>, factory: () -> Node)` overload. Keep a `register(klass, factory)` convenience overload that derives `identifier = klass.qualifiedName`.
- [x] 3.3 Add `identifierFor(klass: KClass<out Node>): String?` that returns the identifier under which `klass` is registered (or null).
- [x] 3.4 Make `registerEngineTypes()` idempotent (no-op on re-entry).
- [x] 3.5 Update `NodeRegistryTest.kt` and add cases for: identifier-explicit register, bidirectional lookup, idempotent `registerEngineTypes`.

## 4. Strip script awareness from SceneLoader

- [x] 4.1 Remove the `endsWith(".kts")` branch in `entryToNode`; route every entry through `NodeRegistry.create(entry.type)`.
- [x] 4.2 Remove the `ScriptHosts.current()` reach in `nodeToEntry`; use `NodeRegistry.identifierFor(node::class)` and fall back to `node::class.qualifiedName`.
- [x] 4.3 Remove the `com.neoutils.engine.scripting.ScriptHosts` import.
- [x] 4.4 Delete `engine/src/main/kotlin/com/neoutils/engine/scripting/ScriptHost.kt` and `ScriptHosts.kt`.
- [x] 4.5 Delete `engine/src/test/kotlin/com/neoutils/engine/scripting/ScriptHostsTest.kt`.
- [x] 4.6 Update `SceneLoaderTest.kt`: drop the script-routing scenarios; keep the structural ones. Verify the test suite still passes (locally compile-only is OK since pong tests will be broken until step 8).

## 5. Refactor KotlinScriptingHost: ScriptSource, round-robin, cache

- [x] 5.1 Introduce internal `sealed interface ScriptSource { fun read(relativePath: String): String }` with `Classpath(bundleRoot: String)` and `Directory(bundleDir: File)` variants.
- [x] 5.2 Replace the `manifest: List<String>` constructor parameter with `source: ScriptSource` and `cacheDir: File`. Drop manifest-driven compilation in the constructor.
- [x] 5.3 Add a public-internal method `compileAll(paths: Set<String>): Map<String, KClass<out Node>>` that runs the round-robin / fixed-point algorithm.
- [x] 5.4 Inside `compileAll`, extract top-level class names per script source via a regex on `^\s*(?:public\s+|open\s+|abstract\s+|sealed\s+)?class\s+(\w+)`. Use this set to distinguish "unresolved-because-pending" from real author errors.
- [x] 5.5 If a full pass through pending scripts makes no progress, throw `CyclicScriptDependencyError` (new exception in the same module) listing the offending paths.
- [x] 5.6 Change the cache key to `SHA256(source || delimiter || sortedImportSet.joinToString || delimiter || engineVersion)`. Read `engineVersion` from a classpath resource `META-INF/nengine.version` packaged with `:engine`; if absent, fall back to a constant default.
- [x] 5.7 Add `META-INF/nengine.version` under `:engine/src/main/resources/` containing the current engine version string.
- [x] 5.8 At bootstrap, rebuild `classesDir` from scratch (delete contents) and only restore bytecode for the script paths that were requested in the current run, using their valid cache files. Bytecode for paths not requested must NOT remain in `classesDir`.
- [x] 5.9 Update `KotlinScriptingHostTest.kt` accordingly: add cases for (a) round-robin success with cross-refs in any input order, (b) genuine syntax error fails fast on first encounter, (c) `CyclicScriptDependencyError` raised on cycle, (d) cache invalidation when the import set changes, (e) cache invalidation when engineVersion changes, (f) orphan bytecode removed at bootstrap.

## 6. Implement BundleLoader

- [x] 6.1 Add `BundleLoader` object in `:engine-bundle` (package `com.neoutils.engine.bundle`) with `fromResources(name: String, types: List<KClass<out Node>> = emptyList())` and `fromPath(bundleDir: File, types: List<KClass<out Node>> = emptyList())`.
- [x] 6.2 Implement a private `load(source: ScriptSource, sceneJsonText: String, cacheDir: File, types: List<KClass<out Node>>): Scene` pivot used by both entry points.
- [x] 6.3 In the pivot: call `NodeRegistry.registerEngineTypes()` first.
- [x] 6.4 For each `KClass` in `types`, register it in `NodeRegistry` with identifier = FQN and factory built via `klass.java.getDeclaredConstructor().newInstance() as Node`. Surface a clear exception if the no-args constructor is missing.
- [x] 6.5 Tree-walk the parsed `SceneFile`: collect every `entry.type` ending with `.nengine.kts` into a set of script paths to compile.
- [x] 6.6 Construct a `KotlinScriptingHost(source, cacheDir)` and call `compileAll(scriptPaths)`. For each resolved `(path, klass)`, register in `NodeRegistry` with identifier = the path string and factory = no-args reflection on `klass`.
- [x] 6.7 Delegate to `SceneLoader.load(sceneJsonText)` and return the resulting scene.
- [x] 6.8 For `fromResources(name)`: read `scene.json` via `ClassLoader.getResource("$name/scene.json")`; cacheDir = `File("build/scripting-cache/$name").absoluteFile`; source = `ScriptSource.Classpath(bundleRoot = name)`.
- [x] 6.9 For `fromPath(bundleDir)`: validate the directory exists; read `File(bundleDir, "scene.json").readText()`; cacheDir = `File(bundleDir, ".nengine-cache")`; source = `ScriptSource.Directory(bundleDir)`.
- [x] 6.10 Validate missing `scene.json` and missing bundle dir both raise exceptions whose messages name the offending argument.
- [x] 6.11 Ensure `NodeRegistry.clear()` is NOT called automatically (multiple bundles in the same JVM are not a use case, but tests need explicit clearing between cases).

## 7. Author BundleLoaderTest

- [x] 7.1 Add `BundleLoaderTest.kt` under `:engine-bundle/src/test/kotlin/.../bundle/`.
- [x] 7.2 Test fixture: a minimal bundle baked into `src/test/resources/test-bundle/` containing `scene.json` (root + one scripted child + one engine-typed child) and `scripts/foo.nengine.kts`.
- [x] 7.3 Case: `BundleLoader.fromResources("test-bundle")` returns a detached scene with the expected tree.
- [x] 7.4 Case: `BundleLoader.fromPath(File("..."))` against a temp directory created by the test (copy of the resources fixture) returns equivalent scene.
- [x] 7.5 Case: classpath bundle and disk bundle produce semantically equivalent scenes from the same JSON.
- [x] 7.6 Case: orphan script in `scripts/` that the JSON does NOT reference is not compiled (assert via inspection of `KotlinScriptingHost.compilationCount` or absence of class file).
- [x] 7.7 Case: same script path referenced multiple times in the tree compiles once.
- [x] 7.8 Case: missing `scene.json` in the bundle raises exception naming the bundle.
- [x] 7.9 Case: `types = listOf(SomeCustomNode::class)` makes a JSON entry referencing the FQN of `SomeCustomNode` resolve correctly.
- [x] 7.10 Case: engine types (e.g. `BoxCollider`) resolve without the caller having registered anything.

## 8. Migrate :games:pong to bundle layout

- [x] 8.1 In `:games:pong/build.gradle.kts`, swap dependency `:engine-scripting` for `:engine-bundle`.
- [x] 8.2 Move `games/pong/src/main/resources/pong.scene.json` to `games/pong/src/main/resources/pong/scene.json`.
- [x] 8.3 Move `games/pong/src/main/resources/scripts/` to `games/pong/src/main/resources/pong/scripts/`.
- [x] 8.4 Verify the `"type"` strings inside `scene.json` are already bundle-relative (`scripts/foo.nengine.kts`) — no change to JSON content needed.
- [x] 8.5 Rewrite `games/pong/src/main/kotlin/com/neoutils/engine/games/pong/Main.kt`: only `val scene = BundleLoader.fromResources("pong")` and `SkikoHost().run(scene, GameConfig(...))`. Remove `registerPongTypes`, `KotlinScriptingHost`, `ScriptHosts.register`, manifest, and `loadScene` helper.
- [ ] 8.6 Run `./gradlew :games:pong:run` end-to-end and verify gameplay matches behavior pre-change (paddles, ball, walls, goals, HUD, AI, scoring).

## 9. Update conventions and documentation

- [x] 9.1 Update `CLAUDE.md` module structure table: replace `:engine-scripting` line with `:engine-bundle`, describing its responsibility as "bundle loading + internal Kotlin script compilation".
- [x] 9.2 Update `CLAUDE.md` "How to Run" section if it references the old `pong.scene.json` path — point to `pong/scene.json`.
- [x] 9.3 Add a new row to the `CLAUDE.md` roadmap table for `add-bundle-loader` with status `Active` (transitions to `Archived` at archive time).
- [x] 9.4 Update the `Scripting contract (.nengine.kts)` subsection in `CLAUDE.md`: drop the mention of "manifest configurado no KotlinScriptingHost"; replace with "compiled on demand by `BundleLoader` via tree-walk discovery + round-robin fixed-point".

## 10. Final validation

- [ ] 10.1 Run `./gradlew clean build` and confirm the whole project builds.
- [ ] 10.2 Run `./gradlew :engine-bundle:test :engine:test` and confirm all unit tests pass.
- [ ] 10.3 Run `./gradlew :games:pong:run`, play a brief match (or simulate one), and confirm gameplay matches the pre-change behavior.
- [ ] 10.4 Confirm `:games:tictactoe` and `:games:demos` still build and run (no regression from the registry refactor).
- [ ] 10.5 Run `openspec validate add-bundle-loader --strict` and address any reported issues.
