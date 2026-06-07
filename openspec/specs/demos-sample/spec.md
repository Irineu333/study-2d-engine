# demos-sample Specification

## Purpose

Módulo executável `:games:demos` que hospeda múltiplos exercícios de consistência da engine (Transform orbit, Scale hierarchy, Spawner, Collision stress, Rotating box, Tumbling swarm) num único processo Skiko com troca por teclado. Serve como prova viva dos invariantes da engine: composição de transform por ancestrais, mutação durante traversal, kinematic CCD via `moveAndCollide`, parent frame rotativo e resposta de impulso angular.

## Requirements

### Requirement: Demos module exists as an executable Skiko sample

O projeto SHALL prover um módulo `:games:demos` que depende de `:engine` e `:engine-skiko`, hospeda múltiplos exercícios de consistência da engine (Transform orbit, Scale hierarchy, Spawner, Collision stress, Rotating box, Tumbling swarm), e é executável via `./gradlew :games:demos:run`. O `Main.kt` MUST instanciar `SkikoHost().run(SceneTree(root = DemoSwitcherRoot()), GameConfig(...))` com `title = "engine-consistency demos"`, `width = 800`, `height = 600`. Os demos MUST rodar em coordenadas de pixel da surface (sem `Camera2D`) por design — o "mundo" do demo coincide com o retângulo da janela, e elementos sensíveis a `tree.size` MUST reagir explicitamente a mudanças dessa dimensão.

#### Scenario: Demos module runs from Gradle

- **WHEN** um desenvolvedor executa `./gradlew :games:demos:run` da raiz do projeto
- **THEN** uma janela desktop Skiko abre com o título `"engine-consistency demos"` exibindo o demo inicial (`1. Transform orbit`)
- **AND** a janela permanece responsiva e teclas `1`-`6` trocam de demo

#### Scenario: No Camera2D in any demo

- **WHEN** os sources de `:games:demos` são inspecionados
- **THEN** nenhum arquivo importa nem instancia `com.neoutils.engine.scene.Camera2D`

### Requirement: BoundaryWalls keeps the four-wall perimeter aligned with tree.size

O módulo `:games:demos` SHALL prover uma classe `BoundaryWalls : Node2D` que, ao entrar na árvore, cria 4 `StaticBody2D` filhos nomeados `topWall`, `bottomWall`, `leftWall`, `rightWall`, cada um com um único `CollisionShape2D` carregando um `RectangleShape2D`. `BoundaryWalls` MUST manter o perímetro alinhado ao retângulo `(0, 0)..(tree.width, tree.height)` em tempo real: enquanto `tree.size` muda (por resize da janela), as 4 paredes MUST ser repositionadas e redimensionadas no mesmo frame de física em que o resize é percebido.

`BoundaryWalls` MUST ser usada como **arena container**: atores físicos (e.g. bolinhas, quadrados) que devem colidir com as paredes MUST ser adicionados como filhos diretos da própria instância `BoundaryWalls`, não como siblings dela no parent do demo. Esse padrão respeita a restrição estrutural de `CharacterBody2D.moveAndCollide`, que só considera bodies-alvo cujo `parent` coincide com o `parent` do corpo se movendo — atores e paredes precisam compartilhar o mesmo parent frame (a própria `BoundaryWalls`) para o sweep encontrá-los.

O construtor SHALL aceitar `thickness: Float = 10f` controlando a espessura das paredes. As paredes ocupam posições EXATAMENTE assim:

- `topWall`: `position = (-thickness, -thickness)`, `size = (tree.width + 2*thickness, thickness)`
- `bottomWall`: `position = (-thickness, tree.height)`, `size = (tree.width + 2*thickness, thickness)`
- `leftWall`: `position = (-thickness, 0)`, `size = (thickness, tree.height)`
- `rightWall`: `position = (tree.width, 0)`, `size = (thickness, tree.height)`

A atualização MUST acontecer em `onPhysicsProcess` (não em `onProcess`) para que o sweep da física do frame em curso já veja a geometria atualizada. A implementação MUST guardar `lastSize: Vec2` (`@Transient`) e fazer early-return quando `tree.size == lastSize`. A classe MUST ser anotada `@Serializable`.

#### Scenario: BoundaryWalls creates four named static bodies

- **WHEN** uma instância de `BoundaryWalls` é adicionada como filha de um nó já anexado à árvore
- **THEN** após o primeiro `onPhysicsProcess` ela tem exatamente 4 filhos do tipo `StaticBody2D`, nomeados `topWall`, `bottomWall`, `leftWall`, `rightWall`
- **AND** cada um tem exatamente um filho `CollisionShape2D` cujo `shape` é um `RectangleShape2D`

#### Scenario: BoundaryWalls repositions when window grows

- **GIVEN** `BoundaryWalls()` está ativo numa árvore com `tree.size = Vec2(800, 600)`
- **WHEN** o host chama `tree.resize(1200, 900)` e o próximo `onPhysicsProcess` roda
- **THEN** `topWall.transform.position` é `Vec2(-10, -10)` e seu shape tem `size = Vec2(1220, 10)`
- **AND** `bottomWall.transform.position` é `Vec2(-10, 900)`
- **AND** `rightWall.transform.position` é `Vec2(1200, 0)` e seu shape tem `size = Vec2(10, 900)`
- **AND** `leftWall.transform.position` é `Vec2(-10, 0)` e seu shape tem `size = Vec2(10, 900)`

#### Scenario: BoundaryWalls skips work when tree.size is unchanged

- **GIVEN** `BoundaryWalls()` está ativo e já fez relayout para `tree.size = Vec2(800, 600)`
- **WHEN** múltiplos frames de `onPhysicsProcess` rodam sem que `tree.size` mude
- **THEN** o relayout NÃO é executado novamente (early-return preserva os transforms e shapes existentes)

#### Scenario: BoundaryWalls is annotated @Serializable

- **WHEN** o source de `BoundaryWalls.kt` é inspecionado
- **THEN** a classe é anotada com `@kotlinx.serialization.Serializable`
- **AND** `thickness` é declarado como parâmetro `val` do construtor primário (não `var`) — não requer `@Inspect`/`@Transient`
- **AND** `lastSize` é declarado como `@Transient var` (estado runtime puro)

### Requirement: makeStaticWall is the canonical way to build a static wall in demos

O módulo `:games:demos` SHALL prover uma função top-level `internal fun makeStaticWall(position: Vec2, size: Vec2): StaticBody2D` (mesma file que `BoundaryWalls`). A função MUST retornar uma `StaticBody2D` com `transform = Transform(position = position)` contendo um único filho `CollisionShape2D` com `shape = RectangleShape2D(size = size)`. Demos que precisam de paredes em frame local (não auto-resize) MUST usar `makeStaticWall` em vez de declarar uma `private fun makeWall` própria.

#### Scenario: makeStaticWall builds a StaticBody2D with one rectangle collider

- **WHEN** `makeStaticWall(Vec2(10f, 20f), Vec2(100f, 5f))` é chamado
- **THEN** retorna um `StaticBody2D` com `transform.position == Vec2(10f, 20f)`
- **AND** o body tem exatamente um filho do tipo `CollisionShape2D`
- **AND** esse `CollisionShape2D` tem `shape` do tipo `RectangleShape2D` com `size == Vec2(100f, 5f)`

#### Scenario: Demos do not define their own private makeWall

- **WHEN** os sources de `CollisionStressDemo.kt`, `RotatingBoxDemo.kt` e `TumblingSwarmDemo.kt` são inspecionados
- **THEN** nenhum deles declara `private fun makeWall(...)` (nem qualquer outra função privada equivalente que construa uma `StaticBody2D` com `CollisionShape2D + RectangleShape2D`)

### Requirement: CollisionStressDemo and TumblingSwarmDemo use BoundaryWalls

`CollisionStressDemo` (demo 4) e `TumblingSwarmDemo` (demo 6) SHALL adicionar uma única instância de `BoundaryWalls` em `onEnter` no lugar de criar 4 paredes manualmente, e SHALL adicionar seus atores físicos (bolinhas/quadrados) como filhos diretos dessa instância — não como siblings dela no `onEnter` do demo. As bolinhas/quadrados de cada demo continuam batendo nas paredes via `moveAndCollide`, e o demo MUST funcionar corretamente quando a janela é redimensionada — bolinhas batem nas paredes nas novas posições no mesmo frame em que o resize é percebido.

#### Scenario: CollisionStressDemo wires BoundaryWalls as arena

- **WHEN** o source de `CollisionStressDemo.kt` é inspecionado
- **THEN** `onEnter` instancia exatamente uma `BoundaryWalls` e a adiciona como filha do demo
- **AND** as 30 `Ball`s são adicionadas como filhas dessa instância de `BoundaryWalls` (e.g. `arena.addChild(Ball(...))`), não como filhas diretas do demo
- **AND** `onEnter` NÃO contém 4 chamadas separadas criando paredes manualmente

#### Scenario: TumblingSwarmDemo wires BoundaryWalls as arena

- **WHEN** o source de `TumblingSwarmDemo.kt` é inspecionado
- **THEN** `onEnter` instancia exatamente uma `BoundaryWalls` e a adiciona como filha do demo
- **AND** os 16 `TumblingSquare`s são adicionados como filhos dessa instância de `BoundaryWalls`, não como filhas diretas do demo
- **AND** `onEnter` NÃO contém 4 chamadas separadas criando paredes manualmente

#### Scenario: Balls in demo 4 stay inside the resized window

- **GIVEN** demo `4 Collision stress` está ativo e a janela está em `800x600`
- **WHEN** o usuário redimensiona a janela para `1200x900`
- **THEN** em poucos frames, bolinhas que cruzam o antigo perímetro batem na nova parede correspondente e quicam para dentro do novo retângulo
- **AND** nenhuma bolinha desaparece ou fica presa numa barreira invisível dentro da nova área visível

#### Scenario: Squares in demo 6 stay inside the resized window

- **GIVEN** demo `6 Tumbling swarm` está ativo e a janela está em `800x600`
- **WHEN** o usuário redimensiona a janela para `1200x900`
- **THEN** em poucos frames, quadrados que cruzam o antigo perímetro batem na nova parede correspondente e quicam para dentro do novo retângulo

### Requirement: RotatingBoxDemo uses makeStaticWall for its local-frame walls

`RotatingBoxDemo` (demo 5) SHALL chamar `makeStaticWall(position, size)` no lugar do antigo `private fun makeWall(position, size)` para construir suas 4 paredes em frame local do wrapper `RotatingBox`. As paredes MUST permanecer em frame local (filhas do wrapper rotativo) — elas NÃO devem ser substituídas por `BoundaryWalls`, porque devem girar com o wrapper, não acompanhar `tree.size`.

#### Scenario: RotatingBoxDemo uses the shared helper

- **WHEN** o source de `RotatingBoxDemo.kt` é inspecionado
- **THEN** `onEnter` chama `makeStaticWall(...)` 4 vezes (top/bottom/left/right)
- **AND** as 4 paredes são adicionadas como filhas do `RotatingBox` wrapper (não como filhas diretas do demo)
- **AND** `RotatingBoxDemo.kt` NÃO declara `private fun makeWall(...)`

#### Scenario: RotatingBoxDemo behavior is unchanged

- **GIVEN** demo `5 Rotating box` está ativo
- **WHEN** o usuário observa o comportamento por alguns segundos
- **THEN** o wrapper continua rotacionando e quicando dentro de `tree.size` (envelope AABB)
- **AND** as 4 paredes locais continuam girando solidárias ao wrapper
- **AND** as 12 bolinhas continuam quicando dentro da caixa rotativa

### Requirement: Documentation reflects resize-aware behavior

`CLAUDE.md` SHALL incluir, nas descrições dos demos `4 Collision stress` e `6 Tumbling swarm` na seção "Para rodar Demos", uma nota explícita de que as paredes acompanham `tree.size` em tempo real durante resize da janela.

#### Scenario: CLAUDE.md mentions resize-aware walls for demo 4

- **WHEN** `CLAUDE.md` é inspecionado
- **THEN** a descrição do demo `4 Collision stress` menciona que as paredes acompanham `tree.size` (resize-aware) via `BoundaryWalls`

#### Scenario: CLAUDE.md mentions resize-aware walls for demo 6

- **WHEN** `CLAUDE.md` é inspecionado
- **THEN** a descrição do demo `6 Tumbling swarm` menciona que as paredes acompanham `tree.size` (resize-aware) via `BoundaryWalls`

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

As cenas `1` Solar System, `2` Scale hierarchy, `3` Spawner, `4` Collision stress, `5` Rotating box, `6` Tumbling swarm SHALL produzir comportamento de gameplay semanticamente idêntico em ambos os entrypoints (`:games:demos:run` e `:games:demos:runLwjgl`). "Semanticamente idêntico" MUST ser interpretado como: mesmas key-bindings (`1`–`6` trocam de cena; `F1`/`F2`/`F3` togglam overlays via `tree.debug.showFps`/`tree.debug.showColliders`/`tree.debug.showMomentum`), mesma resposta a input (clique do mouse no Spawner adiciona bolinhas na posição esperada; arena boundaries reagem a resize), mesma evolução de física (mesmas trajetórias dado mesmo `physicsHz`), mesmas árvores de Nodes (cenas compartilham o mesmo código `DemoSwitcherRoot`).

Diferenças puramente visuais (anti-aliasing edge-expand do NanoVG vs Skia GPU AA; fontstash vs Skia text shaping; sub-pixel positioning) MUST ser aceitas dentro de tolerância — não constituem regressão. Qualquer divergência semântica (cena rodando errado, F-key não togglando, mouse fora de posição, arena não acompanhando resize) MUST ser tratada como bug do backend e investigada antes do merge.

#### Scenario: Switching scenes works identically on both backends

- **WHEN** o usuário pressiona `1` … `6` em qualquer dos dois entrypoints
- **THEN** a cena correspondente é exibida em ambos
- **AND** o conjunto de cenas disponíveis é o mesmo

#### Scenario: F1/F2/F3 toggles apply identically on both backends

- **WHEN** o usuário pressiona `F1`, `F2` ou `F3` em qualquer dos dois entrypoints
- **THEN** `tree.debug.showFps`, `tree.debug.showColliders`, `tree.debug.showMomentum` togglam respectivamente
- **AND** o overlay correspondente aparece/desaparece via os widgets do `DebugOverlayLayer` auto-inserido pela engine (não via helper do host)

#### Scenario: Spawner mouse click adds a ball at the click position on both backends

- **GIVEN** a cena `3` Spawner está ativa
- **WHEN** o usuário clica com o botão esquerdo em `(x, y)` em pixels da janela (e nenhum `Button` da UI está sob o ponteiro)
- **THEN** uma nova bolinha aparece com `position ≈ (x, y)` em ambos os backends
- **AND** o trap central remove a bolinha quando ela entra via `onAreaEntered`

#### Scenario: BoundaryWalls follow window resize on both backends

- **GIVEN** as cenas `4` Collision stress, `5` Rotating box ou `6` Tumbling swarm estão ativas
- **WHEN** o usuário redimensiona a janela
- **THEN** as 4 paredes (`topWall`/`bottomWall`/`leftWall`/`rightWall`) acompanham o novo `tree.size` no próximo `onPhysicsProcess` em ambos os backends

### Requirement: Each demo scene has a documented role exercising specific invariants

A spec `demos-sample` SHALL incluir uma descrição por cena (`1`–`6`) explicando o que ela exercita do ponto de vista da engine — quais invariantes valida, qual sistema põe sob carga, qual diagnóstico visual oferece. Essa documentação MUST viver na spec (não em `CLAUDE.md` nem em `README.md`), de modo que o `README.md` possa fazer apenas o resumo de uma linha por cena e o `CLAUDE.md` possa permanecer livre de descrição cena-a-cena.

As descrições MUST cobrir, no mínimo:

- **Cena `1` Solar system**: Sol amarelo no centro com 8 planetas (Mercúrio→Netuno) e luas conhecidas (Lua na Terra; Io, Europa, Ganimedes, Calisto em Júpiter; Titã em Saturno; Tritão em Netuno) orbitando seus pais. Saturno carrega um `SaturnRing` (anel achatado via scale não-uniforme). Exercita o invariante de composição aninhada de transform (`Transform composition by ancestry` em `engine-core`) em até 4 níveis (Sol → órbita-planeta → planeta → órbita-lua → lua), validando que `world()` cacheia corretamente sob mutação simultânea de múltiplos ancestrais por frame.
- **Cena `2` Scale hierarchy**: Pai com `scale` oscilando faz o filho crescer e encolher. Exercita composição de scale via `Shape.onRender` ao longo da cadeia de ancestrais.
- **Cena `3` Spawner**: Clique do mouse adiciona bolinhas durante `onUpdate`; um trap central (`Area2D`) remove durante `onAreaEntered`. Exercita mutação durante traversal (`Safe mutation during scene traversal` em `engine-core`). `F2` mostra que o overlay de colliders sai do `GameHost` e usa cores distintas para `Area2D` vs `PhysicsBody2D`.
- **Cena `4` Collision stress**: 30 `RigidBody2D` bolinhas (`restitution=1f`, `friction=0f`) dentro de uma arena `BoundaryWalls` (4 `StaticBody2D` que acompanham `tree.size` no resize). O engine solver integra cada bolinha (sem `moveAndCollide` no script), sweep com TOI loop, e aplica impulso bilateral (linear + angular) em cada contato — bola pesada empurra bola leve (transferência de momento), sem tunneling estrutural mesmo em alta velocidade. `F2` mostra os AABBs das `CollisionShape2D` (vermelho para Bodies). `F3` mostra `Σp`, `ΣL`, `ΣKE` com sparklines: KE permanece constante (elástico).
- **Cena `5` Rotating box**: 12 `CharacterBody2D` bolinhas vivem como filhas de um `Node2D` "caixa" que rotaciona **e** translada a cada frame (envelope AABB quicando nas paredes da scene). 4 `StaticBody2D` paredes são filhas do mesmo wrapper rotativo, em coordenadas locais. `moveAndCollide` opera no parent frame compartilhado (= local da caixa), de modo que o sweep continua axis-aligned mesmo com a caixa girando em world — bolinhas batem corretamente em paredes e em siblings sem tunelar. Exercita o invariante de invalidação por mutação de ancestral sob carga real de colisão e em frame rotativo não-estacionário. `F2` mostra os AABBs envelopados dos `CollisionShape2D` rotacionados em world.
- **Cena `6` Tumbling swarm**: 16 quadrados `RigidBody2D` (`restitution=1f`, `friction=0.4f`) com velocidade linear e angular iniciais, dentro de `BoundaryWalls` (paredes acompanham `tree.size` no resize). O engine solver resolve cada contato pelo caminho rotated do sweep (`sweepRotatedRectRotatedRect`) com leading-corner contact point, impulso normal + Coulomb tangencial — squares quicam elasticamente contra paredes e entre si, com spin perceptível em hits glancing. `F2` mostra os OBBs rotacionados envelope. `F3` mostra `ΣL` (angular momentum) conservado em hits elásticos frictionless e drift sob fricção.

#### Scenario: Spec describes all six scenes

- **WHEN** `openspec/specs/demos-sample/spec.md` é aberto
- **THEN** existe uma seção (Requirement) cobrindo as cenas `1` Solar system, `2` Scale hierarchy, `3` Spawner, `4` Collision stress, `5` Rotating box, `6` Tumbling swarm
- **AND** cada cena tem ao menos um parágrafo descrevendo o invariante ou sistema que exercita
- **AND** o conteúdo dessa Requirement não duplica detalhe de implementação (esses ficam nas specs `engine-core`, `rigid-body-2d`, `kinematic-move-and-collide`)

#### Scenario: README.md can summarize without losing detail

- **WHEN** o `README.md` resume a cena em uma única linha
- **THEN** o leitor que quer o detalhe completo (invariantes exercitados, parâmetros de física, what F-keys mostram) encontra o material em `openspec/specs/demos-sample/spec.md`
- **AND** o `CLAUDE.md` pode permanecer livre de descrição cena-a-cena

### Requirement: Demos scene 7 validates ui-foundation in both backends

The `:games:demos` module SHALL include a scene `7` "UI playground" accessible via the same `DemoSwitcherRoot` keybinding scheme as scenes `1`–`6` (pressing `7` switches to it). Scene 7 SHALL contain at minimum:

- Two `CanvasLayer` children of the demo root, with different `layer` values (e.g. `layer = 0` for the HUD layer, `layer = 10` for the menu layer), proving the z-order requirement.
- In the **menu layer** (top-most): three `Button` instances centered on the screen labeled "Start", "Settings", "Quit", each connected to `pressed` and printing a known string via `Log.i` (or equivalent observable mechanism). The buttons SHALL exercise all four color states (`normalColor`, `hoverColor`, `pressedColor`, `disabledColor`); at least one of the three SHALL be `disabled = true` at startup to validate the disabled visual.
- In the **HUD layer** (below the menu): a `Panel` and two `Label`s rendering `"Score: 0"` and `"Lives: 3"` at bottom-left, proving HUDs do not zoom with `Camera2D`.
- A background world (e.g. a single `ColorRect` filling the canvas) — its sole purpose is to make screen-space UI visibly distinct from world-space content.

Because the MVP does not ship anchors (deferred to `ui-anchors`), `UiPlaygroundDemo` SHALL recompute HUD and menu positions in `onProcess` reading `tree.size`, keeping the HUD pinned to bottom-left and the menu horizontally centered when the user resizes the window.

Scene 7 SHALL behave semantically identically in both `:games:demos:run` (Skiko) and `:games:demos:runLwjgl` (LWJGL) entrypoints: same buttons in same positions, same hover/press visuals, same `pressed` signal emission, same HUD layout. Purely visual differences (AA, text rendering) SHALL be accepted within tolerance.

#### Scenario: Pressing 7 switches to the UI playground

- **WHEN** the user presses `7` on either entrypoint
- **THEN** the displayed scene contains the menu (3 buttons centered) and the HUD (Score/Lives at bottom-left)

#### Scenario: Clicking Start emits pressed signal

- **WHEN** the user clicks the "Start" button rect on either entrypoint
- **THEN** the attached handler runs exactly once
- **AND** the click is consumed (any gameplay script checking `tree.input.wasMouseClicked(Left)` sees `false`)

#### Scenario: Disabled button does not respond

- **WHEN** one button is `disabled = true` at startup (e.g. "Settings") and the user clicks its rect
- **THEN** the button renders with `disabledColor`
- **AND** `pressed` does NOT emit
- **AND** the click is NOT consumed (passes through to the world / any other UI below)

#### Scenario: HUD layer remains in screen position when window resized

- **WHEN** the user drags the window border to resize the surface
- **THEN** the "Score: 0" and "Lives: 3" labels remain at the bottom-left corner with constant pixel offsets
- **AND** the buttons remain centered horizontally relative to the new surface width

#### Scenario: Menu layer renders on top of HUD layer

- **WHEN** the menu layer (`layer = 10`) Button overlaps the HUD layer (`layer = 0`) Panel at some screen position
- **THEN** the menu Button is visible (drawn on top)
- **AND** clicking that overlap region triggers the menu Button's `pressed`, not the HUD

#### Scenario: Scene 7 runs in both backends

- **WHEN** the user opens scene 7 via `./gradlew :games:demos:run` and then via `./gradlew :games:demos:runLwjgl`
- **THEN** the menu, HUD, button states, and signal emissions behave identically (modulo backend-specific AA/text rendering differences)

### Requirement: Demos includes a static Sprite2D scene as the texture-rendering sentinel

The `:games:demos` module SHALL include a scene that displays at least one static `Sprite2D` loading a PNG asset from `games/demos/src/main/resources/`. This scene is the living sentinel for the `texture-rendering` capability: it MUST render the same texture **identically (semantically)** on both backends — Skiko (default entrypoint) and LWJGL (`runLwjgl` task) — proving `Renderer.drawImage` + `tree.textures` work end-to-end before any animation or tilemap is built on top. The sprite MUST be sampled with nearest-neighbor (crisp pixel-art when scaled by camera/zoom).

#### Scenario: Sprite scene renders on the Skiko backend

- **WHEN** the demos app runs on Skiko and the sprite scene is shown
- **THEN** the PNG appears on screen, centered at its node position, with crisp (non-blurred) pixels when scaled

#### Scenario: Sprite scene renders identically on the LWJGL backend

- **WHEN** the demos app runs via the `runLwjgl` task and the sprite scene is shown
- **THEN** the same PNG appears in the same place with the same nearest-neighbor crispness as on Skiko

### Requirement: Demos includes an AnimatedSprite2D scene as the animation sentinel

The `:games:demos` module SHALL include a scene that displays at least one `AnimatedSprite2D` cycling a real multi-frame sheet from `games/demos/src/main/resources/` (e.g. a 17-frame fruit or the 12-frame Run sheet). This scene is the living sentinel for the `sprite-animation` capability: it MUST visibly advance frames over time and render **identically (semantically)** on both backends — Skiko (default) and LWJGL (`runLwjgl`).

#### Scenario: Animation advances over time on Skiko

- **WHEN** the demos app runs on Skiko and the animation scene is shown
- **THEN** the sprite cycles through its frames over time (not a frozen frame)

#### Scenario: Animation renders identically on LWJGL

- **WHEN** the demos app runs via `runLwjgl` and the animation scene is shown
- **THEN** the sprite cycles through the same frames at the same rate as on Skiko
