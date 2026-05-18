## 1. Red-first smoke tests

- [x] 1.1 Adicionar `engine/src/test/kotlin/com/neoutils/engine/math/TransformComposeTest.kt` cobrindo identidade, scale componente a componente, soma de rotação, composição de posição com rotação+scale do pai. **Deve falhar** (método ainda não existe).
- [x] 1.2 Adicionar `engine/src/test/kotlin/com/neoutils/engine/scene/WorldTransformTest.kt` cobrindo `Node2D.worldTransform()` para translação pura, scale herdado, rotação herdada e cadeia de 3 níveis. **Deve falhar** (método ainda não existe).
- [x] 1.3 Adicionar `engine/src/test/kotlin/com/neoutils/engine/scene/SceneMutationDuringTraversalTest.kt` com casos: `addChild` durante `onUpdate`, `removeChild` durante `onUpdate`, `addChild` durante `onCollide`, `removeChild` durante `onCollide`. Assert: sem `ConcurrentModificationException`, estado final consistente, `onEnter`/`onExit` corretos antes da próxima fase. **Deve falhar** com CME hoje.
- [x] 1.4 Adicionar `engine/src/test/kotlin/com/neoutils/engine/scene/SceneCoreDecouplingTest.kt` que lê o source de `Scene.kt` como recurso de teste e assert que nenhuma linha de import começa com `com.neoutils.engine.dx`. **Deve falhar** hoje (Scene importa `Debug`).
- [x] 1.5 Rodar `./gradlew :engine:test` e confirmar que os 4 novos testes falham antes de qualquer mudança de produção.

## 2. A3 — Cache de scene no Node

- [x] 2.1 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Node.kt`, adicionar campo `var scene: Scene? = null` com setter `private` ao módulo.
- [x] 2.2 No `attachToLiveTree`, antes de chamar `onEnter()`, atribuir `this.scene` à `Scene` proprietária (passar como parâmetro do helper `attachToLiveTree(scene: Scene)` ou descobrir via `parent?.scene`).
- [x] 2.3 No `detachFromLiveTree`, depois de `onExit()` retornar, zerar `this.scene = null`.
- [x] 2.4 Reescrever `rootScene()` como `return scene` (preservando assinatura `Scene?`).
- [x] 2.5 Rodar `./gradlew :engine:test`; `NodeTest` e `GameLoopTest` devem continuar verdes.

## 3. A1 — Transform composition

- [x] 3.1 Em `engine/src/main/kotlin/com/neoutils/engine/math/Transform.kt`, adicionar `fun compose(child: Transform): Transform` aplicando: `scale = this.scale .* child.scale`, `rotation = this.rotation + child.rotation`, `position = this.position + rotate(this.scale .* child.position, this.rotation)`. Adicionar helpers internos `rotate(Vec2, Float)` se ainda não existirem.
- [x] 3.2 Confirmar que `TransformComposeTest` (tarefa 1.1) passa.
- [x] 3.3 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Node2D.kt`, adicionar `fun worldTransform(): Transform` que coleta os ancestrais `Node2D` (incluindo `this`) em ordem topo→base e dobra via `Transform.compose`.
- [x] 3.4 Reescrever `worldPosition()` como `return worldTransform().position`.
- [x] 3.5 Confirmar que `WorldTransformTest` (tarefa 1.2) passa.
- [x] 3.6 Em `engine/src/main/kotlin/com/neoutils/engine/physics/BoxCollider.kt`, reescrever `bounds()` para usar `worldTransform()`. Se `worldTransform().rotation == 0f`, devolver `Rect(worldTransform().position, size * worldTransform().scale)`. Se for ≠ 0, calcular os 4 cantos do OBB e devolver o AABB que os envolve.
- [x] 3.7 Adicionar cenários de regressão em `PhysicsSystemTest`: collider com pai escalado, collider com pai rotacionado a 45°.
- [x] 3.8 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Shape.kt`, atualizar `onRender` para usar `worldTransform()` em vez de `worldPosition()` + `transform.scale`. Adicionar KDoc no topo da classe documentando que rotação **não** é aplicada visualmente até a futura change `Renderer.withTransform`.
- [x] 3.9 Rodar `./gradlew :engine:test :games:pong:run` (manual). Pong deve se comportar idêntico (zero rotação na cena).

## 4. A4 — Mutação segura durante traversal

- [x] 4.1 Em `Node.kt`, adicionar `private val pendingAdd = mutableListOf<Node>()` e `private val pendingRemove = mutableListOf<Node>()`.
- [x] 4.2 Em `Scene.kt`, adicionar `private var inTraversal: Boolean = false` e helper `internal fun setInTraversal(v: Boolean)`. Wrapping `traverseUpdate` e `traverseRender` (e o helper de coleta de colliders se aplicável) com `try { inTraversal = true; … } finally { inTraversal = false }`.
- [x] 4.3 Em `Node.addChild` e `Node.removeChild`, detectar via `scene?.inTraversal == true`. Quando `true`, enfileirar em `pendingAdd`/`pendingRemove` do `this` e retornar sem mutar `_children`. Quando `false`, manter o caminho síncrono atual.
- [x] 4.4 Em `Node`, adicionar `internal fun applyPending()` que, recursivamente pós-ordem (filhos primeiro), aplica primeiro `pendingRemove` (chamando o caminho síncrono real de `removeChild`) e em seguida `pendingAdd` (caminho síncrono real de `addChild`), e finalmente limpa as filas.
- [x] 4.5 Em `Scene`, expor `fun applyPending()` que invoca `applyPending()` na raiz.
- [x] 4.6 Em `engine/src/main/kotlin/com/neoutils/engine/loop/GameLoop.kt`, intercalar `scene.applyPending()` nos três pontos: antes de `scene.update`, antes de `physics.step`, antes de `scene.render`.
- [x] 4.7 Em `PhysicsSystem.step`, marcar `inTraversal` ao redor do loop de pares (cuidado: `Scene.inTraversal` precisa ser acessível; talvez expor `Scene.beginPhysicsPhase()/endPhysicsPhase()` em vez de manipular o boolean direto).
- [x] 4.8 Em `Scene.render` (que é traversal), garantir que mutações de `onRender` sejam detectadas e ignoradas/loggadas — usar `Log.w("Scene", "addChild called during onRender; ignored")`. Não enfileirar, conforme decisão D5.
- [x] 4.9 Confirmar que `SceneMutationDuringTraversalTest` (tarefa 1.3) passa.
- [x] 4.10 Revisar `NodeTest`, `GameLoopTest`, `PhysicsSystemTest` — tests atuais devem continuar verdes.

## 5. A2 — Desacoplar Scene de Debug

- [x] 5.1 Em `Scene.kt`, remover o import `com.neoutils.engine.dx.Debug` e a função `drawColliderBounds`. Remover a chamada condicional em `Scene.render`. Remover a constante `DEBUG_COLLIDER_COLOR` (mover para a etapa 5.3).
- [x] 5.2 Confirmar que `SceneCoreDecouplingTest` (tarefa 1.4) passa.
- [x] 5.3 Em `engine/src/main/kotlin/com/neoutils/engine/physics/PhysicsSystem.kt` (ou um arquivo novo `Colliders.kt`), expor utilitário público `fun collectColliders(scene: Scene): List<Collider>` reutilizando a lógica interna existente.
- [x] 5.4 Em `engine-compose/src/main/kotlin/com/neoutils/engine/compose/GameSurface.kt`, após `loop.tick(pendingDt)` e ainda dentro do `try` que mantém `renderer.bind`, ler `Debug.colliderVisualization`. Quando `true`, iterar `collectColliders(scene)` e chamar `renderer.drawRect(collider.bounds(), DEBUG_COLLIDER_COLOR, filled = false)`. Adicionar `DEBUG_COLLIDER_COLOR` como `private` no top-level do arquivo.
- [x] 5.5 Rodar Pong com F2 ativado e confirmar visualmente que o overlay continua aparecendo idêntico ao comportamento anterior.

## 6. Validação final

- [x] 6.1 Rodar `./gradlew clean test` em verde para todo o projeto (engine, engine-compose, games).
- [x] 6.2 Rodar `./gradlew :games:pong:run` manualmente. Conferir: paddle humano (W/S), paddle AI, colisões, F1 (FPS overlay), F2 (collider overlay) — todos como antes.
- [x] 6.3 Rodar `./gradlew :games:tictactoe:run` manualmente. Conferir: clique para jogar, reset por clique pós-fim, F1 (FPS).
- [x] 6.4 Atualizar tabela do roadmap em `CLAUDE.md` adicionando linha `engine-consistency` com status `Active`. (O status vai para `Archived` no `/opsx:archive`.)
- [x] 6.5 Rodar `openspec validate engine-consistency --strict` e confirmar zero issues.

## 7. Encerramento

- [x] 7.1 Pedir a um humano para revisar a change e os jogos.
- [x] 7.2 Após aprovação, executar `/opsx:verify engine-consistency` para checagem cruzada com os artefatos.
- [x] 7.3 Executar `/opsx:archive engine-consistency` para sincronizar specs principais e mover a change para `archive/`.

## 8. Módulo `:games:demos` (validação visual das melhorias)

- [x] 8.1 Adicionar `include(":games:demos")` em `settings.gradle.kts`.
- [x] 8.2 Criar `games/demos/build.gradle.kts` espelhando `games/pong/build.gradle.kts` (`kotlinJvm` + `composeMultiplatform` + `composeCompiler`, depende de `:engine` e `:engine-compose`, `mainClass = "com.neoutils.engine.games.demos.MainKt"`).
- [x] 8.3 Criar `Main.kt` em `com.neoutils.engine.games.demos` com `application { Window { ... GameSurface(scene = switcher) ... } }`. Teclas `1/2/3` trocam a demo ativa, `F1` FPS, `F2` overlay de colliders.
- [x] 8.4 Criar `DemoSwitcherScene.kt`: hospeda os três demos como filhos, mas só um está vivo por vez (attach/detach via `addChild`/`removeChild`). Mostra um `Text` no topo com o nome da demo ativa e as teclas disponíveis.
- [x] 8.5 `TransformOrbitDemo`: pai `Node2D` no centro com `rotation` animada em `onUpdate`; dois `Shape` filhos com `position` local `(R, 0)` e `(-R, 0)`. Como `worldTransform` compõe rotação do pai sobre a posição local, os filhos **orbitam** — valida A1 (rotação composta sobre posição). Adiciona um `Shape` de referência no centro para destacar o eixo.
- [x] 8.6 `ScaleHierarchyDemo`: pai com `scale` oscilando entre 0.5 e 2.0 em `onUpdate`; `Shape` filho fixo em tamanho local. Visual: o filho cresce e encolhe — valida A1 (scale composto via `Shape.onRender` lendo `worldTransform().scale`).
- [x] 8.7 `SpawnerDemo`: cada clique do mouse adiciona um `BoxCollider` com `Shape` filho colorido em posição aleatória, criado **dentro de `onUpdate`** do spawner; outro nó "trap" central com `BoxCollider` chama `parent.removeChild(other.parent)` em `onCollide` para remover spawns que tocam — valida A4 (mutação durante `onUpdate` e durante `onCollide` sem CME e visível na fase seguinte). F2 evidencia o overlay (valida A2).
- [x] 8.8 Atualizar `CLAUDE.md` adicionando bloco `Para rodar Demos:` com `./gradlew :games:demos:run` e o cheat sheet das teclas.
- [x] 8.9 Rodar `./gradlew :games:demos:run` manualmente e conferir as três demos.
- [x] 8.10 Em `SpawnerDemo`, corrigir o bug visual em que as bolinhas atravessam a borda inferior antes de quicar — substituir o hard-coded `800f`/`600f` por `rootScene()?.width`/`height` para que o quique respeite o tamanho real do canvas (que difere do tamanho da `Window` em DP).
