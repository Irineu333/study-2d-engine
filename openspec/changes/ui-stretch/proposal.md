## Why

UI in-game vive em screen-space puro (`CanvasLayer` desenha em `tree.size` pixels, ignorando a `Camera2D`), mas os jogos com câmera (`pong`, `snake`, `tictactoe`) autoram o HUD em **coordenadas de design/mundo** (ex.: scores do Pong em `320/440` em torno do centro `400` de `bounds 800×600`; `board.lua` da Velha usa `WORLD_CENTER_X = 300`). Como a `Camera2D` (`FIT`) escala e letterboxa o mundo no resize mas a UI screen-space não recebe esse fator, o HUD **desalinha e não escala junto com o jogo** assim que a janela deixa de ter o tamanho de design. É o débito já registrado no `ROADMAP.md` ("HUD screen-space não acompanha o resize"), e só aparece em jogos com `Camera2D` porque sem câmera screen-space e world-space coincidem.

## What Changes

- **BREAKING (invariante #6):** `CanvasLayer` deixa de desenhar sempre em `tree.size` pixels crus. Ganha um flag `followStretch: Boolean` (default `true`); quando ligado, seus descendentes são resolvidos e desenhados em **design-space** (`tree.designSize`) e a engine empilha uma **UI stretch transform** que mapeia o design rect na superfície com letterbox — fazendo posição, tamanho e `fontSize` escalarem em sincronia com o mundo. Layers com `followStretch = false` mantêm o comportamento atual (pixels crus).
- `SceneTree` ganha `designSize: Vec2` e `uiStretchMode` (`FIT`/`FILL`/`STRETCH`/`DISABLED`), e computa uma `uiStretchTransform(designSize, size, mode)` por frame. A transform reaproveita só a parte de **adaptação de resolução** (escala + offset de centralização do letterbox) — **sem** o pan/zoom dinâmico da `Camera2D`, para o HUD não arrastar com a câmera.
- O **anchor layout pass** e o **hit-test UI** passam a usar `Rect(ZERO, designSize)` como parent rect na fronteira de um `CanvasLayer` com `followStretch`, e `screenRect()`/hit-test compõem a mesma stretch transform — clique e desenho permanecem consistentes.
- A `DebugLayer` auto-inserida (`ScreenDebugCanvas`) é marcada `followStretch = false`: inspector, HUD de debug e painéis continuam em pixels cros, imunes ao stretch.
- Migração dos jogos com `Camera2D` (`pong`, `snake`, `tictactoe`) para consumir o design-space (HUD passa a alinhar e escalar com o tabuleiro/quadra). `hello-world` (sem câmera) é no-op porque `designSize == size`.

## Capabilities

### New Capabilities
- `ui-stretch`: resolução de design (`designSize`) e UI stretch transform na `SceneTree`; flag `followStretch` no `CanvasLayer`; regra de derivação da transform (escala + centralização do letterbox, sem pan de câmera) e seu default a partir do `bounds` da `Camera2D` corrente / `GameConfig`.

### Modified Capabilities
- `ui-foundation`: a requirement "CanvasLayer renders children in screen-space" passa a distinguir layers `followStretch` (design-space esticado) de layers crus; a requirement da two-pass render walk empilha a UI stretch transform por layer; a requirement de hit-test e a de "Button screen-space rect" compõem a mesma transform.
- `ui-controls-base`: a requirement "Anchor layout pass resolves rect from the parent rect" usa `Rect(ZERO, designSize)` (não `Rect(ZERO, size)`) como parent rect na fronteira de um `CanvasLayer` com `followStretch`.

## Impact

- **`:engine`** — `SceneTree` (novos campos `designSize`/`uiStretchMode`, cálculo da stretch transform, `runAnchorLayout`, `hitTestUI`, `render`), `CanvasLayer` (campo `followStretch`), `Control.screenRect()`/`worldBounds()` na composição com a stretch transform. Auto-inserção da `DebugLayer` marca `ScreenDebugCanvas` como não-esticado.
- **Invariante #6** revisado no `CLAUDE.md` após archive.
- **`:games:pong`, `:games:snake`, `:games:tictactoe`** — `scene.json`/scripts de HUD migrados para design-space (define/herda `designSize`). `:games:hello-world` permanece inalterado.
- **Render backends (`:engine-skiko`, `:engine-lwjgl`)** — sem mudança de API; ambos já honram escala na transform stack para `drawText` (verificado).
- **Testes** — novos casos para a stretch transform (escala/centralização por aspect, ausência de pan), reflow do anchor layout em design-space sob resize, hit-test consistente, e imunidade da `DebugLayer`.
- **`ROADMAP.md`** — entrada do débito "HUD screen-space não acompanha o resize" é fechada.
