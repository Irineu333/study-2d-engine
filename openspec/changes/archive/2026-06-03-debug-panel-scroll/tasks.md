## 1. SPIs em `:engine` (primitivas)

- [x] 1.1 Adicionar `pushClip(rect: Rect)` / `popClip()` ao `Renderer` (engine/render/Renderer.kt) com KDoc: pilha LIFO, `rect` sob o transform corrente, interseção (aninha), reset no frame boundary, `IllegalStateException` em pop vazio.
- [x] 1.2 Adicionar `val scrollDelta: Vec2` e `var scrollConsumed: Boolean` (default no-op getter/setter, igual a `mouseDragConsumed`) ao `Input` (engine/input/Input.kt) com KDoc: zerados por tick, `y` positivo = scroll-down.

## 2. Backend Skiko (`:engine-skiko`)

- [x] 2.1 `SkikoRenderer.pushClip` = `canvas.save()` + `canvas.clipRect(...)`; `popClip` = `canvas.restore()`; depth counter para `IllegalStateException` em pop vazio; `unbind()` exige clip stack vazia.
- [x] 2.2 `SkikoInput`: acumulador de roda `@Volatile`/atômico; `onAwtMouseWheel(MouseWheelEvent)` somando `preciseWheelRotation`; drenar em `beginTick()` para `scrollDelta`; resetar `scrollDelta`/`scrollConsumed` por tick; armazenar `scrollConsumed`.
- [x] 2.3 `SkikoHost`: registrar `MouseWheelListener` roteando para `SkikoInput`.

## 3. Backend LWJGL (`:engine-lwjgl`)

- [x] 3.1 `LwjglRenderer.pushClip` = `nvgSave()` + `nvgIntersectScissor(...)`; `popClip` = `nvgRestore()`; depth counter para pop vazio; alinhar com a disciplina save/restore do `pushTransform`.
- [x] 3.2 `LwjglInput`: acumulador de roda; drenar em `beginTick()` invertendo o sinal de `yoffset` do GLFW (wheel-up positivo → `y` negativo); resetar `scrollDelta`/`scrollConsumed` por tick; armazenar `scrollConsumed`.
- [x] 3.3 `LwjglHost`: registrar `glfwSetScrollCallback` roteando para `LwjglInput`.

## 4. Corpo rolável na base `ScreenDebugWidget`

- [x] 4.1 Separar medição: subclasse reporta `contentExtent` (altura intrínseca de todas as linhas); base deriva `viewport.y = min(contentExtent.y, maxBodyHeight)`; `contentSize()` passa a medir o viewport (não o conteúdo completo).
- [x] 4.2 Estado único `scrollOffset: Float`; clamp on-read a `0..max(0, contentExtent.y - viewport.y)` contra o frame corrente; nenhum pixel guardado.
- [x] 4.3 Composição de desenho: envolver o `drawDebug` da subclasse em `pushClip(viewportRect)` → `pushTransform(0, -offset)` → `drawDebug` → `popTransform` → `popClip`.
- [x] 4.4 Scrollbar: desenhar track + grabber (derivados de `contentExtent`/`viewport`/`offset`) só quando `contentExtent.y > viewport.y`, no edge direito; sem reservar espaço quando não rolável.
- [x] 4.5 Drag do grabber: hit-region do retângulo do grabber reaproveitando o polling de press/hold/release do header; escreve `offset` proporcional ao ponteiro no track; consome `mouseDragConsumed`; distinguir do drag de header.
- [x] 4.6 Expor `applyScroll(deltaY: Float)` (e a info de "viewport contém o ponteiro" / "é rolável") para o roteamento em `hitTestUI`.

## 5. Roteamento de scroll em `SceneTree` / `DebugRegistry`

- [x] 5.1 Resetar `input.scrollConsumed` no início de `hitTestUI` (junto com `mouseClickConsumed`/`mouseDragConsumed`).
- [x] 5.2 Em `hitTestUI` (ou irmão `hitTestScroll` chamado no mesmo ponto), resolver `topPanelAt(pointer)`; se for `ScreenDebugWidget` rolável com ponteiro no viewport e `scrollDelta` não-zero, chamar `applyScroll(scrollDelta.y)` e setar `scrollConsumed = true`.

## 6. Inspector e painéis (remover truncamento, hit-test com offset)

- [x] 6.1 `SceneTreeWidget.computeLayout`: remover a lógica `… (+N more)`; layout passa a conter todas as linhas (a base limita o viewport).
- [x] 6.2 `SceneTreeWidget.nodeAt`: aplicar `-scrollOffset` ao `y` inicial para o hit-test casar com o desenho.
- [x] 6.3 `NodeInspectorWidget.computeLayout`: remover a lógica de truncamento (herda scroll da base).

## 7. Testes

- [x] 7.1 `:engine` (ou backend): pilha de clip — clip restringe draws, clips aninhados intersetam, pop vazio lança, intercalação clip+transform restaura corretamente.
- [x] 7.2 `SkikoInput`/`LwjglInput`: `scrollDelta` observável por exatamente um tick, zero sem roda, sinal correto (GLFW invertido), `scrollConsumed` reseta por tick.
- [x] 7.3 Base de scroll: offset clampa aos limites; resize/redock re-clampa sem ramo especial; grabber proporcional à fração visível; sem scrollbar quando cabe.
- [x] 7.4 Roteamento: roda sobre painel rolável consome (`scrollConsumed=true`) e rola; roda fora de painel passa (`scrollConsumed=false`); gameplay honrando `scrollConsumed` não reage à roda consumida.
- [x] 7.5 `SceneTreeWidget`: clicar linha rolada-para-vista seleciona o nó sob o ponteiro; localizar e atualizar/remover testes que afirmem `… (+N more)`.

## 8. Verificação e demos

- [x] 8.1 Rodar a suíte completa (`:engine`, `:engine-skiko`, `:engine-lwjgl`) e o build.
- [x] 8.2 Verificar manualmente no entrypoint Skiko de `:games:demos`: árvore grande no Inspector rola por roda e por grabber, clip suave nas bordas, reajuste ao redimensionar a janela.
- [x] 8.3 Verificar o mesmo no entrypoint LWJGL (`runLwjgl`) — sentinela do invariante #4.
