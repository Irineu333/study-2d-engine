## Why

A engine mostra FPS, colliders, momento e contatos, mas não responde a
pergunta mais básica sobre o scene graph estilo Godot (invariante #1): "esse
objeto que eu vejo desenhado é qual node, e qual o transform/propriedades
dele agora?". A tentativa anterior (`debug-scene-inspector`, descartada no
commit `38521c7`) era uma lista de texto da árvore — "limitada demais"; o
veredito foi pivotar para **world-space picking** (clicar no objeto). A change
`node-local-bounds` já entregou de propósito os primitivos para isto
(`localBounds`/`worldBounds`, `Transform.apply`, `Rect.corners`). Esta change
é o consumidor visual que fecha esse loop — o scene graph como **dado
espacial visto**, não como código.

## What Changes

- **`Transform.applyInverse(p: Vec2): Vec2`** (novo) — espelho exato de
  `Transform.apply`, para levar um ponto world ao frame local de um node
  (`q = p - position; q = rotate(q, -rotation); q = q / scale`). Único
  primitivo de math novo; todo o resto já existe.
- **`SceneTree.hitTestPick(input)`** (novo) — passo de hit-test de pick
  chamado pelo `GameLoop.tick` logo **após** `hitTestUI` e **antes** de
  `tree.process`, gated em `scenePicker.enabled`. Faz o DFS de candidatos,
  resolve front-most + ciclagem, atualiza a seleção e, quando habilitado,
  **consome o clique esquerdo** (`mouseClickConsumed = true`) para pré-emptar
  o gameplay.
- **`ScenePickerWidget`** (novo `ScreenDebugWidget`, built-in) — guarda a
  seleção (por identidade de instância), desenha um breadcrumb do caminho
  na árvore (root→selecionado) e o painel de propriedades `@Inspect` +
  transform world, em pixels de tela. Limpa a seleção quando o node deixa
  de estar `isLive`/na árvore.
- **`SelectionGizmoWidget`** (novo `WorldDebugWidget`) — desenha a caixa
  **OBB** justa do node selecionado (4 cantos de `localBounds` via
  `world().apply`), seguindo a `Camera2D` no world pass.
- **Hit-test OBB justo** — o pick leva o clique-world ao frame local
  (`world().applyInverse`) e testa contra `localBounds()`; `worldBounds()`
  (AABB) serve só de broad-phase barato. Precisão sob rotação é requisito
  (exercitado por `RotatingBoxDemo`/`TumblingSwarmDemo`).
- **`inspectProperties(node): List<InspectEntry>`** (novo helper público em
  `com.neoutils.engine.serialization`) — enumera as `@Inspect` de um node
  (`memberProperties` + `findAnnotation<Inspect>()` + getter), reusando o
  padrão hoje privado no `SceneLoader` (que permanece intacto).
- **Integração `DebugRegistry`/`DebugHud`** — `scenePicker` e
  `selectionGizmo` como built-ins auto-inseridos (default `enabled = false`),
  com rows togglávies no HUD; custo zero quando desabilitados.

## Capabilities

### New Capabilities
- `debug-scene-picker`: world-space picking de nodes (hit-test OBB,
  front-most + ciclagem por nodes empilhados), o `SceneTree.hitTestPick` que
  rouba o clique quando ativo, os widgets `ScenePickerWidget` (breadcrumb +
  painel) e `SelectionGizmoWidget` (OBB highlight), o helper público
  `inspectProperties`, e o registro como built-ins.

### Modified Capabilities
- `engine-core`: nova superfície `Transform.applyInverse` e o passo
  `SceneTree.hitTestPick` no pipeline do `GameLoop.tick` (análogo ao
  `hitTestUI` já existente).

## Impact

- **Affected specs:** `debug-scene-picker` (nova), `engine-core` (modificada).
- **Affected code:**
  - `:engine` `com.neoutils.engine.math` — `Transform.applyInverse`.
  - `:engine` `com.neoutils.engine.tree` — `SceneTree.hitTestPick`; chamada
    no `GameLoop.tick`.
  - `:engine` `com.neoutils.engine.debug` — `ScenePickerWidget`,
    `SelectionGizmoWidget`, campos no `DebugRegistry`, rows no `DebugHud`.
  - `:engine` `com.neoutils.engine.serialization` — helper `inspectProperties`.
- **Dependência:** nenhuma nova (`kotlin-reflect` já é `api` em `:engine`).
- **Invariantes:** #1 (picking seleciona nodes, não muta a árvore), #4
  (`GameHost` não toca debug por frame — picker vive em nodes internos da
  `DebugLayer`; `hitTestPick` é engine-internal no loop, como `hitTestUI`),
  #6 (`hitTestPick` pula subárvores de `CanvasLayer`).
- **Não-objetivos (MVP):** edição de propriedades (read-only); watch de
  `@Transient`/runtime (`linearVelocity` — `VelocityGizmo` cobre velocidade);
  lista de árvore clicável completa (só breadcrumb); picking de
  UI/`CanvasLayer`; marquee/multi-seleção; zoom-to-fit; IDs estáveis entre
  re-attach.
