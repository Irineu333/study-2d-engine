## Context

`CharacterBody2D.moveAndCollide(motion)` faz hoje uma única varredura (`sweepBestHit`) e resolve assim:

```kotlin
position = position + motion * toi + bestHit.depenetration
return KinematicCollision2D(..., remainder = motion * (1f - toi))
```

No caso `toi == 0` (starting overlap), isso vira `position + depenetration` e `remainder == Vec2.ZERO`: **o motion é inteiramente descartado**, mesmo quando aponta para fora do colisor. A depenetração single-shot (MTV) separa do colisor de menor TOI, mas se um peer re-pressiona o overlap a cada frame (o paddle AI do Pong cravando a quina na bola), a separação por frame é marginal (~0.04px) e o corpo nunca gasta sua velocidade de saída — congela. O padrão de "slide" documentado (chamar `moveAndCollide(remainder)` de novo) não escapa: parte da mesma posição sobreposta e retorna `toi == 0` de novo.

Restrições: o caminho comum (`toi > 0`, contato limpo) não pode mudar; o contrato de depenetração com `motion == (0,0)` (pure-recovery, exercido por `CharacterBody2DTest`) deve continuar valendo; sem mudar scene-graph nem vazar tipos de backend (invariante #2/#4); o `RigidBody2D` já roda um TOI loop em `PhysicsSystem` com `TOI_ITERATIONS = 4`.

## Goals / Non-Goals

**Goals:**
- Um `CharacterBody2D` SEMPRE consegue deixar um overlap inicial que está tentando largar — o motion de separação é aplicado, não descartado.
- Preservar o comportamento atual onde ele já está correto: contato limpo (`toi > 0`), motion zero, e motion apontando para dentro do colisor (corpo encostado numa parede não a atravessa).
- Remover a gambiarra do `ball.py` (nudge de escape por escrita direta de posição) e provar, com o harness de ablation, que o trap continua em 0.

**Non-Goals:**
- Não virar um solver de física: nada de impulso/restituição no caminho kinematic (continua "para no contato; script decide a resposta").
- Não resolver pile-ups arbitrários de N corpos num único frame — o recovery é limitado por um teto fixo de iterações; casos patológicos degradam de forma graciosa (sem loop infinito, sem penetração).
- Não tocar no TOI loop do `RigidBody2D` (a não ser extrair um helper compartilhado, se limpo).

## Decisions

### D1 — Recovery-then-continue: varrer o motion restante a partir da posição depenetrada
Transformar a varredura única num **loop limitado**. A cada iteração: `sweepBestHit(remaining)`.
- **Sem hit** → `position += remaining`; encerra.
- **`toi > 0`** (contato limpo) → `position += remaining * toi + depenetration`; encerra (comportamento atual).
- **`toi == 0`** (overlap inicial) → aplica `depenetration`, e **continua o loop** com o `remaining` ainda por gastar, agora a partir da posição já separada daquele colisor.

A sacada: depois da depenetração, o corpo deixa de sobrepor aquele colisor. Se o `remaining` aponta **para fora**, a próxima varredura encontra folga e gasta o motion (null ou novo `toi > 0`) — o corpo escapa, normalmente em **uma** iteração extra. Se o `remaining` aponta **para dentro** do colisor, a varredura re-detecta `toi == 0` com depenetração ~0; sem progresso, o teto de iterações encerra e o corpo fica **na superfície** (encostado, não penetrando) — preservando o caso "parede". Motion zero → varredura nula imediata → só a depenetração, igual ao contrato atual.

*Alternativa considerada:* aplicar só o **componente separador** do motion (projeção em `+normal`). Rejeitada: re-varrer é mais geral (cobre deslizar pela superfície e múltiplos colisores) e reusa o `sweepBestHit` que já existe, sem geometria nova.

### D2 — Reportar o primeiro contato, mover por todo o recovery
O `KinematicCollision2D` retornado carrega `point`/`normal`/`collider` do **primeiro** hit (o overlap inicial) — o script continua recebendo a normal de contato para refletir a velocidade. Mas a `position` já avançou pelo recovery completo, e `remainder` passa a ser **o motion não-consumido após o recovery** (não mais `Vec2.ZERO` fixo no caso `toi == 0`). Assim o script reage ao contato E o corpo realmente sai — eliminando a necessidade do nudge manual.

### D3 — Teto de iterações fixo, reusando a constante do TOI loop
O loop é limitado por um teto pequeno e fixo (alinhar com `TOI_ITERATIONS = 4` do `RigidBody2D`). Garante terminação (sem loop infinito sob re-pressão), e o número casa com a intuição "alguns contatos por frame". Se o teto é atingido com o corpo ainda sobreposto, retorna normalmente (degrada gracioso), igual à filosofia do `PhysicsSystem.step`.

### D4 — Local da implementação
Implementar no `CharacterBody2D.moveAndCollide` (onde o loop vive hoje implícito numa varredura só). Se o controle de iteração/recovery ficar idêntico ao de `RigidBody2D`, extrair um helper privado em `PhysicsSystem`/`Shape2D`; caso contrário, manter local para não acoplar os dois caminhos (o kinematic não tem impulso). Decisão final na implementação, guiada por clareza (invariante didático).

### D5 — Remover a gambiarra do `ball.py` e revalidar
Deletar o bloco de "starting-overlap escape" do `ball.py` (escrita direta de `self.position`). O classificador face-vs-edge por x e o `h_sign` por lado permanecem (são lógica de jogo legítima). Revalidar quina/lateral com o harness headless de ablation: **0 traps** sem o nudge. Esse harness é o critério objetivo de que a engine assumiu o trabalho.

## Risks / Trade-offs

- **Mudança de contrato em `remainder` no caso `toi == 0`** (era `Vec2.ZERO`) → cenário do spec reescrito e `CharacterBody2DTest` atualizado; busca por outros consumidores de `remainder` (nenhum jogo depende do zero hoje).
- **Recovery altera trajetória de algum demo Kotlin?** → os demos batem em contato limpo (`toi > 0`), fora do caminho do recovery; teste de regressão confirma trajetórias. Mitigação: rodar a suíte de física + os demos antes/depois.
- **Teto de iterações insuficiente num pile-up real** → degrada gracioso (corpo pode ficar 1 frame sobreposto, resolve no próximo); aceitável para engine didática, documentado.
- **Custo extra por frame** → só no caminho `toi == 0` (raro); o caminho comum faz uma varredura, igual a hoje.
