## 1. Shape2D resources

- [x] 1.1 Criar `engine/.../physics/Shape2D.kt`: `@Serializable sealed class Shape2D { abstract fun bounds(world: Transform, localOffset: Vec2): Rect }` — `bounds` retorna a AABB world-space dada o transform do `CollisionShape2D` pai (que aplica scale/rotation) e o offset local (na maioria dos casos `Vec2.ZERO`).
- [x] 1.2 No mesmo arquivo: `@Serializable class RectangleShape2D : Shape2D() { @Inspect var size: Vec2 = Vec2(10f, 10f); override fun bounds(...) = ... }` — AABB tem origin no `world.position + localOffset` rotacionada por `world.rotation`, dimensões `size * world.scale`. Quando `world.rotation != 0f`, reusar lógica do `BoxCollider` antigo (4 cantos rotacionados).
- [x] 1.3 No mesmo arquivo: `@Serializable class CircleShape2D : Shape2D() { @Inspect var radius: Float = 5f; override fun bounds(...) = ... }` — AABB é quadrado centrado em `world.position + localOffset` com lado `2 * radius * max(world.scale.x, world.scale.y)`.
- [x] 1.4 Adicionar função pura `Shape2D.overlap(a: Shape2D, aWorld: Transform, b: Shape2D, bWorld: Transform): Boolean` (top-level no package `physics`) implementando rect-rect (AABB overlap), circle-circle (distance < r1+r2), rect-circle (closest-point-on-rect-to-circle-center test). Localmente rotation é tratada via AABB do `bounds()` por enquanto (didático — Asteroids puxa rotação OBB depois se precisar).
- [x] 1.5 Configurar kotlinx.serialization `SerializersModule` para `Shape2D` polymorphic em `:engine` (ou via `@Polymorphic` direto na sealed se a versão de kotlinx suportar). Testar round-trip JSON com `RectangleShape2D` e `CircleShape2D` aninhados em um `CollisionShape2D`.

## 2. CollisionShape2D

- [x] 2.1 Criar `engine/.../physics/CollisionShape2D.kt`: `@Serializable class CollisionShape2D : Node2D() { @Inspect var shape: Shape2D? = null; @Inspect var disabled: Boolean = false; }`.
- [x] 2.2 `CollisionShape2D.worldBounds(): Rect?` retorna `shape?.bounds(worldTransform(), Vec2.ZERO)` se `!disabled` e `shape != null`, senão `null`.

## 3. CollisionObject2D base

- [x] 3.1 Criar `engine/.../physics/CollisionObject2D.kt`: `@Serializable abstract class CollisionObject2D : Node2D() { @Inspect var disabled: Boolean = false }`.
- [x] 3.2 Adicionar quatro hooks abertos:
  ```kotlin
  open fun onAreaEntered(area: Area2D) { (scriptInstance as? CollisionScriptInstanceContract)?.onAreaEntered(area) }
  open fun onAreaExited(area: Area2D)  { ... }
  open fun onBodyEntered(body: PhysicsBody2D) { ... }
  open fun onBodyExited(body: PhysicsBody2D)  { ... }
  ```
- [x] 3.3 Adicionar quatro signals built-in como `@Transient val`:
  ```kotlin
  @Transient val areaEntered: Signal<Area2D> = Signal()
  @Transient val areaExited:  Signal<Area2D> = Signal()
  @Transient val bodyEntered: Signal<PhysicsBody2D> = Signal()
  @Transient val bodyExited:  Signal<PhysicsBody2D> = Signal()
  ```
- [x] 3.4 Helper interno `fun collectActiveShapes(): List<Pair<CollisionShape2D, Rect>>` que percorre filhos diretos do `CollisionObject2D`, filtra `CollisionShape2D` ativos com `bounds != null`, e retorna a lista. (Aceita-se `CollisionShape2D` direto-filhos apenas — sem recursão profunda nesta change; documentado).

## 4. Area2D, PhysicsBody2D, StaticBody2D, CharacterBody2D

- [x] 4.1 Criar `engine/.../physics/Area2D.kt`: `@Serializable class Area2D : CollisionObject2D()`. Sem campos novos.
- [x] 4.2 Criar `engine/.../physics/PhysicsBody2D.kt`: `@Serializable abstract class PhysicsBody2D : CollisionObject2D()`.
- [x] 4.3 Criar `engine/.../physics/StaticBody2D.kt`: `@Serializable class StaticBody2D : PhysicsBody2D()`. Sem campos novos.
- [x] 4.4 Criar `engine/.../physics/CharacterBody2D.kt`: `@Serializable class CharacterBody2D : PhysicsBody2D() { @Inspect var velocity: Vec2 = Vec2.ZERO }`.

## 5. PhysicsSystem rewrite

- [x] 5.1 Reescrever `engine/.../physics/PhysicsSystem.kt`. Estado novo: `private val previousOverlapping: MutableSet<UnorderedPair<CollisionObject2D>> = HashSet()`.
- [x] 5.2 Criar classe value `physics/UnorderedPair.kt`: `class UnorderedPair<T>(val a: T, val b: T)` com `equals`/`hashCode` insensíveis à ordem.
- [x] 5.3 Implementar `step(scene: Scene)`:
  1. coletar `objects = list of CollisionObject2D` em pré-order, filtrando `disabled == false`.
  2. para cada par (i < j), coletar shapes ativos de ambos via `collectActiveShapes()`.
  3. se algum par-de-shapes intersecta (via `Shape2D.overlap`), adicionar `UnorderedPair(objects[i], objects[j])` em `currentOverlapping`.
  4. para `enteredPair in currentOverlapping - previousOverlapping`: chamar `dispatchEnter(enteredPair)`.
  5. para `exitedPair in previousOverlapping - currentOverlapping`: chamar `dispatchExit(exitedPair)`.
  6. `previousOverlapping.clear(); previousOverlapping.addAll(currentOverlapping)`.
- [x] 5.4 `private fun dispatchEnter(pair: UnorderedPair<CollisionObject2D>)`:
  - if both `Area2D`: a.onAreaEntered(b); b.onAreaEntered(a); a.areaEntered.emit(b); b.areaEntered.emit(a).
  - if a `Area2D` and b `PhysicsBody2D`: a.onBodyEntered(b); a.bodyEntered.emit(b); b.onAreaEntered(a); b.areaEntered.emit(a).
  - if both `PhysicsBody2D`: a.onBodyEntered(b); b.onBodyEntered(a); a.bodyEntered.emit(b); b.bodyEntered.emit(a).
- [x] 5.5 `dispatchExit` análogo com `*Exited` e `*Exited` signals.
- [x] 5.6 No `Node.detachFromLiveTree` (já existente), interceptar — se o node-é-`CollisionObject2D`, remover qualquer entrada em `physicsSystem.previousOverlapping` que o contenha. Como o `PhysicsSystem` é parâmetro do `GameLoop` e não está acessível do Node, optar por: cada step, **filtrar** `previousOverlapping` removendo pares cujos endpoints não estão mais em `scene` (i.e., `!isLive`). Adicionar essa filtragem no início do `step`.

## 6. Remover Collider / BoxCollider

- [x] 6.1 Deletar `engine/.../physics/Collider.kt`.
- [x] 6.2 Deletar `engine/.../physics/BoxCollider.kt`.
- [x] 6.3 Em `Node.kt` e `ScriptInstanceContract.kt`: remover `onCollide`. Verificar que `Node` não mantém override default `onCollide`. Em `ScriptInstanceContract`: remover `fun onCollide(other: ...)`.
- [x] 6.4 Em `:engine-bundle/NodeRegistry`: remover registros `BoxCollider`. Adicionar `Area2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`. (PhysicsBody2D e CollisionObject2D são abstratos — não vão no registry.)

## 7. ScriptInstance dispatch para hooks novos

- [x] 7.1 Adicionar à interface `ScriptInstanceContract` quatro métodos opcionais (no-op default em interface vazia ou via default impl):
  ```kotlin
  fun onAreaEntered(area: Area2D) {}
  fun onAreaExited(area: Area2D) {}
  fun onBodyEntered(body: PhysicsBody2D) {}
  fun onBodyExited(body: PhysicsBody2D) {}
  ```
- [x] 7.2 Remover `fun onCollide(other: ...)` do contrato.

## 8. Python bridge

- [x] 8.1 Em `PythonScriptHost.kt`: nos bindings do Context, remover `BoxCollider` (e `Collider` se exposto). Adicionar `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`, `RectangleShape2D`, `CircleShape2D`.
- [x] 8.2 Em `PythonScriptHost.kt`: dispatcher do ScriptInstance — adicionar tentativas para `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`. Remover tentativa para `_on_collide`.
- [x] 8.3 Em `PythonScriptHost.kt`: na lista de tipos válidos para `# extends`, remover `BoxCollider`. Adicionar `Area2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D` (este último permitido caso scripts customizem CollisionShape2D, embora improvável).

## 9. Debug overlay (visualização de colliders)

- [x] 9.1 Em `engine/.../dx/DebugOverlay.kt`: o atual desenho de "colliders" passa a iterar `CollisionShape2D` ativos no scene, e desenha `bounds()` (AABB) com `drawRect(filled=false)`. Colorir `Area2D` filhos com cor distinta de `PhysicsBody2D` filhos (e.g., Area = verde transparente, Body = vermelho transparente).
- [x] 9.2 Verificar que os backends Skiko/Compose continuam roteando `F2` para o mesmo overlay (sem mudança no SPI Renderer).

## 10. Migrar `:games:pong`

- [x] 10.1 `pong.scene.json`: trocar tipo `com.neoutils.engine.physics.BoxCollider` por:
  - `Ball` → `com.neoutils.engine.physics.CharacterBody2D` com filho `CollisionShape2D` (`shape: CircleShape2D` com `radius: 8`).
  - `topWall`, `bottomWall` → `StaticBody2D` com filho `CollisionShape2D` (`shape: RectangleShape2D` com `size: Vec2(800, 4)`), posições `y = 0` e `y = field-height - 4` (ou nas bordas reais).
  - `leftGoal`, `rightGoal` → `Area2D` com filho `CollisionShape2D` (`shape: RectangleShape2D` com `size: Vec2(8, fieldHeight)`), posições nas extremidades esquerda/direita.
  - Paddles (`left`, `right`) → trocar `Node2D` para `StaticBody2D`. Adicionar filho `CollisionShape2D` (`shape: RectangleShape2D` com `size: Vec2(16, 96)`). Remover criação do collider via `paddle.py._ready`.
- [x] 10.2 `pong.scene.json`: declarar groups via `properties.groups`. Paddles em `["paddles", "paddle.left"]` / `["paddles", "paddle.right"]`. Walls em `["walls"]`.
- [x] 10.3 `ball.py`: remover `_on_collide`. Adicionar:
  - `_on_area_entered(self, area)` — se `area.name == "leftGoal"` ou `"rightGoal"`, emite `scored` com lado certo e reseta. Substitui o `_scored_this_tick` (não precisa mais — enter já é one-shot por entrada).
  - `_on_body_entered(self, body)` — se `body.is_in_group("walls")`, flip vertical; se `body.is_in_group("paddles")`, bounce com ângulo (lógica atual do paddle hit migrada).
- [x] 10.4 `ball.py`: trocar `# extends BoxCollider` por `# extends CharacterBody2D`. Substituir `self.size` (usado para mover/draw) por leitura via `self.collision_shape().shape.radius` ou pela própria const local `self.ballSize`. Manter integração via `self.velocity` exposto no `CharacterBody2D`:
  ```python
  def _physics_process(self, dt):
      self.transform.position += self.velocity * dt
  ```
  (em vez de `self._velocity`).
- [x] 10.5 `paddle.py`: trocar `# extends Node2D` por `# extends StaticBody2D`. Remover bloco `_ready` que criava `BoxCollider` filho. Manter o resto (input → set transform.position).
- [x] 10.6 `goal.py`: trocar `# extends BoxCollider` por `# extends Area2D`. Hook permanece `_ready` (configura `name`, possivelmente size).
- [x] 10.7 Verificar interop `Vec2` arithmetic em Python — `Vec2 + Vec2`, `Vec2 * float`. Se GraalPy interop não faz isso automaticamente, atualizar bindings para expor operadores. (Provavelmente já funciona; checar.)

## 11. Migrar `:games:demos`

- [x] 11.1 `SpawnerDemo.kt`: substituir `BoxCollider` central por `Area2D` com filho `CollisionShape2D(RectangleShape2D(size = ...))`. As bolinhas spawnadas também viram `Area2D` com `CircleShape2D`.
- [x] 11.2 Os overrides `onCollide` em SpawnerDemo viram `onAreaEntered` (Area-vs-Area).

## 12. Stubs `.pyi`

- [x] 12.1 Em `engine-bundle-python/src/main/resources/stubs/engine/`: remover stubs de `BoxCollider`, `Collider`. Adicionar `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`, `Shape2D`, `RectangleShape2D`, `CircleShape2D`.
- [x] 12.2 No stub de `CollisionObject2D` (ou `node.pyi`): adicionar declarações dos quatro hooks `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`, e os atributos `area_entered`, `area_exited`, `body_entered`, `body_exited` (todos `Signal`).
- [x] 12.3 Remover stub de `_on_collide`.

## 13. Docs

- [x] 13.1 `CLAUDE.md`: atualizar **Architectural Invariants** item 3 — texto novo: "Colisão via `CollisionObject2D` (Area2D ou PhysicsBody2D) + `CollisionShape2D` filho com `Shape2D` polimórfico (Rectangle/Circle); `PhysicsSystem` central enumera pares e dispara enter/exit em fixed-step."
- [x] 13.2 `CLAUDE.md`: atualizar seção **Coding Conventions / Scripting contract** com os quatro hooks novos (`_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`) e built-in signals.
- [x] 13.3 `CLAUDE.md`: na tabela do roadmap, adicionar linha `collision-overhaul` com status `Active` e resumo curto.
- [x] 13.4 `CLAUDE.md`: na tabela do roadmap, adicionar linha `game-asteroids` com status `Planned` e resumo: "Validador da collision-overhaul + integração com fundação Godot-style: Area2D para balas, CharacterBody2D para nave/asteróides, CollisionShape2D + CircleShape2D, múltiplas shapes por objeto, signal cascade (asteróide quebra em pedaços), Camera2D.bounds para wrap-around, Polygon2D/Line2D wireframe; vai puxar Renderer.withTransform quando for implementado."
- [x] 13.5 KDoc em `PhysicsSystem` documentando o pair-tracking enter/exit e o cleanup de nodes detached.
- [x] 13.6 KDoc em `CharacterBody2D.velocity` esclarecendo que **o engine não integra** — script é responsável.

## 14. Smoke & verify

- [x] 14.1 `./gradlew check` passa.
- [x] 14.2 `./gradlew :games:pong:run` abre e roda — paddle responde a W/S, IA persegue, ball quica em walls + paddles, gols funcionam (score atualiza, ball reseta), F1/F2 toggles. **Verificar visualmente que F2 mostra Areas e Bodies em cores distintas.**
- [x] 14.3 `./gradlew :games:demos:run` abre e roda — teclas 1/2/3 trocam demos, spawner adiciona/remove bolinhas (trap central remove), F2 mostra colliders novos. **Regressão conhecida**: demos 4 (CollisionStress) e 5 (RotatingBox) exibem tunneling (algumas balls em 4, várias em 5) porque o novo modelo enter-only não recobra de pile-ups (4) nem do AABB-envelope permanente de retângulos rotacionados (5). Documentado em `design.md §Known Regressions`; fixes propostos como follow-ups `collision-iterative-resolution` e `collision-rotated-shapes` no ROADMAP.
- [x] 14.4 `./gradlew :games:tictactoe:run` abre e roda — sem colisão, deve continuar funcionando idêntico.
- [x] 14.5 `openspec validate collision-overhaul --strict` passa.
