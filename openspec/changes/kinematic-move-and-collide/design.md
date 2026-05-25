## Context

O modelo de colisão atual do `:engine` (consolidado em `collision-overhaul`, refinado em `collision-rotated-shapes` e `collision-iterative-resolution`) é **discreto e enter-only**: `PhysicsSystem.step(tree)` enumera todos os `CollisionObject2D`, testa pares em broad phase O(N²), e dispatcha `_entered`/`_exited` uma vez por par-transição. O script responde no `_entered` empurrando posições e ajustando velocidades. Isso funciona até **algum** limite — `collision-iterative-resolution` melhorou a convergência em cascatas (KR1), `collision-rotated-shapes` corrigiu o false-positive de AABB-envelope em OBBs rotacionados (KR2), e o D6 do `collision-rotated-shapes` documentou que o script tem que respeitar o contrato enter-only inteiro (não pular swap de velocidade).

Stress test recente (Demo 4 com 80 balls, BALL_SIZE=12, `vmax=2000 px/s` → `vmax·dt ≈ 2.7·BALL_SIZE`) revelou o limite estrutural do modelo discreto: ~5.6% dos `step()`s esgotam o cap de 8 iterações em pile-ups patológicos. `entered/exited` permanecem simétricos (engine não perde pares), mas os pile-ups oscilam sem convergir. A causa é arquitetural: o script reage **depois** que o overlap discreto foi detectado; sem sweep + TOI, velocidades altas geram tunneling impossível de prevenir post-hoc.

Em Godot, esse problema é resolvido na fonte: `CharacterBody2D.move_and_collide(motion)` faz **sweep test embutido** — avança o body até o time-of-impact contra outros bodies, devolve a colisão para o script aplicar a resposta (geralmente reflect na normal). CCD vira parte do contrato de movimento, não opt-in via flag. O nengine já tem `CharacterBody2D` como tipo de Node, mas hoje é funcionalmente uma `Area2D` (sem sweep API), o que força os demos a usar `Area2D` para tudo — incluindo bouncing balls, que em Godot real seriam `CharacterBody2D` ou `RigidBody2D`.

Esta change introduz o caminho Godot-canônico: `moveAndCollide` na `CharacterBody2D`, com infraestrutura de swept tests para axis-aligned shapes em `Shape2D.kt`. Migra Demos 4 e 5 para o idiom correto. Adiciona queries persistentes (`getOverlappingAreas/Bodies`) que Godot tem para complementar o modelo enter-only.

## Goals / Non-Goals

**Goals:**

- API canônica Godot: `body.moveAndCollide(motion) -> KinematicCollision2D?` cobre o caso "move e bata em algo" em uma chamada, com swept test e TOI exato.
- CCD swept embutido para os 3 pares axis-aligned: circle-circle, circle-rect, rect-rect. Tunneling de alta velocidade vira estruturalmente impossível para `CharacterBody2D` movido via `moveAndCollide`.
- `KinematicCollision2D` data class imutável com `point`, `normal`, `collider`, `remainder` — suficiente para o script aplicar reflect, slide, ou stop.
- Queries persistentes `Area2D.getOverlappingAreas()` / `getOverlappingBodies()` — Godot-equivalente para scripts que precisam de "still touching" sem reintroduzir signal correspondente.
- Demos 4 e 5 migrados para `CharacterBody2D` + `moveAndCollide` + reflect, eliminando os 5.6% de `capHits` estrutural do stress test.
- Preserva enter-only signals (`areaEntered`, `bodyEntered`, ...) e o loop convergente do `PhysicsSystem` — coexistem com `moveAndCollide`.
- Preserva todos os invariantes #1-5 do CLAUDE.md.

**Non-Goals:**

- **Não** introduz constraint solver tipo Box2D/Bullet. `RigidBody2D` continua não-existindo no engine.
- **Não** faz swept test contra OBBs rotacionados. Demos 4/5 usam corpos axis-aligned. Sweep contra OBB precisa de Minkowski + raycast em rotated frame, escopo grande, fica para change futura `kinematic-rotated-sweep`.
- **Não** adiciona signal "still touching" / "stay". Queries `getOverlapping*` são pull-mode, não push.
- **Não** muda `PhysicsSystem.step` nem o dispatch enter/exit — `moveAndCollide` é **complemento**, não substituto. Para corpos que escolherem não usar `moveAndCollide`, o comportamento atual segue idêntico.
- **Não** introduz `velocity` integration automática em `CharacterBody2D` (continua slot puro, integração é do script — `collision-overhaul` design D2 mantido).
- **Não** introduz substepping. Step físico continua fixo a `physicsHz` (60Hz default). Substepping é Unity-flavor, Godot não usa.
- **Não** muda Pong, TicTacToe, Hello World, Demos 1/2/3.

## Decisions

### D1. `moveAndCollide` na `CharacterBody2D`, devolve `KinematicCollision2D?`

**Decisão:**

```kotlin
data class KinematicCollision2D(
    val point: Vec2,        // contato em world space
    val normal: Vec2,       // unit vector apontando do body que se move pra fora do collider atingido
    val collider: CollisionObject2D,
    val remainder: Vec2,    // motion não-consumido (motion - actuallyTraveled)
)

class CharacterBody2D : PhysicsBody2D() {
    fun moveAndCollide(motion: Vec2): KinematicCollision2D?
}
```

Comportamento:
1. Coleta `aShapes = this.collectActiveShapes()` no estado atual.
2. Coleta `targets = collectObjects(tree).filter { it !== this && it is PhysicsBody2D && !it.disabled }` — só `StaticBody2D` e `CharacterBody2D` bloqueiam. `Area2D` ignorada (são triggers).
3. Para cada par `(aShape, targetShape)`: chama `sweepOverlap(aShape, aWorld, motion, targetShape, targetWorld)` — devolve `SweepResult(toi: Float, point: Vec2, normal: Vec2)` ou `null`.
4. Pega o menor `toi` entre todos os pares. Se `null` em todos → sem colisão; avança `transform.position` por `motion` inteiro, retorna `null`.
5. Se algum `toi < 1f` → avança `transform.position` por `motion * toi` (corpo encosta no contato); retorna `KinematicCollision2D(point, normal, collider, remainder = motion * (1 - toi))`.

Por quê esse formato: literal Godot. Scripts viram:
```kotlin
val motion = velocity * dt
val collision = moveAndCollide(motion)
if (collision != null) {
    velocity = velocity.reflect(collision.normal)
    // opcional: slide o resto -> moveAndCollide(collision.remainder.slide(collision.normal))
}
```

**Alternativa rejeitada — devolver `List<KinematicCollision2D>`:** Godot também devolve só a primeira (a com menor TOI). Múltiplas colisões num mesmo move são raríssimas em axis-aligned shapes; quando acontecem, o caller pode chamar `moveAndCollide` outra vez com `remainder` (slide). Manter API simples.

**Alternativa rejeitada — `moveAndSlide` (Godot também tem):** `move_and_slide` em Godot internamente faz loop chamando `move_and_collide` + slide até consumir motion ou bater muitos planos. Útil para platformers (escorregar em rampas). Para os demos atuais (bouncing balls), reflect direto resolve. `moveAndSlide` fica como follow-up se game-platformer aparecer.

### D2. Swept tests em `Shape2D.kt` cobrindo só pares axis-aligned

**Decisão:**

```kotlin
data class SweepResult(val toi: Float, val point: Vec2, val normal: Vec2)

fun sweepOverlap(
    a: Shape2D, aWorld: Transform, motion: Vec2,
    b: Shape2D, bWorld: Transform,
): SweepResult?
```

Cobre 3 pares quando `aWorld.rotation == 0f && bWorld.rotation == 0f`:

- **Circle vs Circle**: ray-vs-expanded-circle (Minkowski sum). Distância de `aWorld.position` a `bWorld.position`, sweep ao longo de `motion`, resolve quadrática. Normal = `(aHit - b.center).normalized()`.
- **Circle vs Rect (axis-aligned)**: ray-vs-rounded-rect (Minkowski sum do rect com circle = rect expandido + quarter-circles nos cantos). Resolve em 4 faces + 4 cantos, pega menor TOI ≥ 0. Normal = face normal ou (hit - corner_center).normalized().
- **Rect vs Rect (axis-aligned)**: ray-vs-expanded-rect (Minkowski sum). Reduz a ray-vs-AABB classic. Normal = face axis (eixo onde TOI foi atingido).

Se algum dos dois shapes tem `world.rotation != 0f`: `sweepOverlap` retorna `null` (cai no fast-path discreto do step loop convergente). Documentado como limitação atual; resolvido em `kinematic-rotated-sweep` futura.

**Por que axis-aligned only:** Demos 4 e 5 funcionam com axis-aligned (Demo 4 nunca rotaciona; Demo 5 tem o RotatingBox como parent, mas as paredes da caixa, ao virar `StaticBody2D`, podem ser ou (a) axis-aligned localmente e o sweep roda em local frame, ou (b) rotated e fall-back para discreto — uma ou outra escolha tratada no design de Demo 5 abaixo, ver D4). Cobre 100% dos casos demonstrados sem entrar em Minkowski rotado.

**Alternativa rejeitada — sweep universal via GJK:** mais geral mas escopo maior; também precisa de EPA pra normal de contato; mais código por nenhum ganho nos casos atuais.

### D3. Queries persistentes em `Area2D` via `PhysicsSystem` ativo

**Decisão:**

```kotlin
class Area2D : CollisionObject2D() {
    fun getOverlappingAreas(): List<Area2D>
    fun getOverlappingBodies(): List<PhysicsBody2D>
}
```

Implementação: consulta `tree.physicsSystem?.previousOverlapping` (precisa expor o set internal via getter package-private), filtra pares contendo `this`, classifica peer por tipo. Custo O(K) onde K = pares overlapping atuais (pequeno, normalmente <10).

Garantia: ler estes métodos **dentro** de `_process`/`_physics_process`/`_on_*_entered` devolve o estado overlap **atual após o último dispatch enter/exit** (consistente com o que o script acabou de receber).

**Alternativa rejeitada — manter um set local por `Area2D`:** Godot faz assim (cada Area2D rastreia seu próprio set). Mais barato por query (O(1)), mais memória, mais paths de invalidação. Aceitar O(K) per query até virar gargalo medido — vantagem didática (uma fonte de verdade no `PhysicsSystem`).

**Alternativa rejeitada — só `_entered`/`_exited` sem query:** o ponto da change incluir os queries é completar o contrato Godot. Sem eles, scripts que precisam saber "tem alguém em mim agora?" voltam a manter set próprio (que é o que `_entered`/`_exited` deveria evitar duplicar).

### D4. Migração de Demo 5 (RotatingBox) — paredes como `StaticBody2D` filhos do parent rotativo

**Decisão:** o `RotatingBox` ganha 4 filhos `StaticBody2D`, um por parede, cada um com `CollisionShape2D + RectangleShape2D` em coordenadas locais. As paredes herdam a rotação do parent via `world()`. Os 12 balls viram `CharacterBody2D` filhos do mesmo parent, integram velocidade local no `onProcess`/`onPhysicsProcess`, chamam `moveAndCollide(motion)` que sweepa contra as paredes E contra outros balls.

**Problema:** as paredes rodam com o parent. Quando rodam, ficam não-axis-aligned no world frame. `sweepOverlap` retorna `null` nesse caso (D2). Como resolver?

**Solução:** os balls vivem em coordenadas locais do `RotatingBox` (já vivem hoje). `moveAndCollide` opera em coordenadas locais — converte `motion` (que é local) e as posições para o frame local do `RotatingBox`. Como as paredes E os balls compartilham o frame local rotativo, `world.rotation` para o teste de sweep é **a rotação relativa** entre os dois (zero para sibling em mesmo frame). Sweep axis-aligned cobre.

Implementação: `moveAndCollide` extrai o frame compartilhado via menor ancestral comum e roda sweep nesse frame. Quando ancestrais diferem em rotação, retorna `null` (caso raro nos demos atuais).

**Alternativa rejeitada — paredes axis-aligned globais (caixa não rotaciona):** mata a finalidade de Demo 5 (demonstra composição de rotação ancestral).

### D5. Demo 4 mantém balls bouncing em paredes da scene (world frame), todos axis-aligned

**Decisão:** `CollisionStressDemo` migra para `CharacterBody2D` para os balls. As paredes da scene viram 4 `StaticBody2D` filhos do demo root, axis-aligned, sem rotação. Sweep cobre 100% dos pares (ball-vs-wall e ball-vs-ball são ambos axis-aligned em world frame).

Bouncing logic do script vira:
```kotlin
override fun onPhysicsProcess(dt: Float) {
    val motion = Vec2(vx, vy) * dt
    var collision = moveAndCollide(motion)
    if (collision != null) {
        val v = Vec2(vx, vy)
        val reflected = v - collision.normal * (2f * v.dot(collision.normal))
        vx = reflected.x; vy = reflected.y
        // opcional: aplicar slide pro remainder; pra bouncing puro, basta refletir
    }
}
```

Note que isso elimina **completamente** o `onAreaEntered` lidando com bounce. O script vira Godot-canônico.

### D6. `PhysicsSystem.step` permanece, mas Demo 4/5 não dependem mais dele para resposta

**Decisão:** `PhysicsSystem.step` continua rodando como hoje. Para Areas (Pong gols, Spawner trap), o enter/exit dispatch continua exato. Para Demo 4/5 pós-migração, `CharacterBody2D`s não disparam `_entered` com outros bodies via `step` — eles **detectam** colisões via `moveAndCollide` direto. O `step` ainda detecta overlap-after-move se o script avançou body para dentro de outro por erro, e dispatcha `_entered` como fallback didático (sinaliza bug no script).

**Por quê manter o step:** consistência com o resto do engine. Areas continuam contando. `_entered`/`_exited` continuam o contrato canônico de transição. O `moveAndCollide` é apenas o caminho **estrutural** para evitar tunneling antes que entered seja necessário.

### D7. `KinematicCollision2D` é imutável + dataclass

**Decisão:** `data class` com `point`, `normal`, `collider`, `remainder`. Sem mutators. Reflete o estilo do `Transform`, `Vec2`, `Rect` (CLAUDE.md "Imutabilidade onde for barata").

Helper potencial: `Vec2.reflect(normal: Vec2): Vec2`, `Vec2.slide(normal: Vec2): Vec2` (Godot tem). Adicionar em `Vec2.kt` se demos repetirem o cálculo — D8 marca como follow-up se necessário.

### D8. Tests cobrem TOIs analíticos conhecidos

**Decisão:** `SweepTest.kt` no `engine/src/test/.../physics/` com cenários:

- Circle (r=5, at (0,0)) movendo `motion=(20,0)` contra Circle (r=5, at (12,0)): TOI = (12-10)/20 = 0.1, normal = (-1, 0).
- Circle (r=3, at (0,0)) movendo `motion=(10,0)` contra axis-aligned Rect (size=(4,4) at (8,0)): TOI = (8-3)/10 = 0.5, normal = (-1, 0).
- Rect (size=(4,4) at (0,0)) movendo `motion=(20, 0)` contra Rect (size=(4,4) at (10,0)): TOI = (10-4)/20 = 0.3, normal = (-1, 0).
- Sweep sem colisão (motion paralelo, sem interseção) → `null`.
- Rotated input (`aWorld.rotation = π/4`) → retorna `null` (limitação documentada).
- Negative TOI (corpos já sobrepostos no início) → tratamento explícito: retorna TOI = 0 com normal apontando para fora.

## Risks / Trade-offs

- **R1. `moveAndCollide` em loop infinito.** Se o script chama `moveAndCollide` recursivamente (ex.: aplicar slide → moveAndCollide do remainder → slide → ...) num corner case (preso entre 3 paredes), pode iterar muito. Mitigação: documentar no KDoc que slide é responsabilidade do script com loop controlado (Godot tem `max_slides` em `move_and_slide`); por enquanto a API `moveAndCollide` retorna uma colisão por chamada, não loopa.
- **R2. Pares CharacterBody2D-vs-CharacterBody2D em movimento simultâneo no mesmo frame.** A vai chamar `moveAndCollide(motionA)`, sweepa contra B no estado atual de B (B ainda não moveu). Depois B chama `moveAndCollide(motionB)` sweepando contra A já movido. Resultado depende da ordem. Aceitável — Godot tem o mesmo determinismo dependente de ordem; mitigação possível seria pre-calcular todos os motions, ordenar por TOI minimo, mas escopo grande.
- **R3. Sweep axis-aligned only deixa rotated bodies dependendo do fallback discreto.** Demos 4/5 mitigam via D4 (frame local compartilhado). Bodies rotacionados em world frame com `moveAndCollide` retornam `null` (sem CCD). Documentado como limitação; resolvido em `kinematic-rotated-sweep` futura.
- **R4. Performance do `moveAndCollide` em Demo 4 com 80 balls.** Cada ball faz O(N) broad-phase + sweep contra cada body. 80 × 80 = 6400 sweep tests/frame; cada sweep é ~20 ops. ~128k ops/frame ≈ 7.7M ops/s a 60Hz. Imperceptível.
- **R5. Migração de demos quebra rasamente: comportamento visual pode mudar.** Bouncing pós-migração será mais "perfeito" (sem oscilação patológica, sem tunneling), o que pode mudar a estética visual dos demos (balls não param mais "grudadas vibrando"). Aceitável e desejável — é o ponto.
- **R6. `getOverlappingAreas` retorna o estado **após** dispatch da última iteração do step. Significa que, dentro de `_on_*_entered`, o caller já está incluído no set. Documentar comportamento explicitamente.

## Migration Plan

1. **Adicionar primitivas** em `Shape2D.kt`: `SweepResult` + `sweepOverlap` para circle-circle, circle-rect, rect-rect axis-aligned. Tests unitários.
2. **Adicionar `KinematicCollision2D`** data class em arquivo próprio (`KinematicCollision2D.kt`) no package `physics`.
3. **Adicionar `CharacterBody2D.moveAndCollide`**: implementa o algoritmo de D1 chamando `sweepOverlap`. Tests unitários (body sozinho move livre; body batendo em parede para no TOI; body deeply overlapping no início retorna TOI=0).
4. **Adicionar queries em `Area2D`**: `getOverlappingAreas` / `getOverlappingBodies`. Expor `PhysicsSystem.queryOverlappingFor(obj)` internal. Tests.
5. **Adicionar helpers em `Vec2`** (se demos pedirem): `reflect(normal)`, `slide(normal)`. Ou inline nos demos primeiro, helper se repetir.
6. **Migrar `CollisionStressDemo` (Demo 4):** balls de `Area2D` para `CharacterBody2D`. Paredes da scene como 4 `StaticBody2D` filhos do demo root. Script usa `onPhysicsProcess` + `moveAndCollide` + reflect. Remove `onAreaEntered`.
7. **Migrar `RotatingBoxDemo` (Demo 5):** balls de `Area2D` para `CharacterBody2D` filhos do `RotatingBox`. Paredes como 4 `StaticBody2D` filhos do `RotatingBox` em local frame. Script idem. Confirma que sweep no frame local funciona apesar do parent rotacionar.
8. **Re-rodar stress test instrumentado** (Demo 4 com BALL_COUNT=80, vmax=2000): `capHits` deve cair pra zero ou ordem de grandeza menor (tunneling estrutural eliminado).
9. **Documentar limitações:** `moveAndCollide` requer `world.rotation == 0f` para o sweep cobrir (ou rotation idêntica entre body que move e target — D4). Bodies em world frame rotacionados caem no caminho discreto.
10. **Atualizar `CLAUDE.md` invariante #3** com a nota: bodies que se movem **devem** usar `moveAndCollide` (ou aceitam tunneling explicitamente). Areas continuam triggers.
