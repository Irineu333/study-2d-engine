# pong-sample Specification

## Purpose

Jogo de Pong jogável (humano vs IA) como módulo executável `:games:pong`, servindo como teste de aceitação vivo da fundação da engine. Exercita scene graph, lifecycle, renderer, input, colisão e game loop fim a fim.

## Requirements

### Requirement: Pong is an executable standalone module

The project SHALL provide a `:games:pong` module that depends on `:engine` and `:engine-compose` and contains a `main()` entry point opening a Compose Desktop window hosting a `GameSurface` rendering Pong. The module MUST be runnable via `./gradlew :games:pong:run`. The module MUST NOT depend on any other game module.

#### Scenario: Pong runs from Gradle

- **WHEN** a developer runs `./gradlew :games:pong:run` from the project root
- **THEN** a desktop window opens displaying the Pong scene
- **AND** the game is responsive to keyboard input

#### Scenario: Pong uses only public engine API

- **WHEN** the `:games:pong` source is inspected
- **THEN** all engine interactions go through types exported by `:engine` and `:engine-compose`
- **AND** no internal/private API of either module is referenced

### Requirement: Pong scene composition

The Pong scene SHALL contain the following node tree: two `Paddle` nodes (left labeled "left", right labeled "right"), a `Ball` node, four wall/goal `Collider` nodes (top, bottom, left goal, right goal), and a HUD subtree with two `Score` text nodes and an optional center-line decoration. Each `Paddle` MUST carry a child `PaddleCollider` (which is a `BoxCollider`). The `Ball` MUST itself extend `BoxCollider` (the ball **is** its collider, not a node that contains one) — no anonymous `BoxCollider` subclass MAY be used in the Pong codebase.

#### Scenario: Scene contains the expected nodes after construction

- **WHEN** a new `PongScene` is instantiated
- **THEN** its tree contains exactly: two paddles, one ball, four boundary colliders, two score texts

#### Scenario: Ball is a BoxCollider directly

- **WHEN** the `Ball` class is inspected
- **THEN** it extends `com.neoutils.engine.physics.BoxCollider`
- **AND** no separate child `BoxCollider` node is added to the ball

#### Scenario: No anonymous BoxCollider subclasses exist in Pong

- **WHEN** the `:games:pong` source tree is searched for occurrences of `object : BoxCollider`
- **THEN** no matches are found

### Requirement: Player paddle responds to keyboard input

The left paddle SHALL move vertically in response to keyboard input: a configured "up" key moves it up, a "down" key moves it down. Default bindings MUST be `W` for up and `S` for down. Movement MUST be frame-rate independent using `dt`. The paddle MUST be clamped to remain within the play field (between top and bottom walls).

#### Scenario: Holding W moves the left paddle up

- **WHEN** the user holds the `W` key for one second at default speed
- **THEN** the left paddle's vertical position decreases by `speed * 1.0` units (Y axis grows downward)
- **AND** does not pass the top wall

#### Scenario: Releasing key stops paddle

- **WHEN** the user releases both `W` and `S`
- **THEN** the left paddle's position remains constant in subsequent ticks

### Requirement: AI paddle tracks the ball

The right paddle SHALL be controlled by an AI routine that, each tick, moves the paddle vertically toward the ball's current vertical position, capped at a configurable maximum speed. The AI MUST be intentionally imperfect (max speed strictly less than ball max vertical speed, or with a tolerance band) so the human can score. The paddle SHALL identify the ball via a `NodeRef<Node2D>` property (e.g. `target`) so that the relationship is declarative and survives scene serialization; lambdas-in-constructor MUST NOT be used to express this dependency.

#### Scenario: AI paddle moves toward ball

- **WHEN** the ball's center is below the right paddle's center by more than the AI tolerance
- **THEN** on the next tick the right paddle moves downward (limited by its max speed)

#### Scenario: AI paddle does not exceed max speed

- **WHEN** the ball is far from the right paddle
- **THEN** the paddle's displacement per tick does not exceed `aiMaxSpeed * dt`

#### Scenario: AI target is a NodeRef, not a lambda

- **WHEN** the `Paddle` class is inspected
- **THEN** the property used to point at the ball is of type `NodeRef<Node2D>` (or a subtype)
- **AND** no `() -> Float`, `() -> Vec2`, or similar lambda property is declared for that purpose

### Requirement: Ball physics

The Ball SHALL move with a constant-magnitude velocity vector each tick (`position += velocity * dt`). The Ball MUST reflect on the X or Y axis when its collider overlaps with a paddle or wall: collisions with top/bottom walls reflect Y; collisions with paddles reflect X. The Ball's speed MAY increase modestly on each paddle hit, capped to a configured maximum.

#### Scenario: Ball reflects off top wall

- **WHEN** the ball overlaps the top wall while moving upward
- **THEN** the Y component of its velocity is negated
- **AND** the X component is unchanged

#### Scenario: Ball reflects off a paddle

- **WHEN** the ball overlaps either paddle's collider
- **THEN** the X component of its velocity is negated
- **AND** the ball does not pass through the paddle in the next tick

### Requirement: Score tracking and ball reset

When the ball's collider overlaps a goal collider, the opposite side's score MUST increment by one and the ball MUST reset to the field center with a randomized direction. Score values MUST be reflected in the HUD `Score` text nodes within the same frame. The scoring event SHALL be communicated from the `Ball` to the `Score` nodes (or to `PongScene`) via a `Signal<Goal.Side>` exposed by the ball; lambdas-in-constructor MUST NOT be used to wire the scoring callback.

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

### Requirement: Pong validates the engine surface end to end

The Pong module SHALL exercise all of the following engine capabilities: `Node` lifecycle (`onEnter`/`onUpdate`/`onRender`/`onExit`), `Transform`-based positioning, `Renderer` primitives (rect, circle, text), `Input` queries (keys), `Collider` + `PhysicsSystem` with at least one `onCollide` handler per moving node, `GameLoop` driving via `GameHost`, the `scene-serialization` primitives (`Signal<T>`, `NodeRef<T>`, `@Inspect`-annotated properties), and the `SceneLoader` round-trip on `PongScene`. No engine feature listed for the prepare-for-serialization change MAY remain unexercised by Pong.

#### Scenario: Every engine capability has at least one usage in Pong

- **WHEN** the Pong source is reviewed against the `engine-core`, `compose-runtime`, `skiko-runtime` and `scene-serialization` capability lists
- **THEN** at least one usage of each listed feature is present

#### Scenario: Pong uses Signal for ball-to-score communication

- **WHEN** the Pong source is inspected for the wiring between `Ball` and `Score` nodes
- **THEN** the wiring is expressed via `Signal<T>` registrations (e.g. inside `onEnter`)

#### Scenario: Pong uses NodeRef for AI-to-ball reference

- **WHEN** the Pong source is inspected for the wiring between the AI paddle and the ball
- **THEN** the wiring is expressed via a `NodeRef<Node2D>` declared on the paddle and resolved at update time

#### Scenario: Pong exposes @Inspect properties

- **WHEN** the public configurable properties of `Paddle`, `Ball`, `Score`, `Wall`, `Goal`, `PaddleCollider`, and `PongScene` are inspected
- **THEN** every property intended as initial configuration is annotated with `@Inspect`
- **AND** every property holding transient runtime state is annotated with `@Transient`

### Requirement: Pong nodes have no-args constructors

Every `Node` subclass in the `:games:pong` module that may appear in a serialized scene file SHALL provide a public no-args primary constructor. Initial configuration MUST be provided either by defaults on `@Inspect`-annotated properties or by post-construction assignment (e.g. via `apply { ... }`). Convenience factory functions MAY exist but MUST NOT replace the no-args constructor as the canonical instantiation path.

#### Scenario: Concrete Pong node classes can be instantiated with no arguments

- **WHEN** code evaluates `Paddle()`, `Ball()`, `Score()`, `Wall()`, `Goal()`, `PaddleCollider()`, `PongScene()`
- **THEN** each call returns a valid instance with default property values

#### Scenario: Pong nodes carry @Serializable and Inspect annotations

- **WHEN** each Pong node class is inspected
- **THEN** the class is annotated with `@Serializable` (kotlinx.serialization)
- **AND** every `var` property is annotated with either `@Inspect` or `@Transient`
