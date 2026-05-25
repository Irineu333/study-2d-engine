## Why

`kinematic-move-and-collide` ficou explicitamente limitada a sweeps axis-aligned: quando `aWorld.rotation != 0f || bWorld.rotation != 0f`, `sweepOverlap` retorna `null` e `CharacterBody2D.moveAndCollide` cai num caminho onde apenas avança `position` sem CCD — qualquer body com rotação local ou cujo target tenha rotação no frame da sweep volta a tunelar em alta velocidade. A change foi escrita assim de propósito (escopo enxuto + parents rotativos compartilhados resolvem Demos 4/5), mas a limitação foi documentada como follow-up real, justamente esta `kinematic-rotated-sweep`.

O segundo motivo é metodológico. O post-mortem dos dois bugs de freeze do `kinematic-move-and-collide` (tangent-leaving + spawn-overlap) mostrou que a cobertura de testes da change anterior era de **chamada-única com TOI analítico**, insuficiente pra capturar bugs *que emergem ao longo de múltiplos frames*. Esta change é a primeira oportunidade de introduzir um **harness behavioral** (multi-frame, com assertivas sobre trajetória) que catalize os dois objetivos: validar o caminho rotacionado novo, e criar uma rede de segurança reusável pra próximas mudanças em sweep math.

## What Changes

- **NEW**: `sweepOverlap(...)` cobre os pares com rotação não-zero quando os dois transforms compartilham um frame de origem (no `moveAndCollide` atual, isso significa "compartilham o parent"). Implementação:
  - **Circle-vs-rotated-Rect**: transforma centro do círculo + motion pro frame local do rect (rotação inversa); aplica o `sweepCircleRect` axis-aligned existente (cheap — o círculo é invariante a rotação).
  - **Rotated-Rect-vs-Rotated-Rect**: SAT temporal — projeta os 4 cantos de cada OBB + a motion relativa sobre os 4 eixos candidatos (2 normais por OBB), encontra o intervalo `[tEnter, tExit]` por eixo, a intersecção dá o TOI.
  - **Rotated-Rect-vs-Circle**: dualidade geométrica via `-motion` contra o caminho circle-vs-rotated-rect (mesma técnica do swept-rect-vs-circle axis-aligned atual).
- **NEW**: contrato de SweepResult sem mudança — `depenetration` continua sendo a única saída além de TOI/point/normal. Para starting-overlap em pares rotacionados, depenetration é computada a partir do vetor de separação SAT (axis com menor sobreposição).
- **NEW**: `CharacterBody2D.moveAndCollide` deixa de rejeitar bodies/targets rotacionados — agora delega ao sweep, que cobre os três casos. KDoc da limitação removido.
- **NEW**: harness `BehavioralSweepTest` em `engine/src/test/` — pattern multi-frame que constrói uma scene com bodies cinéticos, roda N ticks via `GameLoop`, e assertes sobre **propriedades de trajetória** ao longo do tempo (monotonia da posição entre bounces, ausência de oscilação patológica, eventual separação após starting-overlap). Cobre os dois cenários da change atual (rotated bounce, rotated spawn-overlap) e fica disponível como template pra changes futuras.
- **DOCUMENTATION**: KDoc de `sweepOverlap` removido da clausula "Both rotations MUST be 0f"; substituído por descrição do que cada par cobre. CLAUDE.md invariante #3 ganha nota: "sweep cobre axis-aligned e bodies rotacionados que compartilham o frame da sweep".

## Capabilities

### New Capabilities

(nenhuma — extensão da capability `kinematic-move-and-collide` existente.)

### Modified Capabilities

- `kinematic-move-and-collide`: o requisito `sweepOverlap` cobre os pares rotacionados quando ambos os transforms estão no mesmo frame (parent local); o requisito de `CharacterBody2D.moveAndCollide` ganha cláusula explícita de que bodies rotacionados (`this.transform.rotation != 0f`) e targets rotacionados são suportados quando compartilham o parent.

## Impact

- **Código tocado:**
  - `engine/.../physics/Shape2D.kt`: adiciona `sweepCircleRotatedRect`, `sweepRotatedRectRotatedRect` (SAT temporal), helpers internos (projeção temporal, inverse-rotate de motion). Atualiza `sweepOverlap` para roteá-los quando `rotation != 0f`. Remove a guarda `if (aWorld.rotation != 0f || bWorld.rotation != 0f) return null` no topo.
  - `engine/src/test/.../physics/SweepTest.kt`: novos cenários — TOI analítico para circle-vs-rotated-rect (e.g. rect 45°, círculo aproximando pela face curta), rotated-rect-vs-rotated-rect (e.g. dois quadrados de mesma rotação contato face-a-face; rotações diferentes contato canto-face).
  - `engine/src/test/.../physics/BehavioralSweepTest.kt` (NOVO): harness multi-frame. Cenários:
    1. Ball CharacterBody2D rotated 30° vs StaticBody2D rotated 30°, motion frontal — ball bate, reflete, separa em ≤ 3 frames (não oscila).
    2. Ball + wall spawnados overlapping em parent rotacionado — depenetration aplicada, próxima frame mostra non-overlap.
    3. Ball percorre arena com 4 paredes rotated 45° (losango) — após 60 frames de bouncing, posição percorrida total > X (não congelou).
  - `engine/.../physics/CharacterBody2D.kt`: KDoc da Limitação removido (o método agora cobre rotacionados).
  - `CLAUDE.md`: invariante #3 atualizado.
- **Performance**: O(1) por par (rotated-rect-vs-rotated-rect projeta 8 corners × 4 eixos = 32 dot products + 8 motion-projections; circle-vs-rotated-rect é um rotate de centro + sweepCircleRect existente). Imperceptível pros demos atuais.
- **Sem impacto em**: `bounds()` (continua AABB envelope), `DebugOverlay` (continua desenhando AABBs world-space), `PhysicsSystem.step` (continua usando SAT discreto via `overlap()`), Pong, TicTacToe, Hello World, Demos 1-3 (não usam sweep rotacionado).
- **Backwards-compat**: pure extension — bodies/targets axis-aligned continuam pegando o caminho fast existente; nada quebra.
