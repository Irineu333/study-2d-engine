## Why

A engine não tem como perguntar a um `Node2D` qualquer "qual é a sua caixa?". Cada folha resolve extensão por conta própria, em frames de coordenada diferentes (`Panel.size` local, `Button.screenRect()` em world/screen ignorando rotation, `Shape2D.bounds()` em AABB world-space), e a classe base não tem noção nenhuma de extensão. Um futuro editor visual e a próxima change de **debug inspector de cena** precisam de uma query polimórfica e uniforme para desenhar caixas de seleção, marquee e "zoom-to-fit" — incluindo sobre `Node2D` que o autor do jogo escreveu por herança.

Esta change entrega **somente o primitivo** (a query de bounds + math de suporte + measurer). O consumidor visual (gizmo/inspector) vem em change separada; a prova aqui é por testes unitários.

## What Changes

- **Novo contrato de bounds em `Node2D`** — três métodos, só o primeiro overridável:
  - `open fun localBounds(): Rect?` — extensão no **frame local**, intrínseca, orientável. `null` = node de transform puro (pivô) sem extensão. Cada folha overrida.
  - `final fun worldBounds(): Rect?` — derivado: 4 cantos de `localBounds` via `world()` → AABB em world-space.
  - `final fun treeBounds(): Rect?` — derivado, recursivo: união dos `worldBounds` de self + descendentes; **para em `CanvasLayer`** (quebra a cadeia de transform). Inerentemente AABB.
- **`localBounds` não impõe convenção de origem** — cada node devolve o `Rect` (origin + size) que reflete o que realmente desenha: `Panel`/`ColorRect` = `Rect(ZERO, size)` (top-left); `Circle2D` = `Rect(-r,-r, 2r,2r)` (centrado); `RectangleShape2D` centrado = `Rect(-size/2, size)`. Por isso devolve `Rect` completo, não `Vec2`.
- **Distinção documentada OBB vs AABB**: `localBounds` + `world()` → caixa **orientada** justa (highlight de um node); `worldBounds`/`treeBounds` são **AABB** (marquee, zoom-to-fit, caixa de grupo).
- **Novos primitivos de math**: `Transform.apply(p: Vec2): Vec2` e `Rect.corners(): List<Vec2>`. `worldBounds` deriva via `AABB( world().apply(c) for c in localBounds.corners() )`. `obbCorners`/`worldCorners` da física passam a reusar `Transform.apply` (reduz duplicação).
- **Overrides nas folhas**: `Panel`, `ColorRect`, `Circle2D`, `Button`, `Label`.
- **`Shape2D.localBounds(): Rect`** (frame local, sem scale) + `CollisionShape2D.localBounds()` devolvendo o rect local da shape. Como `CollisionShape2D` já é `Node2D`, o `treeBounds`/`worldBounds` de qualquer `CollisionObject2D` (Area2D, bodies) cai por recursão, sem código especial. (O `Shape2D.bounds(world, offset)` existente — AABB world-space para broad-phase — permanece.)
- **Nova mini-SPI `TextMeasurer`** em `:engine`, exposta via `SceneTree`, setada no startup pelo host. Resolve o caso do `Label`: `localBounds()` é query pura sem renderer na assinatura, mas medir texto precisa de métrica de fonte. Backends (Skiko, LWJGL) já têm `measureText`; passam a expô-lo também por caminho não-frame, dando bounds correto a `Label` mesmo fora de tela / nunca desenhado.
- **`Button.screenRect()` reescrito** sobre `localBounds()` composto com `world()` — **BREAKING (semântica)**: passa a respeitar `rotation` (hoje aplica só `scale` e ignora rotação).

## Capabilities

### New Capabilities
- `node-local-bounds`: contrato de extensão espacial polimórfica — `Node2D.localBounds`/`worldBounds`/`treeBounds`, convenção de origem por node, semântica OBB vs AABB, parada em `CanvasLayer`, `Shape2D.localBounds` + ponte `CollisionShape2D`, e os primitivos de math `Transform.apply` / `Rect.corners`.

### Modified Capabilities
- `engine-core`: introduz a SPI `TextMeasurer` e o campo `SceneTree.textMeasurer` (nova superfície de SPI em `:engine` puro).
- `ui-foundation`: `Button.screenRect()` passa a derivar de `localBounds()` + `world()` respeitando rotation; `Panel`/`Button` ganham override de `localBounds`.
- `skiko-runtime`: `:engine-skiko` implementa `TextMeasurer` (métrica de fonte off-frame via Skia), wired no startup do host.
- `lwjgl-runtime`: `:engine-lwjgl` implementa `TextMeasurer` (métrica via NanoVG off-frame), wired no startup do host.

## Impact

- **`:engine`**: `Node2D`, `Transform`, `Rect`, `Shape2D`, `CollisionShape2D`, `SceneTree`, nova interface `TextMeasurer`, folhas `Panel`/`ColorRect`/`Circle2D`/`Button`/`Label`. Refactor interno em `Shape2D.kt` (obbCorners/worldCorners).
- **`:engine-skiko`** e **`:engine-lwjgl`**: implementação de `TextMeasurer` + wiring de startup do `GameHost`/config.
- **Invariantes respeitados**: #1 (`localBounds` open = comportamento por herança), #2 (`TextMeasurer` é SPI em `:engine`, impl nos backends — sem vazar tipos de render), #4 (SPIs com múltiplas implementações; ambos backends implementam), #6 (`treeBounds` para em `CanvasLayer`).
- **Sem consumidor visual nesta change**: prova por testes unitários; gizmo/inspector ficam para change futura.
- **Fora de escopo**: gizmo / debug inspector de cena, edição/handles, seleção real, hit-test geométrico, editor visual completo.
