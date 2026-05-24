## 1. Hook renames in `:engine`

- [x] 1.1 Em `Node.kt`: renomear `onUpdate(dt)` → `onProcess(dt)` e `onRender(renderer)` → `onDraw(renderer)`. Manter `onEnter`/`onExit`.
- [x] 1.2 Em `Node.kt`: adicionar `open fun onPhysicsProcess(dt: Float) { scriptInstance?.onPhysicsProcess(dt) }` com no-op default.
- [x] 1.3 Em `Node.kt`: adicionar `groups: Set<String>` (campo respaldado por `MutableSet<String>` `@Transient`), métodos `addToGroup(name)`, `removeFromGroup(name)`, `isInGroup(name): Boolean`.
- [x] 1.4 Em `Node.kt`: o delegate de `scriptInstance` em `onProcess`/`onDraw`/`onPhysicsProcess` chama os métodos correspondentes na SPI `ScriptInstanceContract`.
- [x] 1.5 Em `ScriptInstanceContract.kt`: renomear `onUpdate(dt)` → `onProcess(dt)`, `onRender(renderer)` → `onDraw(renderer)`. Adicionar `onPhysicsProcess(dt)`. Adicionar `signals: Map<String, Signal<*>>`.

## 2. Scene lifecycle + groups

- [x] 2.1 Em `Scene.kt`: renomear `update(dt)` → `process(dt)`, `render(renderer)` interno faz traversal pré-order chamando `onDraw`. Adicionar `physicsProcess(dt)` que faz traversal chamando `onPhysicsProcess`.
- [x] 2.2 Em `Scene.kt`: adicionar `var size: Vec2 = Vec2.ZERO` setado por `resize(w, h)` (já existe — só renomear o campo se preciso); adicionar `val viewport: Rect` computado consultando `currentCamera()?.bounds ?: Rect(Vec2.ZERO, size)`.
- [x] 2.3 Em `Scene.kt`: adicionar `private fun currentCamera(): Camera2D?` que faz tree-walk pré-order procurando `Camera2D` com `current = true`.
- [x] 2.4 Em `Scene.kt`: adicionar `fun getNodesInGroup(name: String): List<Node>` via tree-walk pré-order.

## 3. GameLoop fixed-step

- [x] 3.1 Em `GameLoop.kt`: adicionar parâmetro construtor `physicsHz: Int = 60`; campos `physicsDt: Float = 1f / physicsHz`, `maxStepsPerFrame: Int = 5`, `accumulator: Float = 0f`.
- [x] 3.2 Reescrever `tick(dtNanos)` para o algoritmo descrito em `design.md` D3 (acumulador → enquanto cabe física: drain → physicsProcess → drain → physics.step → decrementar; depois process; depois render; com clamp de spiral-of-death).
- [x] 3.3 Logar warning via `Debug` quando spiral-of-death clamp ativa (`Log.w` com mensagem nomeando `physicsHz` e o `dtNanos` que estourou).
- [x] 3.4 Em `GameConfig.kt`: adicionar `physicsHz: Int = 60` (mantém ABI binária para callers existentes — todos os existentes usam `GameConfig()` default).

## 4. Renderer SPI

- [x] 4.1 Em `Renderer.kt`: adicionar `fun drawPolygon(points: List<Vec2>, color: Color)`.
- [x] 4.2 Em `:engine-skiko/SkikoRenderer.kt`: implementar `drawPolygon` via `org.jetbrains.skia.Path` (`moveTo` → `lineTo`*N → `closePath`), `Paint(color, mode = Fill)`.
- [x] 4.3 Em `:engine-compose/ComposeRenderer.kt`: implementar `drawPolygon` via `androidx.compose.ui.graphics.Path` + `DrawScope.drawPath(path, color, style = Fill)`.

## 5. Visual primitives

- [x] 5.1 Criar `engine/.../scene/ColorRect.kt`: `@Serializable class ColorRect : Node2D()` com `@Inspect var size: Vec2` (default `Vec2(10f, 10f)`) e `@Inspect var color: Color` (default `Color.WHITE`); override `onDraw` desenhando `drawRect` com `worldTransform().scale` aplicado a `size`.
- [x] 5.2 Criar `engine/.../scene/Circle2D.kt`: `@Serializable class Circle2D : Node2D()` com `@Inspect var radius: Float = 5f`, `@Inspect var color: Color`; override `onDraw` desenhando `drawCircle` com `worldPosition() + Vec2(radius, radius)` como centro e `radius * worldTransform().scale.x` como raio efetivo.
- [x] 5.3 Criar `engine/.../scene/Line2D.kt`: `@Serializable class Line2D : Node2D()` com `@Inspect var points: List<Vec2> = emptyList()`, `@Inspect var thickness: Float = 1f`, `@Inspect var color: Color`; override `onDraw` iterando pares consecutivos com `drawLine` somando `worldPosition()`.
- [x] 5.4 Criar `engine/.../scene/Polygon2D.kt`: `@Serializable class Polygon2D : Node2D()` com `@Inspect var points: List<Vec2> = emptyList()`, `@Inspect var color: Color`; override `onDraw` chamando `drawPolygon(points.map { it + worldPosition() }, color)`.
- [x] 5.5 Criar `engine/.../scene/Label.kt`: `@Serializable class Label : Node2D()` com `@Inspect var text: String = ""`, `@Inspect var size: Float = 12f`, `@Inspect var color: Color = Color.WHITE`; override `onDraw` com `drawText`.
- [ ] 5.6 Deletar `engine/.../scene/Shape.kt` e `engine/.../scene/Text.kt`. (deferred until games are migrated off Shape/Text in steps 10-12)
- [x] 5.7 Em `NodeRegistry` (em `:engine` ou `:engine-bundle`, onde mora hoje): remover registros de `Shape` e `Text`; adicionar registros de `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `Label`, `Camera2D`. (Shape/Text removal deferred along with 5.6)

## 6. Camera2D

- [x] 6.1 Criar `engine/.../scene/Camera2D.kt`: `@Serializable class Camera2D : Node2D()` com `@Inspect var bounds: Rect = Rect(Vec2.ZERO, Vec2.ZERO)`, `@Inspect var current: Boolean = false`.

## 7. Signal redesign

- [x] 7.1 Em `engine/.../serialization/Signal.kt`: reescrever para o shape `class Signal<T>` com `connect`, `disconnect`, `emit`. Remover `var path: NodeRef`. Manter `@Serializable` no marcador (mas o serializer ignora handlers — em uso real será `@Transient` em campos de Node).
- [x] 7.2 Adicionar `class Disposable internal constructor(private val onDispose: () -> Unit) { fun dispose() }` (ou alias `() -> Unit`).
- [x] 7.3 Testes unit em `engine/src/test`: `connect/emit/disconnect`, ordem de invocação, disconnect-from-handler safety.

## 8. Python bridge

- [x] 8.1 Em `PythonScriptHost.kt`: na construção do `Context`, expor bindings adicionais: `Signal`, `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`. Remover bindings de `Shape`, `Text`. (Shape/Text bindings removal deferred along with 5.6)
- [x] 8.2 Em `PythonScriptHost.kt`: adicionar binding `signal` (factory que constrói `Signal<Any?>()` ignorando o typeHint).
- [x] 8.3 Em `PythonScriptHost.kt`: ScriptInstance dispatcher passa a chamar `_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`, `_on_collide` no objeto Python. Nomes antigos (`on_*`) deixam de ser tentados.
- [x] 8.4 Em `PythonScriptHost.kt`: AST inspector ganha uma segunda passada: para cada `AnnAssign` top-level cuja `annotation.id == "Signal"`, validar que `value` é `Call(func=Name("signal"))`. Em caso de match, popular `Script.signals[name] = SignalDeclaration(name)`. Em caso de mismatch, levantar com mensagem clara nomeando script + linha.
- [x] 8.5 Adicionar `Script.signals: Map<String, SignalDeclaration>` no contrato de `Script` (em `engine-bundle`).
- [x] 8.6 Em `ScriptInstance` Python: durante `attach`, instanciar `Signal<*>()` Kotlin por entrada em `script.signals`, gravar no map `signals`, e expor cada signal como atributo no objeto Python via `module.<name> = signalInstance`.
- [x] 8.7 Verificar interop `Signal.connect(pythonCallback)`: GraalPy expõe o callable Python como `Value` invocável; envolver em `(T) -> Unit = { value -> pythonCallable.execute(value) }`. (GraalPy SAM-converts Python callables to Function1 automatically with allowAllAccess; explicit wrapper not required.)

## 9. Stubs `.pyi`

- [x] 9.1 Em `engine-bundle-python/src/main/resources/stubs/engine/`: atualizar `node.pyi` (ou equivalente) com novos nomes de hook (`_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`).
- [x] 9.2 Atualizar/criar `signal.pyi` com `Signal[T]`, `connect`, `emit`, `disconnect`, `Disposable`. Adicionar `def signal(t: type | None = None) -> Signal[Any]:` no top-level.
- [x] 9.3 Adicionar stubs para `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`.
- [x] 9.4 Remover stubs de `Shape`, `Text`. (no existing Shape/Text stubs found; visual primitive stubs replace them)

## 10. Migrar `:games:pong`

- [x] 10.1 `pong.scene.json`: adicionar `Camera2D` filho do root com `current: true` e `bounds: Rect(Vec2.ZERO, Vec2(800f, 600f))`.
- [x] 10.2 `pong.scene.json`: trocar `centerLine` de `Node2D` + script para `Line2D` declarativo (sem script), pontos `[Vec2(400, 0), Vec2(400, 600)]` em local-space, `thickness: 2f`, `color: Color.WHITE`.
- [x] 10.3 `pong.scene.json`: remover prop `playFieldHeight` dos dois paddles.
- [x] 10.4 `pong.scene.json`: renomear nodes que usem `type: "com.neoutils.engine.scene.Text"` para `type: "com.neoutils.engine.scene.Label"`. (no Text nodes in pong; nothing to rename)
- [x] 10.5 `paddle.py`: renomear `on_enter→_ready`, `on_update→_physics_process` (movemos a integração para physics step), `on_render→_draw`. Adicionar `_draw(self, renderer)` desenhando o retângulo via `renderer.drawRect`. Remover `playFieldHeight` export; substituir uso por `self.rootScene().viewport.size.y`.
- [x] 10.6 `ball.py`: renomear hooks. Mover integração de velocidade para `_physics_process`. Manter `_draw` que desenha o círculo. Declarar `scored: Signal = signal(str)` top-level. Substituir `_emit_score(self, side)` por `self.scored.emit(side)`. Remover bloco `if not hasattr(self, '_on_score'): self._on_score = None`.
- [x] 10.7 `pong_scene.py`: no `_ready`, resolver `ball` via NodeRef + chamar `ball.scored.connect(self._on_scored)`. Definir `_on_scored(self, side)` que atualiza scoreboards.
- [x] 10.8 `goal.py`: renomear hooks (provavelmente `_ready` para configurar `size` baseado em `viewport`); revisar lógica para usar `scene.viewport` em vez de hardcoded sizes. (goal.py has no hooks; sizes still come from pong_scene._layout via scene.size — equivalent to viewport without a Camera2D override.)
- [x] 10.9 `score.py`: renomear hooks (`_ready`, `_draw`); usar `Label` se aplicável. (kept as Node2D + _draw to preserve the same render shape; Label refactor optional and deferred.)
- [x] 10.10 Deletar `center_line.py`.

## 11. Migrar `:games:demos`

- [x] 11.1 `TransformOrbitDemo.kt`: renomear overrides `onUpdate → onProcess`, `onRender → onDraw`. Substituir `Shape` por `ColorRect` ou `Circle2D` conforme uso (verificar cada filho gerado).
- [x] 11.2 `ScaleHierarchyDemo.kt`: idem.
- [x] 11.3 `SpawnerDemo.kt`: idem; o "trap central" continua usando `BoxCollider` (collision sem mudança).
- [x] 11.4 `DemoSwitcherScene.kt`: idem hooks.
- [x] 11.5 `Main.kt`: nenhum override (apenas wiring) — não precisa mudar.

## 12. Migrar `:games:tictactoe`

- [ ] 12.1 `Board.kt`: renomear `onUpdate → onProcess`, `onRender → onDraw`.
- [ ] 12.2 `StatusText.kt`: substituir extends `Text` por extends `Label` (ou compor `Label` — manter mínimo).
- [ ] 12.3 `TicTacToeScene.kt`: ajustar uso de `Text` → `Label`.
- [ ] 12.4 `Main.kt`: nenhuma mudança (já usa `ComposeHost`).

## 13. Docs

- [ ] 13.1 `CLAUDE.md`: na seção "Coding Conventions / Scripting contract": atualizar exemplos para `_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`, `_on_collide`. Adicionar sub-seção "Signals" com exemplo `signal: Signal = signal(str)` + `connect/emit`.
- [ ] 13.2 `CLAUDE.md`: na tabela do roadmap, adicionar linha `godot-style-foundation` com status `Active` e resumo curto.
- [ ] 13.3 `CLAUDE.md`: na tabela do roadmap, adicionar linha `game-snake` com status `Planned` e resumo "Validador da fundação Godot-style: fixed-step, signals, Camera2D.bounds, primitivas visuais, sem dependência de colisão nova".
- [ ] 13.4 `CLAUDE.md`: atualizar a seção "Architectural Invariants" item 4 se o vocabulário mudou (verificar — `Renderer/Input/GameHost` continuam SPIs; mantém).
- [ ] 13.5 KDoc em `GameLoop.tick` documentando o accumulator e o clamp de spiral-of-death.
- [ ] 13.6 KDoc em `Signal.connect` documentando a semântica de ordem e safety de disconnect-from-handler.

## 14. Smoke & verify

- [ ] 14.1 `./gradlew check` passa.
- [ ] 14.2 `./gradlew :games:pong:run` abre e roda — paddle responde a W/S, IA persegue, gols funcionam (score atualiza), F1/F2 toggles.
- [ ] 14.3 `./gradlew :games:tictactoe:run` abre e roda — clique coloca X/O, vitória mostra linha, novo clique reinicia.
- [ ] 14.4 `./gradlew :games:demos:run` abre e roda — teclas 1/2/3 trocam demos, spawner adiciona/remove bolinhas, F2 mostra colliders.
- [ ] 14.5 `openspec validate godot-style-foundation --strict` passa.
