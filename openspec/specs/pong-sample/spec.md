# pong-sample Specification

## Purpose

Jogo de Pong jogável (humano vs IA) como módulo executável `:games:pong`, servindo como teste de aceitação vivo da fundação da engine. Exercita scene graph, lifecycle, renderer, input, colisão e game loop fim a fim.
## Requirements
### Requirement: Pong is an executable standalone module

O projeto SHALL prover um módulo `:games:pong` que depende de `:engine`, `:engine-skiko`, `:engine-bundle` e `:engine-bundle-python`, e contém um entry point `main()` que abre uma janela hospedando Pong via `SkikoHost`. O módulo MUST ser executável via `./gradlew :games:pong:run`. O módulo MUST NOT depender de nenhum outro módulo de jogo. O `Main.kt` SHALL construir uma única instância de `PythonScriptHost` via `PythonScriptHost.create()` e injetá-la no `BundleLoader` via o parâmetro `scripting`. O `Main.kt` SHALL carregar a cena via `BundleLoader.fromResources("pong", scripting = python)` por padrão e MAY aceitar um path opcional via argumento de programa para carregar via `BundleLoader.fromPath(File(args[0]), scripting = python)` (cenário de editor / verificação de disco). O `Main.kt` MUST NOT registrar tipos da engine no `NodeRegistry` manualmente nem declarar manifesto de scripts; a única dependência explícita relativa a scripting é a construção do `PythonScriptHost`.

#### Scenario: Pong runs from Gradle

- **WHEN** um desenvolvedor executa `./gradlew :games:pong:run` da raiz do projeto
- **THEN** uma janela desktop abre exibindo a cena Pong
- **AND** o jogo é responsivo a input de teclado

#### Scenario: Pong loads from a filesystem bundle when a path argument is provided

- **GIVEN** uma pasta `<dir>` que é um bundle Pong válido (`scene.json` + `scripts/`)
- **WHEN** um desenvolvedor executa `./gradlew :games:pong:run --args="<dir>"`
- **THEN** o `Main.kt` resolve o bundle via `BundleLoader.fromPath(File(<dir>), scripting = python)`
- **AND** o jogo abre com a mesma cena que `fromResources("pong", scripting = python)` produziria sobre o mesmo conteúdo

#### Scenario: Pong uses only public engine API

- **WHEN** o source de `:games:pong` é inspecionado
- **THEN** todas as interações com engine passam por tipos exportados por `:engine`, `:engine-skiko`, `:engine-bundle` e `:engine-bundle-python`
- **AND** nenhuma API interna/privada desses módulos é referenciada

#### Scenario: Pong depends on engine-bundle and engine-bundle-python

- **WHEN** o build configuration de `:games:pong` é inspecionado
- **THEN** declara dependência em `:engine-bundle`
- **AND** declara dependência em `:engine-bundle-python`
- **AND** NÃO declara dependência em `:engine-scripting` (que não existe)
- **AND** NÃO declara dependência em `kotlin-scripting-*`

#### Scenario: Main.kt is concise

- **WHEN** o source de `:games:pong/src/main/kotlin/.../Main.kt` é inspecionado
- **THEN** o corpo de `main()` se resume a (1) construir `val python = PythonScriptHost.create()`, (2) escolher entre `BundleLoader.fromResources("pong", scripting = python)` e `BundleLoader.fromPath(File(args[0]), scripting = python)` (essa escolha é o único condicional admissível), e (3) uma única chamada a `SkikoHost().run(...)`
- **AND** NÃO contém referência a `PythonScriptHost.install`, `ScriptHostRegistry`, `KotlinScriptingHost`, `ScriptHosts` (formato antigo), `NodeRegistry.registerEngineTypes()`, `classLoader.getResource`, nem manifesto de scripts

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

### Requirement: Paddles are script-controlled CharacterBody2D solids

Each paddle SHALL be a `com.neoutils.engine.physics.CharacterBody2D` (NOT a `StaticBody2D`), honoring engine invariant #3: a solid body moved by a script is a `CharacterBody2D` driven through `moveAndCollide`, never a `StaticBody2D` teleported by writing `position` directly. The paddle script SHALL declare `# extends CharacterBody2D`, and the scene node SHALL be typed `CharacterBody2D` — both kept in sync because the script's `extends` is validated by simple name against `NodeRegistry` and MUST match the node's class.

Each tick the paddle SHALL compute its desired vertical displacement `dy` (from input or AI) and apply it with `moveAndCollide(Vec2(0, dy))`, so the motion is swept: the paddle **stops at the contact** with any other `PhysicsBody2D` instead of teleporting into it. As a direct consequence, a paddle MUST NOT be able to push the ball through or into a wall (the squeeze trap is removed at the source). After `moveAndCollide`, the paddle SHALL re-pin its horizontal coordinate to the value it had before the move, so any horizontal depenetration from a transient overlap cannot make the paddle drift off its column. The paddle SHALL remain within the play field by colliding with the top and bottom wall bodies (swept), NOT by a hand-written numeric clamp; the reachable vertical range is therefore bounded by the walls.

#### Scenario: Paddle nodes are CharacterBody2D

- **WHEN** the Pong scene is loaded
- **THEN** both paddle nodes (`left` and `right`) are instances of `com.neoutils.engine.physics.CharacterBody2D`
- **AND** the paddle script declares `# extends CharacterBody2D`
- **AND** no paddle node is a `StaticBody2D`

#### Scenario: Paddle moves via moveAndCollide, not by writing position

- **WHEN** `paddle.py::_physics_process` runs with a non-zero desired displacement
- **THEN** the paddle advances by calling `moveAndCollide(Vec2(0, dy))`
- **AND** it does NOT set `self.position` to the unswept target as its movement mechanism

#### Scenario: Paddle stops at the ball instead of squeezing it

- **WHEN** the paddle moves toward the ball while the ball rests against a wall
- **THEN** the paddle's swept motion stops at contact with the ball
- **AND** the ball is not pushed into or through the wall, and does not become trapped

#### Scenario: Paddle stays on its horizontal column

- **WHEN** the paddle begins a tick transiently overlapping the ball and `moveAndCollide` reports a depenetration with a horizontal component
- **THEN** after the move the paddle's `position.x` equals its `position.x` at the start of the tick
- **AND** the paddle never drifts horizontally out of its lane

#### Scenario: Paddle is bounded by the walls, not a numeric clamp

- **WHEN** the paddle is driven continuously toward the top or bottom wall
- **THEN** it stops against the wall body via swept collision
- **AND** `paddle.py` contains no hand-written `[0, max_y]` position clamp

### Requirement: Ball physics

The Ball SHALL be a `com.neoutils.engine.physics.CharacterBody2D` whose collision shape is a `CircleShape2D`. Each tick the Ball SHALL advance its motion with `moveAndCollide(velocity * dt)` (Godot-style kinematic sweep), so it stops exactly at the first contact with no tunneling at high speed; the engine does NOT integrate `velocity` automatically. On a resolved contact the Ball SHALL react in script by classifying the contact:

- **Paddle face** — the contact is a paddle (group `paddles`) AND the ball's center lies **beside** the paddle (its center-x is outside the paddle's horizontal span, i.e. the ball is pressing on a vertical face, front corners included). The Ball applies the angle-based "english" bounce: the horizontal direction flips to the side the ball is on (its center-x vs the paddle center-x) and the vertical component is derived from where the ball struck the face, with the speed allowed to increase modestly per hit up to a configured maximum.
- **Paddle top/bottom edge, or wall** — every other contact (including a contact whose ball center-x is within the paddle's horizontal span, i.e. a true top/bottom edge). The Ball reflects its velocity across the contact normal (`v' = v - 2(v·n)n`).

Classification MUST be geometric by the ball center's **horizontal** position relative to the paddle (beside the paddle ⇒ face; within the paddle's horizontal span ⇒ top/bottom edge), NOT a comparison of the contact normal's dominant axis (`abs(n.x) > abs(n.y)`). The diagonal normal at a front corner (`|n.x| ≈ |n.y|`) is then resolved deterministically as a face hit (x reversed decisively), never as a weak diagonal reflection that the chasing paddle could pin. The Ball script MUST NOT manually advance its own `position` to escape a starting overlap: `moveAndCollide` now applies outward (separating) motion on a starting overlap itself, so a paddle pressing a marginal overlap cannot freeze the ball without any script-side nudge. Walls and goals keep their current roles: walls are solid bodies picked up by the sweep; goals are `Area2D` sensors handled on the `_on_area_entered` path.

#### Scenario: Ball reflects off top wall

- **WHEN** the ball's swept motion contacts the top wall while moving upward
- **THEN** its velocity is reflected across the wall's (vertical-facing) contact normal, negating the Y component
- **AND** the X component is unchanged

#### Scenario: Ball gets the english bounce on a paddle face

- **WHEN** the ball's swept motion contacts a paddle and the ball's center is beside the paddle's horizontal span
- **THEN** the horizontal direction flips to the side the ball is on (away from the paddle)
- **AND** the vertical component is set from the ball's offset along the face (the angle-based bounce)
- **AND** the ball does not pass through the paddle on the next tick

#### Scenario: Ball corner hit does not trap

- **WHEN** the ball contacts a paddle front corner, producing a near-diagonal contact normal, while the AI paddle chases and re-presses the contact
- **THEN** the contact is classified by the ball center's horizontal position (beside the paddle ⇒ face), not by `abs(n.x) > abs(n.y)`, so the horizontal velocity reverses decisively
- **AND** `moveAndCollide` applies the ball's outward bounce velocity on the starting overlap, so the ball leaves the corner, never oscillating or freezing in place — with no manual `position` nudge in the script

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

### Requirement: Pong nodes have no-args constructors and Python @export semantics

Todo arquivo `.py` em `pong/scripts/` SHALL declarar exports como atribuições anotadas no top-level do módulo, no formato `<name>: <Type> = <default>`. As classes/módulos NÃO usam `@Inspect`/`@Transient` (esses são conceitos do mundo Kotlin que sai). Estado em runtime usado apenas internamente SHALL ser declarado dentro de hooks (variáveis locais ou `self._private = ...` em `on_enter`) — não no top-level. Como Python tem snake_case, **os nomes de exports usam snake_case** (`up_key`, `ai_max_speed`); a engine não exige correspondência com camelCase Kotlin.

#### Scenario: Each Pong script has @export-able top-level annotations

- **WHEN** cada `.py` em `pong/scripts/` é inspecionado pelo AST inspector do `PythonScriptHost`
- **THEN** todos os valores de configuração inicial aparecem como `AnnAssign` no top-level
- **AND** cada um vira um `ExportedProperty` na lista `Script.exports`

#### Scenario: Runtime-only state is not exported

- **WHEN** uma variável de estado puramente interno (ex.: o `BoxCollider` filho criado em `on_enter`) é declarada
- **THEN** ela aparece como `self._collider = ...` dentro de `on_enter`, NÃO como atribuição anotada top-level
- **AND** consequentemente não aparece em `Script.exports`

### Requirement: Pong ships gameplay scripts in Python

O módulo `:games:pong` SHALL servir um bundle de cena sob `src/main/resources/pong/` contendo `scene.json` na raiz e `scripts/` com um `.py` por tipo de gameplay com comportamento próprio. No mínimo o diretório `scripts/` SHALL conter: `paddle.py`, `ball.py`, `goal.py`, `score.py`, `center_line.py`, e `pong_scene.py`. Subclasses tag-only NÃO SHALL existir — entradas em `scene.json` que precisam apenas de `BoxCollider` referenciam o tipo por FQN no campo `_type`. Cada `.py` SHALL declarar `extends <NodeType>` na primeira linha não-vazia (docstring ou comentário). O comportamento de gameplay (movimento, colisão, IA, scoring) SHALL ser idêntico ao build anterior em `.nengine.kts`.

#### Scenario: Bundle directory exists with expected Python scripts

- **WHEN** o conteúdo de `:games:pong/src/main/resources/pong/` é listado
- **THEN** há um arquivo `scene.json` na raiz do bundle
- **AND** há um diretório `scripts/` contendo `paddle.py`, `ball.py`, `goal.py`, `score.py`, `center_line.py`, `pong_scene.py`
- **AND** NÃO há arquivos `.nengine.kts` no bundle

#### Scenario: Bundle directory excludes tag-only scripts

- **WHEN** o conteúdo de `:games:pong/src/main/resources/pong/scripts/` é listado
- **THEN** nenhum arquivo `paddle_collider.py` ou `walls.py` (ou variantes em qualquer extensão) está presente

#### Scenario: Every script declares extends on first non-empty line

- **WHEN** cada arquivo em `pong/scripts/` é inspecionado
- **THEN** a primeira linha não-vazia é uma docstring `"""extends <NodeType>"""` ou comentário `# extends <NodeType>`
- **AND** `<NodeType>` está registrado no `NodeRegistry`

#### Scenario: Loaded Pong matches the previous behavior

- **WHEN** a janela do Pong é lançada após a migração para Python
- **THEN** o layout inicial (paddles, ball, walls, goals, HUD) corresponde ao layout produzido pela versão anterior
- **AND** a resposta a input é idêntica
- **AND** o comportamento da IA é idêntico
- **AND** scoring e reset da bola são idênticos

### Requirement: pong/scene.json uses script slot and props

O arquivo `pong/scene.json` (raiz do bundle Pong) SHALL adotar o novo schema com campos `_type` (Node nativo), `script` (path do `.py` quando o nó carrega comportamento próprio) e `props` (overrides de exports). Nenhum campo `_type` ou `type` em `scene.json` SHALL ser um path terminando em `.nengine.kts` ou `.py`. Nodes cujo comportamento é puramente do tipo da engine (ex.: walls como `BoxCollider`) SHALL referenciar esse tipo por FQN em `_type` (`"_type": "com.neoutils.engine.physics.BoxCollider"`) sem campo `script`. Caminhos de scripts MUST ser relativos ao bundle (sem prefixo `pong/`).

#### Scenario: Pong-owned behavior uses script slot

- **WHEN** `pong/scene.json` é parseado e todos os campos `_type` e `script` coletados
- **THEN** todo nó cuja lógica era previamente em Kotlin tem `_type` apontando para o tipo Node nativo da engine (ex.: `com.neoutils.engine.scene.Node2D`)
- **AND** tem `script` apontando para um arquivo em `scripts/*.py` (sem prefixo `pong/`)
- **AND** nenhum valor de `_type` ou `type` termina em `.nengine.kts` nem em `.py`

#### Scenario: Wall nodes use engine BoxCollider by FQN without script

- **WHEN** `pong/scene.json` é parseado
- **THEN** as entradas nomeadas `topWall` e `bottomWall` têm `_type` igual a `com.neoutils.engine.physics.BoxCollider`
- **AND** o campo `script` é ausente ou nulo nessas entradas

#### Scenario: pong/scene.json round-trips

- **WHEN** código chama `BundleLoader.fromResources("pong")` e então `SceneLoader.save(scene)`
- **THEN** o JSON resultante é equivalente ao original (após canonicalização)
- **AND** os campos `script` e `props` são preservados

### Requirement: Pong scripts use Godot-style lifecycle names

The Python scripts in `:games:pong/src/main/resources/pong/scripts/` SHALL use the Godot-style hook names exclusively: `_ready`, `_process(dt)`, `_physics_process(dt)`, `_draw(renderer)`, `_exit_tree`, `_on_collide(other)`. The legacy names `on_enter`, `on_update`, `on_render`, `on_exit`, `on_collide` MUST NOT appear in any script under that directory.

Pong scripts SHALL additionally use the ergonomic accessors introduced in `ergonomic-core`: writes to local transform components go through `self.position = Vec2(...)`, `self.rotation = ...`, and `self.scale = Vec2(...)`; world-space reads go through `self.world()` (or `self.world().position`). The legacy spellings `self.transform = Transform(Vec2(...), self.transform.scale, self.transform.rotation)` and `self.worldPosition()` MUST NOT appear in any Pong script.

#### Scenario: No legacy hook names in Pong scripts

- **WHEN** any file under `games/pong/src/main/resources/pong/scripts/` is inspected
- **THEN** the file does not contain `def on_enter`, `def on_update`, `def on_render`, `def on_exit`, or `def on_collide`

#### Scenario: Ball runs in physics step

- **WHEN** `ball.py` is inspected
- **THEN** position integration (current `pos += v * dt` logic) lives in `_physics_process(self, dt)`
- **AND** no position update occurs in `_process` or `_draw`

#### Scenario: Paddle moves in physics step

- **WHEN** `paddle.py` is inspected
- **THEN** the AI/human direction integration lives in `_physics_process(self, dt)`
- **AND** the human-input read (current key) MAY happen in `_process` (variable dt) or be polled at physics step start — implementation choice — but resulting motion MUST be in `_physics_process`

#### Scenario: Paddle uses ergonomic position accessor

- **WHEN** `paddle.py` is inspected
- **THEN** position writes go through `self.position = Vec2(...)` (not `self.transform = Transform(...)`)
- **AND** any world-space read uses `self.world().position` (not `self.worldPosition()`)

#### Scenario: No worldPosition call sites remain in Pong scripts

- **WHEN** any file under `games/pong/src/main/resources/pong/scripts/` is grepped for `worldPosition`
- **THEN** no matches are found

### Requirement: Pong communicates score via Signal, not ad-hoc callback

`ball.py` SHALL declare a top-level `scored: Signal = signal(str)`. When the ball touches `leftGoal` or `rightGoal`, it SHALL `self.scored.emit("Right")` or `self.scored.emit("Left")` respectively (side scored, not side hit). The orchestrator script (`pong_scene.py`) SHALL `ball.scored.connect(self._on_scored)` during its `_ready`, and the handler `_on_scored(self, side)` SHALL increment the corresponding score label. The ad-hoc attribute `ball._on_score = callback` pattern MUST be removed.

#### Scenario: Ball declares the scored signal

- **WHEN** `ball.py` is inspected
- **THEN** it contains a top-level `scored: Signal = signal(str)` declaration

#### Scenario: Ball emits the scored signal on goal hit

- **WHEN** `_on_collide(self, other)` runs and `other.name == "leftGoal"`
- **THEN** `self.scored.emit("Right")` is called exactly once for that collision

#### Scenario: Orchestrator connects via signal

- **WHEN** `pong_scene.py` is inspected
- **THEN** its `_ready` looks up the `ball` node via `NodeRef` and calls `ball.scored.connect(self._on_scored)`
- **AND** no assignment of the form `ball._on_score = ...` exists anywhere in `games/pong/`

#### Scenario: No ad-hoc callback attribute

- **WHEN** any file under `games/pong/src/main/resources/pong/scripts/` is inspected
- **THEN** no occurrence of `_on_score` as an instance attribute exists
- **AND** no `hasattr(self, '_on_score')` check exists

### Requirement: Pong reads viewport from the scene, not a prop

The `playFieldHeight` prop on `paddle.py` SHALL remain removed. The paddle clamping logic SHALL continue to read `self.rootScene().viewport.size.y` (or equivalent) at clamp time. The Pong `scene.json` SHALL include a `Camera2D` node with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `current = true`, declaring the play field as world bounds. The `Camera2D` node SHALL additionally declare `aspectMode = AspectMode.FIT` so the 800×600 world maps onto any surface size with letterbox-style preserved aspect ratio. Because `Scene.render` now applies the camera as a view transform, all positions in `pong/scene.json` (paddles, walls, goals, ball, scores, center line) SHALL be authored in the fixed 800×600 world coordinate system and SHALL NOT be repositioned by scripts at runtime.

#### Scenario: paddle.py has no playFieldHeight export

- **WHEN** `paddle.py` is inspected
- **THEN** no top-level `playFieldHeight: float = ...` declaration exists
- **AND** the clamp logic reads `self.rootScene().viewport.size.y` (or an equivalent accessor on `Scene` returning the viewport height)

#### Scenario: Pong scene declares a Camera2D with FIT aspect

- **WHEN** `pong.scene.json` is inspected
- **THEN** the root contains a child of type `com.neoutils.engine.scene.Camera2D` with `current: true`, `bounds: Rect(Vec2.ZERO, Vec2(800f, 600f))`, and `aspectMode: "FIT"`

#### Scenario: Paddle reaches the full play field height on any surface size

- **GIVEN** a Pong instance running with `Camera2D.bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` on a surface of any size (e.g. `1280×900`)
- **WHEN** the left paddle is moved continuously downward until its clamp triggers
- **THEN** the paddle's bottom edge reaches `viewport.size.y == 600` in world coordinates
- **AND** when rendered, the paddle visually touches the bottom edge of the play field (the camera projection respects the surface, the letterbox bars do not eat into the play field)

### Requirement: Pong visuals use new primitive nodes and direct _draw

The Pong scripts SHALL no longer reference `Shape`. Paddles and the ball SHALL render via `_draw(self, renderer)` inside their own script (drawing rect/circle directly). The `centerLine` node SHALL be a declarative `Line2D` child in `scene.json` (no script). Score labels SHALL use `Label`, not `Text`. The previous `center_line.py` script SHALL be removed.

#### Scenario: No Shape references in Pong

- **WHEN** any file under `games/pong/src/main/resources/pong/` is inspected
- **THEN** no occurrence of the identifier `Shape` exists (neither in JSON `type` fields nor as Python references)

#### Scenario: Paddle and ball draw via _draw

- **WHEN** `paddle.py` and `ball.py` are inspected
- **THEN** each defines `_draw(self, renderer)` that issues the rect/circle draw
- **AND** neither script attaches a `ColorRect`/`Circle2D` child node for visual purposes

#### Scenario: centerLine is a Line2D in scene.json

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `centerLine` node has `type: "com.neoutils.engine.scene.Line2D"`
- **AND** it has no `script` field

#### Scenario: center_line.py is removed

- **WHEN** `games/pong/src/main/resources/pong/scripts/` is listed
- **THEN** there is no file named `center_line.py`

#### Scenario: Score nodes use Label

- **WHEN** `score.py` and `pong.scene.json` are inspected
- **THEN** the type of the score nodes (or their visual children) references `com.neoutils.engine.scene.Label`
- **AND** no occurrence of the identifier `Text` exists

### Requirement: Pong wraps the loaded root in a SceneTree

`Main.kt` em `:games:pong` MUST construir uma `SceneTree(root = bundleRoot)` ao redor do `Node` devolvido por `BundleLoader.fromResources("pong", scripting = python)` antes de chamar `SkikoHost.run(tree, config)`. O `Main.kt` MUST NOT armazenar o resultado de `BundleLoader.fromResources` em uma variável tipada como `Scene` (a classe não existe mais) — o tipo declarado MUST ser `Node` ou inferido.

O arquivo `games/pong/src/main/resources/pong/scene.json` MUST declarar `root.type` como um identificador registrado em `NodeRegistry` que NÃO seja `com.neoutils.engine.scene.Scene`. O valor padrão MUST ser `com.neoutils.engine.scene.Node` (root como container puro; o `Camera2D` filho carrega bounds e view transform). Tentativas de carregar o `scene.json` com `root.type = "com.neoutils.engine.scene.Scene"` MUST falhar com `UnknownNodeTypeException`.

#### Scenario: Pong Main wraps the bundle root in a SceneTree

- **WHEN** o source de `games/pong/src/main/kotlin/.../Main.kt` é inspecionado
- **THEN** existe uma chamada com a forma `SkikoHost().run(SceneTree(root = ...), GameConfig(...))` (ou `host.run(SceneTree(root = ...), ...)` equivalente)
- **AND** o tipo declarado da variável que recebe `BundleLoader.fromResources("pong", ...)` é `Node` ou inferido
- **AND** o source NÃO contém referência ao símbolo `Scene`

#### Scenario: Pong scene.json root type is not engine.Scene

- **WHEN** `pong/scene.json` é inspecionado após esta change
- **THEN** o campo `root.type` é `com.neoutils.engine.scene.Node` (ou outro tipo concreto registrado, mas NÃO `com.neoutils.engine.scene.Scene`)
- **AND** o `Camera2D` continua presente como filho do root com `current = true`
- **AND** `version` permanece `2` (sem bump de schema)

#### Scenario: Pong still runs end-to-end on Skiko after the change

- **WHEN** desenvolvedor executa `./gradlew :games:pong:run` após esta change
- **THEN** uma janela Skiko abre e renderiza a cena Pong
- **AND** entradas `W`/`S` movem o paddle esquerdo
- **AND** o paddle direito (AI) acompanha a bola
- **AND** F1/F2 alternam overlays de FPS e colliders, idêntico ao comportamento pré-change

### Requirement: Pong uses Godot-style collision nodes

The Pong scene SHALL be authored with the new collision taxonomy. Specifically:

- The ball SHALL be a `CharacterBody2D` with a child `CollisionShape2D` whose `shape` is a `CircleShape2D`.
- `topWall` and `bottomWall` SHALL be `StaticBody2D` instances, each with a child `CollisionShape2D` (`RectangleShape2D`) sized to span the play field width and positioned at the actual wall location (not the (0,0) placeholder of the previous era).
- `leftGoal` and `rightGoal` SHALL be `Area2D` instances, each with a child `CollisionShape2D` (`RectangleShape2D`) positioned at the play field's left/right boundary.
- Paddles (`left`, `right`) SHALL be `StaticBody2D` instances with a child `CollisionShape2D` (`RectangleShape2D`), declared in `scene.json` — the paddle script SHALL NOT create the collider in `_ready`.
- The `pong.scene.json` SHALL NOT contain any reference to the identifiers `BoxCollider` or `Collider`.

#### Scenario: pong.scene.json has no BoxCollider references

- **WHEN** `games/pong/src/main/resources/pong/pong.scene.json` is inspected
- **THEN** the file does not contain the string `BoxCollider`
- **AND** the file does not contain the string `Collider"` (the closing-quote variant catches any leftover)

#### Scenario: Ball is a CharacterBody2D with CircleShape2D

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `Ball` node has `type: "com.neoutils.engine.physics.CharacterBody2D"`
- **AND** the `Ball` node has at least one child of type `com.neoutils.engine.physics.CollisionShape2D` whose `shape` is `CircleShape2D`

#### Scenario: Goals are Area2D

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `leftGoal` and `rightGoal` nodes have `type: "com.neoutils.engine.physics.Area2D"`
- **AND** each has a `CollisionShape2D` child with a `RectangleShape2D`

#### Scenario: Paddles are StaticBody2D with declared shape

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `left` and `right` nodes have `type: "com.neoutils.engine.physics.StaticBody2D"`
- **AND** each has a `CollisionShape2D` child with a `RectangleShape2D(size = Vec2(16f, 96f))` declared inline (not created in `_ready`)

### Requirement: Pong scripts use enter/exit collision hooks

The Pong scripts SHALL replace the legacy `_on_collide(self, other)` hook with the four Godot-style enter/exit hooks: `_on_area_entered(self, area)`, `_on_area_exited(self, area)`, `_on_body_entered(self, body)`, `_on_body_exited(self, body)`. The string `_on_collide` SHALL NOT appear in any Pong script. Discrimination between walls and paddles in `ball.py` SHALL use `body.is_in_group("walls")` and `body.is_in_group("paddles")` rather than `body.name in ("left", "right")` or `other.parent.name`.

#### Scenario: ball.py uses area/body hooks

- **WHEN** `games/pong/src/main/resources/pong/scripts/ball.py` is inspected
- **THEN** it defines `_on_area_entered(self, area)` to handle goals
- **AND** it defines `_on_body_entered(self, body)` to handle wall and paddle bounces
- **AND** it does NOT define `_on_collide`

#### Scenario: Ball discriminates by group, not by name

- **WHEN** `ball.py` is inspected
- **THEN** the body-entered handler reads `body.is_in_group("walls")` or `body.is_in_group("paddles")`
- **AND** the handler does not branch on `body.name in (...)` or `body.parent.name == ...`

#### Scenario: Ball is a CharacterBody2D and uses self.velocity

- **WHEN** `ball.py` is inspected
- **THEN** the script declares `# extends CharacterBody2D` on its first non-empty line
- **AND** velocity is read/written as `self.velocity` (the inherited slot), not as a private attribute like `self._velocity`

#### Scenario: Paddle is a StaticBody2D without ad-hoc collider creation

- **WHEN** `paddle.py` is inspected
- **THEN** the script declares `# extends StaticBody2D` on its first non-empty line
- **AND** the script does not call `addChild(BoxCollider())` or any equivalent — the collision shape is declared in `pong.scene.json`

#### Scenario: Goal scripts extend Area2D

- **WHEN** `goal.py` is inspected
- **THEN** the script declares `# extends Area2D`

### Requirement: Pong walls and paddles join groups for behavior discrimination

The `pong.scene.json` SHALL declare `groups` on walls and paddles so that the ball script can branch on intent rather than node names:

- `topWall`, `bottomWall` SHALL be members of the group `"walls"`.
- `left`, `right` (paddles) SHALL be members of the group `"paddles"`.

The groups MAY be declared via the `properties.groups` field of the node JSON (preferred) or via `_ready` calls to `add_to_group` in their scripts (acceptable fallback).

#### Scenario: Walls are in the walls group

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `topWall` and `bottomWall` nodes contain `"walls"` in their `properties.groups` array (or their script's `_ready` calls `self.add_to_group("walls")`)

#### Scenario: Paddles are in the paddles group

- **WHEN** `pong.scene.json` is inspected
- **THEN** the `left` and `right` nodes contain `"paddles"` in their `properties.groups` array (or equivalent)
