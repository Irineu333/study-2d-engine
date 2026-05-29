## Context

O `PhysicsSystem.step(tree, dt)` roda um TOI loop que, para cada contato,
computa um `SweepResult(toi, point, normal, depenetration)` (Shape2D.kt) e
resolve o impulso bilateral — mas o `SweepResult` é transiente: vive dentro
do laço de resolução e é descartado ao fim do step. Os corpos expõem
`linearVelocity`/`angularVelocity` (RigidBody2D) e velocity (CharacterBody2D)
de forma estável. A geometria real das shapes é derivável: `CircleShape2D`
tem `radius`; `RectangleShape2D` tem `size` e seus 4 cantos world saem de
`obbCorners(world, size, offset)` — hoje **privado** em Shape2D.kt.

O gizmo de colisão atual (`ColliderWidget`) é um `WorldDebugWidget` que
desenha só `shape.worldBounds()` (o AABB). O world pass do `SceneTree.render`
aplica a view transform da `Camera2D` aos `WorldDebugWidget`, então gizmos
desenham em coordenadas de mundo sem `pushTransform` próprio.

`MomentumDiagnostics` é o precedente para expor estado físico ao debug:
extension functions pull-based em `SceneTree` que varrem a árvore. Funciona
para grandezas *deriváveis a qualquer instante* (momento, energia). Não
serve para contatos, que só existem *durante* o step.

## Goals / Non-Goals

**Goals:**

- Desenhar a **forma real** de cada collider (círculo, rect rotacionado),
  não só o AABB.
- Desenhar **vetores de velocidade** dos corpos.
- Tornar visíveis os **contatos e normais** que o solver realmente usou.
- Custo zero em produção; gating consistente com o resto do `debug-overlay`.
- Change auto-contida: shippável sem `debug-immediate-draw`.

**Non-Goals:**

- Velocidade angular como arco/spinner (menção futura; MVP é só linear).
- Visualizar o caminho do sweep (a trajetória varrida) — fora do MVP;
  apenas o ponto/normal de contato resolvido.
- Visualizar forças/impulsos por contato (magnitude do impulso) — futuro.
- OBB exato no overlap test (a física já aproxima rotacionado onde aproxima;
  o gizmo desenha a geometria real independentemente do que o solver usa).
- Reusar `debug-immediate-draw` (ver D3).

## Decisions

### D1 — Três widgets dedicados, não um

`ShapeGizmoWidget`, `VelocityGizmoWidget`, `ContactGizmoWidget`, cada um um
`WorldDebugWidget` com seu próprio `enabled`/row no HUD. Separados porque
têm fontes de dado e custos distintos (pull puro vs gravação no step) e o
usuário tipicamente quer ligar um sem os outros (ver só contatos numa
investigação de impulso). Espelha o granular do HUD existente.

### D2 — `ShapeGizmoWidget` desenha geometria real via cantos world

Para `CircleShape2D`: `renderer.drawCircle(center, r, color, filled = false)`
com `center`/`r` em world. Para `RectangleShape2D`: os 4 cantos world ligados
por `drawLine` (quad fechado), cobrindo o caso rotacionado corretamente.
Requer expor um helper público — `RectangleShape2D.worldCorners(world):
List<Vec2>` — promovendo a lógica de `obbCorners` (hoje privada). O
`ColliderWidget` (AABB) fica intacto: AABB é o que o broad-phase enxerga,
e ver os dois lado a lado ensina a diferença forma-real vs envelope.

**Alternativa rejeitada — modificar `ColliderWidget` para forma real:**
perderia a visualização do AABB (que tem valor didático próprio) e geraria
um delta MODIFIED na spec debug-overlay, arriscando conflito com
debug-log-overlay. Widget novo e aditivo é mais limpo.

### D3 — Contatos via buffer gravado no step, NÃO via immediate-draw

`PhysicsSystem.step`, quando a gravação está habilitada, registra cada
`(point, normal)` resolvido num buffer por-`SceneTree`. O buffer é limpo no
início do step e preenchido durante a resolução; o `ContactGizmoWidget` lê
no `drawDebug` do mesmo frame. O buffer vive em `tree.debug` (um
`PhysicsContactBuffer` exposto como, ex., `tree.debug.contacts.records`),
alcançável pelo `step` via o argumento `tree` que ele já recebe.

A gravação é gated pelo `enabled` do `ContactGizmoWidget`: `step` consulta
esse flag e faz early-out quando off (custo zero em produção).

**Por que não emitir via `debug-immediate-draw`** (a dependência que o
roadmap previa): os contatos precisam ser **capturados durante o TOI loop**
de qualquer forma — o trabalho é a gravação, não o desenho. Um buffer
dedicado de contatos é mais simples que rotear emissão de física pela
facade genérica (que teria o gating duplo `draw.enabled` × gizmo-enabled).
Decoplar também torna esta change shippável sozinha. O immediate-draw
permanece a ferramenta certa para gizmos *ad-hoc* de game/script, não para
o stream estruturado de contatos da engine.

**Alternativa rejeitada — recomputar contatos no widget:** rodar overlap/
sweep de novo a partir do `drawDebug` duplica a lógica física e produziria
pontos diferentes dos que o solver usou — perde a fidelidade que é o valor
didático. Gravar a verdade do solver é o ponto.

**Alternativa rejeitada — buffer no `PhysicsSystem`:** o `PhysicsSystem` é
externo à árvore (o `GameLoop` o possui); o widget só alcança `tree`. Pôr o
buffer em `tree.debug` é a única superfície comum aos dois.

### D4 — `VelocityGizmoWidget` pull-based

No `drawDebug`, varre a árvore por `RigidBody2D`/`CharacterBody2D` ativos e
desenha `drawLine(pos, pos + vel * scale, ...)` mais uma pequena cabeça de
seta. `scale` é um campo do widget (default tal que velocidades típicas dos
jogos shipped fiquem legíveis). Reusa o padrão de varredura do
`MomentumDiagnostics` (`forEachRigid`), estendido para incluir
`CharacterBody2D`.

### D5 — Gravação não persiste, não serializa

O `PhysicsContactBuffer` é runtime puro per-tree (como o resto de
`tree.debug`): nunca `@Serializable`, nunca compartilhado entre trees,
limpo a cada step. Entradas são `data class ContactRecord(point: Vec2,
normal: Vec2)` imutáveis.

## Risks / Trade-offs

- **[Acoplar `PhysicsSystem` ao `tree.debug`]** → O step passa a referenciar
  `tree.debug.contacts`. Mitigação: `tree.debug` já é a superfície de debug
  per-tree e o `step` já recebe `tree`; a gravação é gated e early-out quando
  off, sem custo em produção. Não viola o invariante #4 (que restringe o
  `GameHost`, não o `PhysicsSystem`).
- **[Volume de contatos em cenas densas]** (pool com 16 bolas) → o buffer
  pode acumular muitos `(point, normal)` por step. Mitigação: limpo a cada
  step (não cresce sem limite entre frames); alocação aceitável para debug.
- **[`worldCorners` público amplia a API de `RectangleShape2D`]** → promove
  lógica antes privada. Mitigação: é um getter derivado puro, sem estado;
  útil além do debug (ex.: futuros testes de OBB). KDoc documenta a ordem
  dos cantos.
- **[Desvio do roadmap: sem dependência de immediate-draw]** → contradiz a
  sequência anotada. Mitigação: decisão registrada em D3; a nota do roadmap
  pode ser ajustada. A ordem de implementação fica livre entre as duas.
- **[Velocity scale mal calibrada vira setas gigantes/invisíveis]** →
  Mitigação: `scale` configurável no widget; default calibrado pelos jogos
  shipped (pong/pool8).
