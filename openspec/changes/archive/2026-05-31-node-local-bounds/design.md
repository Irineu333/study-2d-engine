## Context

Hoje a engine não tem noção uniforme de extensão espacial. `Node2D` é transform puro (pos, rot, scale). Cada folha resolve tamanho do seu jeito e em frame diferente:

- `Panel`/`ColorRect` carregam `size` e desenham `Rect(ZERO, size)` (top-left no origin local).
- `Circle2D` carrega `radius` e desenha centrado no origin.
- `Button.screenRect()` calcula um rect em world/screen aplicando `scale` mas **ignorando `rotation`** (bug latente).
- `Shape2D.bounds(world, offset)` devolve **AABB em world-space** para a broad-phase da física.
- `Label` mede texto via `Renderer.measureText` apenas dentro do `onDraw`.

Um futuro editor visual e a próxima change de debug inspector de cena precisam perguntar a *qualquer* `Node2D` — inclusive subclasses de jogo — "qual é a sua caixa?", para desenhar highlight, marquee e zoom-to-fit. Esta change entrega o primitivo; não há consumidor visual aqui (prova por testes).

Restrições: invariante #1 (comportamento por herança), #2 (`:engine` sem framework de render), #4 (Renderer/Input/GameHost SPIs com Skiko default + LWJGL sentinela), #6 (`CanvasLayer` quebra a view transform).

## Goals / Non-Goals

**Goals:**
- Query polimórfica e uniforme de extensão em `Node2D`, overridável por herança.
- Separar com clareza: extensão local **orientável** (para OBB justo) vs agregados **AABB** (marquee/zoom).
- Cobrir todas as folhas shipped, incluindo `Label` (correção, não aproximação) e colliders.
- Manter `:engine` puro; backends implementam a medição de texto off-frame.

**Non-Goals:**
- Gizmo / debug inspector de cena (change seguinte).
- Edição, handles de resize/rotate, seleção real, hit-test geométrico preciso.
- Editor visual completo.
- Substituir `Shape2D.bounds(world, offset)` da física (permanece como está).

## Decisions

### D1 — `localBounds(): Rect?` na base `Node2D`, não interface `Bounded`

`open fun localBounds(): Rect?` com default `null` na classe base; cada folha overrida. Alternativa considerada: `interface Bounded`. Escolhido método na base por coerência com o invariante #1 (comportamento por herança) e com a convenção "folhas shipped são `open`". `null` carrega semântica real — **node de transform puro (pivô) não tem extensão e não é selecionável por caixa**, o que é informação útil ao editor, não ausência de dado.

### D2 — Devolver `Rect` completo (origin + size), sem impor convenção de origem

`localBounds` devolve o `Rect` que o node **de fato desenha**, no frame local:

```
Panel / ColorRect    → Rect(ZERO, size)        (top-left no origin)
Circle2D             → Rect(-r, -r, 2r, 2r)    (centrado)
RectangleShape2D     → Rect(-size/2, size)     (centrado — convenção da física)
CircleShape2D        → Rect(-r, -r, 2r, 2r)    (centrado)
```

Convenções de origem **divergem** entre física (centrado) e UI/visual (top-left). A API não normaliza — por isso o retorno é `Rect` (origin+size), não `Vec2 size`. Forçar uma convenção quebraria o casamento com o `onDraw` real de cada node.

### D3 — `worldBounds`/`treeBounds` derivados e `final`; OBB fica fora do contrato

```
worldBounds(): Rect?   = AABB( world().apply(c) for c in localBounds().corners() )
treeBounds(): Rect?    = união dos worldBounds de self + descendentes; para em CanvasLayer
```

Só `localBounds` é overridável; os derivados são `final` (caem de graça por composição). **Distinção central:**

- `localBounds()` + `world()` → 4 cantos rotacionados = **OBB justo** → o editor desenha o highlight orientado de **um** node. O OBB **não** é um `Rect` (Rect é eixo-alinhado), então não vira método — o editor compõe `localBounds` com `world()` diretamente.
- `worldBounds`/`treeBounds` são **AABB** por definição. Unir caixas é inerentemente eixo-alinhado (não há "unir orientação"), e é exatamente o que marquee, zoom-to-fit e caixa-de-grupo querem.

`treeBounds` **para em `CanvasLayer`** (invariante #6): descendentes dentro de um `CanvasLayer` vivem em screen-space e não compartilham a cadeia de transform world.

### D4 — Primitivos de math: `Transform.apply` + `Rect.corners`

```
Transform.apply(p: Vec2): Vec2  = position + rotate(scale ⊙ p, rotation)
Rect.corners(): List<Vec2>      = [TL, TR, BR, BL]
```

`worldBounds` deriva via `AABB( world().apply(c) for c in localBounds().corners() )`. O `obbCorners`/`worldCorners` privados de `Shape2D.kt` passam a reusar `Transform.apply` (mesma fórmula hoje duplicada inline com `rotate()`), reduzindo duplicação. Refactor de baixo risco — comportamento idêntico, coberto pelos testes de colisão existentes.

### D5 — Ponte de colisão por recursão, sem código especial no objeto

`CollisionShape2D` já é `Node2D`. Adiciona-se `Shape2D.localBounds(): Rect` (frame local, **sem scale** — o scale entra depois via `world()`) e `CollisionShape2D.localBounds()` devolve o rect local da sua `Shape2D`. Como `worldBounds`/`treeBounds` são recursivos, a caixa de qualquer `CollisionObject2D` (`Area2D`, bodies) cai **automaticamente** via `treeBounds`, sem método novo em `CollisionObject2D`. `Shape2D.bounds(world, offset)` (AABB world para broad-phase) é ortogonal e permanece.

### D6 — `TextMeasurer` SPI exposta em `SceneTree` para o caso `Label`

`localBounds()` é query pura — sem `Renderer` na assinatura (o editor consulta bounds fora de um frame de draw). Todas as folhas calculam de dados puros, **exceto `Label`**, que precisa de métrica de fonte. `SceneTree` não guarda `Renderer` (entra por frame em `render(renderer)`).

Alternativas consideradas:
- **(a) cache-at-draw**: `Label` memoriza o tamanho no `onDraw`; `localBounds` devolve o cache. Barato, zero SPI nova — mas `null`/stale antes do 1º draw (node fora de tela ou nunca desenhado fica sem bounds).
- **(c) guardar `Renderer` no tree**: reaproveita `measureText`, mas guardar a superfície de draw só para medir é smell e fura "render entra por frame".
- **(b) escolhida** — mini-SPI `TextMeasurer` (subset estável: métrica de fonte) em `:engine`, exposta como campo em `SceneTree`, setada no startup pelo host. `Label.localBounds()` mede via `tree.textMeasurer` → bounds **sempre correto**, independente de draw. Ambos backends já têm `measureText`; só expõem por caminho não-frame. Custo: nova mini-SPI + wiring no startup dos dois backends. Escolhido por correção arquitetural (alinha com `Font` como recurso mensurável fora do pass de render, estilo Godot).

`Label.localBounds()` retorna `null` quando `tree`/`textMeasurer` não está disponível (node destacado da árvore) — degradação coerente com a semântica `null = sem extensão conhecida`.

## Risks / Trade-offs

- **Mudança semântica em `Button.screenRect()`** (passa a respeitar `rotation`) → pode alterar hit-test de UIs que dependiam — sem saber — do comportamento antigo. Mitigação: botões shipped não são rotacionados; cobrir com teste a equivalência sob rotation = 0 e o novo comportamento sob rotation ≠ 0.
- **`treeBounds` em árvore grande** recomputa por chamada (sem cache) → custo O(n) na subárvore. Mitigação: é query de editor/debug sob demanda, não per-frame de gameplay; cache fica para change futura se necessário.
- **Refactor de `obbCorners`/`worldCorners`** mexe em caminho quente da física → risco de regressão sutil de colisão. Mitigação: refactor preserva fórmula; rodar suíte de física existente; manter como passo isolado.
- **`Label.localBounds()` dependente de `textMeasurer`** → `null` se o host não fizer o wiring. Mitigação: wiring no startup dos dois backends + teste com measurer fake garantindo bounds não-nulo quando presente.
- **Acoplamento `Label` → `tree`**: introduz dependência de `Label` no `SceneTree` para medir. Aceitável — `Node` já referencia `tree`; nenhum tipo de render vaza para `:engine`.
- **⚠️ Divergência de ancoragem `localBounds` (centrado) vs física (corner-anchored) para `RectangleShape2D` — bug latente.** D2 escolheu `RectangleShape2D.localBounds() = Rect(-size/2, size)` (centrado) justificando como "convenção da física" — mas essa premissa é **factualmente incorreta**: o código de física trata o retângulo como **corner-anchored** (ocupa `[position, position + size·scale]`; ver `obbCorners` com cantos `(0,0),(w,0),(0,h),(w,h)` e o teste `RectangleShape2D bounds reflect transform scale` que assere `origin == position`). `CircleShape2D`, por outro lado, é centrado nos dois mundos. Consequência: para o mesmo retângulo, `worldBounds()`/`treeBounds()` (derivados do `localBounds` centrado) e `broadPhaseBounds()` (física) produzem AABBs **deslocadas em `size/2`**. O risco está **dormente** nesta change (sem consumidor visual), mas dispara no inspector/gizmo futuro: o highlight/marquee/zoom de um collider retangular ficaria meio-tamanho fora do collider real e **discordaria do `ColliderWidget`** (que usa `broadPhaseBounds`). A suíte aqui **não pega** isso: o único teste de recursão sobre `CollisionObject2D` usa um **círculo** (centrado nos dois mundos), mascarando o caso do retângulo. Decisão deliberada: manter `localBounds` centrado conforme a spec (alinha com a convenção Godot, onde `RectangleShape2D` é centrado no dono) e **corrigir a física para também centrar o retângulo** numa change dedicada e isolada (`center-rectangle-shape`), validada por todos os jogos e demos. Até lá, consumidores de colliders devem ciente da divergência.

  **✅ RESOLVIDO por `center-rectangle-shape`.** A física do retângulo passou a ser centrada na origem local (`obbCorners`/`worldCorners` agora emitem cantos `(±w/2, ±h/2)`; os dois sweeps axis-aligned deslocam a origem efetiva em `-size/2·scale`). Com isso, para o mesmo `RectangleShape2D`, `worldBounds()`/`treeBounds()` e `broadPhaseBounds()` produzem AABBs **idênticas** — divergência fechada na raiz. Regressão garantida pelo teste `rectangle collider worldBounds agrees with broadPhaseBounds` em `ShapeLocalBoundsTest` (cobre o caso do retângulo que o teste de círculo mascarava).
