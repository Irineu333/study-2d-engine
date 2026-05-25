## 1. Pré-trabalho: harness behavioral (faz primeiro pra estabelecer rede de segurança)

- [x] 1.1 Criar `engine/src/test/.../physics/BehavioralSweepTest.kt` com infraestrutura mínima: `NoopRenderer`, `NoopInput`, helper `runFrames(tree, n, dtNanos)`, helper `recordPositions(node, frames)`. Pode reusar `NoopRenderer`/`NoopInput` da `GameLoopTest` se for conveniente — caso contrário duplique localmente pra evitar dependência transversal.
- [x] 1.2 Adicionar três cenários AXIS-ALIGNED como baseline (verificam que o harness pega os bugs já fixados): (a) ball CharacterBody2D vs StaticBody2D wall, motion frontal → após bounce não há re-collision em 3 frames; (b) duas balls CharacterBody2D spawnadas overlapping → separadas em ≤ 5 frames; (c) ball em arena de 4 walls axis-aligned → após 60 frames de bouncing, distância total percorrida > 50% da free-flight expectation.
- [x] 1.3 Rodar `./gradlew :engine:test --tests "*BehavioralSweepTest*"` e garantir verde — confirma que o harness funciona com o caminho axis-aligned existente. Se algum cenário falhar com o código atual, é um bug que precisa ser tratado antes de seguir.

## 2. Implementar circle-vs-rotated-rect

- [x] 2.1 Em `Shape2D.kt`, adicionar `private fun inverseRotate(p: Vec2, radians: Float): Vec2` (rotate por `-radians`).
- [x] 2.2 Adicionar `private fun sweepCircleRotatedRect(circle, circleWorld, motion, rect, rectWorld): SweepResult?` que: (a) calcula `delta = circleWorld.position - rectWorld.position`; (b) `localDelta = inverseRotate(delta, rectWorld.rotation)`; (c) `localMotion = inverseRotate(motion, rectWorld.rotation)`; (d) constrói `localCircleWorld = Transform(position = rectWorld.position + localDelta, rotation = 0)` e `localRectWorld = Transform(position = rectWorld.position, rotation = 0)`; (e) chama `sweepCircleRect(circle, localCircleWorld, localMotion, rect, localRectWorld)`; (f) rotaciona o resultado de volta — `point` rotacionado por `+rectWorld.rotation` em torno de `rectWorld.position`; `normal` rotacionado por `+rectWorld.rotation`; `depenetration` rotacionado por `+rectWorld.rotation`.
- [x] 2.3 Adicionar `private fun sweepRotatedRectVsCircle(rect, rectWorld, motion, circle, circleWorld): SweepResult?` via dualidade: chama `sweepCircleRotatedRect(circle, circleWorld, -motion, rect, rectWorld)`; nega normal e depenetration; recalcula `point` no frame original similar ao `sweepRectVsCircle` axis-aligned existente.

## 3. Implementar rotated-rect-vs-rotated-rect (SAT temporal)

- [x] 3.1 Em `Shape2D.kt`, adicionar `private fun projectScalar(v: Vec2, axis: Vec2): Float` (dot product simples — reusa convenção do `projectOnto` existente).
- [x] 3.2 Adicionar `private fun sweepRotatedRectRotatedRect(a, aWorld, motion, b, bWorld): SweepResult?` implementando SAT temporal: (a) coleta `cornersA = obbCorners(aWorld, a.size, Vec2.ZERO)` e `cornersB = obbCorners(bWorld, b.size, Vec2.ZERO)`; (b) calcula os 4 eixos SAT (2 normais de A, 2 normais de B — mesma derivação do `obbVsObbOverlap` estático); (c) para cada eixo: projeta intervalos `[minA, maxA]` e `[minB, maxB]` (via `projectOnto` existente), projeta motion sobre o eixo → `dt`; (d) computa `[tIn, tOut]` por eixo conforme D2; (e) `tEnter = max(tIn)`, `tExit = min(tOut)`; se `tEnter > tExit` ou `tEnter > 1f` ou `tExit < 0f`, return null; (f) starting-overlap: se todos os intervalos sobrepõem em `t=0` (= overlap inicial verdadeiro) → calcula MTV (eixo de menor overlap × overlap magnitude) → retorna `SweepResult(toi=0f, depenetration=MTV, ...)`; (g) guard `tEnter < 0f → return null` (tangent-leaving, mesma lógica de `kinematic-move-and-collide`); (h) caso normal: `toi = tEnter`, `normal` = eixo onde tEnter foi atingido (apontando de B pra A), `point` = ponto médio do contato (escolha didática consistente com o axis-aligned).
- [x] 3.3 Edge case `dt == 0` (motion paralela ao eixo): se intervalos NÃO overlap, eixo separa permanentemente → return null; se overlap, eixo permite todo `t` (intervalo `[-inf, +inf]` mas NÃO contribui pra tEnter/tExit, exceto que ainda contribui pra MTV no caso starting-overlap).

## 4. Wire em sweepOverlap e remover bailout antigo

- [x] 4.1 Em `sweepOverlap`, remover a linha `if (aWorld.rotation != 0f || bWorld.rotation != 0f) return null` do topo.
- [x] 4.2 Atualizar o `when` para usar fast paths axis-aligned e slow paths rotacionados conforme D4 do design.md.
- [x] 4.3 Atualizar KDoc de `sweepOverlap` removendo a cláusula "Both rotations MUST be 0f"; substituir por descrição dos 5 caminhos cobertos.

## 5. Atualizar moveAndCollide

- [x] 5.1 Em `CharacterBody2D.moveAndCollide`, remover do KDoc o bullet "shape com non-zero composed rotation no parent frame retorna `null`". Manter a limitação restante (cross-parent-frame).
- [x] 5.2 Sem mudança no corpo do método — ele já chama `sweepOverlap` e respeita o que volta. O novo caminho rotacionado vem "de graça" agora que sweepOverlap não bailout.

## 6. Testes unitários (chamada única)

- [x] 6.1 `SweepTest.kt`: cenário "swept circle-vs-rotated-rect TOI analítico" — circle aproximando da face frontal de um rect rotated 90°, TOI conhecido.
- [x] 6.2 `SweepTest.kt`: cenário "swept rotated-rect-vs-rotated-rect same rotation contato face-a-face" — dois quadrados rotated 45° aproximando frontalmente no eixo rotacionado, TOI conhecido.
- [x] 6.3 `SweepTest.kt`: cenário "swept rotated-rect-vs-rotated-rect different rotation contato canto-face" — rect A 0° vs rect B 45°, A aproximando — TOI ≥ 0 e collision corretamente detectada.
- [x] 6.4 `SweepTest.kt`: cenário "swept rotated com motion paralela ao eixo" (`dt == 0` em um dos eixos) — comportamento esperado: separação ou overlap consistente.
- [x] 6.5 `SweepTest.kt`: cenário "swept rotated com motion zero" — degenera a SAT estático, retorna null (separados) ou toi=0 + depenetration (overlapping).
- [x] 6.6 `SweepTest.kt`: cenário "swept rotated tangent-leaving guard" — body tocando face rotada e movendo away → null.
- [x] 6.7 `SweepTest.kt`: cenário "swept rotated starting-overlap" — dois rects rotated overlapping; toi=0; depenetration aponta na direção MTV correta.

## 7. Cenários behavioral pra rotated paths

- [x] 7.1 `BehavioralSweepTest.kt`: cenário "rotated bounce" — ball CharacterBody2D 30° vs StaticBody2D wall 30°, motion frontal, 30 frames → bounce detectado, separa em 3 frames, velocidade reflete corretamente.
- [x] 7.2 `BehavioralSweepTest.kt`: cenário "rotated spawn-overlap" — ball + wall spawnados overlapping num parent rotated 45°, motion zero, 5 frames → separação alcançada.
- [x] 7.3 `BehavioralSweepTest.kt`: cenário "rotated arena" — 1 ball CharacterBody2D dentro de 4 walls StaticBody2D rotated 45° formando losango, 60 frames → distância total percorrida > 50% da free-flight expectation.

## 8. Documentação

- [x] 8.1 `CLAUDE.md` invariante #3: adicionar nota — "sweep cobre os três pares com qualquer combinação de rotação dos transforms, contanto que ambos vivam no mesmo frame parent local".
- [x] 8.2 KDoc de `sweepCircleRotatedRect` / `sweepRotatedRectVsCircle` / `sweepRotatedRectRotatedRect` explicando a estratégia (frame transform vs SAT temporal) e referenciando design.md desta change.
- [x] 8.3 KDoc de `SweepResult` re-confirmando que `depenetration` é populado nos starting-overlap (mesma documentação atual; verificar que continua precisa pra paths rotacionados).

## 9. Verify

- [x] 9.1 `./gradlew check` passa (todos os testes novos verdes; testes antigos continuam verdes).
- [x] 9.2 `openspec validate kinematic-rotated-sweep --strict` passa.
- [x] 9.3 Re-rodar smoke de Demos 4 e 5 (`./gradlew :games:demos:run`): comportamento idêntico (axis-aligned não regrediu). Marcar como "validado" se não houver diferença visível.
- [x] 9.4 (Bonus opcional) Estender Demo 5 pra incluir balls com `transform.rotation != 0f` localmente — demonstra visualmente o caminho rotacionado. Fora do escopo mínimo; só se sobrar tempo. — Implementado como Demo 6 (`RotatedSweepDemo`): arena axis-aligned com 16 rectangulares balls (size 22×10) cada uma com `transform.rotation` aleatório; todo ball-vs-wall e ball-vs-ball routes through `sweepRotatedRectRotatedRect`.
