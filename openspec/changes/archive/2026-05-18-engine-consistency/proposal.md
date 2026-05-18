## Why

O `:engine` carrega quatro inconsistências que apareceram durante a fase Pong/Velha e que ficam mais arriscadas à medida que jogos maiores forem aparecendo:

1. `Transform.rotation` e `scale` são declarados, mas `Node2D.worldPosition()` e `BoxCollider.bounds()` os ignoram — a API promete composição hierárquica que não acontece.
2. `Scene.render()` consulta `Debug.colliderVisualization` e desenha contornos inline, invertendo a direção de dependência (core consultando camada de DX).
3. `Node.rootScene()` faz um walk linear toda frame para alcançar a `Scene` e ler `input` — barato hoje, mas o padrão se repete em todo node que precisa de input.
4. `Scene.traverseUpdate/Render` itera diretamente sobre `node.children` enquanto `onUpdate`/`onCollide` podem chamar `addChild`/`removeChild`. Hoje nenhum jogo de exemplo dispara `ConcurrentModificationException` por sorte; o primeiro spawner explode.

A change limpa esses quatro pontos como uma unidade ("engine-consistency") antes que decisões maiores — `fixed-timestep`, `collision lifecycle`, `renderer transforms` — caiam em cima de um terreno torto.

## What Changes

### A1 — Transform composition completo
- Adicionar `Node2D.worldTransform(): Transform` que compõe `position`, `scale` e `rotation` pela cadeia de ancestrais `Node2D`.
- `Node2D.worldPosition()` passa a delegar a `worldTransform().position`.
- `BoxCollider.bounds()` honra o `scale` herdado; quando há rotação no caminho, retorna o AABB do OBB resultante (loose mas correto).
- `Shape.onRender` aplica `scale` herdado. Rotação visual fica adiada para a futura change de `Renderer.withTransform`; `Shape` documenta a limitação.

### A2 — Desacoplar Scene de Debug
- `Scene.render()` deixa de consultar `Debug.colliderVisualization` e de desenhar contornos de colliders.
- A responsabilidade de aplicar o overlay de colliders migra para o runtime que pilota o `GameLoop` (em `:engine-compose`, o `GameSurface`), preservando a UX da tecla F2.
- `:engine.scene.Scene` deixa de importar de `com.neoutils.engine.dx.*`.

### A3 — Cache de scene no Node
- `Node` ganha um campo `scene: Scene?` populado em `attachToLiveTree` e zerado em `detachFromLiveTree`.
- `rootScene()` passa a retornar esse campo em O(1) e mantém a assinatura atual.

### A4 — Mutação segura durante traversal
- `Node` ganha filas `pendingAdd` / `pendingRemove`.
- `addChild` / `removeChild` durante traversal de update, physics ou render são enfileirados em vez de mutar a lista de filhos diretamente.
- As filas são drenadas em pontos determinísticos do tick (ordem travada em `design.md`).
- Chamadas fora de traversal continuam aplicando imediatamente, como hoje.

### Módulo `:games:demos` (validação visual)
- Novo módulo Compose Desktop espelhando o padrão de `:games:pong`, com três cenas (`TransformOrbitDemo`, `ScaleHierarchyDemo`, `SpawnerDemo`) escolhidas em tempo de execução via teclas `1`/`2`/`3`.
- As demos exercitam respectivamente: composição de rotação sobre posição (A1), composição de scale via `Shape.onRender` (A1), e mutação durante `onUpdate`/`onCollide` com overlay F2 ativo (A4 + A2).

### Smoke tests (red→green) que nascem com a change
- `SceneMutationDuringTraversalTest` (A4): cobre `addChild`/`removeChild` dentro de `onUpdate` e dentro de `onCollide`, com assert de não-CME e de aplicação na próxima fase.
- `WorldTransformTest` (A1): cobre `worldTransform()` para combinações de translação, scale e rotação aninhadas.
- Teste leve para A2: garante que `:engine.scene.Scene` não importa nada de `com.neoutils.engine.dx`.
- A3 não ganha teste isolado — fica coberto indiretamente pelos demais.

### Fora do escopo (explícito)
- `Renderer.pushTransform`/`popTransform` e rotação visual de `Shape` — pertencem à Thread D (renderer transforms + texturas).
- Layers/masks de colisão e `onCollisionEnter/Stay/Exit` — pertencem à Thread B.
- Fixed-timestep e desacoplamento simulação/render — pertencem à Thread C.

## Capabilities

### New Capabilities

_(nenhuma)_

### Modified Capabilities

- `engine-core`: composição de transform por ancestralidade; `Node.rootScene()` em O(1); mutação segura durante traversal; `Scene.render` deixa de consultar a camada DX.
- `dx-tooling`: o overlay de colliders deixa de ser desenhado por `Scene` — a flag continua no `Debug`, mas a responsabilidade de desenhar passa ao runtime.
- `compose-runtime`: o `GameSurface` passa a aplicar o overlay de colliders quando `Debug.colliderVisualization` está ativo.

## Impact

**Código no `:engine`:**
- `scene/Node.kt` — campo `scene`, filas `pendingAdd/pendingRemove`, `applyPending()` interno; `rootScene()` passa a ler o campo.
- `scene/Node2D.kt` — `worldTransform()` novo; `worldPosition()` delega.
- `scene/Scene.kt` — drena filas em pontos determinísticos; remove import e uso de `dx.Debug`; remove `drawColliderBounds`.
- `scene/Shape.kt` — usa scale herdado via `worldTransform()`.
- `physics/BoxCollider.kt` — `bounds()` usa `worldTransform()`.
- `math/Transform.kt` — método/utilitário para compor dois `Transform`s (matemática limpa).

**Código no `:engine-compose`:**
- `GameSurface.kt` — após `loop.tick`, desenha contornos dos colliders ativos quando `Debug.colliderVisualization` for `true`.

**Jogos (`:games:pong`, `:games:tictactoe`):**
- Sem mudança de API esperada. `Paddle`/`Board` continuam usando `rootScene()?.input` (mesma assinatura, agora barata).
- Pong não usa rotação nem scale, então `BoxCollider.bounds()` retorna o mesmo `Rect` de hoje — comportamento de jogo preservado.

**Testes:**
- Novos: `WorldTransformTest`, `SceneMutationDuringTraversalTest`, `SceneCoreDecouplingTest` (A2).
- Existentes: `NodeTest`, `PhysicsSystemTest`, `GameLoopTest` revisitados para garantir que continuam verdes com o novo `applyPending`.

**Documentação:**
- `CLAUDE.md` ganha entrada na tabela do roadmap (status Active → Archived após `/opsx:archive`).
- `Shape.kt` documenta que a rotação visual ainda não é aplicada.
- `Scene.kt` documenta a ordem de drenagem das filas.

**Dependências externas / Gradle:** nenhuma.
