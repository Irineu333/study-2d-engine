## Why

O módulo `:games:demos` posiciona paredes de colisão a partir de `tree.size` apenas dentro de `onEnter`, antes do primeiro `render`. Quando o usuário redimensiona a janela depois do demo entrar na árvore, as paredes ficam congeladas no tamanho original — bolinhas escapam para a faixa nova exposta ou batem em barreiras invisíveis no perímetro antigo. O bug afeta demos `4 Collision stress` e `6 Tumbling swarm`. Demo `3 Spawner` já trata o caso via polling em `onProcess`, mas a lógica está duplicada manualmente em cada demo, e a função `private fun makeWall(position, size)` aparece copiada literal em três arquivos (demos 4, 5, 6). Resolver o resize sem unificar a forma de criar paredes deixaria a duplicação intacta.

## What Changes

- Adicionar nova classe `BoundaryWalls : Node2D` em `:games:demos` que encapsula 4 `StaticBody2D` (top/bottom/left/right) e se reposiciona/redimensiona automaticamente quando `tree.size` muda, via polling em `onPhysicsProcess`.
- Adicionar função utilitária top-level `makeStaticWall(position: Vec2, size: Vec2): StaticBody2D` no mesmo arquivo, expondo a construção canônica de parede estática para demos que precisam de paredes em frame local (caso do demo 5).
- Migrar `CollisionStressDemo` (demo 4) e `TumblingSwarmDemo` (demo 6) para usar `BoundaryWalls` — remove o `private fun makeWall` duplicado e as 4 chamadas manuais em `onEnter`.
- Migrar `RotatingBoxDemo` (demo 5) para usar `makeStaticWall` no lugar do seu `private fun makeWall` — refator DRY sem mudança de comportamento (paredes do demo 5 vivem no frame local do wrapper rotativo e não devem reagir a resize).
- Documentar o comportamento resize-aware nas seções dos demos 4 e 6 em `CLAUDE.md`.

## Capabilities

### New Capabilities

- `demos-sample`: cobre o módulo `:games:demos` como sample executável que demonstra invariantes da engine (consistência de transform, mutation durante traversal, kinematic CCD, composição de rotação em ancestrais, impulso angular). Esta change introduz a capability já com o requisito de paredes de fronteira resize-aware, pois é a primeira vez que demos-sample ganha contrato formal de comportamento.

### Modified Capabilities

(nenhuma — `:engine` não é tocado nessa change.)

## Impact

- **Código tocado**: `:games:demos` apenas (novo arquivo `BoundaryWalls.kt`; edição de `CollisionStressDemo.kt`, `TumblingSwarmDemo.kt`, `RotatingBoxDemo.kt`).
- **Documentação**: `CLAUDE.md` recebe nota nas descrições dos demos 4 e 6 mencionando que as paredes acompanham a janela.
- **APIs públicas**: nenhuma mudança em `:engine`. `:games:demos` não exporta API pública para fora do módulo.
- **Dependências**: zero. Polling de `tree.size` reusa infraestrutura existente (`SceneTree.size` é atualizada pelo host por frame).
- **Riscos**: mutar `RectangleShape2D.size` em runtime — o broad phase do `PhysicsSystem` recomputa AABBs por frame (não cacheia), portanto a mutação é segura. Bolinhas que ficam temporariamente penetrando uma parede recém-encolhida saem no próximo `moveAndCollide` via reflect normal; o sweep tolera overlap inicial.
