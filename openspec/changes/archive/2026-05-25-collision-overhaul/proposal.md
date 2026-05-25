## Why

Depois de `godot-style-foundation`, hooks, primitivas visuais, sinais e fixed-step ficam no estilo Godot — mas o modelo de **colisão** ainda é o original: um único `Collider` abstrato (que é `Node2D`), com `BoxCollider` concreto e um `PhysicsSystem` O(N²) que dispara um único callback `onCollide(other)`. Isso confunde dois conceitos que Godot mantém separados:

1. **forma de colisão** (rectangle, circle) vs.
2. **comportamento do corpo** (passa por cima como trigger? bloqueia como sólido? move-se via velocity?).

O resultado é que Pong, Demos e qualquer jogo futuro tratam tudo como `BoxCollider` e gambiarram a intenção em código (`other.name == "leftGoal"` para reconhecer trigger). Não há `Area2D`, `StaticBody2D`, `CharacterBody2D`, nem `CollisionShape2D` separando forma de corpo.

Esta change **reescreve o modelo de colisão no estilo Godot, sem dó**: introduz `CollisionObject2D` como abstração comum, ramificada em `Area2D` (trigger) e `PhysicsBody2D` (sólido) com sub-tipos `StaticBody2D` e `CharacterBody2D`; introduz `CollisionShape2D` como filho que carrega uma `Shape2D` polimórfica (`RectangleShape2D` / `CircleShape2D`); reescreve o `PhysicsSystem` para emitir eventos *entered/exited* com pareamento estável; apaga `Collider` e `BoxCollider` antigos; refaz Pong inteiro com a nova taxonomia.

## What Changes

### Novas classes em `:engine`

- **NEW** `abstract class CollisionObject2D : Node2D()` — base de tudo que participa de colisão. Mantém `groups`, `disabled: Boolean = false`, e quatro hooks abertos: `onAreaEntered(area: Area2D)`, `onAreaExited(area: Area2D)`, `onBodyEntered(body: PhysicsBody2D)`, `onBodyExited(body: PhysicsBody2D)`. Expõe quatro signals built-in (`areaEntered`, `areaExited`, `bodyEntered`, `bodyExited`) populados pelo `PhysicsSystem`.
- **NEW** `class Area2D : CollisionObject2D()` — overlap-only, **não bloqueia** outros corpos. Modelo Godot: gatilhos, sensores, áreas de pickup, gols, hitboxes.
- **NEW** `abstract class PhysicsBody2D : CollisionObject2D()` — base de corpos sólidos.
- **NEW** `class StaticBody2D : PhysicsBody2D()` — corpo sólido que **não se move por força/velocidade**. Position é alterada via script setando `transform` ou ficando parado. Modelo Godot: paredes, paddles do Pong (movidos por script), plataformas.
- **NEW** `class CharacterBody2D : PhysicsBody2D()` — corpo sólido com `velocity: Vec2` exposto. O engine **não integra automaticamente** — segue Godot: script faz `self.transform.position += self.velocity * dt` em `_physics_process`. Resolução de colisão e slide ficam fora desta change (futura `move_and_slide()`); o que diferencia `CharacterBody2D` de `StaticBody2D` é o slot `velocity` e a semântica clara em events.

### CollisionShape2D + Shape2D resources

- **NEW** `class CollisionShape2D : Node2D()` — Node filho de um `CollisionObject2D`. Possui `@Inspect var shape: Shape2D?` e `@Inspect var disabled: Boolean = false`. **Sem** `shape` ou com `disabled = true`, é ignorado.
- **NEW** `sealed class Shape2D` — resource (não-Node, só dados serializáveis):
  - `class RectangleShape2D(var size: Vec2)` — meia-extent? Não — `size` é largura×altura completa, com origem no top-left do `CollisionShape2D` no espaço local (alinhamento idêntico a `ColorRect`).
  - `class CircleShape2D(var radius: Float)` — círculo centrado na posição local do `CollisionShape2D`.

Um `CollisionObject2D` pode ter **vários** `CollisionShape2D` filhos; o engine considera todas as shapes ativas ao testar overlap. (Modelo Godot.)

### PhysicsSystem reescrito

- **BREAKING** `PhysicsSystem.step(scene)` agora:
  1. Enumera todos os `CollisionObject2D` no tree (pré-order).
  2. Para cada par (A, B) com `A.disabled == false` e `B.disabled == false` e A ≠ B, calcula o conjunto de pares de `CollisionShape2D` ativos e testa overlap (AABB para Rectangle×Rectangle, distância-vs-soma-de-raios para Circle×Circle, AABB-vs-circle para misto — todos `bounds()` derivados de `worldTransform()`).
  3. Marca a dupla (A, B) como "atualmente sobrepondo" se **qualquer** par de shapes intersecciona.
  4. Compara com o snapshot anterior (`previousOverlapping: Set<UnorderedPair<CollisionObject2D>>`):
     - pares novos → emite `_on_area_entered` / `_on_body_entered` (dependendo dos tipos) em **ambos** os lados;
     - pares antigos que sumiram → emite `_on_area_exited` / `_on_body_exited` em **ambos** os lados;
     - mantidos não disparam nada (sem evento "still touching").
  5. Atualiza `previousOverlapping`.
- Despacho:
  - A é Area2D, B é Area2D → ambos recebem `onAreaEntered/Exited` com o outro.
  - A é Area2D, B é PhysicsBody2D → A recebe `onBodyEntered/Exited(B)`, B recebe `onAreaEntered/Exited(A)`.
  - A é PhysicsBody2D, B é PhysicsBody2D → ambos recebem `onBodyEntered/Exited`.
- Signals built-in são emitidos no mesmo despacho: `area.areaEntered.emit(otherArea)`, etc.
- Mutação de cena durante o despacho continua segura via mutação diferida (já existente).

### Removidos

- **REMOVED** `com.neoutils.engine.physics.Collider` (abstract).
- **REMOVED** `com.neoutils.engine.physics.BoxCollider`.
- **REMOVED** hook `Collider.onCollide(other)` em Kotlin e `_on_collide(self, other)` em Python — substituídos pelos quatro novos hooks `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`.

### Python hooks novos

- **BREAKING** Python: `_on_collide` apaga. Entram:
  - `_on_area_entered(self, area)`
  - `_on_area_exited(self, area)`
  - `_on_body_entered(self, body)`
  - `_on_body_exited(self, body)`
- Mapeamento Kotlin↔Python espelhado (igual change anterior).
- Built-in signals `area_entered`, `area_exited`, `body_entered`, `body_exited` acessíveis em Python via `self.area_entered.connect(handler)`.

### Migração Pong

`pong.scene.json` é reescrito com a taxonomia nova:

- `topWall`, `bottomWall` → `StaticBody2D` + filho `CollisionShape2D` com `RectangleShape2D` posicionado fora do play field visível mas com bounds adequados (linha sólida).
- `leftGoal`, `rightGoal` → `Area2D` + filho `CollisionShape2D` com `RectangleShape2D`.
- Paddles (`left`, `right`) → `StaticBody2D` + filho `CollisionShape2D` com `RectangleShape2D` (movem-se via script setando `transform.position`).
- Ball → `CharacterBody2D` + filho `CollisionShape2D` com `CircleShape2D`. Script substitui `_on_collide` por:
  - `_on_area_entered(self, area)` → trata gol (emite `scored`, reseta).
  - `_on_body_entered(self, body)` → trata bounce (paddle ou parede, decidido via `groups`).
- Walls/paddles entram em groups `"walls"` e `"paddles"` (ou `paddle.left`/`paddle.right`) declarados no `scene.json` via `properties.groups: ["paddles"]` (ou hook `_ready` chamando `add_to_group`).

### Migração Demos

`SpawnerDemo` usa `BoxCollider` para o "trap central" hoje. Migra para `Area2D + CollisionShape2D + RectangleShape2D`. As bolinhas spawnadas também viram `Area2D` (não bloqueiam entre si).

### Migração TicTacToe

Velha **não usa collision** — fica intocada nesta change. (Bundle-tictactoe é change seguinte.)

### Roadmap

- Adiciona `collision-overhaul` ao roadmap com status `Active`.
- Adiciona **`game-asteroids`** (Planned) como jogo-validador desta change. Asteroids exercita: `Area2D` (balas, gols), `CharacterBody2D` com `velocity` (nave, asteróides), `CollisionShape2D` + `CircleShape2D` (todos os corpos), múltiplos `CollisionShape2D` por objeto (asteróide complexo), pareamento estável de `_on_body_entered`/`_on_area_entered` (bala destrói asteróide, asteróide destrói nave), Signal cascade (asteróide destruído spawna pedaços menores via signal), `Camera2D.bounds` como mundo com wrap-around, `Polygon2D` + `Line2D` para visuais wireframe, e força a introdução futura de `Renderer.withTransform` (rotação visual) — dependência conhecida que será resolvida quando Asteroids for implementado.

## Capabilities

### New Capabilities

- (nenhuma capability nova; tudo entra dentro de `engine-core` modificado)

### Modified Capabilities

- `engine-core`: `Collider`/`BoxCollider` removidos; `CollisionObject2D` + `Area2D` + `PhysicsBody2D` + `StaticBody2D` + `CharacterBody2D` adicionados; `CollisionShape2D` + `Shape2D` (sealed: `RectangleShape2D`, `CircleShape2D`) adicionados; `PhysicsSystem` reescrito com pair-tracking enter/exit; quatro hooks novos (`onAreaEntered`/`onAreaExited`/`onBodyEntered`/`onBodyExited`); signals built-in nos objects (`areaEntered`/`areaExited`/`bodyEntered`/`bodyExited`); `onCollide` removido.
- `python-scripting`: bindings de `BoxCollider`/`Collider` removidos; entram `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`, `RectangleShape2D`, `CircleShape2D`; hook `_on_collide` apaga; entram `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`; stubs `.pyi` atualizados.
- `pong-sample`: refeito com a nova taxonomia (Areas, Bodies, Shapes); ball usa `CharacterBody2D` com `velocity`; goals viram `Area2D`; walls e paddles viram `StaticBody2D`; ball script substitui `_on_collide` pelos quatro novos.

## Impact

- **Código tocado:**
  - `:engine` — `physics/Collider.kt` (REMOVE), `physics/BoxCollider.kt` (REMOVE), `physics/PhysicsSystem.kt` (reescreve), novos `physics/CollisionObject2D.kt`, `physics/Area2D.kt`, `physics/PhysicsBody2D.kt`, `physics/StaticBody2D.kt`, `physics/CharacterBody2D.kt`, `physics/CollisionShape2D.kt`, `physics/Shape2D.kt` (sealed + `RectangleShape2D`, `CircleShape2D`). `serialization/NodeRegistry.kt` adapta registros.
  - `:engine-bundle` — `NodeRegistry` ganha registros automáticos dos novos tipos; remove `BoxCollider`.
  - `:engine-bundle-python` — `PythonScriptHost` adapta bindings + hooks; AST inspector reconhece o tipo `Shape2D` (e sub-tipos) como anotação válida em `@Inspect` se preciso; stubs `.pyi` atualizados.
  - `:engine-skiko` — `DebugOverlay` (visualização de colliders) é refeita para iterar `CollisionShape2D` em vez de `Collider`.
  - `:engine-compose` — idem para `renderDebugOverlay`.
  - `:games:pong` — `pong.scene.json` reescrito (Areas + Bodies + Shapes), scripts `.py` migram para os hooks novos, ball usa `CharacterBody2D`.
  - `:games:demos` — `SpawnerDemo` migrado.
- **Documentação:** `CLAUDE.md` (seção Architectural Invariants invariante 3 atualizada para mencionar `CollisionObject2D` + `CollisionShape2D` + `PhysicsSystem`; seção Coding Conventions com signals built-in; tabela roadmap com `collision-overhaul` Active + `game-asteroids` Planned).
- **Sem impacto em:** hooks visuais (Renderer.withTransform ainda fora — Asteroids puxa isso), Camera2D, primitivas visuais (Polygon2D, Line2D etc.), `Signal<T>` (já é evento hub depois de change 1).
