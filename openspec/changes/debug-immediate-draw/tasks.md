## 1. Command model e canvas

- [x] 1.1 Criar `sealed interface DrawCommand` em `com.neoutils.engine.debug` com variantes `Line`, `Rect`, `Circle`, `Polygon`, `Text` (data classes imutáveis).
- [x] 1.2 Criar `class DebugCanvas` com buffer `MutableList<DrawCommand>` e os verbos `line/rect/circle/polygon/text` (assinaturas da spec) que enfileiram quando habilitado.
- [x] 1.3 Adicionar à `DebugCanvas` um `fun flush(renderer: Renderer)` que emite cada comando na primitiva correspondente do `Renderer`, e `fun clear()`.

## 2. Facade DebugDraw

- [x] 2.1 Criar `class DebugDraw` com `val world: DebugCanvas`, `val screen: DebugCanvas`, `var enabled: Boolean = false` e `fun clearFrame()` (limpa os dois buffers).
- [x] 2.2 Gating: cada verbo de `DebugCanvas` é no-op (early-return) quando o `DebugDraw` dono está desabilitado — passar a referência de enabled ao canvas (ex.: lambda/owner) para o early-return.
- [x] 2.3 Adicionar `val draw: DebugDraw` em `DebugRegistry`.

## 3. Nodes de backing + toggle

- [x] 3.1 Criar `ImmediateWorldDrawNode : Node2D` cujo `onDraw` chama `tree.debug.draw.world.flush(renderer)` (sem `pushTransform` próprio).
- [x] 3.2 Criar `ImmediateScreenDrawNode : Node` cujo `onDraw` chama `tree.debug.draw.screen.flush(renderer)`.
- [x] 3.3 Criar `DebugDrawToggle : ScreenDebugWidget` com `title = "Debug Draw"`, `drawDebug` vazio, e `enabled` delegando para `tree.debug.draw.enabled`.
- [x] 3.4 No `DebugLayer.onEnter`, inserir `ImmediateWorldDrawNode` no `WorldDebugContainer` e `ImmediateScreenDrawNode` no `ScreenDebugCanvas`; registrar `DebugDrawToggle` via o registry.

## 4. Clear no tail do render

- [x] 4.1 Em `SceneTree.render`, após o world pass e o UI pass, chamar `debug.draw.clearFrame()`.

## 5. Exposição a scripts

- [x] 5.1 Verificar via script Python real que `self.tree.debug.draw.world.line(Vec2(...), Vec2(...), Color(...))` navega sob o HostAccess atual; bindar `DebugDraw`/`DebugCanvas` explicitamente se necessário.
- [x] 5.2 Idem para Lua (`self.tree.debug.draw.screen:circle(...)`), respeitando o sandbox de `require`.
- [x] 5.3 Atualizar stubs `.pyi` (GraalPy) e LuaCATS com `DebugDraw`, `DebugCanvas` e os verbos.

## 6. Testes

- [x] 6.1 Teste: cada verbo enfileira um comando; world e screen são buffers distintos.
- [x] 6.2 Teste: comando enfileirado em `physicsProcess` produz exatamente um draw no pass certo (world sob view transform, screen em pixels).
- [x] 6.3 Teste: buffers vazios após `render`; sem acúmulo entre frames (2º frame sem enfileirar → zero draws).
- [x] 6.4 Teste: desabilitado → verbos no-op (buffer vazio, zero draws).
- [x] 6.5 Teste: HUD lista exatamente uma row "Debug Draw" que flipa `tree.debug.draw.enabled`.
- [x] 6.6 Teste de integração: script Python e Lua emitindo gizmo aparecem no buffer após a fase correspondente.

## 7. Fechamento

- [x] 7.1 Rodar a suíte do `:engine`, `:engine-bundle-python` e `:engine-bundle-lua`; garantir verde.
- [x] 7.2 `openspec validate debug-immediate-draw --strict` e revisar coerência specs↔implementação.

## 8. Validação manual

- [x] 8.1 Demonstrar via script real: o `snake.py` emite o grid 20×20 do mundo em `tree.debug.draw.world` a cada `_process` (desligado por padrão — auxílio de debug fiel ao uso real). Coberto por teste de bundle.
- [ ] 8.2 **Teste manual (obrigatório antes do archive):** rodar `./gradlew :games:snake:run` e confirmar visualmente:
  - a tela começa limpa (grid desligado por padrão);
  - abrir o HUD (`F1`) e marcar a row "Debug Draw" → o grid do mundo aparece atrás da cobra, alinhado às células e à `Camera2D`;
  - desmarcar → o grid some;
  - nenhum acúmulo/rastro entre frames (buffers limpos a cada frame).
- [x] 8.3 Demonstrar via script **Lua** (sentinela do segundo backend de scripting): o `board.lua` do tictactoe numera as 9 células em `draw.world`, realça a célula sob o cursor (`draw.world:rect`) e ecoa a célula em `draw.screen` perto do ponteiro — desligado por padrão. Coberto por teste de bundle (`TicTacToeBundleTest`).
- [ ] 8.4 **Teste manual (Lua):** rodar `./gradlew :games:tictactoe:run`, abrir o HUD (`F1`), marcar "Debug Draw" e confirmar: índices 1–9 nas células, realce da célula sob o mouse e o texto "cell N" seguindo o cursor; desmarcar limpa tudo.
- [x] 8.5 Demonstrar em física (pong, Python): `ball.py` desenha o vetor de velocidade da bola (`draw.world.line` + marcador) e `paddle.py` (só IA) desenha a linha do alvo + faixa de tolerância — desligado por padrão. Coberto por teste de bundle (`PongBundleTest`).
- [ ] 8.6 **Teste manual (pong):** rodar `./gradlew :games:pong:run`, abrir o HUD (`F1`), marcar "Debug Draw" e confirmar: o vetor de velocidade gira/encolhe a cada rebatida e a mira do paddle IA segue a bola dentro da faixa; desmarcar limpa tudo.
