# debug-overlay

## MODIFIED Requirements

### Requirement: Debug widget registry e roteamento por espaço
O `SceneTree` SHALL expor um `DebugRegistry` per-tree que roteia cada
`DebugWidget` registrado para o container do seu espaço — `WorldDebugWidget`
para o world pass (com a view transform do `Camera2D`), `ScreenDebugWidget`
para o UI pass (screen pixels). O posicionamento screen-space de cada
`ScreenDebugWidget` SHALL ser responsabilidade do `DebugDock` (por `DockSlot`
declarado), não de pixels hardcoded no próprio widget.

#### Scenario: Widget world-space roteado para o world container
- **WHEN** um `WorldDebugWidget` é registrado via `tree.debug.register`
- **THEN** ele é adicionado ao `WorldDebugContainer` e desenha sob a view
  transform do `Camera2D`

#### Scenario: Widget screen-space roteado para o screen canvas e posicionado pelo dock
- **WHEN** um `ScreenDebugWidget` é registrado via `tree.debug.register`
- **THEN** ele é adicionado ao `ScreenDebugCanvas` (`CanvasLayer`) e o
  `DebugDock` define seu origin a partir do `DockSlot` declarado
