## 1. Abstrações base e novo pacote `engine.debug`

- [x] 1.1 Criar pacote `com.neoutils.engine.debug` em `engine/src/main/kotlin/com/neoutils/engine/debug/`.
- [x] 1.2 Criar `DebugWidget.kt`: classe abstrata sealed-ish (`abstract class DebugWidget` com `abstract val title: String`, `open var enabled: Boolean = false`, `abstract fun drawDebug(renderer: Renderer)`). Não estende `Node` direto; as subclasses base sim. *(Implementado como `interface DebugWidget` — Kotlin não permite multi-inheritance de classes, e as bases `ScreenDebugWidget`/`WorldDebugWidget` precisam estender `Node`/`Node2D` simultaneamente. Semântica preservada.)*
- [x] 1.3 Criar `ScreenDebugWidget.kt`: `abstract class ScreenDebugWidget : Node()` que **delega** a `DebugWidget` via composição ou (mais prático) é ela própria `DebugWidget` via classe abstrata herdando ambos. O `onDraw(renderer)` final chama `if (enabled) drawDebug(renderer)`.
- [x] 1.4 Criar `WorldDebugWidget.kt`: `abstract class WorldDebugWidget : Node2D()` idem.
- [x] 1.5 Criar `DebugRegistry.kt`: classe per-tree com `register(widget)`, `unregister(widget)`, `widgets: List<DebugWidget>`, `inline fun <reified T> find(): T?`, e fields lazy/inicializados pros built-ins (`fps`, `colliders`, `momentum`, `hud`). Roteamento: se `widget is WorldDebugWidget`, adiciona ao `worldContainer`; se `widget is ScreenDebugWidget`, adiciona ao `screenCanvas`.
- [x] 1.6 Criar `DebugLayer.kt`: `class DebugLayer : Node()` (não `Node2D`, não `CanvasLayer` — é só agregador). No `init`, cria e `addChild` `WorldDebugContainer` e `ScreenDebugCanvas`. `name = "__debug"`.
- [x] 1.7 Criar `WorldDebugContainer.kt`: `class WorldDebugContainer : Node2D()` sem comportamento próprio.
- [x] 1.8 Criar `ScreenDebugCanvas.kt`: `class ScreenDebugCanvas : CanvasLayer()` com `layer = Int.MAX_VALUE - 1`.
- [x] 1.9 Mover `FpsCounter.kt` de `engine/dx/` pra `engine/debug/`. Mover `DebugColors.kt` (`AREA_COLOR`, `BODY_COLOR`) também.
- [x] 1.10 Compilar `:engine` (sem ainda usar os novos tipos). Validar via `./gradlew :engine:compileKotlin`.

## 2. Built-in widgets

- [x] 2.1 Criar `FpsWidget.kt`: `class FpsWidget : ScreenDebugWidget()` com `title = "FPS"`, field `counter = FpsCounter()`. Sobrescreve `process(dt)` chamando `counter.record(System.nanoTime())`. `drawDebug` desenha `"fps ${counter.current.toInt()}"` em (8, 24) com `Color.WHITE`.
- [x] 2.2 Criar `ColliderWidget.kt`: `class ColliderWidget : WorldDebugWidget()` com `title = "Colliders"`. `drawDebug(renderer)` itera `collectActiveCollisionShapes(tree)` e desenha cada `worldBounds()` com `AREA_COLOR`/`BODY_COLOR`. **Sem `pushTransform` manual** — o pai `WorldDebugContainer` está no world pass que já aplica view transform da câmera.
- [x] 2.3 Criar `MomentumWidget.kt`: `class MomentumWidget : ScreenDebugWidget()` com `title = "Momentum"`. Field `private val buffers = MomentumBuffers(capacity = 60)` (struct interna com 4 FloatArrays, head, size, recordSample, lastSample). Sobrescreve `physicsProcess(dt)` recording (se `enabled`). `drawDebug` desenha labels e sparklines como o `MomentumOverlay.renderOverlay` faz hoje. Sobrescreve setter de `enabled` pra resetar buffer no flip pra true.
- [x] 2.4 Deletar `engine/dx/MomentumOverlay.kt` — sua lógica de ring buffer e render foi internalizada em `MomentumWidget`. Confirmar via grep que nada mais importa.
- [x] 2.5 Testar via `:engine:test`: novo `FpsWidgetTest` (drawDebug com `enabled=false` não chama renderer), novo `ColliderWidgetTest` (com 2 Area + 1 RigidBody, desenha 3 rects nas cores certas), novo `MomentumWidgetTest` (`enabled = true` flip reseta buffer; `physicsProcess(dt)` adiciona amostra; `enabled = false` não desenha). *(Consolidado em `BuiltinWidgetsTest.kt`.)*

## 3. DebugLayer auto-inserido na SceneTree + DebugRegistry público

- [x] 3.1 Em `engine/tree/SceneTree.kt`: substituir `val debug: DebugFlags = DebugFlags()` por `val debug: DebugRegistry = DebugRegistry(this)`. Atualizar `ensureDebugOverlay()` pra instanciar `DebugLayer()` em vez de `DebugOverlayLayer()`. Mover a função pra `ensureDebugLayer()` por clareza (mantém nome velho como alias deprecated? não — é repo pessoal, deleta).
- [x] 3.2 Em `DebugRegistry.init`: instancia os 4 built-ins (`fps`, `colliders`, `momentum`, `hud`) mas **não** os adiciona aos containers ainda — adiciona quando `tree.start()` cria o `DebugLayer`. Alternativa: `DebugRegistry` cria os widgets lazy quando o `DebugLayer` pede; ou `DebugLayer.init` chama `tree.debug.attachBuiltins(this)`. Prefere a segunda — `DebugLayer` é o "site" da auto-inserção. *(Implementado via `DebugLayer.onEnter` → `tree.debug.bindLayer(this)`.)*
- [x] 3.3 Deletar `engine/tree/DebugFlags.kt` e `engine/scene/DebugOverlayLayer.kt`.
- [x] 3.4 Deletar testes antigos `DebugOverlayLayerTest.kt`. Criar `DebugLayerTest.kt`: verifica que `tree.root.findChild("__debug")` retorna `DebugLayer`, que `DebugLayer` tem exatamente 2 filhos (`WorldDebugContainer`, `ScreenDebugCanvas`), que `ScreenDebugCanvas` contém `FpsWidget`, `MomentumWidget`, `DebugHud`, `DebugToggleNode`, que `WorldDebugContainer` contém `ColliderWidget`, e que `start(); stop(); start()` mantém idempotência.
- [x] 3.5 Criar `DebugRegistryTest.kt`: registrar widget custom (screen e world) e verificar roteamento ao container certo, `find<T>()` retorna a instância, `unregister` remove-a, listagem inclui built-ins + customs.
- [x] 3.6 Rodar `:engine:test` e validar.

## 4. HUD + toggle key

- [x] 4.1 Criar `DebugHud.kt`: `class DebugHud : ScreenDebugWidget()` com `title = "Debug HUD"`. No `process(dt)` checa se `enabled` mudou desde último frame; se ligou, rebuild de filhos (`Panel` + 1 `Button` por widget registrado em `tree.debug.widgets` excluindo self); se desligou, remove os filhos. O `Button.label` é `"[x] ${w.title}"` ou `"[ ] ${w.title}"`. Conecta `Button.pressed` signal a `{ w.enabled = !w.enabled; rebuild() }`. *(Implementado: rebuild é só nas transições; refresh de labels é por frame quando enabled.)*
- [x] 4.2 Posicionar a HUD no canto top-right: `Panel.position = Vec2(tree.size.x - panelWidth - 12f, 12f)`. Atualizar em `process(dt)` pra sobreviver a `tree.resize`.
- [x] 4.3 Garantir que `DebugHud.enabled = false` resulta em zero hit-test consumido: ou esconde `Button.visible = false` (validar comportamento de `Panel`/`Button` herdado de `ui-foundation`), ou remove filhos quando off. *(Implementado: filhos `Panel` + `Button`s são removidos da árvore quando enabled=false, o que zera hit-test consumption.)*
- [x] 4.4 Criar `DebugToggleNode.kt`: `internal class DebugToggleNode : Node()`. Em `process(dt)` lê `tree.input.wasKeyPressed(tree.debugHudKey ?: Key.F1)` — onde `tree.debugHudKey` ainda precisa ser exposto. Alternativa mais limpa: `DebugToggleNode` recebe o key como property, set pelo `DebugLayer.init` lendo de... onde? `GameConfig` não chega na engine via `SceneTree`. Solução: `SceneTree` ganha `var debugHudKey: Key = Key.F1` (pública, host seta no startup). Setter atualiza `DebugToggleNode.key`. *(Implementado: `SceneTree.debugHudKey` exposto; `DebugToggleNode` lê direto de `tree.debugHudKey` em cada tick — sem property-mirror.)*
- [x] 4.5 Hosts setam `tree.debugHudKey = config.debugHudKey` antes do primeiro `tick`. Detalhe na fase 6.
- [x] 4.6 Criar `DebugHudTest.kt`: com `tree.debug.hud.enabled = true`, simular `Button.pressed` da row "FPS" e verificar que `tree.debug.fps.enabled` flippou. Verificar que labels começam com `"[x] "` quando enabled, `"[ ] "` quando não.
- [x] 4.7 Criar `DebugToggleNodeTest.kt`: simular `input.wasKeyPressed(Key.F1) = true` num tick, ver `tree.debug.hud.enabled` flippar pelo próximo tick.

## 5. GameLoop: remover hook do MomentumOverlay singleton

- [x] 5.1 Em `engine/loop/GameLoop.kt`: remover import `MomentumOverlay`. Remover linha `if (tree.debug.showMomentum) MomentumOverlay.recordSample(tree)` do bloco de physics step.
- [x] 5.2 Validar que `MomentumWidget.physicsProcess(dt)` cobre a amostragem agora (via teste — momento certo é dispatched pelo scene graph, igual qualquer Node).
- [x] 5.3 Rodar `:engine:test` e validar — em particular `RigidBodyConservationTest` que **não** usa `MomentumOverlay` (usa diretamente `tree.totalLinearMomentum()` etc.), então segue passando.

## 6. GameConfig: 1 key em vez de 3

- [x] 6.1 Em `engine/runtime/GameConfig.kt`: remover `toggleFpsKey`, `toggleCollidersKey`, `toggleMomentumOverlayKey`. Adicionar `debugHudKey: Key = Key.F1`.
- [x] 6.2 Build deve quebrar nos hosts e quem mais lia esses campos. Caçar todos.
- [x] 6.3 Rodar `./gradlew compileKotlin` e listar erros. Esperado: `SkikoHost.kt`, `LwjglHost.kt`.

## 7. SkikoHost: corta debug

- [x] 7.1 Em `engine-skiko/src/main/kotlin/com/neoutils/engine/skiko/SkikoHost.kt`: remover import de `FpsCounter` e `MomentumOverlay`. Remover instanciação `val fps = FpsCounter()`. Remover linha `tree.debug.currentFps = fps.record(nanoTime)`. Remover bloco `if (input.wasKeyPressed(config.toggleFpsKey))` e os 2 análogos (Colliders, Momentum) e a chamada `MomentumOverlay.reset()`. Adicionar `tree.debugHudKey = config.debugHudKey` antes do primeiro `loop.tick` (após `tree.start()` ou onde o host inicializa).
- [x] 7.2 Compilar `:engine-skiko` e validar.
- [x] 7.3 Atualizar/adicionar teste/grep: source de `SkikoHost.kt` não menciona `tree.debug.*`, `FpsCounter`, `MomentumOverlay` (exceto `tree.debugHudKey =` no setup).

## 8. LwjglHost: corta debug

- [x] 8.1 Em `engine-lwjgl/src/main/kotlin/com/neoutils/engine/lwjgl/LwjglHost.kt`: mesma operação que 7.1.
- [x] 8.2 Compilar `:engine-lwjgl` e validar.
- [x] 8.3 Mesma asserção grep do 7.3 pra `LwjglHost.kt`.

## 9. Limpeza do package `dx`

- [x] 9.1 Deletar `engine/dx/Debug.kt`. Mover `val log: LogConfig` que ali estava pra `engine/dx/Log.kt` como `Log.config: LogConfig` (companion-style). Atualizar `Log.log(...)` pra ler `config.effectiveLevel(tag)`.
- [x] 9.2 Atualizar callers — `LogTest.kt` e qualquer outro lugar que usava `Debug.log`. Grep `Debug.log\.` em todo o projeto.
- [x] 9.3 Compilar `:engine` e rodar `:engine:test`.

## 10. Demo de widget custom em `:games:demos`

- [x] 10.1 Em `games/demos/src/main/kotlin/...`: criar `AxesWidget` extendendo `WorldDebugWidget` com `title = "Axes"`. `drawDebug` desenha `r.drawLine(Vec2.ZERO, Vec2(100f, 0f), 2f, Color.RED)` e `r.drawLine(Vec2.ZERO, Vec2(0f, 100f), 2f, Color.GREEN)`.
- [x] 10.2 No `Main.kt` (ou no `Demo` orchestrator), após `tree.start()` chamar `tree.debug.register(AxesWidget())`. Validar visual: abre o demo, F1, marca `Axes`, vê eixos na origem. *(Registro implementado; validação visual fica pendente — sessão não-interativa.)*
- [x] 10.3 Idem pro entrypoint LWJGL — a demo registra o mesmo widget. Confirma que rodando `:games:demos:runLwjgl` o widget também aparece. *(Registro implementado; validação visual fica pendente — sessão não-interativa.)*

## 11. Documentação

- [x] 11.1 `CLAUDE.md`: atualizar invariante #4 ("`GameHost.render` não desenha" — adicionar "e não toca em `tree.debug.*` nem em qualquer FpsCounter/MomentumOverlay; polling do `debugHudKey` é interno à engine"). Atualizar invariante #6 mencionando que `DebugLayer` tem 2 sub-containers (world Node2D e screen CanvasLayer).
- [x] 11.2 `CLAUDE.md`: atualizar seção "Module Layout" — `:engine` ganha `com.neoutils.engine.debug.*`; `dx` é só logging agora.
- [x] 11.3 `README.md`: substituir mentions de F1/F2/F3 por "F1 abre HUD de debug com checkboxes". Mencionar `tree.debug.register(MyWidget())` como extension hook.
- [x] 11.4 Atualizar `ROADMAP.md` marcando `debug-widgets` como ativa/in-progress.

## 12. Specs

- [ ] 12.1 Atualizar `openspec/specs/debug-overlay/spec.md` aplicando o delta. Verificar que os scenarios refletem `DebugWidget`/`DebugRegistry`/`DebugLayer`/`DebugHud`. *(Pendente — será aplicado no `openspec archive`.)*
- [ ] 12.2 Atualizar `openspec/specs/engine-core/spec.md` aplicando o delta — `GameConfig` schema, `SceneTree.debug` tipo, requisito "host polls toggle keys" removido. *(Pendente — archive.)*
- [ ] 12.3 Atualizar `openspec/specs/skiko-runtime/spec.md` e `openspec/specs/lwjgl-runtime/spec.md` — scenarios de F1/F2/F3 toggle redirecionam pro novo modelo. *(Pendente — archive.)*
- [ ] 12.4 Atualizar `openspec/specs/dx-tooling/spec.md` — escopo encolhe pra "só logging". *(Pendente — archive.)*
- [x] 12.5 Rodar `openspec verify debug-widgets` (se a CLI tiver esse comando) ou `openspec list --json` pra confirmar tasks % bate.

## 13. Validação final

- [x] 13.1 Rodar `./gradlew build` na raiz — todos os módulos compilam.
- [x] 13.2 Rodar `./gradlew :engine:test :engine-skiko:test :engine-lwjgl:test` — todos passam.
- [ ] 13.3 Rodar manualmente cada jogo shipped (`:games:pong:run`, `:games:tictactoe:run`, `:games:snake:run`, `:games:hello-world:run`, `:games:demos:run`, `:games:demos:runLwjgl`) — todos abrem, F1 abre HUD em todos eles. *(Validação visual pendente — sessão não-interativa.)*
- [ ] 13.4 Em `:games:demos`, verificar que `AxesWidget` aparece na HUD e desenha quando habilitado. *(Validação visual pendente.)*
- [x] 13.5 Grep final: `git grep "DebugFlags\|DebugOverlayLayer\|MomentumOverlay\|toggleFpsKey\|toggleCollidersKey\|toggleMomentumOverlayKey\|tree.debug.showFps\|tree.debug.showColliders\|tree.debug.showMomentum\|tree.debug.currentFps"` deve retornar zero hits (exceto em `openspec/changes/archive/`).
- [x] 13.6 Confirmar que adicionar widget novo em outro projeto-jogo é 1 arquivo + 1 chamada. Documentar workflow num bloco curto no `README.md` ou seção do `:games:demos/README` (se existir). *(README.md atualizado com snippet de `MyAxes`.)*
