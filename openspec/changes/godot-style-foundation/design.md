## Context

A engine hoje espelha 70% de Godot 2D mas com nomes próprios e pequenas inconsistências:

- Hooks: `onUpdate/onRender` em vez de `_process/_draw`.
- Loop de update sem separação física/frame: ball em Pong integra velocidade dentro do `onUpdate` com `dt` variável — tunneling possível em FPS baixo.
- `Signal<T>` existe como wrapper `var path: NodeRef` para wiring de editor futuro, mas **não é um event hub runtime** — Python comunica score via callback ad-hoc `ball._on_score`.
- `Shape` carrega `Kind.Rect | Kind.Circle` em um único tipo, com rotação visual quebrada (documentado em `Shape.kt`).
- Tamanho de mundo vive como prop de jogo (`playFieldHeight` na Pong) — nenhuma noção `Scene.viewport`.
- `Text` é o único nó textual; o nome diverge do Godot (`Label`).
- Comunicação entre nós Python passa por atributos mágicos (`other.parent.name == "left"`).

Esta change consolida o vocabulário Godot **sem** mexer no modelo de colisão — separação importante porque colisão é a maior mudança estrutural e merece sua própria change.

## Goals / Non-Goals

**Goals:**

- Hooks Kotlin↔Python espelhados em estilo Godot (`onProcess`/`_process`, `onDraw`/`_draw`, `onEnter`/`_ready`, `onExit`/`_exit_tree`, `onPhysicsProcess`/`_physics_process`).
- Fixed-step `onPhysicsProcess` (default 60Hz) — base para determinismo em colisão futura.
- `Signal<T>` como event hub real, com bridge Python.
- `groups` + `getNodesInGroup` substituindo dependência em `parent.name` ou nomes mágicos.
- Camera2D + Scene.viewport substituindo `playFieldHeight` em scripts.
- Vocabulário visual Godot: `Label`, `ColorRect`, `Polygon2D`, `Line2D`, `Circle2D`. `Shape` deleta.
- Pong/Demos/TicTacToe migrados; smoke-test visual idêntico ao anterior.

**Non-Goals:**

- **Não** introduz `Area2D`/`StaticBody2D`/`CharacterBody2D`/`CollisionShape2D` — change 2.
- **Não** introduz `Timer`, `Tween`, `AnimationPlayer`, `Sprite2D`, `AudioStreamPlayer` — entram com jogos que pedirem.
- **Não** introduz pause mode ou process priority — fora do escopo didático atual.
- **Não** mexe em `Renderer.withTransform` (rotação visual aplicada): permanece pendência conhecida; primitivas novas desenham em world-space sem transformar canvas. Justificativa: introduzir `withTransform` adicionaria push/pop no SPI dos dois backends e ainda não há jogo que requeira rotação visual (Asteroids está em change 4).
- **Não** introduz `InputMap` (actions) — fica para change 3 `bundle-tictactoe` que tem mais necessidade (e Asteroids).

## Decisions

### D1. Hook renames são quebras explícitas, sem alias

**Decisão:** Rename hard. Nada de aliases tipo `@Deprecated fun onUpdate(dt) = onProcess(dt)`. O custo de manter dois nomes é maior que o custo de migrar três jogos.

**Custo:** Pong scripts (5 arquivos), Demos (3 arquivos Kotlin), TicTacToe (3 arquivos Kotlin), tasks de regression-test.

**Alternativa rejeitada:** alias `@Deprecated`. Polui IDE, atrasa o aprendizado.

### D2. Python `_ready/_process/_draw/_exit_tree/_on_collide` segue 100% Godot

**Decisão:** Snake-case com underscore-prefix idêntico a Godot. O Kotlin `onProcess` é a contraparte canônica via SPI; o mapeamento é fixo:

```
Kotlin                       Python
─────────────────────────────────────
onEnter()                    _ready(self)
onProcess(dt)                _process(self, dt)
onPhysicsProcess(dt)         _physics_process(self, dt)
onDraw(renderer)             _draw(self, renderer)
onExit()                     _exit_tree(self)
Collider.onCollide(other)    _on_collide(self, other)
```

**Alternativa rejeitada:** manter `on_*` em Python (mais próximo do Kotlin). Não-Godot, perde fidelidade ao mental model.

### D3. Fixed-step com accumulator dentro do GameLoop

**Decisão:** O accumulator vive em `GameLoop.tick(dtNanos)`. Backends não mudam.

```kotlin
class GameLoop(val scene: Scene, val renderer: Renderer, val input: Input, val physics: PhysicsSystem, val physicsHz: Int = 60) {
    private val physicsDt: Float = 1f / physicsHz
    private val maxStepsPerFrame: Int = 5
    private var accumulator: Float = 0f

    fun tick(dtNanos: Long) {
        val dt = dtNanos / 1_000_000_000f
        accumulator += dt
        var steps = 0
        while (accumulator >= physicsDt && steps < maxStepsPerFrame) {
            scene.drainPending()
            scene.physicsProcess(physicsDt)
            scene.drainPending()
            physics.step(scene)
            accumulator -= physicsDt
            steps++
        }
        if (steps == maxStepsPerFrame && accumulator > physicsDt) accumulator = 0f  // spiral-of-death clamp
        scene.drainPending()
        scene.process(dt)
        scene.drainPending()
        scene.render(renderer)
    }
}
```

**Por que clamp em 5 steps:** se a janela ficou suspensa 1s, não faz sentido rodar 60 ticks de física para "alcançar" — visualmente o jogo já vai pular mesmo; melhor preservar responsividade do que fidelidade temporal.

**Alternativa rejeitada:** rodar física no mesmo `dt` variável. Mantém o bug de tunneling em FPS baixo e impede determinismo na change 2.

### D4. Signal<T> redesenhado como event hub runtime

**Decisão:** `Signal<T>` deixa de ser apenas um marker para wiring serializado e vira um event hub real:

```kotlin
class Signal<T> {
    private val handlers = mutableListOf<(T) -> Unit>()
    fun connect(handler: (T) -> Unit): Disposable = ...
    fun disconnect(disposable: Disposable)
    fun emit(value: T)
}
```

Em Python:

```python
# extends Node2D
scored: Signal = signal(str)  # detectado por AST inspector

def _on_other_node_scored(self, side):
    ...
```

E em algum lugar:

```python
ball.scored.connect(self._on_other_node_scored)
ball.scored.emit("Left")
```

**Inspector AST:** procura top-level `AnnAssign` cujo target name termina convencionalmente em letras minúsculas (mas só importa a anotação) e cuja annotation **é exatamente o identificador `Signal`** (não tipo paramétrico — Python não tem `Signal<T>` runtime; o `signal(str)` factory aceita um tipo apenas como hint/documentação, ignorado runtime).

**`@Inspect` ainda existe:** continua marcando configurações estáticas. `Signal` em Python NÃO é `@Inspect` (não é configuração — é canal runtime).

**Compatibilidade do Signal antigo:** o uso atual de `Signal` em `:engine` é mínimo (apenas o tipo declarado, não usado em scripts ainda). A reescrita não tem callers a quebrar dentro do engine.

### D5. Camera2D + Scene.viewport, sem world-bounds em Scene

**Decisão:** `Scene` ganha `size: Vec2` (preenchida pelo host em `resize(w,h)` — já é fato hoje, só formalizado) e `viewport: Rect` computado.

`Camera2D` é um `Node2D` com `bounds: Rect` e `current: Boolean`. Sem Camera2D ativa, `scene.viewport = Rect(Vec2.ZERO, scene.size)`.

**Por que não colocar `bounds` direto em `Scene`:** porque Asteroids (change 4) precisa de wrap-around com a câmera definindo o "world clip" que é maior que o viewport visual. `Camera2D` desacopla esses dois conceitos cedo.

**Look-up de `currentCamera`:** linear no tree. Aceitável: cenas didáticas têm dezenas de nós, não milhares. Otimização futura: cache em `Scene` setado quando `Camera2D` muda `current` — fora do escopo desta change.

### D6. Shape removido, primitivas dedicadas substituem (com Polygon2D + Line2D)

**Decisão:** `Shape` deletado. No lugar:

| Nó            | O que desenha                                           |
|---------------|--------------------------------------------------------|
| `ColorRect`   | retângulo preenchido (`size` + `color`)                |
| `Circle2D`    | círculo preenchido (`radius` + `color`)                |
| `Line2D`      | segmentos conectados (`points`, `thickness`, `color`)  |
| `Polygon2D`   | polígono preenchido por vértices (`points`, `color`)   |

Todos honram `worldTransform().position`; **nenhum** rotaciona ainda (limitação documentada; futura `Renderer.withTransform`).

`Renderer` SPI ganha `drawPolygon(points: List<Vec2>, color: Color)`. Skiko implementa via `Path`. Compose implementa via `Path` Compose.

**Por que ambos `ColorRect` e `Polygon2D`:** ergonomia (ColorRect é o caso comum 1:1 com `drawRect`) vs. flexibilidade (Polygon2D cobre Asteroids/naves/asteróides). Godot tem ambos.

**Por que `Circle2D` (não-Godot estrito):** Godot puro não tem `Circle2D` — desenhe via `_draw` ou use `Sprite2D` com textura redonda. Mas é ergonomicamente útil (Pong ball, telinhas debug) e o usuário pediu explicitamente.

**Migração de scripts Pong:** paddle.py e ball.py **deixam de criar `Shape`** e passam a desenhar via `_draw(self, renderer)` direto (Godot-orthodox). A bola é um `BoxCollider` (em change 1) cujo `_draw` desenha um `circle`. Após change 2, vira `Area2D + CircleShape2D + Circle2D` (visual dedicado), mas isso é outra change.

### D7. Label = Text renomeado, sem mais nada

**Decisão:** `Text` vira `Label`. Mesma API (`text`, `size`, `color`). Sem `RichTextLabel`, `LineEdit`, etc. — entrará quando algum jogo precisar.

### D8. Groups são Set<String> mutável + tree-walk on-demand

**Decisão:** Sem índice global por grupo. `Scene.getNodesInGroup(name)` faz tree-walk e filtra por `node.groups`. Custo `O(N)`; com `N < 100` em jogos didáticos, irrelevante.

**Alternativa rejeitada:** índice `Map<String, MutableList<Node>>` em Scene. Otimização prematura; requer manutenção em add/remove e em remove-from-group; complexidade não paga.

### D9. Pong scripts migrados para `_draw` direto (sem nós visuais filhos)

**Decisão:** `paddle.py` declara `_draw(self, renderer)` que desenha o próprio retângulo. `ball.py` idem para o círculo. Nada de adicionar filho `ColorRect`/`Circle2D` dentro do `_ready`.

**Justificativa:** mais Godot-orthodox (em Godot um `Sprite2D` desenha-se via `_draw` interno; nós com gameplay próprio frequentemente desenham diretamente). Reduz nó-noise no scene graph. Mantém o nó-script como entidade auto-contida.

**`centerLine` em Pong:** vira `Line2D` filho declarado no `scene.json` (puramente visual, sem script). Demonstra primitiva nova em uso declarativo.

### D10. Snake como jogo-validador no roadmap

**Decisão:** `game-snake` entra como `Planned` no roadmap. Por quê Snake e não outro:

- Stress no `_process` discreto via Timer (introdução natural de Timer em change futura — mas Snake também roda sem Timer, em tick contínuo com acumulator interno);
- Stress no `Signal` (`fruit_eaten`, `game_over`);
- Stress no `Camera2D.bounds` como play field grid;
- Stress no `Polygon2D`/`ColorRect`/`Label` como visual stack inteiro;
- **Não** depende de colisão — perfeito para validar fundação independentemente de `collision-overhaul`.

Snake **não** é implementada nesta change (a change só **adiciona ao roadmap**; implementação ocorre em momento oportuno).

## Risks / Trade-offs

- **R1. Migração simultânea de Pong (Python) + Demos (Kotlin) + TicTacToe (Kotlin)** — risco de quebrar visualmente os três e demorar a perceber. **Mitigação:** smoke-test manual obrigatório nos três jogos como tarefa final.
- **R2. Bridge Python ↔ Kotlin Signal** — GraalPy precisa proxiar `Signal.connect(callback)` onde `callback` é função Python; teste de invocação cross-language obrigatório.
- **R3. Fixed-step muda timing perceptível em jogos atuais** — Pong ball passa a integrar em 1/60s constantes; provavelmente fica visualmente **mais suave** (anti-aliasing temporal). Mitigação: comparar gameplay sentido antes/depois.
- **R4. `Camera2D` adicionado mas Pong tem viewport == scene.size** — Camera2D em Pong fica "decorativa" (bounds == scene.size). Honesto: foi adicionada para o roadmap, não para extrair valor imediato em Pong. Aceitável.
- **R5. `_draw` direto em scripts (D9) sai do declarativo** — perde a chance de "ver o nó visual no inspetor futuro". Mitigação: `_draw` continua sendo um hook do nó, e o inspetor pode mostrar "este nó tem um `_draw` custom" sem perder funcionalidade.
- **R6. Spiral-of-death clamp pode mascarar problemas** — se a física estourar 5 steps por frame consistentemente, o jogo "dança". Mitigação: logar warning em `Debug` quando o clamp ativar.

## Migration Plan

1. Adicionar todas as APIs novas (renomes, primitivas, `onPhysicsProcess`, `Signal` redesenhado, `groups`, `Camera2D`) sem remover as antigas onde possível em PR.
2. Migrar Pong scripts (`pong/scripts/*.py` + `pong.scene.json`).
3. Migrar Demos (Kotlin).
4. Migrar TicTacToe (Kotlin, mas ainda Compose-only — bundle-tictactoe é outra change).
5. Remover Shape/Text e renomear hooks finais. (Quebra total — só dá certo se 2-4 estiverem completos.)
6. Stubs `.pyi` atualizados.
7. Smoke test dos três jogos.
