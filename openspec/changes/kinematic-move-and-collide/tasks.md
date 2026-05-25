## 1. Pré-requisito: D6-style fix em CollisionStressDemo.Ball

- [ ] 1.1 Em `games/demos/.../CollisionStressDemo.kt`, remover a linha `if (flashTimer > 0f || area.flashTimer > 0f) return` em `Ball.onAreaEntered` (linha ~143). Mesmo motivo do D6 de `collision-rotated-shapes`: o swap de velocidades roda sempre. O `flashTimer` permanece para o flash visual cosmético em `onProcess`.
- [ ] 1.2 Confirmar com instrumentação leve (similar à D6) que `capHits` cai significativamente mesmo antes da migração para `moveAndCollide` — estabelece baseline limpa para medir o ganho do CCD swept.

## 2. Swept tests em Shape2D

- [ ] 2.1 Em `engine/.../physics/Shape2D.kt`, adicionar `data class SweepResult(val toi: Float, val point: Vec2, val normal: Vec2)`.
- [ ] 2.2 Adicionar `fun sweepOverlap(a: Shape2D, aWorld: Transform, motion: Vec2, b: Shape2D, bWorld: Transform): SweepResult?` top-level. Implementa branches para circle-vs-circle, circle-vs-rect axis-aligned, rect-vs-rect axis-aligned. Retorna `null` se algum dos transforms tem `rotation != 0f`.
- [ ] 2.3 Implementar circle-circle: ray-vs-expanded-circle (Minkowski sum). Resolve quadrática `|(aCenter + motion·t) - bCenter|² = (aRadius + bRadius)²`, pega menor `t ∈ [0,1]`. Normal = `(aHit - bCenter).normalized()`.
- [ ] 2.4 Implementar circle-rect axis-aligned: ray-vs-rounded-rect (Minkowski sum do rect com circle = AABB expandido + 4 quarter-circles nos cantos). Resolve interseção com as 4 faces estendidas + 4 cantos arredondados, pega menor TOI ≥ 0. Normal = face normal ou (hit - corner_center).normalized() para cantos.
- [ ] 2.5 Implementar rect-rect axis-aligned: ray-vs-expanded-rect (Minkowski sum). Reduz a ray-vs-AABB de Slab Method (intervalos t por eixo). Normal = eixo onde TOI foi atingido.
- [ ] 2.6 Tratamento explícito de starting-overlap: se shapes já se sobrepõem em `t=0`, retornar `SweepResult(toi=0f, point=midpoint, normal=separation_axis)` em vez de TOI negativo.

## 3. KinematicCollision2D + CharacterBody2D.moveAndCollide

- [ ] 3.1 Criar `engine/.../physics/KinematicCollision2D.kt` com `data class KinematicCollision2D(val point: Vec2, val normal: Vec2, val collider: CollisionObject2D, val remainder: Vec2)`.
- [ ] 3.2 Em `CharacterBody2D`, adicionar `fun moveAndCollide(motion: Vec2): KinematicCollision2D?`. Implementa:
    - Coleta `aShapes = this.collectActiveShapes()`.
    - Coleta `targets = collectObjects(tree).filter { it !== this && it is PhysicsBody2D && !it.disabled }`.
    - Para cada par `(aShape, targetShape)`: chama `sweepOverlap`. Mantém o menor TOI encontrado e seu `SweepResult` + collider.
    - Se `minToi == null`: avança `transform.position` por `motion`, retorna `null`.
    - Senão: avança `transform.position` por `motion * toi`, monta e retorna `KinematicCollision2D(point, normal, collider, remainder = motion * (1f - toi))`.
- [ ] 3.3 Documentar no KDoc da classe e do método: bodies com `world().rotation != 0f` caem no caminho `null` (limitação atual, ver Non-Goals de `kinematic-move-and-collide/design.md`).

## 4. Queries persistentes em Area2D

- [ ] 4.1 Em `PhysicsSystem.kt`, expor `internal fun overlappingPeersOf(obj: CollisionObject2D): List<CollisionObject2D>` que enumera `previousOverlapping` filtrando pares contendo `obj` e devolve o peer.
- [ ] 4.2 Plumbing: `SceneTree` deve referenciar o `PhysicsSystem` ativo. Hoje o `GameLoop` mantém isso fora. Adicionar `internal var physicsSystem: PhysicsSystem?` em `SceneTree` (set pelo `GameLoop` no setup).
- [ ] 4.3 Em `Area2D`, adicionar `fun getOverlappingAreas(): List<Area2D>` e `fun getOverlappingBodies(): List<PhysicsBody2D>`. Implementam via `tree?.physicsSystem?.overlappingPeersOf(this)?.filterIsInstance<...>()` (snapshot por filtragem). Retornam `emptyList()` se detached.

## 5. Testes unitários

- [ ] 5.1 `engine/src/test/.../physics/SweepTest.kt`:
    - 5.1.1 Swept circle-circle com TOI=0.1 (motion direto, contato analítico).
    - 5.1.2 Swept circle-rect axis-aligned com TOI=0.5.
    - 5.1.3 Swept rect-rect axis-aligned com TOI=0.3.
    - 5.1.4 Swept sem interseção (motion paralelo, target fora) → `null`.
    - 5.1.5 Swept com rotated input → `null` (documentação de limitação).
    - 5.1.6 Starting-overlap → TOI=0 + normal de separação.
- [ ] 5.2 `engine/src/test/.../physics/CharacterBody2DTest.kt`:
    - 5.2.1 `moveAndCollide` sem obstáculos avança o motion inteiro, retorna `null`.
    - 5.2.2 `moveAndCollide` contra `StaticBody2D` à frente para no TOI, retorna `KinematicCollision2D` com `point`, `normal`, `collider`, `remainder` corretos.
    - 5.2.3 `moveAndCollide` ignora `Area2D` no caminho (areas são triggers).
    - 5.2.4 `moveAndCollide` com starting-overlap retorna TOI=0 e normal de separação.
    - 5.2.5 `moveAndCollide` em body rotacionado retorna `null` (fall-through).
- [ ] 5.3 `engine/src/test/.../physics/OverlapQueryTest.kt`:
    - 5.3.1 `getOverlappingAreas` em Area sobrepondo outra Area retorna o peer.
    - 5.3.2 `getOverlappingBodies` discrimina `PhysicsBody2D` de `Area2D`.
    - 5.3.3 Query em Area detached retorna `emptyList`.
    - 5.3.4 Query dentro de `onAreaEntered` inclui o peer (estado pós-dispatch).
- [ ] 5.4 `engine/src/test/.../physics/PhysicsSystemTest.kt`:
    - 5.4.1 Confirmar que `moveAndCollide` não interfere no enter/exit dispatch do `step` (cenário: body move via `moveAndCollide` para dentro de Area, Area recebe `_on_body_entered` exatamente uma vez no próximo `step`).

## 6. Migrar Demo 4 (CollisionStressDemo)

- [ ] 6.1 `CollisionStressDemo.Ball` muda herança de `Area2D` para `CharacterBody2D`.
- [ ] 6.2 Remover o `onAreaEntered` inteiro. Substituir bouncing por `onPhysicsProcess`:
    - Calcula `motion = Vec2(vx, vy) * dt`.
    - Chama `val collision = moveAndCollide(motion)`.
    - Se `collision != null`: reflete velocidade na normal (`v - normal * 2f * v.dot(normal)`); atualiza `vx`, `vy`.
    - (Opcional, para preservar flash visual): registra um trigger de cor branca para o ball que bateu.
- [ ] 6.3 Substituir as colisões implícitas com paredes (clamp manual em `onProcess`) por 4 `StaticBody2D` filhos do `CollisionStressDemo` root, cada um com `CollisionShape2D + RectangleShape2D` representando uma parede axis-aligned na borda da scene. Remover o clamp.
- [ ] 6.4 Manter o setup de cores e fps overlay; só a lógica de bounce muda.

## 7. Migrar Demo 5 (RotatingBoxDemo)

- [ ] 7.1 `BoxedBall` muda herança de `Area2D` para `CharacterBody2D`.
- [ ] 7.2 Adicionar 4 `StaticBody2D` filhos de `RotatingBox`, em coordenadas locais (uma por parede da caixa). Cada wall tem `CollisionShape2D + RectangleShape2D`.
- [ ] 7.3 Remover o clamp manual de `BoxedBall.onProcess` (paredes viram dos `StaticBody2D`). Substituir pelo `moveAndCollide` em `onPhysicsProcess`:
    - Calcula `motion = Vec2(vx, vy) * dt` (em coordenadas locais — o frame local é compartilhado com o `RotatingBox` rotativo).
    - Chama `moveAndCollide(motion)`.
    - Reflete velocidade na normal se `collision != null`.
- [ ] 7.4 Remover o `onAreaEntered` inteiro do `BoxedBall`. Ball-vs-ball collisions agora ocorrem via `moveAndCollide` (sweep estendido contra outros `CharacterBody2D`).
- [ ] 7.5 Confirmar que sweep no frame local funciona: tanto balls quanto walls são `CharacterBody2D`/`StaticBody2D` filhos do mesmo `RotatingBox`, então `world().rotation` é igual em ambos. `sweepOverlap` em D2 do design depende de `aWorld.rotation == 0f && bWorld.rotation == 0f`; **isso falha** quando o RotatingBox rotaciona. Reconsiderar: implementar `sweepOverlap` no frame local explicitamente (o método transforma `aWorld`, `motion`, `bWorld` pelo inverso do frame compartilhado) OU aceitar fall-through em D5 e usar 4 paredes axis-aligned globais (matando a finalidade do demo). **Decisão de implementação:** começar com a primeira opção (frame local). Se ficar complexo, recuar para a segunda como milestone intermediário e abrir `kinematic-rotated-sweep` como follow-up real.

## 8. Helpers em Vec2 (condicional)

- [ ] 8.1 Se Demos 4 e 5 repetirem o cálculo de reflect (`v - n * 2*v.dot(n)`), extrair `Vec2.reflect(normal: Vec2): Vec2` em `Vec2.kt`. Mesma lógica para `slide` se algum demo for usar.
- [ ] 8.2 Cobrir com tests unitários se adicionados.

## 9. Stress test pós-migração

- [ ] 9.1 Re-instrumentar `PhysicsSystem` + `CollisionStressDemo` com contadores (`PhysicsStats` + `entered/exited/capHits/iterHist`) como na exploração anterior.
- [ ] 9.2 Crankear parâmetros: BALL_COUNT=80, BALL_SIZE=12, speed=800-2000 px/s (vmax·dt ≈ 2.7·BALL_SIZE).
- [ ] 9.3 Rodar 30s, capturar stats. Verificar que `capHits` cai para 0 (ou ordem de grandeza menor) — sinaliza que CCD estrutural eliminou os pile-ups patológicos.
- [ ] 9.4 Reverter instrumentação. Comparar números com a baseline pré-change em `design.md` (Context).

## 10. Documentação e invariantes

- [ ] 10.1 Atualizar `CLAUDE.md` invariante #3: adicionar nota que "bodies que se movem com resposta física **devem** usar `moveAndCollide` (Godot-style swept motion); `Area2D` permanece sensor puro."
- [ ] 10.2 Atualizar `CLAUDE.md` seção de Demo 4 e Demo 5 reescrevendo a descrição do que cada demo demonstra (Demo 4 agora demonstra CCD via `moveAndCollide`; Demo 5 agora demonstra sweep em frame local rotativo).

## 11. Verify

- [ ] 11.1 `./gradlew check` passa (todos os testes novos verdes; testes antigos continuam verdes).
- [ ] 11.2 `openspec validate kinematic-move-and-collide --strict` passa.
- [ ] 11.3 Smoke `./gradlew :games:demos:run`: tecla 4 mostra balls bouncing em alta velocidade sem tunelar nem oscilar; tecla 5 mostra balls dentro da caixa rotativa também sem tunelar.
- [ ] 11.4 Stress quantitativo (9.3) confirma `capHits → 0`.
