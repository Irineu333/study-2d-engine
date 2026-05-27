## ADDED Requirements

### Requirement: Demos module exposes an alternate LWJGL entrypoint

O módulo `:games:demos` SHALL prover um segundo `main` Kotlin function chamado `MainLwjgl.kt` (file-level `fun main()`), localizado em `games/demos/src/main/kotlin/com/neoutils/games/demos/MainLwjgl.kt`. Esse entrypoint MUST construir a **mesma** árvore raiz que o `Main.kt` padrão (`DemoSwitcherRoot()`), com a **mesma** `GameConfig` (mesmos `title`, `width`, `height`, mesmos toggle keys), e MUST entregá-la a um `LwjglHost()` instanciado em vez do `SkikoHost()`. O `Main.kt` padrão (Skiko) MUST permanecer inalterado funcionalmente nesta change — `./gradlew :games:demos:run` continua sendo o entrypoint default rodando Skiko.

O módulo SHALL adicionar `implementation(projects.engineLwjgl)` em `games/demos/build.gradle.kts` (sem remover `implementation(projects.engineSkiko)`).

#### Scenario: MainLwjgl.kt exists and instantiates LwjglHost

- **WHEN** o source `games/demos/src/main/kotlin/com/neoutils/games/demos/MainLwjgl.kt` é inspecionado
- **THEN** ele declara `fun main()` no top level
- **AND** o corpo da função instancia `LwjglHost()` (importado de `com.neoutils.engine.lwjgl.LwjglHost` em `:engine-lwjgl`)
- **AND** chama `host.run(SceneTree(root = DemoSwitcherRoot()), GameConfig(title = "engine-consistency demos", width = 800, height = 600))` (mesmos parâmetros do `Main.kt` Skiko)

#### Scenario: Main.kt remains the default Skiko entrypoint

- **WHEN** o source `games/demos/src/main/kotlin/com/neoutils/games/demos/Main.kt` é inspecionado após esta change
- **THEN** ele permanece instanciando `SkikoHost()` (sem alteração funcional vs. estado anterior)
- **AND** `application { mainClass.set(...) }` em `games/demos/build.gradle.kts` aponta para `com.neoutils.games.demos.MainKt`

#### Scenario: Both backend modules are on the classpath

- **WHEN** `./gradlew :games:demos:dependencies` é inspecionado
- **THEN** tanto `:engine-skiko` quanto `:engine-lwjgl` aparecem como dependências de implementação

### Requirement: runLwjgl Gradle task launches Demos on the LWJGL backend with macOS thread flag

O módulo `:games:demos/build.gradle.kts` SHALL registrar uma task `runLwjgl` do tipo `JavaExec` no grupo `application`, com descrição "Runs :games:demos using the LWJGL backend". A task MUST setar `mainClass.set("com.neoutils.games.demos.MainLwjglKt")` e `classpath = sourceSets["main"].runtimeClasspath`. Quando rodando em macOS (`OperatingSystem.current().isMacOsX`), a task MUST injetar `jvmArgs("-XstartOnFirstThread")` para satisfazer a constraint de Cocoa/GLFW; em Linux/Windows essa flag MUST NÃO ser adicionada.

A task `run` default do plugin `application` MUST permanecer apontando para `com.neoutils.games.demos.MainKt` (Skiko) sem `-XstartOnFirstThread` (Skiko não exige).

#### Scenario: runLwjgl is registered under the application group

- **WHEN** `./gradlew :games:demos:tasks --all` é inspecionado
- **THEN** a task `runLwjgl` aparece sob `Application tasks`
- **AND** sua descrição é "Runs :games:demos using the LWJGL backend"

#### Scenario: runLwjgl injects -XstartOnFirstThread on macOS

- **GIVEN** o build é executado em macOS
- **WHEN** `./gradlew :games:demos:runLwjgl --dry-run` é inspecionado (ou os jvmArgs da task são lidos via reflection)
- **THEN** `-XstartOnFirstThread` está presente em `jvmArgs`

#### Scenario: runLwjgl does not inject -XstartOnFirstThread on Linux/Windows

- **GIVEN** o build é executado em Linux ou Windows
- **WHEN** os jvmArgs da task `runLwjgl` são inspecionados
- **THEN** `-XstartOnFirstThread` NÃO está presente

#### Scenario: Default run task still drives Skiko without the macOS thread flag

- **WHEN** `./gradlew :games:demos:run` é executado em macOS
- **THEN** o processo iniciado NÃO usa `-XstartOnFirstThread`
- **AND** a janela aberta é a janela Skiko/JFrame (não a janela GLFW)

### Requirement: Demos scenes 1–6 behave identically (semantically) on both backends

As cenas `1` Solar System, `2` Scale hierarchy, `3` Spawner, `4` Collision stress, `5` Rotating box, `6` Tumbling swarm SHALL produzir comportamento de gameplay semanticamente idêntico em ambos os entrypoints (`:games:demos:run` e `:games:demos:runLwjgl`). "Semanticamente idêntico" MUST ser interpretado como: mesmas key-bindings (`1`–`6` trocam de cena; `F1`/`F2`/`F3` togglam overlays), mesma resposta a input (clique do mouse no Spawner adiciona bolinhas na posição esperada; arena boundaries reagem a resize), mesma evolução de física (mesmas trajetórias dado mesmo `physicsHz`), mesmas árvores de Nodes (cenas compartilham o mesmo código `DemoSwitcherRoot`).

Diferenças puramente visuais (anti-aliasing edge-expand do NanoVG vs Skia GPU AA; fontstash vs Skia text shaping; sub-pixel positioning) MUST ser aceitas dentro de tolerância — não constituem regressão. Qualquer divergência semântica (cena rodando errado, F-key não togglando, mouse fora de posição, arena não acompanhando resize) MUST ser tratada como bug do backend e investigada antes do merge.

#### Scenario: Switching scenes works identically on both backends

- **WHEN** o usuário pressiona `1` … `6` em qualquer dos dois entrypoints
- **THEN** a cena correspondente é exibida em ambos
- **AND** o conjunto de cenas disponíveis é o mesmo

#### Scenario: F1/F2/F3 toggles apply identically on both backends

- **WHEN** o usuário pressiona `F1`, `F2` ou `F3` em qualquer dos dois entrypoints
- **THEN** `Debug.showFps`, `Debug.colliderVisualization`, `Debug.showMomentumOverlay` togglam respectivamente
- **AND** o overlay correspondente aparece/desaparece via `renderDebugOverlay`

#### Scenario: Spawner mouse click adds a ball at the click position on both backends

- **GIVEN** a cena `3` Spawner está ativa
- **WHEN** o usuário clica com o botão esquerdo em `(x, y)` em pixels da janela
- **THEN** uma nova bolinha aparece com `position ≈ (x, y)` em ambos os backends
- **AND** o trap central remove a bolinha quando ela entra via `onAreaEntered`

#### Scenario: BoundaryWalls follow window resize on both backends

- **GIVEN** as cenas `4` Collision stress, `5` Rotating box ou `6` Tumbling swarm estão ativas
- **WHEN** o usuário redimensiona a janela
- **THEN** as 4 paredes (`topWall`/`bottomWall`/`leftWall`/`rightWall`) acompanham o novo `tree.size` no próximo `onPhysicsProcess` em ambos os backends
