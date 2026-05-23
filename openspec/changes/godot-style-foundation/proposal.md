## Why

A engine cresceu *ad hoc* — hooks chamam `onUpdate`/`onRender`, `Shape` é um catch-all primitivo que não respeita rotação, comunicação entre nós passa por callbacks Python ad-hoc (`ball._on_score = ...`), o "tamanho do mundo" vive como prop de game (`playFieldHeight: 600.0`), e o `dt` do `onUpdate` é variável (FPS-dependente) para tudo, inclusive física. Cada uma dessas escolhas é defensável isoladamente, mas juntas drenam a clareza didática do projeto e impedem padrões "Godot-like" que o usuário declarou como direção.

Esta change consolida as **fundações Godot-style** sem ainda mexer no modelo de colisão (deixa para a change `collision-overhaul`): renomeia hooks, separa frame-step de physics-step, promove `Signal<T>` a primeiro-classe com bridge Python, adiciona `groups`, introduz `Camera2D` com bounds, renomeia `Text → Label`, apaga `Shape` e introduz primitivas de desenho dedicadas (`ColorRect`, `Polygon2D`, `Line2D`, `Circle2D`), e migra Pong/Demos/TicTacToe para os nomes novos.

## What Changes

### Hooks (renomeação simétrica Kotlin/Python)

- **BREAKING** `Node.onUpdate(dt)` → `Node.onProcess(dt)`. Mesmo contrato (dt em segundos, variável).
- **BREAKING** `Node.onRender(renderer)` → `Node.onDraw(renderer)`. Mesmo contrato.
- **BREAKING** `Node.onEnter()` e `Node.onExit()` **permanecem com esses nomes em Kotlin**. Em Python passam a se chamar `_ready(self)` e `_exit_tree(self)` (alinhamento com Godot).
- **NEW** `Node.onPhysicsProcess(dt)` — hook fixed-step (dt constante, ver abaixo).
- **BREAKING** Python: `on_update → _process`, `on_render → _draw`, `on_enter → _ready`, `on_exit → _exit_tree`, `on_collide → _on_collide`. Hooks ausentes continuam no-op.
- `Collider.onCollide(other)` (Kotlin) **mantém o nome em change 1**; sua dissolução em `_on_area_entered` / `_on_body_entered` ocorre na change `collision-overhaul`.

### Game loop fixed-step

- **BREAKING** `GameLoop.tick(dtNanos)` adquire um acumulador interno. Cada `tick`:
  1. acumula `dt`;
  2. enquanto `acc ≥ PHYSICS_DT` (default `1f/60f` s): drena pending → `scene.physicsProcess(PHYSICS_DT)` → `physics.step(scene)` → `acc -= PHYSICS_DT`;
  3. drena pending → `scene.process(dtFrame)`;
  4. drena pending → `scene.render(renderer)`.
- O `PHYSICS_DT` é configurável em `GameConfig.physicsHz: Int = 60` (default 60). Spiral-of-death é mitigado com clamp de iterações (`maxPhysicsStepsPerFrame = 5`).
- Backends (`SkikoHost`, `ComposeHost`) **não mudam** — continuam chamando `loop.tick(dtNanos)`. O accumulator é interno ao `GameLoop`.

### Signals como primeira-classe + bridge Python

- **NEW** `Signal<T>` em `:engine` ganha API uniforme: `connect(handler: (T) -> Unit): Disposable`, `disconnect(disposable)`, `emit(value)`. (`Signal` existe hoje em `serialization` mas é um wrapper de `var path: NodeRef` — vira um event hub real, mantendo NodeRef-style para wiring em editor futuro.)
- **NEW** Python ganha `signal(<type>)` factory: ex. `scored: Signal = signal(str)`. Em Python o handler é uma função; `bola.scored.connect(self._on_scored)` e `bola.scored.emit("Left")` funcionam dos dois lados (Kotlin↔Python).
- `ScriptInstance` SPI ganha hook `getSignal(name): Signal<*>?` para o Kotlin descobrir signals declarados no script. O `PythonScriptHost` implementa varrendo top-level `AnnAssign` cuja anotação é `Signal`.

### Groups

- **NEW** `Node.groups: Set<String>` (mutável via `addToGroup(name)` / `removeFromGroup(name)`); `Scene.getNodesInGroup(name): List<Node>` faz tree-walk on-demand (O(N), aceitável para escala didática).
- Python: `self.add_to_group("paddles")`, `self.is_in_group("paddles")`, `self.rootScene().get_nodes_in_group("paddles")`.

### Camera2D

- **NEW** `Camera2D : Node2D` com `bounds: Rect` (área visível em coordenadas de mundo) e `current: Boolean` (default `false`). Ao tornar-se `current`, registra-se na `Scene` como câmera ativa.
- **NEW** `Scene.currentCamera: Camera2D?` (computado on-demand: primeiro Camera2D com `current = true` no tree-walk; `null` se nenhum). `Scene.viewport: Rect` derivada — se há current camera, retorna seus bounds; senão retorna `Rect(Vec2.ZERO, scene.size)`.
- `Scene.size: Vec2` vira propriedade canônica preenchida pelo host em `resize()` (já é hoje, só ganha nome explícito).

### Label (rename Text)

- **BREAKING** `com.neoutils.engine.scene.Text` → `com.neoutils.engine.scene.Label`. Mesmo campo `text`, `size`, `color`. Substitui em todos os scenes/scripts.

### Visual primitives (Shape sai)

- **REMOVED** `com.neoutils.engine.scene.Shape` (Kind.Rect | Kind.Circle).
- **NEW** `ColorRect : Node2D` — retângulo preenchido em world-space (`size: Vec2`, `color: Color`).
- **NEW** `Circle2D : Node2D` — círculo preenchido (`radius: Float`, `color: Color`).
- **NEW** `Line2D : Node2D` — segmentos conectados (`points: List<Vec2>`, `thickness: Float`, `color: Color`).
- **NEW** `Polygon2D : Node2D` — polígono preenchido por vértices (`points: List<Vec2>`, `color: Color`). Renderizado via decomposição em triângulos no backend (ou `Renderer.drawPolygon` adicionado a SPI).
- **NEW** `Renderer.drawPolygon(points: List<Vec2>, color: Color)` adicionado à SPI.

### Migrações

- `:games:pong/scripts/*.py` migra para `_ready/_process/_draw/_on_collide`, usa `Signal` para score (substitui `_on_score` ad-hoc), substitui `playFieldHeight` por `scene.viewport.size.y`, substitui chamadas `renderer.drawRect`/`drawCircle` dentro de paddle/ball por **`_draw` direto** (Godot-style: o nó **é** seu próprio desenho via `_draw`). Adicionado `Camera2D` ao `pong.scene.json` definindo `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))`.
- `:games:demos` migra os override de `onUpdate/onRender` para `onProcess/onDraw`. `centerLine` em Demos / Pong que usava `Shape` passa a usar `Line2D` ou desenho direto.
- `:games:tictactoe` (Kotlin) migra overrides de `onUpdate/onRender` para `onProcess/onDraw`. Substitui `Text` por `Label`. Permanece Kotlin-puro (bundle-tictactoe é outra change).

### Roadmap

- Adiciona `godot-style-foundation` ao roadmap de `CLAUDE.md` com status `Active`.
- Adiciona **`game-snake`** (Planned) como o jogo-validador desta change: exercita `_process` em tick discreto via `Timer` (introdução opcional aqui ou em change futura), `Signal` para evento "ate fruit", `Camera2D.bounds` como play field, `Polygon2D`/`ColorRect`/`Label` como primitivos. **Não usa colisão** — perfeito para validar fundação sem depender de `collision-overhaul`.

## Capabilities

### New Capabilities

- (nenhuma como capability nova; `Camera2D`, primitivas, `Signal` 1ª classe entram em `engine-core` modificado)

### Modified Capabilities

- `engine-core`: hooks renomeados; `onPhysicsProcess` adicionado; `GameLoop` com accumulator; `Signal<T>` redefinido como event hub; `Camera2D` adicionado; `Scene.size`/`Scene.viewport` formalizados; `Node.groups` + `Scene.getNodesInGroup` adicionados; `Shape` removido; `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D` adicionados; `Renderer.drawPolygon` adicionado.
- `python-scripting`: nomes de hook Python migram (`on_* → _ready/_process/_draw/_exit_tree/_on_collide`); descoberta de `Signal` via AST top-level adicionada; bindings implícitos no Context ganham `Signal`, `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`; stubs `.pyi` atualizados.
- `scene-serialization`: `Signal<T>` deixa de ser wrapper de `var path: NodeRef` e passa a ser tipo runtime; `@Inspect` continua aplicável a configurações estáticas.
- `pong-sample`: scripts migram para novos hooks; goals comunicam-se via `Signal`; `Camera2D` em `scene.json`; sem `Shape`.
- `tictactoe-sample`: `Board`/`StatusText` migram overrides; `Text` → `Label`.

## Impact

- **Código tocado:**
  - `:engine` — `Node.kt`, `Node2D.kt`, `Scene.kt`, `Shape.kt` (REMOVE), `Text.kt` (REMOVE), novo `Label.kt`, `ColorRect.kt`, `Circle2D.kt`, `Line2D.kt`, `Polygon2D.kt`, `Camera2D.kt`. `Renderer.kt` (adiciona `drawPolygon`). `GameLoop.kt` (accumulator). `Signal.kt` (reescrita). Novos arquivos `Groups.kt` (extensões de `Scene`). `Collider.kt` mantém `onCollide`.
  - `:engine-bundle` — `NodeRegistry` ganha registros automáticos dos novos tipos; remove `Shape`/`Text`.
  - `:engine-bundle-python` — `PythonScriptHost` adapta nomes de hook; bindings do `Context` ganham `Signal` etc.; AST inspector reconhece `signal(...)` factory; stubs `.pyi` atualizados.
  - `:engine-skiko` — `SkikoRenderer.drawPolygon` implementado via `Path` Skia.
  - `:engine-compose` — `ComposeRenderer.drawPolygon` implementado via `Path` Compose.
  - `:games:pong` — `pong.scene.json` reescrito (Camera2D, Label, sem BoxCollider para visual — collider permanece como hoje), scripts `.py` migram nomes de hook + sinais.
  - `:games:tictactoe` — `Board.kt`, `StatusText.kt`, `TicTacToeScene.kt`, `Mark.kt` (uso de Color), `Main.kt` — overrides renomeados, `Text → Label`.
  - `:games:demos` — overrides renomeados; `Shape` substituído por primitivas novas.
- **Documentação:** `CLAUDE.md` (seção Coding Conventions com hooks Godot-style, tabela roadmap com `godot-style-foundation` Active + `game-snake` Planned, seção Scripting com `_ready`/`_process`/`_draw`/`_exit_tree`/`_on_collide`).
- **Sem impacto em colisão:** `Collider` / `BoxCollider` / `PhysicsSystem` permanecem como hoje; apenas o **quando** rodam muda (entra no acumulador fixed-step). Reescrita estrutural fica para change 2.
- **Compose-runtime e Skiko-runtime:** tocados apenas pelo `drawPolygon` adicionado.
