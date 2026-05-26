## ADDED Requirements

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

`CollisionStressDemo` (demo 4) e `TumblingSwarmDemo` (demo 6) SHALL adicionar uma única instância de `BoundaryWalls` em `onEnter` no lugar de criar 4 paredes manualmente. As bolinhas/quadrados de cada demo continuam batendo nas paredes via `moveAndCollide`, e o demo MUST funcionar corretamente quando a janela é redimensionada — bolinhas batem nas paredes nas novas posições no mesmo frame em que o resize é percebido.

#### Scenario: CollisionStressDemo wires BoundaryWalls

- **WHEN** o source de `CollisionStressDemo.kt` é inspecionado
- **THEN** `onEnter` chama `addChild(BoundaryWalls())` exatamente uma vez
- **AND** `onEnter` NÃO contém 4 chamadas separadas criando paredes manualmente

#### Scenario: TumblingSwarmDemo wires BoundaryWalls

- **WHEN** o source de `TumblingSwarmDemo.kt` é inspecionado
- **THEN** `onEnter` chama `addChild(BoundaryWalls())` exatamente uma vez
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
