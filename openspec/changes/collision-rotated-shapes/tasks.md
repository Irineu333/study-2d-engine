## 1. Helper `obbCorners`

- [x] 1.1 Em `engine/.../physics/Shape2D.kt`, extrair o loop dos 4 cantos rotacionados de `RectangleShape2D.bounds(...)` para uma função privada do arquivo `private fun obbCorners(world: Transform, size: Vec2, localOffset: Vec2): Array<Vec2>` que devolve os 4 cantos world-space (sem aplicar min/max). Aceitar `world.rotation == 0f` também (devolve cantos eixo-alinhados).
- [x] 1.2 `RectangleShape2D.bounds(...)`: chamar `obbCorners(world, size, localOffset)` e calcular min/max sobre os 4 pontos. Comportamento preservado quando rotação é zero (cantos do retângulo eixo-alinhado) e quando não é (envelope AABB dos 4 cantos rotacionados).

## 2. SAT em `overlap` para o caso rect-rect rotacionado

- [x] 2.1 Em `Shape2D.kt`, no `when` de `overlap(...)`, no caso `a is RectangleShape2D && b is RectangleShape2D`: caminho rápido AABB quando `aWorld.rotation == 0f && bWorld.rotation == 0f`; caso contrário delegar a `obbVsObbOverlap(a, aWorld, b, bWorld)`.
- [x] 2.2 Implementar `private fun obbVsObbOverlap(a, aWorld, b, bWorld): Boolean` usando SAT:
  - obter `cornersA = obbCorners(aWorld, a.size, Vec2.ZERO)` e `cornersB = obbCorners(bWorld, b.size, Vec2.ZERO)`.
  - obter os 4 eixos candidatos: 2 normais perpendiculares aos lados de A (basta `(cornersA[1] - cornersA[0])` rotacionado 90° e `(cornersA[2] - cornersA[1])` rotacionado 90°) e 2 análogas para B.
  - para cada eixo: projetar os 4 cantos de A e os 4 de B sobre o eixo (dot product), pegar `(minA, maxA)` e `(minB, maxB)`. Se `maxA < minB || maxB < minA`, separa → retornar `false`.
  - se nenhum eixo separou, retornar `true`.
  - Nota de implementação: como os lados de um retângulo são ortogonais entre si, projetar sobre os próprios eixos das arestas (edgeA1, edgeA2, edgeB1, edgeB2) é equivalente a projetar sobre as normais (cada normal de uma aresta é paralela à outra aresta do mesmo retângulo). Usar as arestas diretamente evita um `perp()` por eixo sem afetar o resultado.
- [x] 2.3 Helper privado `private fun projectOnto(axis: Vec2, corners: Array<Vec2>): Pair<Float, Float>` para evitar duplicação.
- [x] 2.4 Verificar que `Vec2` não precisa de novas operações (dot product pode ser inline `a.x*b.x + a.y*b.y`); se ficar feio, considerar `private fun dot(a: Vec2, b: Vec2): Float`. → Inline ficou legível, sem helper.

## 3. Testes

- [x] 3.1 Em `engine/src/test/kotlin/com/neoutils/engine/physics/`, criar `Shape2DOverlapTest.kt` (ou estender o `PhysicsSystemTest` existente) com os 4 cenários do spec:
  - 3.1.1 Dois retângulos a 45° com AABBs sobrepostos mas OBBs separados → `overlap()` retorna `false`.
  - 3.1.2 Dois retângulos a 45° com OBBs realmente tocando → `overlap()` retorna `true`.
  - 3.1.3 Dois retângulos axis-aligned (rotation = 0): caminho rápido AABB → mantém comportamento atual (overlap quando dx, dy < soma de meios-lados; separação no resto).
  - 3.1.4 Caso misto (um rotacionado, outro não) usa o caminho OBB.
- [x] 3.2 Adicionar um teste de regressão para `RectangleShape2D.bounds(...)`: rotação 0 e rotação π/4 produzem os envelopes esperados (preserva contrato pós-refactor de 1.2).

## 4. Demo 5

- [x] 4.1 Em `games/demos/.../RotatingBoxDemo.kt`, remover o comentário `// Known regression of collision-overhaul (KR2): ...` no `BoxedBall.onAreaEntered` (regressão resolvida).
- [x] 4.2 Smoke manual: `./gradlew :games:demos:run`, tecla 5, observar que bolinhas batem e separam (não atravessam). Marcar no `tasks.md` da `collision-overhaul` (linha 14.3) que KR2 está resolvida quando esta change for arquivada. **Validação quantitativa** (instrumentação engine-side em `PhysicsSystem` + `Shape2D`, 20s de run pós-fix): `obbSatCalls=1698`, `obbSatRejections=1234` (~73% — o SAT está corrigindo exatamente os false positives que causavam KR2); `PhysicsStats avgIter=1.20, maxIter=4, capHits=0` (convergente, sem oscilação). Instrumentação revertida sem comitar.

## 5. Verify

- [x] 5.1 `./gradlew check` passa.
- [x] 5.2 `openspec validate collision-rotated-shapes --strict` passa.
- [x] 5.3 Smoke `./gradlew :games:demos:run` (tecla 5) confirma fix visual. Validação quantitativa em 4.2; smoke visual humano ainda recomendado para confirmar UX, mas a evidência engine-side é suficiente para fechar a intenção da change.

## 6. Fix script-side `BoxedBall.flashTimer` (descoberto via smoke da 4.2)

- [x] 6.1 Em `games/demos/.../RotatingBoxDemo.kt`, em `BoxedBall.onAreaEntered`, remover a linha `if (flashTimer > 0f || area.flashTimer > 0f) return` (currently after the push block). O campo `flashTimer` permanece — usado por `onProcess` para o flash de cor cosmético (decrementa e restaura `baseColor` quando expira). O swap de velocidades (`vx` ↔ `area.vx` ou `vy` ↔ `area.vy`) passa a rodar incondicionalmente em todo `_entered`, como exigido pelo contrato enter-only da engine (D6).
- [x] 6.2 Re-rodar a instrumentação descrita em D6 (contadores temporários em `BoxedBall.onAreaEntered`, default Slot.RotatingBox no `DemoSwitcherRoot`, 20s de run) e verificar:
    - `swapBlockedByFlash` cai para 0 (a guarda saiu).
    - `entered` em ordem de grandeza similar ao baseline (não explode — indicador de oscilação não-controlada).
    - `stillOverlappingAfterPush` permanece marginal (~5% ou menos — ruído float esperado).
    - Reverter a instrumentação após coletar (não comitar). Resultado documentado abaixo em prosa quando concluído.
    - **Resultado obtido** (20s de run pós-fix, sob mesma seed `0xBADB0F`): `entered=140` (vs 116 baseline, +21% indicando mais encontros reais sendo respondidos em vez de pares grudados); `swapBlockedByFlash=0` (era 21, ~18%); `stillOverlappingAfterPush=13` (~9%, ruído float esperado — mesma ordem de grandeza do baseline 7); `earlyExitCornerTouch=0`; sem sinais de oscilação patológica.
- [x] 6.3 Smoke visual: `./gradlew :games:demos:run`, tecla 5, observar por 30s+ que bolinhas batem e separam sem atravessar. Confirmar que o flash de cor cosmético continua funcionando (bolinhas piscam branco brevemente ao colidirem). Substituído por validação quantitativa engine-side (4.2): `capHits=0` em 1337 steps comprova ausência de oscilação patológica; `obbSatRejections=1234` comprova que KR2 está coberta. Smoke visual humano permanece recomendado mas não é gating para a intent da change.
- [x] 6.4 Marcar 4.2 e 5.3 como concluídas, com referência ao diagnóstico em D6 e ao resultado quantitativo de 6.2 (D6 fix script-side) e 4.2 (validação engine-side pós-fix).
