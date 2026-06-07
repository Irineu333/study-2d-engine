# tilemap-visual Specification

## Purpose

Render estático de grades de tiles (`TileMap`) a partir de um atlas descrito por `TileSet`, reusando a fundação de `texture-rendering` (`Renderer.drawImage` + `tree.textures`). Tilemap é visual-only: não cria nem participa de colisão — terreno colidível permanece responsabilidade de `CollisionObject2D`/`CollisionShape2D` separados.

## Requirements

### Requirement: TileSet describes an atlas and maps tile indices to source rects

`:engine` SHALL prover um descritor `TileSet` (`@Serializable`, **não** Node) com `texturePath: String`, `tileWidth: Int`, `tileHeight: Int`. As colunas do atlas SHALL ser `texture.width / tileWidth`. Um índice de tile `i` (>= 0) SHALL mapear para a célula `(col, row)` com `col = i % columns`, `row = i / columns`, produzindo `src = Rect(Vec2(col*tileWidth, row*tileHeight), Vec2(tileWidth, tileHeight))`.

#### Scenario: tile index maps to the correct atlas cell

- **WHEN** um `TileSet` sobre um atlas 352x176 com `tileWidth = tileHeight = 16` (22 colunas) mapeia o índice `25`
- **THEN** `col == 3`, `row == 1`
- **AND** `src == Rect(Vec2(48, 16), Vec2(16, 16))`

### Requirement: TileMap renders a grid of tile indices via Renderer.drawImage

`:engine` SHALL prover `TileMap : Node2D` (`@Serializable`, `open`) carregando um `TileSet` e uma grade row-major: `columns: Int`, `rows: Int`, `tiles: List<Int>` (esperado `columns*rows` elementos). `tiles[r*columns + c]` é o índice no `TileSet`, ou `-1` para célula vazia. A textura do atlas SHALL ser resolvida no `onEnter` via `tree.textures?.load(tileSet.texturePath)` (cacheado). Em `onDraw`, para cada célula `k` com `tiles[k] >= 0`, `TileMap` SHALL chamar `renderer.drawImage(atlas, srcDoTile, dst)` com `dst = Rect(Vec2((k % columns)*tileWidth, (k / columns)*tileHeight), Vec2(tileWidth, tileHeight))`, em local space com origem no canto superior-esquerdo da grade. Células `-1` MUST NOT desenhar nada. Quando `tree.textures`/handle é `null`, `onDraw` MUST ser no-op. `localBounds` SHALL ser `Rect(ZERO, Vec2(columns*tileWidth, rows*tileHeight))`.

#### Scenario: TileMap draws each non-empty cell at its grid position

- **WHEN** um `TileMap` 3x2 (`columns=3, rows=2`) com `tiles = [5, -1, 7, -1, 2, -1]` (tileWidth=tileHeight=16) é renderizado
- **THEN** `drawImage` é chamado exatamente 3 vezes (índices `5`, `7`, `2`)
- **AND** o tile `7` (k=2) é desenhado em `dst = Rect(Vec2(32, 0), Vec2(16, 16))`
- **AND** o tile `2` (k=4) é desenhado em `dst = Rect(Vec2(16, 16), Vec2(16, 16))`

#### Scenario: empty cells draw nothing

- **WHEN** uma célula tem valor `-1`
- **THEN** nenhum `drawImage` é emitido para ela

#### Scenario: TileMap with no backend is invisible but safe

- **WHEN** `tree.textures` é `null` e um `TileMap` é renderizado
- **THEN** `onDraw` não desenha nada e não lança

### Requirement: TileMap is visual-only and does not participate in physics

`TileMap` MUST NOT ser nem criar um `CollisionObject2D`, MUST NOT carregar `CollisionShape2D`, e MUST NOT participar do `PhysicsSystem.step`. É um `Node2D` puramente visual. Colisão de terreno permanece responsabilidade de `CollisionObject2D`/`CollisionShape2D` separados (ex.: `StaticBody2D` posicionados à mão).

#### Scenario: TileMap adds no physics participant

- **WHEN** uma árvore contém apenas um `TileMap` e nenhum `CollisionObject2D`
- **THEN** `PhysicsSystem.step` não encontra nenhum participante de colisão vindo do `TileMap`
- **AND** o `TileMap` não expõe API de colisão (sem `moveAndCollide`, sem shapes)
