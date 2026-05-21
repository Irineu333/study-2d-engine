## MODIFIED Requirements

### Requirement: NodeRegistry maps type names to factories

The engine SHALL provide a `NodeRegistry` object that maintains a registry from fully-qualified type names (as `String`) to factory functions of shape `() -> Node`. Game modules MUST register every compiled `Node` subclass that may appear in a serialized scene file by calling `NodeRegistry.register(KClass<out Node>, factory)` at startup, before any call to `SceneLoader.load`. A type whose string representation ends with `.kts` SHALL NOT be looked up in `NodeRegistry`; such types are routed to the active `ScriptHost` by `SceneLoader` instead. For non-script types, if the type is not registered, `NodeRegistry.create(type)` MUST throw `UnknownNodeTypeException` with the offending type name.

#### Scenario: Registered type is instantiable by name

- **GIVEN** code has called `NodeRegistry.register(Paddle::class) { Paddle() }`
- **WHEN** the loader encounters a node entry with `type = "com.neoutils.engine.games.pong.Paddle"`
- **THEN** the registry returns a fresh `Paddle()` instance via the factory

#### Scenario: Unknown compiled type fails loud

- **GIVEN** no registration has been made for `com.example.Mystery`
- **AND** the string does not end with `.kts`
- **WHEN** the loader encounters a node entry with `type = "com.example.Mystery"`
- **THEN** the loader throws `UnknownNodeTypeException` whose message names `com.example.Mystery`

#### Scenario: Script paths bypass NodeRegistry

- **GIVEN** `NodeRegistry.register` has NEVER been called for the string `"scripts/paddle.nengine.kts"`
- **WHEN** the loader encounters a node entry with `type = "scripts/paddle.nengine.kts"`
- **THEN** `NodeRegistry.create` is NOT called for this type
- **AND** the loader consults `ScriptHosts.current()` instead

### Requirement: SceneLoader round-trips a scene to JSON

The engine SHALL provide a `SceneLoader` with two operations: `save(scene: Scene): String` returns the JSON representation of the scene; `load(json: String): Scene` parses JSON and returns a fresh, detached `Scene` instance whose tree mirrors the file. The JSON document MUST follow this shape:

```json
{
  "version": 1,
  "root": {
    "type": "<fully-qualified Kotlin class name OR classpath-relative .kts script path>",
    "name": "<string>",
    "properties": { "<inspect-property-name>": <value>, ... },
    "children": [ <node entry>, ... ]
  }
}
```

The `type` field MAY be either a fully-qualified Kotlin class name (existing behavior) or a classpath-relative path ending in `.kts` (new behavior). When loading, `SceneLoader` MUST route the type as follows:

- If `type` ends with `.kts`, `SceneLoader` MUST obtain a factory via `ScriptHosts.current()?.factoryFor(type)`. If no `ScriptHost` is registered, the loader MUST throw an exception whose message names the offending type and explains that no `ScriptHost` is registered.
- Otherwise, `SceneLoader` MUST obtain a factory via `NodeRegistry.create(type)`.

The factory invocation produces the node instance, then `name` and `properties` are applied via reflection exactly as before. The `properties` map MUST contain exactly the values of the node's `@Inspect`-annotated properties, serialized via `kotlinx.serialization` JSON. The `children` array MUST preserve the order of `parent.children`. Loading MUST instantiate each node, apply its `properties`, then attach its children in order via `addChild`. Loading MUST NOT call `Scene.start()` on the resulting scene; the caller decides when to make it live. Saving and loading the same scene MUST be a round-trip: `save(load(save(scene)))` SHALL be byte-equal to `save(scene)` after canonicalization (whitespace-insensitive, key-ordered).

When `SceneLoader.save` serializes a node whose class came from a script (i.e. was instantiated via `ScriptHost.factoryFor`), the saved `type` field MUST be the script path that produced the class, not the runtime FQN of the generated class. The mapping from class to source path is the responsibility of `ScriptHost`; `SceneLoader` MUST consult `ScriptHosts.current()?.pathFor(node::class)` and fall back to `node::class.qualifiedName` only when no mapping is found.

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

#### Scenario: Script-typed entry routes to ScriptHost

- **GIVEN** a registered `KotlinScriptingHost` and a JSON entry with `type = "scripts/paddle.nengine.kts"`
- **WHEN** code calls `SceneLoader.load(json)`
- **THEN** the host's `factoryFor("scripts/paddle.nengine.kts")` is invoked exactly once
- **AND** the resulting node is an instance of the class defined in that script
- **AND** the node's `@Inspect` properties hold the values from the JSON

#### Scenario: Script-typed entry without registered host fails fast

- **GIVEN** a JSON entry with `type = "scripts/foo.nengine.kts"` and `ScriptHosts.current()` returns `null`
- **WHEN** code calls `SceneLoader.load(json)`
- **THEN** the call throws an exception
- **AND** the exception message names the offending type and explains that no `ScriptHost` is registered

#### Scenario: save round-trips script-typed nodes by script path

- **GIVEN** a live scene whose root is a node instantiated by `ScriptHost.factoryFor("scripts/pong.nengine.kts")`
- **WHEN** code calls `SceneLoader.save(scene)`
- **THEN** the returned JSON's root `type` is `"scripts/pong.nengine.kts"`
- **AND** is NOT the runtime FQN of the script-generated class

## ADDED Requirements

### Requirement: ScriptHost exposes a reverse path lookup for save

`ScriptHost` SHALL expose `fun pathFor(klass: KClass<out Node>): String?` returning the script path that originally produced `klass`, or `null` if `klass` was not produced by this host. This operation MUST be the inverse of `compile`: for every path `p` such that `compile(p)` returns a class `K`, `pathFor(K)` MUST return `p`. The mapping MUST be populated lazily as scripts are compiled; classes never compiled by this host MUST NOT appear in the map.

#### Scenario: pathFor recovers the source path

- **GIVEN** `host.compile("scripts/foo.nengine.kts")` has returned class `Foo`
- **WHEN** code calls `host.pathFor(Foo::class)`
- **THEN** the result is `"scripts/foo.nengine.kts"`

#### Scenario: pathFor returns null for non-script classes

- **WHEN** code calls `host.pathFor(Node2D::class)` on a host that never compiled a script producing `Node2D`
- **THEN** the result is `null`
