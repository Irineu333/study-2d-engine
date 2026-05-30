## Context

Os contatos resolvidos chegam ao `PhysicsContactBuffer` por dois caminhos,
ambos gravando no **frame do pai do corpo** (o frame em que o sweep opera):

- **Rigid:** `PhysicsSystem.advanceAndResolve` chama
  `contactSink?.append(sweepResult.point, sweepResult.normal)`. O corpo `r`
  tem `parent` em escopo (já capturado em `val parent = r.parent ?: continue`).
- **Kinematic:** `CharacterBody2D.moveAndCollide` chama
  `tree.debug.contacts.stage(bestHit.point, bestHit.normal)` no ramo de hit.
  O `this.parent` está em escopo.

O `ContactGizmoWidget` é um `WorldDebugWidget` e desenha em **world pass** sem
empilhar transform — logo ele assume que os `ContactRecord` já estão em world.
Para corpos top-level o frame do pai é o root (sem rotação/translação
relevante), então coincide com world. Para corpos aninhados num pai
rotacionado/transladado (as `BoxedBall` dentro do `RotatingBox` na demo 5) os
marcadores aparecem deslocados e girando.

A API de `Transform` já oferece o necessário: `compose(child)` aplica
rotação+escala+translação a um ponto local; o helper `rotate(v, radians)` do
pacote `math` (visível dentro do módulo `:engine`) rotaciona uma direção.
`Node2D.world()` devolve o transform world acumulado de um nó.

## Goals / Non-Goals

**Goals:**

- Gravar todo `ContactRecord` em **world-space**, independentemente do
  aninhamento do corpo, nos dois caminhos (rigid e kinematic).
- Um **único helper** de normalização compartilhado pelos dois caminhos, para
  que não voltem a divergir.
- Identidade para corpos top-level: Pong e Snake desenham exatamente como hoje.
- Custo zero em produção: a normalização vive dentro do caminho de gravação,
  já gated por `ContactGizmoWidget.enabled`.

**Non-Goals:**

- Corrigir normais sob **escala não-uniforme** (exigiria a inversa-transposta
  do transform). A normal é tratada por rotação — correta sob rotação e escala
  uniforme; os corpos com colisão dos demos/jogos não usam escala não-uniforme.
- Mudar o `ContactGizmoWidget`, o gating, a área de staging, ou a ordem do
  tick. A change só muda **o que** se grava (world em vez de parent-frame), não
  quando nem por quem.
- Normalizar qualquer outra coisa do contato (impulso, remainder, trajetória).

## Decisions

### D1 — Um helper único `worldContact(parent, point, normal)` no pacote physics

Em vez de inline em dois lugares, um helper `internal` no pacote
`com.neoutils.engine.physics` converte um par `(point, normal)` do frame do
pai para world e é chamado por ambos os caminhos imediatamente antes de
`stage`/`append`. Garante que rigid e kinematic apliquem **a mesma**
transformação — a divergência entre os dois caminhos era justamente o que a
mudança anterior aceitou como Non-Goal; centralizar elimina o risco.

Alternativa rejeitada — normalizar no `ContactGizmoWidget` na hora de desenhar:
o widget não conhece o corpo de origem nem seu pai (só lê `ContactRecord`),
então teria que rastrear o pai por record. Manter o buffer canonicamente
world-space é mais simples e deixa o widget burro (o que ele já é).

### D2 — Ponto via `compose`, normal via rotação (re-normalizada)

```
parentWorld = (parent as? Node2D)?.world() ?: Transform()   // identidade p/ pai sem transform
worldPoint  = parentWorld.compose(Transform(position = point)).position
worldNormal = rotate(normal, parentWorld.rotation).normalized
```

O `point` é uma posição → `compose` aplica rotação+escala+translação do pai.
O `normal` é uma direção unitária → só a rotação importa; rotacionar por
`parentWorld.rotation` e re-normalizar mantém o vetor unitário (a
re-normalização absorve drift de ponto flutuante e escala uniforme). Escala
não-uniforme distorceria a direção — Non-Goal.

### D3 — Resolução do frame do pai: `Node2D.world()` ou identidade

`parent` é `Node?`. Quando é `Node2D`, `parent.world()` dá o frame; quando é
um `Node` puro (sem transform) ou `null`, o frame é world → `Transform()`
identidade. Para corpos top-level cujo pai é o root sem rotação/translação, a
composição é identidade e o `point`/`normal` saem inalterados — preservando
Pong/Snake byte a byte.

## Risks / Trade-offs

- **[Normal sob escala não-uniforme fica distorcida]** → Non-Goal explícito;
  documentado. Nenhum corpo com colisão nos demos/jogos usa escala
  não-uniforme; o caso correto (inversa-transposta) fica para quando surgir.
- **[Custo extra por contato durante a gravação]** → uma composição de
  transform e uma rotação por contato, só quando a gravação está on (debug).
  Desprezível e fora do caminho de produção.
- **[Mudar a semântica gravada de um spec arquivado (`debug-physics-gizmos`)]**
  → na verdade **alinha** a implementação ao que aquele spec já afirma
  ("world-space point"). A garantia é descrita como requirement ADICIONADA na
  nova capability (estratégia aditiva idêntica à de `debug-kinematic-contacts`),
  evitando um delta MODIFIED contra `debug-kinematic-contacts` ainda não
  arquivada.

## Open Questions

- Nenhuma. O tratamento de escala não-uniforme da normal fica registrado como
  trabalho futuro, fora desta change.
