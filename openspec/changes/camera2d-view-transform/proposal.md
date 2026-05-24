## Why

A change `2026-05-24-godot-style-foundation` introduziu `Camera2D` + `Scene.viewport`, mas o `Renderer` continua desenhando direto em pixels da surface — nenhuma transformação de câmera é aplicada. Resultado: `Scene.viewport` (= `Camera2D.bounds`, fixo em `(0,0,800,600)` no `pong/scene.json`) e `Scene.size` (= surface pixels reais, sensível a HiDPI/resize) divergem em qualquer janela ≠ 800×600. Em Pong a centerLine fica deslocada e o paddle só alcança 600 px de altura mesmo numa janela de 900 px; em demos o pivot de `TransformOrbitDemo`/`ScaleHierarchyDemo` em `Vec2(400, 300)` deixa de estar no centro. A abstração `Camera2D` existe hoje só para enganar quem a usa.

Esta change completa a semântica que a foundation começou: `Camera2D` vira **view transform de verdade**. O `Renderer` passa a aplicar `translate + scale` antes do tree-walk de draw, mapeando `Camera2D.bounds` (mundo virtual fixo) na surface (`Scene.size`). Scripts continuam vivendo em coordenadas de mundo; o backend é o único que conhece pixels. Bug some por construção: posições mundiais ficam estáveis sob qualquer resize.

## What Changes

### Renderer SPI ganha transform stack

- **NEW** `Renderer.pushTransform(translation: Vec2, scale: Vec2)` empilha uma transformação afim aplicada a todas as chamadas subsequentes de `draw*`.
- **NEW** `Renderer.popTransform()` restaura o topo anterior da pilha. As pilhas SÃO LIFO e cada `pushTransform` MUST ter um `popTransform` correspondente.
- **NEW** Convention: o estado inicial da pilha em cada `bind()` do backend é identity (sem transformação).

### Scene.render aplica camera transform na raiz

- **MODIFIED** `Scene.render(renderer)` antes de iniciar o tree-walk de `_draw`:
  1. resolve `currentCamera()` e calcula a matriz de view a partir de `(camera.bounds, scene.size, camera.aspectMode)`;
  2. chama `renderer.pushTransform(translation, scale)`;
  3. faz o tree-walk de `_draw`;
  4. chama `renderer.popTransform()`.
- Quando não há `Camera2D` com `current=true`, `Scene.render` MUST não empilhar transformação (preserva comportamento atual de identity = pixels = mundo).

### Camera2D ganha aspect mode + helpers de coordenada

- **NEW** `Camera2D.aspectMode: AspectMode` (enum `FIT`, `FILL`, `STRETCH`). Default = `FIT`.
- **NEW** `Camera2D.screenToWorld(screenPosition: Vec2, sceneSize: Vec2): Vec2` — converte coordenadas de surface (pixels) para coordenadas de mundo, respeitando `bounds` e `aspectMode`. Necessário para input (mouse).
- **NEW** `Camera2D.worldToScreen(worldPosition: Vec2, sceneSize: Vec2): Vec2` — converte coordenadas de mundo para surface (pixels). Inverso de `screenToWorld`.
- **MODIFIED** `Camera2D.bounds` com `size` zero ou negativo MUST cair em fallback identity (log de aviso, sem divisão por zero).

### Política FIT (letterbox) é o default

- A política `FIT` calcula `scale = min(scene.width / bounds.width, scene.height / bounds.height)` (zoom uniforme), centra o mundo na surface e deixa barras pretas (cor do `Renderer.clear`) nas margens sobressalentes. Garante que o mundo cabe inteiro sem distorção. É a política aplicada por default a qualquer `Camera2D` criado sem `aspectMode` explícito.
- `FILL` calcula `scale = max(...)` e corta nos eixos sobressalentes.
- `STRETCH` aplica `scaleX` e `scaleY` independentes (distorce).

### Debug overlay separa HUD de world-space

- **MODIFIED** `renderDebugOverlay(renderer, scene)` da `:engine` se divide em dois passes:
  - **World pass** (overlay de colliders): executa DENTRO do view transform, logo após o `_draw` walk e antes do `popTransform`.
  - **HUD pass** (FPS counter): executa FORA do view transform, em pixels da surface, depois do `popTransform`.
- Hosts (`SkikoHost`, `ComposeHost`) continuam chamando `renderDebugOverlay` no mesmo ponto; quem orquestra os dois passes é a engine.

### Backends implementam push/pop

- **NEW** `SkikoRenderer.pushTransform/popTransform` via `canvas.save() + canvas.translate() + canvas.scale() / canvas.restore()`.
- **NEW** `ComposeRenderer.pushTransform/popTransform` via Compose `DrawScope` save layer equivalente.

### Migração Pong

- **MODIFIED** `pong/scene.json`: `centerLine` mantém `points = (400, 0) → (400, 600)` (agora válido como mundo lógico fixo). Paddles `left`/`right`, walls (`topWall`/`bottomWall`), goals (`leftGoal`/`rightGoal`) e scoreboards (`leftScore`/`rightScore`) ganham `transform.position` fixo no scene.json em vez de serem reposicionados por script.
- **MODIFIED** `pong/scripts/pong_scene.py`: o `_layout(width, height)` é removido (mundo é fixo). Restam apenas `_ready` (wiring de signal `scored`) e possivelmente nada mais. Se o script ficar reduzido a wiring, ele permanece para manter o ponto de orquestração explícito.
- **NO CHANGE** `paddle.py` continua usando `scene.viewport.size.y` — agora retorna `600` *intencionalmente* (mundo lógico fixo), e a câmera é quem escala pra surface. Bug some sem trocar uma linha do paddle.

### Migração Demos — RETIRADA do escopo

`DemoSwitcherScene` NÃO recebe `Camera2D`. Validação manual mostrou que os demos são exercícios de física/colisão que lêem `scene.size` como mundo lógico (limites de bouncing, anchors de HUD, spawn aleatório); aplicar uma view transform por cima duplica o escalonamento (balls bouncing em metade da surface em janelas pequenas, balls saindo do retângulo letterboxed em janelas grandes). Decisão: demos ficam em surface-px (fallback identity), por design. `Scene.render` sem `Camera2D` preserva o comportamento pré-change, então isso é zero cirurgia.

### Migração tictactoe — REVISÃO no apply (Camera2D incluído)

- `:games:tictactoe` ganha `Camera2D` 600×600 FIT (primeira validação de Camera2D no backend Compose). Cena inteira (board + status text) escala como uma só sob resize. `TicTacToeScene.onResize` removido — posições estáticas no mundo. `Board.onProcess` converte `input.pointerPosition` via `Scene.screenToWorld` antes do hit-test. `StatusText` centraliza por `scene.viewport.size.x`.
- Nova API: `Scene.screenToWorld(p)` / `Scene.worldToScreen(p)` — conveniência que delega para `currentCamera` (identity fallback). Documenta o caminho para conversão de input em jogos com câmera.

## Capabilities

### New Capabilities

(nenhuma — `Camera2D`, `Renderer`, `Scene` e debug overlay já existem nas specs atuais)

### Modified Capabilities

- `engine-core`: `Renderer` SPI ganha `pushTransform`/`popTransform`; `Scene.render` aplica camera transform; `Camera2D` ganha `aspectMode`, `screenToWorld`, `worldToScreen`.
- `dx-tooling`: `renderDebugOverlay` internamente faz dois passes (colliders em world-space via `pushTransform`/`popTransform`; FPS em screen-space). Hosts continuam chamando-o uma vez por frame.
- `skiko-runtime`: `SkikoRenderer` implementa `pushTransform`/`popTransform` via `Canvas.save/translate/scale/restore`.
- `compose-runtime`: `ComposeRenderer` implementa `pushTransform`/`popTransform` via API Compose equivalente.
- `pong-sample`: `pong/scene.json` mantém posições mundiais fixas e centerLine fixa; `pong_scene.py._layout` deixa de existir (mundo é fixo, câmera escala).

## Impact

- **Código tocado:**
  - `:engine` — `Renderer.kt` (push/popTransform na SPI), `Scene.kt` (aplica transform no `render`), `Camera2D.kt` (aspectMode + screenToWorld/worldToScreen), `Debug.kt`/`renderDebugOverlay` (split world/HUD).
  - `:engine-skiko` — `SkikoRenderer.kt` implementa o stack via Skia `Canvas.save/translate/scale/restore`. `SkikoHost` não muda (debug overlay continua sendo chamado no mesmo ponto).
  - `:engine-compose` — `ComposeRenderer.kt` implementa o stack via `DrawScope` save/restore equivalente. `ComposeHost` não muda.
  - `:games:pong` — `pong/scene.json` recebe posições absolutas fixas; `pong/scripts/pong_scene.py` reduz `_layout` a no-op ou remove o método.
  - `:games:demos` — **nenhuma mudança de código** (decisão revisada durante apply; ver seção "Migração Demos — RETIRADA do escopo" e design D8 revisado).
- **Documentação:** `CLAUDE.md` ganha nota curta na seção Coding Conventions explicando que `Camera2D.bounds` define o mundo virtual e o renderer projeta na surface respeitando `aspectMode`. `ROADMAP.md` recebe `camera2d-view-transform` em Active.
- **Sem impacto em tictactoe:** ausência de `Camera2D` mantém identity transform.
- **Sem impacto no game loop, physics, scripting Python ou bundle loading.** Mudança é puramente da camada de render + uma migração de cena/scripts.
- **Risco de regressão visual em tictactoe:** mitigado por contrato — `Scene.render` MUST não chamar `pushTransform` quando não há current camera.
