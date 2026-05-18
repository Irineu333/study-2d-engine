## ADDED Requirements

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

The Pong scene SHALL contain the following node tree: two `Paddle` nodes (left labeled "left", right labeled "right"), a `Ball` node, four wall/goal `Collider` nodes (top, bottom, left goal, right goal), and a HUD subtree with two `Score` text nodes and an optional center-line decoration. Paddles and ball MUST each carry a `BoxCollider`.

#### Scenario: Scene contains the expected nodes after construction

- **WHEN** a new `PongScene` is instantiated
- **THEN** its tree contains exactly: two paddles, one ball, four boundary colliders, two score texts

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

The right paddle SHALL be controlled by an AI routine that, each tick, moves the paddle vertically toward the ball's current vertical position, capped at a configurable maximum speed. The AI MUST be intentionally imperfect (max speed strictly less than ball max vertical speed, or with a tolerance band) so the human can score.

#### Scenario: AI paddle moves toward ball

- **WHEN** the ball's center is below the right paddle's center by more than the AI tolerance
- **THEN** on the next tick the right paddle moves downward (limited by its max speed)

#### Scenario: AI paddle does not exceed max speed

- **WHEN** the ball is far from the right paddle
- **THEN** the paddle's displacement per tick does not exceed `aiMaxSpeed * dt`

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

When the ball's collider overlaps a goal collider, the opposite side's score MUST increment by one and the ball MUST reset to the field center with a randomized direction. Score values MUST be reflected in the HUD `Score` text nodes within the same frame.

#### Scenario: Ball crossing right goal scores for left

- **WHEN** the ball reaches the right goal
- **THEN** the left `Score` text displays an incremented value
- **AND** the ball is positioned at the field center on the next tick

#### Scenario: Score persists across multiple goals

- **WHEN** the left player has scored 3 goals
- **THEN** the left `Score` text displays "3"
- **AND** is not reset by subsequent ball resets

### Requirement: Pong validates the engine surface end to end

The Pong module SHALL exercise all of the following engine capabilities: `Node` lifecycle (`onEnter`/`onUpdate`/`onRender`/`onExit`), `Transform`-based positioning, `Renderer` primitives (rect, circle, text), `Input` queries (keys), `Collider` + `PhysicsSystem` with at least one `onCollide` handler per moving node, and `GameLoop` driving via `GameSurface`. No engine feature listed for this change MAY remain unexercised by Pong.

#### Scenario: Every engine capability has at least one usage in Pong

- **WHEN** the Pong source is reviewed against the `engine-core` and `compose-runtime` capability lists
- **THEN** at least one usage of each listed feature is present
