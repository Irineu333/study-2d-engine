## Why

A SPI `Renderer`/`Input` da engine foi escrita com Compose como único cliente; sem um segundo backend real, "interface" e "código bem nomeado" são indistinguíveis, e a invariante 4 ("Compose é apenas o primeiro backend") é uma promessa não testada. Introduzir um runtime Skiko puro (sem Compose) força a SPI a ser exercida por dois caminhos diferentes, expõe decisões hoje implícitas (host de execução, toggles de debug, unidade de coordenadas) e cristaliza um conceito ausente: a "host de execução do jogo".

## What Changes

- Adicionar capability `skiko-runtime`: novo módulo `:engine-skiko` com `SkikoRenderer`, `SkikoInput`, `SkikoHost` (`SkiaLayer + JFrame`, Swing-based).
- **BREAKING**: promover a "host de execução" a SPI de primeira classe — nova `interface GameHost { fun run(scene, config) }` em `:engine`, com `data class GameConfig(title, width, height, toggleFpsKey, toggleCollidersKey, ...)`.
- **BREAKING**: mover o desenho do overlay de FPS + colliders do `GameSurface` (Compose) para uma função pura `renderDebugOverlay(renderer, scene)` em `:engine`, chamada pelos dois hosts.
- **BREAKING**: tratamento dos toggles `F1`/`F2` deixa de viver no `Main.kt` de cada jogo (que hoje usa `Compose.Key.F1` na janela) e passa a ser responsabilidade do `GameHost`, configurável via `GameConfig`. Para suportar isso, adicionar `Key.F1`/`Key.F2` ao enum em `:engine` e ao mapeamento de cada backend.
- Migrar `:games:pong` e `:games:demos` para Skiko como backend padrão. Remover **todas** as dependências Compose desses dois módulos (plugins `composeMultiplatform`/`composeCompiler`, `compose.desktop`, `kotlinx-coroutines-swing`) e trocar pelo plugin `application` do Gradle puro.
- Manter `:games:tictactoe` em Compose como sentinela viva do segundo backend.
- Wrap do atual `GameSurface` em `ComposeHost : GameHost`, sem perder a API composable existente.
- Tornar Skiko o backend padrão e Compose o secundário, atualizando a invariante 4 e o roadmap em `CLAUDE.md`.

## Capabilities

### New Capabilities
- `skiko-runtime`: backend Skiko puro da engine — `SkikoRenderer` (Renderer SPI sobre `org.jetbrains.skia.Canvas`), `SkikoInput` (Input SPI sobre AWT `KeyListener` + `MouseListener` + `MouseMotionListener`), `SkikoHost` (GameHost SPI hospedando `SkiaLayer` em `JFrame` Swing). Único módulo (além de `:engine-compose`) autorizado a depender de runtime gráfico específico.

### Modified Capabilities
- `engine-core`: introduz `GameHost` SPI + `GameConfig`, adiciona `Key.F1`/`Key.F2`, introduz utilitário `renderDebugOverlay(renderer, scene)`, e desloca a invariante "Renderer/Input são SPIs; Compose é o primeiro backend" para "Renderer/Input/GameHost são SPIs; Skiko é o backend padrão; Compose é o segundo backend".
- `compose-runtime`: o `GameSurface` deixa de aplicar overlay diretamente (delega para `renderDebugOverlay`); a capability ganha `ComposeHost : GameHost` que envelopa o composable; toggles de F1/F2 passam pelo host, não pelo `Main.kt`.
- `project-conventions`: atualizar `CLAUDE.md` — invariante 4 reformulada, roadmap ganha linha `add-skiko-runtime`, instruções de execução continuam idênticas (`./gradlew :games:<jogo>:run`) porque o plugin `application` mantém a task `run`.

## Impact

- **Código**: novo módulo `:engine-skiko`; reescrita de `:engine-compose/GameSurface.kt` (extração de overlay + `ComposeHost`); novo `Main.kt` de `:games:pong` e `:games:demos`; build files desses dois jogos perdem Compose.
- **Build**: `settings.gradle.kts` ganha `:engine-skiko`. Version catalog (`libs.versions.toml`) ganha versão `skiko` alinhada com a embutida no Compose Multiplatform 1.11.0 (evita duplo classloader) e dependência `skiko-awt-runtime` com classifier nativo resolvido por `os.name`/`os.arch` no `build.gradle.kts` do módulo.
- **API pública**: `Key` ganha duas entradas (não breaking — adicionar a um enum aberto é compatível). `GameSurface` continua exportado para uso direto, mas o caminho recomendado passa a ser `ComposeHost().run(scene, config)`.
- **Documentação**: `CLAUDE.md` é o ponto único de atualização (capability `project-conventions`).
- **Validação**: manual em macOS — Pong (W/S, IA, colisão, F1/F2, resize), Demos (1/2/3, spawner, F2 do host), Velha (mouse, F1/F2 via novo host).
- **Não-objetivos**: `SkikoWindow` (sem AWT) postergado; binários Linux/Windows não validados nesta change; troca de backend em runtime / `ServiceLoader` não introduzida; refator do enum `Key` limitado a F1/F2; `SkikoInput` mantém `ConcurrentHashMap.newKeySet()` por simetria com `ComposeInput`, ainda que EDT-only.
