# project-conventions Specification

## Purpose

Documento `CLAUDE.md` na raiz consolidando propósito do projeto, invariantes arquiteturais, estrutura de módulos, convenções de código, workflow OpenSpec e roadmap. Funciona como decision log perene para contribuidores humanos ou agentes.

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

The `CLAUDE.md` SHALL list the architectural invariants that any change must respect, including at minimum: (1) scene graph style Godot (inheritance, no Unity-style components), (2) `:engine` has no dependency on `androidx.compose.*` or `org.jetbrains.compose.*`, (3) collision uses `Collider`-as-node with a central `PhysicsSystem`, (4) `Renderer`, `Input` and `GameHost` are SPIs; Skiko is the default backend and Compose is the second backend.

#### Scenario: Invariants section enumerates the core decisions

- **WHEN** `CLAUDE.md` is opened
- **THEN** the invariants section lists at least the four decisions above with one-line rationale each
- **AND** invariant (4) explicitly names `GameHost` as an SPI alongside `Renderer` and `Input`
- **AND** invariant (4) explicitly identifies Skiko as the default backend and Compose as the second backend

### Requirement: CLAUDE.md describes module structure and how to run

The `CLAUDE.md` SHALL describe the project's module layout (`:engine`, `:engine-compose`, `:engine-skiko`, `:games:<name>`) and the command to run a game module (`./gradlew :games:<name>:run`). The document MUST clarify which game runs on which backend after the migration to Skiko-as-default (Pong and Demos on Skiko; Tic Tac Toe on Compose). Removal of the template's `:desktopApp` and `:shared` modules MUST remain noted.

#### Scenario: Module structure section is accurate

- **WHEN** a developer compares the section to `settings.gradle.kts`
- **THEN** the listed modules match the actual project graph
- **AND** `:engine-skiko` appears alongside `:engine` and `:engine-compose`

#### Scenario: Backend per game is stated

- **WHEN** `CLAUDE.md` is opened
- **THEN** the module-structure section names Skiko as the backend used by `:games:pong` and `:games:demos`
- **AND** names Compose as the backend used by `:games:tictactoe`

#### Scenario: Run instructions work as written

- **WHEN** a developer runs the command shown for Pong
- **THEN** the game starts without additional setup steps

### Requirement: CLAUDE.md states coding conventions

The `CLAUDE.md` SHALL state the project's coding conventions, including at minimum: comments are added only for non-obvious "why" (never for what is already self-evident from naming); public API of `:engine` is documented with KDoc; engine identifiers are in English while in-game text in sample games MAY be in Portuguese.

#### Scenario: Conventions section is present

- **WHEN** `CLAUDE.md` is opened
- **THEN** a "Conventions" (or equivalently titled) section enumerates the rules above

### Requirement: CLAUDE.md describes the OpenSpec workflow and roadmap

The `CLAUDE.md` SHALL explain that material changes (architecture, public API, new modules, new capabilities) go through OpenSpec change proposals before implementation, and SHALL include a visible roadmap pointing to the active and planned changes. The roadmap MUST list each archived change with status `Archived`, including `engine-foundation`, `add-tictactoe`, `engine-consistency`, and `add-skiko-runtime` after this change is archived. The roadmap MUST be updated when an active change advances.

#### Scenario: Workflow section refers contributors to OpenSpec

- **WHEN** a contributor wants to propose a feature
- **THEN** the workflow section directs them to create an OpenSpec change rather than open a code PR directly

#### Scenario: Roadmap reflects current state

- **WHEN** `CLAUDE.md` is read at the end of this change
- **THEN** the roadmap includes a row for `add-skiko-runtime` with status `Archived`
- **AND** the row's summary mentions that Skiko became the default backend and that `GameHost` was introduced as an SPI
