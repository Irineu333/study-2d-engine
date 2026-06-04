## Context

Os painéis de debug screen-space herdam de `ScreenDebugWidget` (chrome, drag de header, medição via `contentSize() = header + bodySize()`). O dock empilha painéis pela altura que `contentSize()` reporta. Hoje `SceneTreeWidget.computeLayout` e `NodeInspectorWidget.computeLayout` resolvem overflow **truncando**: o tail que passaria de `maxHeight = surface.y - margin*2 - headerHeight` vira `… (+N more)` e fica inalcançável.

A engine não tem as primitivas para fazer melhor:
- `Renderer` (`engine/render/Renderer.kt`) tem `pushTransform/popTransform` (pilha LIFO sobre o save/restore nativo do backend), mas **nenhum clip**.
- `Input` (`engine/input/Input.kt`) expõe ponteiro, teclas, cliques e os flags de consumo `mouseClickConsumed`/`mouseDragConsumed`, mas **nenhum delta de roda**. Nem `SkikoInput`/`SkikoHost` (AWT) nem `LwjglInput`/`LwjglHost` (GLFW) capturam a roda.

`SceneTree.hitTestUI(input)` roda **antes** de `tree.process(dt)` no `GameLoop.tick`: é onde cliques/drag são consumidos e onde o `DebugRegistry.pressOwner` é eleito via `topPanelAt(pointer)`. Esse ponto é decisivo para o roteamento de scroll (ver Decisões).

Aprendizado da Godot (ScrollContainer/ScrollBar/Range/clip de CanvasItem): importamos a **invariante** "offset e grabber são derivados de `(conteúdo, viewport)`, nunca pixels guardados", e o **transporte de input** no estilo da nossa engine (delta por tick, não evento de botão). Não importamos a estrutura de classes (sem `Range`/`ScrollBar` reutilizável).

## Goals / Non-Goals

**Goals:**
- Corpo rolável correto para painéis de debug screen-space, com scroll por roda do mouse e scrollbar arrastável.
- Recorte por GPU (clip suave) do conteúdo que vaza do viewport.
- Reajuste correto e automático em resize de janela e troca de dock — sem ramo de código especial.
- Primitivas (`Renderer.pushClip/popClip`, `Input.scrollDelta/scrollConsumed`) genéricas e mínimas, implementadas nos dois backends ativos.
- Correção por construção: offset é o único estado guardado; tudo o mais é derivado por frame.

**Non-Goals:**
- Scroll horizontal (painéis crescem só para baixo; largura auto-ajusta à linha mais longa → o problema é 1D).
- Classe `Range`/`ScrollBar` reutilizável ou nodes de UI de scroll para jogos (isto é debug, desenhado imperativamente).
- Scroll aninhado (painéis de debug não aninham regiões roláveis).
- Scroll suave/inercial (delta aplicado direto no tick).
- Auto-scroll até a seleção (adiável; pode virar stretch posterior).

## Decisions

### D1. Clip como primitiva de `Renderer` (não culling manual)

`Renderer.pushClip(rect: Rect)` / `popClip()`, par natural do `pushTransform/popTransform`: pilha LIFO, `rect` interpretado sob o transform corrente, cada push **interseta** com o clip atual (aninha), reset a "sem clip" na fronteira de frame do backend, `popClip()` em pilha vazia lança `IllegalStateException`.

- **Skiko:** `pushClip` = `canvas.save()` + `canvas.clipRect(...)`; `popClip` = `canvas.restore()`. Mesma pilha save/restore que o `pushTransform` já usa.
- **LWJGL/NanoVG:** NanoVG não tem pop de scissor; emula-se com `nvgSave()` + `nvgIntersectScissor(...)` no push e `nvgRestore()` no pop. A interseção dá o aninhamento "de graça".

*Alternativa rejeitada:* culling manual (só emitir linhas inteiras dentro do viewport). Não toca SPI nem backends, mas linhas na borda cortam no meio do texto e não há recorte suave — falha o requisito explícito "clip suave". Além disso, recorte real é uma primitiva reutilizável que outras ferramentas de debug vão querer.

### D2. Roda como `scrollDelta: Vec2` por tick (não evento de botão estilo Godot)

`Input.scrollDelta: Vec2` acumula o delta de roda do tick (zerado em `beginTick()` junto com os demais sets per-tick), e `Input.scrollConsumed: Boolean` (default no-op, igual a `mouseDragConsumed`) sinaliza consumo por UI. Sinal: **`y` positivo = rolar para baixo** (revela linhas inferiores).

- **Skiko:** `SkikoInput.onAwtMouseWheel(MouseWheelEvent)` acumula `event.preciseWheelRotation` em estado `@Volatile`/atômico (a roda chega na thread AWT, igual aos demais callbacks); `SkikoHost` registra `MouseWheelListener`. `beginTick()` drena o acumulado para `scrollDelta`.
- **LWJGL:** `LwjglHost` registra `glfwSetScrollCallback`; `LwjglInput` acumula `(xoffset, yoffset)` (mesma thread do loop, sem volatilidade necessária); `beginTick()` drena.

*Alternativa rejeitada:* modelar como na Godot (`MOUSE_BUTTON_WHEEL_UP/DOWN` + campo `factor`). GLFW/AWT já entregam delta contínuo; convertê-lo em "botão up/down" jogaria fora a precisão de trackpad que a Godot teve de readicionar via `factor`. Além disso nossa `Input` é snapshot-por-tick, não fila de eventos — um delta acumulado encaixa; uma fila de eventos de roda seria corpo estranho.

### D3. Scroll mora no `ScreenDebugWidget` (base), não por widget

A base já é dona do chrome, da medição e do drag; scroll é mais um aspecto desse mesmo dono. Colocá-lo na base faz `SceneTreeWidget`, `NodeInspectorWidget` e `DebugHud` ganharem scroll sem duplicação. As subclasses continuam produzindo o **layout completo** (todas as linhas) e desenhando em coordenadas absolutas como hoje — a base injeta clip + offset ao redor do `drawDebug`.

### D4. Separação conteúdo/viewport; offset clampado on-read; grabber derivado

A base passa a distinguir:
- **extensão de conteúdo** `contentExtent.y` — altura intrínseca de todas as linhas (o que a subclasse mede hoje em `bodySize`);
- **viewport** `viewport.y = min(contentExtent.y, maxBodyHeight)` — o que o dock mede via `contentSize()`; abaixo do teto o painel ainda auto-sized (preserva o comportamento atual de árvores pequenas).

Único estado guardado: `scrollOffset: Float` (lógico). **Clampado on-read** a `0..max(0, contentExtent.y - viewport.y)` contra o `(contentExtent, viewport)` do frame corrente. Grabber e barra são funções puras por frame:

```
scrollable = contentExtent.y - viewport.y           // 0 ⇒ sem barra
grabberH   = viewport.y * (viewport.y / contentExtent.y)
grabberY   = track.top + (track.h - grabberH) * (offset / scrollable)
```

Consequência: resize de janela, troca de dock e árvore mutante caem **todos** no mesmo caminho — nada é guardado em pixels, então não há ramo especial de "ajuste no resize". É a invariante importada da Godot.

### D5. Composição de desenho: clip por fora, offset por dentro

A base envolve o `drawDebug` da subclasse:

```
pushClip(viewportRect)                 // rect fixo em pixels de tela
  pushTransform(translate(0, -offset)) // desliza o conteúdo
    drawDebug(renderer)                // subclasse desenha como hoje
  popTransform()
popClip()
desenha track + grabber                // fora do clip, derivados
```

O aninhamento (clip por fora, transform por dentro) mantém o LIFO válido tanto na pilha save/restore do Skiko quanto no estado NanoVG. A subclasse fica alheia ao recorte e ao offset.

### D6. Roteamento de scroll em `hitTestUI` (não em `onProcess`)

Cliques/drag são consumidos em `hitTestUI`, **antes** de qualquer node processar. Se o scroll fosse tratado no `onProcess` de cada painel, um node de gameplay que processe antes do painel leria `scrollConsumed == false` e reagiria à roda (zoom de câmera). Por isso o scroll é roteado em `hitTestUI` (ou irmão `hitTestScroll` chamado no mesmo ponto): acha `topPanelAt(pointer)`; se for um `ScreenDebugWidget` rolável com ponteiro dentro do viewport, chama `panel.applyScroll(input.scrollDelta.y)` e seta `input.scrollConsumed = true`. `scrollConsumed` é resetado no começo do tick junto com `mouseClickConsumed`/`mouseDragConsumed`. O **offset continua morando no widget**; só o roteamento sobe.

O drag do grabber, por outro lado, reaproveita o polling de press/hold/release que `ScreenDebugWidget.updateDrag` já faz para o header — é mais um hit-region (o retângulo do grabber) escrevendo o offset.

### D7. Remoção do truncamento

Com layout completo + scroll, a lógica `… (+N more)` de `SceneTreeWidget.computeLayout` e `NodeInspectorWidget.computeLayout` é removida. O hit-test de linha (`SceneTreeWidget.nodeAt`) passa a aplicar `-scrollOffset` ao `y` inicial, mantendo desenho e hit-test em acordo (a invariante "os três concordam" já existente). Nenhuma spec atual exige o truncamento (não há requisito de overflow em `debug-scene-picker`), então a remoção é interna.

## Risks / Trade-offs

- **LIFO intercalando clip e transform na pilha nativa compartilhada** → ambos os backends usam o mesmo save/restore (Skiko canvas; estado NanoVG). Push/pop desbalanceado ou ordem errada corrompe o estado do frame inteiro. Mitigação: composição fixa da D5 (clip sempre por fora), `IllegalStateException` em pop vazio, e teste de pilha intercalada (push transform, push clip, pop clip, pop transform).
- **HiDPI / unidades** → o `rect` de clip precisa estar no mesmo espaço de pixels físicos que `tree.size` e `pointerPosition` (débito conhecido do `surface-units-spec`). Mitigação: o viewport é derivado de `tree.size` e `bodyOrigin`, ambos já em pixels físicos; nenhuma conversão nova.
- **Acúmulo de `scrollDelta` entre threads (AWT)** → a roda Skiko chega fora da thread do loop. Mitigação: acumulador atômico/`@Volatile` drenado em `beginTick`, espelhando o padrão já usado para teclas/botões em `SkikoInput`.
- **Drag do grabber vs drag do header** → dois consumidores de drag no mesmo widget. Mitigação: hit-test do grabber só quando o press cai no retângulo do grabber e o painel é o `pressOwner`; consome `mouseDragConsumed` como o header já faz.
- **Testes existentes que afirmem `… (+N more)`** → a remoção do truncamento pode quebrá-los. Mitigação: localizar e atualizar/remover esses testes na fase de tasks.
- **NanoVG `nvgSave/Restore` salva mais que scissor** → se o `LwjglRenderer` já usa save/restore para transform, push/pop de clip e de transform precisam compartilhar a mesma disciplina. Mitigação: padronizar que tanto `pushTransform` quanto `pushClip` usam `nvgSave/nvgRestore`, validado pelo mesmo teste de pilha intercalada.
