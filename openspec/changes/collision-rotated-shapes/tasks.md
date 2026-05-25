## 1. Helper `obbCorners`

- [ ] 1.1 Em `engine/.../physics/Shape2D.kt`, extrair o loop dos 4 cantos rotacionados de `RectangleShape2D.bounds(...)` para uma função privada do arquivo `private fun obbCorners(world: Transform, size: Vec2, localOffset: Vec2): Array<Vec2>` que devolve os 4 cantos world-space (sem aplicar min/max). Aceitar `world.rotation == 0f` também (devolve cantos eixo-alinhados).
- [ ] 1.2 `RectangleShape2D.bounds(...)`: chamar `obbCorners(world, size, localOffset)` e calcular min/max sobre os 4 pontos. Comportamento preservado quando rotação é zero (cantos do retângulo eixo-alinhado) e quando não é (envelope AABB dos 4 cantos rotacionados).

## 2. SAT em `overlap` para o caso rect-rect rotacionado

- [ ] 2.1 Em `Shape2D.kt`, no `when` de `overlap(...)`, substituir o caso `a is RectangleShape2D && b is RectangleShape2D` por:
  ```kotlin
  a is RectangleShape2D && b is RectangleShape2D -> {
      if (aWorld.rotation == 0f && bWorld.rotation == 0f) {
          a.bounds(aWorld, Vec2.ZERO).intersects(b.bounds(bWorld, Vec2.ZERO))
      } else {
          obbVsObbOverlap(a, aWorld, b, bWorld)
      }
  }
  ```
- [ ] 2.2 Implementar `private fun obbVsObbOverlap(a, aWorld, b, bWorld): Boolean` usando SAT:
  - obter `cornersA = obbCorners(aWorld, a.size, Vec2.ZERO)` e `cornersB = obbCorners(bWorld, b.size, Vec2.ZERO)`.
  - obter os 4 eixos candidatos: 2 normais perpendiculares aos lados de A (basta `(cornersA[1] - cornersA[0])` rotacionado 90° e `(cornersA[2] - cornersA[1])` rotacionado 90°) e 2 análogas para B.
  - para cada eixo: projetar os 4 cantos de A e os 4 de B sobre o eixo (dot product), pegar `(minA, maxA)` e `(minB, maxB)`. Se `maxA < minB || maxB < minA`, separa → retornar `false`.
  - se nenhum eixo separou, retornar `true`.
- [ ] 2.3 Helper privado `private fun projectOnto(axis: Vec2, corners: Array<Vec2>): Pair<Float, Float>` para evitar duplicação.
- [ ] 2.4 Verificar que `Vec2` não precisa de novas operações (dot product pode ser inline `a.x*b.x + a.y*b.y`); se ficar feio, considerar `private fun dot(a: Vec2, b: Vec2): Float`.

## 3. Testes

- [ ] 3.1 Em `engine/src/test/kotlin/com/neoutils/engine/physics/`, criar `Shape2DOverlapTest.kt` (ou estender o `PhysicsSystemTest` existente) com os 4 cenários do spec:
  - 3.1.1 Dois retângulos a 45° com AABBs sobrepostos mas OBBs separados → `overlap()` retorna `false`.
  - 3.1.2 Dois retângulos a 45° com OBBs realmente tocando → `overlap()` retorna `true`.
  - 3.1.3 Dois retângulos axis-aligned (rotation = 0): caminho rápido AABB → mantém comportamento atual (overlap quando dx, dy < soma de meios-lados; separação no resto).
  - 3.1.4 Caso misto (um rotacionado, outro não) usa o caminho OBB.
- [ ] 3.2 Adicionar um teste de regressão para `RectangleShape2D.bounds(...)`: rotação 0 e rotação π/4 produzem os envelopes esperados (preserva contrato pós-refactor de 1.2).

## 4. Demo 5

- [ ] 4.1 Em `games/demos/.../RotatingBoxDemo.kt`, remover o comentário `// Known regression of collision-overhaul (KR2): ...` no `BoxedBall.onAreaEntered` (regressão resolvida).
- [ ] 4.2 Smoke manual: `./gradlew :games:demos:run`, tecla 5, observar que bolinhas batem e separam (não atravessam). Marcar no `tasks.md` da `collision-overhaul` (linha 14.3) que KR2 está resolvida quando esta change for arquivada.

## 5. Verify

- [ ] 5.1 `./gradlew check` passa.
- [ ] 5.2 `openspec validate collision-rotated-shapes --strict` passa.
- [ ] 5.3 Smoke `./gradlew :games:demos:run` (tecla 5) confirma fix visual.
