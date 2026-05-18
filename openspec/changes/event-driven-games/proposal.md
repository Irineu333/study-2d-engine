## Why

A change `engine-foundation` entrega scene graph estilo Godot, `GameLoop` contínuo via `withFrameNanos` e comunicação entre nós por referência direta — suficiente pra Pong, que muda de estado a cada frame. Jogos como Jogo da Velha e Campo Minado têm perfil oposto: a cena só muda quando o usuário clica. Rodar 60fps "no vazio" é desperdício de CPU, e a comunicação por referência direta amarra o tabuleiro à lógica do jogo de um jeito que não escala. Esta change introduz na engine os primitivos que faltam pra esse perfil (sinais tipados, tick-on-demand, eventos de pointer com botões distintos) e entrega os dois jogos como prova viva.

## What Changes

- Acrescenta **sinais/eventos tipados** ao `:engine` como primitiva de primeira classe (estilo Godot signals). Comunicação por referência direta continua suportada; sinais são a opção idiomática quando o acoplamento direto for indesejado.
- Acrescenta **tick-on-demand** ao `:engine`: cada `Scene` carrega um `RenderMode` (`Continuous` — padrão atual, ou `OnDemand`). Em `OnDemand`, o `GameLoop` só roda tick quando há eventos pendentes, mudança marcada como dirty, ou animação em curso.
- Acrescenta **eventos de pointer** ao SPI de `Input`: além de queries de teclado, nós interativos recebem `onClick`/`onRightClick`/`onHover` por hit-test em `bounds()`.
- Acrescenta **distinção de botões do mouse** (esquerdo/direito) ao `Input`. Necessária pra mecânica revelar-vs-flag do Minado.
- Acrescenta um helper opcional `Grid<T>` no `:engine` para tabuleiros (iteração linha/coluna, lookup `(row, col)`). Reusado por Velha e Minado.
- Introduz módulo `:games:tictactoe` com Jogo da Velha 3x3 jogável (dois humanos no mesmo dispositivo, vitória/empate, botão de reset).
- Introduz módulo `:games:minesweeper` com Campo Minado configurável (padrão Beginner 9x9 / 10 minas), flood-fill, first-click safe, contador de minas, timer, vitória/derrota.
- Acrescenta DX: atalho global pra alternar `RenderMode` em runtime (debug → forçar `Continuous` para inspecionar dirty/inputs); log de sinais emitidos no tag `Events` (nível Debug).
- Atualiza `CLAUDE.md` para refletir sinais como primitiva, `RenderMode` como conceito de engine, e roadmap progredido (`engine-foundation` arquivada, `event-driven-games` ativa, editor planejado).

Todas as adições à engine MUST ser aditivas — a API pública usada por Pong não pode quebrar. Se algum ponto exigir mudança de comportamento existente (não esperado nesta change), entra como `MODIFIED Requirement` justificado.

## Capabilities

### New Capabilities

- `tictactoe-sample`: Jogo da Velha 3x3 como módulo executável `:games:tictactoe`. Valida a engine no perfil event-driven em sua forma mais simples (input por clique, lógica discreta, fim de partida, reset).
- `minesweeper-sample`: Campo Minado como módulo executável `:games:minesweeper`. Valida mecânicas mais complexas: dois botões do mouse, flood-fill, first-click safe, timer, condições de vitória/derrota.

### Modified Capabilities

- `engine-core`: acrescenta sinais tipados, `RenderMode` (`Continuous`/`OnDemand`), wake-up explícito de cena (`Scene.requestRender`), eventos de pointer com hit-test e botões distintos no `Input` SPI, e helper `Grid<T>`. Tudo aditivo.
- `compose-runtime`: acrescenta hit-test no `GameSurface`, captura de botão direito do mouse, despacho de eventos de pointer aos nós, e sleep/wake-up do loop quando `RenderMode = OnDemand`. Tudo aditivo.
- `dx-tooling`: acrescenta toggle global de `RenderMode` (override de debug), tag de log `Events` para emissões de sinais. Tudo aditivo.
- `project-conventions`: `CLAUDE.md` ganha menções a sinais, `RenderMode`, e roadmap atualizado. Aditivo.

## Impact

- **Pré-requisito**: `engine-foundation` precisa estar implementada e arquivada antes desta change ser aplicada (capabilities existentes ficam em `openspec/specs/` para que as deltas façam sentido).
- **Módulos novos**: `:games:tictactoe`, `:games:minesweeper`. Cada um com `main()` próprio, executável via `./gradlew :games:tictactoe:run` e `./gradlew :games:minesweeper:run`.
- **API pública da engine**: cresce mas não quebra. Pong continua compilando e jogável sem alterações.
- **Comportamento default**: `Scene.renderMode` default = `Continuous` (compatível com Pong). Velha e Minado opt-in para `OnDemand`.
- **Pontos de evolução documentados (não implementados aqui)**: animações por tween/keyframe (poderiam usar tick-on-demand com flag de "animação em curso"), áudio, save state, gerador de configurações de Minado (Intermediate/Expert) acessível em UI, multiplayer.
- **Roadmap pós-change**: prepara o terreno pro editor visual — sinais já são vocabulário de comunicação, hit-test já é primitiva, RenderMode já existe pra inspetor não-jogo.
