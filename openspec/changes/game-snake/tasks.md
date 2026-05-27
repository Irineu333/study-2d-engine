## 1. Pré-requisitos

- [x] 1.1 Verificar que `node-timer` foi arquivada (`openspec/changes/archive/*-node-timer/`). Se não, parar — `game-snake` depende.
- [x] 1.2 Verificar se `Label` tem propriedade `visible: Boolean` no `:engine` atual. Se sim, usar; se não, usar workaround `color.a = 0.0`/`1.0` em todo o jogo (decidido no design).

## 2. Módulo Gradle

- [x] 2.1 Criar `:games:snake/` ao lado de `:games:pong/`, incluir em `settings.gradle.kts`.
- [x] 2.2 `build.gradle.kts` declarando dependência em `:engine`, `:engine-skiko`, `:engine-bundle`, `:engine-bundle-python` e `kotlinx-serialization`; aplicar plugin de application com `mainClass = "com.neoutils.engine.games.snake.MainKt"`.
- [x] 2.3 Criar pacote `com.neoutils.engine.games.snake` em `src/main/kotlin/.../`.
- [x] 2.4 `Main.kt`: ~10 linhas, espelhar `:games:pong/Main.kt`.

## 3. Bundle (scene.json)

- [x] 3.1 Criar `src/main/resources/snake/` e `src/main/resources/snake/scripts/`.
- [x] 3.2 Escrever `scene.json` (version 2) com a estrutura definida no design.md: Camera2D, Snake (+ Timer filho), Food, ScoreLabel, GameOverLabel.
- [x] 3.3 Validar que o JSON carrega via `BundleLoader.fromResources("snake", scripting = python)` mesmo antes dos scripts existirem (apenas árvore vazia de comportamento).

## 4. Script: snake.py

- [x] 4.1 Cabeçalho `# extends Node2D`.
- [x] 4.2 Exports top-level: `cellSize: float = 20.0`, `startCell: Vec2 = Vec2(10.0, 10.0)`.
- [x] 4.3 Signals top-level: `foodEaten: Signal = signal()`, `gameOver: Signal = signal()`, `restart: Signal = signal()`.
- [x] 4.4 `_ready`: inicializar `self._direction`, `self._pending`, `self._dead`, `self._cells` (lista de Vec2 com posições dos segmentos atuais); chamar `_spawn_initial()` para criar 3 ColorRect filhos centralizados; resolver `MoveTimer` filho e `Camera2D` (via parent); conectar `MoveTimer.timeout` ao handler `_tick`.
- [x] 4.5 `_spawn_initial(self)`: usa `cellSize` e `startCell` para criar 3 ColorRect filhos enfileirados horizontalmente; popula `self._cells`.
- [x] 4.6 `_process(self, dt)`: lê setas via `wasKeyPressed`; bloqueia reversão 180°; atualiza `self._pending`. Se `self._dead and wasKeyPressed(Key.Enter)`: chama `self.reset()`.
- [x] 4.7 `_tick(self)` (handler conectado a MoveTimer.timeout): se `self._dead`, return. Aplica `self._pending` se houver. Calcula nova cabeça. Wrap modulo `bounds.size`. Checa se nova posição == food.cell: se sim, marca `grew=True`. Checa se nova posição em `self._cells[1:]`: se sim, emite `gameOver`, `MoveTimer.stop()`, `self._dead=True`, return. Cria novo ColorRect filho na nova posição (head); insere posição no início de `self._cells`. Se `not grew`: removeChild da cauda; remove última posição de `self._cells`. Se `grew`: emite `foodEaten`.
- [x] 4.8 `reset(self)`: loop removendo todos os filhos ColorRect; zera estado interno; chama `_spawn_initial`; emite `restart`; `MoveTimer.start()`.
- [x] 4.9 Helper local `_cell_to_pos(cellSize, cellXY) -> Vec2` e `_pos_to_cell(cellSize, pos) -> Vec2` para conversão grid ↔ pixel.

## 5. Script: food.py

- [x] 5.1 Cabeçalho `# extends ColorRect`.
- [x] 5.2 `_ready`: resolver script_of(self.parent.findChild("Snake")); conectar `snake.foodEaten` ao reposicionamento; conectar `snake.restart` ao reposicionamento. Forçar uma escolha inicial (food já posicionada via scene.json em (200,200), opcional re-randomizar no ready).
- [x] 5.3 Handler `_reposition(self)`: enumera todas as células do grid 20×20; subtrai as posições atuais em `snake._cells`; escolhe uniformemente entre as restantes; converte para pixel via `cellSize` e seta `self.transform.position`.

## 6. Script: score.py

- [x] 6.1 Cabeçalho `# extends Label`.
- [x] 6.2 `_ready`: conectar `snake.foodEaten` (incrementa contador) e `snake.restart` (zera contador).
- [x] 6.3 Após cada update do contador, setar `self.text = f"Score: {n}"`.

## 7. Script: gameover.py

- [x] 7.1 Cabeçalho `# extends Label`.
- [x] 7.2 `_ready`: garantir invisível no start (`self.color = Color(r,g,b, 0.0)`); conectar `snake.gameOver` (tornar visível `color.a = 1.0`) e `snake.restart` (esconder `color.a = 0.0`). Se `visible` existir, usar diretamente.

## 8. Documentação

- [x] 8.1 Atualizar `CLAUDE.md` adicionando seção "Para rodar Snake" com comandos e controles (setas, Enter para restart, F1 FPS, F2 colliders — F2 sem efeito por não ter colliders).
- [x] 8.2 Atualizar `ROADMAP.md`: remover `game-snake` de "Planned" enquanto a change está em "Active".

## 9. Verificação funcional manual

- [x] 9.1 `./gradlew :games:snake:run` abre janela e o jogo começa imediatamente (autostart=true).
- [x] 9.2 Cobra se move para direita por padrão; setas mudam direção.
- [x] 9.3 ← imediatamente após direção direita é ignorado.
- [x] 9.4 Cobra come comida; comprimento aumenta; comida reposiciona; score sobe.
- [x] 9.5 Cobra atravessa borda direita → reaparece na esquerda (e simétricos para 3 outras bordas).
- [x] 9.6 Cobra colide consigo mesma → `GameOverLabel` aparece, movimento para.
- [x] 9.7 Enter após game over → cobra renasce no centro com comprimento 3, score zera, label some, jogo segue.
- [x] 9.8 F1 alterna overlay de FPS.

## 10. Verificação automatizada

- [x] 10.1 Em `:games:snake/src/test/kotlin/`, teste que `BundleLoader.fromResources("snake", scripting = python)` carrega sem erros e produz a árvore esperada (Camera2D, Snake+Timer, Food, labels).
- [x] 10.2 Teste smoke: dado `Snake` com setup inicial, simular 5 ticks (chamar `_tick` via reflection do script — ou simular `MoveTimer.timeout.emit()`) e validar que a cobra avançou 5 células à direita (com wrap).
- [x] 10.3 `openspec verify game-snake` sem gaps. (`openspec validate game-snake` → "Change 'game-snake' is valid")

## 11. Fixes descobertos durante apply (não previstos)

Snake é o primeiro script Python a acessar constantes de `Key` em runtime (`Key.ARROW_UP`, `Key.ENTER`); Pong/TTT só usavam `Key` como anotação de tipo. Isso destapou um gap no binding do `PythonScriptHost` que precisou ser corrigido para o jogo rodar.

- [x] 11.1 Rebindar `Key` como `ProxyObject.fromMap(Key.entries.associateBy { it.name })` em `engine-bundle-python/.../PythonScriptHost.kt`, espelhando o tratamento já existente para `MouseButton`. O binding anterior (`Key::class.java`) só expõe métodos de instância — constantes de enum (`Key.ARROW_UP` etc.) não eram resolvidas via Polyglot, crashando o `_process` da Snake na primeira leitura de input.
- [x] 11.2 Mover `preferredSize = Dimension(config.width, config.height)` do `JFrame` para o `skiaLayer` em `engine-skiko/.../SkikoHost.kt` e deixar `frame.pack()` envolver a chrome. Antes, `config.width/height` viravam o tamanho **externo** da janela; a chrome (titlebar etc.) comia parte do conteúdo, e `Camera2D.bounds` em FIT virava letterbox lateral porque a área interna deixava de ser quadrada. Agora `width × height` é o conteúdo exato e o viewport bate 1:1 com a janela. Afeta todos os jogos Skiko (Pong, Snake, Demos, Hello-World) — abrem com a mesma área de conteúdo, só a chrome do SO se acumula por fora.
- [x] 11.3 Centralizar `GameOverLabel` via `renderer.measureText` no primeiro `_draw` do `gameover.py`. `Label.onDraw` desenha o texto na origem local sem alinhamento — para centralizar no `bounds` da câmera o script captura o renderer no primeiro frame, mede o texto e reposiciona o nó. Frames seguintes pegam a posição correta; o primeiro frame já estaria invisível (`color.a = 0`), então não há flicker visível.
- [x] 11.4 Passar `None` explicitamente em `Timer.start(override)` dentro de `reset()` em `snake.py`. Kotlin gera apenas um método JVM `start(Float?)` para a assinatura `fun start(override: Float? = null)` (sem `@JvmOverloads`), e o Polyglot não materializa o overload sintético — chamar `timer.start()` sem args lança `TypeError: Arity error - expected: 1 actual: 0`. O Enter pós-game-over crashava o AWT thread no primeiro restart.
