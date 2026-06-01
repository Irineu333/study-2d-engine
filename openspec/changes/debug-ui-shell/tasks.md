# Tasks — Debug UI Shell

## 1. DebugTheme (chrome única)
- [x] 1.1 Criar `DebugTheme` com cor de fundo, cor/espessura de borda, margens,
  paddings e escala de texto nomeada (title/body/small)
- [x] 1.2 Mover/reconciliar `DebugColors` (gizmo/log) sob o tema como fonte única
- [x] 1.3 Substituir os literais de chrome duplicados em `DebugHud` e
  `TimeControlWidget` por referências ao tema

## 2. DebugDock + DockSlot (layout por slot)
- [x] 2.1 Criar enum `DockSlot` (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`,
  `BOTTOM_RIGHT`, `TOP_CENTER`, `BOTTOM_CENTER`)
- [x] 2.2 Criar `DebugDock` per-tree que empilha verticalmente os widgets de
  cada slot a partir do canto, com gutter do tema
- [x] 2.3 Re-fluir o layout a cada `tree.resize` (sem cada widget re-anchorar)

## 3. Contrato de tamanho/origin no ScreenDebugWidget
- [x] 3.1 `ScreenDebugWidget` declara `slot: DockSlot` e reporta o `Vec2`
  ocupado (medido do conteúdo)
- [x] 3.2 Widget desenha a partir do origin dado pelo dock, não de pixels próprios
- [x] 3.3 `DebugRegistry.bindLayer` passa o dock aos widgets e os registra no slot

## 4. Migração dos widgets imediatos para Panel+Label
- [x] 4.1 `FpsWidget` → painel temado
- [x] 4.2 `MomentumWidget` → painel temado (sparklines dentro do retângulo)
- [x] 4.3 `ProfilerWidget` → painel temado
- [x] 4.4 `LogOverlayWidget` → painel temado
- [x] 4.5 `ScenePickerWidget` → painel temado (reusa overflow row existente)
- [x] 4.6 Atribuir slots default sem colisão entre todos os built-ins

## 5. Testes + validação
- [x] 5.1 Teste: dois widgets no mesmo slot empilham sem sobrepor
- [x] 5.2 Teste: re-fluxo no resize mantém widgets dentro do viewport
- [x] 5.3 Teste: tema aplicado — chrome idêntica entre os painéis
- [x] 5.4 Validação visual manual nas demos (todos os widgets ligados juntos)
