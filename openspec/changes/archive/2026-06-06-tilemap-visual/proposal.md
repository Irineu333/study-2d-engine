## Why

Um cenário de plataforma é feito de **tiles**: o `Terrain (16x16).png` do Pixel Adventure 1 é um atlas de 22x11 células de 16px que se montam em chão, plataformas e bordas. Desenhar isso como dezenas de `Sprite2D` individuais seria verboso e ineficiente. A engine precisa de um node de **tilemap**: uma grade de índices que aponta para células de um atlas e as desenha de uma vez.

Esta change adiciona o **tilemap puramente visual** sobre `texture-rendering`. Por decisão de escopo (alinhada com a escolha do usuário), o tilemap **só desenha** — não gera colisão. O chão sólido da demo de plataforma será composto à mão por poucos `StaticBody2D` + `RectangleShape2D` (que já existem). Isso desacopla "desenhar o terreno" de "colidir com o terreno", mantendo esta change pequena e focada.

Terceira de quatro changes. **Depende de `texture-rendering`** (usa `Texture`, `tree.textures`, `drawImage`). É pré-requisito da `game-platformer` (o terreno e o céu da demo).

## What Changes

- **Novo recurso `TileSet`** (`@Serializable`, em `:engine`): descreve um atlas — `texturePath: String`, `tileWidth: Int`, `tileHeight: Int`. As colunas do atlas derivam de `texture.width / tileWidth`; o índice de tile `i` mapeia para a célula `(i % columns, i / columns)` ⇒ `src = Rect(Vec2(col*tileWidth, row*tileHeight), Vec2(tileWidth, tileHeight))`. Não é Node — é um descritor de dados (como `Shape2D`).
- **Novo node `TileMap : Node2D`** (`:engine`): carrega um `TileSet` (via `tileSetTexturePath` + dimensões, ou um `TileSet` embutido) e uma **grade de índices** — `columns: Int`, `rows: Int`, `tiles: List<Int>` (row-major; `-1` = célula vazia). Em `onDraw`, para cada célula não-vazia, desenha o tile do atlas via `Renderer.drawImage`, posicionado em local space na grade (`dst` da célula `(c, r)` = `Rect(Vec2(c*tileWidth, r*tileHeight), Vec2(tileWidth, tileHeight))`). Origem local no canto superior-esquerdo da grade (tilemaps não centram, diferente de sprite).
- **Visual-only**: `TileMap` **não** cria `CollisionObject2D`/`Shape2D` nem participa do `PhysicsSystem`. Colisão de terreno é responsabilidade de `StaticBody2D` posicionados à mão na cena. (Geração de colisão a partir de tiles fica registrada como extensão futura — não muda a SPI do `TileMap`.)
- **`:games:demos` ganha uma cena sentinela** com um `TileMap` montando um pedaço de terreno do atlas real, rodando **nos dois backends**, provando o mapeamento índice→célula→`drawImage` antes da demo de plataforma.
- **`TileMap` (e `TileSet`) registrados nos bindings Lua** (`nengine.TileMap`/`nengine.TileSet` + stubs), para a cena/scripts da change 4.

Sem breaking changes: adição pura sobre `texture-rendering`.

## Capabilities

### New Capabilities
- `tilemap-visual`: o descritor `TileSet` (atlas + tamanho de célula + mapeamento índice→`src`) e o node `TileMap` (grade row-major de índices com `-1` vazio, desenho por célula via `Renderer.drawImage`, **sem** colisão).

### Modified Capabilities
- `demos-sample`: nova cena com `TileMap` montando terreno do atlas, sentinela cross-backend do tilemap.
- `lua-scripting`: `TileMap` e `TileSet` expostos em `nengine.*` + entradas nos stubs LuaCATS.

## Impact

- **Código novo**: `TileSet` (descritor `@Serializable`) e `TileMap : Node2D` em `com.neoutils.engine.scene` (ou `.tilemap`); cena sentinela em demos; asset atlas PNG em `games/demos/src/main/resources/`.
- **Código tocado**: registro de tipos + stubs em `:engine-bundle-lua`.
- **Dependências**: nenhuma nova — usa `Texture`/`tree.textures`/`drawImage` de `texture-rendering`.
- **Pré-requisito**: change `texture-rendering` aplicada.
- **Invariantes**: respeita #1 (`TileMap` é Node por herança; `TileSet` é dado, análogo a `Shape2D`), #2 (sem tipo de backend), #3 (não toca o modelo de colisão — tilemap é visual; colisão segue via `CollisionObject2D`+`CollisionShape2D`), #4 (reusa `drawImage`, sem SPI nova).
- **Docs**: `CLAUDE.md` (menção a `TileMap`/`TileSet`; nota de que v1 é visual-only, colisão à mão), `ROADMAP.md`.
