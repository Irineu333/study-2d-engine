# demos-sample Specification

## Purpose

Módulo executável `:games:demos` que hospeda um conjunto de **5 demos** de consistência da engine (`Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`) num único processo Skiko, navegadas por um menu de UI. Serve como prova viva dos invariantes da engine: composição de transform por ancestrais (com zoom/pan de `Camera2D` na demo `Transforms`), mutação durante traversal, kinematic CCD via `moveAndCollide`, parent frame rotativo, resposta de impulso angular e render de textura (sprite/tilemap) cross-backend.

## Requirements

### Requirement: Demos module exists as an executable Skiko sample

O projeto SHALL prover um módulo `:games:demos` que depende de `:engine` e `:engine-skiko`, hospeda um conjunto de **5 demos** de consistência da engine (`Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`) num único processo, e é executável via `./gradlew :games:demos:run`. O `Main.kt` MUST instanciar `SkikoHost().run(SceneTree(root = DemoSwitcherRoot()), GameConfig(...))` com `title = "engine-consistency demos"`, `width = 800`, `height = 600`.

A navegação entre demos MUST ser feita por um **menu de UI** (não por teclas `1`–`0`): a raiz exibe um menu com um `Button` por demo; selecionar um botão carrega a demo; cada demo exibe um `Button` de voltar (uma seta ←, fundo transparente) que retorna ao menu. As demos rodam em coordenadas de pixel da surface por padrão, **exceto** a demo `Transforms`, que instala deliberadamente uma `Camera2D` local à cena (a antiga convenção "nenhuma demo usa `Camera2D`" foi removida). Elementos sensíveis a `tree.size` MUST reagir explicitamente a mudanças dessa dimensão.

#### Scenario: Demos module runs from Gradle

- **WHEN** um desenvolvedor executa `./gradlew :games:demos:run` da raiz do projeto
- **THEN** uma janela desktop Skiko abre com o título `"engine-consistency demos"` exibindo o menu de demos
- **AND** a janela permanece responsiva e clicar num botão do menu carrega a demo correspondente

#### Scenario: Camera2D is used only by the Transforms demo

- **WHEN** os sources de `:games:demos` são inspecionados
- **THEN** apenas a cena `Transforms` (solar system) importa/instancia `com.neoutils.engine.scene.Camera2D`
- **AND** nenhuma outra demo instancia `Camera2D`

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

- **WHEN** os sources das demos que constroem paredes (`Rotating Frame` e qualquer demo que use arena própria) são inspecionados
- **THEN** nenhum deles declara `private fun makeWall(...)` (nem qualquer outra função privada equivalente que construa uma `StaticBody2D` com `CollisionShape2D + RectangleShape2D`) — todos recorrem a `makeStaticWall` ou `BoundaryWalls`

### Requirement: Spawn & Collide and Tumbling Swarm use BoundaryWalls

A demo `Spawn & Collide` (funde os antigos Spawner + Collision stress) e a demo `Tumbling Swarm` SHALL adicionar uma única instância de `BoundaryWalls` como arena container, e SHALL adicionar seus atores físicos (bolinhas/quadrados) como filhos diretos dessa instância — não como siblings dela no nó do demo. Os atores continuam batendo nas paredes via o solver/`moveAndCollide`, e cada demo MUST funcionar corretamente quando a janela é redimensionada — atores batem nas paredes nas novas posições no mesmo frame em que o resize é percebido.

#### Scenario: Spawn & Collide wires BoundaryWalls as arena

- **WHEN** o source da demo `Spawn & Collide` é inspecionado
- **THEN** ela instancia exatamente uma `BoundaryWalls` e a adiciona como filha do demo
- **AND** as bolinhas spawnadas são adicionadas como filhas dessa instância de `BoundaryWalls`, não como filhas diretas do demo
- **AND** o demo NÃO contém 4 chamadas separadas criando paredes manualmente

#### Scenario: Tumbling Swarm wires BoundaryWalls as arena

- **WHEN** o source da demo `Tumbling Swarm` é inspecionado
- **THEN** `onEnter` instancia exatamente uma `BoundaryWalls` e a adiciona como filha do demo
- **AND** os quadrados `RigidBody2D` são adicionados como filhos dessa instância de `BoundaryWalls`

#### Scenario: Actors stay inside the resized window

- **GIVEN** a demo `Spawn & Collide` ou `Tumbling Swarm` está ativa e a janela está em `800x600`
- **WHEN** o usuário redimensiona a janela para `1200x900`
- **THEN** em poucos frames, atores que cruzam o antigo perímetro batem na nova parede correspondente e quicam para dentro do novo retângulo
- **AND** nenhum ator desaparece ou fica preso numa barreira invisível dentro da nova área visível

### Requirement: RotatingBoxDemo uses makeStaticWall for its local-frame walls

A demo `Rotating Frame` SHALL chamar `makeStaticWall(position, size)` no lugar de um `private fun makeWall(position, size)` próprio para construir suas 4 paredes em frame local do wrapper rotativo. As paredes MUST permanecer em frame local (filhas do wrapper rotativo) — elas NÃO devem ser substituídas por `BoundaryWalls`, porque devem girar com o wrapper, não acompanhar `tree.size`.

#### Scenario: RotatingBoxDemo uses the shared helper

- **WHEN** o source da demo `Rotating Frame` é inspecionado
- **THEN** ele chama `makeStaticWall(...)` 4 vezes (top/bottom/left/right)
- **AND** as 4 paredes são adicionadas como filhas do wrapper rotativo (não como filhas diretas do demo)
- **AND** o source da demo `Rotating Frame` NÃO declara `private fun makeWall(...)`

#### Scenario: RotatingBoxDemo behavior is unchanged

- **GIVEN** a demo `Rotating Frame` está ativa
- **WHEN** o usuário observa o comportamento por alguns segundos
- **THEN** o wrapper continua rotacionando e quicando dentro de `tree.size` (envelope AABB)
- **AND** as 4 paredes locais continuam girando solidárias ao wrapper
- **AND** as bolinhas continuam quicando dentro da caixa rotativa

### Requirement: Documentation reflects the five-demo catalog and resize-aware behavior

A tabela "Games" do `CLAUDE.md` e a seção de demos do `README.md` SHALL refletir o catálogo de **5 demos** (`Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`) com navegação por menu. A documentação SHALL mencionar que as demos com arena (`Spawn & Collide`, `Tumbling Swarm`) têm paredes resize-aware via `BoundaryWalls`.

#### Scenario: README lists the five demos

- **WHEN** `README.md` é inspecionado
- **THEN** as 5 demos aparecem com um resumo de uma linha cada
- **AND** a navegação é descrita como menu de UI (não teclas `1`–`0`)

#### Scenario: CLAUDE.md Games table reflects the new catalog

- **WHEN** a tabela "Games" do `CLAUDE.md` é inspecionada
- **THEN** a linha de `:games:demos` descreve o novo conjunto de demos sem listar 10 cenas numeradas

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

### Requirement: Demos behave identically (semantically) on both backends

As cinco demos (`Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`) e o menu de navegação SHALL produzir comportamento de gameplay semanticamente idêntico em ambos os entrypoints (`:games:demos:run` Skiko e `:games:demos:runLwjgl` LWJGL). "Semanticamente idêntico" MUST ser interpretado como: mesmo menu e mesmas demos disponíveis, mesma resposta a input (clique do mouse no menu/botões e no Spawner na posição esperada; arena boundaries reagem a resize; câmera da demo Transforms responde a scroll/arrasto/teclas), mesma evolução de física dado o mesmo `physicsHz`, mesmas árvores de Nodes (compartilham o código `DemoSwitcherRoot`).

Diferenças puramente visuais (anti-aliasing, text shaping, sub-pixel positioning) MUST ser aceitas dentro de tolerância. Qualquer divergência semântica MUST ser tratada como bug do backend e investigada antes do merge.

#### Scenario: Menu navigation works identically on both backends

- **WHEN** o usuário clica num botão de demo no menu em qualquer dos dois entrypoints
- **THEN** a demo correspondente é carregada em ambos
- **AND** o botão de voltar retorna ao menu em ambos
- **AND** o conjunto de demos disponíveis é o mesmo

#### Scenario: Spawner mouse click adds a ball at the click position on both backends

- **GIVEN** a demo `Spawn & Collide` está ativa
- **WHEN** o usuário clica com o botão esquerdo em `(x, y)` em pixels da janela (e nenhum `Button` da UI está sob o ponteiro)
- **THEN** uma nova bolinha aparece com `position ≈ (x, y)` em ambos os backends
- **AND** o trap central remove a bolinha quando ela entra via `onBodyEntered`

#### Scenario: BoundaryWalls follow window resize on both backends

- **GIVEN** as demos `Spawn & Collide`, `Rotating Frame` ou `Tumbling Swarm` estão ativas
- **WHEN** o usuário redimensiona a janela
- **THEN** as 4 paredes acompanham o novo `tree.size` no próximo `onPhysicsProcess` em ambos os backends

### Requirement: Each demo scene has a documented role exercising specific invariants

A spec `demos-sample` SHALL incluir uma descrição por demo explicando o que ela exercita do ponto de vista da engine — quais invariantes valida, qual sistema põe sob carga, qual diagnóstico visual oferece. Essa documentação MUST viver na spec (não em `CLAUDE.md` nem em `README.md`). As descrições MUST cobrir, no mínimo:

- **`Transforms`** (ex-Solar system): Sol amarelo central com 8 planetas e luas conhecidas orbitando seus pais; Saturno carrega `SaturnRing`. Exercita composição de transform aninhada em até 4 níveis. Adiciona uma `Camera2D` com **zoom/pan interativo** (scroll/teclas) — primeira cobertura de `Camera2D` entre as demos. O zoom também exercita **escala-composição**: ampliar/reduzir escala toda a hierarquia aninhada em uníssono (ancestor scale → tamanho renderizado do filho, o invariante que a antiga demo Scale validava isoladamente). O HUD/overlay vive em `CanvasLayer` e NÃO sofre o zoom da câmera.
- **`Spawn & Collide`** (funde os antigos Spawner + Collision stress): clique/auto-spawn adiciona `RigidBody2D` bolinhas durante `onProcess`. Um trap central **interativo** ilustra a dicotomia sensor-vs-sólido do invariante #3: dois colliders irmãos na arena (`TrapSensor : Area2D` + `TrapWall : StaticBody2D`) alternam o flag `disabled` para trocar entre o modo `Despawn` (o sensor remove a bolinha no `onBodyEntered` — as bolinhas são bodies, então o evento é `onBodyEntered`, não `onAreaEntered`) e o modo `Collide` (o `StaticBody2D` faz as bolinhas quicarem). O trap é **arrastável** pela tela com clamp aos limites (resize re-clampa, não recentra), e o auto-spawn pode ser **desligado** — tudo controlado por um `SpawnCollideWidget : ScreenDebugWidget` que o demo **registra no `onEnter` e des-registra no `onExit`**, exercitando o contrato `register`/`unregister` de debug a partir de um `Node`. As bolinhas quicam elasticamente entre si e nas paredes de uma `BoundaryWalls`. Exercita mutação segura durante traversal, sensor `Area2D` vs corpo sólido `StaticBody2D`, solver `RigidBody2D`, cache de world-transform sob carga e o contrato de widget de debug customizado.
- **`Rotating Frame`** (antigo Rotating box): `CharacterBody2D` bolinhas vivem como filhas de um wrapper que rotaciona e translada a cada frame; 4 paredes `StaticBody2D` são filhas do mesmo wrapper, em coordenadas locais. `moveAndCollide` opera no parent frame compartilhado, mantendo o sweep axis-aligned mesmo com a caixa girando em world. Exercita invalidação por mutação de ancestral sob carga real, em frame rotativo.
- **`Tumbling Swarm`** (antigo Tumbling swarm): quadrados `RigidBody2D` (`restitution=1f`, `friction=0.4f`) com velocidade linear e angular, dentro de `BoundaryWalls`. O solver resolve cada contato pelo caminho rotated do sweep com impulso normal + Coulomb tangencial — spin perceptível em hits glancing.
- **`Sprites & Tiles`** (funde os antigos Animated + Tilemap): um `TileMap` monta um chão a partir de um atlas real; um `AnimatedSprite2D` "corre" sobre ele (avanço de frame engine-driven). O player é um `CharacterBody2D` movendo-se sobre o chão de `StaticBody2D` via `moveAndCollide`. Sentinela cross-backend (Skiko + LWJGL) de `texture-rendering` (o caminho `Renderer.drawImage` é exercido pelo `AnimatedSprite2D` e pelo `TileMap`), `sprite-animation` e `tilemap-visual` numa só tela. Não inclui um `Sprite2D` estático separado — a antiga demo Sprite isolada não recebe um decorador dedicado aqui (o `Sprite2D` é só um wrapper fino de `drawImage`, já coberto pelos outros dois nós).

#### Scenario: Spec describes all five demos

- **WHEN** `openspec/specs/demos-sample/spec.md` é aberto
- **THEN** existe uma seção (Requirement) cobrindo as demos `Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`
- **AND** cada demo tem ao menos um parágrafo descrevendo o invariante ou sistema que exercita
- **AND** o conteúdo dessa Requirement não duplica detalhe de implementação (esses ficam nas specs `engine-core`, `rigid-body-2d`, `kinematic-move-and-collide`)

#### Scenario: README.md can summarize without losing detail

- **WHEN** o `README.md` resume cada demo em uma única linha
- **THEN** o leitor que quer o detalhe completo encontra o material em `openspec/specs/demos-sample/spec.md`
- **AND** o `CLAUDE.md` pode permanecer livre de descrição demo-a-demo (apenas a tabela "Games" de uma linha)

### Requirement: Spawn & Collide trap is interactive with despawn/collide modes, drag, and a custom debug widget

A demo `Spawn & Collide` SHALL prover um trap central **interativo** que ilustra a dicotomia sensor-vs-sólido do invariante #3, é arrastável dentro dos limites da tela, e é controlado por um **widget de debug customizado registrado pelo próprio demo**.

**Modos do trap.** O trap SHALL operar em dois modos mutuamente exclusivos, `Despawn` (default) e `Collide`, implementados por **dois `CollisionObject2D` irmãos diretos da instância de `BoundaryWalls`** (mesmo retângulo, mesma posição):

- `TrapSensor : Area2D` — sensor que remove a bolinha `RigidBody2D` no `onBodyEntered`. Ativo quando o modo é `Despawn` (`disabled = (mode != Despawn)`).
- `TrapWall : StaticBody2D` — obstáculo sólido em que as bolinhas quicam via o solver. Ativo quando o modo é `Collide` (`disabled = (mode != Collide)`).

A troca de modo SHALL alternar o flag `disabled` de `CollisionObject2D` (sem add/remove de nó), de modo que nunca os dois colliders estejam ativos ao mesmo tempo. Os dois MUST ser filhos diretos da `BoundaryWalls` (não de um nó wrapper), porque o sweep do `RigidBody2D` só considera alvos cujo `parent` coincide com o `parent` das bolinhas.

**Drag com clamp.** O trap SHALL ser arrastável com o botão esquerdo: um press dentro do retângulo do trap (quando nenhum painel de debug consumiu o ponteiro) inicia um grab-and-drag que segue o ponteiro enquanto o botão é mantido. A posição do trap SHALL ser clampada a `[half, surface − half]` (com `half = SIZE/2`) em x e y, mantendo o trap inteiramente dentro da arena. Iniciar/manter o drag SHALL suprimir o spawn daquele clique (set `mouseClickConsumed`), de modo que arrastar o trap nunca cria uma bolinha. No resize, a posição do trap SHALL ser **re-clampada** à nova surface (não recentralizada), preservando o arrasto do usuário.

**Auto-spawn toggle.** O `Spawner` SHALL expor um gate booleano de auto-spawn; quando desligado, o drip automático para e somente o clique manual cria bolinhas.

**Widget de debug do demo.** A demo SHALL registrar um `SpawnCollideWidget : ScreenDebugWidget` via `tree.debug.register(...)` no `onEnter` e SHALL des-registrá-lo via `tree.debug.unregister(...)` no `onExit` (some do HUD ao voltar ao menu). O widget SHALL expor dois segmented controls — `Trap [Despawn | Collide]` e `Auto-spawn [On | Off]` — que leem e escrevem o estado compartilhado do demo, com o segmento ativo destacado (no espírito do `ColliderModePanel`). `:engine` SHALL permanecer sem conhecer essa classe (o widget vive em `:games:demos`).

#### Scenario: Trap exposes two sibling colliders in the arena

- **WHEN** o source da demo `Spawn & Collide` é inspecionado
- **THEN** existe um `TrapSensor : Area2D` e um `TrapWall : StaticBody2D`, ambos adicionados como filhos diretos da instância de `BoundaryWalls` (não de um nó wrapper intermediário)
- **AND** cada um carrega um `CollisionShape2D` com o mesmo retângulo, na mesma posição do trap

#### Scenario: Despawn mode removes balls; collide mode bounces them

- **GIVEN** a demo `Spawn & Collide` ativa com o trap em modo `Despawn`
- **WHEN** uma bolinha entra no retângulo do trap
- **THEN** o `TrapSensor` está ativo (`disabled = false`), o `TrapWall` está inativo (`disabled = true`), e a bolinha é removida no `onBodyEntered`
- **WHEN** o modo muda para `Collide`
- **THEN** o `TrapWall` passa a ativo e o `TrapSensor` a inativo, e bolinhas que atingem o trap quicam nele em vez de serem removidas

#### Scenario: Trap can be dragged within screen bounds and survives resize

- **GIVEN** a demo `Spawn & Collide` ativa
- **WHEN** o usuário pressiona o botão esquerdo dentro do retângulo do trap e arrasta
- **THEN** o trap segue o ponteiro, com a posição clampada a `[half, surface − half]` em ambos os eixos, e nenhuma bolinha é spawnada por esse clique
- **WHEN** a janela é redimensionada
- **THEN** a posição arrastada do trap é re-clampada à nova surface, não recentralizada

#### Scenario: Auto-spawn can be turned off

- **GIVEN** a demo `Spawn & Collide` ativa com auto-spawn ligado
- **WHEN** o auto-spawn é desligado pelo widget
- **THEN** o drip automático de bolinhas para
- **AND** o clique manual (fora do trap, sem UI sob o ponteiro) continua criando bolinhas

#### Scenario: SpawnCollideWidget is registered on enter and unregistered on exit

- **WHEN** a demo `Spawn & Collide` é carregada (`onEnter`)
- **THEN** um `SpawnCollideWidget : ScreenDebugWidget` é registrado via `tree.debug.register(...)` e aparece como uma linha no HUD com os segmented controls de modo do trap e de auto-spawn
- **WHEN** o usuário volta ao menu (a demo é removida, `onExit`)
- **THEN** o widget é des-registrado via `tree.debug.unregister(...)` e some de `tree.debug.widgets`/HUD
- **AND** `:engine` não referencia `SpawnCollideWidget` (a classe vive em `:games:demos`)

### Requirement: A navigation menu hosts the demos and validates ui-foundation

O módulo `:games:demos` SHALL prover um **menu de navegação** construído com primitivas de UI (`CanvasLayer` + `Button` + `Panel` + `Label`), exibido pela raiz `DemoSwitcherRoot` na inicialização. O menu MUST conter um `Button` por demo (5 botões), cada um conectado a `pressed` para carregar a demo correspondente via `addChild`/`removeChild`. Cada demo carregada MUST exibir um overlay `CanvasLayer` contendo um `Label` de título, um `Label` de descrição e um `Button` de voltar (uma seta ←) que retorna ao menu. Esse mecanismo absorve a antiga demo de UI dedicada — `Button` (estados normal/hover/press/disabled, signal `pressed`, hit-test, click-consumption), `Panel`, `Label`, anchors e z-order ficam exercitados continuamente em toda tela.

#### Scenario: Menu shows one button per demo

- **WHEN** a aplicação inicia
- **THEN** o menu exibe 5 botões, um por demo (`Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`)

#### Scenario: Clicking a demo button loads it and consumes the click

- **WHEN** o usuário clica num botão de demo
- **THEN** o menu é removido e a demo correspondente é adicionada à árvore
- **AND** o clique é consumido (`tree.input.wasMouseClicked(Left)` retorna `false` para nós de gameplay nesse tick)

#### Scenario: Back button returns to the menu

- **GIVEN** uma demo está carregada e exibe seu overlay com o `Button` de voltar (seta ←)
- **WHEN** o usuário clica na seta de voltar
- **THEN** a demo é removida e o menu volta a ser exibido

### Requirement: Per-demo title/description use CanvasLayer Labels, not raw drawText, and no per-demo FPS

As demos SHALL exibir título e descrição via `Label` dentro de um `CanvasLayer` (screen-space), não via `renderer.drawText` cru no `onDraw` do nó de gameplay. Nenhuma demo SHALL desenhar um contador de FPS próprio — o `ProfilerWidget` (acionado por `F1`) é a única fonte de FPS. Métricas auxiliares (contagem de corpos, contatos, velocidade) SHALL ser expostas via `tree.debug` (gizmos/profiler) quando necessárias, não via texto no canto.

#### Scenario: No demo draws its own FPS counter

- **WHEN** os sources das demos são inspecionados
- **THEN** nenhum `onDraw` de demo computa `1f / dt` para exibir FPS nem desenha string contendo `"fps"` via `drawText`

#### Scenario: Demo title is a Label in a CanvasLayer

- **WHEN** uma demo está carregada
- **THEN** seu título e descrição são renderizados por nós `Label` filhos de um `CanvasLayer`
- **AND** ao usar a câmera da demo `Transforms`, o título/descrição NÃO sofrem zoom (vivem em screen-space)

### Requirement: A single shared hue helper replaces duplicated copies

O módulo `:games:demos` SHALL prover uma única função/objeto helper de cor (`hue(h: Float): Color` ou equivalente) compartilhada pelas demos que coloram atores por índice. Nenhuma demo SHALL declarar sua própria cópia privada de `hue(...)`.

#### Scenario: hue is defined once

- **WHEN** os sources de `:games:demos` são grepados por `fun hue(`
- **THEN** existe exatamente uma definição, num helper compartilhado
- **AND** nenhuma demo individual declara um `private fun hue(...)` próprio
