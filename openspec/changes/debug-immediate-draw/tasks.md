## 1. Command model e canvas

- [ ] 1.1 Criar `sealed interface DrawCommand` em `com.neoutils.engine.debug` com variantes `Line`, `Rect`, `Circle`, `Polygon`, `Text` (data classes imutáveis).
- [ ] 1.2 Criar `class DebugCanvas` com buffer `MutableList<DrawCommand>` e os verbos `line/rect/circle/polygon/text` (assinaturas da spec) que enfileiram quando habilitado.
- [ ] 1.3 Adicionar à `DebugCanvas` um `fun flush(renderer: Renderer)` que emite cada comando na primitiva correspondente do `Renderer`, e `fun clear()`.

## 2. Facade DebugDraw

- [ ] 2.1 Criar `class DebugDraw` com `val world: DebugCanvas`, `val screen: DebugCanvas`, `var enabled: Boolean = false` e `fun clearFrame()` (limpa os dois buffers).
- [ ] 2.2 Gating: cada verbo de `DebugCanvas` é no-op (early-return) quando o `DebugDraw` dono está desabilitado — passar a referência de enabled ao canvas (ex.: lambda/owner) para o early-return.
- [ ] 2.3 Adicionar `val draw: DebugDraw` em `DebugRegistry`.

## 3. Nodes de backing + toggle

- [ ] 3.1 Criar `ImmediateWorldDrawNode : Node2D` cujo `onDraw` chama `tree.debug.draw.world.flush(renderer)` (sem `pushTransform` próprio).
- [ ] 3.2 Criar `ImmediateScreenDrawNode : Node` cujo `onDraw` chama `tree.debug.draw.screen.flush(renderer)`.
- [ ] 3.3 Criar `DebugDrawToggle : ScreenDebugWidget` com `title = "Debug Draw"`, `drawDebug` vazio, e `enabled` delegando para `tree.debug.draw.enabled`.
- [ ] 3.4 No `DebugLayer.onEnter`, inserir `ImmediateWorldDrawNode` no `WorldDebugContainer` e `ImmediateScreenDrawNode` no `ScreenDebugCanvas`; registrar `DebugDrawToggle` via o registry.

## 4. Clear no tail do render

- [ ] 4.1 Em `SceneTree.render`, após o world pass e o UI pass, chamar `debug.draw.clearFrame()`.

## 5. Exposição a scripts

- [ ] 5.1 Verificar via script Python real que `self.tree.debug.draw.world.line(Vec2(...), Vec2(...), Color(...))` navega sob o HostAccess atual; bindar `DebugDraw`/`DebugCanvas` explicitamente se necessário.
- [ ] 5.2 Idem para Lua (`self.tree.debug.draw.screen:circle(...)`), respeitando o sandbox de `require`.
- [ ] 5.3 Atualizar stubs `.pyi` (GraalPy) e LuaCATS com `DebugDraw`, `DebugCanvas` e os verbos.

## 6. Testes

- [ ] 6.1 Teste: cada verbo enfileira um comando; world e screen são buffers distintos.
- [ ] 6.2 Teste: comando enfileirado em `physicsProcess` produz exatamente um draw no pass certo (world sob view transform, screen em pixels).
- [ ] 6.3 Teste: buffers vazios após `render`; sem acúmulo entre frames (2º frame sem enfileirar → zero draws).
- [ ] 6.4 Teste: desabilitado → verbos no-op (buffer vazio, zero draws).
- [ ] 6.5 Teste: HUD lista exatamente uma row "Debug Draw" que flipa `tree.debug.draw.enabled`.
- [ ] 6.6 Teste de integração: script Python e Lua emitindo gizmo aparecem no buffer após a fase correspondente.

## 7. Fechamento

- [ ] 7.1 Rodar a suíte do `:engine`, `:engine-bundle-python` e `:engine-bundle-lua`; garantir verde.
- [ ] 7.2 `openspec validate debug-immediate-draw --strict` e revisar coerência specs↔implementação.
