## 1. Extract snapshot helper

- [x] 1.1 Em `engine/.../physics/PhysicsSystem.kt`, extrair as linhas que coletam `currentOverlapping` (collect + nested for-loop + `anyShapePairOverlaps`) para `private fun computeOverlapping(objects: List<CollisionObject2D>): HashSet<UnorderedPair<CollisionObject2D>>`. Verificar via build que o `step()` atual funciona quando reescrito chamando este helper uma vez.

## 2. Convergence loop

- [x] 2.1 Adicionar `private const val MAX_RESOLUTION_ITERATIONS = 8` no companion object (criar `companion object { private const val TAG = "PhysicsSystem" }` se não existir).
- [x] 2.2 Reescrever `step(tree)` para o loop convergente (snippet em `design.md` D1).
- [x] 2.3 Atualizar o KDoc de `PhysicsSystem` mencionando o loop convergente e o teto.

## 3. Tests

- [x] 3.1 `PhysicsSystemTest`: novo teste `three-body pile-up emits all chained entered events in one step` correspondendo ao primeiro Scenario do spec.
- [x] 3.2 Verificar que os testes existentes (`overlapping body pair fires bodyEntered exactly once on each`, `sustained overlap does not re-fire enter`, `exit fires when overlap ends`, ...) continuam passando sem modificação.
- [x] 3.3 Novo teste de fail-safe: criar dois `StaticBody2D` cujo `onBodyEntered` empurra para fora e `onBodyExited` empurra de volta. Rodar `step()` e verificar (a) que o log de warning é emitido (capturar via `Log` interceptor se existir, senão skip; documentar) e (b) que `step()` retorna sem exception.

## 4. Demo 4

- [x] 4.1 Em `games/demos/.../CollisionStressDemo.kt`, remover o comentário `// Known regression of collision-overhaul (KR1): ...` no `Ball.onAreaEntered` (regressão resolvida).
- [ ] 4.2 Smoke manual: `./gradlew :games:demos:run`, tecla 4, deixar rodar 10s — observar que bolinhas não atravessam mais (ou que tunneling residual é causado por velocidades excessivamente altas, não por pile-ups).

## 5. Verify

- [x] 5.1 `./gradlew check` passa.
- [x] 5.2 `openspec validate collision-iterative-resolution --strict` passa.
- [ ] 5.3 Smoke `./gradlew :games:demos:run` (tecla 4) confirma fix visual.
