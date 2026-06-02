# nengine

2D game engine com scene graph estilo Godot, escrita em Kotlin, com backends de renderização e linguagens de scripting agnósticas.

## Proposta

A `nengine` existe para aprender arquitetura de engine — clareza didática acima de performance prematura, evolução incremental guiada por jogos de exemplo que viram prova viva de cada capacidade. Cada decisão fundamental nasce como uma change OpenSpec (proposal + design + specs) antes do código.

A meta de longo prazo é cobrir o ciclo completo: do scene graph mínimo até um editor visual, passando por backends e linguagens de scripting trocáveis sem tocar no núcleo (`:engine` permanece Kotlin puro, sem dependência de UI/render).

## Capacidades

### Backends de renderização

| Backend | Status                                            | Módulo            |
| ------- | ------------------------------------------------- | ----------------- |
| Skiko   | default — todos os jogos shipped                  | `:engine-skiko`   |
| LWJGL   | segundo backend ativo (NanoVG + GLFW + OpenGL)    | `:engine-lwjgl`   |

### Scripting

| Linguagem | Status    | Módulo                  | Notas                                   |
| --------- | --------- | ----------------------- | --------------------------------------- |
| Kotlin    | native    | `:engine`               | biblioteca entra como dependência       |
| Python    | default   | `:engine-bundle-python` | via GraalPy 24.x; stubs `.pyi` inclusos |
| Lua       | suportado | `:engine-bundle-lua`    | via LuaJ 3.0.x; stubs LuaCATS inclusos  |

### Jogos shipped

| Jogo          | Backend          | Scripting | Função na engine                                                          |
| ------------- | ---------------- | --------- | ------------------------------------------------------------------------- |
| Pong          | Skiko            | Python    | prova da fundação (loop, física, scripts, signals, `Camera2D`)            |
| Jogo da Velha | Skiko            | Lua       | sentinela do segundo backend de scripting                                 |
| Demos         | Skiko (+LWJGL)   | Kotlin    | 6 cenas exercitando invariantes; sentinela do segundo backend de render   |
| Snake         | Skiko            | Python    | gameplay discreto/tick-based; mutação dinâmica de scene graph             |
| Hello World   | Skiko            | —         | exemplo code-only mínimo (um `Label` centralizado)                        |

## Exemplos

```sh
./gradlew :games:hello-world:run # exemplo code-only mínimo
./gradlew :games:pong:run        # backend padrão: Skiko + Python
./gradlew :games:tictactoe:run   # backend padrão: Skiko + Lua
./gradlew :games:demos:run       # backend padrão: Skiko
./gradlew :games:demos:runLwjgl  # segundo backend: LWJGL
./gradlew :games:snake:run
```

> **macOS** — o backend LWJGL precisa rodar no main thread do processo (`-XstartOnFirstThread`), pois GLFW liga em Cocoa via `NSApp`. A task `runLwjgl` injeta essa flag automaticamente; quem invocar `MainLwjglKt` manualmente via `java -cp ...` precisa adicionar a flag à linha de comando. Linux e Windows não exigem.

## Demos

A executável `:games:demos` expõe 6 cenas trocáveis pelas teclas `1`–`6`, cada uma exercitando um aspecto da engine. Detalhe completo em [`openspec/specs/demos-sample/`](./openspec/specs/demos-sample/spec.md).

1. **Solar system** — composição aninhada de transform em até 4 níveis (Sol → órbita → planeta → órbita-lua → lua).
2. **Scale hierarchy** — composição de scale via `Shape.onRender` ao longo da cadeia de ancestrais.
3. **Spawner** — mutação durante traversal (clique adiciona bolinha; `Area2D` trap remove no `onAreaEntered`).
4. **Collision stress** — 30 `RigidBody2D` numa arena; impulse solver bilateral; conservação de KE com `restitution=1`.
5. **Rotating box** — sweep `moveAndCollide` em frame rotativo (12 `CharacterBody2D` numa caixa que gira).
6. **Tumbling swarm** — 16 quadrados `RigidBody2D` com spin; OBB rotated sweep; fricção Coulomb tangencial.

## Controles globais

- `F1` — abre/fecha a HUD de debug com checkboxes para cada widget registrado (Colliders, Log, Debug Draw, Velocity, Contacts, Time, Profiler, Picker, e quaisquer widgets custom do jogo). O keybind é configurável via `GameConfig(debugHudKey = ...)`.
- `C` — com o **Colliders** ligado, cicla o modo de desenho `AABB → REAL → BOTH` (default `REAL`: geometria real da forma; `AABB`: envelope do broad-phase; `BOTH`: os dois). O **Profiler** mostra `fps` no topo do painel, amostrado de forma barata (independente da instrumentação de fases), além das medições por fase.

A HUD lista uma linha por `DebugWidget` ativo no `tree.debug` registry; clicar uma linha alterna o `enabled` do widget. Cada painel screen-space (`ScreenDebugWidget`) tem um header com controles de janela: o **grip** (grade de pontos) à esquerda arrasta o painel, e à direita ficam **colapsar** (`[_]`, esconde o corpo mantendo só o header) e **fechar** (`[x]`, soft close via `enabled = false` — reabre pela HUD). `BACKSPACE` restaura o layout default: devolve cada painel ao seu slot e expande os colapsados. Para plugar um gizmo novo num projeto-jogo basta criar uma classe estendendo `ScreenDebugWidget` (overlay 2D em pixels) ou `WorldDebugWidget` (gizmo em coordenadas de mundo, recebe a view transform da `Camera2D` automaticamente) e registrá-la após `tree.start()`:

```kotlin
class MyAxes : WorldDebugWidget() {
    override val title = "My axes"
    override fun drawDebug(renderer: Renderer) { /* ... */ }
}

fun main() {
    val tree = SceneTree(root = MyRoot())
    tree.start()
    tree.debug.register(MyAxes())
    SkikoHost().run(tree, GameConfig())
}
```

Controles específicos de cada jogo (teclas de movimento, mouse) vivem na spec do respectivo `<jogo>-sample` em [`openspec/specs/`](./openspec/specs/).

## Configurando o IDE

Para autocompletar e type-check em scripts:

- **Python (Pyright/Pylance)** — adicione `engine-bundle-python/src/main/resources/stubs` ao `extraPaths`:

  ```json
  { "extraPaths": ["engine-bundle-python/src/main/resources/stubs"] }
  ```

- **Lua (sumneko-lua)** — adicione `engine-bundle-lua/src/main/resources/stubs` à `workspace.library`:

  ```json
  { "workspace": { "library": ["engine-bundle-lua/src/main/resources/stubs"] } }
  ```

## Saber mais

- [`CLAUDE.md`](./CLAUDE.md) — invariantes arquiteturais, convenções de código, modelo de scripting, workflow OpenSpec
- [`ROADMAP.md`](./ROADMAP.md) — changes ativas e planejadas
- [`openspec/specs/`](./openspec/specs/) — especificações por capability (jogos, físicas, renderers, scripting)
- [`openspec/changes/archive/`](./openspec/changes/archive/) — histórico de changes arquivadas
