## Context

`Renderer` já oferece o set completo de primitivas (`drawLine`, `drawRect`,
`drawCircle`, `drawPolygon`, `drawText`) e uma transform stack LIFO.
`SceneTree.render` roda dois passes: world (DFS pulando `CanvasLayer`s, com
a view transform da `Camera2D` empilhada) e UI (cada `CanvasLayer` em
`layer asc`, partindo de identidade). O `DebugLayer` já hospeda um
`WorldDebugContainer` (`Node2D` no world pass) e um `ScreenDebugCanvas`
(`CanvasLayer` no UI pass) — exatamente os dois ganchos que um draw
immediate-mode precisa para os espaços world e screen.

Hoje, desenhar um gizmo exige subclassar `WorldDebugWidget`/`ScreenDebugWidget`
e registrar. A `debug-physics-gizmos` (próxima change) precisaria emitir
dezenas de primitivas por frame (contatos, normais, sweeps) — inviável via
widget dedicado por tipo de gizmo. Esta change entrega a primitiva que ela
e o game/script code consomem.

Scripts recebem `self` como objeto Kotlin via GraalPy/LuaJ HostAccess, com
`Vec2`/`Color` ligados como factories nos hosts. `node.tree` existe. Logo
`self.tree.debug.draw...` é alcançável se o HostAccess permitir navegar as
properties.

## Goals / Non-Goals

**Goals:**

- API immediate-mode `tree.debug.draw` com espaços `world` e `screen`,
  cada um com `line/rect/circle/polygon/text`.
- Acúmulo por frame + flush no render + limpeza automática no fim do
  `render`, sem cleanup manual e sem acúmulo entre frames.
- No-op de custo zero quando desabilitada (não acumula).
- Toggle único "Debug Draw" no HUD.
- Emissão a partir de Python e Lua.

**Non-Goals:**

- Duração/TTL multi-frame (gizmo que persiste X segundos). Single-frame só.
- Depth/z-sorting entre gizmos e a cena — gizmos pintam por cima do seu
  pass, na ordem de emissão.
- Pool/arena de comandos para zero-GC. MVP aceita alocação por comando
  (ver Risks); otimização é change futura se o profiler apontar.
- Reidratar via scene file: a facade é runtime puro, nunca `@Serializable`.

## Decisions

### D1 — `DebugDraw` facade com dois `DebugCanvas` (world/screen)

`tree.debug.draw: DebugDraw` expõe `val world: DebugCanvas` e
`val screen: DebugCanvas`. `DebugCanvas` é a superfície com os verbos:

```kotlin
class DebugCanvas {
    fun line(from: Vec2, to: Vec2, color: Color, thickness: Float = 1f)
    fun rect(rect: Rect, color: Color, filled: Boolean = false)
    fun circle(center: Vec2, radius: Float, color: Color, filled: Boolean = false, thickness: Float = 1f)
    fun polygon(points: List<Vec2>, color: Color)
    fun text(position: Vec2, text: String, color: Color, size: Float = 14f)
}
```

Cada verbo enfileira um `DrawCommand` (sealed) num `MutableList` do canvas.
Simetria explícita world/screen espelha o par `WorldDebugWidget`/
`ScreenDebugWidget` já existente — o autor escolhe o espaço pelo namespace.

**Alternativa rejeitada — espaço por parâmetro** (`draw.line(..., space = World)`):
some a simetria com o resto do debug e polui cada assinatura.

**Alternativa rejeitada — `draw` é world e `draw.screen` é o caso raro:**
assimétrico, confunde leitura ("por que line é world mas text pode ser screen?").

### D2 — Flush nos passes existentes, limpeza no tail do `render`

Dois nodes de backing internos, auto-inseridos pelo `DebugLayer`:

- `ImmediateWorldDrawNode : Node2D` no `WorldDebugContainer` — em `onDraw`
  itera `draw.world` e emite cada comando no `Renderer` (já sob a view
  transform da `Camera2D`, sem `pushTransform` próprio).
- `ImmediateScreenDrawNode : Node` no `ScreenDebugCanvas` — em `onDraw`
  itera `draw.screen` (pixels de tela).

`SceneTree.render`, **após** os dois passes, chama `debug.draw.clearFrame()`
que esvazia os dois buffers. Assim comandos emitidos em
`_process`/`_physics_process` (antes do render) aparecem nesse frame e somem.

**Por que limpar no `render` e não no `GameLoop.tick`:** mantém tudo dentro
de `:engine`/`SceneTree` (entry único que o host chama), sem tocar
`GameHost` nem `GameLoop` — que `debug-time-controls`/`debug-profiler`
ainda vão mexer. Acoplamento mínimo.

**Por que dois nodes e não um:** um `Node` não pode participar dos dois
passes; o split world/screen é estrutural no `SceneTree.render`.

### D3 — Toggle único via um `DebugWidget` proxy "Debug Draw"

O HUD enumera `tree.debug.widgets` (cada um vira uma row). Para ter **uma**
row controlando os **dois** buffers, registra-se um único `DebugWidget`
proxy — `DebugDrawToggle` (um `ScreenDebugWidget` que não desenha nada, só
carrega `title = "Debug Draw"` e delega `enabled` para a `DebugDraw`
facade). Os dois nodes de backing leem `draw.enabled`.

`draw.enabled` default `false`. Quando `false`, **os verbos são no-ops**:
nada é enfileirado (early-return no início de cada verbo), custo
desprezível mesmo se o game chamar `draw.line` todo frame. Quando `true`,
enfileira e desenha.

**Trade-off documentado:** game code que chama `draw.line` vê nada até abrir
o HUD e ligar "Debug Draw" — consistente com a filosofia "produção limpa
por default" de toda a `debug-overlay`. Para um gizmo sempre-on durante
desenvolvimento, o game seta `tree.debug.draw.enabled = true` no setup.

**Alternativa rejeitada — duas rows (World/Screen):** clutter no HUD para um
ganho marginal; o caso "só world" raramente precisa silenciar o screen.

### D4 — Exposição a scripts via HostAccess existente

`self.tree.debug.draw.world.line(Vec2(...), Vec2(...), Color(...))` deve
funcionar reusando os `Vec2`/`Color` factories já ligados. Se a política
de HostAccess dos hosts não expõe a navegação de properties Kotlin até
`draw`, a change adiciona binding explícito mínimo (ex.: expor `DebugCanvas`
como tipo acessível). Stubs `.pyi` (GraalPy) e LuaCATS (LuaJ) ganham as
assinaturas dos verbos. Sandbox de `require` (Lua) e fail-fast em erro de
hook permanecem como na spec de scripting.

### D5 — `DrawCommand` sealed, alocado por chamada (MVP)

`sealed interface DrawCommand` com uma variante por verbo
(`Line`/`Rect`/`Circle`/`Polygon`/`Text`), cada uma data class imutável.
Buffers são `MutableList<DrawCommand>` por canvas, limpos por frame.

**Por que aceitar alocação por comando no MVP:** clareza didática > zero-GC
prematuro (invariante de propósito da engine). Um pool/arena entra só se o
`debug-profiler` mostrar pressão real de GC com muitos gizmos.

## Risks / Trade-offs

- **[Pressão de GC com muitos gizmos por frame]** → MVP aloca um
  `DrawCommand` por chamada; `debug-physics-gizmos` pode emitir dezenas por
  frame. Mitigação: aceitável para debug (não é caminho de produção);
  pool/arena adiado e condicionado a evidência do profiler.
- **[Comando emitido tarde demais no frame não aparece]** → Emitir em
  `onDraw` de um node que pinta depois do container, ou após o tail-clear,
  perde o frame. Mitigação: contrato "emita em process/physics", documentado
  no spec; é o fluxo natural de gizmos.
- **[HostAccess pode não navegar até `draw`]** → Se properties Kotlin não
  forem expostas por padrão, scripts não alcançam a facade. Mitigação: D4
  prevê binding explícito como fallback; uma tarefa valida via script real
  em Python e Lua.
- **[Confusão "chamei draw e não vi nada"]** → Default `enabled = false`.
  Mitigação: documentado; atalho `tree.debug.draw.enabled = true` no setup
  para devs que querem gizmos sempre on.
- **[Reentrância/threading]** → Diferente do log, os verbos são chamados na
  thread do game loop (process/physics/render), não de threads arbitrárias.
  Buffers não precisam de sincronização; documentar a expectativa single-thread.
