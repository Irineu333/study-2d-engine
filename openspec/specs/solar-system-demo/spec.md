# solar-system-demo Specification

## Purpose

The `Transforms` demo of `:games:demos` (reached via the menu's `Transforms` button) hosts `SolarSystemDemo` — a code-only scene with a Sun, 8 planets, 7 known moons (Moon; Io, Europa, Ganymede, Callisto; Titan; Triton) and a `SaturnRing`. Each planetary and lunar orbit is a `Rotator` pivot with its own `angularVelocity`, exercising the A1 transform-composition invariant in up to four nested levels (Sun → planet-orbit → planet → moon-orbit → moon). The scene also installs a `Camera2D` (`current = true`) with interactive zoom/pan — the only demo using a camera — so zoom additionally exercises ancestor→child scale composition. The capability fixes the menu entry, the tree topology, the resize semantics, the interactive camera, and the location of tunable constants.

## Requirements

### Requirement: Slot 1 of demos hosts SolarSystemDemo

O módulo `:games:demos` SHALL hospedar a cena `SolarSystemDemo` como a demo **`Transforms`**, alcançada pelo botão `Transforms` do **menu de navegação** (não mais pela tecla `1`). O enum interno `DemoSwitcherRoot.Slot` (ou equivalente) MUST referenciar a cena como `Transforms`/`SolarSystem`, mapeada para `::SolarSystemDemo`. A classe `TransformOrbitDemo` MUST NOT existir no source tree do módulo. O título exibido pela demo MUST ser renderizado por um `Label` em `CanvasLayer` (não por `drawText` cru) com a string descrevendo a composição de transform + câmera.

#### Scenario: Selecting Transforms in the menu loads the solar system demo

- **WHEN** o usuário clica no botão `Transforms` do menu
- **THEN** a cena ativa é uma instância de `SolarSystemDemo`
- **AND** o overlay da demo exibe, via `Label` em `CanvasLayer`, o título da demo `Transforms`

#### Scenario: TransformOrbitDemo no longer exists

- **WHEN** o source tree de `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/` é inspecionado
- **THEN** nenhum arquivo contém a declaração `class TransformOrbitDemo`
- **AND** nenhum arquivo do módulo importa ou referencia o identificador `TransformOrbitDemo`

### Requirement: SolarSystemDemo builds a fixed scene of sun, planets, moons and ring

`SolarSystemDemo` SHALL ser uma `class SolarSystemDemo : Node2D()` que, no seu `init`, constrói (via método privado `buildTree()`) uma árvore com exatamente a seguinte topologia e quantidades de corpos celestes:

- 1 `Node2D` filho direto chamado `Center` (sem visual próprio), posicionado em `(tree.width / 2f, tree.height / 2f)` quando `tree.size` é conhecido.
- Sob `Center`: 1 `Circle2D` chamado `Sun` em posição local `Vec2.ZERO`.
- Sob `Center`: 8 instâncias de `Rotator` chamadas `MercuryOrbit`, `VenusOrbit`, `EarthOrbit`, `MarsOrbit`, `JupiterOrbit`, `SaturnOrbit`, `UranusOrbit`, `NeptuneOrbit`, cada uma com sua `angularVelocity` distinta.
- Sob cada `*Orbit`: exatamente 1 `Circle2D` planeta (`Mercury`, `Venus`, ..., `Neptune`), posicionado em local `(radius, 0f)`.
- Sob `Earth`: 1 `Rotator` `MoonOrbit` contendo 1 `Circle2D` `Moon`.
- Sob `Jupiter`: 4 `Rotator`s (`IoOrbit`, `EuropaOrbit`, `GanymedeOrbit`, `CallistoOrbit`), cada um contendo 1 `Circle2D` (`Io`, `Europa`, `Ganymede`, `Callisto`).
- Sob `Saturn`: 1 `SaturnRing` (nó visual customizado), e 1 `Rotator` `TitanOrbit` contendo 1 `Circle2D` `Titan`.
- Sob `Neptune`: 1 `Rotator` `TritonOrbit` contendo 1 `Circle2D` `Triton`.

Além da topologia de corpos celestes acima, a cena PODE conter nós adicionais introduzidos pela câmera: um `Camera2D` (`current = true`). Esses nós extras MUST ser nomeados e identificáveis, e MUST NOT alterar a topologia/contagem dos corpos celestes descrita acima.

#### Scenario: All eight planets are present under Center

- **WHEN** uma instância de `SolarSystemDemo` é construída e seu `Center` é localizado via `findChild("Center")`
- **THEN** `Center.children` contém exatamente um nó nomeado `Sun` e os oito nós nomeados `MercuryOrbit`, `VenusOrbit`, `EarthOrbit`, `MarsOrbit`, `JupiterOrbit`, `SaturnOrbit`, `UranusOrbit`, `NeptuneOrbit` (em qualquer ordem)

#### Scenario: Earth has exactly one moon (Moon)

- **WHEN** o nó `Earth` é localizado (filho único de `EarthOrbit`)
- **THEN** `Earth.children` contém exatamente um nó `MoonOrbit`
- **AND** `MoonOrbit.children` contém exatamente um nó `Moon`

#### Scenario: Jupiter has exactly four Galilean moons

- **WHEN** o nó `Jupiter` é localizado (filho único de `JupiterOrbit`)
- **THEN** `Jupiter.children` contém exatamente quatro `Rotator`s nomeados `IoOrbit`, `EuropaOrbit`, `GanymedeOrbit`, `CallistoOrbit`
- **AND** cada um contém exatamente um `Circle2D` nomeado `Io`, `Europa`, `Ganymede`, `Callisto` respectivamente

#### Scenario: Saturn has ring and Titan

- **WHEN** o nó `Saturn` é localizado (filho único de `SaturnOrbit`)
- **THEN** `Saturn.children` contém exatamente um nó do tipo `SaturnRing` e um `Rotator` nomeado `TitanOrbit`
- **AND** `TitanOrbit.children` contém exatamente um `Circle2D` nomeado `Titan`

#### Scenario: Neptune has exactly one moon (Triton)

- **WHEN** o nó `Neptune` é localizado
- **THEN** `Neptune.children` contém exatamente um `Rotator` nomeado `TritonOrbit`
- **AND** `TritonOrbit.children` contém exatamente um `Circle2D` nomeado `Triton`

#### Scenario: Mercury, Venus, Mars, Uranus are childless

- **WHEN** cada um dos nós `Mercury`, `Venus`, `Mars`, `Uranus` é localizado
- **THEN** seu `children` é vazio (essas posições orbitais não recebem luas neste demo)

### Requirement: Center repositions on viewport resize

`SolarSystemDemo` SHALL ter um `onProcess(dt: Float)` que, sempre que `tree.size` muda em relação à última observação, reposiciona o nó `Center` para `Vec2(tree.width / 2f, tree.height / 2f)`. A implementação MUST seguir o idiom de cache de `lastSize` (campo `@Transient private var lastSize: Vec2 = Vec2.ZERO`, early-return se `tree.size == lastSize`). A implementação MUST NOT recomputar raios orbitais dos planetas nem reposicionar planetas/luas em resize — apenas o `Center` se move.

#### Scenario: Center follows viewport center

- **WHEN** o demo está rodando e `tree.size` é igual a `Vec2(800f, 600f)`
- **THEN** o `transform.position` do nó `Center` é `Vec2(400f, 300f)`

#### Scenario: Center updates only when size changes

- **WHEN** o demo está rodando e `tree.size` permanece constante entre dois frames consecutivos
- **THEN** o `transform` do `Center` NÃO MUST ser reatribuído nesse segundo frame (verificável por instrumentação ou por inspeção do código: a guarda `if (tree.size == lastSize) return` aparece antes de qualquer escrita em transform)

### Requirement: Transforms scene provides interactive Camera2D zoom/pan

A demo `Transforms` SHALL instalar uma `Camera2D` (`current = true`) local à cena, cujo `bounds: Rect` define o retângulo de mundo enquadrado. O usuário MUST poder fazer **zoom** (encolhendo/expandindo `bounds`, scroll do mouse) e **pan** (transladando `bounds.origin`). O pan MUST estar disponível por **arrasto do mouse** (segurar o botão esquerdo e mover — grab-and-drag, com o ponto de mundo sob o cursor pinado durante o arrasto) **e** por teclas (setas). O drag-pan MUST honrar `mouseDragConsumed` (arrastar um painel de debug não arrasta o sistema solar). A câmera MUST ser desmontada junto com a cena ao retornar ao menu, de modo que as demais demos sigam em pixels de surface crus (sem `Camera2D`). O overlay de UI da demo (título/descrição/back-button) vive em `CanvasLayer` e MUST NOT sofrer a view transform da câmera.

Além de enquadrar a cena, o zoom **exercita a escala-composição**: a view scale da câmera é empilhada via `Renderer.pushTransform` no topo do world pass e compõe com o transform local de cada nó, então ampliar/reduzir escala a hierarquia aninhada inteira (Sol → órbita → planeta → lua) em uníssono — a mesma propagação `ancestor scale → tamanho renderizado do filho` que a antiga demo `Scale hierarchy` validava isoladamente. Por isso a demo Scale não precisa de slot próprio nem de um corpo de pulso de escala dedicado.

#### Scenario: Scrolling zooms the solar system

- **GIVEN** a demo `Transforms` está ativa
- **WHEN** o usuário rola o scroll do mouse
- **THEN** o sistema solar é ampliado/reduzido (a `Camera2D.bounds` muda de escala)
- **AND** toda a hierarquia aninhada (planetas e luas) escala em uníssono, demonstrando a escala-composição ancestor → filho
- **AND** o título/descrição em `CanvasLayer` permanecem do mesmo tamanho em pixels (não sofrem zoom)

#### Scenario: Dragging the mouse pans the solar system

- **GIVEN** a demo `Transforms` está ativa
- **WHEN** o usuário segura o botão esquerdo do mouse e arrasta sobre a cena
- **THEN** o sistema solar acompanha o cursor (a `Camera2D.bounds.origin` translada), mantendo o ponto de mundo agarrado sob o cursor
- **AND** soltar o botão encerra o pan

#### Scenario: Camera is scoped to the Transforms scene

- **WHEN** o usuário retorna ao menu e carrega outra demo
- **THEN** a `Camera2D` da demo `Transforms` foi desmontada
- **AND** a outra demo renderiza em pixels de surface crus

### Requirement: Rotator becomes configurable per-instance

A classe `Rotator` em `:games:demos` SHALL declarar `var angularVelocity: Float` (default `1f`) e usar essa variável em seu `onProcess(dt)` (em vez de ler uma constante global como hoje). A classe MUST viver em um arquivo próprio `Rotator.kt` no package `com.neoutils.engine.games.demos`. A constante `TransformOrbitDemo.ANGULAR_VELOCITY` MUST NOT existir mais após esta change.

#### Scenario: Rotator advances rotation by its own angularVelocity

- **WHEN** um `Rotator` é construído com `angularVelocity = 2f` e seu `onProcess(0.5f)` é chamado uma vez
- **THEN** o `transform.rotation` aumenta em `1f` (= `2f * 0.5f`) em relação ao valor anterior

#### Scenario: Rotator lives in its own file

- **WHEN** o source tree de `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/` é inspecionado
- **THEN** existe o arquivo `Rotator.kt` declarando `class Rotator : Node2D` com a property `var angularVelocity: Float`
- **AND** nenhum outro arquivo do módulo declara `class Rotator` (a definição é única)

#### Scenario: Global ANGULAR_VELOCITY constant is removed

- **WHEN** o source do módulo é inspecionado
- **THEN** nenhuma `companion object` declara `const val ANGULAR_VELOCITY`
- **AND** nenhum `Rotator.onProcess` lê uma constante de outro objeto/classe; lê apenas `this.angularVelocity`

### Requirement: SaturnRing draws a flattened hollow ellipse

O arquivo `SolarSystemDemo.kt` SHALL declarar uma classe top-level `class SaturnRing : Node2D()` cujo `transform` local default tem `scale = Vec2(1f, 0.4f)` (ou outro valor entre 0.3 e 0.5 escolhido no design) e cujo `onDraw(renderer)` chama `renderer.drawCircle(center = Vec2.ZERO, radius = R, color = C, filled = false, thickness = T)` com `R`, `C` (alpha < 1.0), e `T` definidos como constantes/properties do próprio arquivo. A classe MUST NOT ser declarada em `:engine`. O `SaturnRing` MUST ser filho do nó `Saturn` (não de `SaturnOrbit`) para herdar a translação orbital de Saturno sem girar com a órbita.

#### Scenario: SaturnRing is local to the demos module

- **WHEN** os arquivos de `:engine` são inspecionados
- **THEN** nenhum arquivo declara `class SaturnRing`
- **AND** o arquivo `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/SolarSystemDemo.kt` declara `class SaturnRing : Node2D()` como classe top-level

#### Scenario: SaturnRing has non-uniform scale

- **WHEN** uma instância de `SaturnRing` recém-construída é inspecionada
- **THEN** seu `transform.scale.x != transform.scale.y` (o `y` é menor, produzindo a aparência de elipse achatada após o push-de-transform do renderer)

#### Scenario: SaturnRing draws hollow

- **WHEN** o método `onDraw(renderer)` de `SaturnRing` é executado
- **THEN** o renderer recebe uma chamada `drawCircle(..., filled = false, ...)` (não preenchido)

### Requirement: Speeds and palette live in companion objects

`SolarSystemDemo` SHALL declarar uma `companion object` (ou múltiplos objetos nested) agrupando todas as velocidades angulares (uma constante por planeta e lua), raios orbitais e cores em um lugar único do arquivo. O `buildTree()` MUST ler dessas constantes; MUST NOT espalhar literais numéricos pelo corpo de `buildTree`. Justificativa: tunagem visual é o caso de uso mais frequente após o demo rodar; centralizar reduz fricção.

#### Scenario: Tuning angular velocity touches only the companion

- **WHEN** um contribuidor quer reduzir a velocidade de Júpiter pela metade
- **THEN** a edição é feita em uma única linha dentro de uma companion object (ex.: `const val JUPITER_OMEGA = 0.065f`), sem alterar o corpo de `buildTree`

### Requirement: Transforms title/description shown via CanvasLayer Label

A demo `Transforms` SHALL exibir seu título e descrição via nós `Label` dentro de um `CanvasLayer` (screen-space), não via `drawText` cru num HUD overlay. O texto MUST descrever a composição de transform aninhada e a câmera interativa. O título/descrição MUST NOT sofrer o zoom/pan da `Camera2D` da cena (vivem em screen-space).

#### Scenario: Transforms title is a Label in a CanvasLayer

- **WHEN** a demo `Transforms` está ativa
- **THEN** seu título e descrição são renderizados por nós `Label` filhos de um `CanvasLayer`
- **AND** ao aplicar zoom/pan na câmera, o título/descrição NÃO escalam nem transladam (permanecem em pixels de tela)

### Requirement: CLAUDE.md Games table reflects the Transforms demo

O arquivo `CLAUDE.md` (raiz do repo) SHALL descrever, na linha de `:games:demos` da tabela "Games", o catálogo de 5 demos incluindo a demo `Transforms` (com `Camera2D`), sem listar cenas numeradas `1`–`0`. O texto MUST mencionar que `Transforms` exercita a composição de transform aninhada com zoom/pan de `Camera2D`.

#### Scenario: CLAUDE.md describes the Transforms demo

- **WHEN** o arquivo `CLAUDE.md` é lido
- **THEN** a linha de `:games:demos` da tabela "Games" menciona a demo `Transforms` com `Camera2D` e a composição aninhada de transform
- **AND** não há lista numerada de cenas `1`–`0` para os demos
