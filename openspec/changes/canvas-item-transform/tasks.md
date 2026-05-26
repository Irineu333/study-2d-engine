## 1. Renderer SPI

- [x] 1.1 Estender `Renderer.pushTransform` em `engine/src/main/kotlin/com/neoutils/engine/render/Renderer.kt` para `pushTransform(translation: Vec2, rotation: Float, scale: Vec2)`. Atualizar KDoc descrevendo a composição `translate ∘ rotate ∘ scale` (rotação em radianos, em torno do novo origem pós-translation).
- [x] 1.2 Garantir que a compilação quebra exatamente nos call sites esperados (Skiko, Compose, Camera2D, DebugOverlay) — sem regressão silenciosa.

## 2. Skiko backend

- [x] 2.1 Atualizar `SkikoRenderer.pushTransform` em `engine-skiko/src/main/kotlin/com/neoutils/engine/skiko/SkikoRenderer.kt`: `canvas.save()`, `canvas.translate(translation.x, translation.y)`, `canvas.rotate((rotation * 180f / PI).toFloat())` (conversão para degrees), `canvas.scale(scale.x, scale.y)`.
- [x] 2.2 Garantir que `popTransform` continua emitindo `canvas.restore()` e que a contagem de imbalance permanece funcional.

## 3. Compose backend

- [x] 3.1 Atualizar `ComposeRenderer.pushTransform` em `engine-compose/src/main/kotlin/com/neoutils/engine/compose/ComposeRenderer.kt`: aplicar `DrawScope.translate(...)`, depois `DrawScope.rotate(degrees, pivot = Offset.Zero)` (degrees = radians * 180 / PI), depois `DrawScope.scale(...)` (pivot na origem do frame translation-deslocado). Pode usar `DrawTransform` diretamente se for mais limpo do que aninhar builders.
- [x] 3.2 Garantir que `popTransform` continua restaurando o `DrawScope` para o estado anterior corretamente.

## 4. SceneTree per-Node2D push

- [x] 4.1 Refatorar `SceneTree.render(renderer)` em `engine/src/main/kotlin/com/neoutils/engine/tree/SceneTree.kt` (e/ou no walker correspondente) para, ao visitar cada `Node2D` durante o walk, chamar `renderer.pushTransform(node.transform.position, node.transform.rotation, node.transform.scale)` antes do `onDraw`, recursar nos filhos, e chamar `renderer.popTransform()` depois (via `try/finally`). Nodes não-`Node2D` (e.g. `Timer`) NÃO devem chamar push/pop — apenas forward para descendentes.
- [x] 4.2 Garantir que o push da view do `Camera2D` (já aplicado no início de `SceneTree.render`) continua sendo o outermost; per-Node2D pushes aninham dentro dele.
- [x] 4.3 Atualizar o call site da view do `Camera2D` em `SceneTree.render` para passar `rotation = 0f` na nova assinatura.

## 5. Visual nodes em local space

- [x] 5.1 Reescrever `Polygon2D.onDraw` em `engine/src/main/kotlin/com/neoutils/engine/scene/Polygon2D.kt` para chamar `renderer.drawPolygon(points, color)` direto (sem somar `world().position`).
- [x] 5.2 Reescrever `Line2D.onDraw` em `engine/src/main/kotlin/com/neoutils/engine/scene/Line2D.kt` para iterar `points` em local space.
- [x] 5.3 Reescrever `Circle2D.onDraw` em `engine/src/main/kotlin/com/neoutils/engine/scene/Circle2D.kt` para desenhar em `Vec2.ZERO` com `radius` literal (sem `worldCenter`/`worldRadius`).
- [x] 5.4 Reescrever `ColorRect.onDraw` em `engine/src/main/kotlin/com/neoutils/engine/scene/ColorRect.kt` para `drawRect(Rect(Vec2.ZERO, size), color, filled = true)`.
- [x] 5.5 Reescrever `Label.onDraw` em `engine/src/main/kotlin/com/neoutils/engine/scene/Label.kt` para `drawText(text, Vec2.ZERO, size, color)`. Manter cálculos de alinhamento via `measureText` referenciados à origem local.
- [x] 5.6 Auditar `Camera2D` (e qualquer outro `Node2D` shipped que sobrescreva `onDraw`) para confirmar que o que ele desenha hoje continua coerente com local-space ou não desenha nada.

## 6. Debug overlay adapta nova assinatura

- [x] 6.1 Atualizar `renderDebugOverlay` em `engine/src/main/kotlin/com/neoutils/engine/dx/DebugOverlay.kt` para chamar `renderer.pushTransform(view.first, 0f, view.second)` (nova assinatura). Confirmar que `shape.worldBounds()` continua sendo a fonte da verdade dos AABBs desenhados; overlay opera em coordenadas world independente do per-Node2D push (não está dentro do walk de `SceneTree.render`).

## 7. Testes unitários (RecordingRenderer)

- [x] 7.1 Criar `RecordingRenderer` de teste em `engine/src/test/kotlin/.../render/` que armazena a sequência de chamadas `push/pop/draw*` recebidas, expondo uma `events: List<RecordedEvent>` para asserções.
- [x] 7.2 Adicionar teste: `SceneTree.render` com um `ColorRect` único e sem câmera produz exatamente um `pushTransform(Vec2(x, y), 0f, Vec2(1f, 1f))` + um `drawRect(Rect(Vec2.ZERO, size), ...)` + um `popTransform()`, nessa ordem.
- [x] 7.3 Adicionar teste: dois `Node2D` aninhados (`parent` em (100,0), `child ColorRect` em (0,50) local) produzem dois pushes aninhados; o `drawRect` do filho aparece sob a stack composta (verificar via simulação da stack acumulada do `RecordingRenderer`).
- [x] 7.4 Adicionar teste: um `Timer` filho de um `Node2D` NÃO gera `pushTransform` para si próprio (apenas o pai gera).
- [x] 7.5 Adicionar teste: `Polygon2D` com `transform.rotation = PI / 2` gera `pushTransform(..., PI / 2, ...)` e `drawPolygon(points-in-local-space, color)` — verificar valores literais.
- [x] 7.6 Adicionar teste: exception lançada dentro de `Node2D.onDraw` ainda dispara `popTransform()` (try/finally cumprido).
- [x] 7.7 Adicionar teste: árvore com `Camera2D` current empilha view no topo; per-Node2D push é o seguinte. Ordem completa: `push(view)`, `push(node)`, `draw...`, `pop(node)`, `pop(view)`.

## 8. Validação cruzada manual dos jogos existentes

- [x] 8.1 `./gradlew :games:pong:run` — partida funciona; paddles desenham; bola desenha; score Label desenha; F1/F2 toggle ainda funcionam; F2 mostra colliders alinhados ao mundo.
- [x] 8.2 `./gradlew :games:tictactoe:run` — grade + X/O desenham; clique posiciona corretamente.
- [x] 8.3 `./gradlew :games:demos:run` — todas as 6 demos visualmente idênticas; especificamente #1 (orbit) e #2 (scale hierarchy) continuam compondo transforms corretamente; #4–#6 colliders alinhados.
- [x] 8.4 `./gradlew :games:hello-world:run` — "Hello, world!" centralizado ao redimensionar.
- [x] 8.5 Se `:games:snake` já estiver archived: `./gradlew :games:snake:run` — segmentos desenham, food desenha, score desenha, restart funciona.

## 9. Documentação

- [x] 9.1 Atualizar KDoc de `Renderer.pushTransform` para refletir a nova assinatura + semântica de composição com rotação.
- [x] 9.2 Adicionar nota em `CLAUDE.md` (seção "Coding Conventions" ou nova subseção "Drawing in local space") explicando que `Node2D.onDraw` deve operar em local space; o engine aplica `pushTransform` ao redor.
- [x] 9.3 Atualizar a seção "Camera2D define o mundo virtual" em `CLAUDE.md` para mencionar que cada `Node2D` agora também recebe push da sua local transform.

## 10. Roadmap

- [x] 10.1 Não promover nada para Active ainda — `canvas-item-transform` entrará como Active quando ficar pronto para apply (após archive deste propose). Quando archive ocorrer, remover da Active.
