## 1. Migrate :games:tictactoe to SkikoHost (verifiable smoke-test first)

- [ ] 1.1 Editar `games/tictactoe/src/main/kotlin/com/neoutils/engine/games/tictactoe/Main.kt`: trocar `import com.neoutils.engine.compose.ComposeHost` por `import com.neoutils.engine.skiko.SkikoHost` (ou path equivalente); trocar `ComposeHost()` por `SkikoHost()` em `main()`.
- [ ] 1.2 Editar `games/tictactoe/build.gradle.kts`: trocar `implementation(projects.engineCompose)` por `implementation(projects.engineSkiko)`; remover do bloco `plugins {}` os aliases `composeMultiplatform` e `composeCompiler`; manter apenas `alias(libs.plugins.kotlinJvm)`. Remover quaisquer outras dependências Compose (e.g. `libs.compose.*`).
- [ ] 1.3 Rodar `./gradlew :games:tictactoe:run` e validar manualmente: janela 600×600 abre, X joga primeiro, click numa célula vazia coloca X, próximo click coloca O, alternância continua, ao vencer ou empatar o próximo click reinicia, F1 toggla FPS overlay.
- [ ] 1.4 Validar paridade do mouse: clicks landem nas células corretas (hit-test em pixels da surface — TTT continua sem `Camera2D`, então `Input.pointerPosition` está em coordenadas-mundo idênticas a pixels).

## 2. Delete :engine-compose module

- [ ] 2.1 Remover `include(":engine-compose")` de `settings.gradle.kts`.
- [ ] 2.2 Deletar do disco o diretório `engine-compose/` inteiro (inclui `build.gradle.kts` e `src/main/kotlin/com/neoutils/engine/compose/*.kt`).
- [ ] 2.3 Rodar `./gradlew clean` para limpar caches do Gradle que ainda referenciem o módulo deletado.
- [ ] 2.4 Rodar `./gradlew build` e confirmar que passa verde (todos os módulos remanescentes compilam e seus testes passam).

## 3. Clean up version catalog

- [ ] 3.1 Inspecionar `gradle/libs.versions.toml`. Para cada entrada Compose (plugins `composeMultiplatform`, `composeCompiler`; libs `compose-runtime`, `compose-foundation`, `compose-ui`, `compose-desktop-currentOs`, `kotlinx-coroutinesSwing`), rodar `grep -rn "<entry-key>" --include="*.kts" --include="*.kt"` na árvore inteira (exceto `.claude/worktrees/` e `build/`).
- [ ] 3.2 Remover do `libs.versions.toml` apenas as entradas com **zero** matches remanescentes pós-passo 1+2. Se alguma entrada continuar referenciada por outro módulo, deixar e documentar no commit message.
- [ ] 3.3 Rodar `./gradlew build` novamente para confirmar que o catálogo limpo não quebra nada.

## 4. Update CLAUDE.md

- [ ] 4.1 Reescrever o header sobre stack: substituir "Skiko é o backend padrão da engine; Compose Multiplatform é o segundo backend, mantido vivo via :games:tictactoe" por "Skiko é o único backend de render ativo no momento; LWJGL está planejado como segundo backend experimental para revalidar a SPI".
- [ ] 4.2 Reescrever invariante #2 como genérico: "`:engine` não depende de nenhum framework de UI/render — sem `androidx.compose.*`, `org.jetbrains.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, AWT/Swing além do strictly necessário, e sem futuras dependências LWJGL/OpenGL/Vulkan".
- [ ] 4.3 Reescrever invariante #4 com redação forte: "`Renderer`, `Input` e `GameHost` permanecem SPIs obrigatórias. Skiko é o backend padrão (`:engine-skiko`); `:engine` não pode vazar tipos de Skiko (`org.jetbrains.skia.*`, `SkikoView`, `SkiaLayer`) — quem precisa de Skia direto é o `:engine-skiko`. LWJGL está planejado como segundo backend experimental para revalidar a SPI."
- [ ] 4.4 Atualizar a seção "Module Structure & How to Run": remover a linha do `:engine-compose`; atualizar a descrição de `:games:tictactoe` para "jogo Velha (humano vs humano), roda em **Skiko** com scripting Lua — sentinela do segundo backend de scripting" (remover "segundo backend de render").
- [ ] 4.5 Atualizar a seção "Para rodar Velha": remover qualquer menção a Compose (ComposeHost, segundo backend de render); manter o comando `./gradlew :games:tictactoe:run` intacto.
- [ ] 4.6 `grep -in "compose" CLAUDE.md` — confirmar que nenhuma menção remanescente trate Compose como módulo ativo.

## 5. Update ROADMAP.md

- [ ] 5.1 Adicionar linha em **Planned** chamada `engine-lwjgl` com resumo: "Segundo backend experimental de render via LWJGL — sucessor do papel de `:engine-compose` no invariante #4. Sem Skia no caminho; valida que `Renderer`/`Input`/`GameHost` continuam SPIs honestas para um caminho arquiteturalmente distinto."
- [ ] 5.2 Verificar que nenhuma linha do roadmap referencie `:engine-compose` ou "segundo backend Compose"; ajustar se houver.

## 6. Sync OpenSpec specs

- [ ] 6.1 Após archive desta change (`/opsx:archive remove-compose-backend`), confirmar que `openspec/specs/compose-runtime/` foi deletado.
- [ ] 6.2 Confirmar que `openspec/specs/tictactoe-sample/spec.md`, `engine-core/spec.md`, `project-conventions/spec.md`, `skiko-runtime/spec.md`, e `bundle-loading/spec.md` refletem as modificações do delta.
- [ ] 6.3 Rodar `openspec lint` (ou comando equivalente) e confirmar que nenhuma spec referencia `:engine-compose` como módulo ativo.

## 7. Final acceptance gate

- [ ] 7.1 `./gradlew build` verde.
- [ ] 7.2 `./gradlew :games:pong:run` abre Pong e o jogo é jogável.
- [ ] 7.3 `./gradlew :games:tictactoe:run` abre Tic Tac Toe e o jogo é jogável (paridade com pré-change).
- [ ] 7.4 `./gradlew :games:demos:run` abre Demos e exercita todas as cenas (`1`–`6`, `F1`, `F2`, `F3`).
- [ ] 7.5 `./gradlew :games:hello-world:run` abre Hello World.
- [ ] 7.6 `grep -rn "compose" engine/src engine-bundle/src engine-bundle-python/src engine-bundle-lua/src engine-skiko/src games/ --include="*.kt" --include="*.kts"` retorna zero matches fora de strings de teste/docs históricos legítimos.
- [ ] 7.7 `grep -rn "ComposeHost\|engine-compose\|engineCompose" .` (excluindo `.claude/worktrees/`, `build/`, `openspec/changes/archive/`) retorna zero matches.
- [ ] 7.8 `find engine-compose -type f 2>/dev/null | wc -l` retorna `0`.
