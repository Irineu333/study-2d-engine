## MODIFIED Requirements

### Requirement: Pong is an executable standalone module

The project SHALL provide a `:games:pong` module that depends on `:engine`, `:engine-skiko`, and `:engine-scripting`, and contains a `main()` entry point opening a window hosting Pong via `SkikoHost`. The module MUST be runnable via `./gradlew :games:pong:run`. The module MUST NOT depend on any other game module. The module's `Main.kt` SHALL register a `KotlinScriptingHost` (with the appropriate manifest) before invoking `SceneLoader.load`.

#### Scenario: Pong runs from Gradle

- **WHEN** a developer runs `./gradlew :games:pong:run` from the project root
- **THEN** a desktop window opens displaying the Pong scene
- **AND** the game is responsive to keyboard input

#### Scenario: Pong uses only public engine API

- **WHEN** the `:games:pong` source is inspected
- **THEN** all engine interactions go through types exported by `:engine`, `:engine-skiko`, and `:engine-scripting`
- **AND** no internal/private API of any of those modules is referenced

#### Scenario: Pong depends on engine-scripting

- **WHEN** the build configuration of `:games:pong` is inspected
- **THEN** it declares a dependency on `:engine-scripting`

#### Scenario: Main.kt registers a ScriptHost before loading the scene

- **WHEN** the source of `:games:pong/src/main/kotlin/.../Main.kt` is inspected
- **THEN** it contains a call to `ScriptHosts.register(KotlinScriptingHost(...))` before any call to `SceneLoader.load`

### Requirement: Pong nodes have no-args constructors

Every script `.nengine.kts` file that defines a `Node` subclass used by Pong SHALL declare a primary no-args constructor (either explicitly or implicitly via the default constructor of an open subclass). All initial configuration SHALL be expressed as `var` properties on the class, each annotated with `@Inspect` (serialized contract) or `@Transient` (runtime-only state). The class itself MAY OR MAY NOT carry `@Serializable` from `kotlinx.serialization`; the `SceneLoader` does not depend on the class-level annotation, only on per-property reflection.

#### Scenario: Each Pong script class can be instantiated with no arguments

- **GIVEN** the active `ScriptHost` has compiled every script listed in Pong's manifest
- **WHEN** code calls `host.factoryFor(path)()` for each script path in the manifest
- **THEN** each call returns a valid instance with default property values

#### Scenario: Pong script vars carry @Inspect or @Transient

- **WHEN** each Pong script's top-level class is inspected
- **THEN** every `var` property is annotated either with `@Inspect` or with `@Transient`

### Requirement: Pong validates the engine surface end to end

The Pong module SHALL exercise all of the following engine capabilities: `Node` lifecycle (`onEnter`/`onUpdate`/`onRender`/`onExit`), `Transform`-based positioning, `Renderer` primitives (rect, circle, text), `Input` queries (keys), `Collider` + `PhysicsSystem` with at least one `onCollide` handler per moving node, `GameLoop` driving via `GameHost`, the `scene-serialization` primitives (`Signal<T>`, `NodeRef<T>`, `@Inspect`-annotated properties), the `SceneLoader` round-trip on `PongScene`, and the `scripting` capability (every gameplay node type is defined in a `.nengine.kts` script). No engine feature listed for the prepare-for-serialization or add-scripting changes MAY remain unexercised by Pong.

#### Scenario: Every engine capability has at least one usage in Pong

- **WHEN** the Pong source is reviewed against the `engine-core`, `skiko-runtime`, `scene-serialization`, and `scripting` capability lists
- **THEN** at least one usage of each listed feature is present

#### Scenario: Pong uses Signal for ball-to-score communication

- **WHEN** the Pong source is inspected for the wiring between `Ball` and `Score` nodes
- **THEN** the wiring is expressed via `Signal<T>` registrations (e.g. inside `onEnter`)

#### Scenario: Pong uses NodeRef for AI-to-ball reference

- **WHEN** the Pong source is inspected for the wiring between the AI paddle and the ball
- **THEN** the wiring is expressed via a `NodeRef<Node2D>` declared on the paddle and resolved at update time

#### Scenario: Pong exposes @Inspect properties

- **WHEN** the public configurable properties of the script-defined `Paddle`, `Ball`, `Score`, `Wall`, `Goal`, `PaddleCollider`, and `PongScene` are inspected
- **THEN** every property intended as initial configuration is annotated with `@Inspect`
- **AND** every property holding transient runtime state is annotated with `@Transient`

#### Scenario: Every Pong gameplay node is defined by a script

- **WHEN** the Pong source tree under `:games:pong/src/main/kotlin` is inspected
- **THEN** no class extending `com.neoutils.engine.scene.Node` (directly or transitively) is declared in Kotlin source
- **AND** every gameplay node type referenced from `pong.scene.json` resolves through `ScriptHost`

## ADDED Requirements

### Requirement: Pong ships gameplay nodes as scripts under resources

The `:games:pong` module SHALL ship a directory `src/main/resources/scripts/` containing one `.nengine.kts` file per gameplay node type. At minimum the directory SHALL contain: `paddle.nengine.kts`, `ball.nengine.kts`, `walls.nengine.kts`, `goal.nengine.kts` (or equivalent), `score.nengine.kts`, `center-line.nengine.kts`, `paddle-collider.nengine.kts`, and `pong-scene.nengine.kts`. Each script SHALL define exactly one top-level class extending `Node` (or a subclass). The classes SHALL implement the gameplay behavior previously implemented in Kotlin (movement, collision response, AI, scoring) without observable behavioral difference from the previous code-only build.

#### Scenario: scripts directory exists with the expected files

- **WHEN** the contents of `:games:pong/src/main/resources/scripts/` are listed
- **THEN** every file name listed above is present
- **AND** each file is non-empty and parses as a valid Kotlin script

#### Scenario: Loaded Pong matches the previous Kotlin-only behavior

- **WHEN** the Pong window is launched after the migration completes
- **THEN** the initial scene layout (paddles, ball, walls, goals, HUD) matches the layout produced by the prior Kotlin-only `PongScene` construction
- **AND** input response is identical to the prior build
- **AND** AI behavior is identical to the prior build
- **AND** scoring and ball reset are identical to the prior build

### Requirement: Pong manifest declares script compilation order

The `:games:pong` module SHALL declare a manifest listing every Pong script in compilation order (deepest dependency first, outermost dependent last). The manifest MAY be expressed as a literal `List<String>` inside `Main.kt` or as an external resource. The manifest SHALL be passed to `KotlinScriptingHost` at construction. The manifest order SHALL be: leaves (`paddle-collider`, `walls`, `goal`, `score`, `center-line`) → mid (`ball`, `paddle`) → root (`pong-scene`).

#### Scenario: Manifest is exhaustive

- **WHEN** the manifest in `Main.kt` is compared against the contents of `src/main/resources/scripts/`
- **THEN** every `.nengine.kts` file in the directory is present in the manifest
- **AND** no manifest entry references a missing file

#### Scenario: Manifest places dependencies before dependents

- **WHEN** the manifest is inspected
- **THEN** `paddle-collider.nengine.kts` appears before `paddle.nengine.kts`
- **AND** every script that defines a `Node` referenced by another script appears before that other script

### Requirement: pong.scene.json references scripts by path

The `pong.scene.json` file SHALL reference every gameplay node by its script path under `scripts/` (e.g. `"type": "scripts/paddle.nengine.kts"`). No `type` field in `pong.scene.json` SHALL be a fully-qualified Kotlin class name of a `:games:pong`-owned class. `Node2D`, `Node`, and other engine-provided types (which remain compiled Kotlin) MAY continue to appear by FQN, though Pong's tree is not expected to use them as gameplay-relevant types.

#### Scenario: All Pong-owned types in pong.scene.json are script paths

- **WHEN** `pong.scene.json` is parsed and every `type` field is collected
- **THEN** every `type` whose corresponding class originates from `:games:pong` is a string ending in `.kts`

#### Scenario: pong.scene.json round-trips

- **WHEN** code reads `pong.scene.json`, calls `SceneLoader.load(json)`, then `SceneLoader.save(scene)` on the result
- **THEN** the resulting JSON is equivalent to the original (after canonicalization)
