# project-conventions Specification

## Purpose

Define os dois entrypoints documentais do projeto e os contratos de cada um. O `CLAUDE.md` na raiz funciona como decision log perene para contribuidores humanos ou agentes (propósito do projeto, invariantes arquiteturais, layout de módulos e games, convenções de código, workflow OpenSpec, mapa de "onde achar o resto"). O `README.md` na raiz funciona como entrypoint humano (proposta, capacidades, quickstart Gradle, demos, controles globais, configuração de IDE). O `ROADMAP.md` permanece como fonte da verdade do roadmap (changes ativas e planejadas) sem duplicar o histórico arquivado.

## Requirements

### Requirement: CLAUDE.md exists at repository root

The repository SHALL contain a `CLAUDE.md` file at the project root. The file MUST be kept under version control and updated when foundational decisions change. The file MUST be written in a way that lets a new contributor (human or AI) get oriented without reading the entire codebase first.

#### Scenario: Fresh checkout includes the document

- **WHEN** a developer clones the repository
- **THEN** `CLAUDE.md` is present at the root

### Requirement: CLAUDE.md states the project purpose

The `CLAUDE.md` SHALL include a section explicitly describing the project's purpose: a 2D game engine built for learning, starting code-only with sample games and evolving toward a visual editor.

#### Scenario: Purpose section is present

- **WHEN** `CLAUDE.md` is opened
- **THEN** a "Purpose" (or equivalently titled) section appears near the top
- **AND** it explains the project as a 2D game engine for learning

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

### Requirement: CLAUDE.md describes module structure and games

O `CLAUDE.md` SHALL descrever o layout de módulos do projeto (`:engine`, `:engine-bundle`, `:engine-bundle-python`, `:engine-bundle-lua`, `:engine-skiko`, `:engine-lwjgl`, `:games:<name>`) com uma linha de responsabilidade para cada um. O `CLAUDE.md` SHALL incluir uma seção dedicada listando os jogos shipped (`:games:pong`, `:games:tictactoe`, `:games:demos`, `:games:snake`, `:games:hello-world`) com seu backend de render, scripting e função-sentinela na engine. O `CLAUDE.md` MUST NÃO incluir comandos Gradle de execução (`./gradlew :games:<name>:run`), controles in-game por jogo, descrição cena-a-cena de Demos, ou caveat macOS sobre `-XstartOnFirstThread` — esse material vive no `README.md` (entrypoint humano) e nas specs `<jogo>-sample` / `demos-sample`. O `CLAUDE.md` MUST NÃO listar `:engine-compose` na seção de módulos. A linha do `:engine-lwjgl` MUST descrevê-lo como "implementação de `Renderer`/`Input`/`GameHost` SPIs via LWJGL (NanoVG + GLFW + OpenGL 3.3 core); segundo backend ativo; sentinela do invariante #4 via entrypoint LWJGL de `:games:demos`". A descrição de `:games:tictactoe` MUST continuar identificando-o como sentinela do segundo backend de scripting (Lua).

#### Scenario: Module section lists the active modules

- **WHEN** um desenvolvedor compara a seção com `settings.gradle.kts`
- **THEN** os módulos listados batem com o grafo real do projeto
- **AND** `:engine-bundle`, `:engine-bundle-python`, `:engine-bundle-lua`, `:engine-skiko`, `:engine-lwjgl` aparecem listados separadamente
- **AND** `:engine-compose` NÃO aparece em nenhum lugar do documento
- **AND** `:engine-scripting` NÃO aparece em nenhum lugar do documento

#### Scenario: Games section enumerates shipped games with their role

- **WHEN** `CLAUDE.md` é aberto
- **THEN** existe uma seção "Games" (ou equivalente) listando `:games:pong`, `:games:tictactoe`, `:games:demos`, `:games:snake`, `:games:hello-world`
- **AND** cada linha identifica o backend de render, o backend de scripting (se houver) e a função-sentinela na engine
- **AND** `:games:tictactoe` é identificado como sentinela do segundo backend de scripting (Lua), e NÃO como sentinela do segundo backend de render

#### Scenario: CLAUDE.md does not embed a runbook

- **WHEN** `CLAUDE.md` é aberto
- **THEN** nenhum comando `./gradlew :games:*:run` aparece no documento
- **AND** nenhuma instrução de execução, caveat de plataforma ou descrição de controles in-game aparece
- **AND** descrições cena-a-cena das demos (Solar system, Scale hierarchy, Spawner, Collision stress, Rotating box, Tumbling swarm) NÃO aparecem — esse conteúdo vive em `openspec/specs/demos-sample`

#### Scenario: engine-bundle responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-bundle` descreve sua responsabilidade como "carregar cena via bundle (scene.json + scripts/) e hospedar a SPI `ScriptHost` agnóstica de linguagem"

#### Scenario: engine-bundle-python responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-bundle-python` descreve sua responsabilidade como "implementação concreta de `ScriptHost` para scripts Python `.py`, usando GraalPy"

#### Scenario: engine-bundle-lua responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-bundle-lua` descreve sua responsabilidade como "implementação concreta de `ScriptHost` para scripts Lua `.lua`, usando LuaJ"

#### Scenario: engine-lwjgl responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-lwjgl` descreve sua responsabilidade como "implementação de `Renderer`/`Input`/`GameHost` SPIs via LWJGL (NanoVG + GLFW + OpenGL 3.3 core); segundo backend ativo de render; sentinela do invariante #4 via entrypoint LWJGL de `:games:demos`"
- **AND** indica que apenas `:engine-lwjgl` declara dependência em `org.lwjgl.*` artifacts

### Requirement: CLAUDE.md states coding conventions

O `CLAUDE.md` SHALL listar as convenções de código do projeto como bullets curtos, incluindo no mínimo: comentários só para o "por quê" não-óbvio; identificadores em inglês (texto in-game pode ser em português); API pública de `:engine` documentada com KDoc quando o uso pretendido não for óbvio; imutabilidade onde for barata (`Vec2`/`Rect`/`Transform`/`Color`); folhas `Node2D` shipped por `:engine` são `open` por default; sem dependências escondidas (declaradas no `build.gradle.kts` do módulo que precisa); testes para regras invariantes; `Node2D.onDraw` desenha em local space; disciplina `@Inspect` / `@Transient` para classes `@Serializable`. O `CLAUDE.md` MUST NÃO incluir o **contrato detalhado de scripting** (estrutura de script, hooks, exports, signals, scene.json properties) — esse contrato vive em `openspec/specs/python-scripting/spec.md` e `openspec/specs/lua-scripting/spec.md` e é referenciado por um ponteiro curto. O `CLAUDE.md` MUST NÃO incluir code snippets além do necessário para regras que ficam ambíguas sem exemplo (na prática, zero). O `CLAUDE.md` MUST NÃO incluir uma seção "Performance Notes" sobre cache de `world()` ou `Node.tree` — esse material vive em `openspec/specs/engine-core/spec.md` como Requirements "Transform composition by ancestry" e "Node caches its SceneTree".

#### Scenario: Conventions section lists the rules as bullets

- **WHEN** `CLAUDE.md` é aberto
- **THEN** uma seção "Coding Conventions" (ou título equivalente) enumera as regras acima em bullets curtos
- **AND** nenhum bloco de código com mais de duas linhas aparece nessa seção

#### Scenario: Scripting model is summarized with pointers

- **WHEN** `CLAUDE.md` é aberto
- **THEN** uma seção "Scripting Model" (ou equivalente) descreve em até cinco linhas que o modelo é Godot-style (hooks underscore-prefixed, factory `signal()`, fail-fast)
- **AND** essa seção referencia explicitamente `openspec/specs/python-scripting` e `openspec/specs/lua-scripting` para o contrato detalhado
- **AND** essa seção NÃO inclui code snippets de scripts Python ou Lua
- **AND** essa seção NÃO inclui exemplo de `scene.json`

#### Scenario: CLAUDE.md does not duplicate performance notes from engine-core

- **WHEN** `CLAUDE.md` é aberto
- **THEN** nenhuma seção descreve detalhes de invalidação de cache de `world()` ou cache de `Node.tree`
- **AND** o assunto, se mencionado, aparece apenas como ponteiro para `openspec/specs/engine-core`

#### Scenario: CLAUDE.md does not duplicate Camera2D rationale from engine-core

- **WHEN** `CLAUDE.md` é aberto
- **THEN** nenhuma seção descreve em detalhe que `Camera2D.bounds` define o espaço de mundo virtual, `AspectMode`, fallback identity ou view transform pipeline
- **AND** o assunto, se mencionado, aparece apenas como ponteiro para `openspec/specs/engine-core`

### Requirement: CLAUDE.md describes the OpenSpec workflow

O `CLAUDE.md` SHALL explicar que mudanças materiais (arquitetura, API pública, novos módulos, novas capabilities) passam por proposta OpenSpec antes da implementação. A seção MUST listar os passos do fluxo (`explore`, `propose`, `apply`, `verify`, `archive`). O `CLAUDE.md` MUST NÃO incluir um roadmap visível (lista de changes ativas/planejadas/arquivadas) — o roadmap vive em `ROADMAP.md`, e `CLAUDE.md` apenas referencia esse arquivo na seção "Where to find more".

#### Scenario: Workflow section refers contributors to OpenSpec

- **WHEN** um contribuidor quer propor uma feature
- **THEN** a seção de workflow direciona a criar uma change OpenSpec em vez de abrir um PR direto
- **AND** lista os passos do fluxo (`explore`, `propose`, `apply`, `verify`, `archive`)

#### Scenario: No roadmap content lives in CLAUDE.md

- **WHEN** `CLAUDE.md` é aberto
- **THEN** nenhuma seção lista changes ativas, planejadas ou arquivadas
- **AND** o roadmap, se mencionado, aparece apenas como link para `ROADMAP.md`

### Requirement: CLAUDE.md provides a "Where to find more" map

O `CLAUDE.md` SHALL incluir uma seção curta no fim do documento ("Where to find more" ou equivalente) listando os recursos para onde um leitor (humano ou IA) precisa ir para conteúdo que NÃO mora no `CLAUDE.md`. A lista MUST incluir: `ROADMAP.md` (changes ativas e planejadas), `openspec/specs/` (especificações por capability), `openspec/changes/archive/` (histórico de changes arquivadas), e `README.md` (entrypoint humano com quickstart e capacidades).

#### Scenario: Pointers section is present at end of CLAUDE.md

- **WHEN** `CLAUDE.md` é aberto e se rola até o fim
- **THEN** existe uma seção mapeando `ROADMAP.md`, `openspec/specs/`, `openspec/changes/archive/` e `README.md`
- **AND** cada ponteiro tem uma frase curta descrevendo o conteúdo que vive lá

### Requirement: README.md is the human entrypoint

O repositório SHALL conter um `README.md` na raiz funcionando como entrypoint para humanos (em contraste com o `CLAUDE.md`, que serve IAs e como decision log perene). O `README.md` MUST conter, na ordem ou em ordem equivalente que faça sentido para leitor de primeira vez: (1) uma proposta breve da engine; (2) tabelas de capacidades (backends de render, linguagens de scripting suportadas, jogos shipped) atualizadas — LWJGL MUST aparecer como segundo backend ativo (não "planejado"), Snake MUST aparecer como jogo shipped; (3) quickstart com comandos Gradle (`./gradlew :games:<name>:run` para cada jogo shipped, incluindo `./gradlew :games:demos:runLwjgl`); (4) caveat macOS sobre `-XstartOnFirstThread` e a injeção automática pela task `runLwjgl`; (5) resumo das cenas de Demos (até uma linha por cena); (6) controles globais (`F1`/`F2`/`F3`); (7) instruções de configuração de IDE para stubs Python (`.pyi` para Pyright/Pylance) e Lua (LuaCATS para sumneko-lua); (8) links para `CLAUDE.md`, `ROADMAP.md`, `openspec/specs/` e `openspec/changes/archive/`.

#### Scenario: README.md is present at repository root

- **WHEN** um desenvolvedor clona o repositório
- **THEN** `README.md` está presente na raiz
- **AND** é o primeiro documento que GitHub renderiza na página do repositório

#### Scenario: README.md proposes the project to humans

- **WHEN** `README.md` é aberto
- **THEN** uma seção inicial descreve a proposta da engine (2D game engine para aprender; Kotlin; Skiko default + LWJGL como segundo backend; Godot-style scene graph)
- **AND** essa seção NÃO duplica o conteúdo de invariantes arquiteturais (que vive em `CLAUDE.md`)

#### Scenario: README.md lists current capabilities accurately

- **WHEN** `README.md` é aberto
- **THEN** existem tabelas listando backends de render, linguagens de scripting e jogos shipped
- **AND** a tabela de backends identifica Skiko como default e LWJGL como segundo backend ativo
- **AND** a tabela de backends NÃO identifica LWJGL como "planejado"
- **AND** a tabela de jogos lista Pong, Velha, Demos, Snake e Hello World
- **AND** a tabela de jogos NÃO identifica Snake como "planejado"

#### Scenario: README.md documents quickstart Gradle commands

- **WHEN** `README.md` é aberto
- **THEN** uma seção de quickstart lista os comandos `./gradlew :games:pong:run`, `./gradlew :games:tictactoe:run`, `./gradlew :games:demos:run`, `./gradlew :games:demos:runLwjgl`, `./gradlew :games:snake:run` e `./gradlew :games:hello-world:run`
- **AND** o comando `./gradlew :games:demos:runLwjgl` é documentado como rodando o mesmo Demos sobre o segundo backend LWJGL
- **AND** o caveat macOS sobre `-XstartOnFirstThread` aparece próximo aos comandos LWJGL, mencionando que a task `runLwjgl` injeta a flag automaticamente

#### Scenario: README.md summarizes the demo scenes

- **WHEN** `README.md` é aberto
- **THEN** existe uma seção resumindo as cenas de Demos (Solar system, Scale hierarchy, Spawner, Collision stress, Rotating box, Tumbling swarm)
- **AND** cada cena recebe um resumo de até uma linha
- **AND** o `README.md` NÃO duplica a descrição detalhada de cada cena (que vive em `openspec/specs/demos-sample`)

#### Scenario: README.md lists global toggle controls

- **WHEN** `README.md` é aberto
- **THEN** uma seção lista os controles globais que valem para todos os jogos: `F1` (overlay FPS), `F2` (overlay de colliders), `F3` (overlay de momento, apenas em Demos)
- **AND** controles específicos de cada jogo NÃO são listados em detalhe; o `README.md` direciona o leitor para `openspec/specs/<jogo>-sample` quando precisar do mapeamento completo

#### Scenario: README.md documents IDE stub configuration

- **WHEN** `README.md` é aberto
- **THEN** uma seção descreve como configurar Pyright/Pylance para autocompletar scripts Python (`engine-bundle-python/src/main/resources/stubs/` como `extraPaths`)
- **AND** uma seção descreve como configurar sumneko-lua para autocompletar scripts Lua (`engine-bundle-lua/src/main/resources/stubs/` como `workspace.library`)

#### Scenario: README.md cross-references CLAUDE.md and OpenSpec

- **WHEN** `README.md` é aberto
- **THEN** uma seção final lista pelo menos: `CLAUDE.md` (invariantes/convenções/scripting summary), `ROADMAP.md` (changes ativas e planejadas), `openspec/specs/` (especificações por capability) e `openspec/changes/archive/` (histórico arquivado)

### Requirement: ROADMAP.md tracks active and planned changes without duplicating archive

O `ROADMAP.md` SHALL conter duas seções (`Active`, `Planned`) listando changes OpenSpec em andamento e intenções firmadas, respectivamente, com resumo de uma linha cada. O documento MUST NÃO duplicar histórico de changes arquivadas (que vive em `openspec/changes/archive/`). Quando uma change planejada vira proposal, MUST ser promovida de `Planned` para `Active`. Quando uma change é arquivada, sua linha MUST ser removida de `Active`. A change `engine-lwjgl` MUST NÃO aparecer em `Planned` após esta change — ela é Active enquanto a implementação roda, e some quando arquivada.

#### Scenario: engine-lwjgl is not listed in Planned after this change

- **WHEN** `ROADMAP.md` é aberto após esta change
- **THEN** a seção `Planned` NÃO contém uma linha mencionando `engine-lwjgl` como segundo backend a ser construído (ela foi promovida para `Active` ao virar proposal; será removida ao ser arquivada)
