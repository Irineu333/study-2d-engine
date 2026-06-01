## Context

A change arquivada `debug-ui-draggable` estabeleceu o header do `ScreenDebugWidget` como alça de arraste: a base desenha o chrome (fundo + barra de título + grip 2×3 de pontos no canto direito + borda), `inHeader` define a zona de arraste como o header inteiro, e `updateDrag` (polling em `onProcess`) captura o press, segue o ponteiro e escreve `customOrigin` — uma posição de sessão que sobrevive a toggle de `enabled` e a `tree.resize`. O `DebugDock` posiciona os painéis sem `customOrigin` e re-clampa os arrastados.

Hoje só dá para esconder um painel pela `DebugHud` (lista de `[x]/[ ]` que alterna `enabled`), e não há como reduzir um painel grande sem perdê-lo de vista. Esta change adiciona os dois gestos de janela diretamente no header: **fechar** e **colapsar**.

Restrição central: `Node` **não tem** flag `visible`. A base consegue cortar `drawDebug` (caminho dos widgets de desenho imediato: Fps, Momentum, Profiler, Log), mas não consegue "apagar" os `Button` filhos que `DebugHud` e `TimeControlWidget` montam — esses se desenham e recebem clique pela travessia da árvore, fora do controle de `drawDebug`.

## Goals / Non-Goals

**Goals:**
- Botão fechar (`[x]`) e botão colapsar (`[_]`) desenhados no canto direito do header de todo `ScreenDebugWidget`.
- Estado de sessão `collapsed` com a mesma semântica de `customOrigin` (sobrevive a toggle/resize, nunca persiste).
- Colapsar esconde o corpo mantendo o header, incluindo o teardown dos `Button` filhos dos widgets de nós-filhos; o dock re-flui.
- Grip migra para a esquerda do título; a zona de arraste recorta os três retângulos interativos.
- Reset de layout (BACKSPACE) também expande os painéis colapsados.

**Non-Goals:**
- Introduzir um flag `visible` genérico no `Node` (mudança de invariante muito mais ampla; decidido reusar o build/teardown existente).
- Persistir `collapsed` ou `customOrigin` em disco.
- Controles em `WorldDebugWidget` (gizmos não têm chrome).
- Redimensionar painéis por arraste de borda, dock/undock, ou empilhamento por z-order — fora de escopo.

## Decisions

### 1. `[x]` fechar = `enabled = false` (soft close)
Reusa o mecanismo que a `DebugHud` já expõe: o painel some do dock, custa zero, e reabre pela HUD (a própria HUD reabre por F10). Recuperável e sem conceito novo.
- **Alternativa descartada**: dismiss permanente / unregister. Pior para ferramenta de debug — perde recuperabilidade e exigiria um caminho de re-registro.

### 2. `collapsed` na base, espelhando `customOrigin`
Novo `var collapsed: Boolean = false` (private set, alternado por `toggleCollapsed()`), sessão-only. Visibilidade efetiva do corpo centralizada em `val bodyVisible get() = enabled && !collapsed`.
- `contentSize()`: quando `!bodyVisible` → `Vec2(bodySize().x, headerHeight)` (só o header, **mesma largura** — colapsar remove só a altura do corpo); quando `bodyVisible` → `Vec2(bodySize().x, headerHeight + bodySize().y)`. O dock já re-flui a partir de `contentSize()`, então o colapso propaga sem mudar o dock.
- `onDraw`: desenha o chrome sempre que `enabled` (mesmo colapsado, o header aparece); chama `drawDebug` **apenas** se `bodyVisible`.
- **Por que `bodySize().x` mesmo colapsado**: o header precisa de uma largura estável; reusar a largura do corpo mantém o painel coerente entre estados sem cachear dimensão.

### 3. Ponte para widgets de nós-filhos: gatilho passa de `enabled` para `bodyVisible`
`DebugHud` e `TimeControlWidget` hoje fazem `if (enabled != lastEnabled) build/tearDown`. Passam a observar `bodyVisible`: `if (bodyVisible != lastBodyVisible) { if (bodyVisible) buildPanel() else tearDownPanel() }`. Assim colapsar desmonta o `Panel`+`Button`s (zero draw, **zero hit-test**) reusando o teardown que já existe, sem precisar de `Node.visible`.
- A base expõe `bodyVisible` como `protected`/`internal` para as subclasses lerem.
- Widgets de desenho imediato não mudam: a base já corta `drawDebug` quando `!bodyVisible`.
- **Alternativa descartada**: flag `visible` no `Node` podando draw+hit-test da subárvore. Mais geral, porém é mudança de invariante de núcleo — adiada para uma change própria se houver demanda.

### 4. Controles desenhados no chrome + hit-test manual (não `Button` filhos)
`[_]` e `[x]` são glifos simples desenhados em `drawChrome` via `drawRect`/`drawLine` (traço para colapsar, X para fechar), no espírito dos grip-dots, sem fonte de ícones nova. O hit-test é manual em `updateDrag`, coerente com o estilo de polling do drag.
- **Por que não `Button` filhos**: `Button` integra no `hitTestUI` global, mas complica a coexistência com a zona de arraste do header e adicionaria ciclo de vida de nós só para dois ícones. Manter a base autossuficiente é mais simples e fiel ao padrão do drag.

### 5. Layout do header e recorte da zona de arraste
```
┌────────────────────────────────────────────────┐
│ ⠿ Debug HUD                            [_]  [x] │
└────────────────────────────────────────────────┘
  grip    título                      colapsar  fechar
```
- Grip: de `o.x + width - padding - gridW` para `o.x + padding` (esquerda).
- Título: começa em `o.x + padding + gridW + gap` (à direita do grip).
- `[x]` no extremo: `o.x + width - padding - iconW`; `[_]` logo à esquerda dele com um gap.
- `inHeader` deixa de ser o header inteiro: subtrai os três retângulos (grip, `[_]`, `[x]`). Press sobre eles não inicia arraste. Os controles, no press-edge, executam a ação e setam `mouseDragConsumed`/`mouseClickConsumed` para não vazar ao scene-picker — mesmo padrão de consumo já usado pelo drag.

### 6. Reset de layout também expande
`resetAllPanelPositions` (atalho BACKSPACE via `DebugLayoutShortcutNode`) hoje limpa `customOrigin`. Passa a também setar `collapsed = false` em cada painel. "Restaurar layout padrão" devolve posição **e** expande tudo, semântica única e previsível.

## Risks / Trade-offs

- **Ordem de precedência press-edge no header (controle vs. drag)** → No mesmo frame, avaliar os controles **antes** de iniciar o drag em `updateDrag`; se o press caiu num rect de controle, executa a ação e retorna sem armar o drag. Os rects de controle são subtraídos de `inHeader`, então o caminho de drag nunca dispara sobre eles.
- **Painel colapsado de nós-filhos ainda consumir clique no corpo** → Coberto por teste: após colapsar, o `Panel`/`Button`s são removidos (não só ocultados), garantindo zero hit-test. Regressão explícita no suite.
- **Header muito estreito faz título, grip e controles se sobreporem** → Os widgets shipados têm largura suficiente; não há tentativa de elisão de título nesta change (registrar como limitação conhecida, não bloquear).
- **`customOrigin` re-clamp após colapsar/expandir** → `contentSize()` muda ao colapsar; o `reclampCustomOrigin` do dock já roda por relayout e re-clampa com o tamanho corrente, então um painel colapsado perto da borda inferior continua visível.
- **Consistência visual dos glifos entre backends (Skiko/LWJGL)** → Usar apenas primitivas `drawRect`/`drawLine` já exercitadas pelo grip e pelas gizmos garante paridade; sem dependência de glyph/fonte.
