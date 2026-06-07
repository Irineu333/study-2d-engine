## Context

`texture-rendering` deu `Texture`/`tree.textures`/`Renderer.drawImage(src, dst, flipH)` com `src`/`dst` arbitrários — exatamente o que um tilemap precisa: desenhar recortes (células do atlas) em posições de grade. O `Terrain (16x16).png` é um atlas 352x176 = 22 colunas × 11 linhas de células 16×16.

A engine já tem o modelo de colisão (`CollisionObject2D` + `CollisionShape2D` + `PhysicsSystem`, invariante #3). O usuário optou por **tilemap visual-only**: o terreno desenhado é separado do terreno sólido (`StaticBody2D` à mão). Isso mantém esta change pequena e não toca o invariante #3.

## Goals / Non-Goals

**Goals:**
- Um descritor `TileSet` (atlas + tamanho de célula) e um node `TileMap` (grade de índices) que desenha o terreno reusando `drawImage`.
- Mapeamento determinístico índice→célula do atlas e célula da grade→`dst` em local space.
- `-1` = célula vazia (não desenha).
- Sentinela cross-backend em `:games:demos`.

**Non-Goals:**
- **Colisão a partir do tilemap** — decisão de escopo: visual-only. Chão sólido = `StaticBody2D` à mão. (Auto-geração de colisão é extensão futura sem mudança da SPI do `TileMap`.)
- Múltiplas camadas (layers) de tilemap, autotiling, terrain bitmasking, tiles animados.
- Edição em runtime ergonômica além de setar `tiles`/`columns`/`rows` (sem API de `setCell` no v1 — pode entrar depois).
- Scroll infinito / chunking — a grade é finita e pequena (uma tela de demo).
- `flipH`/rotação por tile — v1 desenha tiles na orientação do atlas.

## Decisions

### D1 — `TileSet` é descritor de dados (`@Serializable`), não Node

`TileSet` carrega `texturePath`, `tileWidth`, `tileHeight`. `columns = texture.width / tileWidth`. Índice `i` ⇒ `col = i % columns`, `row = i / columns` ⇒ `src = Rect(Vec2(col*tileWidth, row*tileHeight), Vec2(tileWidth, tileHeight))`.

- **Por quê não-Node**: é dado de configuração compartilhável entre tilemaps, análogo a `Shape2D` (que `CollisionShape2D` carrega). Não tem transform nem lifecycle próprios.
- **Por quê derivar `columns` do atlas**: usa o `texture.width` que `Texture` expõe; evita um campo redundante que pode divergir do atlas real.
- **Embutir vs referenciar**: o `TileMap` pode embutir o `TileSet` como propriedade serializável (mais simples para uma cena declarativa) — sem um sistema de recursos externos no v1.

### D2 — `TileMap` guarda a grade como `List<Int>` row-major, `-1` vazio

`columns: Int`, `rows: Int`, `tiles: List<Int>` (tamanho `columns*rows`, row-major). `tiles[r*columns + c]` é o índice no `TileSet`, ou `-1` para vazio.

- **Por quê `List<Int>` plano**: serializa trivialmente em `scene.json` (um array de ints), é compacto e óbvio. Row-major é a convenção universal de tilemap.
- **Por quê `-1` vazio**: sentinela natural fora do espaço de índices válidos (>= 0); permite buracos (céu entre plataformas) sem desenhar nada.
- **Distinção de duas grades**: a grade **do atlas** (quais células existem na textura, via `TileSet.columns`) e a grade **do mapa** (`TileMap.columns/rows`, o layout no mundo) são independentes — `tiles[k]` indexa a primeira, a posição `k` indexa a segunda.

### D3 — `onDraw` desenha por célula, origem no canto (não centrado)

Para cada `k` com `tiles[k] >= 0`: `c = k % columns`, `r = k / columns`; `dst = Rect(Vec2(c*tileWidth, r*tileHeight), Vec2(tileWidth, tileHeight))`; `src` do `TileSet` para `tiles[k]`; `drawImage(atlasTex, src, dst)`. Origem local = canto superior-esquerdo (`(0,0)`) da grade.

- **Por quê canto, não centrado**: tilemaps são naturalmente ancorados no canto (coordenada de célula = posição de grade). Centrar complicaria o cálculo de célula sem ganho. (Sprite/AnimatedSprite centram porque representam um objeto; tilemap representa um campo de grade.)
- **`localBounds`**: `Rect(ZERO, Vec2(columns*tileWidth, rows*tileHeight))`.

### D4 — Visual-only: zero acoplamento com física

`TileMap` **não** cria nem é um `CollisionObject2D`, não tem `CollisionShape2D`, não aparece no `PhysicsSystem.step`. É um `Node2D` que só desenha.

- **Por quê**: decisão de escopo do usuário. Desacopla render de física, mantém a change pequena, e não arrisca o invariante #3. O chão sólido da demo são `StaticBody2D` posicionados à mão (a demo documenta a correspondência visual↔colisor).
- **Extensão futura**: um `TileMap.generateCollision()` (ou um flag por tile "sólido") que cria `StaticBody2D`/shapes a partir das células sólidas — não muda a SPI de desenho do `TileMap`.

### D5 — Resolução de atlas no `onEnter`; sentinela em demos

A textura do atlas resolve no `onEnter` via `tree.textures?.load(tileSet.texturePath)` (cacheado). `null` ⇒ `onDraw` no-op. Sentinela = cena em `:games:demos` montando um pedaço de terreno real, nos dois backends.

- **Por quê demos**: prova índice→célula→`dst` **antes** da demo de plataforma; reaproveita o test bed cross-backend. Um tile no índice/posição errado salta aos olhos.

## Risks / Trade-offs

- **`tiles.size != columns*rows`** → validar no `onEnter` (logar/avisar); desenhar só os índices válidos. Documentar a expectativa de tamanho.
- **Índice fora do range do atlas** → célula inválida; clampar/pular e avisar. Não deve quebrar o frame.
- **Visual e colisão divergirem** (chão desenhado num lugar, colisor noutro) → risco inerente ao visual-only; a demo encapsula a correspondência e a documenta. Auto-geração futura elimina o risco.
- **`texture.width` não divisível por `tileWidth`** → `columns` trunca; os assets reais são exatos (352/16=22). Documentar.
- **Muitos tiles ⇒ muitos `drawImage`** → para uma tela de demo é trivial; chunking/culling é otimização futura sem mudança de SPI.
