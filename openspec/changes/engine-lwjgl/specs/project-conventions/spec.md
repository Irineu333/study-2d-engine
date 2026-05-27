## MODIFIED Requirements

### Requirement: CLAUDE.md enumerates invariant architectural decisions

The `CLAUDE.md` SHALL list the architectural invariants that any change must respect, including at minimum: (1) scene graph style Godot (inheritance, no Unity-style components), (2) `:engine` has no dependency on any UI or render framework (no `androidx.compose.*`, `org.jetbrains.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, `org.lwjgl.*`, no Swing/AWT widget types, no Vulkan/WebGPU bindings), (3) collision uses `Collider`-as-node with a central `PhysicsSystem`, (4) `Renderer`, `Input` and `GameHost` are SPIs; Skiko is the default render backend (`:engine-skiko`, used by all shipped games — Pong, Demos, Hello-World, Tic Tac Toe); LWJGL is the second active backend (`:engine-lwjgl`, via NanoVG/GLFW/OpenGL) serving as the sentinel of invariant #4 through the `runLwjgl` entrypoint of `:games:demos`; no module other than the respective backend module is allowed to leak backend-specific types into `:engine`, (5) **the live tree is owned by a `SceneTree` that is not a `Node` and not `@Serializable`; a `Scene` class no longer exists in `:engine`; nodes reach the tree via the cached `Node.tree` property (set on attach, cleared on detach); `SceneTree` is not subclassable for setup — a root `Node` with `onEnter()` populates the tree; `SceneLoader.load` and `BundleLoader` return `Node` (root-type free); the host wraps the root in `SceneTree(root = ...)` before `run(...)`**.

#### Scenario: Invariants section enumerates the core decisions

- **WHEN** `CLAUDE.md` is opened
- **THEN** the invariants section lists at least the five decisions above with one-line rationale each
- **AND** invariant (2) is worded generically over UI/render frameworks (not Compose-specific, not Skiko-specific, not LWJGL-specific) and includes `org.lwjgl.*` in the prohibited list
- **AND** invariant (4) explicitly names `GameHost` as an SPI alongside `Renderer` and `Input`
- **AND** invariant (4) identifies Skiko as the default render backend used by all shipped games
- **AND** invariant (4) identifies LWJGL as the second active backend serving as the invariant #4 sentinel via `:games:demos`'s `runLwjgl` entrypoint
- **AND** invariant (4) explicitly forbids `:engine` from referencing Skiko or LWJGL types
- **AND** invariant (5) is present and explicitly states that `SceneTree` is not a `Node` and that `Scene` has been removed
- **AND** invariant (5) names `Node.tree` as the cached access path from any live node
- **AND** invariant (5) prescribes the host-wraps-root pattern (`Host.run(SceneTree(root = ...), config)`)

#### Scenario: No mention of Compose as second backend remains

- **WHEN** `CLAUDE.md` is opened
- **THEN** no paragraph in the invariants section names Compose Multiplatform as a second render backend
- **AND** no paragraph references `:engine-compose` as a current module

#### Scenario: LWJGL is named as the active second backend, not "planned"

- **WHEN** `CLAUDE.md` is opened after this change
- **THEN** the invariant #4 paragraph describes LWJGL as the **active** second backend (not "planned" or "experimental future")
- **AND** the document references `:engine-lwjgl` as a module that exists in the project
- **AND** the document references `./gradlew :games:demos:runLwjgl` as the canonical way to exercise the LWJGL backend

### Requirement: CLAUDE.md describes module structure and how to run

O `CLAUDE.md` SHALL descrever o layout de módulos do projeto (`:engine`, `:engine-bundle`, `:engine-bundle-python`, `:engine-bundle-lua`, `:engine-skiko`, `:engine-lwjgl`, `:games:<name>`) e o comando para rodar um módulo de jogo (`./gradlew :games:<name>:run`). O documento MUST esclarecer que todos os jogos shipped rodam em Skiko por padrão (Pong, Demos, Hello-World, Tic Tac Toe). O documento MUST documentar que `:games:demos` expõe um segundo entrypoint LWJGL invocado via `./gradlew :games:demos:runLwjgl`, e MUST nomeá-lo como sentinela do invariante #4 (segundo backend de render exercitando o `Renderer`/`Input`/`GameHost` SPIs). O documento MUST documentar o caveat macOS: o entrypoint LWJGL precisa de `-XstartOnFirstThread`, injetado automaticamente pela task `runLwjgl` do Gradle — usuários que invocam manualmente via `java -cp` precisam adicionar a flag. O documento MUST NOT listar `:engine-compose` na seção de módulos. A linha do `:engine-lwjgl` MUST descrevê-lo como "implementação de `Renderer`/`Input`/`GameHost` via LWJGL (NanoVG + GLFW + OpenGL 3.3 core); segundo backend ativo; sentinela do invariante #4 via `:games:demos`'s LWJGL entrypoint". A descrição de `:games:tictactoe` MUST continuar identificando-o como "jogo Velha (humano vs humano), roda em Skiko com scripting Lua — sentinela do segundo backend de scripting" (papel de scripting permanece).

#### Scenario: Module structure section is accurate

- **WHEN** um desenvolvedor compara a seção com `settings.gradle.kts`
- **THEN** os módulos listados batem com o grafo real do projeto
- **AND** `:engine-bundle` aparece ao lado de `:engine` e `:engine-skiko`
- **AND** `:engine-bundle-python` aparece listado separadamente
- **AND** `:engine-bundle-lua` aparece listado separadamente
- **AND** `:engine-lwjgl` aparece listado separadamente como segundo backend de render
- **AND** `:engine-compose` NÃO aparece em nenhum lugar do documento como módulo ativo
- **AND** `:engine-scripting` NÃO aparece nem na seção de módulos nem no roadmap como módulo ativo

#### Scenario: All shipped games default to Skiko

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a seção de módulos nomeia Skiko como backend default usado por `:games:pong`, `:games:demos`, `:games:hello-world` e `:games:tictactoe`
- **AND** NÃO menciona Compose como backend de nenhum jogo

#### Scenario: Demos documents the LWJGL alternate entrypoint

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a seção "Para rodar Demos" documenta tanto `./gradlew :games:demos:run` (Skiko, default) quanto `./gradlew :games:demos:runLwjgl` (LWJGL, alternativo)
- **AND** a documentação inclui o caveat macOS sobre `-XstartOnFirstThread`
- **AND** ambos os entrypoints rodam o mesmo conjunto de cenas `1`–`6` com as mesmas key-bindings

#### Scenario: Tictactoe role is described as Lua scripting sentinel

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a descrição de `:games:tictactoe` identifica seu papel como sentinela do segundo backend de scripting (Lua)
- **AND** NÃO o identifica como sentinela do segundo backend de render (esse papel agora é do `:games:demos`'s LWJGL entrypoint)

#### Scenario: engine-bundle responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-bundle` descreve sua responsabilidade como "carregar cena via bundle (scene.json + scripts/) e hospedar a SPI `ScriptHost` agnóstica de linguagem"
- **AND** não menciona Kotlin Scripting nem `.nengine.kts` como mecanismo vigente

#### Scenario: engine-bundle-python responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-bundle-python` descreve sua responsabilidade como "implementação concreta de `ScriptHost` para scripts Python `.py`, usando GraalPy"
- **AND** indica que jogos que usam Python scripting declaram dependência neste módulo

#### Scenario: engine-bundle-lua responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-bundle-lua` descreve sua responsabilidade como "implementação concreta de `ScriptHost` para scripts Lua `.lua`, usando LuaJ"
- **AND** indica que jogos que usam Lua scripting declaram dependência neste módulo

#### Scenario: engine-lwjgl responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-lwjgl` descreve sua responsabilidade como "implementação de `Renderer`/`Input`/`GameHost` SPIs via LWJGL (NanoVG + GLFW + OpenGL 3.3 core); segundo backend ativo de render; sentinela do invariante #4 via entrypoint LWJGL de `:games:demos`"
- **AND** indica que apenas `:engine-lwjgl` declara dependência em `org.lwjgl.*` artifacts

#### Scenario: Run instructions work as written

- **WHEN** um desenvolvedor executa o comando mostrado para Pong, Tic Tac Toe, Demos, Hello-World ou o entrypoint LWJGL de Demos
- **THEN** o jogo inicia sem passos adicionais (a flag `-XstartOnFirstThread` é injetada pela task `runLwjgl` em macOS)

### Requirement: ROADMAP.md tracks active and planned changes without duplicating archive

O `ROADMAP.md` SHALL conter duas seções (`Active`, `Planned`) listando changes OpenSpec em andamento e intenções firmadas, respectivamente, com resumo de uma linha cada. O documento MUST NÃO duplicar histórico de changes arquivadas (que vive em `openspec/changes/archive/`). Quando uma change planejada vira proposal, MUST ser promovida de `Planned` para `Active`. Quando uma change é arquivada, sua linha MUST ser removida de `Active`. A change `engine-lwjgl` MUST NÃO aparecer em `Planned` após esta change — ela é Active enquanto a implementação roda, e some quando arquivada.

#### Scenario: engine-lwjgl is not listed in Planned after this change

- **WHEN** `ROADMAP.md` é aberto após esta change
- **THEN** a seção `Planned` NÃO contém uma linha mencionando `engine-lwjgl` como segundo backend a ser construído (ela foi promovida para `Active` ao virar proposal; será removida ao ser arquivada)

#### Scenario: Active section tracks in-flight changes

- **WHEN** uma change está com implementação rodando ou aguardando merge
- **THEN** uma linha resumindo-a aparece em `Active`
- **AND** o resumo cabe numa linha "o que muda + por quê"
