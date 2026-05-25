## 1. Extract snapshot helper

- [ ] 1.1 Em `engine/.../physics/PhysicsSystem.kt`, extrair as linhas que coletam `currentOverlapping` (collect + nested for-loop + `anyShapePairOverlaps`) para `private fun computeOverlapping(objects: List<CollisionObject2D>): HashSet<UnorderedPair<CollisionObject2D>>`. Verificar via build que o `step()` atual funciona quando reescrito chamando este helper uma vez.

## 2. Convergence loop

- [ ] 2.1 Adicionar `private const val MAX_RESOLUTION_ITERATIONS = 8` no companion object (criar `companion object { private const val TAG = "PhysicsSystem" }` se não existir).
- [ ] 2.2 Reescrever `step(tree)` para o loop convergente:
  ```kotlin
  fun step(tree: SceneTree) {
      previousOverlapping.removeAll { !it.a.isLive || !it.b.isLive }
      val objects = collectObjects(tree).filter { !it.disabled }
      tree.beginPhysicsPhase()
      try {
          var iteration = 0
          var dispatchedSomething = true
          while (dispatchedSomething && iteration < MAX_RESOLUTION_ITERATIONS) {
              val currentOverlapping = computeOverlapping(objects)
              val newlyEntered = currentOverlapping - previousOverlapping
              val newlyExited = previousOverlapping - currentOverlapping
              dispatchedSomething = newlyEntered.isNotEmpty() || newlyExited.isNotEmpty()
              for (pair in newlyExited) dispatchExit(pair)
              for (pair in newlyEntered) dispatchEnter(pair)
              previousOverlapping.clear()
              previousOverlapping.addAll(currentOverlapping)
              iteration++
          }
          if (iteration == MAX_RESOLUTION_ITERATIONS && dispatchedSomething) {
              Log.w(TAG, "step hit MAX_RESOLUTION_ITERATIONS=$MAX_RESOLUTION_ITERATIONS — pile-up not converged")
          }
      } finally {
          tree.endPhysicsPhase()
      }
  }
  ```
- [ ] 2.3 Atualizar o KDoc de `PhysicsSystem` mencionando o loop convergente e o teto.

## 3. Tests

- [ ] 3.1 `PhysicsSystemTest`: novo teste `three-body pile-up emits all chained entered events in one step` correspondendo ao primeiro Scenario do spec.
- [ ] 3.2 Verificar que os testes existentes (`overlapping body pair fires bodyEntered exactly once on each`, `sustained overlap does not re-fire enter`, `exit fires when overlap ends`, ...) continuam passando sem modificação.
- [ ] 3.3 Novo teste de fail-safe: criar dois `StaticBody2D` cujo `onBodyEntered` empurra para fora e `onBodyExited` empurra de volta. Rodar `step()` e verificar (a) que o log de warning é emitido (capturar via `Log` interceptor se existir, senão skip; documentar) e (b) que `step()` retorna sem exception.

## 4. Demo 4

- [ ] 4.1 Em `games/demos/.../CollisionStressDemo.kt`, remover o comentário `// Known regression of collision-overhaul (KR1): ...` no `Ball.onAreaEntered` (regressão resolvida).
- [ ] 4.2 Smoke manual: `./gradlew :games:demos:run`, tecla 4, deixar rodar 10s — observar que bolinhas não atravessam mais (ou que tunneling residual é causado por velocidades excessivamente altas, não por pile-ups).

## 5. Verify

- [ ] 5.1 `./gradlew check` passa.
- [ ] 5.2 `openspec validate collision-iterative-resolution --strict` passa.
- [ ] 5.3 Smoke `./gradlew :games:demos:run` (tecla 4) confirma fix visual.
