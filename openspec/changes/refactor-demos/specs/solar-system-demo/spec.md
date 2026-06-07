## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Transforms scene provides interactive Camera2D zoom/pan

A demo `Transforms` SHALL instalar uma `Camera2D` (`current = true`) local à cena, cujo `bounds: Rect` define o retângulo de mundo enquadrado. O usuário MUST poder fazer **zoom** (encolhendo/expandindo `bounds`) e **pan** (transladando `bounds.origin`) de forma interativa via input (scroll do mouse e/ou teclas). A câmera MUST ser desmontada junto com a cena ao retornar ao menu, de modo que as demais demos sigam em pixels de surface crus (sem `Camera2D`). O overlay de UI da demo (título/descrição/back-button) vive em `CanvasLayer` e MUST NOT sofrer a view transform da câmera.

#### Scenario: Scrolling zooms the solar system

- **GIVEN** a demo `Transforms` está ativa
- **WHEN** o usuário rola o scroll do mouse
- **THEN** o sistema solar é ampliado/reduzido (a `Camera2D.bounds` muda de escala)
- **AND** o título/descrição em `CanvasLayer` permanecem do mesmo tamanho em pixels (não sofrem zoom)

#### Scenario: Camera is scoped to the Transforms scene

- **WHEN** o usuário retorna ao menu e carrega outra demo
- **THEN** a `Camera2D` da demo `Transforms` foi desmontada
- **AND** a outra demo renderiza em pixels de surface crus
