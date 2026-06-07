## 1. Constantes e estado de foco

- [x] 1.1 Adicionar à companion/objects de `SolarSystemDemo` as constantes de tuning: `FOCUS_RADIUS_MULT`, `FOCUS_PADDING`, `FOCUS_MIN_HALF`, `FOCUS_MIN_WIDTH`, `FOCUS_LERP`, `CLICK_SLOP_PX`, `MIN_PICK_PX`, `RING_GAP` e cor do anel de seleção.
- [x] 1.2 Adicionar os campos `@Transient` de estado: `focused: Node2D?`, `pressAnchor: Vec2`, `pendingClick: Boolean`, `wasMouseDown: Boolean` (borda) e o `focusSize`-alvo (largura de foco corrente).

## 2. Pick por distância

- [x] 2.1 Implementar coleta de candidatos `Circle2D` (Sol + planetas + luas) percorrendo a árvore a partir de `Center` (reaproveitando a travessia do `onDraw`).
- [x] 2.2 Implementar `pickBody(clickWorld, cam): Circle2D?` testando `distância(world().position, clickWorld) ≤ max(radius, MIN_PICK_PX / escalaDaCamera)`, retornando o de **menor raio** em empate; `null` se nada acerta.
- [x] 2.3 Calcular a escala da câmera como `tree.size.x / cam.bounds.size.x` para converter `MIN_PICK_PX` em world.

## 3. Desambiguação clique vs. arrasto

- [x] 3.1 Rastrear borda de `isMouseDown(Left)`: no press gravar `pressAnchor` e `pendingClick = true`; honrar `mouseDragConsumed`.
- [x] 3.2 Durante o hold, se `distância(pointer, pressAnchor) > CLICK_SLOP_PX` então `pendingClick = false` (vira arrasto; se travado, destrava — ver 5.3).
- [x] 3.3 No release, se `pendingClick` ainda `true`, executar o pick (seção 2) e aplicar a regra de foco/toggle/vazio (seção 5).

## 4. Follow e zoom de foco

- [x] 4.1 Em `onProcess`, quando `focused != null`, recentrar `bounds.origin = focused.world().position − bounds.size/2` (depois do avanço dos `Rotator`s).
- [x] 4.2 Computar `focusSize` enquadrando corpo + órbita: meia-extensão `max(radius * FOCUS_RADIUS_MULT, maiorRaioOrbitalDeFilho, FOCUS_MIN_HALF)`, largura `2 * meiaExtensão * (1 + FOCUS_PADDING)`, com piso `FOCUS_MIN_WIDTH` (clamp relaxado vs. modo livre) e aspecto preservado via `clampZoom`.
- [x] 4.3 Convergir `bounds.size` por lerp exponencial: `size += (focusSize − size) * min(1f, FOCUS_LERP * dt)`.

## 5. Modo travado fluido e destravamento

- [x] 5.1 Branch em `updateCamera`: se `focused != null` rodar o ramo travado (follow + lerp + scroll-ajusta-foco); senão o ramo livre atual intacto.
- [x] 5.2 Scroll em modo travado escala o `focusSize`-alvo (clampeado), mantendo o corpo centrado; não fazer pan livre.
- [x] 5.3 Destravar (`focused = null`) em: arrasto cruzou `CLICK_SLOP_PX`, `Esc` (`wasKeyPressed(ESCAPE)`), clique no vazio, ou clique no mesmo corpo (toggle). Garantir que o destravamento por arrasto ocorra antes do recenter do frame.
- [x] 5.4 Confirmar que com `focused == null` o comportamento livre (scroll-zoom em torno do cursor, drag-pan, setas) é byte-idêntico ao anterior.

## 6. Feedback visual

- [x] 6.1 No `onDraw`, se `focused != null`, desenhar anel não-preenchido em `focused.world().position` com raio `focused.radius + RING_GAP` na cor de destaque (world-space).
- [x] 6.2 Exibir o nome do corpo focado num `Label` no `CanvasLayer` do overlay da demo; vazio/oculto quando `focused == null`. Atualizar em `onProcess`.

## 7. Testes e documentação

- [x] 7.1 Teste headless de `pickBody`: clique sobre planeta seleciona o planeta; clique na sobreposição planeta+lua seleciona a lua (menor raio); lua minúscula em zoom-out é selecionável via piso `MIN_PICK_PX`.
- [x] 7.2 Teste headless de follow: com um corpo focado, após `onProcess`, `bounds.origin + bounds.size/2 == focused.world().position`.
- [x] 7.3 Teste headless de destravamento: `Esc`/clique-no-vazio/clique-no-mesmo-corpo zera `focused`.
- [x] 7.4 Atualizar a descrição da demo no menu (e a linha de `:games:demos` no `CLAUDE.md`, se aplicável) mencionando o foco por clique.
- [x] 7.5 Rodar `./gradlew :games:demos:test` (ou o módulo correspondente) e garantir verde.
