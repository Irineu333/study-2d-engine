## 1. Shared helpers & cleanup

- [ ] 1.1 Criar um helper compartilhado `hue(h: Float): Color` (top-level ou objeto no módulo) e remover as 3 cópias privadas (`CollisionStressDemo`, `RotatingBoxDemo`, `TumblingSwarmDemo`)
- [ ] 1.2 Remover todo tracking de FPS por demo (`instantFps`, `1f/dt`, `drawText` com `"fps"`) — profiler `F1` assume
- [ ] 1.3 Criar um helper de overlay de demo (`CanvasLayer` com `Label` título + `Label` descrição + `Button` "← Menu") reutilizável por todas as demos

## 2. Navigation menu (absorve a demo de UI)

- [ ] 2.1 Reescrever `DemoSwitcherRoot` para exibir um menu (`CanvasLayer` + `Panel` + 5 `Button`s, um por demo) na inicialização, removendo o `HudOverlay` de `drawText` e o polling de teclas `1`–`0`
- [ ] 2.2 Conectar `pressed` de cada botão para trocar a cena via `addChild`/`removeChild` (guardado contra re-attach), e o `Button` "← Menu" para voltar ao menu
- [ ] 2.3 Garantir que clique no menu/botões é consumido (`wasMouseClicked` retorna `false` para gameplay no tick)
- [ ] 2.4 Remover `UiPlaygroundDemo.kt`

## 3. Transforms demo (funde Solar system + Scale + Camera2D)

- [ ] 3.1 Adicionar uma `Camera2D` (`current = true`) local à `SolarSystemDemo`, desmontada com a cena
- [ ] 3.2 Implementar zoom (escalar `bounds`) e pan (transladar `bounds.origin`) via scroll do mouse e/ou teclas
- [ ] 3.3 Adicionar um nó dedicado com `scale` oscilante (corpo de pulso de escala), visualmente distinto dos planetas
- [ ] 3.4 Mover título/descrição da demo para `Label` em `CanvasLayer` (não sofre zoom da câmera)
- [ ] 3.5 Remover `ScaleHierarchyDemo.kt` (e `ScalePivot`) — sua cobertura passa para o corpo de pulso

## 4. Spawn & Collide demo (funde Spawner + Collision stress)

- [ ] 4.1 Criar a demo `Spawn & Collide`: `BoundaryWalls` como arena, spawn por clique + auto-spawn de `RigidBody2D` bolinhas (quicam elasticamente), trap `Area2D` central que remove via `onAreaEntered`
- [ ] 4.2 Adicionar atores como filhos da instância de `BoundaryWalls` (não siblings); honrar `wasMouseClicked` para click-consumption
- [ ] 4.3 Adicionar overlay de título/descrição via `CanvasLayer`/`Label`
- [ ] 4.4 Remover `CollisionStressDemo.kt` e `SpawnerDemo.kt` (mover o que for reutilizável: `Spawner`, `Trap`, bola rígida)

## 5. Sprites & Tiles demo (funde Sprite + Animated + Tilemap)

- [ ] 5.1 Criar a demo `Sprites & Tiles`: `TileMap` (chão a partir do atlas real) + `AnimatedSprite2D` correndo + `Sprite2D` estático decorativo
- [ ] 5.2 Tornar o player um `CharacterBody2D` que se move sobre o chão de `StaticBody2D` (chão sólido via `makeStaticWall`/`StaticBody2D` à mão) usando `moveAndCollide`
- [ ] 5.3 Adicionar overlay de título/descrição via `CanvasLayer`/`Label`
- [ ] 5.4 Remover `SpriteDemo.kt`, `AnimatedSpriteDemo.kt`, `TileMapDemo.kt`

## 6. Rotating Frame & Tumbling Swarm (mantidas, ajustadas)

- [ ] 6.1 `Rotating Frame` (ex-`RotatingBoxDemo`): remover FPS/`drawText`, adicionar overlay `CanvasLayer`/`Label`; manter paredes locais via `makeStaticWall` e o gizmo de velocidade
- [ ] 6.2 `Tumbling Swarm` (ex-`TumblingSwarmDemo`): remover FPS/`drawText`, adicionar overlay `CanvasLayer`/`Label`; usar `hue` compartilhada; manter `BoundaryWalls`

## 7. Entrypoints & wiring

- [ ] 7.1 Atualizar `DemoSwitcherRoot.Slot` (ou estrutura equivalente) para o novo catálogo de 5: `Transforms`, `SpawnCollide`, `RotatingFrame`, `TumblingSwarm`, `SpritesTiles`
- [ ] 7.2 Confirmar que `Main` (Skiko) e `MainLwjgl` (LWJGL) constroem a mesma raiz com a mesma `GameConfig` e registram `AxesWidget` (sem novas dependências em `build.gradle.kts`)
- [ ] 7.3 Validar que a demo `Sprites & Tiles` roda cross-backend (Skiko + `runLwjgl`)

## 8. Tests & docs

- [ ] 8.1 Atualizar/recriar testes de unidade amarrados ao catálogo antigo (slots, teclas, topologia, "no Camera2D") para o novo catálogo de 5 + navegação por menu
- [ ] 8.2 Adicionar teste de fumaça: menu monta 5 botões; clicar carrega a demo; "← Menu" volta
- [ ] 8.3 Atualizar a tabela "Games" do `CLAUDE.md` para o novo catálogo (sem 10 cenas numeradas) e a seção de demos do `README.md` (resumo de uma linha por demo, navegação por menu)
- [ ] 8.4 Rodar `./gradlew :games:demos:run` e `:runLwjgl` e conferir as 5 demos + menu manualmente; rodar `openspec validate refactor-demos`
