## Why

O HUD de debug acumulou **12 toggles**, e parte deles ensina a mesma lição ou
quase nunca é usada. Numa engine didática isso é ruído: `Colliders` (AABB) e
`Shapes` (geometria real) duplicam a família "colliders", `FPS` é um
subconjunto estrito do que o `Profiler` já imprime (`fps ≈ 1000/total`), e
`Momentum` (Σp/ΣL/ΣKE) só tem sentido com `RigidBody2D` — inerte na maioria
dos jogos. Enxugar o catálogo torna o HUD legível e cada ferramenta restante
ganha um propósito distinto.

## What Changes

- **Fundir `ColliderWidget` + `ShapeGizmoWidget`** num único `ColliderWidget`
  (world gizmo) com um modo `enum ColliderDrawMode { AABB, REAL }`,
  default `REAL`. Um segmented control no painel companheiro escolhe entre os
  dois modos. O `ShapeGizmoWidget` e o campo `DebugRegistry.shapeGizmo` deixam
  de existir. Resultado: 1 toggle cobre tanto a forma real quanto o envelope do
  broad-phase.
- **Fundir `FpsWidget` no `ProfilerWidget`**: o Profiler ganha uma linha
  `fps NN` no topo, amostrada de forma barata via `nanoTime` (como o
  `FpsCounter` já fazia) — **não** depende da instrumentação pesada do
  `GameLoop`, então o fps aparece assim que o painel abre, mesmo antes de
  qualquer medição de fase. O `FpsWidget` e o campo `DebugRegistry.fps` deixam
  de existir.
- **Remover `MomentumWidget`** e o campo `DebugRegistry.momentum`. Os helpers
  de física `totalLinearMomentum` / `totalAngularMomentum` / `totalKineticEnergy`
  (em `MomentumDiagnostics.kt`) **permanecem** como API pública — são utilitários
  válidos por si, apenas perdem o widget que os consumia.
- **`Debug Draw`** (toggle do immediate-mode facade) fica **intocado**.
- **BREAKING (interno)** `DebugRegistry` perde os campos `fps`, `momentum` e
  `shapeGizmo`; o catálogo de built-ins registrados em `DebugLayer` cai de 12
  para 9 widgets. Sem mudança na API pública dos jogos (nenhum jogo shipped
  referencia esses campos).

Líquido: o HUD cai de **12 → 8** linhas togláveis (Colliders, Velocity,
Contacts, Time, Profiler, Log, Picker, Debug Draw — mais o próprio Debug HUD).

## Capabilities

### New Capabilities

(nenhuma — esta change só consolida e remove visualizações existentes)

### Modified Capabilities

- `debug-overlay`: `DebugRegistry` perde os campos `fps` e `momentum` e ganha
  semântica de modo no `colliders`; o auto-insert de `DebugLayer` deixa de
  registrar `FpsWidget` e `MomentumWidget`; a requirement "MomentumWidget owns
  its ring buffer" é **removida**; a requirement do `ColliderWidget` passa a
  cobrir o modo `{AABB, REAL}` (incorporando o desenho de geometria real),
  selecionado por um painel companheiro `ColliderModePanel`.
- `debug-profiler`: `ProfilerWidget` passa a exibir uma linha `fps`,
  amostrada independente da instrumentação de fases.
- `debug-physics-gizmos`: a requirement "ShapeGizmoWidget draws real collider
  geometry" é **removida** (comportamento absorvido pelo `ColliderWidget`); a
  requirement "Physics gizmos are registered built-ins" passa a listar apenas
  `VelocityGizmoWidget` e `ContactGizmoWidget`.

## Impact

- `:engine` — `com.neoutils.engine.debug`: `ColliderWidget` (ganha modo),
  `ProfilerWidget` (ganha fps), `DebugRegistry` (perde 3 campos), `DebugLayer`
  (ordem de registro), remoção de `FpsWidget`/`ShapeGizmoWidget`/`MomentumWidget`.
  `FpsCounter` migra para uso interno do `ProfilerWidget`.
- `com.neoutils.engine.physics`: `MomentumDiagnostics.kt` permanece (API
  pública), só perde seu único consumidor interno.
- Documentação: `CLAUDE.md` invariante #6 cita `fps/colliders/momentum/hud`
  por nome — atualizar para o catálogo novo. `README.md`/`ROADMAP.md` na seção
  de debug.
- Testes: ~18 referências a `FpsWidget`/`MomentumWidget`/`ShapeGizmoWidget` em
  `DebugRegistryTest`, `DebugLayerTest`, `BuiltinWidgetsTest`,
  `PhysicsGizmosTest` a reescrever/remover; novos testes para o modo do
  `ColliderWidget` e a linha de fps do `ProfilerWidget`.
- Coexiste com a change ativa `debug-ui-z-order` (toca `DebugRegistry` e
  `ScreenDebugCanvas` num eixo diferente — z-order, não catálogo): sem colisão
  real de código, mas convém alinhar a ordem de merge.
