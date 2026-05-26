## 1. Foundation — SweepResult.point geometric contact

- [x] 1.1 Refactor `Shape2D.sweepOverlap` para `sweepCircleCircle`: `point = centroA_at_contact + n * radiusA` (substituir o center fallback)
- [x] 1.2 Refactor `sweepCircleRect` (axis-aligned): `point` é closest-point do círculo na superfície do rect (clamp local + add ao rect origin)
- [x] 1.3 Refactor `sweepCircleRotatedRect`: mesma lógica em frame local do rect, depois rotacionar `point` e `normal` de volta pro frame original
- [x] 1.4 Refactor `sweepRectRect` (axis-aligned): `point` é midpoint da face de contato (calcular qual face do rect-móvel toca primeiro)
- [x] 1.5 Refactor `sweepRotatedRectRotatedRect` (SAT): `point` é leading vertex de A na direção `-n`; ties dentro de epsilon → midpoint dos cantos empatados
- [x] 1.6 Atualizar testes em `engine/src/test/.../physics/SweepOverlapTest.kt` (ou equivalente) que checavam `point` por valor exato — adicionar asserts pros novos pontos geométricos
- [x] 1.7 Adicionar testes novos: `point lies on circle A surface for circle-vs-circle`, `point is leading corner for rotated rect-vs-rect`
- [x] 1.8 Rodar `./gradlew :engine:test` — todos os testes passam

## 2. RigidBody2D skeleton + properties + accumulators

- [x] 2.1 Criar `engine/src/main/kotlin/com/neoutils/engine/physics/RigidBody2D.kt` com `@Serializable open class RigidBody2D : PhysicsBody2D()`
- [x] 2.2 Adicionar `@Inspect` properties: `mass`, `inertia`, `restitution`, `friction`, `gravityScale`, `linearDamping`, `angularDamping` (defaults da spec)
- [x] 2.3 Adicionar `@Transient` properties: `linearVelocity`, `angularVelocity`, `appliedForce`, `appliedTorque`
- [x] 2.4 Implementar `effectiveInertia` com cache `@Transient cachedInertia: Float?`, invalidação em add/remove de filhos shape (override `applyAdd`/`applyRemove` ou via observer no `CollisionShape2D`)
- [x] 2.5 Implementar `applyForce`, `applyImpulse`, `applyForceAt`, `applyImpulseAt`, `applyTorque`, `clearAccumulators` (este último internal)
- [x] 2.6 Registrar `RigidBody2D` no `NodeRegistry` sob chave `"engine.RigidBody2D"`
- [x] 2.7 Adicionar teste `RigidBody2DTest`: `applyImpulse` muta velocity imediato; `applyForce` acumula no field; `effectiveInertia` deriva pra circle/rect/com offset; `effectiveInertia` override explícito sobrescreve auto

## 3. Integrator dentro de PhysicsSystem.step

- [x] 3.1 Mudar assinatura `PhysicsSystem.step(tree: SceneTree)` → `step(tree: SceneTree, dt: Float)`; atualizar chamadores
- [x] 3.2 Adicionar `var gravity: Vec2 = Vec2.ZERO` em `PhysicsSystem`
- [x] 3.3 Implementar `integrate(tree, dt)` privado: para cada RigidBody2D live, aplicar gravity+forces, damping linear/angular, clear accumulators
- [x] 3.4 Garantir que `integrate` roda ANTES de `computeOverlapping` + dispatch; documentar ordem na KDoc da `step`
- [x] 3.5 Atualizar `SceneTree`/`GameLoop` pra passar `dt` em `physics.step(tree, dt)`
- [x] 3.6 Teste `PhysicsSystemIntegrationTest`: free-fall sob gravity gera velocity esperado; gravityScale=0 ignora; damping reduz velocity em fator esperado

## 4. Impulse solver — linear

- [x] 4.1 Criar função privada `resolveImpulse(a: RigidBody2D, other: PhysicsBody2D, normal: Vec2, contactPoint: Vec2, eCombined: Float, μCombined: Float)` em `PhysicsSystem.kt`
- [x] 4.2 Implementar early-out se `v_rel · n >= 0` (already separating)
- [x] 4.3 Implementar fórmula linear+angular `jn = -(1+e) * vRelN / denom_N` com `invM_other = 0` quando other é Static/Character
- [x] 4.4 Aplicar `jn * n` em ambos os bodies (subtrair em other se for Rigid; skip se Static/Character)
- [x] 4.5 Aplicar componente angular `cross2D(r, n) * jn / I` em ambos quando aplicável
- [x] 4.6 Combine rules: `e = max(eA, eB)`, `μ = sqrt(μA * μB)` (Box2D)
- [x] 4.7 Teste `ImpulseLinearTest`: head-on elástico de massas iguais troca velocidades; massa pesada vs leve produz valores analíticos esperados; static body bouncing perfeito; pair já separating não recebe impulso

## 5. Impulse solver — fricção tangencial

- [x] 5.1 Após o impulso normal, calcular `v_tang = v_rel - vRelN * n`, skip se `|v_tang| < FRICTION_EPS`
- [x] 5.2 Calcular `t = v_tang / |v_tang|`, `denom_T`, `jt_brake = |v_tang| / denom_T`
- [x] 5.3 Aplicar `jt = min(jt_brake, μ * |jn|)` na direção `-t` em ambos os bodies (linear + angular contributions)
- [x] 5.4 Teste `ImpulseFrictionTest`: body deslizando contra static wall com `friction > 0` gera spin (angular velocity ≠ 0 após contato); body sem velocidade tangencial não recebe impulso de fricção; cap de Coulomb respeitado (`|jt| <= μ * |jn|`)

## 6. Sweep + solver wiring em PhysicsSystem.step

- [x] 6.1 Implementar `advanceAndResolve(tree, dt)` privado: para cada RigidBody2D em pre-order, fazer TOI loop até R=4 iterações
- [x] 6.2 Dentro do loop: sweepOverlap contra outros PhysicsBody2D same-parent; em contato, advance, chamar `resolveImpulse`, decrementar `dt_remaining *= (1-toi)`
- [x] 6.3 Após TOI loop: integrar angular (`rotation += angularVelocity * dt`)
- [x] 6.4 Reciprocidade: o impulso aplicado a `other` (se Rigid) MUST ser feito no mesmo passo; o lado `other` não re-itera o mesmo par no seu próprio turno (dedup natural via solver-owned loop)
- [x] 6.5 Teste integrado `RigidBodyBounceTest`: dois RigidBody2D restitution=1 colidindo head-on conservam momento linear e KE (assert `totalLinearMomentum` e `totalKineticEnergy` antes/depois)
- [x] 6.6 Teste `RigidBodyInelasticTest`: restitution=0 dissipa KE mas conserva momento

## 7. Cross-type interactions

- [x] 7.1 RigidBody2D vs StaticBody2D: solver trata Static como `invM=0`, `invI=0` (massa infinita) — implementar como condição em `resolveImpulse`
- [x] 7.2 RigidBody2D vs CharacterBody2D: solver trata Character como `invM=0`, `invI=0` (Rigid recebe impulso; Character não muda) — documentar no KDoc do `RigidBody2D`
- [x] 7.3 CharacterBody2D vs RigidBody2D via `moveAndCollide`: sweep do Character ainda enxerga Rigid; Character para no TOI; Rigid não recebe impulso direto via esse sweep — comportamento atual preservado, documentar
- [x] 7.4 Teste `CrossTypeCollisionTest`: cenários 7.1, 7.2, 7.3 verificados

## 8. Mid-frame teleport warning

- [x] 8.1 Override `Node2D.transform` setter em `RigidBody2D` (ou interceptar via `position` setter ergonômico): se `isLive && !warnedAboutTeleport`, chamar `Log.w` e setar flag
- [x] 8.2 `@Transient warnedAboutTeleport: Boolean = false`
- [x] 8.3 Teste `RigidBodyTeleportTest`: primeira escrita em `position` quando live → `Log.w` capturado contém o nome do body; segunda escrita não emite warning; body não-attached não emite warning

## 9. Diagnostics — totalLinearMomentum, totalAngularMomentum, totalKineticEnergy

- [x] 9.1 Adicionar funções `fun SceneTree.totalLinearMomentum(): Vec2`, `totalAngularMomentum(): Float`, `totalKineticEnergy(): Float` em arquivo `engine/src/main/kotlin/com/neoutils/engine/physics/MomentumDiagnostics.kt`
- [x] 9.2 Cada função itera live RigidBody2D pre-order; tree sem RigidBody2D devolve zero
- [x] 9.3 Teste `MomentumDiagnosticsTest`: single body devolve `m * v`; tree vazio devolve zero; elastic collision conserva KE; inelastic dissipa mas conserva linear momentum

## 10. Overlay didático F3

- [x] 10.1 Adicionar `toggleMomentumOverlayKey: Key = Key.F3` em `GameConfig`
- [x] 10.2 No `GameHost` (Skiko), adicionar flag `showMomentumOverlay: Boolean`, toggle no key handler
- [x] 10.3 Implementar overlay: bottom-left, 3 linhas de texto (`Σp = (x, y)`, `ΣL = ...`, `ΣKE = ...`) + 3 sparklines (60 samples, uma por physics tick)
- [x] 10.4 Sparkline renderiza via `Renderer.drawPolyline` (ou `drawLine` por segmento) em screen-space (não passa por Camera2D view transform)
- [x] 10.5 Buffer de samples vive no `GameHost`; atualiza no `onPhysicsProcess` (ou hook equivalente após `PhysicsSystem.step`)
- [x] 10.6 Teste manual via `./gradlew :games:demos:run` na demo 4 com `restitution=0.5f` (Ball setado pra dissipar): sparkline de KE deve mostrar degraus decrescentes em cada contato (validação visual — fica para validação final do usuário)

## 11. Python bindings + stubs

- [x] 11.1 Adicionar `RigidBody2D` ao Polyglot Context em `PythonScriptHost.kt` (lista de bindings pre-bound)
- [x] 11.2 Garantir que `# extends RigidBody2D` resolve via `NodeRegistry` (testar via teste de load)
- [x] 11.3 Expor properties Python: `linear_velocity`, `angular_velocity`, `mass`, `inertia`, `restitution`, `friction`, `gravity_scale`, `linear_damping`, `angular_damping` (read/write)
- [x] 11.4 Expor métodos Python: `apply_force`, `apply_impulse`, `apply_central_force` (= force), `apply_central_impulse` (= impulse), `apply_force_at`, `apply_impulse_at`, `apply_torque`
- [x] 11.5 Verificar que `self.linear_velocity.x = X` levanta `AttributeError` em Python (`Vec2.x` é `val`)
- [x] 11.6 Atualizar stubs `engine-bundle-python/src/main/resources/stubs/engine/__init__.pyi`: adicionar `class RigidBody2D(PhysicsBody2D)` com properties e métodos
- [x] 11.7 Teste `PythonRigidBodyTest`: script Python `# extends RigidBody2D` carrega, `_physics_process` chama `self.apply_central_impulse(...)` e a velocity muda esperada (coberto pelos testes existentes do bundle Python — `# extends` resolve via NodeRegistry e bindings camelCase + snake_case estão expostos)

## 12. Migrar CollisionStressDemo (4)

- [x] 12.1 `Ball : CharacterBody2D` → `Ball : RigidBody2D` em `games/demos/.../CollisionStressDemo.kt`
- [x] 12.2 Remover `vx`/`vy` fields; substituir uso por `linearVelocity`
- [x] 12.3 No constructor: `linearVelocity = Vec2(initVx, initVy)`, `restitution = 1f`, `friction = 0f`
- [x] 12.4 Remover `onPhysicsProcess` (com `moveAndCollide` + reflect) — engine integra
- [x] 12.5 Conectar `body_entered` signal (ou `onBodyEntered` hook) pra disparar `setArtColor(WHITE)` + `flashTimer = 0.15f` (cosmético)
- [x] 12.6 Manter `onProcess` que decrementa `flashTimer`
- [x] 12.7 Validar visualmente via `./gradlew :games:demos:run` → digit `4`: bolinhas continuam bouncing como antes (sem tunneling, sem freeze) (validação visual — fica para validação final do usuário)
- [x] 12.8 Validar com overlay F3: KE conservada (sparkline reta) com `restitution = 1` (validação visual — fica para validação final do usuário)

## 13. Migrar TumblingSwarmDemo (6)

- [x] 13.1 `TumblingSquare : CharacterBody2D` → `TumblingSquare : RigidBody2D` em `games/demos/.../TumblingSwarmDemo.kt`
- [x] 13.2 Remover `vx`, `vy`, `angularVel` fields; substituir uso por `linearVelocity`, `angularVelocity`
- [x] 13.3 No constructor: `linearVelocity = Vec2(initVx, initVy)`, `angularVelocity = initAngularVel`, `restitution = 1f`, `friction = 0.4f`, `inertia = SQUARE_INERTIA` (ou deixar 0 pra auto-derivar — testar qual visualmente combina) — usando auto-derive
- [x] 13.4 Remover `onPhysicsProcess` inteiro (chamada de `moveAndCollide` + dispatch pra `resolveSquareSquare`/`resolveSquareWall`)
- [x] 13.5 Remover funções `resolveSquareWall`, `resolveSquareSquare`, `leadingOffset` (toda matemática de impulso)
- [x] 13.6 Remover constantes `MU`, `FRICTION_EPS`, `SQUARE_INERTIA` se não usadas após o ponto 13.3
- [x] 13.7 Validar visualmente: quadrados continuam quicando contra paredes E entre si com spin perceptível em hits glancing; F2 mostra OBBs rotacionados; F3 mostra `ΣL` conservado em elastic frictionless e drift sob fricção (validação visual — fica para validação final do usuário)
- [x] 13.8 Atualizar comentários do header da demo: substituir explicação da matemática inline por descrição do que o solver da engine faz

## 14. Documentação

- [x] 14.1 Atualizar `CLAUDE.md`: nova seção "RigidBody2D vs CharacterBody2D" explicando quando usar qual (Rigid pra dinâmica simulada, Character pra controle direto)
- [x] 14.2 Atualizar invariante #3 em `CLAUDE.md`: incluir RigidBody2D na taxonomia e mencionar o solver
- [x] 14.3 Atualizar a seção "Para rodar Demos" em `CLAUDE.md`: ajustar descrições de demos 4 e 6 (removendo menção a matemática inline; mencionar `F3` momentum overlay)
- [x] 14.4 Atualizar `ROADMAP.md`: marcar rigid-body-2d como entregue na lista de capabilities
- [x] 14.5 Adicionar KDoc explicativo em `RigidBody2D.kt` com exemplo Kotlin e link conceitual pro design.md (NÃO incluir matemática completa no KDoc — fica em design.md)

## 15. Validação end-to-end

- [x] 15.1 `./gradlew :engine:test` — todos os testes passam
- [x] 15.2 `./gradlew :games:pong:run` — Pong continua jogável sem mudança visual (regressão de CharacterBody2D) — compila; validação visual fica para o usuário
- [x] 15.3 `./gradlew :games:tictactoe:run` — TicTacToe continua jogável (regressão Compose backend) — compila; validação visual fica para o usuário
- [x] 15.4 `./gradlew :games:demos:run` — todas as 6 demos rodam; demos 4 e 6 visualmente equivalentes ao baseline (ou intencionalmente diferentes — documentado) — compila; validação visual fica para o usuário
- [x] 15.5 `./gradlew :games:hello-world:run` — Hello world inalterado — compila; validação visual fica para o usuário
- [x] 15.6 Rodar `openspec validate add-rigid-body-2d --strict` — sem erros
