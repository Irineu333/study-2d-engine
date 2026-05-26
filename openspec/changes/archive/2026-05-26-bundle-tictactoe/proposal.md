## Why

Após `godot-style-foundation` e `collision-overhaul`, a engine está com o vocabulário Godot consolidado e o modelo de colisão refeito. Mas o jogo da Velha permanece **Kotlin-puro**: `Board.kt`, `Mark.kt`, `StatusText.kt`, `TicTacToeScene.kt` são tipos `Node`/`Scene` declarados em Kotlin, instanciados pelo `Main.kt` e entregues ao `ComposeHost`. Isso significa que o pipeline `BundleLoader → ScriptHost → Scene` que move o Pong nunca foi exercitado dentro do backend Compose.

Como invariante arquitetural, `BundleLoader` e `PythonScriptHost` **devem ser backend-agnósticos** — a `Scene` produzida por `BundleLoader.fromResources(...)` deve rodar tanto no `SkikoHost` quanto no `ComposeHost` sem ajuste. A única prova viva disso hoje é o Pong, que só roda em Skiko. Falta um caso onde o mesmo pipeline emite uma cena consumida por Compose.

Esta change converte `:games:tictactoe` em um bundle `tictactoe/` com `scene.json` + `scripts/*.py`, carregado via `BundleLoader.fromResources("tictactoe")` no `Main.kt`, e entregue ao `ComposeHost.run(scene)`. Os tipos Kotlin específicos do jogo (`Board`, `Mark`, `StatusText`, `TicTacToeScene`) são **apagados**; toda a lógica do jogo passa para `scripts/board.py`. A grade (linhas do tabuleiro) e o status text viram nodes declarativos da engine (`Line2D`, `Label`) no `scene.json`.

Esta é a prova viva de que o backend Compose engole bundle+Python idêntico ao Skiko.

## What Changes

### Estrutura do bundle

Novo diretório `games/tictactoe/src/main/resources/tictactoe/` contendo:

```
tictactoe/
├── scene.json
└── scripts/
    └── board.py
```

`scene.json` declara:

- Root `Scene` com `script: "scripts/board.py"` (o script orchestra o jogo).
- `Camera2D` filha com `current: true`, `bounds = Rect(Vec2.ZERO, Vec2(600f, 600f))`.
- Quatro `Line2D` filhas — as duas verticais e duas horizontais da grade 3×3, em posições dependentes do tamanho do tabuleiro. (Posições absolutas declarativas; o script lê e desenha marks por cima.)
- `Label` filha `status` para o texto de status (vez de X / vencedor / empate / restart).

`scripts/board.py`:

- `# extends Scene` (o script vai no root porque é orquestrador).
- Estado: array de 9 células (X/O/None), jogador atual, vencedor, empate, linha vencedora.
- Hooks:
  - `_ready`: resolve `status` via `NodeRef`, configura layout (origin, cellSize) baseado em `self.viewport.size`.
  - `_process(dt)`: lê input (`scene.input.wasMouseClicked + pointerPosition`), aplica jogada ou restart.
  - `_draw(renderer)`: desenha marks (X e O) e linha vencedora.
- Lógica de checkWinner (8 linhas vencedoras), placeMove, reset — todas em Python.

### Arquivos apagados

- **REMOVED** `games/tictactoe/src/main/kotlin/.../Board.kt`
- **REMOVED** `games/tictactoe/src/main/kotlin/.../Mark.kt`
- **REMOVED** `games/tictactoe/src/main/kotlin/.../StatusText.kt`
- **REMOVED** `games/tictactoe/src/main/kotlin/.../TicTacToeScene.kt`

O único `.kt` que sobrevive é `Main.kt`, reescrito para o pipeline bundle.

### `Main.kt` reescrito

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

### `build.gradle.kts` do TicTacToe ganha dependências

```kotlin
dependencies {
    implementation(projects.engine)
    implementation(projects.engineCompose)
    implementation(projects.engineBundle)
    implementation(projects.engineBundlePython)
    // demais (compose.desktop, coroutines) inalterados
}
```

### NodeRegistry

`Scene` ainda **não** está no `NodeRegistry` (até hoje só `Node2D`, `Camera2D`, `Label`, `ColorRect`, etc. estão registrados — não a raiz `Scene`). Esta change registra `com.neoutils.engine.scene.Scene` no `NodeRegistry` para que `BundleLoader` consiga instanciar o root quando o `scene.json` o nomeia explicitamente, ou — alternativa decidida no design — `BundleLoader` ganha um caminho especial que aceita `type: "com.neoutils.engine.scene.Scene"` no root. Decisão em `design.md`.

### Roadmap

- Adiciona `bundle-tictactoe` ao roadmap com status `Active`.
- **Não adiciona um jogo novo ao roadmap** — esta change é, ela própria, a migração de um jogo existente para o bundle, e serve como validação do invariante "BundleLoader+Python rodam em Compose".

## Capabilities

### New Capabilities

- (nenhuma)

### Modified Capabilities

- `tictactoe-sample`: a velha vira bundle (`scene.json` + `scripts/board.py`), todos os `Node`/`Scene` Kotlin específicos do jogo são apagados; o único Kotlin restante é `Main.kt` orquestrando o pipeline.
- `bundle-loading`: scenario explícito de que `BundleLoader.fromResources(...)` produz `Scene` consumível por `ComposeHost` — backend agnosticism formalizado.

## Impact

- **Código tocado:**
  - `:games:tictactoe` — apaga `Board.kt`, `Mark.kt`, `StatusText.kt`, `TicTacToeScene.kt`. Reescreve `Main.kt`. Adiciona `src/main/resources/tictactoe/scene.json` + `scripts/board.py`. Adiciona deps `engineBundle` + `engineBundlePython` em `build.gradle.kts`.
  - `:engine-bundle` — `BundleLoader` pode precisar suportar `type: "com.neoutils.engine.scene.Scene"` no root (ou `NodeRegistry` ganha registro de `Scene`). Decisão em `design.md`.
- **Documentação:** `CLAUDE.md` (seção "Module Structure & How to Run" — atualizar instruções de Velha mencionando bundle; seção "Scripting contract" — adicionar nota sobre `# extends Scene` para scripts no root, se permitido; tabela roadmap com `bundle-tictactoe` Active).
- **Sem impacto em:** colisão (Velha não usa), Skiko, Pong, Demos.
- **Sem novo jogo no roadmap.**
