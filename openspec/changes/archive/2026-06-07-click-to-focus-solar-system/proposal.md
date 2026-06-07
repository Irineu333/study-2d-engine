## Why

A demo `Transforms` (`SolarSystemDemo`) hoje só permite zoom/pan livre da câmera — o espectador navega o sistema solar "de fora". Falta uma interação que torne a composição de transforms aninhados **visceral**: travar a câmera num corpo e segui-lo. Seguir uma lua faz o universo inteiro girar em volta dela (a lua é resolvida 4 níveis fundo na árvore via `world()`), entregando a demonstração mais clara possível do invariante A1 — exatamente o propósito didático da demo.

## What Changes

- **Foco por clique**: clicar com o botão esquerdo sobre um corpo celeste (Sol, planeta ou lua) **trava** a câmera nele. A câmera dá **zoom suave** (lerp exponencial de `bounds.size`) até enquadrar o corpo + sua vizinhança orbital, e **segue** o corpo todo frame (`bounds.origin = corpo.world().position − bounds.size/2`), mantendo-o centrado enquanto a hierarquia gira em volta.
- **Desambiguação clique vs. arrasto** no botão esquerdo: movimento abaixo de um threshold em pixels entre press e click = clique (tenta selecionar corpo); acima = pan (comportamento de drag-pan atual intacto).
- **Modo travado fluido**: com um corpo focado, o scroll ajusta o zoom de foco (alvo segue centrado); arrastar (drag-pan), `Esc`, clicar no vazio ou clicar o mesmo corpo **destravam** e devolvem o controle livre. Sem corpo focado, o comportamento atual (scroll-zoom, drag-pan, setas) é idêntico.
- **Feedback visual**: anel de seleção desenhado em world-space (`onDraw`) em volta do corpo focado + nome do corpo exibido no overlay de UI (`CanvasLayer`).
- **Pick próprio por distância**: teste de distância ponteiro→corpo (não reusa `hitTestPick`, que é gated no inspector), com raio mínimo de clique em pixels de tela para luas minúsculas (2px) serem clicáveis.

Sem mudanças em `:engine` — tudo vive em `:games:demos`. Não há breaking changes; o comportamento livre da câmera é preservado.

## Capabilities

### New Capabilities
<!-- nenhuma -->

### Modified Capabilities
- `solar-system-demo`: adiciona a interação de foco por clique (travar/seguir/zoom + desambiguação clique-vs-arrasto + feedback visual) à demo `Transforms`, sobre o `Camera2D` interativo já existente.

## Impact

- **Código**: `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/SolarSystemDemo.kt` (estado de foco, pick, follow, zoom-lerp, anel de seleção); o overlay/`CanvasLayer` da demo (`DemoHelpers.kt` / overlay da demo) para o nome do corpo focado.
- **Testes**: `DemoCatalogTest` (ou teste novo) cobrindo pick e centralização do follow de forma headless.
- **Docs**: descrição da demo no menu e, se aplicável, a linha de `:games:demos` no `CLAUDE.md`.
- **SPIs/Engine**: nenhuma. `Input`, `Camera2D`, `SceneTree.screenToWorld` usados como estão; invariantes #1 (herança), #4 (GameHost não desenha), #6 (HUD em `CanvasLayer`) preservados.
