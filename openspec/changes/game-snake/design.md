## Context

A engine atinge `game-snake` com tudo no lugar: scene graph Godot-style, `_physics_process` fixed-step, `Signal<T>` em ambas direções (Python → Kotlin via Pong, Kotlin → Python via `node-timer`), `wasKeyPressed` edge-triggered no Input SPI, `Camera2D.bounds`, primitivas visuais (`ColorRect`, `Label`), `BundleLoader` + `PythonScriptHost`. A única peça nova requerida — `Timer` Node — vem na change `node-timer`.

Snake é o terceiro jogo do repositório, o **primeiro com gameplay discreto/grid-based**. Pong e Velha cobrem outros nichos:

```
Jogo     Backend    Tick     Input          Validador de
─────    ───────    ─────    ─────          ───────────
Pong     Skiko      contínuo W/S            BoxCollider, PhysicsSystem, signals Python
Velha    Compose    n/a      mouse          segundo backend, mutação por clique
Snake    Skiko      discreto setas          Timer, wraparound, signals Kotlin→Python, scene graph mutation sob script
```

A grid é `20×20` células de `20px` num campo `400×400`. Tick lógico a `8Hz` (`waitTime=0.125`). Snake clássico Nokia.

## Goals / Non-Goals

**Goals:**
- Jogo Snake jogável `./gradlew :games:snake:run` com restart por tecla.
- Validar `Timer` em produção real (não só na cena demo).
- Validar mutação dinâmica do scene graph dirigida por script Python (addChild/removeChild a cada tick).
- Validar wraparound usando `Camera2D.bounds` como contrato lógico.
- Validar Kotlin Signal → Python handler (`Timer.timeout.connect`).
- Validar input edge-triggered (`wasKeyPressed`) com buffer de direção.
- Manter Snake **inteiramente em Python** — `Main.kt` parecido com Pong, sem Kotlin de gameplay.

**Non-Goals:**
- Dificuldade progressiva (aceleração com score). Validador não precisa.
- Sons, música, animações.
- Menu de start / pause / configurações.
- Power-ups, obstáculos, modos alternativos.
- Multiplayer / IA. Snake é single-player puro.
- Persistência de high score.
- Suporte ao backend Compose (Snake é Skiko-only, igual Pong).

## Decisions

### Segmentos = filhos `ColorRect` dinâmicos, não lista interna

Cada segmento da cobra é um `ColorRect` filho de `Snake`. A cada tick: `addChild(newHead)` na frente; se não comeu, `removeChild(tail)`. Cresce: omite o `removeChild`.

**Por quê:** valida o caminho que mais importa — mutação do scene graph dirigida por script. Lista interna seria mais rápida mas zero exercício de fundação.

**Alternativa rejeitada:** `_draw` custom desenhando N rects sobre uma lista de `Vec2`. Mais barato em ciclos, mas não valida `addChild`/`removeChild` em loop apertado.

### Tick lógico via `Timer` filho, não acumulador em `_physics_process`

`Snake.scene.json` declara um `Timer` filho chamado `MoveTimer` com `waitTime=0.125`, `processCallback=PHYSICS`, `autostart=true`. `snake.py._ready` conecta `MoveTimer.timeout` → `snake._tick`.

**Por quê:** já fechamos no explore — Timer Node é a forma canônica Godot. Acumulador seria duplicação de lógica.

### Direção é uma `Vec2` discreta `(±20, 0)` ou `(0, ±20)`; rotação 90° apenas

`snake.py` mantém `self._direction: Vec2`. Mudanças de direção checadas em `_process` (frame-step) lendo `wasKeyPressed(Key.ArrowUp/Down/Left/Right)`. Bloqueio de reversão: ignora seta cuja direção é o oposto exato da corrente.

**Buffer de direção (1-slot):** mudanças aplicadas só no próximo `_tick`. Isso evita o problema "apertou ← e ↑ entre dois ticks; só uma é aplicada na ordem correta": guardamos a última seta válida desde o tick anterior, aplicamos no início do próximo tick, depois consumimos.

```python
def _process(self, dt):
    if wasKeyPressed(Key.ArrowUp)    and self._direction.y != +20: self._pending = Vec2(0, -20)
    if wasKeyPressed(Key.ArrowDown)  and self._direction.y != -20: self._pending = Vec2(0, +20)
    # ... etc

def _tick(self):
    if self._pending is not None:
        self._direction = self._pending
        self._pending = None
    # advance head, check food/self-collide, etc.
```

### Wraparound via módulo sobre `Camera2D.bounds`

```python
camera = self._node.parent.findChild("Camera2D")
bounds = camera.bounds   # Rect

new_x = (head_x + direction.x) % bounds.size.x
new_y = (head_y + direction.y) % bounds.size.y
```

Como o módulo Python `%` retorna positivo para dividendo negativo (Python semantics, diferente de C), `(−20) % 400 == 380`. Funciona out-of-the-box para movimento na esquerda/cima cruzando borda.

**Decisão:** ler `bounds` da Camera no `_ready` e cachear; mudar `bounds` em runtime é considerado out-of-scope (a engine ainda não muda câmera em runtime nesse jogo). Caching pelo lado seguro.

### Food: nó `ColorRect` com script, conhece a Snake via `findChild`

`food.py` em `_ready` resolve `script_of(scene.findChild("Snake"))` e conecta-se ao signal `foodEaten` (declarado em `snake.py`). Handler escolhe uma nova posição aleatória **em célula vazia** (não overlap com nenhum segmento da cobra).

**Cálculo de "célula vazia":** enumera todas as células do grid; remove as ocupadas pelos segmentos atuais; escolhe uniformemente entre as restantes. O(N²) em N=segmentos+grid_size; suficiente para Snake.

**Alternativa rejeitada:** comida re-randomiza sem checar overlap, retry se overlap. Mais simples mas faz "comida invisível" sob a cobra; ruim UX.

### Game over via signal Python `gameOver`, sem panic Kotlin

`snake.py` declara `gameOver: Signal = signal()` (zero args). Quando auto-colisão detectada: `self.gameOver.emit()` + `MoveTimer.stop()` + estado interno `self._dead = True`.

Um HUD listener: `snake.gameOver.connect(handler)` em `_ready` do script do `GameOverLabel` (ou da scene principal); handler seta `label.visible = true`.

### Restart via `snake.py.reset()`, sem `Scene.reload()`

`reset()`:
1. Remove todos os filhos `ColorRect` (segmentos atuais) via `removeChild` em loop.
2. Recria `ColorRect` cabeça inicial no centro do bounds.
3. Reseta `self._direction = Vec2(20, 0)`, `self._pending = None`, `self._dead = False`, score=0.
4. Emite `restart` signal (HUD esconde `GameOverLabel`; Food reposiciona).
5. `MoveTimer.start()`.

Trigger: em `_process`, se `self._dead and wasKeyPressed(Key.Enter)`: chama `reset()`.

**Mutação durante traversal:** `removeChild` é diferido (`pendingRemove`). Múltiplos `removeChild` na mesma frame agregam para após o tick. O `reset()` deve rodar em `_process` (frame-step), não dentro de outro `_physics_process` — Enter dispara em `_process`, perfeito.

### Score = label simples atualizado em handler

`scoreLabel.py` (mínimo) escuta `foodEaten` e incrementa contador local; atualiza `label.text = "Score: {n}"`. Reset zera de volta a 0.

### Layout do scene.json

```json
{
  "version": 2,
  "type": "engine.Node2D",
  "name": "SnakeScene",
  "properties": {
    "transform": { "position": {"x":0,"y":0}, "scale": {"x":1,"y":1}, "rotation": 0 }
  },
  "children": [
    {
      "type": "engine.Camera2D",
      "name": "Camera2D",
      "properties": {
        "current": true,
        "bounds": { "position": {"x":0,"y":0}, "size": {"x":400,"y":400} }
      }
    },
    {
      "type": "engine.Node2D",
      "name": "Snake",
      "script": "scripts/snake.py",
      "properties": {
        "transform": { "position": {"x":0,"y":0}, "scale": {"x":1,"y":1}, "rotation": 0 },
        "cellSize": 20.0,
        "startCell": { "x": 10, "y": 10 }
      },
      "children": [
        {
          "type": "engine.Timer",
          "name": "MoveTimer",
          "properties": {
            "waitTime": 0.125,
            "autostart": true,
            "oneShot": false,
            "processCallback": "PHYSICS"
          }
        }
      ]
    },
    {
      "type": "engine.ColorRect",
      "name": "Food",
      "script": "scripts/food.py",
      "properties": {
        "transform": { "position": {"x":200,"y":200}, "scale": {"x":1,"y":1}, "rotation": 0 },
        "size": { "x": 20, "y": 20 },
        "color": { "r": 1.0, "g": 0.3, "b": 0.3, "a": 1.0 }
      }
    },
    {
      "type": "engine.Label",
      "name": "ScoreLabel",
      "script": "scripts/score.py",
      "properties": {
        "transform": { "position": {"x":10,"y":10}, "scale": {"x":1,"y":1}, "rotation": 0 },
        "text": "Score: 0",
        "size": 16.0,
        "color": { "r": 1.0, "g": 1.0, "b": 1.0, "a": 1.0 }
      }
    },
    {
      "type": "engine.Label",
      "name": "GameOverLabel",
      "script": "scripts/gameover.py",
      "properties": {
        "transform": { "position": {"x":150,"y":190}, "scale": {"x":1,"y":1}, "rotation": 0 },
        "text": "GAME OVER — press Enter",
        "size": 20.0,
        "color": { "r": 1.0, "g": 0.5, "b": 0.5, "a": 1.0 },
        "visible": false
      }
    }
  ]
}
```

**Nota:** Label tem `visible: Boolean`? Verificar no spec — se não, esta change precisa adicionar (ou GameOverLabel é controlado por `color.a = 0`). Verificar antes de finalizar.

## Risks / Trade-offs

- **[Risco] `Label.visible` pode não existir hoje.**
  → Mitigação: ao implementar, verificar; se não existe, fallback é controlar via `color.a` (0 = invisível, 1 = visível). Adicionar `visible` é uma mudança trivial de `:engine`, mas pertence a uma change separada. Snake aceita o workaround.

- **[Risco] `ColorRect.size` setado em runtime pelo script Python; bridge atual cobre?**
  → Pong faz `self.size = Vec2(...)` em `BoxCollider` (ver `ball.py`). Deveria funcionar para `ColorRect` (que herda padrão similar). Validar na implementação.

- **[Trade-off] Velocidade fixa `0.125s/tick` significa Snake longo fica difícil pelo tamanho, não pela velocidade.**
  → Aceito: dificuldade progressiva fora do escopo. Quem quiser ajustar edita `scene.json`.

- **[Risco] `removeChild` em loop dentro de `reset()` pode interagir mal com `pendingRemove`.**
  → Garantir que `reset()` roda em `_process` (não `_physics_process`); confiar no contrato existente do `:engine` ("safe mutation during scene traversal").

- **[Risco] Wraparound + grid-snap visual: se a cabeça atravessa a borda na MESMA frame em que come, comida pode reaparecer no segmento que acabou de wrapar.**
  → Como o cálculo de "células vazias" enumera segmentos atuais (pós-wrap), o spawn da nova comida sempre considera o estado final. Não há janela.

- **[Risco] Auto-colisão na PRIMEIRA célula movida (cobra de tamanho 1, head sobre body inicial).**
  → A cobra começa com 1 segmento (só a cabeça); checagem é "head está em algum segmento DO CORPO (ou seja, não head)". Implementar como "head in segments[1:]" (slice excluindo cabeça). Cobra de comprimento 1 é imune a auto-colisão até crescer.

- **[Trade-off] Aleatoriedade da Food via `random` Python.**
  → Determinístico se seed for fixado; Pong já usa `random` sem seed. Manter livre. Documentar que demos de regressão visual não devem fixar score esperado.

## Open Questions

- **`Label.visible` existe?** Verificar antes do `/opsx:apply`; se não, decidir entre (a) implementar como parte desta change (escopo mínimo) ou (b) workaround via `color.a`. Recomendação: (b), porque (a) é mudança de capability fora do tema do validador.

- **Random seeding:** fixar seed para reproducibility de regression testing manual? Recomendação: não, igual Pong; livre.

- **HUD vs Snake como pai do `MoveTimer`:** Timer mora como filho de Snake (mais próximo do owner) ou da raiz da cena (mais visível)? Recomendação: filho de Snake — quando Snake é removida, Timer auto-stop e morre junto.

- **Restart input:** Enter é a tecla. Espaço também? Recomendação: só Enter, mantém minimalismo.

- **Tamanho da cobra inicial:** 1 segmento (clássico) ou 3 (mais visualmente óbvio)? Recomendação: 3 segmentos, alinhados horizontalmente, direção inicial = direita. Aparência mais "snake" de cara.
