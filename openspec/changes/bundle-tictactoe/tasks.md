## 0. Hold note

> **Em espera até `scene-tree` arquivar.** A change `scene-tree` apagou a classe `Scene` de `:engine`, removeu `engine.Scene` do `NodeRegistry`, e fez `BundleLoader.fromResources` devolver `Node` (não mais `Scene`). Ao retomar este plano, revisite tasks 2.x–3.x: o registro/`extends`/binding de `Scene` deixa de fazer sentido; o root do bundle Velha deve ser `engine.Node` (ou um `TicTacToeRoot` registrado pelo jogo) e o `Main.kt` deve envolver o retorno do `BundleLoader` em `SceneTree(root = ...)` antes de passar ao `ComposeHost`.

## 1. Build deps

- [ ] 1.1 Em `games/tictactoe/build.gradle.kts`: adicionar `implementation(projects.engineBundle)` e `implementation(projects.engineBundlePython)`.

## 2. NodeRegistry registra Scene

- [ ] 2.1 Em `:engine-bundle/NodeRegistry` (ou onde a tabela vive hoje): adicionar registro `"com.neoutils.engine.scene.Scene" → Scene::class`. Documentar via KDoc que `Scene` é registrável **apenas como root** de um bundle — usar como filho não-root é programar erro mas não detectável aqui.

## 3. BundleLoader aceita Scene no root

- [ ] 3.1 Em `BundleLoader.fromResources` (e `fromPath`): verificar o caminho atual de construção do root. Se hoje o root é sempre `Scene()` independente do `type` no JSON, modificar para: ler `root.type` do JSON, resolver via `NodeRegistry`, instanciar. Se `type` ausente, manter fallback para `Scene()` plain.
- [ ] 3.2 Verificar que `BundleLoader.attachScript` funciona quando o target é uma `Scene` (root). Em particular, validar que `# extends Scene` na primeira linha do script é aceito pelo `PythonScriptHost` (precisa de binding `Scene` no Context — adicionar se ausente).
- [ ] 3.3 Em `PythonScriptHost.kt`: na lista de tipos válidos para `# extends`, adicionar `Scene`.
- [ ] 3.4 Em `PythonScriptHost.kt`: nos bindings do Context, expor `Scene` como tipo acessível (para que `# extends Scene` resolva).

## 4. scene.json

- [ ] 4.1 Criar `games/tictactoe/src/main/resources/tictactoe/scene.json` com a estrutura:
  ```json
  {
    "version": 1,
    "root": {
      "type": "com.neoutils.engine.scene.Scene",
      "name": "TicTacToeScene",
      "script": "scripts/board.py",
      "children": [
        { "type": "com.neoutils.engine.scene.Camera2D",
          "name": "camera",
          "properties": { "bounds": { "origin": {"x":0,"y":0}, "size": {"x":600,"y":600} }, "current": true } },
        { "type": "com.neoutils.engine.scene.Line2D",
          "name": "grid_v1",
          "properties": {
            "points": [{"x":220,"y":60},{"x":220,"y":540}],
            "thickness": 4.0,
            "color": {"r":0.9,"g":0.9,"b":0.9,"a":1.0}
          } },
        { "type": "com.neoutils.engine.scene.Line2D",
          "name": "grid_v2",
          "properties": {
            "points": [{"x":380,"y":60},{"x":380,"y":540}],
            "thickness": 4.0,
            "color": {"r":0.9,"g":0.9,"b":0.9,"a":1.0}
          } },
        { "type": "com.neoutils.engine.scene.Line2D",
          "name": "grid_h1",
          "properties": {
            "points": [{"x":60,"y":220},{"x":540,"y":220}],
            "thickness": 4.0,
            "color": {"r":0.9,"g":0.9,"b":0.9,"a":1.0}
          } },
        { "type": "com.neoutils.engine.scene.Line2D",
          "name": "grid_h2",
          "properties": {
            "points": [{"x":60,"y":380},{"x":540,"y":380}],
            "thickness": 4.0,
            "color": {"r":0.9,"g":0.9,"b":0.9,"a":1.0}
          } },
        { "type": "com.neoutils.engine.scene.Label",
          "name": "status",
          "properties": {
            "transform": { "position": {"x":300,"y":24}, "scale": {"x":1,"y":1}, "rotation": 0 },
            "text": "Vez de X",
            "size": 22.0,
            "color": {"r":1,"g":1,"b":1,"a":1}
          } }
      ]
    }
  }
  ```
  (Posições verificadas no smoke test; ajustar se necessário.)

## 5. scripts/board.py

- [ ] 5.1 Criar `games/tictactoe/src/main/resources/tictactoe/scripts/board.py` com:
  - `# extends Scene` na primeira linha.
  - Estado: `_cells: list`, `_current_player: str`, `_winner: str | None`, `_is_draw: bool`, `_winning_line: tuple | None`, `_hovered: int | None`, `_status: NodeRef`.
  - Constantes de layout: `BOARD_ORIGIN = Vec2(60, 60)`, `CELL_SIZE = 160`, `MARK_INSET = 0.18`, `MARK_THICKNESS = 0.08`, `WIN_THICKNESS = 0.12`.
  - Linhas vencedoras: `WINNING_LINES = [(0,1,2),(3,4,5),(6,7,8),(0,3,6),(1,4,7),(2,5,8),(0,4,8),(2,4,6)]`.
  - `_ready(self)`: inicializa estado, resolve `self._status = NodeRef("status").resolve(self)` (ou via API equivalente).
  - `_cell_rect(self, index): Rect` — calcula bounds da célula.
  - `_cell_at(self, point: Vec2) -> int | None` — varredura inversa.
  - `_place_move(self, index)` — coloca, checa vitória, alterna jogador.
  - `_check_winner(self) -> tuple | None` — verifica todas as 8 linhas.
  - `_reset(self)` — limpa estado.
  - `_process(self, dt)`: lê `input.pointerPosition`, atualiza `self._hovered`; se `input.wasMouseClicked(MouseButton.Left)`: se `gameOver`, reset; senão, se hovered não-None e célula vazia, `_place_move`. Atualiza `self._status.text`.
  - `_draw(self, renderer)`: para cada célula com mark, desenhar X (duas linhas cruzadas) ou O (círculo outline) via `renderer.drawLine`/`renderer.drawCircle`. Se há `_winning_line`, desenhar a linha vencedora. Se há `_hovered` e célula vazia e não-game-over, desenhar mark ghost com alpha 0.3.
- [ ] 5.2 Garantir que o script importa `MouseButton` apenas se necessário (provavelmente sim via binding implícito).
- [ ] 5.3 Garantir que `Rect` retornado por `_cell_rect` use o binding do `Rect` do Context (não recriar localmente).

## 6. Main.kt

- [ ] 6.1 Reescrever `games/tictactoe/src/main/kotlin/.../Main.kt` para o conteúdo:
  ```kotlin
  package com.neoutils.engine.games.tictactoe

  import com.neoutils.engine.bundle.BundleLoader
  import com.neoutils.engine.bundle.python.PythonScriptHost
  import com.neoutils.engine.compose.ComposeHost
  import com.neoutils.engine.runtime.GameConfig

  fun main() {
      PythonScriptHost.install()
      val scene = BundleLoader.fromResources("tictactoe")
      ComposeHost().run(
          scene = scene,
          config = GameConfig(title = "Tic Tac Toe", width = 600, height = 600),
      )
  }
  ```

## 7. Apagar tipos Kotlin específicos

- [ ] 7.1 Deletar `games/tictactoe/src/main/kotlin/.../Board.kt`.
- [ ] 7.2 Deletar `games/tictactoe/src/main/kotlin/.../Mark.kt`.
- [ ] 7.3 Deletar `games/tictactoe/src/main/kotlin/.../StatusText.kt`.
- [ ] 7.4 Deletar `games/tictactoe/src/main/kotlin/.../TicTacToeScene.kt`.
- [ ] 7.5 Verificar via `grep -r 'TicTacToeScene\|StatusText\|Board\b' games/tictactoe/` que não há referências sobrando.

## 8. Docs

- [ ] 8.1 `CLAUDE.md`: seção "Module Structure & How to Run" — atualizar parágrafo da Velha mencionando que ela agora carrega via `BundleLoader.fromResources("tictactoe")` igual Pong; mencionar que o backend `ComposeHost` consome a `Scene` produzida pelo bundle sem ajuste.
- [ ] 8.2 `CLAUDE.md`: seção "Scripting contract" — adicionar nota curta de que `# extends Scene` é permitido **apenas** para o script no root do bundle (orquestrador).
- [ ] 8.3 `CLAUDE.md`: tabela roadmap — adicionar linha `bundle-tictactoe` com status `Active` e resumo: "Migra TicTacToe para BundleLoader + scripts/*.py rodando em ComposeHost; valida que bundle+Python são backend-agnósticos."

## 9. Smoke & verify

- [ ] 9.1 `./gradlew check` passa.
- [ ] 9.2 `./gradlew :games:tictactoe:run` abre — janela 600×600, grade visível, status "Vez de X".
- [ ] 9.3 Clique numa célula vazia coloca X; próximo clique noutra coloca O; alterna corretamente.
- [ ] 9.4 Linha vencedora aparece quando 3 em linha; status muda para "X venceu — clique para jogar de novo" (ou equivalente). Próximo clique reseta.
- [ ] 9.5 Empate quando 9 jogadas sem vencedor; status muda para "Empate — clique para jogar de novo". Próximo clique reseta.
- [ ] 9.6 F1 (FPS overlay) e F2 (collider overlay vazio — Velha sem colliders) funcionam.
- [ ] 9.7 `./gradlew :games:pong:run` ainda funciona (nenhuma regressão em Skiko+bundle).
- [ ] 9.8 `openspec validate bundle-tictactoe --strict` passa.
