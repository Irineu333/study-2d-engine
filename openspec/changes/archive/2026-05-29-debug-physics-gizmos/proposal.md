## Why

A engine existe para **ensinar arquitetura de engine**, e o subsistema mais
rico — a física — é o mais opaco no debug atual. O único gizmo de colisão
hoje (`ColliderWidget`) desenha apenas o **AABB** de cada shape: esconde a
forma real (um círculo e um retângulo rotacionado viram a mesma caixa), os
**pontos de contato** e **normais** que o solver de impulso realmente usou,
e os **vetores de velocidade** dos corpos. O `PhysicsSystem` computa tudo
isso internamente (`SweepResult.point`/`.normal`, `RigidBody2D.linearVelocity`,
os cantos OBB via `obbCorners`) — mas descarta ou não expõe. Esta change
expõe o que a física já calcula, transformando o passo de colisão de caixa-
preta em algo observável.

## What Changes

- **`ShapeGizmoWidget`** (novo `WorldDebugWidget`): desenha a **geometria
  real** de cada `CollisionShape2D` ativo — contorno do círculo para
  `CircleShape2D`, os 4 cantos world (OBB) para `RectangleShape2D`
  rotacionado — em vez do AABB. O `ColliderWidget` (AABB) permanece como
  está: AABB é o que o broad-phase usa, didaticamente válido manter os dois.
- **`VelocityGizmoWidget`** (novo `WorldDebugWidget`): para cada
  `RigidBody2D`/`CharacterBody2D` ativo, desenha uma seta da posição do
  corpo ao longo da velocidade linear (escala configurável). Velocidade
  angular fica como menção futura.
- **Gravação de contatos no `PhysicsSystem`**: quando a gravação de
  contatos está habilitada, `PhysicsSystem.step` registra os
  `(point, normal)` resolvidos no último step num buffer por-`SceneTree`
  (limpo no início do step, preenchido durante a resolução). Custo zero
  quando desabilitada.
- **`ContactGizmoWidget`** (novo `WorldDebugWidget`): desenha cada contato
  gravado como um ponto + uma seta de normal. Seu `enabled` liga/desliga a
  gravação no `PhysicsSystem` (sem gravação fora do debug).
- **Integração com a `DebugRegistry`**: os três widgets viram built-ins
  auto-inseridos e aparecem como rows togglável no `DebugHud`.

## Capabilities

### New Capabilities

- `debug-physics-gizmos`: os três widgets de física (`ShapeGizmoWidget`,
  `VelocityGizmoWidget`, `ContactGizmoWidget`), o buffer de contatos
  por-`SceneTree`, o seam de gravação gated no `PhysicsSystem.step`, e a
  exposição/registro como built-ins. Inclui o helper público de cantos
  world em `RectangleShape2D`.

### Modified Capabilities
<!-- Nenhuma. Mesmo padrão da debug-immediate-draw: a integração com
     debug-overlay (novos built-ins, rows no HUD) é descrita como
     requirements ADICIONADAS na nova capability, sem tocar as requirements
     que debug-log-overlay já altera. O ColliderWidget (AABB) NÃO é
     modificado — os gizmos de forma real são widgets novos e adicionais.
     A gravação de contatos é descrita como requirement nova desta
     capability, sem modificar as specs de rigid-body-2d / kinematic. -->

## Impact

- **Código afetado:**
  - `:engine` `com.neoutils.engine.debug` — `ShapeGizmoWidget`,
    `VelocityGizmoWidget`, `ContactGizmoWidget`; campos no `DebugRegistry`.
  - `:engine` `com.neoutils.engine.physics.Shape2D` — expor um helper
    público de cantos world para `RectangleShape2D` (hoje `obbCorners` é
    privado).
  - `:engine` `com.neoutils.engine.physics.PhysicsSystem` — gravar contatos
    no buffer por-tree quando a gravação está habilitada (early-out quando
    não). Buffer reside no `tree.debug` (mesma superfície que o `step` já
    alcança via o argumento `tree`).
- **Não depende de `debug-immediate-draw`.** Decisão revista (ver design):
  contatos exigem *captura* durante o TOI loop, não só desenho; um buffer
  dedicado é mais limpo e torna a change auto-contida. immediate-draw
  permanece útil independentemente para gizmos ad-hoc de game/script.
- **Custo em produção:** zero. Sem widget habilitado, nenhum gizmo desenha
  e a gravação de contatos não roda (gated pelo `enabled` do
  `ContactGizmoWidget`).
- **Testes:** contorno real (círculo, rect rotacionado) vs AABB; setas de
  velocidade; gravação de contatos só quando habilitada e limpa por step;
  os três widgets como built-ins e rows no HUD; gravação não roda quando
  desabilitada. Sem novas dependências externas.
