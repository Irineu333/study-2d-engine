## 1. Parte 1 — Paddle vira CharacterBody2D (atômico)

- [x] 1.1 Em `games/pong/src/main/resources/pong/scene.json`, trocar `"type"` dos nós `left` e `right` de `com.neoutils.engine.physics.StaticBody2D` para `com.neoutils.engine.physics.CharacterBody2D` (preservar properties, groups, shape filho).
- [x] 1.2 Em `paddle.py`, trocar o cabeçalho `# extends StaticBody2D` por `# extends CharacterBody2D`.
- [x] 1.3 Em `paddle.py::_physics_process`, substituir `self.position = Vec2(pos.x, new_y)` por: salvar `target_x = self.position.x`, mover com `self.moveAndCollide(Vec2(0.0, dy))`, e re-fixar `self.position = Vec2(target_x, self.position.y)` (cinta D2). Sair cedo quando `dy == 0.0` como hoje.
- [x] 1.4 Remover o clamp numérico manual `[0, max_y]` do `paddle.py` (D3) — as paredes passam a limitar por sweep. Conferir que `_compute_human`/`_compute_ai` continuam devolvendo `dy` (deslocamento), não posição absoluta.

## 2. Parte 2 — Classificador de contato da bola por geometria

- [x] 2.1 Em `ball.py::_physics_process`, substituir `if body.isInGroup("paddles") and abs(n.x) > abs(n.y)` por um teste geométrico: bounce angular só quando o centro-y da bola está dentro do vão vertical da face do paddle (derivado de `script_of(body).size` + posição do paddle, como em `_bounce_off_paddle`); caso contrário, reflexão espelhada na normal.
- [x] 2.2 Conferir que o branch de reflexão across-normal (`v' = v - 2(v·n)n`) cobre quina, topo/baixo do paddle e paredes, e que `_bounce_off_paddle` permanece inalterado.

## 3. Verificação

- [x] 3.1 Build do módulo do jogo: `./gradlew :games:pong:build` (ou compile/check equivalente) passa.
- [x] 3.2 Suíte de testes da engine intacta: `./gradlew :engine:test` continua verde (mudança é só em resources do jogo).
- [x] 3.3 Rodar o Pong e reproduzir os cenários do bug: confirmar que o squeeze contra a parede **sumiu** (paddle para na bola, não esmaga) e que a bola **não prende na quina**.
- [x] 3.4 Confirmar que o rebote/feel está idêntico (ângulo do english preservado), o paddle **não desliza em x** (cinta D2), a AI ainda rastreia a bola e o humano (W/S) responde igual.
- [x] 3.5 Atualizar `# extends` nos stubs/comentários se houver referência ao tipo antigo; rodar `openspec validate fix-pong-paddle-as-characterbody`.
