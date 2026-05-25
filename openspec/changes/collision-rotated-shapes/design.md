## Context

`collision-overhaul` introduziu `RectangleShape2D` com semântica completa: tamanho local, transform composto via `world()`, `bounds()` retornando AABB. Para o caso axis-aligned (`world.rotation == 0f`), `bounds()` é o próprio retângulo no mundo e o teste `overlap(rect, rect)` via `aabb.intersects(bAabb)` é exato.

Quando há rotação, `bounds()` precisa retornar **algo** porque (a) `PhysicsSystem` usa AABB world-space para broad phase O(N²) com rejeição rápida via `Rect.intersects`; (b) `DebugOverlay` (F2) desenha esses AABBs alinhados ao mundo projetado pela `Camera2D`. A escolha foi devolver o **AABB-envelope** dos 4 cantos rotacionados — funciona como rejeição conservadora (nunca rejeita um overlap real), mas é um superset do retângulo de verdade.

O caminho `overlap(rect, rect)` na change anterior delegou para `aabb.intersects(bAabb)` sem distinguir o caso rotacionado. Isso introduziu **falsos positivos persistentes**: dois quadrados a 45° com envelopes sobrepostos mas retângulos locais distantes são reportados como overlapping. Combinado com a semântica enter-only do `PhysicsSystem`, vira a regressão KR2 — o par grudou em `previousOverlapping` para sempre.

## Goals / Non-Goals

**Goals:**

- `overlap(RectangleShape2D, RectangleShape2D)` retorna `true` se, e somente se, os dois retângulos rotacionados realmente intersectam em coordenadas de mundo.
- Sem regressão de performance para o caso axis-aligned (que é o caminho dominante em Pong, Tic, hello-world, demo 4).
- Sem mudança em `bounds()` — mantém o contrato com broad phase e overlay.
- Cobertura de teste explícita do caso "envelopes sobrepostos, retângulos distantes" para travar a regressão.

**Non-Goals:**

- **Não** introduz uma nova `Shape2D` tipo `OBBShape2D` ou variante "oriented box" — o suporte a rotação vive dentro de `RectangleShape2D` mesmo (a rotação vem do `world.rotation` do nó pai, não do shape).
- **Não** muda `Shape2D.bounds()` para devolver OBB em vez de AABB — broad phase e overlay continuam alinhados ao mundo.
- **Não** trata `RectangleShape2D` contra `CircleShape2D` rotacionados (círculo é invariante à rotação; o caminho atual já é exato).
- **Não** resolve KR1 (pile-up tunneling) — essa fica para `collision-iterative-resolution`.
- **Não** trata rotações que aparecem do `scale` negativo ou outras transformações afins exóticas — o contrato é "rotação como ângulo em radianos no `Transform`".
- **Não** introduz raycast nem CCD para tunneling de alta velocidade (continua deferred).

## Decisions

### D1. SAT (Separating Axis Theorem) com 4 eixos, sem early-rejection de bounds

**Decisão:** quando `aWorld.rotation != 0f || bWorld.rotation != 0f`, computar os 4 cantos world-space de cada retângulo (helper interno `obbCorners`) e aplicar SAT em 4 eixos: as duas normais do OBB A (perpendiculares aos lados de A) e as duas normais do OBB B. Para cada eixo, projetar os 4 cantos de cada OBB e checar overlap das duas projeções (intervalos `[min, max]`). Se algum eixo separa (gap entre projeções), retorna `false`. Se nenhum separa, retorna `true`.

**Por que SAT em vez de testar cada vértice contra o outro polígono:** SAT é o padrão didático para OBB-vs-OBB; é O(1) (4 eixos × 8 projeções), simétrico e não precisa de classificação especial (vértice dentro do outro, aresta cruzando aresta, etc.). Para o tamanho deste engine, performance é dominada pela broad phase, não pelo teste exato.

**Por que não fazer broad-phase de novo dentro do `overlap`:** o `PhysicsSystem` já fez `aRect.intersects(bRect)` antes de chamar `overlap()` (linha "Cheap AABB rejection first" no `PhysicsSystem.step`). Não precisa repetir.

**Alternativa rejeitada — GJK:** mais geral (qualquer convexo) mas mais código por nenhum ganho aqui. Voltamos a ele em `game-asteroids` se aparecer `ConvexPolygonShape2D`.

### D2. `obbCorners` helper interno, top-level no package

**Decisão:** `private fun obbCorners(world: Transform, size: Vec2, localOffset: Vec2): Array<Vec2>` no mesmo arquivo `Shape2D.kt`. Já temos uma lógica idêntica em `RectangleShape2D.bounds(...)` (loop dos 4 cantos com rotação aplicada); refatorar isso para o helper e chamar de ambos os lados (`bounds()` continua tomando o envelope; `overlap()` usa os cantos crus).

**Por quê:** evita duplicação do mesmo loop de rotação. A função fica `internal` ou `private` do arquivo — não vaza para o resto da API pública.

### D3. Threshold `rotation == 0f` é igualdade exata

**Decisão:** o branch axis-aligned é `aWorld.rotation == 0f && bWorld.rotation == 0f` (igualdade exata em `Float`).

**Por quê:** o caminho do código que produz rotações zero é literal (`Transform()` defaulta `rotation = 0f`; `transform.copy(...)` não toca rotação se não passar). Não há acumulação de erro de ponto flutuante porque rotações de gameplay vêm de `transform.rotation + ω * dt` em scripts — uma vez não-zero, fica não-zero. Adicionar `abs(rotation) < epsilon` esconde bugs onde rotação acumula erro silenciosamente e mantém o caso lento.

**Custo:** se um dia o engine começar a normalizar rotação para `[0, 2π)`, valores como `2π` (que são igual a 0 modularmente) cairiam no caminho lento por engano. Quando isso acontecer, ajustar — não agora.

### D4. `bounds()` permanece como AABB-envelope

**Decisão:** `RectangleShape2D.bounds(world, localOffset)` retorna o mesmo AABB-envelope de hoje (4 cantos rotacionados → min/max). Sem mudança.

**Por quê:**

- `PhysicsSystem.step` usa `bounds()` para a rejeição rápida `aRect.intersects(bRect)` antes do `overlap()` exato. Manter AABB conservador faz a rejeição segura (nunca rejeita um overlap real).
- `DebugOverlay` (F2) desenha `worldBounds()` literalmente — usar OBB faria o overlay rodar `drawPolygon` em vez de `drawRect`, escopo a mais.
- Outras consumidoras potenciais (futuro inspetor visual, hit-testing) continuam recebendo AABB world-aligned, que é o que a maioria das ferramentas espera.

### D5. Demo 5 documenta no comentário que KR2 está resolvida

**Decisão:** ao final desta change, o bloco `// Known regression of collision-overhaul (KR2): ...` no `RotatingBoxDemo.BoxedBall.onAreaEntered` é removido (regressão resolvida). Sem outras mudanças no demo — o `onAreaEntered` continua sendo a lógica de bounce.

**Por quê:** o demo é o teste vivo da fix. Se SAT estiver certo, os AABBs dos retângulos rotacionados podem ficar sobrepostos, mas `overlap()` rejeita corretamente — `currentOverlapping` não inclui o par, então `_entered` refire normalmente nos próximos encontros reais.

## Risks / Trade-offs

- **R1. SAT bug introduzindo falso negativo** (rejeição de overlap real). Mitigação: três testes unitários cobrindo (a) AABB sobreposto + OBB distante = `false`; (b) AABB sobreposto + OBB tocando = `true`; (c) rotações iguais em vários ângulos com contato real = `true`. Smoke test final: rodar Demo 5 e verificar que bolinhas batem (e não atravessam).
- **R2. Custo extra no caminho rotacionado.** SAT é O(1) mas substancialmente mais aritmética que `aabb.intersects` (4 eixos × ~12 multiplicações). Em demos com 12 balls rotacionadas (Demo 5), são ~66 pares por step × broad-phase reject mais alta → poucos chegam ao SAT. Imperceptível. Documentado.
- **R3. `obbCorners` chamado duas vezes para o mesmo nó num único step.** Cada par (A, B) que cai no branch rotacionado chama `obbCorners(A)` e `obbCorners(B)`. Se A está em N pares, são N invocações redundantes. Aceitável por agora; otimização (cache por step) só se profiling mostrar custo real.
- **R4. Sinal da rotação.** SAT é simétrico em rotação positiva/negativa — não há decisão de sinal, só dot products. Sem risco.

## Migration Plan

1. Refatorar a lógica de "4 cantos rotacionados" em `RectangleShape2D.bounds()` para um `obbCorners(world, size, localOffset)` interno; `bounds()` chama o helper e tira min/max como antes (regressão zero do teste atual do `bounds`).
2. Adicionar branch em `overlap(a: Shape2D, aWorld, b: Shape2D, bWorld)`:
   ```kotlin
   a is RectangleShape2D && b is RectangleShape2D -> {
       if (aWorld.rotation == 0f && bWorld.rotation == 0f) {
           a.bounds(aWorld, Vec2.ZERO).intersects(b.bounds(bWorld, Vec2.ZERO))
       } else {
           obbVsObbOverlap(a, aWorld, b, bWorld)
       }
   }
   ```
3. Implementar `obbVsObbOverlap` usando `obbCorners` e um helper `projectOnto(axis, corners)` que devolve `(min, max)`.
4. Testes unitários (KR2 reproduzido pré-fix; resolvido pós-fix).
5. Remover comentário "Known regression KR2" em `RotatingBoxDemo.BoxedBall.onAreaEntered`.
6. Smoke manual: `./gradlew :games:demos:run`, tecla 5, observar que bolinhas batem em vez de atravessar.
