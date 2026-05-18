## Why

O repositório é apenas um template Compose Multiplatform "Hello, World"; ainda não há engine. Esta change estabelece a fundação code-only de uma 2D game engine com scene graph estilo Godot e entrega Pong como prova viva de que a fundação funciona. Sem esta etapa, qualquer discussão sobre tooling/editor é especulativa — Pong força game loop, input contínuo, física e renderização a aparecerem juntos.

## What Changes

- Introduz módulo `:engine` em Kotlin puro (sem dependências Compose) com scene graph estilo Godot: `Node`/`Node2D`, primitivas visuais (`Shape`, `Text`), `Scene`, lifecycle (`onEnter`/`onUpdate`/`onRender`/`onExit`), math (`Vec2`, `Rect`, `Transform`), SPI de `Renderer` e `Input`, sistema de colisão (`Collider` como nó + `PhysicsSystem` com broad phase O(N²)) e `GameLoop`.
- Introduz módulo `:engine-compose` como primeiro runtime: `ComposeRenderer` sobre `DrawScope`, `ComposeInput` sobre eventos Compose, `GameSurface` composable que dirige o loop via `withFrameNanos`.
- Introduz módulo `:games:pong` como executável standalone com Pong jogável (humano vs IA simples) usando apenas a API pública da engine.
- Adiciona DX inicial: overlay de FPS togglable, log estruturado simples, visualização de colliders (desenha `bounds` com flag de debug).
- Adiciona `CLAUDE.md` consolidando propósito do projeto, decisões arquiteturais invariantes, estrutura de módulos, convenções de código, workflow OpenSpec e roadmap visível.
- **BREAKING**: Remove módulo `:desktopApp` e o `App.kt` boilerplate de `:shared` herdados do template KMP. O módulo `:shared` é substituído por `:engine`. Cada jogo passa a ter seu próprio `main()` e é executado via `./gradlew :games:<jogo>:run`.

## Capabilities

### New Capabilities

- `engine-core`: scene graph estilo Godot em Kotlin puro — `Node` hierarchy, lifecycle, `Scene`, math primitives, SPI de `Renderer`/`Input`, `Collider` + `PhysicsSystem`, `GameLoop`. Invariante: zero dependência em `androidx.compose.*`.
- `compose-runtime`: backend Compose Multiplatform da engine — implementações de `Renderer` e `Input` sobre Compose, `GameSurface` composable que integra game loop em `withFrameNanos`.
- `dx-tooling`: ferramentas de developer experience embutidas na engine — overlay de FPS togglable, log estruturado, visualização de debug de colliders.
- `pong-sample`: jogo de Pong jogável (humano vs IA) como módulo executável `:games:pong`, servindo como teste de aceitação vivo da fundação da engine.
- `project-conventions`: documento `CLAUDE.md` na raiz consolidando propósito, invariantes arquiteturais, estrutura, convenções de código, workflow OpenSpec e roadmap. Decision log perene.

### Modified Capabilities

<!-- Nenhuma capability existente — repositório está em estado inicial (template KMP). -->

## Impact

- **Estrutura de módulos**: passa de `:shared` + `:desktopApp` (template) para `:engine` + `:engine-compose` + `:games:pong`. `settings.gradle.kts` reescrito.
- **Arquivos removidos**: `desktopApp/`, `shared/src/commonMain/kotlin/com/neoutils/engine/App.kt`. O módulo `:shared` é renomeado/substituído por `:engine`.
- **Dependências**: `:engine` mantém apenas Kotlin stdlib. `:engine-compose` é o único módulo com dependência em Compose Multiplatform. `:games:pong` depende de `:engine` + `:engine-compose`.
- **Como rodar**: substitui `./gradlew :desktopApp:run` por `./gradlew :games:pong:run`. Comando de hot reload do template deixa de ser aplicável.
- **Pacote base**: mantém `com.neoutils.engine` para o módulo `:engine`; jogos vivem em `com.neoutils.engine.games.<jogo>`.
- **Pontos de evolução documentados (não implementados nesta change)**: sinais/eventos entre nós (hoje: referência direta), broad phase de colisão escalável (hoje: O(N²)), backends adicionais de `Renderer`/`Input` (hoje: só Compose), sprites com textura, áudio, animações, serialização de cena, multiplataforma além de JVM Desktop, editor visual.