## Context

`GameLoop.tick(dtNanos)` faz, em ordem: `hitTestUI(input)` (sempre);
acumula `rawDt`; drena um laço fixed-step de física (`physicsProcess` +
`physics.step` em `physicsDt`, até `maxStepsPerFrame`, com clamp de
spiral-of-death); roda `process(frameDt)` com `frameDt = rawDt.coerceAtMost(maxDt)`;
e `render`. O acumulador vive no loop; os backends não sabem de fixed-step.

O toggle do HUD (`DebugToggleNode`) e qualquer node que poll input fazem-no
dentro de `tree.process`. Logo, **pular `process` quebraria o próprio HUD**
quando pausado. `hitTestUI` é a exceção que roda sempre — UI de tela
funciona pausada.

`timeScale` é "meio-tempo meio-debug": útil como ferramenta de debug, mas
também como feature de gameplay (slow-mo). A modelagem deve permitir os
dois sem prender em `tree.debug`.

## Goals / Non-Goals

**Goals:**

- Pausar o gameplay mantendo HUD/UI/atalhos de debug vivos.
- `timeScale` escalando física e `_process` coerentemente.
- Step-frame determinístico de um step de física por vez.
- Default (1, false, sem step) idêntico ao tick atual.
- `timeScale`/`paused` first-class na `SceneTree` (reusáveis por gameplay).

**Non-Goals:**

- `process_mode` por-node (exemção seletiva do pause estilo Godot) — todo o
  gameplay congela junto no MVP; só o subtree de debug segue vivo via `dt=0`.
- Interpolação visual entre steps de física.
- Determinismo de replay / time-rewind.
- Escalar o tempo de `Timer` independentemente (segue o `process`/`physics`
  dt já escalado, sem knob próprio).

## Decisions

### D1 — `dt` de gameplay efetivo: `paused ? 0 : rawDt * timeScale`

O `GameLoop.tick` computa `gameplayDt = if (paused) 0f else rawDt * timeScale`
e **acumula `gameplayDt`** (não `rawDt`) para o laço de física. Consequência
natural: `timeScale = 0` ou `paused` ⇒ acumulador não cresce ⇒ nenhum step
de física roda. O `frameDt` do `_process` também passa a derivar do
`gameplayDt` (`gameplayDt.coerceAtMost(maxDt)`), então slow-mo desacelera
animações de `_process` junto com a física.

**`physics.step` continua usando `physicsDt` fixo** — só *quantos* steps
acontecem muda (via acumulador), preservando a estabilidade do integrador.

### D2 — Pause roda `process(0f)`, não pula `process`

Quando `paused` (ou `gameplayDt == 0`), o loop **ainda chama
`tree.process(0f)`**. Nodes de gameplay que integram por `dt` não avançam
(dt zero); nodes de debug que pollam input (toggle do HUD, atalhos de tempo)
rodam normalmente — `process` não depende de `dt` para ser despachado. Com
`hitTestUI` já sempre-on, o HUD fica 100% operável pausado.

**Trade-off aceito:** um node de gameplay que faça algo `dt`-independente no
`_process` (ex.: ler input e teleportar) ainda roda sob pause. Sem
`process_mode`, não há exemção seletiva no MVP. É raro e documentado.

**Alternativa rejeitada — pular `process` inteiro no pause:** mataria o
polling de teclas do debug e o `DebugToggleNode`, tornando o HUD inacessível
exatamente quando mais se precisa dele.

### D3 — Step injeta exatamente um step de física

`SceneTree.requestStep()` seta um flag de uso único. No `tick`, **antes** do
caminho normal, se o flag está setado E `paused`/`timeScale==0`:

```
tree.applyPending(); tree.physicsProcess(physicsDt)
tree.applyPending(); physics.step(tree, physicsDt)
tree.applyPending(); tree.process(physicsDt)
tree.applyPending(); tree.render(renderer)
// limpa o flag; ignora o acumulador
```

Um step = um `physicsDt` de simulação, o grão certo para observar uma
colisão evoluir. O flag auto-limpa, então cada `requestStep()` avança
exatamente um. Quando não pausado, `requestStep()` é no-op (o tempo já corre).

**Alternativa rejeitada — step = um frame de wall-clock:** wall-clock varia;
um `physicsDt` fixo é reproduzível e alinhado ao grão da física.

### D4 — Estado first-class na `SceneTree`, UI em `tree.debug`

`var timeScale: Float`, `var paused: Boolean` e o mecanismo de step vivem na
`SceneTree` (runtime puro, não `@Serializable`, não persistem) — espelhando
`debugHudKey`. Assim gameplay pode setar `tree.timeScale = 0.5f` sem tocar em
debug. O `TimeControlWidget` e os atalhos são a *interface de debug* sobre
esse estado, em `com.neoutils.engine.debug`. Clamp: `timeScale` é coerçido a
`>= 0f` no setter (negativo não tem semântica aqui).

### D5 — Atalhos de teclado vivos sob pause

O polling de atalhos (pause/step/ciclo de velocidade) vive num node de debug
(estilo `DebugToggleNode`) dentro da `DebugLayer`, que roda em `process` — e
como D2 mantém `process` rodando sob pause, os atalhos funcionam pausado. O
`TimeControlWidget` também oferece os mesmos controles como `Button`s
(via `hitTestUI`), redundância proposital para quem prefere mouse.

### D6 — Ordem vs `debug-profiler`

O profiler vai instrumentar `tick` com timing por fase. Esta change mantém
o `tick` com fases claras e nomeáveis (hitTest, physics-loop, process,
render) e centraliza a decisão de `gameplayDt` no topo, deixando o corpo
fácil de cercar com medições. Sem dependência dura; só coordenação de merge.

## Risks / Trade-offs

- **[Pause não exime nodes `dt`-independentes]** → D2 trade-off. Mitigação:
  documentado; `process_mode` por-node é não-objetivo explícito, candidato a
  change futura se um jogo precisar.
- **[`timeScale` alto multiplica steps de física por frame]** → `timeScale=4`
  acumula 4× mais rápido; o `maxStepsPerFrame`/clamp de spiral-of-death já
  existente protege (descarta excesso). Mitigação: nenhuma nova; o clamp
  cobre.
- **[Step com mutação pendente]** → o caminho de step drena `applyPending`
  nos mesmos pontos que o tick normal, então spawn/despawn durante o step se
  comporta como num tick normal. Coberto por teste.
- **[`timeScale` first-class pode ser confundido com feature estável]** →
  está na `SceneTree` mas a UI é debug. Mitigação: KDoc deixa claro que é um
  knob de tempo de uso geral; a semântica de pause/step é o que esta change
  garante.
- **[Acoplar `GameLoop` a mais estado do tree]** → o `tick` passa a ler três
  campos. Mitigação: já lê `tree` extensivamente; defaults preservam o
  comportamento; sem custo perceptível.
