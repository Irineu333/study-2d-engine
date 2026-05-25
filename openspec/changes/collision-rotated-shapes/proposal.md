## Why

`collision-overhaul` deferiu o teste de sobreposição entre `RectangleShape2D`s rotacionados — quando `world.rotation != 0f`, `RectangleShape2D.bounds(...)` calcula o **AABB-envelope** dos 4 cantos rotacionados e `overlap(rect, rect)` apenas intersecta esses AABBs. Para um par de quadrados rotacionados a 45°, os envelopes inflam por √2 (~141%) e ficam permanentemente sobrepostos mesmo quando os retângulos locais estão muito longe.

Combinado com a semântica enter-only de `PhysicsSystem` (`_entered` dispara uma vez no início da sobreposição, `_exited` no fim), isso vira a regressão **KR2** documentada em `collision-overhaul/design.md`: o par entra em `previousOverlapping` na primeira frame e nunca mais sai — `_entered` não refire e Demo 5 (RotatingBox) exibe bolinhas atravessando livremente após a primeira separação parcial.

Esta change introduz **OBB-vs-OBB exato** (Separating Axis Theorem em 4 eixos perpendiculares aos lados) no caminho `overlap(RectangleShape2D, RectangleShape2D)` quando ao menos um dos dois tem `rotation != 0f`. Quando ambos têm rotação zero, mantém o caminho rect-rect AABB atual (mais barato, idêntico ao OBB para axis-aligned).

## What Changes

- `overlap(a: Shape2D, aWorld: Transform, b: Shape2D, bWorld: Transform)` em `engine/.../physics/Shape2D.kt`: o caminho rect-rect ganha branch para o caso rotacionado.
  - Se `aWorld.rotation == 0f && bWorld.rotation == 0f`: comportamento atual (AABB-vs-AABB exato).
  - Caso contrário: SAT-OBB-vs-OBB — projeta os 4 cantos de cada retângulo sobre os 4 eixos candidatos (2 normais de cada OBB), testa overlap das projeções em todos; nenhum eixo separa → colisão.
- `RectangleShape2D.bounds(world, localOffset)`: **inalterado**. O AABB-envelope continua sendo o que o `PhysicsSystem` usa para broad phase (rejeição rápida) e o que o `DebugOverlay` desenha em F2. Apenas o teste exato no `overlap()` muda.
- Helper interno `private fun obbCorners(world, size, localOffset): Array<Vec2>` no mesmo arquivo, reutilizado pelo branch novo.
- Teste unitário cobrindo: (a) dois retângulos rotacionados a 45° com `bounds()` sobrepostos mas retângulos locais distantes — deve retornar `false`; (b) dois retângulos com rotação igual e contato real — deve retornar `true`; (c) regression dos casos axis-aligned existentes — sem mudança.
- Demo 5 (`RotatingBoxDemo`) na change anterior tem comentário marcando KR2; remover esse comentário porque a regressão fica resolvida.

## Capabilities

### New Capabilities

(nenhuma — modifica capability existente)

### Modified Capabilities

- `engine-core`: requisito de overlap exato em `Shape2D` ganha cláusula adicional para retângulos rotacionados.

## Impact

- **Código tocado:**
  - `engine/.../physics/Shape2D.kt` — branch novo em `overlap(rect, rect)`, helper `obbCorners`, teste SAT.
  - `engine/src/test/kotlin/com/neoutils/engine/physics/PhysicsSystemTest.kt` (ou um `Shape2DTest.kt` novo) — três cenários novos.
  - `games/demos/.../RotatingBoxDemo.kt` — remoção do comentário "Known regression KR2".
  - `openspec/changes/collision-overhaul/design.md` (durante archive) ou anotação no archive: KR2 marcada como resolvida por esta change.
- **Documentação:** nenhuma mudança em `CLAUDE.md` (overlap continua sendo detalhe interno).
- **Sem impacto em:** `RectangleShape2D.bounds()` (preserva contrato AABB-envelope), `DebugOverlay` (continua desenhando AABB), `PhysicsSystem.step` (continua usando AABB para broad phase), Demo 4 (não envolve rotação — KR1 fica para `collision-iterative-resolution`).
