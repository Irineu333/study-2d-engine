## 1. Geometria pública das shapes

- [x] 1.1 Promover a lógica de `obbCorners` a um método público `fun worldCorners(world: Transform): List<Vec2>` em `RectangleShape2D`, com KDoc documentando a ordem dos cantos (TL, TR, BR, BL — loop fechado).
- [x] 1.2 Teste: cantos de rect não-rotacionado batem com o AABB; rect rotacionado dá quad não-alinhado com centroide no centro world.

## 2. ShapeGizmoWidget

- [x] 2.1 Criar `ShapeGizmoWidget : WorldDebugWidget` (`title = "Shapes"`, `enabled = false`); `drawDebug` itera `collectActiveCollisionShapes(tree)`.
- [x] 2.2 Círculo: `drawCircle(center, r, color, filled = false)` em world. Retângulo: ligar os 4 `worldCorners` com `drawLine` (quad fechado). Sem `pushTransform`.
- [x] 2.3 Testes: círculo desenhado como outline no centro world; rect rotacionado como quad ≠ AABB; desabilitado → zero draws; zero push/pop.

## 3. VelocityGizmoWidget

- [x] 3.1 Criar `VelocityGizmoWidget : WorldDebugWidget` (`title = "Velocity"`) com `var velocityScale: Float` (renomeado de `scale` para não sombrear `Node2D.scale`; default calibrado por pong/pool8).
- [x] 3.2 `drawDebug` varre `RigidBody2D`/`CharacterBody2D` ativos (estender o `forEachRigid` do MomentumDiagnostics para incluir Character) e desenha `drawLine(pos, pos + vel * scale)` + cabeça de seta; velocidade zero → nada.
- [x] 3.3 Testes: corpo em movimento gera linha `p → p + v*s`; corpo parado não gera linha.

## 4. Buffer de contatos + seam no PhysicsSystem

- [x] 4.1 Criar `data class ContactRecord(point: Vec2, normal: Vec2)` e um `PhysicsContactBuffer` por-tree (runtime puro, não `@Serializable`) exposto via `tree.debug` (ex.: `tree.debug.contacts`).
- [x] 4.2 Em `PhysicsSystem.step`: se a gravação está habilitada, limpar o buffer no início e dar `append(point, normal)` em cada contato resolvido (a partir do `SweepResult`); early-out total quando desabilitada.
- [x] 4.3 Conectar o gating ao `ContactGizmoWidget.enabled` (habilitar o widget liga a gravação; desabilitar desliga).
- [x] 4.4 Testes: buffer vazio quando recording off mesmo com colisão; populado quando on; limpo a cada step (step sem contato → buffer vazio).

## 5. ContactGizmoWidget

- [x] 5.1 Criar `ContactGizmoWidget : WorldDebugWidget` (`title = "Contacts"`); `enabled` dirige a gravação (override do setter ou flag lido pelo step).
- [x] 5.2 `drawDebug` desenha, por `ContactRecord`, um marcador no `point` e uma `drawLine` de `point` ao longo de `normal` (comprimento fixo curto). Sem `pushTransform`.
- [x] 5.3 Testes: cada contato → marcador + linha de normal; habilitar o widget popula o buffer no próximo step com colisão.

## 6. Registro como built-ins

- [x] 6.1 Adicionar campos de conveniência no `DebugRegistry` para os três gizmos.
- [x] 6.2 No `DebugLayer`/`bindLayer`, registrar os três no `WorldDebugContainer` (default `enabled = false`).
- [x] 6.3 Testes: três gizmos não-nulos após `start()`, `parent == WorldDebugContainer`, presentes em `widgets`; HUD lista uma row por gizmo, individualmente togglável.

## 7. Fechamento

- [x] 7.1 Rodar a suíte do `:engine`; garantir verde (incluindo regressão de física — a gravação gated não pode alterar o resultado do step).
- [x] 7.2 `openspec validate debug-physics-gizmos --strict` e revisar coerência specs↔implementação.
