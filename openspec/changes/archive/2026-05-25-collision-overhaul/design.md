## Context

Depois de `godot-style-foundation`, a engine está em estilo Godot para hooks, primitivas visuais, `Signal`, `Camera2D`, `groups`, fixed-step. O que sobrou de "não-Godot" é o **modelo de colisão**: `Collider` (Node abstrato), `BoxCollider` concreto, `PhysicsSystem` O(N²) com um único callback `onCollide(other)`.

Problemas concretos com o modelo atual:

1. **Forma e corpo são o mesmo objeto.** `BoxCollider` é simultaneamente "o corpo" e "a forma" — não dá pra ter dois colliders num mesmo gameplay-entity, nem trocar a forma sem trocar o nó.
2. **Sem semântica de trigger vs sólido.** Goal e parede são ambos `BoxCollider`; o script precisa adivinhar a intenção via nome (`other.name == "leftGoal"`).
3. **Sem enter/exit.** `onCollide` dispara enquanto pares sobrepõem (todo physics step). Não há "começou a sobrepor" vs "deixou de sobrepor". Pong precisa do flag `_scored_this_tick` para não emitir gol múltiplas vezes.
4. **Sem `velocity` de primeira classe.** Bola e paddle integram movimento na mão; nada na engine sabe distinguir um corpo cinemático de um estático.

Esta change adota o **modelo Godot 4 simplificado**: separação forma/corpo, dois sabores de corpo (trigger vs sólido), sólido subdividido em estático vs cinemático, eventos enter/exit pareados estáveis. Não copiamos a parte mais complexa (RigidBody dinâmica com massa/atrito/impulso, slide tangencial, layers/masks, CCD).

## Goals / Non-Goals

**Goals:**

- `CollisionObject2D` como base; `Area2D` (trigger), `StaticBody2D`, `CharacterBody2D` (com slot `velocity`).
- `CollisionShape2D` como Node filho; `Shape2D` polimórfico (`RectangleShape2D`, `CircleShape2D`).
- Eventos enter/exit pareados estáveis (`_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`).
- Signals built-in (`areaEntered`, `areaExited`, `bodyEntered`, `bodyExited`) em cada `CollisionObject2D`.
- Múltiplas shapes por objeto suportadas.
- Pong refeito: Areas em goals, Bodies em paddles/walls/ball, Shapes filhas.
- `BoxCollider`, `Collider`, `_on_collide` apagados.

**Non-Goals:**

- **Não** implementa `move_and_slide()`, `move_and_collide()`, slide tangencial, ou resolução automática de colisão. `CharacterBody2D.velocity` é só um slot — quem integra é o script.
- **Não** introduz `RigidBody2D` (com massa, atrito, impulso). Modelo dinâmico fica fora — sai do escopo didático e da necessidade dos jogos atuais/planejados.
- **Não** introduz `collision_layer` / `collision_mask` (filtros bitmask). Toda dupla é testada. Adicionar layers seria O(1) por par mas adiciona conceito que Pong/Asteroids não precisam.
- **Não** implementa CCD (continuous collision detection). Pares são testados pontualmente cada physics step. Tunneling é mitigado pelo fixed-step de change 1, não por raycast.
- **Não** introduz `Shape2D` com geometria de polígono (`ConvexPolygonShape2D`). Só rect + circle. Asteroides em Asteroids serão **circle shape** para colisão (visual via `Polygon2D` separado).
- **Não** mexe em `Renderer.withTransform` — rotação visual segue ausente. Asteroids puxa isso em sua própria change.
- **Não** introduz `Tween`, `Timer`, `Sprite2D`, `InputMap`.

## Decisions

### D1. CollisionShape2D como Node, Shape2D como dado polimórfico

**Decisão:** `CollisionShape2D : Node2D()` é o nó filho; ele possui `shape: Shape2D?` polimórfico. `Shape2D` é uma `sealed class` com dois subtipos hoje: `RectangleShape2D(size: Vec2)` e `CircleShape2D(radius: Float)`.

**Por quê sealed em vez de duas `Node`s separadas (e.g. `CollisionShape2DRectangle`):** matches o modelo Godot (CollisionShape2D + RectangleShape2D resource). Em Godot, `Shape2D` é um *resource*, ou seja, dado serializável reusável. Aqui não temos Resource separado de Node, então `Shape2D` é uma `sealed class @Serializable`. Permite trocar a forma sem trocar o nó (importante para o futuro inspetor visual).

**Custo:** kotlinx.serialization precisa de configuração para sealed (`@Polymorphic` ou serializer module). Tarefa explícita no tasks.md.

**Alternativa rejeitada:** dois nós (`CircleCollisionShape2D`, `RectCollisionShape2D`). Mais simples de serializar mas duplica nó-types e foge do modelo Godot.

### D2. CollisionObject2D agrupa Areas e Bodies com base comum

**Decisão:** `CollisionObject2D` é a base abstrata. Sub-tipos:

```
CollisionObject2D                                 disabled, signals built-in
├── Area2D                                        trigger (sem bloqueio)
└── PhysicsBody2D (abstract)
    ├── StaticBody2D                              sólido parado (move via script)
    └── CharacterBody2D                           sólido + velocity exposto
```

**Despacho de eventos:**

| A é          | B é          | A recebe                | B recebe                |
|--------------|--------------|-------------------------|-------------------------|
| Area2D       | Area2D       | onAreaEntered(B)        | onAreaEntered(A)        |
| Area2D       | PhysicsBody  | onBodyEntered(B)        | onAreaEntered(A)        |
| PhysicsBody  | PhysicsBody  | onBodyEntered(B)        | onBodyEntered(A)        |

**Por quê quatro hooks (e não um `onObjectEntered(other)`):** clareza de intenção. Quem escreve `_on_body_entered` sabe que está olhando para algo sólido; quem escreve `_on_area_entered` sabe que é um trigger. Espelha Godot 1:1.

### D3. CharacterBody2D não integra automaticamente

**Decisão:** `CharacterBody2D` expõe `var velocity: Vec2 = Vec2.ZERO` como `@Inspect`, mas **não** integra a posição automaticamente. Quem move é o script:

```python
# extends CharacterBody2D
def _physics_process(self, dt):
    self.transform.position += self.velocity * dt
```

**Por quê não integrar:** Godot mesmo não integra — chama-se `move_and_slide()` explicitamente. Integrar automaticamente "esconde" uma decisão importante (quando se moveu? antes ou depois do que?) e cria pressão por outros parâmetros (gravity? friction? damping?) que ainda não temos.

**Custo:** Pong scripts continuam fazendo a integração na mão. Pouca diferença em código.

**Alternativa rejeitada:** integrar em `CharacterBody2D.onPhysicsProcess`. Coloca lógica no Node, esconde o "quando".

**Futuro:** `move_and_slide()` entra em change futura quando algum jogo precisar de slide tangencial (provavelmente change pós-Asteroids, com platformer/top-down).

### D4. PhysicsSystem mantém set de pares overlapping

**Decisão:** `PhysicsSystem` carrega `previousOverlapping: MutableSet<UnorderedPair<CollisionObject2D>>` entre steps. A cada step:

```
currentOverlapping = enumerate-pairs-and-test()
for pair in (currentOverlapping - previousOverlapping):
    dispatch enter(pair.a, pair.b)
for pair in (previousOverlapping - currentOverlapping):
    dispatch exit(pair.a, pair.b)
previousOverlapping = currentOverlapping
```

`UnorderedPair` é uma classe value-style com `equals`/`hashCode` insensíveis à ordem (`UnorderedPair(a, b) == UnorderedPair(b, a)`).

**Por quê set ao invés de "estado por par":** pareamento por hash é O(1) e simples. Um `Map<UnorderedPair, OverlapState>` daria mais info por par mas não precisamos hoje.

**Custo:** memória O(P) onde P é número de pares atualmente sobrepondo. Em escala didática, irrelevante.

**Garbage collection de pares:** quando um Node é removido do tree, seus pares no `previousOverlapping` precisam sair. Solução: ao detectar que `node !is in scene`, remove de todos os pares. Tarefa explícita.

### D5. Múltiplas shapes por CollisionObject2D — overlap = OR

**Decisão:** Um `CollisionObject2D` pode ter múltiplos `CollisionShape2D` filhos. Dois objects A, B são considerados "overlapping" se **alguma** dupla (shapeA, shapeB) intersecta. Apenas **um** par de eventos é disparado por dupla (A, B) — não um por par-de-shape.

**Por quê OR e não por-shape:** simplicidade semântica. Em Godot, o evento `body_entered` é por par de bodies, não por par de shapes (existe `body_shape_entered` pra granularidade extra; pulamos).

**Custo:** O((NA · NB)) por dupla de objects, onde N* é número de shapes. Aceitável.

### D6. Despacho usa tanto hook quanto signal

**Decisão:** Quando `PhysicsSystem` detecta enter/exit, ele faz **duas** coisas no mesmo passo:

1. Chama o hook aberto (`object.onAreaEntered(other)` ou `object.onBodyEntered(other)`), que por sua vez delega para o script (`_on_area_entered(self, other)`).
2. Emite no signal built-in (`object.areaEntered.emit(other)`).

**Por quê os dois:** Godot expõe os dois mecanismos. Hooks são para subclasses que querem reagir polimorficamente; signals são para wiring desacoplado (outro nó/script conecta no signal). Mesmo no Pong: a ball usa `_on_area_entered` interna; um observer externo poderia conectar em `ball.areaEntered` sem subclassar.

**Custo:** sempre que houver entrada, há duas chamadas (hook + signal emit). Hosts numa cena com Δ pares por step ≤ ~10, irrelevante.

### D7. Hooks `onCollide` e `_on_collide` apagados sem alias

**Decisão:** Mesmo princípio da change 1 — rename hard. `Collider.onCollide(other)` morre junto com `Collider` (a classe inteira sai). Python `_on_collide` deixa de ser reconhecido.

**Custo:** todos os scripts/Kotlin que reagiam a colisão precisam migrar para um dos quatro hooks novos. Pong tem dois (`ball.py`, `goal.py`); Demos tem um (`SpawnerDemo`).

### D8. Pong: paddle = StaticBody2D, ball = CharacterBody2D

**Decisão:** Os paddles **se movem por script** (transform position alterado), e **bloqueiam a ball**. Isso seria `StaticBody2D` (sólido, parado-do-ponto-de-vista-do-engine) ou `CharacterBody2D` (sólido + velocity).

Escolha: **`StaticBody2D`**. Porque (a) o engine não integra `velocity` mesmo se fosse `CharacterBody2D`, e (b) `StaticBody2D` comunica a intenção "este corpo não tem velocity" — paddle.py altera `transform.position` diretamente baseado em input.

**Custo:** se um dia quisermos exposição clara de "paddle.velocity" para a IA mirar, é re-tag (StaticBody2D → CharacterBody2D). Aceitável.

### D9. Walls em Pong viram StaticBody2D, mas vivem fora do play field

Hoje as walls são `BoxCollider` parados com `size: Vec2(10, 10)` posicionados em `(0,0)` — claramente bug acumulado (o ball script trata wall-hit como fallback). A mudança vai além de renomear: o `scene.json` declara `topWall` e `bottomWall` com transform e shape adequados (linhas horizontais full-width nas y=0 e y=field-height), e o ball script reage no `_on_body_entered` por group `"walls"` (não por `name`).

**Custo:** revisão de Pong cena. Mantém escopo gerenciável.

### D10. Asteroids como jogo-validador no roadmap

**Decisão:** `game-asteroids` (Planned) é adicionada ao roadmap. Asteroids é o jogo que mais usa este refator: balas são `Area2D`, asteróides e nave são `CharacterBody2D`, todos com `CollisionShape2D` + `CircleShape2D`, pareamento estável é essencial (uma bala que toca um asteróide gera *um* `_on_area_entered`, não vinte).

Asteroids também **puxa dependências de outras changes**:

- `Renderer.withTransform` para rotacionar nave/asteróide visualmente — sai numa change pós-asteroids ou na própria game-asteroids.
- `Timer` ou wrap em script para spawn periódico — provavelmente vira mini-change ou parte de game-asteroids.
- `Camera2D.bounds` como mundo wrap-around — bounds já existe; logic de wrap é game-side.

O entry no roadmap deixa claras essas dependências para implementação futura.

## Risks / Trade-offs

- **R1. Pong rewrite extenso.** scene.json + 4-5 scripts Python. Mitigação: smoke-test manual obrigatório como tarefa final.
- **R2. Mutabilidade durante despacho.** Já temos drainPending; signals built-in podem disparar add/removeChild via handler. Garantir que enter/exit calls usem o mesmo modelo de mutação diferida.
- **R3. `UnorderedPair` referenciando Nodes pode segurar nodes vivos no Set quando saem da scene.** Mitigação: cleanup ativo — quando um Node é detached, percorrer `previousOverlapping` e remover entradas que o contenham. Tarefa explícita no tasks.md.
- **R4. Sealed `Shape2D` polimorfismo com kotlinx.serialization** pode dar trabalho de configuração (precisa registrar subclasses no `SerializersModule` ou usar `@Polymorphic`). Mitigação: tarefa dedicada com teste unitário round-trip.
- **R5. Demos `SpawnerDemo` perde `BoxCollider`.** Reescrever para Area2D+Shape; comportamento idêntico mas com semântica clara de "trap = trigger".
- **R6. `CollisionShape2D` filho não-visual no editor futuro** — sem visual, é difícil ver "onde está a hitbox". Mitigação: overlay de debug (`F2`) passa a desenhar bounds das shapes, não dos colliders antigos. Tarefa explícita.

## Known Regressions

Documentadas durante a implementação (não eram previstas no design original) e **aceitas** como custo do escopo enxuto desta change. Cada uma tem follow-up dedicada no ROADMAP:

### KR1. Tunneling em pile-ups de 3+ objetos (Demo 4 — CollisionStress)

**Sintoma:** algumas bolinhas atravessam outras durante encontros simultâneos.

**Causa:** `_entered` é one-shot por begin-of-overlap. Quando 3+ corpos colidem dentro de um único `PhysicsSystem.step`, o dispatcher itera pares numa ordem que pode deixar algum par ainda sobreposto após a resposta do script. Como esse par já está em `previousOverlapping`, nenhum `_entered` dispara no próximo step — e como nunca saiu, nenhum `_exited` também. O par fica grudado e eventualmente tunela quando as velocidades os atravessam.

**Fix proposto:** iteração-até-convergência no `PhysicsSystem.step` (re-snapshot e re-dispatch até `currentOverlapping` estabilizar dentro do mesmo step), ou um hook adicional `onAreaStaying`/`onBodyStaying`. Follow-up `collision-iterative-resolution`.

### KR2. Retângulos rotacionados ficam permanentemente "sobrepostos" (Demo 5 — RotatingBox)

**Sintoma:** várias bolinhas atravessam outras quando o wrapper rotaciona.

**Causa:** `RectangleShape2D.bounds(world, ...)` quando `world.rotation != 0f` retorna o AABB-envelope dos 4 cantos rotacionados. `overlap(rect, rect)` cai no caminho rect-rect que apenas intersecta esses AABBs. Para um par de quadrados rotacionados a ~45°, os AABBs sempre se sobrepõem mesmo quando os retângulos locais estão muito longe. Resultado: o par entra em `previousOverlapping` na primeira frame e nunca mais sai (AABBs nunca separam), então `_entered` não refire — bolinhas atravessam livremente após a primeira separação parcial.

**Fix proposto:** `overlap(RectangleShape2D, RectangleShape2D)` quando ambas têm `rotation != 0f` deve usar **OBB-vs-OBB exato** (separating-axis theorem nos 4 eixos perpendiculares aos lados). Follow-up `collision-rotated-shapes`. Estava listado como Non-Goal desta change ("Asteroids puxa rotação OBB depois se precisar"), mas Demo 5 já expõe o problema.

## Migration Plan

1. Adicionar todas as classes novas (`CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`, `Shape2D` + sub-tipos) ao `:engine`, com `BoxCollider`/`Collider` ainda vivos lado-a-lado.
2. Reescrever `PhysicsSystem` para usar `CollisionObject2D`+`CollisionShape2D`. Adicionar caminho temporário que **ignora** `BoxCollider` (vai ser apagado em seguida).
3. Atualizar overlay de debug em ambos backends para renderizar `CollisionShape2D`.
4. Migrar Pong (`scene.json` + scripts) para novos tipos.
5. Migrar `SpawnerDemo` em Demos.
6. Remover `Collider`, `BoxCollider`, hook `onCollide`, hook Python `_on_collide`. Atualizar `NodeRegistry`.
7. Stubs `.pyi` atualizados. Bindings Python atualizados.
8. Smoke test: Pong, Demos. (TicTacToe não toca colisão.)
9. CLAUDE.md atualizado (invariante 3, roadmap).
