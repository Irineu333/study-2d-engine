## 1. Descritor TileSet

- [x] 1.1 Criar `TileSet` (`@Serializable`, não-Node) em `com.neoutils.engine.scene` (ou `.tilemap`) com `texturePath: String`, `tileWidth: Int`, `tileHeight: Int` (campos `@Inspect`).
- [x] 1.2 Implementar o mapeamento índice→`src`: `columns = texture.width / tileWidth`; `i` ⇒ `Rect(Vec2((i%columns)*tileWidth, (i/columns)*tileHeight), Vec2(tileWidth, tileHeight))`.
- [x] 1.3 Teste em `:engine`: mapeamento de índice para `src` com um atlas de dimensão conhecida (fake `Texture`).

## 2. Node TileMap

- [x] 2.1 Criar `TileMap : Node2D` (`@Serializable`, `open`) com `tileSet: TileSet` (embutido), `columns: Int`, `rows: Int`, `tiles: List<Int>` (`@Inspect`); handle do atlas `@Transient`.
- [x] 2.2 `onEnter`: resolver `tree.textures?.load(tileSet.texturePath)`; validar `tiles.size == columns*rows` (logar/avisar se não).
- [x] 2.3 `onDraw`: para cada `k` com `tiles[k] >= 0`, `drawImage(atlas, tileSet.src(tiles[k]), Rect(Vec2((k%columns)*tileWidth, (k/columns)*tileHeight), Vec2(tileWidth, tileHeight)))`; `-1` pula; `null` handle ⇒ no-op.
- [x] 2.4 `localBounds = Rect(ZERO, Vec2(columns*tileWidth, rows*tileHeight))`.
- [x] 2.5 Garantir que `TileMap` **não** é/cria `CollisionObject2D` e não participa do `PhysicsSystem`.

## 3. Testes em :engine

- [x] 3.1 Render: grade com vazios desenha só as células `>= 0`, cada uma no `dst` correto (fake `Renderer`/`TextureBackend` captura chamadas).
- [x] 3.2 Vazio (`-1`) não emite `drawImage`; sem backend ⇒ no-op; `localBounds` correto.
- [x] 3.3 Invariante: árvore só com `TileMap` ⇒ `PhysicsSystem.step` sem participantes.

## 4. Sentinela cross-backend em :games:demos

- [x] 4.1 Importar `Terrain (16x16).png` para `games/demos/src/main/resources/demos/tiles/terrain.png`.
- [x] 4.2 Criar cena `TileMapDemo` montando um pedaço de terreno (ex.: uma faixa de chão + uma plataforma), registrar no rol de cenas (Skiko default).
- [x] 4.3 Confirmar paridade visual nos dois backends (Skiko + `runLwjgl`).

## 5. Binding Lua + stubs

- [x] 5.1 Registrar `put("TileMap", TileMap::class.java)` e `put("TileSet", TileSet::class.java)` no `LuaScriptHost`.
- [x] 5.2 Adicionar entradas `TileMap`/`TileSet` nos stubs LuaCATS.
- [x] 5.3 Teste: `nengine.TileMap` e `nengine.TileSet` resolvem não-nil.

## 6. Verificação e docs

- [x] 6.1 Suíte verde: `:engine`, `:engine-bundle-lua`, `:games:demos`.
- [x] 6.2 Atualizar `CLAUDE.md` (menção a `TileMap`/`TileSet`; nota visual-only, colisão à mão) e `ROADMAP.md`.
- [x] 6.3 Rodar `/opsx:verify tilemap-visual` e fechar pendências.
