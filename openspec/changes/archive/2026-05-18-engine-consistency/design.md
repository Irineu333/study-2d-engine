## Context

A fundação `engine-foundation` deixou o scene graph estável o bastante para dois jogos (Pong, Velha) mas com quatro arestas que só não geram bug porque os jogos atuais não exercitam: rotação/scale herdados, acoplamento `Scene → Debug`, walk linear para alcançar a `Scene`, e mutação durante traversal. Nenhuma delas é descoberta nova — todas surgiram durante a exploração que precedeu esta change.

Esta change consolida as quatro correções como **uma unidade de "limpeza"** antes de evoluções maiores (Thread B/C/D na nomenclatura do explore). É deliberadamente conservadora: cada decisão escolhe a alternativa que mais limita a área de mudança, preservando os invariantes arquiteturais do `CLAUDE.md`.

**Stakeholders:** o próprio repo (didático). Os "consumidores" da API são os dois jogos existentes; ambos devem continuar rodando sem mudança visível.

## Goals / Non-Goals

**Goals:**

- Eliminar a divergência entre o que `Transform` promete (position+scale+rotation) e o que `Node2D.worldPosition()` / `BoxCollider.bounds()` / `Shape.onRender` cumprem.
- Tornar o uso de `addChild`/`removeChild` durante `onUpdate`/`onCollide` seguro e determinístico.
- Remover o acoplamento `engine.scene.Scene` → `engine.dx.Debug`.
- Tornar `Node.rootScene()` O(1) sem mudar a assinatura pública.
- Cobrir A1 e A4 com smoke tests que **falham hoje** e **passam após a change**.

**Non-Goals:**

- **Não** introduzir `Renderer.withTransform`/`pushTransform`/`popTransform` — fica para Thread D. `Shape` não renderiza rotação visual nesta change.
- **Não** introduzir layers/masks ou `onCollisionEnter/Stay/Exit` — Thread B.
- **Não** introduzir fixed-timestep — Thread C.
- **Não** mudar a assinatura pública de `Node.addChild`/`removeChild` (continuam síncronas em código fora de traversal).
- **Não** mudar `BoxCollider` para OBB de verdade. Quando há rotação no caminho, devolvemos o AABB do OBB resultante (frouxo mas correto e suficiente para Pong/Velha).
- **Não** introduzir matrizes 3×3 ou álgebra linear formal — composição de `Transform` é matemática 2D direta sobre os três campos.

## Decisions

### D1 — Composição de transform é matemática direta sobre `Transform`, não matriz

`Transform.compose(parent: Transform): Transform` aplica a regra clássica TRS local em ordem pai-aplica-filho:

```
worldScale    = parent.scale .* child.scale
worldRotation = parent.rotation + child.rotation
worldPos      = parent.position + rotate(parent.scale .* child.position, parent.rotation)
```

`Node2D.worldTransform()` faz walk do ancestral mais alto até `this`, dobrando via `compose`. `worldPosition()` passa a ser `worldTransform().position`.

**Por que não matriz 3×3?** Engine didática, 2D apenas, sem cisalhamento — três `Float`s carregam toda a informação que precisamos. Matriz só seria útil quando o renderer suportar `pushTransform` (Thread D); aí migramos.

**Alternativa considerada:** cachear `worldTransform` por node com invalidação on-dirty. Rejeitado porque a invalidação correta exige observar mutação de pais e de transform local — explosão de complexidade desproporcional. O walk é O(profundidade), e a profundidade das cenas atuais é ≤ 3. Quando virar gargalo, a thread certa é a do editor (que muda como cenas são compostas).

### D2 — Walk do ancestral mais alto até `this`, não de `this` até a raiz

A versão atual de `worldPosition()` soma a partir de `this`, subindo via `parent`. Isso só funciona pra translação pura. Para rotação/scale, é necessário **descer** do ancestral mais alto aplicando `compose` em cascata, porque a rotação do pai gira o sistema de coordenadas do filho.

Implementação: empilha a cadeia em uma lista local (alocação de ~3 entradas em cenas reais), itera de cima para baixo. Trade-off aceitável para o ganho de correção.

### D3 — `BoxCollider.bounds()` devolve o AABB do OBB resultante

Quando `worldTransform()` inclui rotação ≠ 0, a "caixa" girada não é mais axis-aligned. Três opções:

| Alternativa | Veredito |
|---|---|
| Ignorar rotação no collider | Mantém a inconsistência atual — rejeitado, contradiz A1. |
| Virar `BoxCollider` em OBB e atualizar `PhysicsSystem` | Toca SAT, broad-phase, drawing de bounds — muito além desta change. Pertence à Thread B. |
| Calcular OBB e devolver seu AABB | **Adotado.** Conservador: cobre mais que o necessário (potencial falso positivo nas bordas em alta rotação), mas o `PhysicsSystem` continua AABB-AABB, e o overlay desenha um retângulo coerente. |

`PhysicsSystem` continua intocado. `BoxCollider` ganha responsabilidade de devolver bounds que respeitam o transform mundial.

### D4 — Pontos de drenagem das filas: antes de update, antes de physics, antes de render

A fila pendente é drenada em três pontos por tick, sempre na sequência `pendingRemove → pendingAdd`:

```
GameLoop.tick:
  scene.start() if needed       ← drena já-existentes (não é traversal)
  scene.input = input
  scene.applyPending()          ← drain 1
  scene.update(dt)              ← onUpdate pode enfileirar
  scene.applyPending()          ← drain 2
  physics.step(scene)           ← onCollide pode enfileirar
  scene.applyPending()          ← drain 3
  scene.render(renderer)        ← onRender NÃO deve enfileirar (regra)
```

Onde `Scene.applyPending()` é traversal próprio que chama `node.applyPending()` em pós-ordem (filhos primeiro), aplicando removes antes de adds para evitar ressuscitar nó já removido.

**Por que três drenagens em vez de uma só no fim?** Para que comportamento no mesmo tick seja determinístico e fácil de raciocinar: o que foi enfileirado em `onUpdate` está vivo durante `physics.step`; o que foi enfileirado em `onCollide` está vivo durante `render`. Drenagem única no fim adiaria por um frame e quebraria o "spawnou na update, vê no render" intuitivo.

**Trade-off:** três traversals por tick. Em cenas pequenas é irrelevante; a alternativa de drain único é mais barata mas menos intuitiva.

**Adição síncrona quando fora de traversal:** `addChild`/`removeChild` mantém efeito imediato quando chamado fora de `onUpdate`/`onRender`/`onCollide`. Sinalizamos "estou dentro de traversal" com uma flag `inTraversal: Boolean` em `Scene`. Quando `false`, mutação é direta (atual comportamento). Quando `true`, vai para fila.

### D5 — Cache `scene: Scene?` em `Node`, populado no attach

`Node.scene: Scene?` é zerado por padrão; `attachToLiveTree` propaga `this.scene = owningScene` antes de chamar `onEnter()`; `detachFromLiveTree` zera depois de `onExit()`. `rootScene()` passa a ser um simples `return scene`, preservando assinatura.

**Alternativa considerada:** lazy-cache via `lazy { walkUp() }`. Rejeitado porque o nó é "vivo" e muda de scene; a invalidação ficaria implícita. Setar no attach/detach é direto, segue exatamente o mesmo lifecycle que `isLive`, e a invalidação é trivial.

### D6 — Overlay de colliders migra para o runtime, não para o `GameLoop`

O `Debug.colliderVisualization` continua como flag global em `:engine` (parte do contract de `dx-tooling`). Quem **consome** a flag e desenha os contornos é o `GameSurface` em `:engine-compose`, após `loop.tick`, antes de soltar o `DrawScope`.

**Por que não no `GameLoop`?** Porque `GameLoop` recebe um `Renderer` da SPI e não tem visão do scene tree de colliders — a iteração que enumera colliders hoje vive em `Scene.drawColliderBounds`. Há duas saídas:

| Alternativa | Veredito |
|---|---|
| Mover o helper para `GameLoop` | Acopla `GameLoop` ao traversal de colliders e ao `Debug`. Não é horrível, mas espalha responsabilidade. |
| Mover o helper para `PhysicsSystem` e o `GameLoop` chama após render | Inverte a responsabilidade de `PhysicsSystem` (passa a desenhar). Rejeitado. |
| **Adotada:** runtime (`GameSurface`) consulta `Debug` e desenha após `loop.tick` | Mantém `:engine` puro: nenhum core component consulta `Debug`. O runtime é justamente a camada de integração de DX com a superfície gráfica. |

O helper de enumerar colliders pode virar utilitário público em `:engine` (`PhysicsSystem.colliders(scene)` ou `Scene.collectColliders()`) — decidir no `tasks.md`.

### D7 — `Shape` documenta a limitação de rotação visual

`Shape.onRender` aplica `worldTransform().scale` ao tamanho, mas não rotaciona o desenho. Adicionamos KDoc explícito: "rotation is composed into `worldTransform()` but not applied to drawing until `Renderer.withTransform` exists." Isso evita falsa expectativa quando o usuário rotaciona um `Shape` e nada gira na tela.

`Text` segue mesma regra — usa `worldPosition`, nada mais.

## Risks / Trade-offs

- **[Pong/Velha podem regredir visualmente]** → mitigado por: (a) `BoxCollider.bounds()` para transform identidade ou sem rotação retorna exatamente o mesmo `Rect` de hoje; (b) tests existentes (`PhysicsSystemTest`, `NodeTest`) continuam verdes; (c) smoke manual rodando ambos os jogos antes de marcar a change como concluída.

- **[OBB→AABB introduz falso positivo de colisão em rotação alta]** → aceito, documentado. Pong e Velha não rotacionam colliders. Quando Thread B chegar, OBB real fica trivial dada a infraestrutura desta change.

- **[Mutação durante render]** → política: `onRender` **não deve** chamar `addChild`/`removeChild`. Vamos detectar e logar (não crash) se acontecer. Isso evita inflar a complexidade do drain para um caso que não tem caso de uso.

- **[Walk top-down para `worldTransform` aloca]** → uma `ArrayDeque` curta por chamada. Em cenas atuais ≤ 3 entradas. Quando virar problema, viramos pra estratégia stack-allocated ou cache; nenhuma das duas precisa ser feita agora.

- **[Drenagem em 3 pontos altera fronteira de testes]** → tests existentes que dependiam de "child está lá no mesmo tick que foi adicionado" precisam ser revisitados. Inspeção mostra que `NodeTest` testa attach síncrono fora de traversal — fica preservado.

## Migration Plan

Não há migração externa: o repo inteiro evolui em lockstep com a change. Roteiro de implementação (detalhado em `tasks.md`):

1. **Red first.** Escrever `WorldTransformTest`, `SceneMutationDuringTraversalTest` e `SceneCoreDecouplingTest` antes de qualquer mudança de produção. Os três devem falhar.
2. **A3 primeiro** (cache de scene) — mudança menor, base para os outros.
3. **A1 em seguida** — `Transform.compose`, `Node2D.worldTransform`, atualizar `BoxCollider.bounds`, atualizar `Shape.onRender`.
4. **A4** — filas pendentes, `applyPending`, drain points no `GameLoop`.
5. **A2** — remover acoplamento Scene→Debug, mover overlay para `GameSurface`.
6. **Validação** — rodar Pong e Velha manualmente; ambos devem se comportar como hoje (incluindo `F2` para colliders).
7. **Atualizar** `CLAUDE.md` roadmap.

**Rollback:** cada item (A1–A4) é commit independente; reverter um sem perder os outros é trivial.

## Open Questions

Nenhuma. Trade-offs e alternativas foram travados no explore que precedeu esta change.
