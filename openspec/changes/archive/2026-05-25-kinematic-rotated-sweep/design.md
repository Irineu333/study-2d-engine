## Context

`kinematic-move-and-collide` introduziu `CharacterBody2D.moveAndCollide(motion)` cobrindo as três combinações axis-aligned (circle-circle, circle-rect, rect-rect) via `sweepOverlap`. Quando ao menos um dos dois transforms tem `rotation != 0f` no frame compartilhado, `sweepOverlap` retorna `null` e `moveAndCollide` cai num "free advance" sem CCD. O `PhysicsSystem.step` discreto então pode (ou não) detectar o overlap pós-fato. O caminho discreto + iterative loop existente cobre o caso, mas perde a propriedade estrutural anti-tunneling que `moveAndCollide` oferece — bodies rotacionados com velocidade alta voltam a tunelar.

Pano de fundo geométrico:
- **Circle vs Rotated Rect**: o círculo é invariante a rotação. Transformar o problema pro frame local do rect (rotação inversa do rect aplicada ao centro do círculo + à motion) torna o rect axis-aligned. O `sweepCircleRect` existente cobre. Cheap.
- **Rotated Rect vs Rotated Rect**: precisa de SAT temporal. A versão estática (`obbVsObbOverlap` de `collision-rotated-shapes`) projeta os cantos de cada OBB sobre 4 eixos candidatos e checa intervalos `[min, max]`. A versão temporal estende: enquanto A se desloca por `motion·t` (t ∈ [0, 1]), a projeção dos cantos de A sobre cada eixo é uma **função linear de t**. Computar o intervalo de t em que as projeções se sobrepõem e intersectar todos dá `[tEnter, tExit]`.

Pano de fundo de testes: o post-mortem dos dois freeze bugs do `kinematic-move-and-collide` (tangent-leaving + spawn-overlap) revelou uma lacuna: `SweepTest` cobre **chamada-única com TOI analítico**, mas bugs que emergem **ao longo de múltiplos frames** (oscilação patológica, falha de separação) ficam invisíveis. O harness behavioral introduzido nesta change estabelece o pattern pra cobrir essa classe.

## Goals / Non-Goals

**Goals:**

- `sweepOverlap` cobre os três pares rotacionados (circle-vs-rotated-rect, rect-vs-circle no espelho, rotated-rect-vs-rotated-rect) com TOI exato e normal correta.
- `CharacterBody2D.moveAndCollide` deixa de bailout em bodies rotacionados — comportamento CCD-correto pra qualquer combinação de rotações no parent frame compartilhado.
- Starting-overlap continua resolvendo via `depenetration` (contrato sem mudança), agora também pros casos rotacionados.
- `BehavioralSweepTest` introduzido como template de teste multi-frame, com cenários cobrindo bounce + spawn-overlap + arena rotacionada.
- Performance: O(1) por par, na mesma ordem de magnitude do caminho axis-aligned.

**Non-Goals:**

- **Não** suporta sweeps cross-parent-frame (continua exigindo `target.parent === this.parent`).
- **Não** suporta bodies que **rotacionam durante** o sweep (`this.transform.rotation` é snapshot no início do `moveAndCollide`; rotações progressivas exigem múltiplas chamadas).
- **Não** muda `bounds()` — continua AABB envelope (broad phase + overlay continuam consistentes).
- **Não** introduz "ConvexPolygonShape2D" arbitrário (continua limitado a rect + circle).
- **Não** muda `PhysicsSystem.step` discreto.
- **Não** introduz substepping ou multi-sample sweep (single sweep por chamada).
- **Não** trata escala não-uniforme rotacionada como caso especial (o sweep de OBB rotacionado já cobre escala uniforme via `world.scale`; non-uniform scale + rotation é território de shearing, deixado como future work se aparecer demanda).

## Decisions

### D1. Circle-vs-rotated-rect via inverse-rotation pro frame local do rect

**Decisão**: quando o par é `(CircleShape2D, RectangleShape2D)` e o rect tem `rotation != 0f`:

1. Compute `rotation_inv = -rectWorld.rotation`.
2. Rotacione `circleWorld.position - rectWorld.position` por `rotation_inv` → posição do centro do círculo no frame local do rect.
3. Rotacione `motion` por `rotation_inv` → motion no frame local do rect.
4. Construa `circleLocalWorld = Transform(position = rotatedCenter + rectWorld.position, rotation = 0)` e `rectLocalWorld = Transform(position = rectWorld.position, rotation = 0)`.
5. Chame `sweepCircleRect(circle, circleLocalWorld, rotatedMotion, rect, rectLocalWorld)`.
6. Result: TOI invariante (rotação preserva distâncias). `point` é no frame local — precisa ser rotacionado de volta pro frame original. `normal` idem.

**Por que essa abordagem**: aproveita 100% da matemática axis-aligned já testada (`sweepCircleRect`, com refinement de quarter-circle nos cantos). Custo extra: 1 rotação de motion + 1 rotação de centro + 2 rotações inversas do resultado. ~20 ops. Trivial.

**Alternativa rejeitada — Minkowski sum rotacionado direto no frame original**: precisa de quarter-circle rotacionado, parametrização do contato angular, mais propenso a bug de boundary. Sem ganho prático.

### D2. Rotated-Rect-vs-Rotated-Rect via SAT temporal

**Decisão**: SAT estendido pro domínio temporal. Algoritmo:

1. Compute corners de A em `aWorld` (4 cantos via `obbCorners`).
2. Compute corners de B em `bWorld` (idem).
3. Compute 4 eixos SAT: as duas normais dos lados de A (perpendiculares às arestas TL→TR e TL→BL) e as duas normais dos lados de B.
4. Para cada eixo `n`:
   - Projete os 4 cantos de A sobre `n` → intervalo `[minA, maxA]`.
   - Projete os 4 cantos de B sobre `n` → intervalo `[minB, maxB]`.
   - Projete `motion` sobre `n` → `dt` (delta de A's projection por unidade de tempo).
   - Se `dt == 0`: se `[minA, maxA]` não overlap `[minB, maxB]`, eixo separa permanentemente → `return null`. Senão, eixo permite overlap em todo `t`.
   - Se `dt != 0`: ache o intervalo `[t_in, t_out]` em que as projeções (A deslocada por `dt·t`) sobrepõem. Equivale a resolver:
     - `maxA + dt·t >= minB` AND `minA + dt·t <= maxB`
     - dá `t_in = (minB - maxA) / dt` e `t_out = (maxB - minA) / dt`, com swap se `dt < 0`.
5. `tEnter = max(t_in_axis_i)` para todos os eixos. `tExit = min(t_out_axis_i)`. Se `tEnter > tExit` → `return null` (separadas durante todo o sweep). Senão TOI = `tEnter`, mas...
6. Starting-overlap detection: se `obbVsObbOverlap(A, B)` retorna `true` no início (ou equivalente: todos os intervalos contêm `t = 0`), trata como toi=0 + depenetration (D3).
7. tEnter < 0 guard: análogo ao caso axis-aligned — se `tEnter < 0`, A foi inside no passado e está saindo agora → `return null` (não é colisão nova). O caminho starting-overlap acima cobre o caso de overlap real.

**Por que SAT temporal e não GJK/raycast contra Minkowski**: SAT é simétrico, didático, escala bem com mais axes (se um dia entrar polígono convexo n-gon). Custo é O(num_axes · num_corners) = O(4·4) = 16 dot products + 8 motion-projections. Imperceptível.

**Alternativa rejeitada — transformar pro frame local de uma das OBBs**: torna a outra OBB rotacionada, o problema é OBB-vs-OBB de novo. Não simplifica nada (vs caso circle-rect onde o círculo é invariante).

### D3. Depenetration pra OBB-vs-OBB starting-overlap usa o eixo SAT de menor overlap

**Decisão**: quando overlap inicial é detectado, o vetor de depenetration é o eixo SAT cuja **sobreposição é mínima** (overlap = `min(maxA, maxB) - max(minA, minB)`), com direção apontando de B (target) pra A (mover). Magnitude = overlap. Normal = mesmo eixo (unitizado).

**Por que esse critério**: equivalente ao "minimum translation vector" (MTV) clássico de SAT. Empurra A no sentido de menor distância pra separar.

**Edge case**: se um eixo tem `dt == 0` (motion paralela a esse eixo) e `[minA, maxA]` overlap `[minB, maxB]` permanentemente, esse eixo NÃO contribui pra TOI mas DEVE contribuir pra MTV (depenetration). Garantir.

### D4. `sweepOverlap` decide qual caminho via `when` ortogonal

**Decisão**: o `when` atual de `sweepOverlap` ganha branches:

```
a is CircleShape2D && b is CircleShape2D -> sweepCircleCircle  // já cobre rotacionado (círculo é invariante)
a is CircleShape2D && b is RectangleShape2D ->
    if (bWorld.rotation == 0f) sweepCircleRect   // axis-aligned fast path
    else sweepCircleRotatedRect                  // novo
a is RectangleShape2D && b is CircleShape2D ->
    if (aWorld.rotation == 0f) sweepRectVsCircle // axis-aligned fast path
    else sweepRotatedRectVsCircle                // novo (via dualidade -motion)
a is RectangleShape2D && b is RectangleShape2D ->
    if (aWorld.rotation == 0f && bWorld.rotation == 0f) sweepRectRect  // fast path
    else sweepRotatedRectRotatedRect             // novo
```

Remove a guarda `if (aWorld.rotation != 0f || bWorld.rotation != 0f) return null` do topo.

**Por que branch explícito**: mantém o caminho axis-aligned barato (fast path). Apenas pares com rotação não-zero pagam o custo extra.

### D5. Behavioral integration test pattern

**Decisão**: novo arquivo `engine/src/test/.../physics/BehavioralSweepTest.kt` introduzindo um harness multi-frame com primitivos:

```kotlin
private fun runFrames(tree: SceneTree, count: Int, dtNanos: Long = 16_666_666L) {
    val loop = GameLoop(tree, NoopRenderer, NoopInput, PhysicsSystem())
    repeat(count) { loop.tick(dtNanos) }
}

private fun trackPositions(node: Node2D, frames: Int, ...): List<Vec2> { ... }
```

Cada teste constrói uma scene, roda N frames, e assert **propriedades comportamentais** sobre a sequência de posições:

- **Monotonia entre bounces**: posição não inverte direção sem ter passado por um bounce reportado.
- **Eventual separação**: bodies spawnados overlapping têm distância crescente nos primeiros K frames.
- **Energia conservada (aproximadamente)**: velocidade scalar do mover não decresce > X% no período (bouncing perfeito).
- **Ausência de oscilação patológica**: se posição t e t+2 estão a < epsilon de distância, mas t+1 está a > delta, é freeze.

Cenários cobertos nesta change:

1. **Rotated bounce**: ball (CharacterBody2D 30°) bate em wall (StaticBody2D 30°) frontal — separa em ≤ 3 frames, velocidade reflete corretamente.
2. **Rotated spawn-overlap**: ball + wall spawnados overlapping com rotação 45° — depenetration aplica, próxima frame mostra separação.
3. **Rotated arena**: 4 walls a 45° (losango) com 1 ball bouncing — após 60 frames, distância total percorrida > X (não congelou).

**Por que aqui e não em change separada**: a metodologia nasce no contexto que mais precisa dela (sweep math), valida diretamente esta change, e fica como template pra próximas. Bundling reduz overhead de coordenação e dá ao harness um "primeiro consumidor" real.

**Por que não retroagir aos cenários do `kinematic-move-and-collide` archived**: change archived é imutável; cobertura behavioral retroativa entra como verificação aqui (cenários axis-aligned do harness também cobrem os bugs daquela change), provando que o harness pegaria.

### D6. KDoc + CLAUDE.md atualizados

**Decisão**: o KDoc atual de `sweepOverlap` ("Both `aWorld.rotation` and `bWorld.rotation` MUST be `0f`") é substituído por descrição do que cada par cobre, com mention explícito de que sweep rotacionado funciona quando o body e o target compartilham o frame de origem (i.e., o `moveAndCollide` o transformou pro mesmo parent local). CLAUDE.md invariante #3 ganha uma frase: "sweep cobre os três pares com qualquer combinação de rotação dos transforms, contanto que ambos vivam no mesmo frame parent local".

KDoc de `CharacterBody2D.moveAndCollide` perde o bullet "**Limitations: ... shape com non-zero composed rotation no parent frame retorna `null`**". Limitação restante (cross-parent-frame) permanece documentada.

## Risks / Trade-offs

- **R1. SAT temporal bug de boundary**. O algoritmo tem dois lugares delicados: (a) `dt == 0` (motion paralela ao eixo) — eixo separa permanentemente vs sempre overlap; (b) swap de `t_in/t_out` quando `dt < 0`. Mitigação: testes unitários cobrindo cada um (D5 cenário "rotated bounce" exercita `dt != 0` na maioria dos eixos, e um teste dedicado "motion paralela ao eixo X de A" exercita `dt == 0`).
- **R2. tEnter < 0 guard nos novos paths**. O mesmo bug de freeze do `kinematic-move-and-collide` (tangent-leaving) pode aparecer na versão rotacionada se o guard for esquecido. Mitigação: o BehavioralSweepTest "rotated bounce" diretamente exercita o cenário post-bounce → tangente. Mais: replicar o guard nos novos sweep funcs com comment cross-referenciando o tangent-leaving lesson.
- **R3. Rotação inversa em circle-vs-rotated-rect introduz erro FP cumulativo se motion for muito pequena**. Mitigação: para motion muito pequena, o sweepCircleRect já retorna toi=1 ou null cleanly. Pequenos erros não causam freeze (a depenetration cobre starting-overlap).
- **R4. Custo do harness behavioral nos demais módulos de teste**. `BehavioralSweepTest` roda GameLoop com N=60 ticks por cenário. Tempo total esperado < 200ms (engine puro, sem renderer real). Aceitável dentro do orçamento de `./gradlew check`.
- **R5. Overshooting de depenetration em cenários simétricos (dois CharacterBody2D mutuamente overlapping)**. Já existe na change anterior — cada body push-out do penetration completo, separação dobra. Não regride com rotação; mitigação fica como follow-up (`moveAndCollide` half-depen quando target é também CharacterBody2D não-disabled).
- **R6. SAT temporal degenera se `motion = Vec2.ZERO`**. Todos os `dt` ficam zero; o algoritmo reduz a SAT estático (= overlap check). Cobrir com unit test "motion zero rotated rect-rect" → ou starting-overlap (toi=0 + depen) ou null.

## Migration Plan

1. **Implementar `sweepCircleRotatedRect`** em `Shape2D.kt` (D1). Helper interno `private fun inverseRotate(p: Vec2, radians: Float): Vec2`. Testes analíticos com rect 45° e círculo aproximando.
2. **Implementar `sweepRotatedRectVsCircle`** via dualidade `-motion` + inverse-rotate do normal/point.
3. **Implementar `sweepRotatedRectRotatedRect`** com SAT temporal (D2). Helper `private fun projectInterval(corners, axis): Pair<Float, Float>` (reusa do estático) + `private fun projectScalar(v: Vec2, axis: Vec2): Float`. Testes analíticos: contato face-a-face simétrico, contato canto-face oblíquo, motion paralela ao eixo (caso `dt == 0`).
4. **Wire em `sweepOverlap`** — substitui o early bail por `when` com fast/slow paths (D4).
5. **Aplicar o guard `tEnter < 0 → null`** consistentemente nos novos paths (R2).
6. **Implementar `BehavioralSweepTest`** com os três cenários (D5).
7. **Remover Limitations no KDoc de `CharacterBody2D.moveAndCollide`** (D6).
8. **Atualizar CLAUDE.md invariante #3** (D6).
9. **`./gradlew check`** verde; **`openspec validate --strict`** verde.
10. **Smoke manual** opcional: se um demo simples puder ser construído pra exercitar visualmente bodies rotacionados bouncing (ou enhancing Demo 5 com balls que rotacionam localmente), incluir como follow-up bonus.
