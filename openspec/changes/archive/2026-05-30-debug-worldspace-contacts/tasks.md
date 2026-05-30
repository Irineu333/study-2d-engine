## 1. Helper de normalização

- [x] 1.1 Adicionar um helper `internal` no pacote `com.neoutils.engine.physics` que converte um par `(point, normal)` do frame do pai para world: `parentWorld = (parent as? Node2D)?.world() ?: Transform()`, `worldPoint = parentWorld.compose(Transform(position = point)).position`, `worldNormal = rotate(normal, parentWorld.rotation).normalized`. Aceita `parent: Node?` (null/Node puro → identidade).
- [x] 1.2 Testes do helper: pai com rotação+translação → point/normal saídos em world (normal unitária); pai identidade (top-level) → point/normal idênticos aos locais.

## 2. Aplicar nos dois caminhos de gravação

- [x] 2.1 Em `CharacterBody2D.moveAndCollide`, normalizar `bestHit.point`/`bestHit.normal` via o helper (usando `this.parent`) antes de `tree.debug.contacts.stage(...)`. O `KinematicCollision2D` retornado permanece no frame do pai (não mudar a API pública de retorno).
- [x] 2.2 Em `PhysicsSystem.advanceAndResolve`, normalizar `sweepResult.point`/`sweepResult.normal` via o helper (usando o `parent` do corpo `r` já em escopo) antes de `contactSink?.append(...)`. A resolução de impulso continua usando o `sweepResult` cru (não normalizado).

## 3. Testes de integração e regressão

- [x] 3.1 Teste: `CharacterBody2D` aninhado num pai rotacionado/transladado, batendo via `moveAndCollide` com gravação on → o `ContactRecord` no buffer está em world-space (não no frame do pai).
- [x] 3.2 Teste: `RigidBody2D` aninhado num pai rotacionado, resolvendo contato no `step` com gravação on → o `ContactRecord` está em world-space; corpos top-level (caso de `debug-physics-gizmos`) permanecem inalterados.
- [x] 3.3 Rodar a suíte do `:engine`; garantir verde (regressão de `debug-physics-gizmos` e `debug-kinematic-contacts`: contatos top-level não mudam).
- [x] 3.4 `openspec validate debug-worldspace-contacts --strict` e revisar coerência specs↔implementação.

## 4. Verificação visual

- [x] 4.1 Rodar `games:demos:run`, abrir o HUD (F1), habilitar o gizmo de contato e confirmar na demo 5 que os marcadores acompanham as bolas dentro do `RotatingBox` que gira (não mais deslocados).
