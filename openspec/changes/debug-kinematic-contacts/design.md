## Context

A change `debug-physics-gizmos` introduziu o `PhysicsContactBuffer` per-tree
(em `tree.debug.contacts`) e o `ContactGizmoWidget`. O buffer é preenchido
**dentro de `PhysicsSystem.step`**: o `step` limpa `records` no início (quando
a gravação está on) e dá `append(point, normal)` a cada contato que o solver
de impulso resolve. Como o solver só roda sobre `RigidBody2D`, contatos de
`CharacterBody2D` — resolvidos pelo script via `moveAndCollide` — nunca chegam
ao buffer.

A ordem de um substep de física no `GameLoop.tick` é fixa:

```
while (accumulator >= physicsDt) {
    applyPending()
    physicsProcess(physicsDt)   // _physics_process → moveAndCollide (contato kinematic)
    applyPending()
    physics.step(tree, dt)      // limpa records + resolve/grava contatos rigid
}
process(dt); render(renderer)   // ContactGizmoWidget lê records aqui
```

O `moveAndCollide` roda em `physicsProcess`, **antes** do `step`. O `step`
limpa `records` no início. Logo, se `moveAndCollide` gravasse direto em
`records`, o clear do `step` apagaria o contato kinematic antes do render.

## Goals / Non-Goals

**Goals:**

- Fazer o `ContactGizmoWidget` mostrar contatos de `CharacterBody2D`
  (`moveAndCollide`), cobrindo Pong/Snake e qualquer jogo cinemático.
- Reusar o mesmo buffer, o mesmo gating (`ContactGizmoWidget.enabled`) e o
  mesmo widget — sem nova UI.
- Não contradizer a semântica de `debug-physics-gizmos` ("`step` limpa no
  início e grava cada contato rigid"): a change é puramente aditiva.
- Custo zero em produção (early-out quando a gravação está off).

**Non-Goals:**

- Normalizar os contatos para world-space. Tanto o caminho rigid quanto o
  kinematic gravam no frame em que operam (o frame do pai). Para corpos
  top-level (Pong, Snake) isso é world e o gizmo desenha certo; corpos
  aninhados herdam a mesma imprecisão já existente no caminho rigid. Uma
  normalização cobriria os dois caminhos de uma vez — change futura.
- Gravar a trajetória do sweep, magnitude de impulso, ou o `remainder` do
  slide. Só o `(point, normal)` do contato resolvido.
- Mudar a ordem do tick ou tocar no `GameLoop`/`GameHost`.

## Decisions

### D1 — Staging + fold no `step`, não clear no `physicsProcess` nem no render

O `moveAndCollide` **stage** o contato numa área separada do
`PhysicsContactBuffer` (`stage(point, normal)`); o `PhysicsSystem.step`, ao
limpar `records` no início (quando a gravação está on), **dobra** os staged em
`records` (`takeStaged()` → move staged para records e esvazia o staging) e só
então grava os contatos rigid deste step. No render, `records` carrega
kinematic + rigid do mesmo substep.

Por que staging+fold e não mover o clear:

- **Preserva a semântica existente.** `debug-physics-gizmos` define "`step`
  limpa `records` no início". Mantendo o clear no `step` (agora clear =
  "esvazia records e consolida staged"), a requirement não muda — esta change
  só **adiciona** o fold. Evita um delta MODIFIED contra um spec ainda não
  arquivado (mesma estratégia aditiva de `debug-immediate-draw`).
- **`physicsProcess` sempre precede o `step` no mesmo substep**, então todo
  contato staged é consolidado no `step` imediatamente seguinte — nunca fica
  "atrasado" nem se perde.
- **Não toca o `GameLoop`.** Toda a lógica vive no `PhysicsContactBuffer`,
  no `CharacterBody2D` e no `PhysicsSystem` — exatamente os pontos que a change
  já alcança.

**Alternativa rejeitada — clear no render tail (como `debug.draw.clearFrame`):**
seria conceitualmente limpo (contatos como dado single-frame), mas *modifica*
a requirement "clear no início do `step`" de `debug-physics-gizmos` (ainda não
arquivada), forçando um delta MODIFIED frágil e acumulando contatos entre
substeps do mesmo frame. O staging+fold mantém a granularidade per-substep que
já existe.

**Alternativa rejeitada — clear no início de `physicsProcess`:** também tira
o clear do `step` (modifica a requirement) e espalha a vida do buffer entre
`SceneTree.physicsProcess` e `PhysicsSystem.step`.

### D2 — Gravar no frame de operação do `moveAndCollide` (consistente com rigid)

O `KinematicCollision2D.point`/`.normal` vivem no frame do pai (onde o
`moveAndCollide` faz o sweep). O caminho rigid (`SweepResult.point`) também
grava no frame do pai. Gravar o contato kinematic como está mantém os dois
caminhos **consistentes**: para corpos top-level (Pong, Snake) o frame do pai
é world e o `ContactGizmoWidget` (world pass) desenha certo. Normalizar só o
kinematic criaria divergência entre os dois caminhos; normalizar ambos é uma
change separada (Non-Goal).

### D3 — Gating idêntico ao rigid

`moveAndCollide` lê `tree.debug.contacts.recording` (já espelhado de
`ContactGizmoWidget.enabled`) e só faz stage quando on; o fold no `step` também
é gated. Sem widget habilitado, nem stage nem fold rodam — custo zero. Nenhum
novo flag, nenhum novo toggle de HUD.

## Risks / Trade-offs

- **[Contato kinematic em frame não-world para corpos aninhados]** (ex.: bolas
  da demo 5 dentro da caixa girando) → o marcador apareceria deslocado.
  Mitigação: é a **mesma** limitação já presente no caminho rigid; documentada
  como Non-Goal e endereçável por uma normalização world-space futura que
  cobre os dois caminhos. Jogos shipped cinemáticos (Pong, Snake) são
  top-level e desenham certo.
- **[Slide com múltiplos `moveAndCollide` por substep]** → cada hit dá um
  stage, então um slide gera vários marcadores no mesmo frame. Mitigação:
  comportamento aceitável e até informativo para debug; o fold consolida todos.
- **[Acoplar `CharacterBody2D` ao `tree.debug`]** → o `moveAndCollide` passa a
  referenciar `tree.debug.contacts`. Mitigação: `tree.debug` já é a superfície
  de debug per-tree, o corpo já alcança `tree`, e o stage é gated/early-out.
  Espelha o acoplamento que `PhysicsSystem.step` já tem (aceito em
  `debug-physics-gizmos`).
- **[Crescimento do staging se um substep nunca chegar ao `step`]** → na
  prática impossível: `physicsProcess` e `step` rodam em par no mesmo substep
  do `GameLoop`. Mitigação: o fold esvazia o staging a cada `step`; mesmo um
  `moveAndCollide` chamado fora do loop só acumularia até o próximo `step`.

## Open Questions

- Nenhuma. A normalização world-space dos contatos (rigid + kinematic) fica
  registrada como trabalho futuro, fora desta change.
