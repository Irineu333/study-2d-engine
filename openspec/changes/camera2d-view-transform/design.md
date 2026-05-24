## Context

A change `2026-05-24-godot-style-foundation` introduziu o conceito `Camera2D` + `Scene.viewport`, mas o `Renderer` continua sendo apenas um wrapper sobre coordenadas de surface — o backend desenha em pixels e ninguém aplica a câmera como projeção. O resultado prático foi:

- `pong/scene.json` declara `Camera2D.bounds = (0,0,800,600)` e `current=true`.
- `pong_scene.py._layout()` reposiciona paddles/walls/goals/score a cada resize com base em `scene.size` (surface px).
- `paddle.py._physics_process` faz `play_field_height = scene.viewport.size.y` — pega `Camera2D.bounds.size.y = 600` *hardcoded*.

Em qualquer janela que não seja exatamente 800×600 (resize manual, monitor HiDPI), as duas convenções divergem: o paddle clampa em y=600 mesmo na janela de 900 px; o `centerLine` (`Line2D` com pontos literais `(400,0)→(400,600)`) fica fora do centro porque nada o reposiciona; demos com pivot `Vec2(400, 300)` (`TransformOrbitDemo`, `ScaleHierarchyDemo`) também perdem o centro.

Esta change completa a semântica que a foundation começou: a única responsabilidade do `Renderer` na presença de `Camera2D.current = true` passa a ser **projetar o mundo virtual sobre a surface**. Mundo lógico volta a ser fixo (800×600 em Pong), e o paddle clampar em 600 deixa de ser bug e vira contrato.

A engine continua sendo Skiko-first (`:engine-skiko`) com Compose como segundo backend (`:engine-compose`); ambos precisam respeitar a mesma SPI.

## Goals / Non-Goals

**Goals:**
- Fazer `Camera2D` ter efeito visual real, não apenas informativo.
- Garantir que qualquer cena com `Camera2D.current=true` tenha posicionamento estável sob resize/HiDPI — bug some por construção, não por reposicionamento manual de cada nó.
- Manter cenas sem `Camera2D` (tictactoe) com comportamento idêntico ao atual (identity transform = pixels = mundo).
- Adicionar API simétrica `Camera2D.screenToWorld` / `worldToScreen` para que o próximo jogo com clique-no-mundo tenha um caminho documentado.
- Reduzir a complexidade do `pong_scene.py`: posições passam a ser declarativas em `scene.json`, não computadas em runtime.

**Non-Goals:**
- Zoom animado, multiple cameras com transição/blend, follow-camera (perseguir um nó).
- `CanvasLayer` à la Godot (HUD-as-scene-graph).
- Reescrita da semântica de `Camera2D` para `position + zoom` ortodoxo de Godot — mantemos `bounds: Rect` (mais simples para nível didático atual).
- Aplicar `rotation` nas primitivas visuais (limitação herdada da foundation, fica para change futura).
- Trocar a cor de letterbox (continua a `Renderer.clear`, hoje `Color.BLACK`); configuração via `GameConfig` fica como TODO documentado.

## Decisions

### D1. SPI do `Renderer`: `pushTransform(translation, scale)` + `popTransform()`

**Alternativas consideradas:**
- `Renderer.withTransform(t, s) { … }` higher-order encapsulando save/restore.
- `Renderer.setViewTransform(Mat3)` (single-slot, sem stack).
- `Renderer.pushTransform(Mat3)` recebendo matriz arbitrária.

**Escolha:** `pushTransform(translation: Vec2, scale: Vec2)` + `popTransform()` (LIFO, espelhando `Canvas.save/restore`).

**Por quê:**
- Casa direto com `canvas.save() + translate + scale / canvas.restore()` em Skia, e com `DrawScope` save/restore em Compose — sem matemática extra na engine.
- Não precisamos de rotação na view transform (Camera2D não rotaciona neste change). Restringir o tipo a `(translation, scale)` documenta isso na própria SPI; quando rotação entrar (futuro), a SPI evolui com método novo, sem confundir o caso atual.
- Higher-order `withTransform { … }` forçaria inversão de controle no `Scene.render` (que hoje é um while-loop). Stack explícito mantém o callsite legível.
- Single-slot `setViewTransform` impede aninhamento (debug overlay world-pass + scene render aplicariam transformações sobrepostas). Não vejo uso imediato para profundidade >1, mas LIFO custa o mesmo.

### D2. `Scene.render` é dono do push/pop da câmera

A view transform é empilhada por `Scene.render` no início do tree-walk e desempilhada num `finally` ao fim. Backends e jogos não enxergam a operação.

**Alternativas consideradas:**
- Push/pop no `GameLoop.tick` (entre `scene.physicsProcess` e `scene.render`).
- Push/pop no `GameHost` (`SkikoHost`/`ComposeHost`).
- Camera2D auto-aplica via `onEnter`/`onProcess`.

**Por quê na Scene:**
- `Scene.render` já é o ponto centralizador do tree-walk de draw. Mover a responsabilidade para fora vazaria detalhe de câmera para o `GameLoop` (que não sabe nada de coordenadas) ou para os hosts (que teriam que duplicar a lógica em cada backend novo).
- `Camera2D` como ator ativo (auto-aplicar no `onEnter`) cria ordering frágil — múltiplos `Camera2D` com `current=true` numa árvore teriam que se desativar mutuamente, e a invalidação `tree mutation → current camera changed` deixa de ser uma busca lazy.
- Mantém o invariante "render walk é determinístico e centralizado em `Scene.render`" da foundation.

### D3. Política padrão = `FIT` (letterbox), com `FILL`/`STRETCH` como alternativas

`Camera2D.aspectMode: AspectMode = AspectMode.FIT` por default.

| Modo | Scale | Quando usar |
|---|---|---|
| `FIT` | `min(sx, sy)` | Garante mundo todo visível, sem distorção; barras nas margens sobressalentes. **Default.** |
| `FILL` | `max(sx, sy)` | Preenche tudo, cortando o eixo sobressalente. Útil quando "bordas" não importam. |
| `STRETCH` | `(sx, sy)` independentes | Distorce. Útil só pra UI fluida, raramente pra gameplay. |

`FIT` é o que faz Pong "parecer Pong" em qualquer monitor: a bola não fica oval, o paddle não vira retângulo achatado. Trade-off explícito: surge espaço morto nas laterais quando o aspect ratio do monitor não bate com 4:3 do mundo virtual — preenchido com a cor de `Renderer.clear` (preto hoje).

### D4. Sem current Camera2D ⇒ identity transform (sem push)

Quando `Scene.render` não encontra um `Camera2D.current = true` (ou o encontra com `bounds.size` ≤ 0), ele NÃO chama `pushTransform`. O `_draw` walk roda contra a identidade — comportamento idêntico ao pré-change. Isso preserva tictactoe sem cirurgia.

**Por quê estritamente "não chamar":**
- Empurrar identity (`Vec2.ZERO`, `Vec2(1f, 1f)`) funcionaria igual, mas custa um `save/restore` por frame na Skia para zero benefício, e adiciona ruído no tracing.

### D5. `Camera2D.screenToWorld`/`worldToScreen` são pure functions de `(bounds, sceneSize, aspectMode)`

Não mantêm estado, recebem `sceneSize` como parâmetro. Razão: `Scene.size` muda a cada `resize`, mas a Camera2D pode existir antes da primeira `resize`. Tomar `sceneSize` como argumento explicita a dependência e torna a função 100% testável sem precisar de uma `Scene` instanciada.

Trade-off: callers que querem o caso comum precisam fazer `camera.screenToWorld(p, scene.size)`. Aceitável — é uma chamada por quadro no máximo, no input handler.

Math de `FIT` (representativa):
```
scale = min(sceneSize.x / bounds.size.x, sceneSize.y / bounds.size.y)
offset = (sceneSize - bounds.size * scale) / 2          // centraliza
screen = offset + (world - bounds.origin) * scale
world  = bounds.origin + (screen - offset) / scale
```

Fallback de divisão por zero: quando `bounds.size.x <= 0` ou `bounds.size.y <= 0`, ambas as funções retornam o argumento sem alteração (identity). Não loga — pode acontecer no primeiro frame antes do Camera2D ser configurado pelo jogo.

### D6. `renderDebugOverlay` gerencia internamente as duas passes

A função `renderDebugOverlay(renderer, scene)` continua sendo chamada UMA vez por frame pelos hosts (`SkikoHost`, `ComposeHost`), após `loop.tick(...)` e antes de `renderer.unbind()`. A função internamente faz:

1. Se `Debug.colliderVisualization`:
   - Resolve current camera + computa view transform (mesma lógica que `Scene.render`).
   - Se há transform a aplicar: `renderer.pushTransform(...)`.
   - Desenha bounds dos colliders em coords mundiais.
   - Se empurrou: `renderer.popTransform()`.
2. Se `Debug.showFps`:
   - Desenha o FPS em coords de surface (sem push, identity).

**Alternativa rejeitada:** quebrar em `renderColliderOverlay(renderer, scene)` (chamada DENTRO de `Scene.render`) + `renderHudOverlay(renderer, scene)` (chamada FORA, pelos hosts). Custaria mais cirurgia nos hosts e nos specs já estabilizados, e o "MUST não duplicar lógica nos hosts" do dx-tooling vira mais frágil.

### D7. Migração de Pong: posições absolutas no JSON, sem `_layout`

`pong/scene.json` ganha posições world-space concretas para cada nó (paddles, walls, goals, ball, scores, centerLine). O `pong_scene.py._layout(width, height)` é removido (mundo é fixo). O script reduz-se a `_ready` que faz o wiring do `Signal.scored` do ball nos scoreboards.

**Por quê não simplesmente atualizar o `_layout` para usar `viewport`:**
- `_layout` lê `scene.size` e calcula posições em pixels — toda essa matemática vira lixo quando o renderer já projeta. Reescrever para usar `viewport.size` seria atalhar bug-fix mas manter o anti-pattern "cena é configurada em runtime quando sceniografia é estática".
- Posições autorais no JSON são mais editáveis no futuro editor visual (a meta da engine), e ainda servem como documentação executável da intenção do designer.

### D8. Migração de Demos: SEM `Camera2D` (revisado em apply)

Inicialmente o design previa adicionar `Camera2D` no `DemoSwitcherScene` para que `Vec2(400,300)` virasse honestamente o centro do mundo virtual. Validação manual durante o apply mostrou que isso introduzia regressão clara:

- `CollisionStressDemo`, `SpawnerDemo` e `RotatingBoxDemo` lêem `scene.width`/`scene.height` como mundo lógico (limites de bouncing, anchors de HUD right-aligned, spawn aleatório). Quando a câmera projeta `bounds = (0,0,800,600)` sobre `scene.size = 1600×900`, as posições de ball clampam em `x = 1600` *no mundo*, que renderiza fora do retângulo letterboxed visível. Em janelas pequenas (`scene.size = 400×400`), clampam em `x=400` que vira `x≈200` na surface — a "linha virtual" do feedback do usuário.
- `SpawnerDemo` também usa `input.pointerPosition` (surface px) como coordenada de mundo para spawnar a ball, o que com câmera ativa exige `screenToWorld` em todo callsite.

Decisão revisada: demos rodam em surface-px (sem `Camera2D`), pelo fallback identity de `Scene.render`. A justificativa é semântica: demos são exercícios de física/colisão que documentam o tree-walk e cache de world transform; o "palco" deles é honestamente a surface, não um mundo lógico. Quem precisa de mundo lógico fixo é jogo (Pong tem 800×600 porque o gameplay assim exige); demos não têm gameplay.

Trade-off aceito: `TransformOrbitDemo` e `ScaleHierarchyDemo` continuam usando `Vec2(400, 300)` literalmente como pivot. Isso fica visualmente "deslocado" do centro em surface ≠ 800×600 — aceitável porque o ponto desses demos é a composição de transforms hierárquicos, não o framing absoluto. Refator futuro pode usar `scene.size / 2` para centralizar honestamente.

**Por quê não Camera2D centralizada no switcher:**
- Demos não compartilham um mundo lógico — eles compartilham um palco visual. Camera2D forçaria todos a viverem em 800×600, mas a vantagem disso é zero para gameplay-less demos.

### D9. Tictactoe migra para Camera2D (revisado em apply)

Versão original do design dizia "tictactoe não muda". Validação manual mostrou um bug visível: ao redimensionar a janela, o `Board.onResize` reescalava cellSize e re-centrava o board, mas `StatusText` centralizava por `scene.width` em surface px — uma centralização "honesta" para o estado pré-câmera mas que, junto com a escala do board, dava a sensação de UI desconexa (texto não acompanhava o tabuleiro).

Decisão revisada: tictactoe ganha `Camera2D` 600×600 FIT. Vantagens:
- Cena inteira (board + status) escala como uma só sob resize, sem precisar de `onResize` manual.
- Posições viram declarativas: `Board.origin`, `Board.cellSize`, posições do `Camera2D` e do status são constantes no `init` do `TicTacToeScene`.
- Primeira validação de `Camera2D` rodando no backend Compose (Pong roda em Skiko; sem o tictactoe, o stack `pushTransform/popTransform` do `ComposeRenderer` ficaria sem smoke test end-to-end).
- `Board.onProcess` força a aparecer o caso de uso de `Scene.screenToWorld` — converte `input.pointerPosition` (surface px) para coordenada de mundo antes do hit-test contra `cellRect`. Isso valida a API e a documenta com um caller real.

Trade-off: jogos compostos quase inteiramente por HUD (caso clássico do tictactoe) agora pagam o letterbox em monitores não-quadrados. Aceitável dado o ganho de consistência e o reuso da projeção para input.

## Risks / Trade-offs

- **Risk:** `renderDebugOverlay` precisa replicar a matemática de view transform de `Scene.render`. **Mitigation:** extrair uma função pure `Camera2D.computeViewTransform(sceneSize): Pair<Vec2, Vec2>?` (translation, scale) que ambos chamem. Vive em `Camera2D` (próximo do `aspectMode` que ela usa). Coberta por teste unitário.

- **Risk:** Imbalanced push/pop crashes o frame inteiro silenciosamente em Skia (canvas state corrompido). **Mitigation:** `SkikoRenderer.unbind()` verifica stack profundidade == 0 e raise `IllegalStateException` se não. Testes unitários cobrem o caso "Scene.render sempre pareia".

- **Risk:** Mouse input fica em coords de surface — qualquer game novo que faça `is BoxCollider`-hit-test contra um `bounds()` mundial vai errar. **Mitigation:** `Camera2D.screenToWorld` entra preventivamente nesta change, com teste de round-trip. Não temos consumer imediato (tictactoe não usa colliders e tem Compose sem Camera2D), mas a API fica registrada e o próximo jogo encontra o caminho documentado em CLAUDE.md.

- **Risk:** Surface muito pequena (ex.: usuário arrasta a janela para 100×100) → letterbox bars dominam a tela e a área jogável fica ilegível. **Mitigation:** problema cosmético, não bug. Aceitável para o nível didático. Futuro: GameConfig poderia ter `minimumSize` para o JFrame não permitir resize abaixo de um threshold.

- **Risk:** Pong `_layout` removido implica que paddles começam em posições absolutas do JSON; reposicionamento de partida (após ponto, posição inicial) depende do `_ready` do paddle e da lógica de reset do ball. **Mitigation:** ball.py já tem `_reset(side)` que usa `fieldCenter` declarado no JSON. Paddles voltam a posição inicial quando o JSON é carregado; durante partida ficam onde o jogador deixou — mesmo comportamento do Pong original.

- **Risk:** Compose `DrawScope` save/restore tem semântica ligeiramente diferente da Skia (mecânica `withTransform`-style em vez de `save/restore` puros). **Mitigation:** documentado no spec de compose-runtime; implementação pode usar a abstração nativa que melhor preserve LIFO. Teste comportamental (push/draw/pop/draw posições corretas) cobre o contrato sem prescrever a mecânica.

- **Trade-off:** A política FIT introduz letterbox bars pretas em monitores ultrawide rodando Pong. Alternativa seria `FILL` default — mas isso cortaria gameplay (paddle pode ficar fora da área visível). FIT é o trade-off correto para um jogo com mundo lógico bounded.

- **Trade-off:** Manter `Camera2D.bounds: Rect` (em vez de migrar para `position + zoom` Godot-ortodoxo) é uma dívida cognitiva — quem vem de Godot espera a API canônica. Custo de migrar agora: refazer specs, scenes, e a math de `worldToScreen`. Adiada para uma change futura quando houver caso real (pan/zoom dinâmico).
