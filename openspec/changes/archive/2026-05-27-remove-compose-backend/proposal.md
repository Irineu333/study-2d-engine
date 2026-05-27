## Why

Compose Multiplatform Desktop nunca foi um segundo backend de render *de verdade*: Compose Desktop renderiza sobre Skia, então `:engine-compose` é essencialmente Skia com plumbing diferente do `:engine-skiko`. Mantê-lo paga custo real (Compose Compiler atrelado à versão de Kotlin, +1 plugin no build, ~410 LoC de tradução de SPIs) sem dar em troca a validação arquitetural que motivou sua existência. Com `:games:tictactoe` como único consumidor e o editor visual roadmappeado em Skiko, o módulo cumpriu o papel didático e passou a estorvar evolução.

## What Changes

- **BREAKING** Remove o módulo `:engine-compose` inteiro: `ComposeHost`, `ComposeRenderer`, `ComposeInput`, `GameSurface` (4 arquivos, ~410 LoC) e seu `build.gradle.kts`.
- **BREAKING** Remove `include(":engine-compose")` em `settings.gradle.kts`.
- **BREAKING** Remove dependências Compose do `libs.versions.toml` (plugins `composeMultiplatform`/`composeCompiler`, libs `compose.runtime/foundation/ui`, `compose.desktop.currentOs`, `kotlinx.coroutinesSwing`) — apenas as que ficarem sem consumidor após a remoção.
- Migra `:games:tictactoe`: `ComposeHost()` → `SkikoHost()` em `Main.kt`; troca `implementation(projects.engineCompose)` por `implementation(projects.engineSkiko)`; remove plugins Compose do `build.gradle.kts` (passa a usar apenas `kotlinJvm`). Bundle Lua (`scene.json` + `scripts/board.lua`) NÃO muda.
- Reescreve o invariante #2 do CLAUDE.md de forma genérica: ":engine não depende de nenhum framework de UI" — não mais Compose-específico.
- Reescreve o invariante #4 do CLAUDE.md (redação **forte**): SPIs `Renderer`/`Input`/`GameHost` permanecem obrigatórias; `:engine` continua proibido de vazar tipos de Skiko (`org.jetbrains.skia.*`, `SkikoView`, `SkiaLayer`); LWJGL está planejado como segundo backend experimental para revalidar a SPI.
- Atualiza descrição de `:games:tictactoe` no CLAUDE.md: passa a ser sentinela apenas do segundo backend de **scripting** (Lua), não mais de render.
- Adiciona linha em `ROADMAP.md → Planned` antecipando `engine-lwjgl` como segundo backend experimental (substituto do papel que Compose ocupava no invariante #4).

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `compose-runtime`: **REMOVED** — capability inteira deixa de existir. O módulo é deletado; nenhum requisito sobrevive.
- `tictactoe-sample`: troca de host de render — passa a depender de `:engine-skiko` em vez de `:engine-compose`; `Main.kt` instancia `SkikoHost`; ciclo "BundleLoader → SceneTree → SkikoHost" substitui "BundleLoader → SceneTree → ComposeHost". Continua sentinela de `LuaScriptHost`.
- `engine-core`: invariantes arquiteturais (#2 ":engine sem dependência de UI framework" e #4 "SPIs Renderer/Input/GameHost") reescritos para refletir o vácuo de segundo backend e a intenção LWJGL.
- `project-conventions`: remoção de todas as menções a Compose como segundo backend; texto reescrito para Skiko-only ativo + LWJGL planejado.

## Impact

**Código**:
- Deletado: `engine-compose/` (módulo inteiro, ~410 LoC + `build.gradle.kts`).
- Modificado: `games/tictactoe/src/main/kotlin/.../Main.kt` (imports + instanciação do host), `games/tictactoe/build.gradle.kts` (dependência e plugins).
- Modificado: `settings.gradle.kts` (remove `include(":engine-compose")`).
- Modificado: `gradle/libs.versions.toml` (limpa entradas Compose se não houver outros consumidores).

**Docs**:
- `CLAUDE.md`: header, invariantes #2 e #4, módulo `:games:tictactoe`, seção "Para rodar Velha", estrutura de módulos.
- `ROADMAP.md`: adiciona `engine-lwjgl` em Planned.
- `openspec/specs/`: capabilities `compose-runtime` (delete), `tictactoe-sample`, `engine-core`, `project-conventions`.

**Dependências externas**: Compose Multiplatform plugin, Compose Compiler plugin, libs Compose, `kotlinx.coroutinesSwing` saem do classpath (se nada mais consumir).

**Build**: tempo de configuração e compilação cai (menos um módulo, menos plugins). `./gradlew build` precisa permanecer verde após a remoção.

**Runtime**: `:games:tictactoe` muda de janela Compose para janela Skiko/JFrame; comportamento de gameplay (clique, X/O, restart, F1 FPS, F2 colliders) deve permanecer idêntico ao usuário final.

**Riscos**:
- Paridade Skiko ↔ Compose para TTT: confirmar que `SkikoInput.pointerPosition` reporta em pixels da surface e que `SkikoRenderer` opera sob fallback identity (sem `Camera2D`).
- Vácuo temporário do invariante #4: aceito explicitamente; cobertura volta com `engine-lwjgl`.
