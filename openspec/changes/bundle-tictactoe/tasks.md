## 0. Hold note (resolved)

> **Resolvido em 2026-05-26.** Quando este plano foi escrito originalmente, ele assumia que `Scene` ainda era uma classe de `:engine` que podia ser registrada no `NodeRegistry` e usada como root do bundle (`# extends Scene`, `type: "com.neoutils.engine.scene.Scene"`). A change `scene-tree` (arquivada em 2026-05-24) apagou `Scene` por completo: o root agora é um `Node` qualquer e a vida da árvore mora em `SceneTree(root = ...)`, que **não** é um `Node`.
>
> Tasks 2.x e 3.x foram reescritas para refletir essa realidade: o root da Velha é `com.neoutils.engine.scene.Node` (já registrado no `NodeRegistry`), o script usa `# extends Node` (já suportado pelo `PythonScriptHost`), e `Main.kt` envolve o retorno do `BundleLoader` em `SceneTree(root = ...)` antes de passar ao `ComposeHost`. Só falta expor `MouseButton` como binding Python — Pong não usa mouse, então a ergonomia nunca foi exercitada.

## 1. Build deps

- [x] 1.1 Em `games/tictactoe/build.gradle.kts`: adicionar `implementation(projects.engineBundle)` e `implementation(projects.engineBundlePython)`.

## 2. MouseButton binding em PythonScriptHost

- [x] 2.1 Em `engine-bundle-python/src/main/kotlin/.../PythonScriptHost.kt`: adicionar `bindings.putMember("MouseButton", MouseButton::class.java)` junto aos outros bindings de classe (logo após `Key`).
- [x] 2.2 Em `engine-bundle-python/src/main/resources/_nengine_runtime.py`: expor `MouseButton` no namespace de `_nengine_load_module` (perto de `Key`) para que scripts possam usar `MouseButton.Left` sem import.
- [x] 2.3 Em `engine-bundle-python/src/main/resources/stubs/engine/input.pyi`: adicionar stub para `MouseButton` (`Left`, `Right`, `Middle`) e os métodos `is_mouse_down(...)` / `was_mouse_clicked(...)` no `Input`.

## 3. scene.json

- [x] 3.1 Criar `games/tictactoe/src/main/resources/tictactoe/scene.json` com root `Node` + script `scripts/board.py` + filhos `Camera2D` (bounds 600×600, `current=true`), quatro `Line2D` (grid 3×3 no retângulo (60,60)..(540,540), cellSize 160), e um `Label` `status` posicionado no topo. Vide proposal.md para o layout exato.

## 4. scripts/board.py

- [x] 4.1 Criar `games/tictactoe/src/main/resources/tictactoe/scripts/board.py` com:
  - `# extends Node` na primeira linha.
  - Estado: `_cells: list`, `_current_player: str`, `_winner: str | None`, `_is_draw: bool`, `_winning_line: tuple | None`, `_hovered: int | None`, `_status` (resolvido via `NodeRef("status").resolve(self._node)` em `_ready`).
  - Constantes de layout: `BOARD_ORIGIN = Vec2(60, 60)`, `CELL_SIZE = 160`, `MARK_INSET = 0.18`, `MARK_THICKNESS = 0.08`, `WIN_THICKNESS = 0.12`.
  - Linhas vencedoras: `WINNING_LINES = [(0,1,2),(3,4,5),(6,7,8),(0,3,6),(1,4,7),(2,5,8),(0,4,8),(2,4,6)]`.
  - `_ready(self)`: inicializa estado, resolve `self._status` via `NodeRef`, escreve `_update_status_text`.
  - `_cell_rect(self, index) -> Rect` — calcula bounds da célula via `BOARD_ORIGIN` + `CELL_SIZE`.
  - `_cell_at(self, point: Vec2) -> int | None` — varredura linear sobre `_cell_rect`.
  - `_place_move(self, index)` — coloca mark, checa vitória, alterna jogador.
  - `_check_winner(self) -> tuple | None` — verifica todas as 8 linhas.
  - `_reset(self)` — limpa estado.
  - `_process(self, dt)`: lê `tree.input.pointerPosition` + projeção via `tree.screenToWorld`, atualiza `_hovered`. Se `input.wasMouseClicked(MouseButton.Left)`: se `gameOver`, reset; senão, se hovered não-None e célula vazia, `_place_move`. Atualiza `self._status.text`.
  - `_draw(self, renderer)`: para cada célula com mark, desenhar X (duas linhas cruzadas) ou O (círculo outline). Se há `_winning_line`, desenhar a linha vencedora. Se há `_hovered` e célula vazia e não-game-over, desenhar ghost com alpha 0.3.
- [x] 4.2 `Rect` em `_cell_rect` usa o binding `Rect(origin, size)` do Context (não recriar localmente).
- [x] 4.3 `MouseButton.Left` é acessado via binding implícito (sem `import`).

## 5. Main.kt

- [x] 5.1 Reescrever `games/tictactoe/src/main/kotlin/.../Main.kt` para:
  ```kotlin
  package com.neoutils.engine.games.tictactoe

  import com.neoutils.engine.bundle.BundleLoader
  import com.neoutils.engine.bundle.python.PythonScriptHost
  import com.neoutils.engine.compose.ComposeHost
  import com.neoutils.engine.runtime.GameConfig
  import com.neoutils.engine.tree.SceneTree

  fun main() {
      val python = PythonScriptHost.create()
      val root = BundleLoader.fromResources("tictactoe", scripting = python)
      ComposeHost().run(
          tree = SceneTree(root = root),
          config = GameConfig(title = "Tic Tac Toe", width = 600, height = 600),
      )
  }
  ```

## 6. Apagar tipos Kotlin específicos

- [x] 6.1 Deletar `games/tictactoe/src/main/kotlin/.../Board.kt`.
- [x] 6.2 Deletar `games/tictactoe/src/main/kotlin/.../Mark.kt`.
- [x] 6.3 Deletar `games/tictactoe/src/main/kotlin/.../StatusText.kt`.
- [x] 6.4 Deletar `games/tictactoe/src/main/kotlin/.../TicTacToeRoot.kt` (originalmente chamado `TicTacToeScene` no plano; renomeado por `scene-tree`).
- [x] 6.5 Verificar via `grep -r 'TicTacToeRoot\|TicTacToeScene\|StatusText\|\bBoard\b\|\bMark\b' games/tictactoe/src/main/kotlin/` que não há referências sobrando.

## 7. Docs

- [x] 7.1 `CLAUDE.md`: seção "Module Structure & How to Run" — atualizar parágrafo da Velha mencionando que ela agora carrega via `BundleLoader.fromResources("tictactoe")` igual Pong; mencionar que o backend `ComposeHost` consome a `SceneTree` produzida sem ajuste.
- [x] 7.2 `CLAUDE.md`: seção "Scripting contract" — adicionar nota de que `MouseButton` (binding implícito, valores `MouseButton.Left`/`Right`/`Middle`) está disponível para scripts que precisam de mouse discreto, e exemplo curto de `input.wasMouseClicked(MouseButton.Left)`.

## 8. Smoke & verify

- [x] 8.1 `./gradlew check` passa.
- [x] 8.2 `./gradlew :games:tictactoe:run` abre — janela 600×600, grade visível, status "Vez de X" (verificado por boot smoke de ~25s sem crash).
- [x] 8.3 Clique numa célula vazia coloca X; próximo clique noutra coloca O; alterna corretamente.
- [x] 8.4 Linha vencedora aparece quando 3 em linha; status muda para "X venceu — clique para jogar de novo" (ou equivalente). Próximo clique reseta.
- [x] 8.5 Empate quando 9 jogadas sem vencedor; status muda para "Empate — clique para jogar de novo". Próximo clique reseta.
- [x] 8.6 F1 (FPS overlay) e F2 (collider overlay vazio — Velha sem colliders) funcionam.
- [x] 8.7 `./gradlew :games:pong:run` ainda funciona (nenhuma regressão em Skiko+bundle).
- [x] 8.8 `openspec validate bundle-tictactoe --strict` passa.
