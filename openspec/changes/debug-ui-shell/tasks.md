# Tasks — Debug UI Shell

## 1. DebugTheme (chrome única)
- [ ] 1.1 Criar `DebugTheme` com cor de fundo, cor/espessura de borda, margens,
  paddings e escala de texto nomeada (title/body/small)
- [ ] 1.2 Mover/reconciliar `DebugColors` (gizmo/log) sob o tema como fonte única
- [ ] 1.3 Substituir os literais de chrome duplicados em `DebugHud` e
  `TimeControlWidget` por referências ao tema

## 2. DebugDock + DockSlot (layout por slot)
- [ ] 2.1 Criar enum `DockSlot` (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`,
  `BOTTOM_RIGHT`, `TOP_CENTER`, `BOTTOM_CENTER`)
- [ ] 2.2 Criar `DebugDock` per-tree que empilha verticalmente os widgets de
  cada slot a partir do canto, com gutter do tema
- [ ] 2.3 Re-fluir o layout a cada `tree.resize` (sem cada widget re-anchorar)

## 3. Contrato de tamanho/origin no ScreenDebugWidget
- [ ] 3.1 `ScreenDebugWidget` declara `slot: DockSlot` e reporta o `Vec2`
  ocupado (medido do conteúdo)
- [ ] 3.2 Widget desenha a partir do origin dado pelo dock, não de pixels próprios
- [ ] 3.3 `DebugRegistry.bindLayer` passa o dock aos widgets e os registra no slot

## 4. Migração dos widgets imediatos para Panel+Label
- [ ] 4.1 `FpsWidget` → painel temado
- [ ] 4.2 `MomentumWidget` → painel temado (sparklines dentro do retângulo)
- [ ] 4.3 `ProfilerWidget` → painel temado
- [ ] 4.4 `LogOverlayWidget` → painel temado
- [ ] 4.5 `ScenePickerWidget` → painel temado (reusa overflow row existente)
- [ ] 4.6 Atribuir slots default sem colisão entre todos os built-ins

## 5. Testes + validação
- [ ] 5.1 Teste: dois widgets no mesmo slot empilham sem sobrepor
- [ ] 5.2 Teste: re-fluxo no resize mantém widgets dentro do viewport
- [ ] 5.3 Teste: tema aplicado — chrome idêntica entre os painéis
- [ ] 5.4 Validação visual manual nas demos (todos os widgets ligados juntos)
