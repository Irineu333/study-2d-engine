## Why

Pong validou a fundação em **movimento contínuo** (60Hz, física, colliders). Velha validou o **segundo backend** (Compose) e input por mouse. Falta validar a fundação em **gameplay discreto** — tick-based, grid-based, com mutação dinâmica densa do scene graph dirigida por script. Snake é o vetor canônico para isso.

Snake também exercita combinações ainda não cobertas: `Camera2D.bounds` como contrato lógico do mundo (não só viewport visível), wraparound de coordenadas, input edge-triggered (`wasKeyPressed`) com buffer de direção, e — via `node-timer` — signal Kotlin `Timer.timeout` conectado em handler Python. Não introduz fundação nova além do que `node-timer` já entrega; é puramente um validador.

## What Changes

- Adiciona o módulo executável `:games:snake` com `Main.kt`, dependências em `:engine`, `:engine-skiko`, `:engine-bundle`, `:engine-bundle-python` e `kotlinx-serialization`.
- Adiciona bundle `snake/` em `src/main/resources/snake/` contendo `scene.json` e `scripts/snake.py`, `scripts/food.py`.
- Cena Snake:
  - `Camera2D` com `current=true` e `bounds=Rect(0,0, 400,400)` definindo o campo.
  - `Snake` (Node2D, script `snake.py`) contendo um `Timer` filho (`MoveTimer`, `waitTime=0.125`, `processCallback=PHYSICS`, `autostart=true`) e segmentos `ColorRect` filhos criados/removidos em runtime.
  - `Food` (ColorRect, script `food.py`) representando a comida.
  - HUD com `ScoreLabel` (Label) e `GameOverLabel` (Label, visible=false até game over).
- Mecânica:
  - A cada `timeout` do `MoveTimer`, `snake.py` avança a cabeça uma célula (`20px`) na direção corrente; cria novo `ColorRect` filho na nova posição (head); remove o último filho (cauda) a menos que tenha comido neste tick.
  - Direção corrente lida em `_process` via `wasKeyPressed(ArrowUp/Down/Left/Right)`; ignora reverter (se vai pra direita, ← é ignorado).
  - Wraparound: posição da cabeça vira `pos mod bounds.size`. Saída pela direita reentra pela esquerda. Sem game over por parede.
  - Auto-colisão (cabeça encontra posição de qualquer segmento corpo) emite `gameOver` e dispara `MoveTimer.stop()`.
  - Quando a cabeça pisa na célula da `Food`, `snake.py` emite `foodEaten` (Signal Python); `food.py` escuta, reposiciona em célula aleatória vazia; HUD escuta e incrementa score.
  - Restart por `wasKeyPressed(Enter)` quando em estado game-over: `snake.py.reset()` remove todos os filhos segmentos, recria cabeça inicial, zera direção, esconde `GameOverLabel`, reposiciona `Food`, reinicia `MoveTimer.start()`.
- Atualiza `CLAUDE.md` com seção "Para rodar Snake".
- Atualiza `ROADMAP.md`: remove `game-snake` da seção Planned (passa a Active enquanto a change roda).

## Capabilities

### New Capabilities
- `snake-sample`: jogo Snake jogável como módulo executável `:games:snake`, primeira validação end-to-end de gameplay discreto/grid-based, mutação dinâmica de scene graph via script, wraparound em `Camera2D.bounds`, e ponte Kotlin Signal → Python handler através de `Timer.timeout`.

### Modified Capabilities
- nenhuma.

## Impact

- **Novo módulo**: `:games:snake` (~análogo a `:games:pong` em estrutura). Adicionar entrada no `settings.gradle.kts`.
- **Dependência hard** em `node-timer` (que ainda não está implementada). Esta change SOMENTE pode entrar em `apply` depois que `node-timer` estiver archived.
- **Sem mudança em `:engine`** — toda a lógica de Snake vive em Python. Se durante a implementação aparecer uma necessidade de API nova no `:engine`, é sinal de que `node-timer` não cobriu algo e a engine precisa de outra change separada (não inflar `game-snake`).
- **Sem mudança em `:engine-bundle`** além das já cobertas por `node-timer`.
- **`:games:demos`** não é afetado, mas o desenvolvedor pode opcionalmente comparar a cena de Timer (demos #6) com Snake como validação cruzada.
- **Pré-requisito de testes manuais**: rodar `./gradlew :games:snake:run`, brincar, validar setas, restart, wraparound, score.
