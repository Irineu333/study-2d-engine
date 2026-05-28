## MODIFIED Requirements

### Requirement: Pong scene composition

The Pong scene SHALL contain the following node tree: two `Paddle` nodes (left labeled "left", right labeled "right"), a `Ball` node, four wall/goal `Collider` nodes (top, bottom, left goal, right goal), an optional center-line decoration in the world, and a **HUD `CanvasLayer`** subtree containing two `Score` text nodes (left, right). Each `Paddle` MUST carry a child `BoxCollider` whose `size` mirrors the paddle's `size`. The `Ball` MUST itself extend `BoxCollider` (the ball **is** its collider, not a node that contains one) — no anonymous `BoxCollider` subclass MAY be used in the Pong codebase. The wall and paddle-child colliders SHALL be plain `com.neoutils.engine.physics.BoxCollider` instances; Pong MUST NOT declare empty `BoxCollider` subclasses (e.g. `Wall`, `PaddleCollider`) in scripts or Kotlin source.

The scene file SHALL author every gameplay node position in the 800×600 world coordinate system declared by the `Camera2D`: paddles at fixed `transform.position` values that center them vertically and offset them horizontally by `PADDLE_MARGIN` from each goal; the top wall at `Vec2(0, 0)` with full play-field width; the bottom wall at `Vec2(0, 600 - WALL_THICKNESS)`; the goals bracketing the play field at `x = -GOAL_THICKNESS` and `x = 800`; the ball authored with `fieldCenter = Vec2(400, 300)`. **Score labels SHALL NOT live in world-space** — they live inside the HUD `CanvasLayer` with positions expressed in screen pixels (left score near top-left, right score near top-right). The center-line decoration, if present, MAY remain in world-space.

The Pong `pong_scene.py` script SHALL NOT contain a `_layout(width, height)` function or any equivalent runtime reposition routine — the world is fixed and the camera handles surface mapping. The script MAY retain a `_ready` for signal wiring (e.g. connecting the ball's `scored` signal to the scoreboards) and nothing else.

#### Scenario: Scene contains the expected nodes after construction

- **WHEN** a new `PongScene` is instantiated
- **THEN** its tree contains exactly: two paddles, one ball, four boundary colliders, plus one `CanvasLayer` (HUD) holding two score `Label`s

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

#### Scenario: Gameplay nodes have fixed world-space positions in scene.json

- **WHEN** `pong/scene.json` is inspected
- **THEN** every gameplay node (paddles, walls, goals, ball, center line) carries a `transform.position` value in the 800×600 world coordinate system
- **AND** no gameplay node's position is `Vec2(0, 0)` placeholder waiting to be filled by a script

#### Scenario: Score labels live inside a CanvasLayer

- **WHEN** `pong/scene.json` is inspected
- **THEN** the two score labels (left and right) are children of a `CanvasLayer` node, NOT direct children of the world root
- **AND** their `transform.position` values are screen-pixel coordinates (e.g. top-left at `Vec2(20, 20)`, top-right computed relative to the surface)
- **AND** the labels render at constant screen positions regardless of any Camera2D zoom or pan applied to the world

#### Scenario: pong_scene.py does not reposition nodes at runtime

- **WHEN** `pong/scripts/pong_scene.py` is inspected
- **THEN** no function named `_layout` (or equivalent reposition routine that reads `scene.size`/`scene.width`/`scene.height` to assign `transform`) exists
- **AND** no read of `self._node.width`, `self._node.height`, `scene.size`, or `scene.width`/`scene.height` appears in the script
- **AND** if `_process` exists at all, it does not assign `transform` on any node based on surface size
