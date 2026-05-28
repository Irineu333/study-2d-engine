## Context

A engine ganhou debug infrastructure incrementalmente: `dx/FpsCounter` (skiko-runtime change inicial), `dx/Debug.colliderVisualization` (dx-tooling change), `dx/MomentumOverlay` (add-rigid-body-2d change), e finalmente `tree/DebugFlags` + `scene/DebugOverlayLayer` (ui-foundation change, que migrou as flags pra dentro da `SceneTree` e fez overlays virar nodes). Cada passo individual fez sentido, mas o conjunto resultante é um mosaico fragmentado:

- **3 flags fixas** (`showFps`, `showColliders`, `showMomentum`) e **3 keys fixas** em `GameConfig` (`toggleFpsKey`, `toggleCollidersKey`, `toggleMomentumOverlayKey`). Espaço de keybinds não escala — F4, F5, ... esgotam-se rápido e poluem `GameConfig`.
- **5 arquivos tocados pra adicionar widget**: `DebugFlags`, `GameConfig`, `SkikoHost`, `LwjglHost`, `DebugOverlayLayer.init`.
- **Singleton global** `MomentumOverlay` com `FloatArray(60) × 4` mutável compartilhado entre `SceneTree`s — bug latente em testes paralelos e re-starts.
- **12 linhas duplicadas** de polling de toggle keys + record de FPS em cada host. Cada backend novo (Vulkan, WebGPU, headless) herdará esse boilerplate.
- **`DebugOverlayLayer.init { addChild(...) }` fechado**, `open + @Serializable` na classe é dead weight — engine sempre instancia a base e nunca persiste.
- **`ColliderOverlay` empurra view transform manualmente** dentro de uma `CanvasLayer` (que existe pra escapar de transforms) — fricção contra o invariante #6.

A change `ui-foundation` (já arquivada) entregou as primitivas — `Panel`, `Button`, `CanvasLayer`, `tree.hitTestUI(input)`. Isso destrava substituir keybind-por-flag por uma HUD com checkboxes.

Stakeholder único: contribuidor (humano ou agente) estudando arquitetura. Restrição: nenhum jogo shipped deve quebrar nem precisar de migração manual além de `GameConfig` (e mesmo aí só quem customizava keys de toggle).

## Goals / Non-Goals

**Goals:**

- Adicionar widget de debug novo = **1 arquivo** (uma subclasse de `ScreenDebugWidget` ou `WorldDebugWidget`) + **1 chamada** `tree.debug.register(MyWidget())` em qualquer ponto após `tree.start()`. Zero toques em engine, zero toques em host, zero toques em `GameConfig`.
- Estado de debug **per-tree**: cada `SceneTree` tem seu próprio `DebugRegistry` com seus próprios widgets, sem singleton global. Dois `SceneTree`s no mesmo processo (caso futuro de level loading) não cruzam estado.
- Hosts deixam de saber que debug existe. `SkikoHost`/`LwjglHost`/futuro Vulkan/WebGPU/headless: loop é `input.beginTick()` → `loop.tick(dt)` mais wiring de janela; **zero menções a `tree.debug`, `FpsCounter`, `MomentumOverlay`**.
- Substituir 3 keybinds (F1/F2/F3) por 1 (F1 abre HUD). Espaço de keybinds liberado.
- Manter o invariante #4 ("`GameHost.render` não desenha"): toda saída visual passa por `SceneTree.render`, incluindo a HUD.
- Alinhar com invariante #6 (UI in-game em `CanvasLayer` ignora view transform; gizmos de mundo participam do world pass): `WorldDebugWidget` é `Node2D` no world pass, `ScreenDebugWidget` é `Node` na CanvasLayer.

**Non-Goals:**

- **Persistência da seleção de widgets** (qual está ligado entre runs). Sessão-only. Se aparecer demanda, change futura.
- **Console de log in-game** (widget que captura `LogSink` e mostra N últimas linhas). Conceitualmente cabe, mas adiciona escopo (texto multi-linha, scroll, filtro por tag) e fica pra change futura.
- **Inspector de scene tree** ou inspector de variáveis. Idem — design merece sua própria change.
- **Drag/resize da HUD**. Pinada num canto, sem botão de fechar (só toggle por keybind). Future work.
- **Scripts (Python/Lua) registrando widgets**. `DebugRegistry` é Kotlin-side. Se aparecer demanda, change pra binding.
- **Substituir o invariante #4**. Hosts ainda podem fazer wiring (janela, input setup), só não tocam em debug.
- **API pra widgets se comunicarem** (ex: gizmo de "selecione node X" lendo seleção de outro widget). YAGNI; cada widget é independente.
- **Calibração visual da HUD** (cores, fontes, padding). Usa defaults razoáveis; ajuste fica pro implementador.

## Decisions

### D1. World-space vs screen-space: dois containers, dois subtipos base

`DebugLayer` (rename de `DebugOverlayLayer`) é um `Node` filho de `tree.root` que hospeda **dois sub-containers**:

```
SceneTree.root
  ├─ <usuario>
  └─ DebugLayer (name="__debug")
       ├─ WorldDebugContainer (Node2D direto, world pass)
       │    ├─ ColliderWidget : WorldDebugWidget
       │    └─ <world widgets do jogo via tree.debug.register>
       └─ ScreenDebugCanvas (CanvasLayer, layer=Int.MAX_VALUE-1, screen pass)
            ├─ FpsWidget : ScreenDebugWidget
            ├─ MomentumWidget : ScreenDebugWidget
            ├─ DebugHud : ScreenDebugWidget (visualmente oculto até abrir)
            ├─ DebugToggleNode (Node interno, polla debugHudKey)
            └─ <screen widgets do jogo via tree.debug.register>
```

`WorldDebugWidget : Node2D` participa do world pass — ancestral `Camera2D` aplica view transform automaticamente. `ColliderWidget` agora desenha em mundo direto, sem `pushTransform(view)` manual.

`ScreenDebugWidget : Node` vive na `CanvasLayer` — desenha em pixels de tela.

`tree.debug.register(widget)` lê o tipo do widget e roteia: instância de `WorldDebugWidget` vai pro `WorldDebugContainer`; instância de `ScreenDebugWidget` vai pro `ScreenDebugCanvas`.

**Alternativa considerada**: um único `DebugWidget` com flag `space: WorldOrScreen`. Rejeitada — flag desliga checks estáticos. Subtipos forçam o autor a escolher cedo e o registry sabe onde plugar sem branch.

**Alternativa considerada**: empurrar todo mundo pra `CanvasLayer` (como hoje) e cada world widget faz seu `pushTransform(view)`. Rejeitada — perpetua a fricção com invariante #6 e ColliderWidget vai querer também `popTransform` etc. Container dedicado paga o custo zero pra escala futura.

### D2. Registry como tipo concreto em `SceneTree.debug`

`SceneTree.debug: DebugRegistry` (em vez de `DebugFlags`):

```kotlin
class DebugRegistry internal constructor(private val tree: SceneTree) {

    private val _widgets = mutableListOf<DebugWidget>()

    /** Built-in widgets, expostas via aliases conveniente para acesso direto. */
    val fps: FpsWidget
    val colliders: ColliderWidget
    val momentum: MomentumWidget
    val hud: DebugHud  // o painel; .enabled toggla visibilidade

    val widgets: List<DebugWidget> get() = _widgets

    fun register(widget: DebugWidget) { ... }   // routes by subtype, addChild ao container
    fun unregister(widget: DebugWidget) { ... }
    inline fun <reified T : DebugWidget> find(): T? = ...
}
```

`tree.debug.fps.enabled = true` é o caminho ergonômico pros 3 built-ins. `tree.debug.find<AxesWidget>()?.enabled = true` é o caminho genérico. `tree.debug.register(MyWidget())` é o caminho de extensão.

**Alternativa considerada**: registry global `DebugRegistry` (object). Rejeitada — bug latente quando duas `SceneTree` coexistem (caso de level loading). Per-tree é o caminho certo.

### D3. Hosts não tocam em debug — polling vai pra dentro da engine

Hoje SkikoHost e LwjglHost têm:

```kotlin
val fps = FpsCounter()
// per frame:
tree.debug.currentFps = fps.record(nanoTime)
if (input.wasKeyPressed(config.toggleFpsKey)) tree.debug.showFps = !tree.debug.showFps
// ... idem para Colliders, Momentum, com MomentumOverlay.reset() condicional
```

Tudo isso some. No lugar:

- `FpsWidget` é dona do `FpsCounter`. Em `process(dt)` chama `counter.record(System.nanoTime())`. Em `drawDebug` desenha o `text "fps ${counter.current.toInt()}"`. Zero envolvimento do host.
- `DebugToggleNode` (filho interno do `DebugLayer.ScreenDebugCanvas`) em `process(dt)` lê `tree.input` e detecta `config.debugHudKey`. Quando pressionado, faz `tree.debug.hud.enabled = !tree.debug.hud.enabled`.
- `MomentumWidget` em `physicsProcess(dt)` (hook de scene graph já existente) registra amostra no seu próprio `FloatArray`. Quando `enabled` flipa pra true, faz `reset()` no `enter` da habilitação (ver D5).

Hosts ficam:

```kotlin
// per frame (Skiko ou LWJGL):
input.beginTick()
loop.tick(dtNanos)
// fim
```

**Alternativa considerada**: deixar polling no host com um único keybind. Rejeitada — quebra o objetivo "host não conhece debug". Movendo pra engine, qualquer backend futuro herda o comportamento sem código novo.

### D4. HUD = `Panel` listando linhas, cada linha um `Button` toggling `enabled`

```
┌─ Debug ────────────────┐
│ [x] FPS                │
│ [ ] Colliders          │
│ [ ] Momentum           │
│ [ ] Axes               │
└────────────────────────┘
```

Renderiza no canto top-right (config: posição fixa por enquanto). Cada linha é um `Button` cujo texto começa com `"[x] "` ou `"[ ] "` dependendo de `widget.enabled`. Clicar inverte. A HUD ela mesma é um `ScreenDebugWidget` (com `enabled = false` default, ligando = visível e hit-testable). Quando `enabled = false`, nem desenha nem participa do hit-test (Node.visible = false ou equivalente — ver D9).

Botão de "fechar" não é necessário: keybind toggla. Drag/resize também não.

Ordem das linhas: ordem de registro no registry. Built-ins registrados primeiro na criação do `DebugLayer`; widgets do jogo aparecem abaixo.

**Alternativa considerada**: introduzir `Checkbox` como widget novo de UI. Rejeitada por escopo — `Button` com texto `"[x] "/"[ ] "` é visualmente claro e não exige nova primitiva. Quando `ui-controls-base` ou afim shipped trouxer `Checkbox`, migra-se.

**Alternativa considerada**: HUD persistente sem keybind (sempre na tela). Rejeitada — polui o frame mesmo em prod-mode.

### D5. `MomentumWidget` reset on enable

Quando o usuário liga `MomentumWidget` na HUD, o ring buffer deve começar zerado pra não mostrar samples velhos. Solução: `enabled` setter custom em `MomentumWidget`:

```kotlin
override var enabled: Boolean = false
    set(value) { field = value; if (value) ringBuffer.reset() }
```

Outros widgets não precisam — `FpsCounter` é amortizado em 1 segundo de sliding window, `ColliderWidget` é stateless.

### D6. `GameConfig` API change

```kotlin
// antes
data class GameConfig(
    val title: String = "Game",
    val width: Int = 800,
    val height: Int = 600,
    val toggleFpsKey: Key = Key.F1,
    val toggleCollidersKey: Key = Key.F2,
    val toggleMomentumOverlayKey: Key = Key.F3,
    val physicsHz: Int = 60,
)

// depois
data class GameConfig(
    val title: String = "Game",
    val width: Int = 800,
    val height: Int = 600,
    val debugHudKey: Key = Key.F1,
    val physicsHz: Int = 60,
)
```

Quebra dura. Jogos shipped não customizam esses keys; os exemplos em `:games:*/Main.kt` ficam intactos (todos usam `GameConfig(title = "...")` aceitando defaults). Se algum hipotético customizava `toggleCollidersKey = Key.C` por algum motivo, migra pra `debugHudKey = Key.C` ou aceita F1 default.

**Alternativa considerada**: deprecated shims (`val toggleFpsKey: Key get() = debugHudKey`). Rejeitada — sem usuário externo, sem motivo pra carry forward.

### D7. Reorg de pacotes: novo `engine.debug.*`

Hoje:
```
engine/dx/   { Debug, DebugColors, FpsCounter, MomentumOverlay, Log, LogConfig, LogLevel, LogSink, ConsoleLogSink }
engine/tree/ { DebugFlags }
engine/scene/{ DebugOverlayLayer }
```

Depois:
```
engine/debug/ { DebugWidget, ScreenDebugWidget, WorldDebugWidget, DebugRegistry,
                DebugLayer, ScreenDebugCanvas, WorldDebugContainer,
                FpsWidget, ColliderWidget, MomentumWidget,
                DebugHud, DebugToggleNode,
                FpsCounter, DebugColors }
engine/dx/    { Log, LogConfig, LogLevel, LogSink, ConsoleLogSink }
```

`Debug` object deletado; sua única responsabilidade (`log: LogConfig`) absorvida por `Log.config: LogConfig` (já que `Log.log(...)` precisa ler `effectiveLevel` antes de emitir, e isso é interno).

**Alternativa considerada**: mover `Log` também pra `engine.debug`. Rejeitada — logging é estrutural (vai existir mesmo se a infra de debug overlay desaparecesse), debug-overlay é "ferramenta visual". Capabilities diferentes (`dx-tooling` vs `debug-overlay`), pacotes diferentes.

### D8. World gizmos sem `pushTransform` manual

Hoje `ColliderOverlay.onDraw` faz:

```kotlin
val view = tree.currentCamera()?.computeViewTransform(tree.size)
if (view != null) { renderer.pushTransform(view.first, 0f, view.second); try { drawShapes(renderer) } finally { renderer.popTransform() } }
else drawShapes(renderer)
```

Depois, como `ColliderWidget : WorldDebugWidget : Node2D` e vive em `WorldDebugContainer : Node2D` direto sob root, ele participa do world pass de `SceneTree.render` que **já empilha** a view transform da `Camera2D` corrente. O `onDraw` fica:

```kotlin
override fun drawDebug(renderer: Renderer) {
    for ((shape, owner) in collectActiveCollisionShapes(tree)) {
        val bounds = shape.worldBounds() ?: continue
        renderer.drawRect(bounds, if (owner is Area2D) AREA_COLOR else BODY_COLOR, filled = false)
    }
}
```

A pendência é que `worldBounds()` retorna mundo em coords absolutas, e o widget está num Node2D filho de root com transform identidade. Confere — o widget desenha world-coord rects sob view transform da câmera. Igual ao que faz hoje, sem a fricção.

### D9. `enabled = false` ⇒ widget não desenha **e** não consome hit-test

`DebugHud` quando `enabled = false`:

- não desenha (o painel + buttons ficam ocultos)
- não consome hit-test (clicar onde a HUD estaria não consome o click)

Implementação: cada `ScreenDebugWidget`/`WorldDebugWidget` checa `enabled` no `drawDebug` (já no contrato). Pro hit-test, o `DebugHud` (que contém `Button`s) precisa esconder os Buttons. Solução: quando `DebugHud.enabled = false`, ele desativa via `addChild`/`removeChild` os buttons, ou via `Button.disabled = true` + `Button.visible = false`. Detalhe a resolver na implementação — pode usar o que `Panel`/`Button` shipped pelo `ui-foundation` oferece (provavelmente já tem `visible` que gateia hit-test, mas se não tiver, ajusta `Button`).

### D10. FPS default `enabled = false`

Consistente com Colliders e Momentum. Workflow: usuário aperta F1, abre HUD, marca `[x] FPS`. Quebra da affordance atual ("aperto F1 e vejo FPS na hora"), aceita em nome de uniformidade.

**Alternativa considerada**: FPS default `enabled = true`. Rejeitada — visual ruído por default não combina com "build limpo de produção". Quem quer pode setar `tree.debug.fps.enabled = true` no `Main.kt` em 1 linha.

## Risks / Trade-offs

- **Risco: F1 muda semântica.** Antes: F1 = "toggle FPS". Depois: F1 = "abre HUD; FPS é um dos checkboxes". Usuário cego à mudança aperta F1, vê HUD aparecer em vez de FPS direto. Mitigação: documentar no `CLAUDE.md` e no `README.md`. Aceitável — é exatamente o ganho que queremos.

- **Risco: Adicionar `WorldDebugContainer` debaixo de `root` muda a contagem de filhos do root.** Hoje root tem `+1` filho (`__debug` `DebugOverlayLayer`); depois passa a ter `+1` filho (`__debug` `DebugLayer`, que internamente tem 2 children: `WorldDebugContainer` e `ScreenDebugCanvas`). Externamente é o mesmo "+1 child com nome `__debug`". Quem usa `tree.root.findChild("__debug")` continua funcionando. **Mitigação**: invariante "engine auto-insere um único child com nome `__debug`" preservado.

- **Trade-off: Acesso runtime fica menos direto.** Antes: `tree.debug.showFps = true` (1 boolean). Depois: `tree.debug.fps.enabled = true` (1 reference + 1 boolean). Verboso por 1 token. Aceitável — desbloqueia `tree.debug.register(MyWidget())` que vale o overhead.

- **Trade-off: `enabled = false` ⇒ Button gone vs Button disabled.** `DebugHud` quando fechada precisa garantir que cliques onde ela estaria não consomem. Detalhe de implementação que pode esbarrar em comportamento de `Panel`/`Button` herdado de `ui-foundation`. Se `Button.visible = false` já gateia hit-test, gratis. Senão, ajusta `Button` ou inserir/remover do tree dinamicamente. Não é fundamental — tem 2-3 saídas viáveis.

- **Risco: testes paralelos que dependiam do singleton `MomentumOverlay` se quebram.** Improvável (singleton estava só ligado ao render-overlay; tests de física usam `MomentumDiagnostics.totalLinearMomentum` etc. que **não passam por** `MomentumOverlay`). Confirmar grep `MomentumOverlay.recordSample/renderOverlay/reset` ao implementar.

- **Risco: re-import em jogos.** Quem hipoteticamente importava `com.neoutils.engine.dx.MomentumOverlay` ou `com.neoutils.engine.scene.DebugOverlayLayer` quebra. Grep do código atual mostra zero usuários fora de `:engine` + `:engine-skiko` + `:engine-lwjgl`. Confirmar na fase de implementação.

## Migration Plan

Não é um sistema com usuários externos pagos; é um repositório de aprendizado. Tudo é flag day:

1. Implementa novo modelo lado-a-lado (não vai quebrar até remover o velho — porém **não vamos manter os dois**, então fase 1 é "introduzir DebugWidget abstração sem ainda usar nos default widgets").
2. Migra os 3 widgets default pra novas bases.
3. Conecta registry em `SceneTree.debug` substituindo `DebugFlags`.
4. Retira polling dos hosts.
5. Reorganiza pacotes.
6. Atualiza specs e docs.

Order matters porque `:engine-skiko` e `:engine-lwjgl` compilam contra `:engine`. Faz-se a mudança em `:engine` com aliases temporários ou comentários `// TODO` mínimos, faz-se os hosts no mesmo commit, depois remove os arquivos antigos. Ou em commit único — repositório pessoal, sem revisão paralela.

## Open Questions

Nenhuma de bloqueio. Detalhes a resolver durante implementação:

- **(impl)** `Button.visible = false` gateia hit-test? Se sim, perfeito. Se não, ajusta `Button` ou usa `addChild`/`removeChild` no `DebugHud.enabled` setter.
- **(impl)** Posição do HUD: top-right? top-left? offset configurável via `tree.debug.hud.position`? Default top-right (não conflita com `FpsLabel` que era top-left) é provável; deixa pro implementador.
- **(impl)** O `AxesWidget` do `:games:demos` é um Node2D que desenha dois `drawLine` na origem. Trivial. Cena 0 ou nova cena?
