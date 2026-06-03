## Why

A bolinha do Pong trava em duas situações reproduzíveis: **presa na quina do paddle** e **espremida contra a parede**. A causa-raiz não é um bug pontual no script da bola — é arquitetural: os paddles são `StaticBody2D` movidos por **teleporte** (escrevem `self.position` direto a cada frame), violando o invariante #3 do projeto, que diz que um corpo sólido controlado por script deve ser `CharacterBody2D` movido via `moveAndCollide`. Um `StaticBody2D` que se move vira um colisor de massa infinita que aparece dentro da bola sem sweep e sem parar no contato — ele literalmente empurra a bola para dentro da parede. Como o Pong é a demonstração viva da fundação da engine, ele precisa terminar **funcionando e arquiteturalmente correto**: não pode ensinar o uso errado de nó.

## What Changes

- **Paddle vira `CharacterBody2D`** (era `StaticBody2D`) no `scene.json` (nós `left` e `right`) e no cabeçalho `# extends` do `paddle.py`. Os dois mudam juntos porque o `# extends` é validado por nome contra o `NodeRegistry` e precisa casar com o tipo do nó na cena.
- **`paddle.py::_physics_process` passa a mover via `moveAndCollide(Vec2(0, dy))`** em vez de `self.position = Vec2(pos.x, new_y)`. Com isso o paddle **para no contato** em vez de teleportar para dentro da bola — o squeeze contra a parede deixa de existir na origem.
- **O paddle é re-fixado na sua coluna `x`** após o `moveAndCollide` (`self.position = Vec2(target_x, self.position.y)`), descartando qualquer depenetração horizontal que um overlap transitório com a bola poderia injetar — garante que o paddle nunca derive lateralmente.
- **O clamp numérico manual `[0, max_y]` é removido**: as paredes (`StaticBody2D`) passam a limitar o paddle via sweep. Não há collision mask na engine, então o paddle colide com as paredes naturalmente; a faixa vertical alcançável encolhe ~`WALL_THICKNESS` px em cada ponta — comportamento mais correto (o paddle deixa de clipar dentro da parede), aceito de propósito.
- **`ball.py` classifica acerto de face vs. edge por geometria horizontal** em vez de `abs(n.x) > abs(n.y)`: o bounce angular (english) é aplicado quando o centro-**x** da bola está **fora** do vão horizontal do paddle (a bola está ao lado, numa face vertical — quinas dianteiras incluídas); só topo/base genuíno (centro-x dentro do vão) e paredes refletem na normal. Isso resolve a quina dianteira de forma determinística (revertendo o x decisivamente) em vez da reflexão diagonal fraca que prendia a bola. Complementa um **nudge** que tira a bola de overlap inicial (`moveAndCollide` descarta o motion em toi=0), evitando o congelamento na quina sob pressão da AI. O par classificador-x + nudge foi confirmado como mínimo necessário por ablation (~4.800 condições contra o `moveAndCollide` real).

Sem mudanças na engine: `StaticBody2D` e `CharacterBody2D` já são ambos `PhysicsBody2D`, `ball.moveAndCollide` trata os dois de forma idêntica e move só a bola — o **rebote/feel da bola permanece igual**. A correção é inteiramente nos resources do jogo (`games/pong/`).

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova. -->

### Modified Capabilities
- `pong-sample`: o contrato de movimento dos paddles passa a exigir `CharacterBody2D` + `moveAndCollide` (parada no contato, sem teleporte, sem squeeze, fixo na coluna `x`); a resposta de colisão da bola passa a classificar face vs. quina/edge por geometria, refletindo across-normal e nunca prendendo na quina.

## Impact

- **Código afetado** (somente jogo, sem engine):
  - `games/pong/src/main/resources/pong/scene.json` — `type` dos nós `left` e `right`.
  - `games/pong/src/main/resources/pong/scripts/paddle.py` — `# extends`, `_physics_process`, remoção do clamp manual, re-fixação de `x`.
  - `games/pong/src/main/resources/pong/scripts/ball.py` — classificador face vs. quina/edge.
- **Engine**: zero alterações. O comportamento atual já está dentro do contrato; "Static que teleporta" é uso indevido, não defeito.
- **Comportamento aceito como mudança**: faixa vertical do paddle encolhe ~`WALL_THICKNESS` px em cada ponta (mais correto).
- **Risco**: baixo, localizado em `games/pong/`. Único risco real (drift horizontal do paddle em overlap transitório) coberto pela re-fixação de `x`. Suíte de testes da engine não é afetada.
