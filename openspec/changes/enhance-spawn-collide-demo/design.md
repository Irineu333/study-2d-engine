## Context

A demo `Spawn & Collide` (`:games:demos`) hoje monta uma `BoundaryWalls` (arena), um `Spawner` que adiciona `RigidBody2D` bolinhas por clique + drip automático, e um `Trap : Area2D` central que as remove no `onBodyEntered`. O `Trap` é recentralizado a cada resize via `SpawnCollideDemo.onProcess`.

Restrições que moldam o design:

- **Sweep do `RigidBody2D` é parent-escopado.** `PhysicsSystem.sweepBestHit` filtra alvos por `target.parent !== parent` (e por `target.disabled`). Para uma bolinha colidir com um obstáculo, esse obstáculo precisa ser um `PhysicsBody2D` sólido **filho direto da mesma `BoundaryWalls`** que as bolinhas — não pode estar aninhado em um nó wrapper.
- **`Area2D` é sensor puro** (invariante #3): nunca bloqueia movimento. "Quicar no trap" exige um `StaticBody2D`.
- **`CollisionObject2D.disabled`** já existe (`@Inspect`) e é honrado tanto no broad-phase (`isLive`/objetos filtrados) quanto no sweep (`if (target.disabled) continue`). É o mecanismo de liga/desliga sem add/remove de nó.
- **Contrato de widget de debug** já suporta extensão: `tree.debug.register(widget)` / `unregister(widget)` chamável de qualquer call-site não-engine após `tree.start()`. `ScreenDebugWidget` dá chrome arrastável, dock, collapse/close e linha no HUD de graça. `ColliderModePanel` é o template canônico de painel com segmented control (monta `Panel` + `Button`s em `bodyVisible`, reporta `bodySize()`).
- O demo entra **dinamicamente** via `DemoSwitcherRoot.select` (bem depois de `tree.start()`), então a `DebugLayer` já está ligada quando o `onEnter` do demo roda — `register` no `onEnter` é seguro.

## Goals / Non-Goals

**Goals:**

- Trap alternável entre **Despawn** (sensor) e **Collide** (sólido) em runtime.
- Trap **arrastável** dentro dos limites da tela, sobrevivendo a resize.
- **Widget de debug do demo** com toggles de modo do trap e de auto-spawn, registrado/des-registrado pelo ciclo de vida do demo.
- Remover o `AxesWidget`, transferindo seu papel de "exemplo de widget customizado" para o novo widget (e generalizando a spec da engine).
- Manter a demo funcionando idêntica nos dois backends (Skiko + LWJGL) via o `DemoSwitcherRoot` compartilhado.

**Non-Goals:**

- Nenhuma mudança de API em `:engine`. Sem novo flag, nó ou SPI na engine.
- Sem "trap kinematic" que empurra bolinhas por momento ao ser arrastado (o `StaticBody2D` movido por teleporte não transfere momento; arrastar rápido só reposiciona o obstáculo). Empurrão real fica fora de escopo.
- Sem persistência do estado do widget/trap em scene file (estado de sessão, `@Transient`).
- Sem mudança nos outros 4 demos além da remoção do register de `AxesWidget` nos `main`.

## Decisions

### D1. Dois colliders irmãos com toggle `disabled` (vs swap de nó)

Trap vira **dois `CollisionObject2D` irmãos diretos na `BoundaryWalls`**, no mesmo rect e na mesma posição:

- `TrapSensor : Area2D` — `disabled = (mode != DESPAWN)`. No `onBodyEntered`, remove a bolinha (lógica de hoje).
- `TrapWall : StaticBody2D` — `disabled = (mode != COLLIDE)`. Sólido; o solver faz as bolinhas quicarem.

Um nó visual (a borda `Line2D` atual) segue a posição compartilhada. Como nunca os dois estão ativos ao mesmo tempo, eles não interagem entre si.

**Por que não swap de nó** (remove `Area2D` / add `StaticBody2D`): churn maior, complica a continuidade do drag e do estado, e perde a clareza didática. O toggle `disabled` é mais limpo, usa um mecanismo já existente e testado, e ilustra literalmente a dicotomia sensor/sólido do invariante #3.

**Por que irmãos na arena, não filhos de um wrapper "Trap"**: o sweep do `RigidBody2D` exige `target.parent === parent` das bolinhas. Aninhar o `TrapWall` num wrapper quebraria a colisão. A posição compartilhada é mantida por um pequeno controlador (ver D2), não pela hierarquia.

### D2. Posição do trap como estado próprio; drag + clamp; resize re-clampa

A posição do trap deixa de ser derivada do centro a cada resize e passa a ser **estado de sessão** (`@Transient trapPosition: Vec2`), aplicada a `TrapSensor`, `TrapWall` e ao visual a cada frame.

- **Inicialização**: centro na primeira surface válida (degenerada → centra uma vez).
- **Drag**: no press do botão esquerdo, se o ponteiro cai dentro do rect do trap e `!mouseDragConsumed`, inicia grab-and-drag (estilo `SolarSystemDemo.dragPan`, held = `isMouseDown`). Ao iniciar/segurar, seta `mouseClickConsumed = true` para **suprimir o spawn** daquele clique (mesmo gesto do `ScreenDebugWidget.consumePress`).
- **Clamp**: `trapPosition` clampada a `[half, size − half]` em x e y, com `half = SIZE/2`.
- **Resize**: re-clampa `trapPosition` à nova surface em vez de recentrar — preserva o arrasto do usuário.

**Arbitragem do botão esquerdo** (3 consumidores, em ordem): (1) painel de debug — a engine já seta `mouseClickConsumed`/`mouseDragConsumed` no `hitTestUI`; (2) drag do trap — só quando o ponteiro está dentro do rect e nada consumiu; (3) spawn — `wasMouseClicked` em qualquer outro lugar.

### D3. Estado compartilhado entre demo e widget

Um holder simples (objeto/propriedades do demo) é a fonte de verdade: `trapMode: TrapMode { DESPAWN, COLLIDE }` e `autoSpawnEnabled: Boolean`. O `SpawnCollideWidget` recebe uma referência a esse holder (injeção no construtor) e lê/escreve nele — exatamente como o `ColliderModePanel` lê/escreve `tree.debug.colliders`. A lógica do demo (toggle `disabled`, gate do `Spawner`) reage ao mesmo holder a cada frame.

**Alternativa descartada**: o widget alcançar o demo via busca na árvore (`tree.findChild`). Acoplamento mais frágil e implícito; a injeção explícita é mais clara.

### D4. `SpawnCollideWidget : ScreenDebugWidget`, registrado pelo demo

Espelha o `ColliderModePanel`: monta um `Panel` invisível com dois segmented controls (`Button`s) em `buildPanel()` gated por `bodyVisible`, reporta `bodySize()`, e em `refresh` destaca o segmento ativo a partir do holder.

- **Linhas**: `Trap [Despawn | Collide]` (escreve `trapMode`), `Auto-spawn [On | Off]` (escreve `autoSpawnEnabled`).
- **Ciclo de vida**: o demo chama `tree.debug.register(widget)` no `onEnter` e `tree.debug.unregister(widget)` no `onExit`. Some do HUD ao voltar ao menu. Exercita `register` **e** `unregister` a partir de um `Node`.
- **`title = "Spawn & Collide"`** → rótulo da linha no HUD.

### D5. Remoção do `AxesWidget` e generalização da spec `debug-overlay`

Deleta `AxesWidget.kt` e as duas chamadas `tree.debug.register(AxesWidget())` nos `main`. A spec ativa `debug-overlay` nomeia `AxesWidget` em 3 cenários como testemunha de "widget customizado registrado pelo usuário". Esses cenários testam a **máquina da engine** (register/find/HUD row) e não deveriam depender de uma classe do módulo de jogos.

**Decisão**: generalizar os 3 cenários para um widget de exemplo hipotético (ex.: `ExampleWidget : WorldDebugWidget` / "um widget customizado qualquer"), desacoplando a spec da engine do módulo `:games:demos`. O `SpawnCollideWidget` passa a ser o **exemplo vivo** do padrão nos demos, sem a spec da engine citá-lo por nome.

**Alternativa descartada**: re-apontar os cenários para `SpawnCollideWidget`. Re-acopla a spec da engine aos demos e não encaixa — o novo widget é `ScreenDebugWidget` registrado/des-registrado dinamicamente, enquanto os cenários assumem "registrado exatamente uma vez" e um `WorldDebugWidget`.

## Risks / Trade-offs

- **Conflito drag-vs-spawn no clique** → resolvido pela ordem de arbitragem em D2 e por setar `mouseClickConsumed` ao agarrar o trap; coberto por cenário de spec.
- **Trap arrastado para dentro de uma parede / fora da área** → o clamp a `[half, size − half]` mantém o trap inteiramente dentro da arena.
- **Bolinha presa dentro do `TrapWall` ao trocar Despawn→Collide com a bolinha sobreposta** → caso de borda visual aceitável numa demo; o solver empurra para fora no próximo step. Não é objetivo resolver overlap inicial.
- **Spec da engine generalizada deixa de citar um exemplo concreto** → o exemplo concreto migra para os demos (`SpawnCollideWidget`), que é referência viva; o README já usa um snippet hipotético (`MyAxes`), coerente com a generalização.
- **Estado do trap/auto-spawn não persiste** → intencional (estado de sessão, `@Transient`), coerente com a disciplina `@Inspect`/`@Transient`.

## Migration Plan

Mudança contida em `:games:demos` + dois deltas de spec; sem migração de dados nem mudança de API. Rollback = reverter o commit (restaura `AxesWidget.kt` e os registers). Sem flags de feature.

## Open Questions

Nenhuma — as decisões de escopo (toggle vs swap, painel demo-local vs widget de debug, generalizar vs re-apontar a spec) foram resolvidas na exploração.
