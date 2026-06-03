## Context

A bola do Pong é `CharacterBody2D` (`CircleShape2D` r=8) que varre o movimento com `moveAndCollide` e reflete a velocidade no `ball.py`. Os paddles são `StaticBody2D` (`RectangleShape2D` 16×96) movidos por **teleporte** — o `paddle.py::_physics_process` escreve `self.position` direto a cada frame. O paddle direito é AI que persegue o `y` da bola (`target = ../Ball`).

Dois travamentos reproduzíveis surgem disso:

- **Squeeze contra a parede**: a AI enfia a face do paddle no vão entre a bola e a parede. Como o `StaticBody2D` teleporta (massa infinita, sem sweep, sem parar no contato), ele empurra a bola para dentro da parede. A bola fica em overlap simultâneo com dois corpos; `moveAndCollide` resolve só o de menor TOI por chamada, com depenetração de um único MTV — impossível separar um ponto espremido entre duas superfícies. A bola congela jitterando.
- **Prender na quina**: no contato círculo-vs-rect na quina, a normal é diagonal (`sep = (dx0, dy0).normalized`), logo `|n.x| ≈ |n.y|`. O classificador do `ball.py` (`abs(n.x) > abs(n.y)`) vira cara-ou-coroa e, somado ao re-overlap a cada teleporte do paddle, prende a bola.

Verificações feitas na exploração (leitura de código): `StaticBody2D` e `CharacterBody2D` são **ambos** `PhysicsBody2D`; `NodeRegistry` registra os dois (`NodeRegistry.kt:82-83`); o `# extends` é validado por nome simples contra `NodeRegistry` e precisa casar com o tipo do nó; **não existe collision mask** na engine.

## Goals / Non-Goals

**Goals:**
- Eliminar os dois travamentos na **causa-raiz**, não com paliativo de script.
- Deixar o Pong **arquiteturalmente correto** como demonstração didática: paddle controlado por script = `CharacterBody2D` + `moveAndCollide` (invariante #3).
- Preservar o **rebote/feel** da bola exatamente como hoje.
- Manter a engine intocada.

**Non-Goals:**
- Não alterar a engine (`:engine`): o comportamento atual está dentro do contrato; "Static que teleporta" é uso indevido.
- Não introduzir `moveAndCollide` multi-contato/iterativo (seria defense-in-depth, desnecessário uma vez que ninguém mais teleporta sólidos para dentro de outros).
- Não reescrever o resto do spec `pong-sample` que está em vocabulário antigo além das partes que esta mudança toca.
- Não adicionar collision layers/masks.

## Decisions

### D1 — Paddle vira `CharacterBody2D` movido por `moveAndCollide`
Troca `type` no `scene.json` (`left`, `right`) e `# extends` no `paddle.py`, **juntos** (o `extends` é validado contra o tipo do nó). O `_physics_process` computa `dy` (input/AI, como hoje) e chama `moveAndCollide(Vec2(0, dy))`. O paddle passa a **parar no contato** com a bola em vez de teleportar para dentro dela — o squeeze deixa de ser possível na origem.

**Por que isso não muda o rebote:** `ball.moveAndCollide` varre contra todo `PhysicsBody2D`; `CharacterBody2D` é `PhysicsBody2D` igual ao `StaticBody2D`, com a mesma normal círculo-vs-rect, e a chamada move **só a bola**. Da ótica da bola, o paddle continua sendo massa infinita. Rebote idêntico.

### D2 — Re-fixar `x` do paddle após o move (cinta de segurança)
Se um frame começar com paddle e bola transientemente sobrepostos, a depenetração círculo-vs-rect aponta na diagonal e empurraria o paddle **fora da coluna** (deriva em `x`). Guarda: salvar `target_x = self.position.x` antes do move e reescrever `self.position = Vec2(target_x, self.position.y)` depois. Isso descarta só a componente horizontal; a parada vertical no contato (que mata o squeeze) é preservada.

### D3 — Remover o clamp numérico manual; paredes limitam por sweep
Como não há collision mask, o paddle `CharacterBody2D` passa a colidir com as paredes naturalmente. Removemos o clamp `[0, max_y]` do `paddle.py` e deixamos o sweep parar o paddle nas paredes. A faixa vertical alcançável encolhe ~`WALL_THICKNESS` px em cada ponta — **mais correto** (o paddle deixa de clipar dentro da parede). Mantém o `paddle.py` sem lógica de bound duplicada.

### D4 — Classificador face vs. edge por geometria **horizontal** no `ball.py`
Troca `if body.isInGroup("paddles") and abs(n.x) > abs(n.y)` por um teste geométrico pelo **eixo x**: aplica o bounce angular (english) quando o **centro-x da bola está fora do vão horizontal do paddle** (a bola está ao lado, encostando numa face vertical — quinas dianteiras incluídas); caso contrário (centro-x dentro do vão = topo/base genuíno, ou parede) reflete espelhado na normal.

**Por que x, não y (revisão durante a implementação):** o teste original por centro-y (dentro do vão vertical) classificava a **quina dianteira** como edge — lá o centro-y fica um fio acima/abaixo do vão. A normal da quina é diagonal e quase tangente à velocidade de entrada, então a reflexão mal tocava no componente +x que empurrava a bola contra a face; como `moveAndCollide` não desliza em overlap inicial (toi=0), a bola **congelava na quina**, presa pelo paddle perseguidor. Classificar por centro-x conserta: contato de quina dianteira é face → o x reverte decisivamente → a bola sai. O `h_sign` também passou a vir do **lado** em que a bola está (`ball_center_x` vs `paddle_center_x`), estável frame-a-frame (a `v.x` de entrada e a normal diagonal oscilam; o lado não). Reprodução determinística e correção validadas por um harness headless que varre ~9.300 condições iniciais contra o `moveAndCollide` real (0 traps pós-fix).

### D6 — Escape de overlap inicial no `ball.py`
`moveAndCollide` descarta o motion pretendido num overlap inicial (toi=0, `remainder == motion`) e só aplica a depenetração. Quando a AI re-pressiona um overlap marginal na quina, a bola nunca gasta sua velocidade (já apontando para fora) e congela. **Nudge de escape:** após o bounce, se foi overlap inicial (`remainder ≈ motion`) e a nova velocidade aponta para fora (`nv·n > 0`), a bola avança um frame ao longo dela por escrita direta. O passo parte da superfície de contato apontando para longe do paddle, então não tunela. Sem isso, o pin marginal da quina permanece mesmo com a velocidade correta.

**Conjunto mínimo (ablation):** um harness headless varrendo ~4.800 condições iniciais contra o `moveAndCollide` real mostrou que **D4 (classificar por x) + o nudge** são necessários e suficientes (0 traps); remover qualquer um volta a ~249 traps. Um guard "só refletir quando `v·n < 0`" e derivar `h_sign` da normal foram testados e descartados como redundantes para o bug — `h_sign` ficou pelo **lado** da bola (mais correto/estável que `-sign(v.x)`), e a reflexão segue incondicional como antes.

### D5 — Ordem das edições (atômica)
Os três pontos do paddle (`scene.json` type, `# extends`, `_physics_process`) têm de aterrissar juntos: trocar só o `type` deixa um `CharacterBody2D` ainda teleportando (mesmo bug); trocar só o `extends`/`type` isolado quebra a validação de `extends` no load (fail-fast). D4 (ball.py) é independente e pode ser validado isolado.

## Risks / Trade-offs

- **Drift horizontal do paddle** em overlap transitório → coberto por D2 (re-fixar `x`).
- **Faixa do paddle encolhe ~`WALL_THICKNESS` px** → aceito de propósito (D3), comportamento mais correto.
- **Paddle "gruda" na bola** ao parar no contato durante perseguição da AI → desejável; a bola sempre ressalta na própria vez, reabrindo o vão. Sem squeeze.
- **Sem multi-contato na engine** → o caso patológico de dois overlaps simultâneos só era atingível porque o paddle teleportava; com o paddle parando no contato, a bola não fica mais espremida entre duas superfícies sólidas. Risco residual aceitável para uma engine didática; registrado como possível defense-in-depth futuro, fora de escopo.
- **Spec `pong-sample` legado** permanece em vocabulário antigo fora das partes tocadas → fora de escopo desta mudança; sinalizado para uma sincronização futura do spec.
