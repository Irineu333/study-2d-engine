## Context

A engine começou com dois backends de render: `:engine-skiko` (Skiko puro sobre `SkiaLayer` + `JFrame`) como padrão e `:engine-compose` (Compose Multiplatform Desktop) como segundo backend "validador" do invariante #4 (`Renderer`/`Input`/`GameHost` são SPIs). Compose Desktop, porém, renderiza por baixo via Skia — então o "segundo backend" sempre foi Skia com plumbing de composição diferente, não um caminho de render arquiteturalmente distinto.

Hoje a situação é:

- `:engine-compose` tem 4 arquivos (~410 LoC) e é consumido **apenas** por `:games:tictactoe`.
- O Compose Compiler plugin atrela cada bump de Kotlin a um release compatível do Compose, atrasando atualizações.
- O futuro editor visual está roadmappeado em Skiko (`editor-visual` em Planned).
- LWJGL está planejado como segundo backend experimental (`engine-lwjgl`) — esse sim arquiteturalmente distinto (OpenGL/Vulkan direto, sem Skia no caminho), e portanto um validador honesto da SPI.

Restrições:

- CLAUDE.md tem 4 invariantes arquiteturais formais; #2 e #4 mencionam Compose explicitamente e precisam reescrita coerente.
- `:games:tictactoe` foi recém-migrado para scripting Lua (`add-lua-scripting` archived em 2026-05). Ele continua sendo o único consumidor de `LuaScriptHost` — então não pode ser deletado, apenas re-hospedado.
- TTT é o único jogo da árvore que roda **sem** `Camera2D` (fallback identity, coordenadas-mundo = pixels). `:engine-skiko` precisa suportar esse modo sem regressão.

## Goals / Non-Goals

**Goals:**

- Remover `:engine-compose` do projeto sem deixar referências penduradas.
- Manter `:games:tictactoe` rodando com paridade visual e de input após migração para `SkikoHost`.
- Atualizar CLAUDE.md/ROADMAP.md/specs OpenSpec para refletir o estado pós-Compose com redação **forte** dos invariantes #2 e #4 (a SPI continua mandatória, `:engine` continua proibido de vazar tipos de qualquer backend de render).
- Registrar `engine-lwjgl` no ROADMAP como segundo backend experimental — futuro re-validador da SPI.

**Non-Goals:**

- Implementar `engine-lwjgl` agora. Esta change apenas o nomeia em Planned.
- Alterar a lógica de gameplay, scene.json ou scripts Lua de TTT. A migração é puramente do host.
- Introduzir `Camera2D` em TTT. Fallback identity continua sendo o modo suportado.
- Refatorar `:engine-skiko` para "compensar" funcionalidade do Compose. Skiko já é o backend padrão completo.
- Remover specs históricos do archive OpenSpec (`engine-foundation`, `add-lua-scripting`, etc.). O histórico fica intacto.

## Decisions

### Decision 1: Hard remove vs soft-deprecate

**Escolha**: hard remove — deletar `:engine-compose` inteiro do `settings.gradle.kts` e do disco.

**Por quê**: Compose Desktop **é** Skia por baixo dos panos. Manter `:engine-compose` congelado não preserva nenhuma propriedade arquitetural única — preserva apenas custo de build e de manutenção (Compose Compiler trava versão de Kotlin). Como `:games:tictactoe` migra trivialmente para `SkikoHost` (troca de 1 import + 1 instanciação), não há valor em deixar o módulo morto na árvore.

**Alternativas consideradas**:
- *Soft-deprecate*: marcar como `@Deprecated`, manter no build mas congelar. Rejeitado: ainda paga custo de classpath e atrasa updates de Kotlin.
- *Archive-as-museum*: mover sources para `archive/engine-compose/` fora de Gradle. Rejeitado: git já preserva a história; comprovação está no archive da change OpenSpec `engine-foundation`.
- *Substituir imediatamente por outro backend*: trazer `engine-lwjgl` já nesta change para manter o invariante #4 com 2 consumidores vivos. Rejeitado: muito escopo para uma change. Vácuo temporário é aceitável e explícito.

### Decision 2: Manter TTT, migrar para Skiko

**Escolha**: `:games:tictactoe` permanece, com `SkikoHost` substituindo `ComposeHost` em `Main.kt`.

**Por quê**: TTT ainda é o único consumidor de `LuaScriptHost`. Deletá-lo apagaria o sentinela de scripting Lua. O custo da migração é 1 arquivo Kotlin (Main.kt, ~13 linhas) + 1 build script. O bundle Lua (`scene.json`, `scripts/board.lua`) não muda, e isso prova de bônus que o pipeline `BundleLoader → SceneTree → host` é mesmo agnóstico de backend de render.

**Alternativas consideradas**:
- *Deletar TTT*: rejeitado — perderia o sentinela Lua.
- *Reescrever TTT em Pong-style com Python*: rejeitado — abandonaria a cobertura Lua que `add-lua-scripting` acabou de ganhar.

### Decision 3: Redação forte dos invariantes #2 e #4

**Escolha**: redação **forte** (não pragmática).

- **#2 reescrito como genérico**: ":engine não depende de nenhum framework de UI" em vez de "não depende de Compose". Cobre Compose, Skia direto, Swing, AWT — todos.
- **#4 reescrito com cláusula explícita**: SPIs `Renderer`/`Input`/`GameHost` permanecem mandatórias; `:engine` continua proibido de vazar tipos de backend específico (incluindo Skiko); `engine-lwjgl` está planejado como segundo backend para revalidar a SPI.

**Por quê**: a redação forte mantém disciplina mesmo no vácuo. Se aceitarmos "regra de papel até LWJGL chegar", a primeira tentação será sangrar tipos de Skiko para `:engine` "porque ninguém vai notar". O custo de manter a disciplina é nulo (já está enforçado pelo build do `:engine`), e quando LWJGL chegar, a SPI estará pronta sem dívida arquitetural.

**Alternativas consideradas**:
- *Pragmática*: "SPIs valem por convenção até LWJGL". Rejeitada — convida vazamentos.
- *Suspender o invariante*: rejeitada — apagaria o motivo de a SPI existir.

### Decision 4: Limpeza do version catalog

**Escolha**: remover do `libs.versions.toml` toda entrada Compose **apenas se** não tiver mais consumidor após a remoção do módulo `:engine-compose` e da modificação de `:games:tictactoe/build.gradle.kts`. Cada entrada é decidida na tarefa de implementação via `grep`.

Entradas candidatas:
- Plugins: `composeMultiplatform`, `composeCompiler`.
- Libs: `compose-runtime`, `compose-foundation`, `compose-ui`, `compose-desktop-currentOs`, `kotlinx-coroutinesSwing`.

**Por quê**: zero ambiguidade no resultado — pós-change, `grep -r "compose" gradle/libs.versions.toml` deve casar apenas entradas que tenham outro consumidor explicitado, ou estar vazio.

### Decision 5: LWJGL no ROADMAP, não no escopo

**Escolha**: adicionar uma linha em `ROADMAP.md → Planned` chamada `engine-lwjgl` com resumo de uma frase apontando que é o futuro re-validador do invariante #4. Não criar change OpenSpec agora.

**Por quê**: a change atual já tem escopo definido (deletar Compose + migrar TTT + reescrever docs). LWJGL é trabalho futuro com decisões próprias (OpenGL via LWJGL? GLFW window? como integrar com `GameHost` SPI?). Misturar é antipattern OpenSpec.

## Risks / Trade-offs

- **[Risco] TTT em SkikoHost sem `Camera2D` pode regredir**: TTT é o primeiro jogo Skiko sem `Camera2D` (Pong/Demos/Solar-System todos têm). O fallback identity já está documentado em CLAUDE.md ("Árvores sem `Camera2D` caem no fallback identity — coordenadas mundiais são pixels da surface"). → **Mitigação**: tarefa explícita de smoke-test após a migração; o invariante de paridade está nos critérios de aceitação da change. Se o fallback regredir, é bug do `:engine-skiko` e ganha tarefa própria — não bloqueia a remoção do Compose.

- **[Risco] Input do mouse em SkikoInput sem paridade com ComposeInput**: TTT depende de `tree.input.wasMouseClicked(MouseButton.Left)` + `pointerPosition` em pixels. → **Mitigação**: smoke-test do TTT explicitamente verifica clique numa célula, alternância X/O, clique pós-fim que reinicia. Se houver dessincronização, é bug do `SkikoInput` corrigido no escopo desta change (não joga pra outra).

- **[Risco] Vácuo do invariante #4**: por algum período, a engine tem 1 único backend de render ativo. → **Mitigação aceita explicitamente**: redação forte dos invariantes na docs mantém a disciplina; LWJGL roadmappeado é o re-validador. O risco real (sangrar tipos Skiko pro `:engine`) é mitigado pela própria estrutura do build — `:engine` não declara dependência em Skiko, e o compilador reclama de import vazado.

- **[Risco] Compose Compiler plugin pode estar enrolado em outros plugins**: se algum outro módulo (improvável, mas possível) usar `kotlinx-coroutinesSwing` por outro motivo, removê-lo quebra. → **Mitigação**: tarefa de cleanup do version catalog é o último passo e roda `./gradlew build` antes de commitar cada remoção.

- **[Trade-off] Perda temporária de cobertura "multi-backend"**: durante o vácuo até `engine-lwjgl`, o invariante #4 não tem prova viva. Aceito por dois motivos: (1) `:engine` continua proibido de declarar dependência em Skiko via `build.gradle.kts`, então a regra do compilador-enforced sobrevive; (2) Compose nunca foi realmente um "outro backend" — era Skia com plumbing diferente.

## Migration Plan

A migração é simples e segue ordem:

1. **Modificar `:games:tictactoe`** primeiro (Main.kt + build.gradle.kts) — TTT passa a depender de `:engine-skiko`.
2. **Validar TTT em Skiko** (`./gradlew :games:tictactoe:run`) — confirma paridade de gameplay antes de qualquer deleção.
3. **Remover `:engine-compose`** do disco e do `settings.gradle.kts`.
4. **Limpar `libs.versions.toml`** — remover apenas entradas sem consumidor remanescente (verificar via `grep`).
5. **Atualizar docs** (CLAUDE.md, ROADMAP.md) com as redações novas.
6. **Atualizar specs OpenSpec** (delta specs já produzidos por esta change).
7. **`./gradlew build`** verde + smoke-tests manuais de TTT, Pong, Demos, Hello-World.

**Rollback**: se algum passo regredir, revert do branch via git. Nenhuma das operações é irreversível.

## Open Questions

- Nenhuma. As três perguntas finas levantadas no explore (motivação, papel de TTT, redação do invariante #4) foram fechadas antes desta change ser proposta.
