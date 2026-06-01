## 1. Estado de colapso na base

- [x] 1.1 Adicionar `var collapsed: Boolean = false` (private set) ao `ScreenDebugWidget`, com KDoc espelhando a semântica de sessão de `customOrigin`
- [x] 1.2 Adicionar `toggleCollapsed()` e a property `bodyVisible get() = enabled && !collapsed` (visível a subclasses)
- [x] 1.3 Ajustar `contentSize()`: `!bodyVisible` → `Vec2(bodySize().x, headerHeight)`; `bodyVisible` → `Vec2(bodySize().x, headerHeight + bodySize().y)`
- [x] 1.4 Ajustar `onDraw`: desenhar chrome sempre que `enabled`; chamar `drawDebug` apenas se `bodyVisible`

## 2. Header: grip à esquerda e controles à direita

- [x] 2.1 Mover `drawDragGrip` para a esquerda (`startX = headerOrigin.x + padding`)
- [x] 2.2 Deslocar o título para `o.x + padding + gripW + gap` em `drawChrome`
- [x] 2.3 Desenhar o glifo de colapsar (`[_]`, traço) e de fechar (`[x]`, X) à direita via `drawRect`/`drawLine`; `[x]` no extremo, `[_]` à esquerda dele
- [x] 2.4 Adicionar constantes de ícone (tamanho/gap) — em `DebugTheme` se compartilhadas, ou companion do widget se locais
- [x] 2.5 Extrair helpers de geometria dos três rects (grip, colapsar, fechar) reusados por desenho e hit-test

## 3. Hit-test: ação dos controles e recorte da zona de arraste

- [x] 3.1 Em `updateDrag`, no press-edge: se o ponteiro caiu no rect de fechar → `enabled = false` + consumir clique/drag e retornar
- [x] 3.2 No press-edge: se caiu no rect de colapsar → `toggleCollapsed()` + consumir clique/drag e retornar
- [x] 3.3 Reescrever `inHeader` para subtrair os três rects (grip, colapsar, fechar) da zona de arraste
- [x] 3.4 Garantir que pressionar qualquer controle não vaze ao scene-picker (setar `mouseClickConsumed`/`mouseDragConsumed` como o drag já faz)

## 4. Ponte para widgets de nós-filhos

- [x] 4.1 `DebugHud`: trocar o gatilho `if (enabled != lastEnabled)` por `if (bodyVisible != lastBodyVisible)` chamando `buildPanel`/`tearDownPanel`
- [x] 4.2 `TimeControlWidget`: mesma troca de gatilho para `bodyVisible`
- [x] 4.3 Confirmar que widgets de desenho imediato (Fps, Momentum, Profiler, Log) não precisam de mudança (a base já corta `drawDebug`)

## 5. Reset de layout também expande

- [x] 5.1 Em `resetPosition` (ou no `resetAllPanelPositions`/`DebugLayoutShortcutNode`) também setar `collapsed = false` ao limpar `customOrigin`
- [x] 5.2 Confirmar que o atalho BACKSPACE restaura posição e expande todos os painéis numa única ação

## 6. Testes de regressão

- [x] 6.1 Fechar: acionar `[x]` leva o widget a `enabled = false`
- [x] 6.2 Colapsar: acionar `[_]` colapsa e `contentSize().y` cai para `headerHeight`; reacionar expande
- [x] 6.3 Hit-test do corpo desmontado: painel de nós-filhos colapsado não atinge nenhum `Button` onde o corpo estaria
- [x] 6.4 Persistência de sessão: `collapsed` sobrevive a toggle `enabled` e re-clamp de `customOrigin` no `resize`
- [x] 6.5 Zona de arraste: press no grip e nos controles não inicia drag; press no resto do header inicia
- [x] 6.6 Reset: BACKSPACE limpa `customOrigin` e expande painéis colapsados

## 7. Documentação

- [x] 7.1 Atualizar KDoc de `ScreenDebugWidget` (controles do header, `collapsed`, `bodyVisible`)
- [x] 7.2 Revisar se README/CLAUDE precisam de nota sobre os controles de janela dos painéis de debug
