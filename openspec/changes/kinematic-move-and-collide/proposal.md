## Why

Os demos físicos do engine (Demo 4 CollisionStress e Demo 5 RotatingBox) usam hoje `Area2D` para corpos que se movem e respondem fisicamente — empurrando posições e trocando velocidades dentro de `_on_area_entered`. Esse é um anti-pattern Godot: em Godot, `Area2D` é trigger puro (sensor), e corpos que se movem com resposta física usam `CharacterBody2D.move_and_collide(motion)` — uma API de sweep test que avança o corpo até o time-of-impact contra outros bodies e devolve a colisão para o script refletir na normal. Stress test (80 balls, `vmax·dt ≈ 2.7·BALL_SIZE`) mostrou que mesmo com o iterative loop funcionando, ~5.6% dos steps esgotam o cap de 8 iterações em pile-ups patológicos onde o swap-no-eixo-dominante não converge. A causa raiz é estrutural: o script reage **depois** que o engine detectou overlap discreto — não há sweep, não há TOI, e velocidades altas geram tunneling impossível de prevenir post-hoc.

Esta change introduz `CharacterBody2D.moveAndCollide(motion: Vec2): KinematicCollision2D?` como o canal canônico para corpos que se movem, com CCD swept embutido por construção, e migra os demos para o idiom correto. Resolve tunneling de velocidade alta estruturalmente (o engine garante, não o script), reforça o invariante #3 (`Area2D` trigger-only, bodies fazem física) e alinha o engine com a API mais identificável de Godot (`move_and_collide` literal).

## What Changes

- **NEW**: `CharacterBody2D.moveAndCollide(motion: Vec2): KinematicCollision2D?` — método em `:engine` que faz sweep da shape do body do ponto atual a `position + motion` contra todos os `StaticBody2D`/`CharacterBody2D` ativos no tree, avança o `transform.position` até o menor TOI encontrado (ou `motion` completo se sem colisão), e devolve `KinematicCollision2D(point, normal, collider, remainder)` ou `null`.
- **NEW**: `KinematicCollision2D` — data class imutável em `:engine` representando o resultado de uma colisão swept (ponto de contato em world space, normal apontando do body que se move para fora do collider atingido, referência ao collider, e `remainder: Vec2` = motion não-consumido para o script aplicar sliding se desejar).
- **NEW**: swept-shape tests internos em `Shape2D.kt` — `sweepOverlap(...)` cobrindo (a) circle-vs-circle, (b) circle-vs-rect axis-aligned, (c) rect-vs-rect axis-aligned. **Rotated swept tests ficam deferred** para change futura (`kinematic-rotated-sweep`) — Demos 4/5 não precisam.
- **NEW**: `Area2D.getOverlappingAreas(): List<Area2D>` e `Area2D.getOverlappingBodies(): List<PhysicsBody2D>` — queries persistentes (Godot-equivalente) para scripts que precisam saber "quem está em mim agora" sem violar enter-only.
- **MIGRATION**: `CollisionStressDemo` (Demo 4) migrado de `Area2D` para `CharacterBody2D`, scripts usando `moveAndCollide` + reflect na normal no lugar de `transform.position += vel*dt` + `_on_area_entered`.
- **MIGRATION**: `RotatingBoxDemo` (Demo 5) — `BoxedBall` migrado de `Area2D` para `CharacterBody2D`. As paredes da caixa rotativa viram `StaticBody2D` filhos do `RotatingBox` (também swept-tested).
- **DOCUMENTATION**: D6-style fix em `CollisionStressDemo.Ball.onAreaEntered` (remoção da guarda `flashTimer`) sai como pré-requisito antes da migração — mantido durante a transição.
- **NO CHANGE**: enter-only signals continuam (`areaEntered`, `bodyEntered`, etc.); o loop convergente em `PhysicsSystem.step` continua; nenhum constraint solver introduzido.

## Capabilities

### New Capabilities

- `kinematic-move-and-collide`: API kinematic-Godot-style com sweep test embutido. Cobre `CharacterBody2D.moveAndCollide`, `KinematicCollision2D`, swept-shape tests entre primitivos axis-aligned, e queries persistentes de overlap em `Area2D`.

### Modified Capabilities

(nenhuma — `engine-core` já cobre `CollisionObject2D`/`CharacterBody2D` em traços abstratos; a nova API é uma adição não-disruptiva, então mora numa capability própria. Demos não têm specs próprias.)

## Impact

- **Código tocado:**
  - `engine/.../physics/Shape2D.kt`: adiciona `sweepOverlap(a, aWorld, aMotion, b, bWorld): SweepResult?` para os 3 pares axis-aligned. Mantém `overlap(...)` discreto intacto.
  - `engine/.../physics/CollisionObject2D.kt` (ou novo `KinematicCollision2D.kt`): novo data class `KinematicCollision2D`.
  - `engine/.../physics/PhysicsBody2D.kt` (subclasse `CharacterBody2D`): novo método `moveAndCollide(motion)`.
  - `engine/.../physics/Area2D.kt`: novos métodos `getOverlappingAreas()` e `getOverlappingBodies()` — implementação usa o `previousOverlapping` do `PhysicsSystem` ativo via `tree`.
  - `engine/.../physics/PhysicsSystem.kt`: expor `currentOverlappingFor(obj)` internal ou similar para alimentar os queries (não muda dispatch).
  - `engine/src/test/.../physics/SweepTest.kt`: novos testes unitários — swept circle-circle, swept circle-rect, swept rect-rect (TOI conhecido analiticamente).
  - `engine/src/test/.../physics/OverlapQueryTest.kt`: testes para `getOverlappingAreas/Bodies`.
  - `games/demos/.../CollisionStressDemo.kt`: migração completa.
  - `games/demos/.../RotatingBoxDemo.kt`: migração completa.
- **Performance:** cada `moveAndCollide` faz O(N) broad-phase sobre bodies (vs O(N²) no `step`). Para Demo 4 com 80 balls movendo-se, são 80 sweeps × ~80 broad-phase tests = 6400 testes/frame — mesma ordem de grandeza do `step()` atual. Imperceptível em demos didáticos.
- **Sem impacto em:** `PhysicsSystem.step` (enter/exit signals continuam exatos pra Areas); Pong (usa `Area2D` para gols, que é o uso correto); TicTacToe (sem física); Hello World (sem física); `Spawner` Demo (3) e `Orbit`/`Scale` (1/2).
- **Deferred:** swept tests para shapes rotacionados (CCD em rotated bodies) ficam para change futura `kinematic-rotated-sweep`. Sweep contra OBB precisa de Minkowski + raycast em rotated frame, escopo maior.
- **Deferred:** `RigidBody2D` com solver Box2D-style continua non-goal explícito (CLAUDE.md invariante #3 + Godot's design philosophy distingue `RigidBody2D` de `CharacterBody2D` — esta change foca no segundo).
