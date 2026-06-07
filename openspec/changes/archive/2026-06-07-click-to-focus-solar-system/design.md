## Context

`SolarSystemDemo` (demo `Transforms` de `:games:demos`) já tem um `Camera2D` interativo com scroll-zoom (em torno do cursor), drag-pan (botão esquerdo, grab-and-drag pinando o ponto de mundo) e pan por setas — tudo via mutação de `Camera2D.bounds: Rect(origin, size)`. Os corpos celestes são `Circle2D` aninhados sob `Rotator`s que giram a cada frame; a posição de mundo de qualquer corpo é `corpo.world().position`, que já compõe toda a cadeia de transforms ancestrais.

Esta change adiciona uma camada de interação **sobre** essa base, sem tocar em `:engine`. A demo continua sendo uma `Node2D` (invariante #1), o overlay segue em `CanvasLayer` (invariante #6) e nada é desenhado fora de `onDraw`/`SceneTree.render` (invariante #4).

Fato confirmado no código (`SkikoInput.kt`): `wasMouseClickedRaw` é **press-edge** (dispara no tick do pressionar, via `pendingButtonPresses`), não no soltar. Logo a desambiguação clique-vs-arrasto **não** pode esperar o release via `wasMouseClicked` — usamos rastreamento de borda de `isMouseDown`.

## Goals / Non-Goals

**Goals:**
- Clicar (botão esquerdo) num corpo trava a câmera nele: zoom suave + follow centrado.
- Preservar 100% o comportamento livre da câmera quando nada está focado.
- Desambiguação robusta clique-vs-arrasto no mesmo botão esquerdo.
- Feedback visual claro do corpo focado (anel world-space + nome no HUD).
- Tudo testável headless (pick e centralização) sem depender de render real.

**Non-Goals:**
- Nenhuma mudança em `:engine` (sem `Camera2D.followTarget` built-in — fica para uma change futura se o padrão se repetir).
- Sem animação de "voltar suave" ao destravar (devolve controle livre na view atual).
- Sem foco em corpos não-`Circle2D` (o `SaturnRing`/trilhas não são alvos).
- Sem reusar `hitTestPick` (gated no inspector, consome o clique para o picker do inspector).

## Decisions

### D1 — Estado de foco: um único campo `focused: Node2D?`
`@Transient private var focused: Node2D? = null`. `null` ⇒ modo LIVRE (comportamento atual intacto). Não-`null` ⇒ modo TRAVADO. Sem máquina de estados explícita: o resto deriva desse campo. O alvo guardado é o `Circle2D` do corpo (Sol/planeta/lua), de quem se lê `world().position` e `radius`.

*Alternativa descartada:* enum de estados (LIVRE/ZOOMING/FOLLOWING) — desnecessário, já que o zoom é uma lerp contínua que converge sozinha.

### D2 — Pick por distância próprio (não `hitTestPick`)
No clique, converter `input.pointerPosition` → world via `cam.screenToWorld(pointer, tree.size)`. Para cada `Circle2D` candidato (Sol + planetas + luas), testar `distância(world().position, clickWorld) ≤ raioDePick`, onde `raioDePick = max(corpo.radius, MIN_PICK_PX / escalaDaCamera)` — o piso em pixels de tela convertido para world garante que luas de 2px sejam clicáveis em qualquer zoom. Em empate (vários sob o cursor), vence o de **menor raio** (a lua sobre o planeta), que é também o "mais à frente" visualmente. Coletar candidatos andando a árvore a partir de `Center` (mesma travessia do `onDraw` atual já existente).

*Alternativa descartada:* reusar `SceneTree.hitTestPick` — é gated em `debug.inspector.enabled`, consome o clique para o picker do inspector e seleciona por `localBounds`/AABB, não pelo critério "menor corpo sob o cursor com piso de pixel" que queremos.

A escala da câmera é `tree.size.x / cam.bounds.size.x` (largura) — reaproveita a mesma matemática de `fitTransform`; como `MIN_PICK_PX` é folga de usabilidade, usar a escala horizontal é suficiente.

### D3 — Desambiguação clique-vs-arrasto por borda de `isMouseDown`
Como o clique é press-edge, rastreamos manualmente:
- **Press** (`isMouseDown(Left)` e não estava down no frame anterior): grava `pressAnchor = pointerPosition`, `pendingClick = true`.
- **Held**: se `distância(pointerPosition, pressAnchor) > CLICK_SLOP_PX`, então `pendingClick = false` (virou arrasto → o `dragPan` atual assume; em modo travado, o arrasto **destrava**).
- **Release** (estava down, agora não): se `pendingClick` ainda `true`, foi um **clique** → executa o pick (D2).

O `dragPan` atual já só translada quando o ponteiro de fato se move entre frames, então um clique parado não causa pan visível. `CLICK_SLOP_PX` (~4–6px) separa tremor de clique de arrasto real. Respeita `mouseDragConsumed` como hoje.

*Alternativa descartada:* selecionar no press e cancelar se virar arrasto — causa "piscada" de foco; e botão direito para selecionar — menos intuitivo (o pedido é "clicar").

### D4 — Follow: recentrar `bounds.origin` todo frame
Travado, a cada `onProcess`: `target = focused.world().position`; `cam.bounds = Rect(target − cam.bounds.size/2, cam.bounds.size)`. Isso mantém o corpo cravado no centro enquanto a hierarquia gira em volta — a demonstração do invariante A1. O recenter roda **depois** do avanço dos `Rotator`s no tick (a `world()` já reflete a rotação do frame), evitando jitter.

### D5 — Zoom de foco: lerp exponencial de `bounds.size` enquadrando corpo + órbita
Tamanho-alvo `focusSize`: largura = `clamp(corpo.radius * FOCUS_RADIUS_MULT, FOCUS_MIN_WIDTH, …)`, mas elevada para enquadrar a **vizinhança orbital** — para um corpo com luas, usar o maior raio orbital de lua; para uma lua, enquadrar em torno do próprio corpo com um piso de contexto. Operacionalmente: `focusHalfExtent = max(corpo.radius * FOCUS_RADIUS_MULT, maiorRaioOrbitalDeFilho, FOCUS_MIN_HALF)`, `focusWidth = 2 * focusHalfExtent * (1 + FOCUS_PADDING)`. A largura é convergida por suavização exponencial: `size += (focusSize − size) * min(1f, FOCUS_LERP * dt)`, mantendo o aspecto via `clampZoom` existente. O piso `FOCUS_MIN_WIDTH` é **menor** que o `MIN_ZOOM_WIDTH` livre (relaxado em modo travado) para que luas pequenas sejam enquadráveis.

Scroll em modo travado **ajusta** `focusSize` (multiplica o alvo, clampeado) em vez de mexer direto em `bounds` — o alvo continua centrado pela D4. Isso é o "modo fluido".

### D6 — Destravar (modo fluido)
`focused = null` quando: o arrasto cruza `CLICK_SLOP_PX` (D3), `Esc` é pressionada (`wasKeyPressed(ESCAPE)`), um clique cai no vazio (pick não acha corpo) ou um clique acerta o **mesmo** corpo já focado (toggle). Ao destravar, a câmera permanece na view atual e o controle livre (scroll/drag/setas) volta a valer — `bounds` não é restaurado.

### D7 — Feedback visual
- **Anel de seleção**: em `onDraw` (world-space, onde as trilhas já são desenhadas), se `focused != null`, desenhar um círculo não-preenchido em `focused.world().position` com raio `corpo.radius + RING_GAP` e cor de destaque. Espessura constante em world; aceitável (engrossa visualmente sob zoom alto — simples e suficiente para demo).
- **Nome no HUD**: um `Label` no `CanvasLayer` do overlay da demo exibindo o nome do corpo focado (`focused.name`), vazio/oculto quando livre. Reaproveita o overlay existente (`DemoOverlay`/helpers).

### D8 — Free-camera intacto quando `focused == null`
`updateCamera` mantém scroll-zoom/drag-pan/keyPan idênticos no ramo livre. O ramo travado é um early-branch dentro de `updateCamera` (ou método `updateFocusCamera`). O guard de inicialização de `bounds` no resize permanece.

## Risks / Trade-offs

- **[Pick de luas em zoom-out extremo]** Luas a 2px podem ficar sob o Sol/planeta no clique → mitigado pelo critério "menor raio vence" (D2) e pelo piso `MIN_PICK_PX`; ainda assim, em zoom muito afastado pode ser difícil mirar a lua certa. Aceitável para demo (o usuário pode dar zoom antes).
- **[Arrasto acidental rouba o clique]** `CLICK_SLOP_PX` muito baixo trata tremor como arrasto. → calibrar ~4–6px; testar manualmente.
- **[Espessura do anel sob zoom]** Anel em world-space engrossa ao aproximar. → constante simples; se incomodar, dividir pela escala da câmera (deixado como tuning, não bloqueia).
- **[Recenter vs. drag no mesmo tick]** Se travado e o usuário começa a arrastar, o recenter (D4) poderia brigar com o pan no frame da transição. → a D6 destrava assim que o slop é cruzado, antes do recenter daquele frame; ordenar o destravamento antes do bloco de follow em `onProcess`.
- **[Sincronia anel/HUD]** Nome no HUD e anel devem refletir o mesmo `focused`. → ambos derivam do único campo `focused` (D1); HUD atualizado em `onProcess`.

## Open Questions

Nenhuma bloqueante. Questão de press-vs-release **resolvida** (press-edge → borda de `isMouseDown`). Valores exatos de `FOCUS_RADIUS_MULT`, `FOCUS_PADDING`, `FOCUS_LERP`, `CLICK_SLOP_PX`, `MIN_PICK_PX`, `FOCUS_MIN_WIDTH`, `RING_GAP` são tuning a calibrar na implementação (companion object, conforme o requisito existente de centralizar constantes).
