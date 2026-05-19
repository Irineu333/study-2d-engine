## ADDED Requirements

### Requirement: NodeRef resolves a typed reference by relative path

The engine SHALL provide a `NodeRef<T : Node>` type that carries a relative path string and resolves at runtime to a node of type `T` in the scene graph. The path syntax SHALL use Godot-style segments: `..` to walk up to the parent, segment names separated by `/` to walk down, and an empty path to point at the bearer itself. Resolution SHALL be a method `resolve(from: Node): T?` that walks the path starting from `from` and returns the resolved node cast to `T`, or `null` if the path is invalid, the target does not exist, or the target is not of type `T`. `NodeRef` MUST be `@Serializable` so that the path persists in scene files. The resolved target MAY be cached internally and the cache MUST be invalidated when the bearing node is re-attached to the tree.

#### Scenario: NodeRef walks up and down

- **GIVEN** a tree where `Scene` has children `A` and `B`, and `A` has child `C`
- **WHEN** code calls `NodeRef<Node>(path = "../B").resolve(from = C)`
- **THEN** the result is the node `B`

#### Scenario: NodeRef returns null when target type does not match

- **GIVEN** a tree where node `Foo : Node2D` exists and a `NodeRef<Ball>(path = "Foo")` is held on the scene root
- **WHEN** code calls `ref.resolve(from = sceneRoot)`
- **AND** `Foo` is not a `Ball`
- **THEN** the result is `null`

#### Scenario: NodeRef returns null when path is invalid

- **GIVEN** a tree where node `A` has no child named `Missing`
- **WHEN** code calls `NodeRef<Node>(path = "Missing").resolve(from = A)`
- **THEN** the result is `null`

#### Scenario: Empty path resolves to the bearer

- **WHEN** code calls `NodeRef<Node>(path = "").resolve(from = nodeA)`
- **THEN** the result is `nodeA` itself

#### Scenario: NodeRef is serializable

- **WHEN** code holds a `NodeRef<Node2D>` instance and serializes it with `kotlinx.serialization` JSON
- **THEN** the resulting JSON contains the path string
- **AND** deserializing it yields a new `NodeRef<Node2D>` with the same path
- **AND** the deserialized `NodeRef` resolves correctly once placed in a live tree

#### Scenario: Resolution cache invalidates on re-attach

- **GIVEN** a `Paddle` holding `NodeRef<Node2D>(path = "../Ball")` and the paddle is part of a live scene
- **WHEN** the paddle is removed from the scene and then re-added
- **THEN** the next call to `ref.resolve(from = paddle)` recomputes the resolution rather than returning a stale cached value

### Requirement: Signal exposes a typed event bus per node

The engine SHALL provide a `Signal<T>` class usable as a per-node event bus. The class MUST expose `operator fun plusAssign(handler: (T) -> Unit)` to register handlers, `operator fun minusAssign(handler: (T) -> Unit)` to unregister handlers, and `fun emit(value: T)` to invoke all currently registered handlers in registration order. `Signal` MUST NOT be `@Serializable` itself, and SHALL be carried on nodes as a `@Transient` field; handlers registered in code are not persisted across save/load and MUST be re-registered after `onEnter`. Emission MUST tolerate handler registration and removal that occur during the emission itself by iterating over a snapshot of the handler list taken at the start of `emit`.

#### Scenario: plusAssign registers a handler

- **GIVEN** an empty `Signal<Int>`
- **WHEN** code calls `signal += { value -> received = value }`
- **AND** code calls `signal.emit(42)`
- **THEN** `received` equals `42`

#### Scenario: minusAssign unregisters a handler

- **GIVEN** a `Signal<Int>` with handler `h` registered
- **WHEN** code calls `signal -= h`
- **AND** code calls `signal.emit(5)`
- **THEN** `h` is not invoked

#### Scenario: emit invokes handlers in registration order

- **GIVEN** a `Signal<Int>` with handlers `h1`, `h2`, `h3` registered in that order
- **WHEN** code calls `signal.emit(0)`
- **THEN** invocation order is `h1`, `h2`, `h3`

#### Scenario: Registration during emit affects only future emissions

- **GIVEN** a `Signal<Int>` whose registered handler `h1` registers a second handler `h2` when invoked
- **WHEN** code calls `signal.emit(1)`
- **THEN** `h1` is invoked
- **AND** `h2` is NOT invoked during this `emit` call
- **AND** the next `signal.emit(2)` invokes both `h1` and `h2`

#### Scenario: Removal during emit takes effect immediately for subsequent handlers in the same emit

- **GIVEN** a `Signal<Int>` with handlers `h1` and `h2`, where `h1` removes `h2` when invoked
- **WHEN** code calls `signal.emit(1)`
- **THEN** `h1` is invoked
- **AND** `h2` is still invoked because emission iterates over the snapshot taken at the start

### Requirement: @Inspect marks properties as inspectable and serialized

The engine SHALL define an annotation `@Inspect` with `@Target(AnnotationTarget.PROPERTY)` and `@Retention(AnnotationRetention.RUNTIME)`. The annotation MAY carry an optional `displayName: String` (default empty). The annotation's semantic contract is: a property annotated with `@Inspect` is part of the node's serialized contract and is intended to be shown by a future editor; a property NOT annotated with `@Inspect` SHALL be explicitly annotated with `@Transient` so that it is excluded from the serialized form. This contract MUST be documented in `CLAUDE.md` so that contributors uphold it.

#### Scenario: @Inspect is present on every editor-facing property of a serializable node

- **WHEN** any class annotated with `@Serializable` that extends `Node` is inspected for `var` properties intended as initial configuration
- **THEN** each such property is annotated with `@Inspect`

#### Scenario: @Transient is present on every runtime-only property of a serializable node

- **WHEN** any class annotated with `@Serializable` that extends `Node` is inspected for `var` properties that hold transient runtime state
- **THEN** each such property is annotated with `@Transient`

#### Scenario: @Inspect carries an optional display name

- **WHEN** code reads `@Inspect(displayName = "Move speed")` on a property
- **THEN** the annotation's `displayName` value is `"Move speed"`

### Requirement: NodeRegistry maps type names to factories

The engine SHALL provide a `NodeRegistry` object that maintains a registry from fully-qualified type names (as `String`) to factory functions of shape `() -> Node`. Game modules MUST register every `Node` subclass that may appear in a serialized scene file by calling `NodeRegistry.register(KClass<out Node>, factory)` at startup, before any call to `SceneLoader.load`. Loading SHALL look up the `type` field of each node entry against the registry; if the type is not registered, the loader MUST throw `UnknownNodeTypeException` with the offending type name.

#### Scenario: Registered type is instantiable by name

- **GIVEN** code has called `NodeRegistry.register(Paddle::class) { Paddle() }`
- **WHEN** the loader encounters a node entry with `type = "com.neoutils.engine.games.pong.Paddle"`
- **THEN** the registry returns a fresh `Paddle()` instance via the factory

#### Scenario: Unknown type fails loud

- **GIVEN** no registration has been made for `com.example.Mystery`
- **WHEN** the loader encounters a node entry with `type = "com.example.Mystery"`
- **THEN** the loader throws `UnknownNodeTypeException` whose message names `com.example.Mystery`

### Requirement: SceneLoader round-trips a scene to JSON

The engine SHALL provide a `SceneLoader` with two operations: `save(scene: Scene): String` returns the JSON representation of the scene; `load(json: String): Scene` parses JSON and returns a fresh, detached `Scene` instance whose tree mirrors the file. The JSON document MUST follow this shape:

```json
{
  "version": 1,
  "root": {
    "type": "<fully-qualified Kotlin class name>",
    "name": "<string>",
    "properties": { "<inspect-property-name>": <value>, ... },
    "children": [ <node entry>, ... ]
  }
}
```

The `properties` map MUST contain exactly the values of the node's `@Inspect`-annotated properties, serialized via `kotlinx.serialization` JSON. The `children` array MUST preserve the order of `parent.children`. Loading MUST instantiate each node via `NodeRegistry`, apply its `properties`, then attach its children in order via `addChild`. Loading MUST NOT call `Scene.start()` on the resulting scene; the caller decides when to make it live. Saving and loading the same scene MUST be a round-trip: `save(load(save(scene)))` SHALL be byte-equal to `save(scene)` after canonicalization (whitespace-insensitive, key-ordered).

#### Scenario: save produces well-formed JSON with version and root

- **WHEN** code calls `SceneLoader.save(scene)`
- **THEN** the returned string parses as JSON
- **AND** the top-level object has fields `version` (integer 1) and `root` (object)
- **AND** `root` has fields `type`, `name`, `properties`, `children`

#### Scenario: load produces a detached scene

- **WHEN** code calls `SceneLoader.load(json)`
- **THEN** the returned `Scene`'s `isLive` property is `false`
- **AND** the scene is not registered with any `GameLoop`

#### Scenario: load preserves tree shape and inspect properties

- **GIVEN** a JSON document describing a `PongScene` with three children in a specific order, each with `@Inspect` properties set to specific values
- **WHEN** code calls `SceneLoader.load(json)` followed by `Scene.start()`
- **THEN** the loaded scene's children appear in the same order
- **AND** each child's `@Inspect` properties hold the values from the JSON

#### Scenario: Round-trip is stable

- **GIVEN** a scene `scene`
- **WHEN** code computes `json1 = SceneLoader.save(scene)` then `scene2 = SceneLoader.load(json1)` then `json2 = SceneLoader.save(scene2)`
- **THEN** `json1` and `json2` are equivalent JSON documents

#### Scenario: Loading does not invoke onEnter until start

- **GIVEN** a node type whose `onEnter` increments a counter
- **WHEN** code calls `SceneLoader.load(json)` on a scene containing that node
- **THEN** the counter has NOT been incremented
- **AND** after a subsequent `scene.start()`, the counter has been incremented exactly once

### Requirement: Pong scene file ships as proof of concept

The `:games:pong` module SHALL include `pong.scene.json` under `src/main/resources/` containing the serialized initial state of `PongScene`. The module's single entry point `Main.kt` SHALL load this resource via `SceneLoader.load` and run the resulting scene via the Skiko host; no code-only construction path SHALL ship as a parallel entry point. The resulting gameplay MUST be indistinguishable from the previous code-only build of `PongScene`: same paddles, same ball start, same colliders, same HUD layout.

#### Scenario: pong.scene.json exists and parses

- **WHEN** the file `:games:pong/src/main/resources/pong.scene.json` is read at runtime
- **THEN** `SceneLoader.load` returns a `Scene` whose root is a `PongScene`

#### Scenario: Pong's Main loads the scene file

- **WHEN** the user runs `./gradlew :games:pong:run`
- **THEN** `Main.kt` reads `pong.scene.json` from the classpath
- **AND** calls `SceneLoader.load` to build the scene
- **AND** hands the scene to `SkikoHost` for execution

#### Scenario: Loaded Pong matches the previous code-only behavior

- **WHEN** the Pong window is launched
- **THEN** the initial scene layout (paddles, ball, walls, goals, HUD) matches the layout produced by the prior code-only `PongScene` construction
- **AND** input response is identical to the prior code-only build
