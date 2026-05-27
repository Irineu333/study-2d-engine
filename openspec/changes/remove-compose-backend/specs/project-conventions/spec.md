## MODIFIED Requirements

### Requirement: CLAUDE.md enumerates invariant architectural decisions

The `CLAUDE.md` SHALL list the architectural invariants that any change must respect, including at minimum: (1) scene graph style Godot (inheritance, no Unity-style components), (2) `:engine` has no dependency on any UI or render framework (no `androidx.compose.*`, `org.jetbrains.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, no Swing/AWT widget types, no future LWJGL/OpenGL/Vulkan bindings), (3) collision uses `Collider`-as-node with a central `PhysicsSystem`, (4) `Renderer`, `Input` and `GameHost` are SPIs; Skiko is the only render backend currently active (`:engine-skiko`), no other module is allowed to leak Skiko types into `:engine`, and an LWJGL-based experimental backend is planned to revalidate the SPI, (5) **the live tree is owned by a `SceneTree` that is not a `Node` and not `@Serializable`; a `Scene` class no longer exists in `:engine`; nodes reach the tree via the cached `Node.tree` property (set on attach, cleared on detach); `SceneTree` is not subclassable for setup — a root `Node` with `onEnter()` populates the tree; `SceneLoader.load` and `BundleLoader` return `Node` (root-type free); the host wraps the root in `SceneTree(root = ...)` before `run(...)`**.

#### Scenario: Invariants section enumerates the core decisions

- **WHEN** `CLAUDE.md` is opened
- **THEN** the invariants section lists at least the five decisions above with one-line rationale each
- **AND** invariant (2) is worded generically over UI/render frameworks (not Compose-specific)
- **AND** invariant (4) explicitly names `GameHost` as an SPI alongside `Renderer` and `Input`
- **AND** invariant (4) identifies Skiko as the only render backend currently active
- **AND** invariant (4) explicitly forbids `:engine` from referencing Skiko types
- **AND** invariant (4) names LWJGL as the planned experimental second backend that will revalidate the SPI
- **AND** invariant (5) is present and explicitly states that `SceneTree` is not a `Node` and that `Scene` has been removed
- **AND** invariant (5) names `Node.tree` as the cached access path from any live node
- **AND** invariant (5) prescribes the host-wraps-root pattern (`Host.run(SceneTree(root = ...), config)`)

#### Scenario: No mention of Compose as second backend remains

- **WHEN** `CLAUDE.md` is opened
- **THEN** no paragraph in the invariants section names Compose Multiplatform as a second render backend
- **AND** no paragraph references `:engine-compose` as a current module

### Requirement: CLAUDE.md describes module structure and how to run

O `CLAUDE.md` SHALL descrever o layout de módulos do projeto (`:engine`, `:engine-bundle`, `:engine-bundle-python`, `:engine-bundle-lua`, `:engine-skiko`, `:games:<name>`) e o comando para rodar um módulo de jogo (`./gradlew :games:<name>:run`). O documento MUST esclarecer que todos os jogos rodam em Skiko (Pong, Demos, Hello-World, Tic Tac Toe). O documento MUST NOT listar `:engine-compose` na seção de módulos — esse módulo foi removido nesta change. A remoção dos módulos `:desktopApp` e `:shared` do template MUST permanecer registrada. O módulo `:engine-scripting` MUST NÃO aparecer no documento — foi absorvido por `:engine-bundle` e em seguida substituído por `:engine-bundle-python` como local de scripting. A linha do `:engine-bundle` MUST descrever-lo como hospedeiro do `BundleLoader` e da **SPI `ScriptHost`** (agnóstica de linguagem), sem mencionar Kotlin Scripting. A linha do `:engine-bundle-python` MUST descrevê-lo como implementação de `ScriptHost` para Python via GraalPy. A linha do `:engine-bundle-lua` MUST descrevê-lo como implementação de `ScriptHost` para Lua via LuaJ. A descrição de `:games:tictactoe` MUST identificá-lo como "jogo Velha (humano vs humano), roda em Skiko com scripting Lua — sentinela do segundo backend de scripting" — não mais como sentinela do segundo backend de render.

#### Scenario: Module structure section is accurate

- **WHEN** um desenvolvedor compara a seção com `settings.gradle.kts`
- **THEN** os módulos listados batem com o grafo real do projeto
- **AND** `:engine-bundle` aparece ao lado de `:engine` e `:engine-skiko`
- **AND** `:engine-bundle-python` aparece listado separadamente
- **AND** `:engine-bundle-lua` aparece listado separadamente
- **AND** `:engine-compose` NÃO aparece em nenhum lugar do documento como módulo ativo
- **AND** `:engine-scripting` NÃO aparece nem na seção de módulos nem no roadmap como módulo ativo

#### Scenario: All games run on Skiko

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a seção de módulos nomeia Skiko como backend usado por `:games:pong`, `:games:demos`, `:games:hello-world` e `:games:tictactoe`
- **AND** NÃO menciona Compose como backend de nenhum jogo

#### Scenario: Tictactoe role is described as Lua scripting sentinel

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a descrição de `:games:tictactoe` identifica seu papel como sentinela do segundo backend de scripting (Lua)
- **AND** NÃO o identifica como sentinela do segundo backend de render

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

#### Scenario: Run instructions work as written

- **WHEN** um desenvolvedor executa o comando mostrado para Pong, Tic Tac Toe, Demos ou Hello-World
- **THEN** o jogo inicia sem passos adicionais
