## MODIFIED Requirements

### Requirement: Pong scene composition

The Pong scene SHALL contain the following node tree: two `Paddle` nodes (left labeled "left", right labeled "right"), a `Ball` node, four wall/goal `Collider` nodes (top, bottom, left goal, right goal), and a HUD subtree with two `Score` text nodes and an optional center-line decoration. Each `Paddle` MUST carry a child `BoxCollider` whose `size` mirrors the paddle's `size`. The `Ball` MUST itself extend `BoxCollider` (the ball **is** its collider, not a node that contains one) — no anonymous `BoxCollider` subclass MAY be used in the Pong codebase. The wall and paddle-child colliders SHALL be plain `com.neoutils.engine.physics.BoxCollider` instances; Pong MUST NOT declare empty `BoxCollider` subclasses (e.g. `Wall`, `PaddleCollider`) in scripts or Kotlin source.

#### Scenario: Scene contains the expected nodes after construction

- **WHEN** a new `PongScene` is instantiated
- **THEN** its tree contains exactly: two paddles, one ball, four boundary colliders, two score texts

#### Scenario: Ball is a BoxCollider directly

- **WHEN** the `Ball` class is inspected
- **THEN** it extends `com.neoutils.engine.physics.BoxCollider`
- **AND** no separate child `BoxCollider` node is added to the ball

#### Scenario: Paddle child collider is a plain BoxCollider

- **WHEN** a `Paddle` instance is constructed and `onEnter` runs
- **THEN** it has exactly one child of type `com.neoutils.engine.physics.BoxCollider`
- **AND** the child's runtime class is `BoxCollider` itself, not a subclass

#### Scenario: No anonymous BoxCollider subclasses exist in Pong

- **WHEN** the `:games:pong` source tree is searched for occurrences of `object : BoxCollider`
- **THEN** no matches are found

#### Scenario: No empty BoxCollider subclasses exist in Pong

- **WHEN** the `:games:pong/src/main/resources/scripts/` directory and `:games:pong/src/main/kotlin` source tree are searched
- **THEN** no file declares an empty subclass of `BoxCollider` (i.e. a class whose body is empty or only contains property defaults with no overrides)

### Requirement: Score tracking and ball reset

When the ball's collider overlaps a goal collider, the opposite side's score MUST increment by one and the ball MUST reset to the field center with a randomized direction. Score values MUST be reflected in the HUD `Score` text nodes within the same frame. The scoring event SHALL be communicated from the `Ball` to the `Score` nodes (or to `PongScene`) via a `Signal<Goal.Side>` exposed by the ball; lambdas-in-constructor MUST NOT be used to wire the scoring callback. The `Ball.onCollide` dispatch SHALL identify collision categories by scene structure — `other is Goal`, `other.parent is Paddle`, or fall-through to plain `BoxCollider` (wall) — and MUST NOT branch on `::class.simpleName` string literals for the paddle and wall cases.

#### Scenario: Ball crossing right goal scores for left

- **WHEN** the ball reaches the right goal
- **THEN** the left `Score` text displays an incremented value
- **AND** the ball is positioned at the field center on the next tick

#### Scenario: Score persists across multiple goals

- **WHEN** the left player has scored 3 goals
- **THEN** the left `Score` text displays "3"
- **AND** is not reset by subsequent ball resets

#### Scenario: Ball exposes a scoring Signal

- **WHEN** the `Ball` class is inspected
- **THEN** it exposes a public `Signal<Goal.Side>` (e.g. `onScore`) that is emitted whenever a goal collision occurs
- **AND** no `(Goal.Side) -> Unit` parameter exists on its constructor

#### Scenario: Ball dispatch uses structural checks for paddle and wall

- **WHEN** the body of `Ball.onCollide` is inspected
- **THEN** the paddle case is selected by an `is Paddle` check on `other.parent` (or equivalent structural test)
- **AND** the wall case is reached as the fall-through for `BoxCollider` instances that are neither `Goal` nor a paddle child
- **AND** no `when` branch compares `other::class.simpleName` (or `.java.simpleName`) to the strings `"Wall"` or `"PaddleCollider"`

### Requirement: Pong validates the engine surface end to end

The Pong module SHALL exercise all of the following engine capabilities: `Node` lifecycle (`onEnter`/`onUpdate`/`onRender`/`onExit`), `Transform`-based positioning, `Renderer` primitives (rect, circle, text), `Input` queries (keys), `Collider` + `PhysicsSystem` with at least one `onCollide` handler per moving node, `GameLoop` driving via `GameHost`, the `scene-serialization` primitives (`Signal<T>`, `NodeRef<T>`, `@Inspect`-annotated properties), the `SceneLoader` round-trip on `PongScene`, and the `scripting` capability (every gameplay node type with non-trivial behavior is defined in a `.nengine.kts` script). Pure tag-only subclasses of engine types MUST NOT exist as scripts or Kotlin classes; engine types (e.g. `BoxCollider`) SHALL be used directly when a gameplay node carries no behavior beyond what the engine type already provides.

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

- **WHEN** the public configurable properties of the script-defined `Paddle`, `Ball`, `Score`, `Goal`, `CenterLine`, and `PongScene` are inspected
- **THEN** every property intended as initial configuration is annotated with `@Inspect`
- **AND** every property holding transient runtime state is annotated with `@Transient`

#### Scenario: Every Pong gameplay node with behavior is defined by a script

- **WHEN** the Pong source tree under `:games:pong/src/main/kotlin` is inspected
- **THEN** no class extending `com.neoutils.engine.scene.Node` (directly or transitively) is declared in Kotlin source
- **AND** every node type referenced from `pong.scene.json` either resolves through `ScriptHost` (gameplay scripts) or maps to a built-in registered in `NodeRegistry.registerEngineTypes()` (engine types used as-is, e.g. `BoxCollider`)

### Requirement: Pong ships gameplay nodes as scripts under resources

The `:games:pong` module SHALL ship a directory `src/main/resources/scripts/` containing one `.nengine.kts` file per gameplay node type that carries behavior of its own. At minimum the directory SHALL contain: `paddle.nengine.kts`, `ball.nengine.kts`, `goal.nengine.kts`, `score.nengine.kts`, `center-line.nengine.kts`, and `pong-scene.nengine.kts`. Tag-only subclasses (`paddle-collider.nengine.kts`, `walls.nengine.kts`) MUST NOT exist — the corresponding nodes in the scene file SHALL reference `com.neoutils.engine.physics.BoxCollider` by FQN instead. Each remaining script SHALL define exactly one top-level class extending `Node` (or a subclass). The classes SHALL implement the gameplay behavior previously implemented in Kotlin (movement, collision response, AI, scoring) without observable behavioral difference from the previous code-only build.

#### Scenario: scripts directory exists with the expected files

- **WHEN** the contents of `:games:pong/src/main/resources/scripts/` are listed
- **THEN** every file name listed above is present
- **AND** each file is non-empty and parses as a valid Kotlin script

#### Scenario: scripts directory excludes tag-only files

- **WHEN** the contents of `:games:pong/src/main/resources/scripts/` are listed
- **THEN** no file named `paddle-collider.nengine.kts` is present
- **AND** no file named `walls.nengine.kts` is present

#### Scenario: Loaded Pong matches the previous Kotlin-only behavior

- **WHEN** the Pong window is launched after the migration completes
- **THEN** the initial scene layout (paddles, ball, walls, goals, HUD) matches the layout produced by the prior `PongScene` construction
- **AND** input response is identical to the prior build
- **AND** AI behavior is identical to the prior build
- **AND** scoring and ball reset are identical to the prior build

### Requirement: Pong manifest declares script compilation order

The `:games:pong` module SHALL declare a manifest listing every Pong script in compilation order (deepest dependency first, outermost dependent last). The manifest MAY be expressed as a literal `List<String>` inside `Main.kt` or as an external resource. The manifest SHALL be passed to `KotlinScriptingHost` at construction. The manifest order SHALL be: leaves (`goal`, `score`, `center-line`) → mid (`ball`, `paddle`) → root (`pong-scene`). The manifest MUST NOT contain entries for tag-only scripts that no longer exist (`paddle-collider`, `walls`).

#### Scenario: Manifest is exhaustive

- **WHEN** the manifest in `Main.kt` is compared against the contents of `src/main/resources/scripts/`
- **THEN** every `.nengine.kts` file in the directory is present in the manifest
- **AND** no manifest entry references a missing file

#### Scenario: Manifest excludes removed tag-only scripts

- **WHEN** the manifest in `Main.kt` is inspected
- **THEN** it does not contain `scripts/paddle-collider.nengine.kts`
- **AND** it does not contain `scripts/walls.nengine.kts`

#### Scenario: Manifest places dependencies before dependents

- **WHEN** the manifest is inspected
- **THEN** every script that defines a `Node` referenced by another script appears before that other script
- **AND** `goal.nengine.kts` appears before `ball.nengine.kts`
- **AND** `paddle.nengine.kts` appears before `pong-scene.nengine.kts`

### Requirement: pong.scene.json references scripts by path

The `pong.scene.json` file SHALL reference every gameplay node that carries Pong-owned behavior by its script path under `scripts/` (e.g. `"type": "scripts/paddle.nengine.kts"`). No `type` field in `pong.scene.json` SHALL be a fully-qualified Kotlin class name of a `:games:pong`-owned class. Nodes whose behavior is provided entirely by an engine type (e.g. plain `BoxCollider` walls) SHALL reference that engine type by its fully-qualified class name (e.g. `"type": "com.neoutils.engine.physics.BoxCollider"`), which the `SceneLoader` resolves through `NodeRegistry`.

#### Scenario: All Pong-owned types in pong.scene.json are script paths

- **WHEN** `pong.scene.json` is parsed and every `type` field is collected
- **THEN** every `type` whose corresponding class originates from `:games:pong` is a string ending in `.kts`

#### Scenario: Wall nodes use engine BoxCollider by FQN

- **WHEN** `pong.scene.json` is parsed
- **THEN** the entries named `topWall` and `bottomWall` have `type` equal to `com.neoutils.engine.physics.BoxCollider`

#### Scenario: pong.scene.json round-trips

- **WHEN** code reads `pong.scene.json`, calls `SceneLoader.load(json)`, then `SceneLoader.save(scene)` on the result
- **THEN** the resulting JSON is equivalent to the original (after canonicalization)
