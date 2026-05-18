## Why

Pong validou a engine no eixo "tempo real": loop contínuo, input contínuo, física e render simultâneos. Falta provar o eixo complementar — **interação discreta**: clicks, hit-testing, máquina de estados de jogo, feedback de UI. Velha (tic-tac-toe) é o jogo mínimo que força exatamente isso, fechando o leque de capacidades exercitadas pela fundação.

Como subproduto, identificamos três lacunas reais na SPI que esta change preenche: input de mouse não captura cliques, `Renderer` não desenha linhas (necessário para o `X` em diagonal e para a linha vencedora), e falta um helper de `Rect.contains(Vec2)` para hit-testing limpo.

## What Changes

- **Engine SPI — Input de mouse**: novo enum `MouseButton` e métodos `Input.wasMouseClicked(button)` / `Input.isMouseDown(button)`, espelhando o padrão de `wasKeyPressed`/`isKeyDown`.
- **Engine SPI — `Renderer.drawLine`**: novo método `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` para desenhar segmentos arbitrários (diagonais, marcadores, decorações).
- **Engine math — `Rect.contains`**: novo helper `Rect.contains(point: Vec2): Boolean` para hit-testing.
- **Compose backend**: `ComposeInput` passa a tratar eventos de press/release do ponteiro, com o mesmo modelo de tick que as teclas (`pendingPresses` → `pressedThisTick`). `ComposeRenderer.drawLine` implementado sobre `DrawScope.drawLine`.
- **Novo módulo `:games:tictactoe`**: jogo da velha humano vs humano, alternando turnos entre X e O, com Board monolítica (estado, render e hit-test em um nó), `StatusText` para "vez de"/"venceu"/"empate", hover ghost da marca atual na célula vazia sob o ponteiro, destaque da linha vencedora, e reset por click pós-fim. Executável via `./gradlew :games:tictactoe:run`.
- **DX**: F1 (FPS) e F2 (colliders) continuam ativos — velha não usa colliders mas o overlay de FPS é útil em qualquer jogo.

## Capabilities

### New Capabilities
- `tictactoe-sample`: jogo da velha executável (humano vs humano) como módulo `:games:tictactoe`, exercitando input de mouse, hit-testing, máquina de estados de turno e detecção de vitória/empate.

### Modified Capabilities
- `engine-core`: extensões na SPI de `Input` (mouse buttons), na SPI de `Renderer` (`drawLine`) e no `Rect` (`contains`). Invariante "engine sem Compose" permanece.
- `compose-runtime`: `ComposeInput` ganha captura de press/release do ponteiro; `ComposeRenderer` implementa `drawLine`.

## Impact

- **`:engine`**: novos arquivos `MouseButton.kt`; modificações em `Input.kt`, `Renderer.kt`, `Rect.kt`. Testes unitários novos para `Rect.contains`.
- **`:engine-compose`**: modificações em `ComposeInput.kt` (captura de cliques) e `ComposeRenderer.kt` (drawLine).
- **`:games:tictactoe`** (novo): módulo Gradle com `Main.kt`, `TicTacToeScene.kt`, `Board.kt`, `StatusText.kt` (ou equivalente). Declarado em `settings.gradle.kts`.
- **`README.md`** e tabela de roadmap em `CLAUDE.md`: adicionar entrada para a change.
- **Sem mudanças** em `Node`, `Scene`, `PhysicsSystem`, `GameLoop`, `Transform`, `Vec2`, `Color`.
