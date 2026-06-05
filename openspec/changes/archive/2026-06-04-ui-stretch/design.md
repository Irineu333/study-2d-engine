## Context

A UI in-game vive em `CanvasLayer` (invariante #6), que hoje desenha sempre em `tree.size` pixels crus, **ignorando** a view transform da `Camera2D`. Jogos com câmera (`pong`, `snake`, `tictactoe`) usam `Camera2D` com `aspectMode = FIT` para escalar e centralizar o mundo na janela, mas autoram o HUD em coordenadas de **design/mundo** (scores do Pong em `320/440` num `bounds 800×600`; `WORLD_CENTER_X = 300` na Velha). Enquanto a janela tem o tamanho de design, screen-space e design-space coincidem e tudo alinha. Ao redimensionar, o mundo escala+letterboxa mas a UI não — o HUD desalinha e não escala. Sem câmera (`hello-world`), screen e design são o mesmo espaço, então o problema não aparece. Débito já registrado no `ROADMAP.md`.

Verificado neste change: **ambos os backends** (`:engine-skiko` via `Canvas.scale` + `drawTextLine`; `:engine-lwjgl` via `nvgScale` + `nvgText`) aplicam a escala da transform stack ao texto. Logo, empilhar uma transform com escala `S` ao redor do subtree de um `CanvasLayer` escala posição, tamanho **e** `fontSize` por `S` — sem tocar em `fontSize` explicitamente. `measureText` retorna unidades de fonte puras (não afetado pela stack), o que mantém o anchor layout correto.

## Goals / Non-Goals

**Goals:**
- HUD dos jogos com `Camera2D` alinha **e** escala em sincronia com o mundo no resize (modo `canvas_items` do Godot).
- Mecanismo sistêmico na engine: todo `CanvasLayer` de jogo herda o comportamento; jogos migram com mínima churn (idealmente só herdando o `designSize`).
- Debug UI (inspector, HUD, painéis) permanece em pixels crus, imune ao stretch.
- HUD **não** arrasta com pan/zoom da câmera de gameplay — só acompanha a adaptação de resolução.

**Non-Goals:**
- Modo `viewport` do Godot (render numa base fixa via render target intermediário). Fora de escopo; `canvas_items` resolve o problema com menos superfície.
- Stretch por-eixo configurável por `CanvasLayer` individual (um `uiStretchMode` por árvore basta para os jogos shipped).
- `CanvasLayer.follow_viewport` (acoplar HUD ao pan da câmera) — explicitamente o oposto do que queremos aqui.
- Mudar a API dos `Renderer` backends (já honram escala na stack).

## Decisions

### 1. Resolução de design mora na `SceneTree`, não na `Camera2D`

`designSize: Vec2` e `uiStretchMode` são propriedades da **árvore**, não da câmera. Razão: o stretch da UI é uma propriedade de como a superfície se adapta à resolução de referência, **estável** no tempo; a câmera de gameplay pode panar/zoom e não deve perturbar o HUD. Default de `designSize`: `bounds` da `Camera2D` corrente no startup (assim UI e mundo compartilham o mesmo espaço de design e as barras de letterbox coincidem), caindo para `GameConfig` w×h quando não há câmera.

_Alternativa descartada:_ `designSize` por `CanvasLayer` — mais flexível, mas repete a resolução em cada layer e nenhum jogo shipped precisa de design-res heterogênea.

### 2. Decompor `computeViewTransform`: stretch = só adaptação de resolução

A `Camera2D.computeViewTransform` já produz `(translation, scale)` mapeando `bounds → surface`, mas mistura a adaptação de resolução com o **pan** da câmera (`−bounds.origin·scale`). A UI stretch transform reusa **só** a parte de adaptação:

```
scale       = aspect(designSize, size, mode)          // FIT/FILL/STRETCH
translation = (size − designSize·scale) / 2           // centering do letterbox, SEM pan
```

É efetivamente `computeViewTransform` com `bounds.origin = 0` e `bounds.size = designSize`. Implementação natural: extrair a função pura `fitTransform(designSize, surfaceSize, mode)` e fazer tanto `Camera2D` quanto o UI pass consumirem-na (a câmera adiciona o termo de pan por cima). Isso evita duplicar a matemática de aspect e garante que as barras de letterbox de mundo e HUD batam.

### 3. `followStretch` no `CanvasLayer`, default `true`; debug = `false`

A escolha de default é a tensão central. Há dois consumidores legítimos de `CanvasLayer`:
- **HUD de jogo** — quer esticar (o caso comum, o bug a consertar).
- **Overlay pixel-locked** — debug, e futuramente coisas como minimapa/cursor que devem ignorar resolução.

Default `true` faz os jogos "consertarem-se" ao herdar o `designSize` correto, ao custo de a engine precisar marcar explicitamente o `ScreenDebugCanvas` como `followStretch = false`. Preferido a default `false` porque o caso comum (HUD de jogo) é o que o usuário quer e o debug é um conjunto fechado e controlado pela engine. `hello-world` é no-op de qualquer forma (`designSize == size` → transform identidade).

_Alternativa descartada:_ default `false` (opt-in) — seguro mas exige cada jogo opter-in, não "conserta" o débito automaticamente.

### 4. Layout e render em design-space para layers esticados

Na fronteira de um `CanvasLayer` com `followStretch`, o anchor layout pass usa `Rect(ZERO, designSize)` como parent rect, e o UI render pass empilha a stretch transform. Consequência elegante: as posições de mundo que os jogos **já escreveram** voltam a ser corretas, porque o layer agora interpreta os filhos em design-space e estica para a tela. Migração dos jogos vira quase só garantir o `designSize` certo (que já é o default do `bounds` da câmera).

### 5. Hit-test compõe a stretch (mapeia ponteiro → design-space)

Para um clique cair no `Button` onde ele é **desenhado**, o `hitTestUI` mapeia `input.pointerPosition` para o design-space do layer (inverso da stretch transform) antes de testar `screenRect()`. Localiza-se em `SceneTree.hitTestUI`, que já itera os layers — `Control.screenRect()` permanece em design-space e não precisa conhecer o stretch.

## Risks / Trade-offs

- **[Quebra do invariante #6]** → A revisão é deliberada e mediada por esta change; o `CLAUDE.md` é atualizado no archive. O comportamento antigo (pixels crus) continua disponível via `followStretch = false`, e o `ScreenDebugCanvas` o usa, então o invariante "debug não estica" se mantém.
- **[Cenários de spec existentes mudam de número]** → Vários scenarios de `ui-foundation` assumiam Panel em pixels crus sob `CanvasLayer`. Com default `true` e `designSize = bounds`, esses valores mudam (ex.: Panel 2x). Os deltas MODIFIED reescrevem esses scenarios e adicionam o par `followStretch = false` preservando o comportamento antigo — garante cobertura dos dois caminhos.
- **[`designSize` desatualizado se a câmera muda de `bounds` em runtime]** → `designSize` é capturado no startup e estável; se um jogo trocar `bounds` da câmera dinamicamente, o HUD não segue. Aceitável: nenhum jogo shipped faz isso, e `designSize` é settable para o caso raro. Documentado como propriedade estável.
- **[Hit-test invertido com escala degenerada]** → Se `scale` tiver componente `0` (design degenerado), a transform é `null` (sem push) e o ponteiro é usado cru — sem divisão por zero.
- **[Rotação dentro de layer esticado]** → A stretch é translação+escala uniforme (FIT/FILL) ou por-eixo (STRETCH); compõe com a transform local do `Node2D` normalmente. `screenRect()` rotation-aware continua válido em design-space; o mapeamento do ponteiro é afim e preserva a composição.

## Migration Plan

1. Engine primeiro: `fitTransform` extraída, `SceneTree.designSize`/`uiStretchMode`/`uiStretchTransform`, `CanvasLayer.followStretch`, e os três pontos de consumo (`runAnchorLayout`, `render`, `hitTestUI`). `DebugLayer` marca `ScreenDebugCanvas.followStretch = false`.
2. Testes de engine: stretch transform por aspect/centering, ausência de pan, reflow design-space sob resize, hit-test mapeado, imunidade do debug.
3. Migração dos jogos (`pong`, `snake`, `tictactoe`): confirmar que `designSize` herda o `bounds` da câmera; ajustar HUD onde necessário (Pong/Velha já em design-space passam a alinhar; remover o recentramento por-frame da Velha que vira redundante). `hello-world` inalterado.
4. Atualizar `ROADMAP.md` (fechar o débito) e `CLAUDE.md` (invariante #6) no archive.

## Open Questions

- `uiStretchMode` deve **espelhar** automaticamente o `aspectMode` da câmera corrente (para garantir letterbox idêntico) ou ser independente com default `FIT`? Proposta atual: independente, default `FIT`; o default de `designSize` já alinha as barras quando ambos são `FIT`. Decidir se vale acoplar.
- O recentramento por-frame do status da Velha (`board.lua`) deve ser **removido** (passa a usar anchor/center em design-space) ou mantido como está (já funciona em design-space estável)? Inclinação: remover, para o HUD ser declarativo e a Velha virar exemplo limpo de design-space.
