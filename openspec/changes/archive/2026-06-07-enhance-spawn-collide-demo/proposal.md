## Why

A demo `Spawn & Collide` hoje é unidirecional: o trap central é um `Area2D` fixo que só despawna, sem nada para mexer em runtime. Ao mesmo tempo, o `AxesWidget` registrado nos dois `main` é uma demonstração mínima do contrato de widget customizado (só dois `drawLine`, registrado uma vez, nunca des-registrado). Transformar o trap em algo interativo — alternável entre sensor e obstáculo, arrastável, controlado por um widget de debug próprio do demo — torna a demo uma prova viva muito mais rica da dicotomia sensor/sólido (invariante #3) e do contrato de extensão de debug (`register`/`unregister`), tornando o `AxesWidget` redundante.

## What Changes

- **Trap alterna entre dois modos**: `Despawn` (sensor `Area2D` que remove a bolinha no `onBodyEntered`, como hoje) e `Collide` (obstáculo sólido `StaticBody2D` em que as bolinhas quicam). Implementado com **dois colliders irmãos na arena** (`TrapSensor : Area2D` + `TrapWall : StaticBody2D`), nunca ativos ao mesmo tempo, alternando o flag `disabled` de `CollisionObject2D`.
- **Trap arrastável pela tela**: grab-and-drag com botão esquerdo dentro do rect do trap, posição clampada a `[half, size − half]`. O resize re-clampa a posição (não recentra mais), preservando o arrasto do usuário.
- **Widget de debug customizado do demo**: `SpawnCollideWidget : ScreenDebugWidget`, registrado pelo demo no `onEnter` e **des-registrado no `onExit`** (some ao voltar ao menu). Expõe dois segmented controls — `Trap [Despawn | Collide]` e `Auto-spawn [On | Off]` — espelhando o padrão visual do `ColliderModePanel`.
- **Desligar o spawn aleatório**: o `Spawner` ganha um gate booleano que pausa o drip automático; com `Auto = Off`, só o clique manual spawna (e arrastar o trap nunca spawna).
- **Remover o `AxesWidget`**: deleta `AxesWidget.kt` e as chamadas `tree.debug.register(AxesWidget())` em `Main.kt` e `MainLwjgl.kt`. O novo widget é uma demonstração mais útil do mesmo contrato de extensão, agora exercitando `register` **e** `unregister` a partir de um `Node`.

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova: o widget e os modos do trap são comportamento do demo (demos-sample) e uso do contrato existente da debug-overlay. -->

### Modified Capabilities
- `demos-sample`: a requirement do `Spawn & Collide` muda — trap deixa de ser só `Area2D` despawn e passa a ter modos despawn/colisão (sensor vs `StaticBody2D` sólido), ser arrastável com clamp à tela, e expor um `SpawnCollideWidget` de debug com toggles de modo do trap e de auto-spawn. O cenário cross-backend do trap (que hoje afirma "o trap remove a bolinha") passa a ser dependente de modo.
- `debug-overlay`: os 3 cenários que nomeiam `AxesWidget` (`find` por tipo, "HUD lists one row per registered widget", "Custom widget registered post-start appears in HUD") são generalizados para um widget de exemplo hipotético, desacoplando a spec da engine de uma classe do módulo `:games:demos`.

## Impact

- **Código (`:games:demos`)**: `SpawnCollideDemo.kt` (modos do trap, drag, gate de auto-spawn, registro/des-registro do widget), novo `SpawnCollideWidget.kt`, `Main.kt` e `MainLwjgl.kt` (remoção do register), deleção de `AxesWidget.kt`.
- **APIs da engine**: nenhuma mudança de API — usa `CollisionObject2D.disabled`, `tree.debug.register`/`unregister`, `ScreenDebugWidget`, `Input.mouseDragConsumed`/`mouseClickConsumed` já existentes. Restrição estrutural respeitada: `TrapWall` é irmão direto das bolinhas na `BoundaryWalls` (sweep do `RigidBody2D` só considera `target.parent === parent`).
- **Specs**: deltas em `demos-sample` e `debug-overlay`.
- **Invariantes**: #3 (sensor vs sólido) é exatamente o que a demo passa a ilustrar; nenhum invariante é quebrado. `:engine` permanece sem conhecer o widget do demo.
