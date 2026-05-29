## Why

O `ContactGizmoWidget` (da change `debug-physics-gizmos`) só mostra os
contatos que o solver de impulso resolve dentro de `PhysicsSystem.step` —
ou seja, **apenas colisões de `RigidBody2D`**. Mas a maior parte do gameplay
shipped é cinemática: a bola do Pong, a cobra do Snake, paddles, naves — tudo
`CharacterBody2D` resolvendo colisão via `moveAndCollide` chamado pelo script,
fora do `step`. Resultado: no Pong (que **não tem nenhum `RigidBody2D`**) o
gizmo de contato nunca acende. Para uma ferramenta cujo propósito é tornar a
física observável, ignorar o modelo de corpo mais usado é um ponto cego.

## What Changes

- **`CharacterBody2D.moveAndCollide` grava o contato resolvido** (o `point` e
  o `normal` do `KinematicCollision2D` que ele já computa) no
  `PhysicsContactBuffer` per-tree, quando a gravação está habilitada — o mesmo
  buffer e o mesmo gating (`ContactGizmoWidget.enabled`) que os contatos de
  `RigidBody2D` já usam. Custo zero quando desabilitado (early-out).
- **O contato fica "staged" até o `step` do mesmo substep o consolidar.**
  Como `moveAndCollide` roda no `_physics_process` (antes do `step`, que limpa
  o buffer no início), o contato cinemático é gravado numa área de staging que
  o `PhysicsSystem.step` **dobra** no buffer ao limpá-lo/repopulá-lo — assim
  ele sobrevive até o render sem que o clear do `step` o apague. A semântica
  atual ("`step` limpa no início e grava cada contato resolvido") permanece;
  esta change só **adiciona** o fold dos contatos staged.
- **Sem novos widgets, sem mudança de UI.** O `ContactGizmoWidget` existente
  passa a desenhar os contatos cinemáticos automaticamente, porque eles agora
  chegam ao buffer que ele já lê.

## Capabilities

### New Capabilities

- `debug-kinematic-contacts`: a gravação dos contatos resolvidos pelo
  `CharacterBody2D.moveAndCollide` no `PhysicsContactBuffer` (staging +
  consolidação no `step`), gated pelo mesmo `ContactGizmoWidget.enabled`,
  fazendo o gizmo de contato cobrir os dois modelos de corpo.

### Modified Capabilities
<!-- Nenhuma. Mesmo padrão de debug-immediate-draw / debug-physics-gizmos:
     a extensão é descrita como requirements ADICIONADAS na nova capability,
     sem tocar nas requirements que debug-physics-gizmos já define. O clear
     no início do `step` e a gravação de contatos de RigidBody2D continuam
     exatamente como estão; esta change só acrescenta o staging cinemático e
     o fold. Evita um delta MODIFIED contra um spec ainda não arquivado. -->

## Impact

- **Código afetado:**
  - `:engine` `com.neoutils.engine.physics.CharacterBody2D` — `moveAndCollide`
    grava (stage) o contato no `tree.debug.contacts` quando a gravação está on.
  - `:engine` `com.neoutils.engine.debug.PhysicsContactBuffer` — área de
    staging (`stage`/`takeStaged`) além dos `records` já existentes.
  - `:engine` `com.neoutils.engine.physics.PhysicsSystem.step` — ao limpar o
    buffer no início, consolida os contatos staged antes de gravar os de
    `RigidBody2D`.
- **Frame de coordenadas:** o `point`/`normal` são gravados no frame em que o
  `moveAndCollide` opera (o frame do pai), exatamente como o caminho de
  `RigidBody2D` já grava. Para corpos top-level (Pong, Snake) isso coincide
  com world e o gizmo desenha certo. Corpos aninhados (ex.: bolas da demo 5
  dentro da caixa girando) herdam a mesma limitação do caminho rigid — fora
  do escopo; uma normalização world-space futura cobriria os dois caminhos.
- **Custo em produção:** zero. Sem `ContactGizmoWidget.enabled`, nem o stage
  nem o fold rodam (early-out pelo flag de gravação).
- **Testes:** contato cinemático aparece no buffer só com gravação on; limpo
  por frame como os de rigid; Pong/cenário kinematic passa a popular o buffer;
  rigid + kinematic coexistem num mesmo step. Sem novas dependências externas.
