## ADDED Requirements

### Requirement: ScriptHost SPI in engine core

The `:engine` module SHALL define an interface `ScriptHost` that abstracts the act of turning a script source file into an instantiable `Node` subclass. The interface MUST expose at minimum:

- `fun compile(path: String): KClass<out Node>` — load and compile the script at the given path, returning the resolved class.
- `fun factoryFor(path: String): () -> Node` — return a zero-argument factory that produces fresh instances of the script's class.

The interface MUST NOT reference any type from `org.jetbrains.kotlin.*`, `kotlin-scripting-*`, or any other compiler-specific package. The interface MUST be implementable without reflection or service loaders. The `:engine` module SHALL also expose a global registry `ScriptHosts` with `register(host: ScriptHost)` and `current(): ScriptHost?`, so that a `GameHost`-agnostic `SceneLoader` can locate the active host without dependency injection.

#### Scenario: ScriptHost lives in :engine and is implementation-free

- **WHEN** the file declaring `ScriptHost` in `:engine` is parsed
- **THEN** every import begins with `com.neoutils.engine.*` or `kotlin.*` (including `kotlin.reflect.*`)
- **AND** no import begins with `org.jetbrains.kotlin.*` or any kotlin-scripting package

#### Scenario: ScriptHosts registry holds a single current host

- **GIVEN** no host has been registered
- **WHEN** code calls `ScriptHosts.current()`
- **THEN** the result is `null`
- **AND** after `ScriptHosts.register(host)`, `ScriptHosts.current()` returns `host`

#### Scenario: factoryFor returns fresh instances

- **GIVEN** a registered `ScriptHost` whose `compile("scripts/foo.nengine.kts")` returns a class `Foo : Node`
- **WHEN** code calls `host.factoryFor("scripts/foo.nengine.kts")` twice and invokes each result
- **THEN** the two invocations return two distinct `Foo` instances

### Requirement: engine-scripting module provides the Kotlin scripting backend

The project SHALL provide a new Gradle module `:engine-scripting` that depends on `:engine` and on `org.jetbrains.kotlin:kotlin-scripting-jvm-host`. The module SHALL expose a concrete `KotlinScriptingHost : ScriptHost` implementation backed by `kotlin-scripting-jvm-host`. The module MUST NOT be a dependency of `:engine`, `:engine-skiko`, or `:engine-compose`. Only game modules that opt into scripting depend on it.

#### Scenario: engine-scripting module exists with correct dependencies

- **WHEN** the build configuration of `:engine-scripting` is inspected
- **THEN** it declares a dependency on `:engine`
- **AND** it declares a dependency on `org.jetbrains.kotlin:kotlin-scripting-jvm-host`
- **AND** it does not declare any other engine module as a dependency

#### Scenario: engine, engine-skiko, and engine-compose do not depend on engine-scripting

- **WHEN** the build configuration of `:engine`, `:engine-skiko`, and `:engine-compose` is inspected
- **THEN** none of them lists `:engine-scripting` as a dependency, direct or transitive

#### Scenario: Games without scripting do not pull engine-scripting transitively

- **WHEN** the resolved runtime classpath of `:games:tictactoe` or `:games:demos` is computed
- **THEN** no artifact from `:engine-scripting` or `kotlin-scripting-jvm-host` is present

### Requirement: A script defines exactly one top-level Node subclass

The `KotlinScriptingHost` SHALL compile a `.nengine.kts` source and inspect the compiled output for top-level classes. The compilation MUST succeed only if there is exactly one top-level class whose erasure is assignable to `Node`. Zero, two, or more such classes MUST cause the compilation to fail fast with `ScriptCompilationException` whose message names the offending file and the count.

#### Scenario: One Node subclass is accepted

- **GIVEN** a script file containing `class Paddle : Node2D() { ... }` and no other top-level class
- **WHEN** code calls `host.compile("scripts/paddle.nengine.kts")`
- **THEN** the call returns the `KClass<Paddle>` instance

#### Scenario: Zero Node subclasses fails fast

- **GIVEN** a script file containing only top-level statements and no class declaration
- **WHEN** code calls `host.compile(path)`
- **THEN** the call throws `ScriptCompilationException`
- **AND** the exception message names the offending path

#### Scenario: Two Node subclasses fails fast

- **GIVEN** a script file containing both `class A : Node2D()` and `class B : Node2D()` at the top level
- **WHEN** code calls `host.compile(path)`
- **THEN** the call throws `ScriptCompilationException`
- **AND** the exception message names the offending path

### Requirement: ScriptDefinition pre-imports the engine API

The `KotlinScriptingHost` SHALL use a custom `ScriptDefinition` for the `.nengine.kts` extension that adds the following packages to the implicit default imports of every script:

- `com.neoutils.engine.scene.*`
- `com.neoutils.engine.math.*`
- `com.neoutils.engine.render.*`
- `com.neoutils.engine.input.*`
- `com.neoutils.engine.serialization.*`
- `com.neoutils.engine.physics.*`

A script that uses only these packages and `kotlin.*` SHALL compile without writing any `import` statement.

#### Scenario: Script using only pre-imported packages compiles without imports

- **GIVEN** a script file whose only non-`kotlin.*` references are `Node2D`, `Vec2`, `Color`, `Renderer`, `Inspect`
- **WHEN** code calls `host.compile(path)`
- **THEN** the compilation succeeds
- **AND** the script source file contains no `import` statement

### Requirement: Manifest-ordered compilation for inter-script references

The `KotlinScriptingHost` SHALL accept a `manifest: List<String>` parameter at construction listing script paths in compilation order (deepest dependency first, outermost dependent last). When `compile(path)` is invoked, the host MUST ensure that every script earlier in the manifest has already been compiled and its output is on the classpath of `path`'s compilation. Scripts not listed in the manifest MAY be compiled on demand with no inter-script visibility.

#### Scenario: Manifest order makes earlier scripts visible to later ones

- **GIVEN** a `KotlinScriptingHost(manifest = listOf("scripts/paddle-collider.nengine.kts", "scripts/paddle.nengine.kts"))`
- **AND** `paddle.nengine.kts` references the class declared in `paddle-collider.nengine.kts`
- **WHEN** code calls `host.factoryFor("scripts/paddle.nengine.kts")` and invokes the result
- **THEN** the resulting instance contains a child of the type declared in `paddle-collider.nengine.kts`

#### Scenario: Reversed manifest order fails to compile

- **GIVEN** the same scripts but a manifest with `paddle.nengine.kts` listed before `paddle-collider.nengine.kts`
- **WHEN** code calls `host.compile("scripts/paddle.nengine.kts")`
- **THEN** the call throws `ScriptCompilationException` because the referenced class is not yet on the classpath

### Requirement: Compilation cache persists between runs

The `KotlinScriptingHost` SHALL maintain a compilation cache keyed by the SHA-256 of the script source content concatenated with a stable version identifier of `:engine-scripting`. The cache SHALL be persisted to disk under `<gameModuleDir>/build/scripting-cache/`. On a `compile(path)` call, a cache hit SHALL skip the kotlin-scripting invocation and load the previously produced class directly. The cache directory MUST be safe to delete; deletion MUST NOT corrupt subsequent runs (the next `compile` rebuilds and repopulates the cache).

#### Scenario: First compilation populates the cache

- **GIVEN** an empty `build/scripting-cache/` directory
- **WHEN** code calls `host.compile("scripts/foo.nengine.kts")`
- **THEN** after the call, the cache directory contains at least one file whose name encodes the SHA-256 of the script source

#### Scenario: Subsequent compilation uses the cache

- **GIVEN** the cache has been populated by a prior `compile(path)` call
- **AND** the script source has not changed
- **WHEN** code calls `host.compile(path)` again in a new JVM instance
- **THEN** the call returns the same `KClass` without invoking the kotlin compiler

#### Scenario: Cache deletion forces recompilation

- **GIVEN** the cache directory has been deleted
- **WHEN** code calls `host.compile(path)`
- **THEN** the call recompiles the script
- **AND** the cache directory is repopulated

### Requirement: Script errors crash the process fail-fast

Any failure during script handling — file not found, compilation error, more than one top-level class, top-level class not assignable to `Node`, instantiation failure, runtime exception in a lifecycle hook — SHALL propagate to the caller without being caught or transformed by the engine into a placeholder node. The `ScriptHost` MUST NOT emit warnings and continue; it MUST throw. The engine MUST NOT install any default exception handler that swallows script failures.

#### Scenario: Missing script file crashes load

- **GIVEN** `pong.scene.json` references `scripts/missing.nengine.kts` which does not exist on disk
- **WHEN** code calls `SceneLoader.load(json)`
- **THEN** the call throws an exception
- **AND** the exception message names the missing path

#### Scenario: Compilation error crashes load

- **GIVEN** `scripts/broken.nengine.kts` contains a syntax error
- **WHEN** code calls `host.compile("scripts/broken.nengine.kts")`
- **THEN** the call throws `ScriptCompilationException`
- **AND** the exception message contains the diagnostic output from the Kotlin compiler

#### Scenario: Top-level class not extending Node crashes compile

- **GIVEN** a script file whose single top-level class extends `Any` (not `Node`)
- **WHEN** code calls `host.compile(path)`
- **THEN** the call throws `ScriptCompilationException`
- **AND** the exception message names the offending class and clarifies the required supertype

### Requirement: Scripts are loaded from the JVM classpath

The `KotlinScriptingHost` SHALL interpret a script `path` as a classpath-relative resource location (e.g. `scripts/paddle.nengine.kts` is resolved via `ClassLoader.getResource(path)`). The host MUST NOT require absolute filesystem paths. Scripts that ship inside JARs MUST be loadable identically to scripts in `src/main/resources/scripts/`.

#### Scenario: Script in src/main/resources/scripts is found

- **GIVEN** a file at `<gameModule>/src/main/resources/scripts/foo.nengine.kts`
- **WHEN** code calls `host.compile("scripts/foo.nengine.kts")`
- **THEN** the call succeeds and returns a `KClass<out Node>`

#### Scenario: Script not present on the classpath fails fast

- **GIVEN** no file at the requested classpath location
- **WHEN** code calls `host.compile("scripts/nonexistent.nengine.kts")`
- **THEN** the call throws an exception whose message names the requested path
