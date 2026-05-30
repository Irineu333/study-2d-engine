## Why

O `PhysicsContactBuffer` grava os contatos no **frame do pai do corpo** — tanto
o caminho `RigidBody2D` (o `SweepResult` que `PhysicsSystem.advanceAndResolve`
passa a `contactSink.append`) quanto o caminho `CharacterBody2D` (o
`KinematicCollision2D` que `moveAndCollide` faz `stage`). Mas o
`ContactGizmoWidget` desenha em **world pass**. Para corpos top-level (Pong,
Snake) o frame do pai coincide com world e o gizmo acerta; para corpos
**aninhados** (as bolas da demo 5 dentro do `RotatingBox` que gira) os
marcadores aparecem deslocados e girando, porque coordenadas do frame da caixa
são interpretadas como world. O próprio spec de `debug-physics-gizmos` já
**afirma** "world-space `point` and unit `normal`" — uma garantia que a
implementação só cumpre para corpos top-level. Esta é a normalização
world-space registrada como "trabalho futuro" nos Non-Goals de
`debug-physics-gizmos` e `debug-kinematic-contacts`.

## What Changes

- **Os contatos passam a ser gravados em world-space, nos dois caminhos.** No
  ponto de gravação, o `point` é transformado do frame do pai do corpo para
  world (compondo com o `world()` do pai) e o `normal` é rotacionado pela
  rotação world do pai, permanecendo unitário. Para corpos top-level o pai é o
  root (sem rotação/translação relevante) e a transformação é identidade — o
  comportamento de Pong/Snake não muda.
- **Um helper único de normalização** converte `(point, normal)` do frame do
  pai para world, usado tanto por `CharacterBody2D.moveAndCollide` (antes do
  `stage`) quanto por `PhysicsSystem.advanceAndResolve` (antes do `append`),
  para que os dois caminhos fiquem consistentes e não divirjam de novo.
- **Sem novos widgets, sem mudança de UI, sem mudança de gating.** O
  `ContactGizmoWidget` existente passa a desenhar certo para corpos aninhados
  porque o buffer agora carrega world-space. Custo zero quando a gravação está
  off (a normalização só roda dentro do caminho de gravação já gated).

## Capabilities

### New Capabilities

- `debug-worldspace-contacts`: a garantia de que todo `ContactRecord` no
  `PhysicsContactBuffer` está em world-space, independentemente do aninhamento
  do corpo, normalizando `point`/`normal` do frame do pai para world no ponto
  de gravação — cobrindo o caminho `RigidBody2D` e o caminho `CharacterBody2D`
  com o mesmo helper, de modo que o `ContactGizmoWidget` (world pass) desenhe
  corretamente para corpos top-level e aninhados.

### Modified Capabilities
<!-- Nenhuma. Mesma estratégia aditiva de debug-immediate-draw / debug-kinematic-contacts:
     a garantia world-space é descrita como requirements ADICIONADAS na nova
     capability. O spec de debug-physics-gizmos já declara "world-space point";
     esta change torna isso verdade para corpos aninhados e estende ao caminho
     kinematic, sem reescrever a requirement existente (e debug-kinematic-contacts
     ainda não está arquivada — evita um delta MODIFIED frágil). -->

## Impact

- **Código afetado:**
  - `:engine` `com.neoutils.engine.physics` — um helper de normalização
    `(point, normal)` parent-frame → world (provavelmente em `PhysicsSystem`
    ou utilitário do pacote `physics`), reusando `Transform.compose` e o
    `rotate` do pacote `math`.
  - `:engine` `com.neoutils.engine.physics.CharacterBody2D.moveAndCollide` —
    normaliza antes do `tree.debug.contacts.stage(...)`.
  - `:engine` `com.neoutils.engine.physics.PhysicsSystem.advanceAndResolve` —
    normaliza antes do `contactSink.append(...)`.
- **Frame de coordenadas:** após a change, o buffer é canonicamente
  world-space. Para corpos top-level a transformação é identidade (Pong, Snake
  inalterados); para aninhados (demo 5) o marcador passa a acompanhar a bola
  dentro da caixa girando.
- **Escala não-uniforme:** a normal é tratada por rotação (correta sob rotação
  e escala uniforme). Escala não-uniforme distorceria a direção da normal —
  fora do escopo; os corpos com colisão nos demos/jogos não usam escala
  não-uniforme. Documentado como limitação.
- **Custo em produção:** zero. A normalização só roda dentro do caminho de
  gravação, que já é gated por `ContactGizmoWidget.enabled`.
- **Testes:** contato de corpo aninhado (pai rotacionado/transladado) sai em
  world-space nos dois caminhos; contato de corpo top-level permanece idêntico
  (transformação identidade); a suíte de `debug-physics-gizmos` /
  `debug-kinematic-contacts` continua verde.
