## 1. ColliderWidget ganha modo ciclável (funde Shapes)

- [x] 1.1 Adicionar `enum ColliderDrawMode { AABB, REAL, BOTH }` no pacote `com.neoutils.engine.debug`
- [x] 1.2 Adicionar `var mode: ColliderDrawMode = REAL` ao `ColliderWidget` e refatorar `drawDebug` para despachar por modo (AABB = `broadPhaseBounds` via `drawRect`; REAL = círculo outline / quad de `worldCorners`; BOTH = AABB primeiro, real por cima)
- [x] 1.3 Portar a lógica de geometria real do `ShapeGizmoWidget` (círculo escalado, `worldCorners`) para o caminho REAL do `ColliderWidget`
- [x] 1.4 Criar node interno `ColliderModeShortcutNode` (no espírito de `TimeControlShortcutNode`/`DebugLayoutShortcutNode`) que cicla `colliders.mode`, e inseri-lo no `ScreenDebugCanvas` via `DebugLayer`
- [x] 1.5 Remover o arquivo `ShapeGizmoWidget.kt`

## 2. ProfilerWidget absorve fps (funde FPS)

- [x] 2.1 Adicionar um `FpsCounter` interno ao `ProfilerWidget`, amostrado em `onProcess` via `System.nanoTime()`, independente do `FrameProfile`
- [x] 2.2 Desenhar a linha `fps NN` no topo do painel, inclusive quando `size == 0` (antes da primeira janela de fases)
- [x] 2.3 Remover o arquivo `FpsWidget.kt`

## 3. Remoção do Momentum

- [x] 3.1 Remover o arquivo `MomentumWidget.kt`
- [x] 3.2 Confirmar que `MomentumDiagnostics.kt` (`totalLinearMomentum`/`totalAngularMomentum`/`totalKineticEnergy`) permanece intacto como API pública de física

## 4. DebugRegistry e DebugLayer (catálogo)

- [x] 4.1 Remover os campos `fps`, `momentum` e `shapeGizmo` do `DebugRegistry`
- [x] 4.2 Ajustar `DebugRegistry.bindLayer` para registrar o catálogo novo (sem `fps`/`momentum`/`shapeGizmo`), preservando a ordem de linhas do HUD
- [x] 4.3 Inserir o `ColliderModeShortcutNode` na construção da `DebugLayer`
- [x] 4.4 Verificar que `register`/`unregister` e `find<T>()` seguem funcionando com o catálogo reduzido

## 5. Testes

- [x] 5.1 Reescrever `DebugRegistryTest` removendo referências a `momentum`/`fps`; cobrir não-compartilhamento via `colliders`
- [x] 5.2 Reescrever `DebugLayerTest` para o catálogo novo (sem `FpsWidget`/`MomentumWidget`); validar containers corretos
- [x] 5.3 Reescrever `BuiltinWidgetsTest` removendo casos de `MomentumWidget`
- [x] 5.4 Ajustar `PhysicsGizmosTest` removendo `ShapeGizmoWidget`; adicionar/mover casos de geometria real para o `ColliderWidget` (modos REAL/BOTH)
- [x] 5.5 Adicionar teste do modo do `ColliderWidget` (AABB/REAL/BOTH, default REAL, ciclo via shortcut)
- [x] 5.6 Adicionar teste da linha de fps do `ProfilerWidget` (aparece com `size == 0`, independente da instrumentação)
- [x] 5.7 Rodar a suíte completa do `:engine` e garantir verde

## 6. Documentação

- [x] 6.1 Atualizar `CLAUDE.md` invariante #6 (catálogo de built-ins: trocar `fps/colliders/momentum/hud` pelo conjunto novo; mencionar modo do `colliders` e fps no profiler)
- [x] 6.2 Atualizar `README.md` (seção de debug tools) e `ROADMAP.md`
- [x] 6.3 Verificar que nenhuma referência órfã a `FpsWidget`/`ShapeGizmoWidget`/`MomentumWidget` sobra em docs/specs principais (deixar a sincronização de specs para o archive)
