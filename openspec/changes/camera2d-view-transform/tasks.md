## 1. Engine SPI: Renderer transform stack

- [x] 1.1 Estender `Renderer` interface em `:engine` com `pushTransform(translation: Vec2, scale: Vec2)` e `popTransform()` (KDoc descrevendo LIFO + identity inicial por frame).
- [x] 1.2 Adicionar testes em `:engine` para o contrato puro (não-backend): verificar que uma `FakeRenderer` recording implementa o stack corretamente (push/pop balanceados, empty-pop → IllegalStateException).

## 2. Engine: Camera2D aspect mode + helpers de coordenada

- [x] 2.1 Adicionar enum `AspectMode { FIT, FILL, STRETCH }` em `com.neoutils.engine.scene` (mesmo arquivo que `Camera2D.kt` ou separado, à escolha).
- [x] 2.2 Adicionar `@Inspect var aspectMode: AspectMode = AspectMode.FIT` em `Camera2D`.
- [x] 2.3 Implementar `Camera2D.computeViewTransform(sceneSize: Vec2): Pair<Vec2, Vec2>?` (pure, retorna `(translation, scale)` ou `null` quando `bounds.size` é degenerado). Esta é a função compartilhada por `Scene.render` e `renderDebugOverlay`.
- [x] 2.4 Implementar `Camera2D.screenToWorld(screenPosition: Vec2, sceneSize: Vec2): Vec2` honrando `bounds` + `aspectMode`; fallback identity para `bounds.size <= 0`.
- [x] 2.5 Implementar `Camera2D.worldToScreen(worldPosition: Vec2, sceneSize: Vec2): Vec2` honrando `bounds` + `aspectMode`; fallback identity para `bounds.size <= 0`.
- [x] 2.6 Adicionar testes unitários para `computeViewTransform` (FIT/FILL/STRETCH com bounds quadrado, retangular, e sceneSize ultrawide/portrait/degenerado).
- [x] 2.7 Adicionar teste round-trip `worldToScreen(screenToWorld(p, s), s) ≈ p` para os 3 aspect modes.
- [x] 2.8 Garantir que `Camera2D` permanece `@Serializable` com no-args constructor; atualizar registry / serialização se necessário.

## 3. Engine: Scene.render aplica camera transform

- [x] 3.1 Modificar `Scene.render(renderer)` para resolver `currentCamera()` e chamar `camera.computeViewTransform(size)`.
- [x] 3.2 Se o resultado for não-nulo: `renderer.pushTransform(t, s)` antes do tree-walk de `_draw`, e `renderer.popTransform()` num bloco `finally` (mesmo se o walk crashar, o stack fica balanceado).
- [x] 3.3 Se o resultado for `null` (sem camera ou bounds degenerado): rodar o tree-walk sem nenhum push.
- [x] 3.4 Adicionar teste em `:engine` com `FakeRenderer` recording confirmando: (a) ordem das chamadas push → draw* → pop quando há camera; (b) ausência de push/pop quando não há camera; (c) chamadas `_draw` recebem world coords (não pré-projetadas).

## 4. Backend Skiko: pushTransform/popTransform

- [x] 4.1 Implementar `SkikoRenderer.pushTransform(translation, scale)` via `canvas.save() + canvas.translate(t.x, t.y) + canvas.scale(s.x, s.y)`.
- [x] 4.2 Implementar `SkikoRenderer.popTransform()` via `canvas.restore()`.
- [x] 4.3 Adicionar contador de depth + `IllegalStateException` em `popTransform()` quando depth == 0.
- [x] 4.4 Adicionar verificação em `SkikoRenderer.unbind()`: se depth != 0, raise `IllegalStateException` nomeando o desbalanceamento.
- [x] 4.5 Compilar `:engine-skiko`; rodar testes do módulo se houver. (validado em §12.4)

## 5. Backend Compose: pushTransform/popTransform

- [x] 5.1 Implementar `ComposeRenderer.pushTransform(translation, scale)` via `DrawScope` transform API (`translate { scale { … } }` aninhamento, ou manipulação de `DrawTransform` se necessário). Avaliar o approach que melhor preserve LIFO sem inverter controle.
- [x] 5.2 Implementar `ComposeRenderer.popTransform()` restaurando o transform anterior.
- [x] 5.3 Adicionar contador de depth + `IllegalStateException` em `popTransform()` quando depth == 0.
- [x] 5.4 Validar que `GameSurface` continua chamando `composeRenderer.unbind()` (ou equivalente) com stack vazio entre frames.
- [x] 5.5 Compilar `:engine-compose`; rodar testes do módulo se houver. (validado em §12.4)

## 6. DX: renderDebugOverlay dois passes

- [x] 6.1 Em `renderDebugOverlay(renderer, scene)`, antes do collider pass, resolver current camera e `computeViewTransform(scene.size)`.
- [x] 6.2 Se resultado não-nulo e `Debug.colliderVisualization`: `renderer.pushTransform(t, s)` → desenha bounds dos colliders em coords mundiais → `renderer.popTransform()` (em try/finally).
- [x] 6.3 Se resultado nulo e `Debug.colliderVisualization`: desenha colliders sem push (comportamento atual para cenas sem camera).
- [x] 6.4 FPS pass sempre fora de qualquer push (screen-space), depois do collider pass.
- [x] 6.5 Adicionar teste em `:engine` com `FakeRenderer` recording confirmando a ordem: pushTransform (se camera) → drawRect(collider bounds) × N → popTransform (se camera) → drawText(FPS).
- [x] 6.6 Verificar que ambos os hosts (`SkikoHost`, `ComposeHost`) continuam chamando `renderDebugOverlay` exatamente como antes — sem cirurgia neles.

## 7. Pong sample: scene.json com posições absolutas

- [x] 7.1 Atualizar `pong/scene.json` para incluir `"aspectMode": "FIT"` no nó `Camera2D` (mantém `bounds` e `current` como estão).
- [x] 7.2 Atualizar `transform.position` de cada nó (paddles `left`/`right`, walls `topWall`/`bottomWall`, goals `leftGoal`/`rightGoal`, scoreboards `leftScore`/`rightScore`, ball `Ball`) com valores absolutos no mundo 800×600. Confirmar que `centerLine` mantém `points: [(400,0), (400,600)]`.
- [x] 7.3 Atualizar `topWall.size`, `bottomWall.size`, `leftGoal.size`, `rightGoal.size` para os tamanhos finais world-space (ex.: `(800, 8)` para top/bottom, `(8, 600)` para goals) em vez dos placeholders `(10, 10)` atuais.

## 8. Pong sample: pong_scene.py reduzido

- [x] 8.1 Remover `_layout(self, width, height)` de `pong/scripts/pong_scene.py`.
- [x] 8.2 Remover `_process` que polla `width/height` e dispara `_layout` — passa a no-op (ou remover se o script reduzir-se a `_ready`).
- [x] 8.3 Manter `_ready` e `_wire_scoring` (signal wiring é a única responsabilidade do script agora).
- [ ] 8.4 Validar manualmente em janela 800×600 + janela redimensionada (ex.: 1280×900 + ultrawide) que: paddle alcança topo e base do play field, centerLine sempre vertical e centrada, ball sempre quica nas paredes do mundo virtual, letterbox bars aparecem como esperado.

## 9. Demos sample: SEM `Camera2D` (escopo revisado)

Validação manual mostrou regressão clara — demos lêem `scene.size` como mundo (bouncing limits, HUD anchors, spawn) e duplicam o escalonamento quando a câmera projeta. Decisão: demos são exercícios de física/colisão; ficam em pixels de surface por design, sem `Camera2D`. Atualiza design.md D8 e proposal.md (escopo de demos retirado).

- [x] 9.1 ~~Adicionar `Camera2D` em `DemoSwitcherScene`~~ — **revertido**: demos rodam em surface-px (sem câmera). `DemoSwitcherScene.init {}` não adiciona `Camera2D`.
- [x] 9.2 ~~`Vec2(400f, 300f)` como pivot centrado no mundo virtual~~ — **N/A**: pivot continua sendo metade da surface (comportamento pré-change preservado pelo fallback identity em `Scene.render`).
- [x] 9.3 ~~Validar Camera2D nos demos~~ — **N/A**: smoke test manual (12.2) só precisa confirmar paridade com comportamento pré-change.

## 10. Tictactoe sample: migra para Camera2D (escopo revisado)

Validação manual mostrou que o resize do tictactoe escalava só o board mas não o texto (StatusText centralizava por `scene.width` em surface px enquanto Board.layout escalava por `min(width, height)`). Decisão revisada: tictactoe ganha `Camera2D` 600×600 FIT — primeira validação de Camera2D rodando no backend Compose, e cena inteira escala como uma só. Também serve para validar `Scene.screenToWorld` (Board converte pointerPosition antes do hit-test).

- [x] 10.1 ~~Tictactoe sem mudanças~~ — **revertido**: `TicTacToeScene` adiciona `Camera2D` (bounds 600×600 FIT). Layout dinâmico via `onResize` removido; posições agora estáticas no mundo (board centralizado abaixo do status reservado). Smoke test manual: clique nas células, vitória/empate/restart, redimensionamento da janela escala tudo proporcionalmente.

## 11. Documentação e roadmap

- [x] 11.1 Atualizar `CLAUDE.md` na seção Coding Conventions: nota breve explicando que `Camera2D.bounds` define o mundo virtual e o renderer projeta na surface respeitando `aspectMode` (FIT default). Mencionar que jogos sem `Camera2D` ficam em pixels da surface.
- [x] 11.2 Atualizar `CLAUDE.md` na seção Scripting Python: mencionar `screenToWorld`/`worldToScreen` em `Camera2D` para quem precisar de conversão de coords (não usado hoje em Pong/Demos/Tic, mas documentado).
- [x] 11.3 Atualizar `ROADMAP.md`: adicionar `camera2d-view-transform` como Active.
- [x] 11.4 Atualizar stubs Python `.pyi` em `engine-bundle-python/src/main/resources/stubs/engine/` para refletir `Camera2D.aspectMode`, `screenToWorld`, `worldToScreen`.

## 12. Validação final

- [ ] 12.1 Rodar `./gradlew :games:pong:run` em janela 800×600 e em janela redimensionada (1280×900 e ultrawide). Confirmar: paddle alcança topo/base; centerLine sempre vertical centrada; ball nunca escapa do mundo virtual; F1 mostra FPS; F2 mostra collider bounds projetados corretamente sobre o mundo.
- [ ] 12.2 Rodar `./gradlew :games:demos:run` percorrendo 1–5 em janela 800×600 e redimensionada. Confirmar: orbit/scale centralizados; spawner adiciona/remove ok; stress sem regressão; rotating box ok; F2 mostra AABBs projetados.
- [ ] 12.3 Rodar `./gradlew :games:tictactoe:run` em janela 600×600 e redimensionada. Confirmar: zero mudança visual; clique acerta a célula correta; vitória/empate/restart ok.
- [x] 12.4 Rodar `./gradlew test` em `:engine`, `:engine-skiko`, `:engine-compose` — todos verdes.
- [ ] 12.5 Rodar `openspec verify camera2d-view-transform` (via `/opsx:verify`) e endereçar quaisquer descobertas antes do archive.
