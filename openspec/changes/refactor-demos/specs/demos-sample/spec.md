## MODIFIED Requirements

### Requirement: Demos module exists as an executable Skiko sample

O projeto SHALL prover um módulo `:games:demos` que depende de `:engine` e `:engine-skiko`, hospeda um conjunto de **5 demos** de consistência da engine (`Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`) num único processo, e é executável via `./gradlew :games:demos:run`. O `Main.kt` MUST instanciar `SkikoHost().run(SceneTree(root = DemoSwitcherRoot()), GameConfig(...))` com `title = "engine-consistency demos"`, `width = 800`, `height = 600`.

A navegação entre demos MUST ser feita por um **menu de UI** (não por teclas `1`–`0`): a raiz exibe um menu com um `Button` por demo; selecionar um botão carrega a demo; cada demo exibe um `Button` "← Menu" que retorna ao menu. As demos rodam em coordenadas de pixel da surface por padrão, **exceto** a demo `Transforms`, que instala deliberadamente uma `Camera2D` local à cena (a antiga convenção "nenhuma demo usa `Camera2D`" foi removida). Elementos sensíveis a `tree.size` MUST reagir explicitamente a mudanças dessa dimensão.

#### Scenario: Demos module runs from Gradle

- **WHEN** um desenvolvedor executa `./gradlew :games:demos:run` da raiz do projeto
- **THEN** uma janela desktop Skiko abre com o título `"engine-consistency demos"` exibindo o menu de demos
- **AND** a janela permanece responsiva e clicar num botão do menu carrega a demo correspondente

#### Scenario: Camera2D is used only by the Transforms demo

- **WHEN** os sources de `:games:demos` são inspecionados
- **THEN** apenas a cena `Transforms` (solar system) importa/instancia `com.neoutils.engine.scene.Camera2D`
- **AND** nenhuma outra demo instancia `Camera2D`

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

### Requirement: Each demo scene has a documented role exercising specific invariants

A spec `demos-sample` SHALL incluir uma descrição por demo explicando o que ela exercita do ponto de vista da engine — quais invariantes valida, qual sistema põe sob carga, qual diagnóstico visual oferece. Essa documentação MUST viver na spec (não em `CLAUDE.md` nem em `README.md`). As descrições MUST cobrir, no mínimo:

- **`Transforms`** (ex-Solar system): Sol amarelo central com 8 planetas e luas conhecidas orbitando seus pais; Saturno carrega `SaturnRing`. Exercita composição de transform aninhada em até 4 níveis. Adiciona uma `Camera2D` com **zoom/pan interativo** (scroll/teclas) — primeira cobertura de `Camera2D` entre as demos. O zoom também exercita **escala-composição**: ampliar/reduzir escala toda a hierarquia aninhada em uníssono (ancestor scale → tamanho renderizado do filho, o invariante que a antiga demo Scale validava isoladamente). O HUD/overlay vive em `CanvasLayer` e NÃO sofre o zoom da câmera.
- **`Spawn & Collide`** (funde os antigos Spawner + Collision stress): clique/auto-spawn adiciona `RigidBody2D` bolinhas durante `onProcess`; um trap `Area2D` central as remove durante `onAreaEntered`. As bolinhas quicam elasticamente entre si e nas paredes de uma `BoundaryWalls`. Exercita mutação segura durante traversal, sensor `Area2D`, solver `RigidBody2D` e cache de world-transform sob carga.
- **`Rotating Frame`** (antigo Rotating box): `CharacterBody2D` bolinhas vivem como filhas de um wrapper que rotaciona e translada a cada frame; 4 paredes `StaticBody2D` são filhas do mesmo wrapper, em coordenadas locais. `moveAndCollide` opera no parent frame compartilhado, mantendo o sweep axis-aligned mesmo com a caixa girando em world. Exercita invalidação por mutação de ancestral sob carga real, em frame rotativo.
- **`Tumbling Swarm`** (antigo Tumbling swarm): quadrados `RigidBody2D` (`restitution=1f`, `friction=0.4f`) com velocidade linear e angular, dentro de `BoundaryWalls`. O solver resolve cada contato pelo caminho rotated do sweep com impulso normal + Coulomb tangencial — spin perceptível em hits glancing.
- **`Sprites & Tiles`** (funde os antigos Sprite + Animated + Tilemap): um `TileMap` monta um chão a partir de um atlas real; um `AnimatedSprite2D` "corre" sobre ele (avanço de frame engine-driven); um `Sprite2D` estático decora a cena. O player é um `CharacterBody2D` movendo-se sobre o chão de `StaticBody2D` via `moveAndCollide`. Sentinela cross-backend (Skiko + LWJGL) de `texture-rendering`, `sprite-animation` e `tilemap-visual` numa só tela.

#### Scenario: Spec describes all five demos

- **WHEN** `openspec/specs/demos-sample/spec.md` é aberto
- **THEN** existe uma seção (Requirement) cobrindo as demos `Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`
- **AND** cada demo tem ao menos um parágrafo descrevendo o invariante ou sistema que exercita
- **AND** o conteúdo dessa Requirement não duplica detalhe de implementação (esses ficam nas specs `engine-core`, `rigid-body-2d`, `kinematic-move-and-collide`)

#### Scenario: README.md can summarize without losing detail

- **WHEN** o `README.md` resume cada demo em uma única linha
- **THEN** o leitor que quer o detalhe completo encontra o material em `openspec/specs/demos-sample/spec.md`
- **AND** o `CLAUDE.md` pode permanecer livre de descrição demo-a-demo (apenas a tabela "Games" de uma linha)

### Requirement: Demos behave identically (semantically) on both backends

As cinco demos (`Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`) e o menu de navegação SHALL produzir comportamento de gameplay semanticamente idêntico em ambos os entrypoints (`:games:demos:run` Skiko e `:games:demos:runLwjgl` LWJGL). "Semanticamente idêntico" MUST ser interpretado como: mesmo menu e mesmas demos disponíveis, mesma resposta a input (clique do mouse no menu/botões e no Spawner na posição esperada; arena boundaries reagem a resize; câmera da demo Transforms responde a scroll/arrasto/teclas), mesma evolução de física dado o mesmo `physicsHz`, mesmas árvores de Nodes (compartilham o código `DemoSwitcherRoot`).

Diferenças puramente visuais (anti-aliasing, text shaping, sub-pixel positioning) MUST ser aceitas dentro de tolerância. Qualquer divergência semântica MUST ser tratada como bug do backend e investigada antes do merge.

#### Scenario: Menu navigation works identically on both backends

- **WHEN** o usuário clica num botão de demo no menu em qualquer dos dois entrypoints
- **THEN** a demo correspondente é carregada em ambos
- **AND** o botão "← Menu" retorna ao menu em ambos
- **AND** o conjunto de demos disponíveis é o mesmo

#### Scenario: Spawner mouse click adds a ball at the click position on both backends

- **GIVEN** a demo `Spawn & Collide` está ativa
- **WHEN** o usuário clica com o botão esquerdo em `(x, y)` em pixels da janela (e nenhum `Button` da UI está sob o ponteiro)
- **THEN** uma nova bolinha aparece com `position ≈ (x, y)` em ambos os backends
- **AND** o trap central remove a bolinha quando ela entra via `onAreaEntered`

#### Scenario: BoundaryWalls follow window resize on both backends

- **GIVEN** as demos `Spawn & Collide`, `Rotating Frame` ou `Tumbling Swarm` estão ativas
- **WHEN** o usuário redimensiona a janela
- **THEN** as 4 paredes acompanham o novo `tree.size` no próximo `onPhysicsProcess` em ambos os backends

### Requirement: Documentation reflects the five-demo catalog and resize-aware behavior

A tabela "Games" do `CLAUDE.md` e a seção de demos do `README.md` SHALL refletir o catálogo de **5 demos** (`Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`) com navegação por menu. A documentação SHALL mencionar que as demos com arena (`Spawn & Collide`, `Tumbling Swarm`) têm paredes resize-aware via `BoundaryWalls`.

#### Scenario: README lists the five demos

- **WHEN** `README.md` é inspecionado
- **THEN** as 5 demos aparecem com um resumo de uma linha cada
- **AND** a navegação é descrita como menu de UI (não teclas `1`–`0`)

#### Scenario: CLAUDE.md Games table reflects the new catalog

- **WHEN** a tabela "Games" do `CLAUDE.md` é inspecionada
- **THEN** a linha de `:games:demos` descreve o novo conjunto de demos sem listar 10 cenas numeradas

## ADDED Requirements

### Requirement: A navigation menu hosts the demos and validates ui-foundation

O módulo `:games:demos` SHALL prover um **menu de navegação** construído com primitivas de UI (`CanvasLayer` + `Button` + `Panel` + `Label`), exibido pela raiz `DemoSwitcherRoot` na inicialização. O menu MUST conter um `Button` por demo (5 botões), cada um conectado a `pressed` para carregar a demo correspondente via `addChild`/`removeChild`. Cada demo carregada MUST exibir um overlay `CanvasLayer` contendo um `Label` de título, um `Label` de descrição e um `Button` "← Menu" que retorna ao menu. Esse mecanismo absorve a antiga demo de UI dedicada — `Button` (estados normal/hover/press/disabled, signal `pressed`, hit-test, click-consumption), `Panel`, `Label`, anchors e z-order ficam exercitados continuamente em toda tela.

#### Scenario: Menu shows one button per demo

- **WHEN** a aplicação inicia
- **THEN** o menu exibe 5 botões, um por demo (`Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`)

#### Scenario: Clicking a demo button loads it and consumes the click

- **WHEN** o usuário clica num botão de demo
- **THEN** o menu é removido e a demo correspondente é adicionada à árvore
- **AND** o clique é consumido (`tree.input.wasMouseClicked(Left)` retorna `false` para nós de gameplay nesse tick)

#### Scenario: Back button returns to the menu

- **GIVEN** uma demo está carregada e exibe seu overlay com o `Button` "← Menu"
- **WHEN** o usuário clica em "← Menu"
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

## REMOVED Requirements

### Requirement: Demos scene 7 validates ui-foundation in both backends

**Reason**: A demo de UI dedicada (`UiPlaygroundDemo`, slot 7) é absorvida pelo menu de navegação e pelo botão "← Menu" presente em toda demo, que passam a exercitar `CanvasLayer`/`Button`/`Panel`/`Label`/anchors/z-order/click-consumption continuamente.

**Migration**: A cobertura de `ui-foundation` é garantida pela nova requirement "A navigation menu hosts the demos and validates ui-foundation". Nenhum slot `7` é necessário; `UiPlaygroundDemo.kt` é removido.
