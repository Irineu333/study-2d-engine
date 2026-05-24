## 1. Pré-requisitos

- [ ] 1.1 Verificar que `node-timer` foi arquivada (`openspec/changes/archive/*-node-timer/`). Se não, parar — `game-snake` depende.
- [ ] 1.2 Verificar se `Label` tem propriedade `visible: Boolean` no `:engine` atual. Se sim, usar; se não, usar workaround `color.a = 0.0`/`1.0` em todo o jogo (decidido no design).

## 2. Módulo Gradle

- [ ] 2.1 Criar `:games:snake/` ao lado de `:games:pong/`, incluir em `settings.gradle.kts`.
- [ ] 2.2 `build.gradle.kts` declarando dependência em `:engine`, `:engine-skiko`, `:engine-bundle`, `:engine-bundle-python` e `kotlinx-serialization`; aplicar plugin de application com `mainClass = "com.neoutils.engine.games.snake.MainKt"`.
- [ ] 2.3 Criar pacote `com.neoutils.engine.games.snake` em `src/main/kotlin/.../`.
- [ ] 2.4 `Main.kt`: ~10 linhas, espelhar `:games:pong/Main.kt`.

## 3. Bundle (scene.json)

- [ ] 3.1 Criar `src/main/resources/snake/` e `src/main/resources/snake/scripts/`.
- [ ] 3.2 Escrever `scene.json` (version 2) com a estrutura definida no design.md: Camera2D, Snake (+ Timer filho), Food, ScoreLabel, GameOverLabel.
- [ ] 3.3 Validar que o JSON carrega via `BundleLoader.fromResources("snake", scripting = python)` mesmo antes dos scripts existirem (apenas árvore vazia de comportamento).

## 4. Script: snake.py

- [ ] 4.1 Cabeçalho `# extends Node2D`.
- [ ] 4.2 Exports top-level: `cellSize: float = 20.0`, `startCell: Vec2 = Vec2(10.0, 10.0)`.
- [ ] 4.3 Signals top-level: `foodEaten: Signal = signal()`, `gameOver: Signal = signal()`, `restart: Signal = signal()`.
- [ ] 4.4 `_ready`: inicializar `self._direction`, `self._pending`, `self._dead`, `self._cells` (lista de Vec2 com posições dos segmentos atuais); chamar `_spawn_initial()` para criar 3 ColorRect filhos centralizados; resolver `MoveTimer` filho e `Camera2D` (via parent); conectar `MoveTimer.timeout` ao handler `_tick`.
- [ ] 4.5 `_spawn_initial(self)`: usa `cellSize` e `startCell` para criar 3 ColorRect filhos enfileirados horizontalmente; popula `self._cells`.
- [ ] 4.6 `_process(self, dt)`: lê setas via `wasKeyPressed`; bloqueia reversão 180°; atualiza `self._pending`. Se `self._dead and wasKeyPressed(Key.Enter)`: chama `self.reset()`.
- [ ] 4.7 `_tick(self)` (handler conectado a MoveTimer.timeout): se `self._dead`, return. Aplica `self._pending` se houver. Calcula nova cabeça. Wrap modulo `bounds.size`. Checa se nova posição == food.cell: se sim, marca `grew=True`. Checa se nova posição em `self._cells[1:]`: se sim, emite `gameOver`, `MoveTimer.stop()`, `self._dead=True`, return. Cria novo ColorRect filho na nova posição (head); insere posição no início de `self._cells`. Se `not grew`: removeChild da cauda; remove última posição de `self._cells`. Se `grew`: emite `foodEaten`.
- [ ] 4.8 `reset(self)`: loop removendo todos os filhos ColorRect; zera estado interno; chama `_spawn_initial`; emite `restart`; `MoveTimer.start()`.
- [ ] 4.9 Helper local `_cell_to_pos(cellSize, cellXY) -> Vec2` e `_pos_to_cell(cellSize, pos) -> Vec2` para conversão grid ↔ pixel.

## 5. Script: food.py

- [ ] 5.1 Cabeçalho `# extends ColorRect`.
- [ ] 5.2 `_ready`: resolver script_of(self.parent.findChild("Snake")); conectar `snake.foodEaten` ao reposicionamento; conectar `snake.restart` ao reposicionamento. Forçar uma escolha inicial (food já posicionada via scene.json em (200,200), opcional re-randomizar no ready).
- [ ] 5.3 Handler `_reposition(self)`: enumera todas as células do grid 20×20; subtrai as posições atuais em `snake._cells`; escolhe uniformemente entre as restantes; converte para pixel via `cellSize` e seta `self.transform.position`.

## 6. Script: score.py

- [ ] 6.1 Cabeçalho `# extends Label`.
- [ ] 6.2 `_ready`: conectar `snake.foodEaten` (incrementa contador) e `snake.restart` (zera contador).
- [ ] 6.3 Após cada update do contador, setar `self.text = f"Score: {n}"`.

## 7. Script: gameover.py

- [ ] 7.1 Cabeçalho `# extends Label`.
- [ ] 7.2 `_ready`: garantir invisível no start (`self.color = Color(r,g,b, 0.0)`); conectar `snake.gameOver` (tornar visível `color.a = 1.0`) e `snake.restart` (esconder `color.a = 0.0`). Se `visible` existir, usar diretamente.

## 8. Documentação

- [ ] 8.1 Atualizar `CLAUDE.md` adicionando seção "Para rodar Snake" com comandos e controles (setas, Enter para restart, F1 FPS, F2 colliders — F2 sem efeito por não ter colliders).
- [ ] 8.2 Atualizar `ROADMAP.md`: remover `game-snake` de "Planned" enquanto a change está em "Active".

## 9. Verificação funcional manual

- [ ] 9.1 `./gradlew :games:snake:run` abre janela e o jogo começa imediatamente (autostart=true).
- [ ] 9.2 Cobra se move para direita por padrão; setas mudam direção.
- [ ] 9.3 ← imediatamente após direção direita é ignorado.
- [ ] 9.4 Cobra come comida; comprimento aumenta; comida reposiciona; score sobe.
- [ ] 9.5 Cobra atravessa borda direita → reaparece na esquerda (e simétricos para 3 outras bordas).
- [ ] 9.6 Cobra colide consigo mesma → `GameOverLabel` aparece, movimento para.
- [ ] 9.7 Enter após game over → cobra renasce no centro com comprimento 3, score zera, label some, jogo segue.
- [ ] 9.8 F1 alterna overlay de FPS.

## 10. Verificação automatizada

- [ ] 10.1 Em `:games:snake/src/test/kotlin/`, teste que `BundleLoader.fromResources("snake", scripting = python)` carrega sem erros e produz a árvore esperada (Camera2D, Snake+Timer, Food, labels).
- [ ] 10.2 Teste smoke: dado `Snake` com setup inicial, simular 5 ticks (chamar `_tick` via reflection do script — ou simular `MoveTimer.timeout.emit()`) e validar que a cobra avançou 5 células à direita (com wrap).
- [ ] 10.3 `openspec verify game-snake` sem gaps.
