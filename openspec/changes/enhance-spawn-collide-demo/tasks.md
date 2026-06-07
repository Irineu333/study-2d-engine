## 1. Estado compartilhado e modos do trap

- [ ] 1.1 Em `SpawnCollideDemo.kt`, declarar `TrapMode { DESPAWN, COLLIDE }` e o estado de sessão compartilhado (`@Transient` `trapMode`, `autoSpawnEnabled`, `trapPosition`) que será lido pela lógica do demo e pelo widget.
- [ ] 1.2 Substituir o único `Trap : Area2D` por dois colliders irmãos adicionados como filhos diretos da `BoundaryWalls`: `TrapSensor : Area2D` (remove a bolinha no `onBodyEntered`) e `TrapWall : StaticBody2D` (sólido), cada um com `CollisionShape2D` + `RectangleShape2D` do mesmo `SIZE`.
- [ ] 1.3 Manter o visual do trap (a borda `Line2D` atual) como nó que segue `trapPosition`.
- [ ] 1.4 A cada frame, aplicar `trapMode` alternando `disabled`: `TrapSensor.disabled = (trapMode != DESPAWN)`, `TrapWall.disabled = (trapMode != COLLIDE)` — nunca os dois ativos.
- [ ] 1.5 Propagar `trapPosition` para `TrapSensor`, `TrapWall` e o visual a cada frame (via `transform`/`position`).

## 2. Drag do trap e clamp à tela

- [ ] 2.1 Inicializar `trapPosition` no centro na primeira surface válida (degenerada → centra uma vez); remover a recentralização-por-resize anterior.
- [ ] 2.2 Implementar grab-and-drag (estilo `SolarSystemDemo.dragPan`): no press do botão esquerdo, se o ponteiro cai dentro do rect do trap e `!input.mouseDragConsumed`, iniciar drag; seguir o ponteiro enquanto `isMouseDown`.
- [ ] 2.3 Ao agarrar/manter o drag, setar `input.mouseClickConsumed = true` para suprimir o spawn daquele clique.
- [ ] 2.4 Clampar `trapPosition` a `[half, surface − half]` em x e y (`half = SIZE/2`) ao arrastar e no resize (re-clamp, não recentra).

## 3. Gate de auto-spawn no Spawner

- [ ] 3.1 No `Spawner`, gatear o drip automático por `autoSpawnEnabled` (lido do estado compartilhado do demo); com off, só o clique manual spawna.
- [ ] 3.2 Confirmar que o clique manual continua honrando `wasMouseClicked` (UI/drag-consumption) — clicar em painel de debug, no "← Menu" ou arrastar o trap não spawna.

## 4. SpawnCollideWidget (widget de debug customizado)

- [ ] 4.1 Criar `SpawnCollideWidget.kt` em `:games:demos` estendendo `ScreenDebugWidget`, recebendo o estado compartilhado no construtor; `title = "Spawn & Collide"`.
- [ ] 4.2 Espelhar o `ColliderModePanel`: montar um `Panel` invisível com dois segmented controls de `Button`s (`Trap [Despawn | Collide]` e `Auto-spawn [On | Off]`) em `buildPanel()` gated por `bodyVisible`, tear-down quando colapsa/desabilita; reportar `bodySize()`.
- [ ] 4.3 Botões escrevem `trapMode`/`autoSpawnEnabled` no estado compartilhado; `refresh` destaca o segmento ativo lendo o mesmo estado.
- [ ] 4.4 No `SpawnCollideDemo.onEnter`, chamar `tree.debug.register(widget)`; no `onExit`, chamar `tree.debug.unregister(widget)`.

## 5. Remover AxesWidget

- [ ] 5.1 Deletar `games/demos/.../AxesWidget.kt`.
- [ ] 5.2 Remover `tree.debug.register(AxesWidget())` de `Main.kt` e `MainLwjgl.kt` (e o import).

## 6. Specs e documentação

- [ ] 6.1 Sincronizar o delta `debug-overlay` na spec principal: generalizar os 3 cenários que nomeavam `AxesWidget` para `ExampleWidget` (find por tipo, HUD lists one row, custom widget registered post-start) e adicionar o cenário de unregister.
- [ ] 6.2 Sincronizar o delta `demos-sample` na spec principal: nova requirement do trap interativo + atualização do bullet `Spawn & Collide` na requirement documented-role.
- [ ] 6.3 Atualizar a descrição de `:games:demos` em `CLAUDE.md` e `README.md` se necessário (a linha do `Spawn & Collide` menciona o trap interativo / widget); confirmar que o snippet `MyAxes` do README continua coerente (hipotético, não a classe removida).

## 7. Testes e verificação

- [ ] 7.1 Atualizar/estender `DemoCatalogTest` (ou adicionar teste) cobrindo: dois colliders irmãos na arena, toggle de `disabled` por modo, clamp do trap, gate de auto-spawn, e register/unregister do widget no enter/exit.
- [ ] 7.2 Rodar `./gradlew :games:demos:test` (e o build) e garantir verde; confirmar que nenhum teste referencia `AxesWidget`.
- [ ] 7.3 Validação visual nos dois backends (`:games:demos:run` Skiko e `:games:demos:runLwjgl`): abrir Spawn & Collide, abrir HUD, alternar Despawn/Collide, arrastar o trap até as bordas, desligar auto-spawn, voltar ao menu e confirmar que a linha do widget some.
