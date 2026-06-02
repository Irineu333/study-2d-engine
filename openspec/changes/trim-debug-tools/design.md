## Context

O subsistema de debug tem 13 widgets built-in (12 toggles no HUD + o
`SelectionGizmoWidget` que é braço do picker). Três pares ensinam lições
redundantes ou nichadas:

- `ColliderWidget` (AABB do broad-phase, em `debug-overlay`) e
  `ShapeGizmoWidget` (geometria real, em `debug-physics-gizmos`) visualizam a
  mesma família "colliders" — dois toggles, dois widgets, dois nomes no HUD.
- `FpsWidget` imprime `fps NN`; o `ProfilerWidget` imprime `total = X ms`, do
  qual fps é `1000/total`. FPS é subconjunto estrito — mas o Profiler é caro
  (seu `enabled` liga a instrumentação do `GameLoop.tick`).
- `MomentumWidget` (Σp/ΣL/ΣKE) só produz dados úteis com `RigidBody2D`.

As decisões de catálogo (o quê fundir/remover) já foram tomadas em explore;
este design fixa **como** sem quebrar invariantes nem a API dos jogos.

## Goals / Non-Goals

**Goals:**

- Reduzir o HUD de 12 → 8 toggles sem perder nenhuma lição que tenha valor
  pedagógico único.
- Fundir Colliders+Shapes num widget com modo ciclável, preservando as três
  visões (AABB, forma real, ambas).
- Mover fps para dentro do Profiler sem acoplá-lo à instrumentação pesada.
- Manter a API pública de física (`MomentumDiagnostics`) intacta.

**Non-Goals:**

- Não mexer em `Debug Draw`, `Time`, `Log`, `Picker`, `Contacts`, `Velocity`.
- Não tocar no z-order de painéis (change `debug-ui-z-order`, eixo separado).
- Não introduzir um framework de "modo" genérico para widgets — o ciclo de
  modo do `ColliderWidget` é específico dele.

## Decisions

### 1. Fusão Colliders+Shapes: modo no `ColliderWidget`, não um novo tipo

`ColliderWidget` ganha `enum ColliderDrawMode { AABB, REAL, BOTH }` e um
`var mode: ColliderDrawMode = REAL`. `drawDebug` despacha:

- `AABB` → comportamento atual do `ColliderWidget` (`shape.broadPhaseBounds()`
  via `drawRect filled=false`).
- `REAL` → comportamento atual do `ShapeGizmoWidget` (outline de círculo /
  quad de `worldCorners`).
- `BOTH` → desenha os dois (AABB primeiro, geometria por cima).

**Default `REAL`**: é a forma que o jogador raramente vê e a mais informativa;
o AABB vira a visão "avançada" que explica o broad-phase.

**Por que no `ColliderWidget` e não num tipo novo:** o `ColliderWidget` já é o
nome canônico no `debug-overlay` e o campo `DebugRegistry.colliders` é estável.
Absorver a geometria real para dentro dele mantém o campo e mata o
`shapeGizmo` — alternativa (criar `CollidersWidget` novo) renomearia o campo
público à toa.

**Controle de ciclo do modo:** o `ColliderWidget` é um `WorldDebugWidget`
(`Node2D`), não tem chrome de painel screen-space onde encaixar um botão. O
ciclo será exposto como uma **tecla/atalho** polled por um node interno (no
espírito do `TimeControlShortcutNode`/`DebugLayoutShortcutNode` já existentes),
**e** `mode` continua sendo `var` público para scripts/jogos ciclarem
programaticamente. Decisão registrada como aberta abaixo caso se prefira um
mini-controle screen-space acoplado.

### 2. fps dentro do `ProfilerWidget`, amostrado independente da instrumentação

O `ProfilerWidget` passa a possuir um `FpsCounter` próprio e o amostra em
`onProcess` via `System.nanoTime()` — exatamente o que o `FpsWidget` fazia,
custo desprezível e **sem** depender do `FrameProfile`. A linha `fps NN` é
desenhada no topo do painel, **mesmo quando size==0** (antes de a
instrumentação de fases acumular amostras), de modo que abrir o Profiler já
mostra fps imediatamente.

Consequência aceita: para ver fps é preciso abrir o Profiler. Como o fps em si
não liga a instrumentação (só o `enabled` do profiler liga), o overhead de
"só fps" é o do `FpsCounter` — equivalente ao `FpsWidget` antigo.

**Alternativa descartada:** derivar fps de `1000/total` do `FrameProfile`.
Rejeitada porque amarraria o fps à instrumentação cara e daria zero antes da
primeira janela de medição.

### 3. Remoção de `MomentumWidget`, retenção de `MomentumDiagnostics`

O widget e o campo `momentum` saem. `MomentumDiagnostics.kt`
(`totalLinearMomentum`/`totalAngularMomentum`/`totalKineticEnergy`) fica como
API pública de `com.neoutils.engine.physics` — é a única referência externa e
permanece um utilitário didático legítimo (um jogo pode logar conservação sem
o overlay). Nenhum `GameLoop`/host chamava momentum; a remoção é local.

### 4. Catálogo de registro em `DebugLayer`/`DebugRegistry`

`DebugRegistry.bindLayer` deixa de registrar `fps`, `momentum` e `shapeGizmo`.
A ordem de registro restante define a ordem das linhas do HUD:
`colliders, log, hud, drawToggle, velocityGizmo, contactGizmo, timeControls,
profiler, scenePicker` (+ `selectionGizmo` fora do HUD, como hoje).

## Risks / Trade-offs

- **Specs cross-cutting (3 arquivos):** a fusão move a lição "geometria real"
  de `debug-physics-gizmos` para `debug-overlay`. → O delta remove a
  requirement do `ShapeGizmoWidget` e reescreve a do `ColliderWidget`; verificar
  na fase de specs que nenhuma referência órfã a `ShapeGizmoWidget` sobra.
- **~18 testes referenciam os widgets removidos.** → Reescrever
  `DebugLayerTest`/`DebugRegistryTest`/`BuiltinWidgetsTest`/`PhysicsGizmosTest`
  para o catálogo novo; adicionar cobertura de `ColliderWidget.mode` e da linha
  de fps do Profiler.
- **Coexistência com `debug-ui-z-order`.** Ambas tocam `DebugRegistry`. →
  Eixos disjuntos (catálogo vs z-order); alinhar ordem de merge para evitar
  conflito textual em `bindLayer`.
- **Default `REAL` muda o que aparece** para quem ligava `colliders` esperando
  AABB. → É uma engine de aprendizado pré-1.0, sem garantia de estabilidade de
  comportamento de debug; documentado no proposal como BREAKING interno.

## Migration Plan

1. Implementar o `mode` no `ColliderWidget` e portar o desenho de geometria
   real do `ShapeGizmoWidget`.
2. Adicionar fps ao `ProfilerWidget`; remover `FpsWidget`.
3. Remover `ShapeGizmoWidget`, `MomentumWidget` e os campos do `DebugRegistry`.
4. Ajustar `bindLayer` (ordem/catálogo) e os testes.
5. Atualizar `CLAUDE.md` (invariante #6), `README.md`, `ROADMAP.md`.

Rollback: a change é puramente aditiva-reversível (remoção de widgets); reverter
o commit restaura os três widgets sem migração de dados (nada é serializado).

## Open Questions

- **Controle de modo do `ColliderWidget`:** atalho de teclado (consistente com
  os shortcut nodes existentes) vs. um mini-controle screen-space. A proposta
  assume **atalho + `var mode` público**; confirmar na fase de apply se o
  atalho é suficiente ou se vale um controle visual.
