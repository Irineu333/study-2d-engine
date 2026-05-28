## Context

Hoje `:engine` modela cena em world-space dominada por `Camera2D`: o `SceneTree.render` empilha uma view transform (projeção do `Camera2D.bounds` na surface), depois walka a árvore via `Renderer.pushTransform`/`popTransform` por `Node2D`. UI in-game não existe como conceito separado; jogos usam `Label` no world (sujeito à view transform da câmera) ou dependem do `GameHost` para overlays de debug. O `SkikoHost` e o `LwjglHost` desenham FPS/colliders/momentum **fora** do tree walk, com pipeline próprio (`renderer.drawText`, `renderer.drawRect` em identity transform após chamar `tree.render`).

Esse modelo tem três sintomas:

1. **HUDs vazam com a câmera.** Score do Pong sobrevive porque o `Camera2D.bounds` é estático e do tamanho da janela — coincidência, não desenho. Em Asteroids/Pool8 (planned), o Camera2D vai precisar zoomar ou pannar, e qualquer Label-no-world quebra.
2. **GameHost virou caixa-preta de UI.** O contrato "renderer + input + window loop" cresceu para incluir desenho de overlay e gerenciamento de teclas debug — backend Skiko e LWJGL implementam isso paralelamente, com risco de divergência silenciosa.
3. **Sem nodes UI, não há editor.** A planned `editor-visual` precisa de algo para inspecionar / arrastar / largar. Não dá pra montar inspector sobre `Label` no world.

Estamos no momento em que o terceiro backend de scripting (já temos Python e Lua) e os próximos jogos (Asteroids, Pool8, Billiards) batem na barreira. Esta change introduz o mínimo viável de UI in-tree: 3 nodes (`CanvasLayer`, `Panel`, `Button`), um pass extra no render, uma fase extra no tick, e a migração dos overlays do GameHost pra dentro da árvore.

Constraints:

- Invariante #2 (`:engine` sem deps de UI framework) — Panel/Button só podem usar `Renderer` SPI; nada de AWT/Compose/Skia direto.
- Invariante #4 (Renderer/Input/GameHost SPIs, LWJGL como sentinela) — toda mudança deve passar pelos dois backends ativos. Demo cena 7 valida em `run` e `runLwjgl`.
- Invariante #1 (herança, sem ECS) — `CanvasLayer`, `Panel`, `Button` são classes Node[2D] `open` herdáveis.
- Invariante #5 (`SceneTree` dona da árvore) — CanvasLayer é Node convencional, não um conceito paralelo.
- `scene.json` v2 inalterado — novos `type`s entram via `NodeRegistry`; properties via bag único.

## Goals / Non-Goals

**Goals:**

- Permitir construir menus e HUDs declarativamente em `scene.json`, com handlers em script Python ou Lua.
- Separar definitivamente "rendering de gameplay" (world-space, sujeito à `Camera2D`) de "rendering de UI" (screen-space, independente da câmera).
- Esvaziar `GameHost.render` — toda saída visual passa por `SceneTree.render`. O host só faz polling de input e seta flags em `tree.debug`.
- Manter o modelo de input polling existente, adicionando apenas uma fase de hit-test e uma flag `consumed`. Scripts gameplay continuam usando `wasMouseClicked()` sem precisar conhecer o conceito de UI.
- Garantir paridade Skiko ↔ LWJGL para todos os caminhos novos (CanvasLayer ordering, hit-test, overlay).
- Migrar UIs Label-no-world dos jogos shipped que se beneficiam (Pong, Snake, Tic-tac-toe, Hello World).
- Documentar o caminho para promoções futuras (Control base, anchors, layout, focus, theme, event model) como changes separadas no `ROADMAP.md`.

**Non-Goals:**

- `Control` base abstrata (com `mouse_filter`, `focus`, `size_flags`). Adiado: introduzir agora seria carregar conceito que só rende quando `ui-anchors`/`ui-layout`/`ui-focus` chegar. Documentado como `ui-controls-base` em Planned.
- Anchors/presets estilo Godot 4 (`anchor_left/top/right/bottom` + `offset_*`). Documentado como `ui-anchors` em Planned.
- Containers (`HBoxContainer`, `VBoxContainer`, `GridContainer`, `MarginContainer`). Documentado como `ui-layout` em Planned.
- Focus + keyboard navigation (`grab_focus`, Tab/Shift+Tab). Documentado como `ui-focus` em Planned.
- Theme/StyleBox/font system. Documentado como `ui-theme` em Planned.
- Modelo de eventos enfileirados (`_input`/`_gui_input` com `event.accept()`). Documentado como `ui-input-events` em Planned — só será proposto se o modelo polling+consumed virar dolorido.
- Widgets adicionais (`TextEdit`, `Slider`, `Checkbox`, `OptionButton`, `ScrollContainer`). Cada um seria change própria conforme demanda.
- Mudança no formato `scene.json` — continua v2.
- Mudança no protocolo de scripting (hooks Python `_ready`/`_process`/etc, hooks Lua) — apenas novos tipos exportados.

## Decisions

### Decisão 1: CanvasLayer como `Node` (não `Node2D`)

`CanvasLayer` herda diretamente de `Node`, não de `Node2D`. Motivo: `Node2D` carrega `transform` local que é composto pelo render walk via `Renderer.pushTransform`. CanvasLayer não tem transform composta no mesmo eixo da árvore — ele **interrompe** a transform chain, resetando para identity (= screen-space) antes de descer aos filhos. Se herdasse de `Node2D`, ficaria ambíguo se sua `transform` ainda compõe.

Filhos podem ser `Node2D` normais — eles têm sua própria `transform` local, que será composta normalmente **a partir da identity** que o CanvasLayer estabeleceu. Isso preserva ergonomia: `Panel` e `Button` herdam de `Node2D` e usam `position`/`rotation`/`scale` como qualquer outro Node2D.

**Alternativa considerada:** CanvasLayer com `transform: Transform2D` próprio que compõe sobre identity (Godot 3.x faz isso para offset/rotation do layer inteiro). Recusada por agora — adiciona conceito que MVP não precisa, pode entrar via field `layerTransform` em change futura se demanda surgir.

### Decisão 2: `layer: Int` com sort estável

CanvasLayer expõe `layer: Int = 0`. No render, `SceneTree` coleta todos os CanvasLayers da árvore em DFS, sort por `(layer ascendente, ordem de descoberta DFS ascendente)`, e desenha cada um em ordem (= último desenhado fica em cima).

**Por que sort, não z-buffer?** Renderer SPI atual é 2D-painter (sem depth buffer). Sort no nível da engine é o caminho consistente com Skiko (que pinta em ordem) e LWJGL/NanoVG (idem). Custo é trivial: N tipicamente ≤ 4 CanvasLayers por cena.

**Por que `Int` e não `Float`?** Z-order é discreto (camadas, não posições contínuas). Inteiros simplificam comparação e debug. Godot usa `int layer` por isso.

**Alternativa considerada:** ordem da árvore só, sem campo `layer`. Recusada porque modal/dialog que precisa pular pra frente dinamicamente forçaria reordenar nós na árvore — operação cara e que mexe com IDs/refs. `layer: Int` resolve com uma atribuição.

### Decisão 3: Render passes — world primeiro, depois CanvasLayers

`SceneTree.render` separa em dois passes:

```
Pass 1 (world):
  1. Coleta Camera2D current (se houver)
  2. renderer.pushTransform(viewTranslation, 0, viewScale)
  3. Walk DFS pula CanvasLayers (e seus subárvores inteiras)
  4. renderer.popTransform

Pass 2 (UI):
  1. Coleta todos CanvasLayers, sort por (layer, dfsOrder)
  2. Para cada CanvasLayer:
     a. renderer está em identity (no push de view) → desenha em screen-space
     b. Walk DFS na subárvore do CanvasLayer (filhos Node2D recebem push/pop normal)
```

A "stack final" no momento de `onDraw` de um Panel filho de CanvasLayer é: `identity ∘ panel.local.transform` = `panel.local.transform`. Coordenadas screen-space puras.

**Trade-off:** dois passes vs um pass único com flag. O pass único com flag mistura world e UI no walk, o que complica debug e overlay collider (que só deve mostrar mundo). Dois passes mantêm cada um trivialmente compreensível.

### Decisão 4: Hit-test pull-based, fase entre `beginTick` e `process`

```
tick:
  input.beginTick()
  tree.hitTestUI(input)          ← novo
  tree.process(dt)
  tree.physics_process(dt)
  tree.render(renderer)
```

`tree.hitTestUI(input)`:

1. Se `input.wasMouseClickedRaw(Left)`:
   1. Coleta CanvasLayers, sort decrescente por (layer, dfsOrder)
   2. Para cada CanvasLayer (top → bottom):
      1. Walk DFS reverso na subárvore
      2. Para cada `Button` habilitado: se `rect(button).contains(pointerPosition)` → marca `input.mouseClickConsumed = true`, retorna
3. Hover/press visual é resolvido pelo próprio `Button._process` lendo `input.pointerPosition` e `Rect.contains`.
4. Button emite `pressed` em `_process` quando: `mouse-down ocorreu sobre mim` E `mouse-up ocorreu sobre mim`. Estado `pressedFlag` interno por button.

**API novo no `Input`:**

```kotlin
interface Input {
    fun wasMouseClicked(button: MouseButton): Boolean     // ← retorna false se consumed
    fun wasMouseClickedRaw(button: MouseButton): Boolean  // ← evento bruto
    var mouseClickConsumed: Boolean                       // ← setado por hitTestUI
    // ... resto inalterado
}
```

Scripts gameplay que querem ver clicks **na UI também** (raro) usam `wasMouseClickedRaw`. Maioria absoluta usa `wasMouseClicked` e ganha o behavior de "UI engole o click" de graça.

**Alternativa considerada:** modelo Godot `_input`/`_gui_input` com event objects + `event.accept()`. Recusada por dobrar superfície da Input SPI sem benefício imediato; documentada como `ui-input-events` em Planned.

### Decisão 5: `DebugOverlayLayer` auto-inserido pela engine

Quando `SceneTree` é construído (ou via `SceneTree.attach(root)` se for o caminho), a engine insere automaticamente um node `DebugOverlayLayer` (CanvasLayer com `layer = Int.MAX_VALUE - 1`) como **último filho da raiz**. Esse node:

- Mantém visibilidade controlada por `tree.debug.showFps`, `tree.debug.showColliders`, `tree.debug.showMomentum`.
- Lê essas flags em `_process` e mostra/esconde seus filhos (`FpsLabel`, `ColliderOverlay`, `MomentumOverlay`).
- `ColliderOverlay` walka a árvore game atrás de `CollisionObject2D` e desenha AABBs em screen-space (aplicando view transform inversa via `Camera2D.worldToScreen`).
- `MomentumOverlay` consulta `PhysicsSystem` por `Σp`, `ΣL`, `ΣKE`.

**GameHost simplificado:**

```kotlin
// Skiko/LWJGL host:
fun frame() {
    input.beginTick()

    if (input.wasKeyPressed(config.toggleFpsKey)) tree.debug.showFps = !tree.debug.showFps
    if (input.wasKeyPressed(config.toggleCollidersKey)) tree.debug.showColliders = !tree.debug.showColliders
    if (input.wasKeyPressed(config.toggleMomentumOverlayKey)) tree.debug.showMomentum = !tree.debug.showMomentum

    tree.hitTestUI(input)
    tree.process(dt)
    tree.physicsProcess(dt)
    tree.render(renderer)
    // ← sem mais nada. Sem desenho de overlay aqui.
}
```

**Por que auto-inserir, em vez de cada jogo declarar?** Overlay é meta-debug, transversal a todos os jogos. Forçar cada `scene.json` a declarar `DebugOverlayLayer` é boilerplate sem ganho. Como contrapartida, `:engine` carrega o código de FPS counter / collider drawer — aceitável porque já era a engine quem desenhava esses overlays (no host) antes.

**Alternativa considerada:** flag opcional `GameConfig.debugOverlay = true` que pula a auto-inserção. Recusada por agora — ninguém precisa desligar isso (e se precisar, basta setar todas as 3 flags pra `false`). Pode virar config se cenário aparecer.

### Decisão 6: Button via geometria, não via Area2D

`Button.hitTest(pointer): Boolean` faz `Rect(position, size).contains(pointer)` direto em screen-space. **Não** usa `Area2D` + `CollisionShape2D` + `PhysicsSystem`. Razões:

- `PhysicsSystem` corre em physics tick (60Hz por default), não em frame tick — descasaria com input/render.
- `Area2D` opera em world-space, e UI vive em screen-space — converter cada frame é overhead inútil.
- Hit-test de UI é trivialmente rect-vs-point; engenharia paralela de `PhysicsSystem` é overkill.

Trade-off: Button só suporta rect rectangular axis-aligned (sem rotação, sem shape circular). Aceitável para MVP. Quando precisar de hit-test não-trivial (botões circulares, botões rotacionados), introduzir `Button.shape: Shape2D` opcional vira upgrade compatível.

### Decisão 7: `tree.debug` como singleton mutável

```kotlin
class SceneTree(...) {
    val debug: DebugFlags = DebugFlags()
}

class DebugFlags {
    var showFps: Boolean = false
    var showColliders: Boolean = false
    var showMomentum: Boolean = false
}
```

Não é Node, não é serializável. Vive enquanto o `SceneTree` vive. Host seta via polling; `DebugOverlayLayer` lê em `_process`.

**Alternativa considerada:** flags como properties do próprio `DebugOverlayLayer`. Recusada porque host precisaria acessar `tree.root.findChild("__debug").showFps`, frágil. Centralizar em `tree.debug` é limpo.

### Decisão 8: Naming e package layout

- Package: `com.neoutils.engine.scene` (junto com `Node`, `Node2D`, `Label`, etc.). UI é parte do scene graph, não um subsistema separado.
- Arquivos: `CanvasLayer.kt`, `Panel.kt`, `Button.kt`, `DebugOverlayLayer.kt`, `DebugFlags.kt`.
- Stubs Python: `engine-bundle-python/src/main/resources/stubs/engine/__init__.pyi` ganha as novas classes.
- Stubs Lua: `engine-bundle-lua/src/main/resources/stubs/engine/*.lua` ganham `nengine.CanvasLayer`, `nengine.Panel`, `nengine.Button`.

### Decisão 9: Signal `pressed` segue o contrato existente

`Button.pressed` é `Signal<Unit>` (Kotlin) / `Signal` (Python/Lua), built-in instanciado em `attach`. Conecta via:

- Kotlin: `button.pressed.connect { ... }`
- Python: `self.pressed.connect(handler)` no script anexado, ou `script_of(button).pressed.connect(handler)` de fora.
- Lua: `self.pressed:connect(handler)` no script anexado.

Zero conceito novo no scripting contract.

## Risks / Trade-offs

- **[Risk] Divergência Skiko ↔ LWJGL na ordem de pintura de CanvasLayers** → cena 7 da Demos exercita explicitamente 2 CanvasLayers com `layer` diferente. Validação manual + comparação visual nos dois entrypoints.
- **[Risk] Auto-inserção de DebugOverlayLayer quebra cenas que esperam root puro** → tasks de migração revisam cada jogo. Testes do `engine-core` checam que `tree.root.children` antes da auto-inserção é igual ao que `SceneLoader` devolveu (auto-insert é additive, não substitui).
- **[Risk] `wasMouseClicked` mudar de comportamento (passar a retornar `false` quando consumido) quebra scripts gameplay existentes** → migração explícita: jogos shipped que dependem de click revisitam input handling. Maioria não tem UI sobreposta, então `mouseClickConsumed` continua `false` na prática. Backstop: `wasMouseClickedRaw` disponível.
- **[Risk] Hit-test em screen-space falha quando `Camera2D` aplica scale não-uniforme à surface (`AspectMode.FIT` com letterbox)** → coordenadas de Button são screen pixels brutos, sem passar por `worldToScreen`. Letterbox só afeta world-pass. Validar na cena 7 ao redimensionar.
- **[Risk] Pull-based hover em todo `Button._process` desperdiça ciclos quando há muitos botões** → MVP aceita; ordem de magnitude `≤ 30` botões em qualquer cena imaginável. Otimização (push-based hover do `tree.hitTestUI`) é trivial depois.
- **[Trade-off] `Control` adiado** → menus e HUDs MVP funcionam sem hierarquia de Control comum (Panel/Button não compartilham nada além de `Node2D`). Quando `ui-anchors` chegar, a refatoração será concentrada num único PR.
- **[Trade-off] Sem layout containers** → posicionamento manual via `position: Vec2`. Aceitável para MVP de 3 botões; doloroso para forms ricos, mas esses não estão no roadmap próximo.

## Migration Plan

1. Implementar `CanvasLayer` + render passes + testes no `:engine`.
2. Implementar `Panel`, `Button`, `Input.mouseClickConsumed`, `tree.hitTestUI` + testes.
3. Introduzir `DebugFlags`, `DebugOverlayLayer`, auto-inserção; remover desenho de overlay de `SkikoHost` e `LwjglHost`.
4. Adicionar cena 7 em `:games:demos`; validar em `run` e `runLwjgl`.
5. Migrar bundles dos jogos shipped (hello-world, pong, snake, tictactoe).
6. Atualizar stubs Python e Lua.
7. Atualizar `CLAUDE.md` (seção de rendering + invariante GameHost) e `ROADMAP.md` (Active + Planned).

Rollback: cada passo é atômico no nível de PR/commit; reverter a remoção do overlay do GameHost (passo 3) é trivial se DebugOverlayLayer não estiver pronto.

## Open Questions

- **Q1**: `Button.pressed` emite no mouse-up ou no mouse-down? Decisão preliminar: **mouse-up dentro do botão se mouse-down também ocorreu nele** (= Godot behavior; permite cancelar arrastando o mouse pra fora). Confirmar via cena 7.
- **Q2**: Onde mora `DebugOverlayLayer`? Engine puro ou módulo separado? Decisão preliminar: **junto com `:engine`** (no mesmo package `scene`), porque o overlay de momento depende do `PhysicsSystem` interno e separar geraria circular deps. Reavaliar se ficar muito pesado.
- **Q3**: `Panel` precisa de border desde MVP, ou só fill? Decisão preliminar: **`border: { color, width }?` opcional desde MVP** (custa 1 desenho extra de `drawRect` filled=false), porque sem border um Panel é só ColorRect renomeado — diferenciação semântica é fraca. Com border faz sentido como widget de "frame".
- **Q4**: `Tic-tac-toe` usa Lua. O signal `pressed` precisa de ergonomia equivalente em Lua. Confirmar que cena 7 Demos use Python (já existem) E que pelo menos um teste valide o caminho Lua. Decisão preliminar: **demo cena 7 só Python para reduzir escopo**; validação Lua via tictactoe usando Button no migration.
