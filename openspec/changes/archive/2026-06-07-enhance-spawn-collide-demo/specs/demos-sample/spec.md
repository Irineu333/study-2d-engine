## ADDED Requirements

### Requirement: Spawn & Collide trap is interactive with despawn/collide modes, drag, and a custom debug widget

A demo `Spawn & Collide` SHALL prover um trap central **interativo** que ilustra a dicotomia sensor-vs-sólido do invariante #3, é arrastável dentro dos limites da tela, e é controlado por um **widget de debug customizado registrado pelo próprio demo**.

**Modos do trap.** O trap SHALL operar em dois modos mutuamente exclusivos, `Despawn` (default) e `Collide`, implementados por **dois `CollisionObject2D` irmãos diretos da instância de `BoundaryWalls`** (mesmo retângulo, mesma posição):

- `TrapSensor : Area2D` — sensor que remove a bolinha `RigidBody2D` no `onBodyEntered`. Ativo quando o modo é `Despawn` (`disabled = (mode != Despawn)`).
- `TrapWall : StaticBody2D` — obstáculo sólido em que as bolinhas quicam via o solver. Ativo quando o modo é `Collide` (`disabled = (mode != Collide)`).

A troca de modo SHALL alternar o flag `disabled` de `CollisionObject2D` (sem add/remove de nó), de modo que nunca os dois colliders estejam ativos ao mesmo tempo. Os dois MUST ser filhos diretos da `BoundaryWalls` (não de um nó wrapper), porque o sweep do `RigidBody2D` só considera alvos cujo `parent` coincide com o `parent` das bolinhas.

**Drag com clamp.** O trap SHALL ser arrastável com o botão esquerdo: um press dentro do retângulo do trap (quando nenhum painel de debug consumiu o ponteiro) inicia um grab-and-drag que segue o ponteiro enquanto o botão é mantido. A posição do trap SHALL ser clampada a `[half, surface − half]` (com `half = SIZE/2`) em x e y, mantendo o trap inteiramente dentro da arena. Iniciar/manter o drag SHALL suprimir o spawn daquele clique (set `mouseClickConsumed`), de modo que arrastar o trap nunca cria uma bolinha. No resize, a posição do trap SHALL ser **re-clampada** à nova surface (não recentralizada), preservando o arrasto do usuário.

**Auto-spawn toggle.** O `Spawner` SHALL expor um gate booleano de auto-spawn; quando desligado, o drip automático para e somente o clique manual cria bolinhas.

**Widget de debug do demo.** A demo SHALL registrar um `SpawnCollideWidget : ScreenDebugWidget` via `tree.debug.register(...)` no `onEnter` e SHALL des-registrá-lo via `tree.debug.unregister(...)` no `onExit` (some do HUD ao voltar ao menu). O widget SHALL expor dois segmented controls — `Trap [Despawn | Collide]` e `Auto-spawn [On | Off]` — que leem e escrevem o estado compartilhado do demo, com o segmento ativo destacado (no espírito do `ColliderModePanel`). `:engine` SHALL permanecer sem conhecer essa classe (o widget vive em `:games:demos`).

#### Scenario: Trap exposes two sibling colliders in the arena

- **WHEN** o source da demo `Spawn & Collide` é inspecionado
- **THEN** existe um `TrapSensor : Area2D` e um `TrapWall : StaticBody2D`, ambos adicionados como filhos diretos da instância de `BoundaryWalls` (não de um nó wrapper intermediário)
- **AND** cada um carrega um `CollisionShape2D` com o mesmo retângulo, na mesma posição do trap

#### Scenario: Despawn mode removes balls; collide mode bounces them

- **GIVEN** a demo `Spawn & Collide` ativa com o trap em modo `Despawn`
- **WHEN** uma bolinha entra no retângulo do trap
- **THEN** o `TrapSensor` está ativo (`disabled = false`), o `TrapWall` está inativo (`disabled = true`), e a bolinha é removida no `onBodyEntered`
- **WHEN** o modo muda para `Collide`
- **THEN** o `TrapWall` passa a ativo e o `TrapSensor` a inativo, e bolinhas que atingem o trap quicam nele em vez de serem removidas

#### Scenario: Trap can be dragged within screen bounds and survives resize

- **GIVEN** a demo `Spawn & Collide` ativa
- **WHEN** o usuário pressiona o botão esquerdo dentro do retângulo do trap e arrasta
- **THEN** o trap segue o ponteiro, com a posição clampada a `[half, surface − half]` em ambos os eixos, e nenhuma bolinha é spawnada por esse clique
- **WHEN** a janela é redimensionada
- **THEN** a posição arrastada do trap é re-clampada à nova surface, não recentralizada

#### Scenario: Auto-spawn can be turned off

- **GIVEN** a demo `Spawn & Collide` ativa com auto-spawn ligado
- **WHEN** o auto-spawn é desligado pelo widget
- **THEN** o drip automático de bolinhas para
- **AND** o clique manual (fora do trap, sem UI sob o ponteiro) continua criando bolinhas

#### Scenario: SpawnCollideWidget is registered on enter and unregistered on exit

- **WHEN** a demo `Spawn & Collide` é carregada (`onEnter`)
- **THEN** um `SpawnCollideWidget : ScreenDebugWidget` é registrado via `tree.debug.register(...)` e aparece como uma linha no HUD com os segmented controls de modo do trap e de auto-spawn
- **WHEN** o usuário volta ao menu (a demo é removida, `onExit`)
- **THEN** o widget é des-registrado via `tree.debug.unregister(...)` e some de `tree.debug.widgets`/HUD
- **AND** `:engine` não referencia `SpawnCollideWidget` (a classe vive em `:games:demos`)

## MODIFIED Requirements

### Requirement: Each demo scene has a documented role exercising specific invariants

A spec `demos-sample` SHALL incluir uma descrição por demo explicando o que ela exercita do ponto de vista da engine — quais invariantes valida, qual sistema põe sob carga, qual diagnóstico visual oferece. Essa documentação MUST viver na spec (não em `CLAUDE.md` nem em `README.md`). As descrições MUST cobrir, no mínimo:

- **`Transforms`** (ex-Solar system): Sol amarelo central com 8 planetas e luas conhecidas orbitando seus pais; Saturno carrega `SaturnRing`. Exercita composição de transform aninhada em até 4 níveis. Adiciona uma `Camera2D` com **zoom/pan interativo** (scroll/teclas) — primeira cobertura de `Camera2D` entre as demos. O zoom também exercita **escala-composição**: ampliar/reduzir escala toda a hierarquia aninhada em uníssono (ancestor scale → tamanho renderizado do filho, o invariante que a antiga demo Scale validava isoladamente). O HUD/overlay vive em `CanvasLayer` e NÃO sofre o zoom da câmera.
- **`Spawn & Collide`** (funde os antigos Spawner + Collision stress): clique/auto-spawn adiciona `RigidBody2D` bolinhas durante `onProcess`. Um trap central **interativo** ilustra a dicotomia sensor-vs-sólido do invariante #3: dois colliders irmãos na arena (`TrapSensor : Area2D` + `TrapWall : StaticBody2D`) alternam o flag `disabled` para trocar entre o modo `Despawn` (o sensor remove a bolinha no `onBodyEntered` — as bolinhas são bodies, então o evento é `onBodyEntered`, não `onAreaEntered`) e o modo `Collide` (o `StaticBody2D` faz as bolinhas quicarem). O trap é **arrastável** pela tela com clamp aos limites (resize re-clampa, não recentra), e o auto-spawn pode ser **desligado** — tudo controlado por um `SpawnCollideWidget : ScreenDebugWidget` que o demo **registra no `onEnter` e des-registra no `onExit`**, exercitando o contrato `register`/`unregister` de debug a partir de um `Node`. As bolinhas quicam elasticamente entre si e nas paredes de uma `BoundaryWalls`. Exercita mutação segura durante traversal, sensor `Area2D` vs corpo sólido `StaticBody2D`, solver `RigidBody2D`, cache de world-transform sob carga e o contrato de widget de debug customizado.
- **`Rotating Frame`** (antigo Rotating box): `CharacterBody2D` bolinhas vivem como filhas de um wrapper que rotaciona e translada a cada frame; 4 paredes `StaticBody2D` são filhas do mesmo wrapper, em coordenadas locais. `moveAndCollide` opera no parent frame compartilhado, mantendo o sweep axis-aligned mesmo com a caixa girando em world. Exercita invalidação por mutação de ancestral sob carga real, em frame rotativo.
- **`Tumbling Swarm`** (antigo Tumbling swarm): quadrados `RigidBody2D` (`restitution=1f`, `friction=0.4f`) com velocidade linear e angular, dentro de `BoundaryWalls`. O solver resolve cada contato pelo caminho rotated do sweep com impulso normal + Coulomb tangencial — spin perceptível em hits glancing.
- **`Sprites & Tiles`** (funde os antigos Animated + Tilemap): um `TileMap` monta um chão a partir de um atlas real; um `AnimatedSprite2D` "corre" sobre ele (avanço de frame engine-driven). O player é um `CharacterBody2D` movendo-se sobre o chão de `StaticBody2D` via `moveAndCollide`. Sentinela cross-backend (Skiko + LWJGL) de `texture-rendering` (o caminho `Renderer.drawImage` é exercido pelo `AnimatedSprite2D` e pelo `TileMap`), `sprite-animation` e `tilemap-visual` numa só tela. Não inclui um `Sprite2D` estático separado — a antiga demo Sprite isolada não recebe um decorador dedicado aqui (o `Sprite2D` é só um wrapper fino de `drawImage`, já coberto pelos outros dois nós).

#### Scenario: Spec describes all five demos

- **WHEN** `openspec/specs/demos-sample/spec.md` é aberto
- **THEN** existe uma seção (Requirement) cobrindo as demos `Transforms`, `Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`, `Sprites & Tiles`
- **AND** cada demo tem ao menos um parágrafo descrevendo o invariante ou sistema que exercita
- **AND** o conteúdo dessa Requirement não duplica detalhe de implementação (esses ficam nas specs `engine-core`, `rigid-body-2d`, `kinematic-move-and-collide`)
