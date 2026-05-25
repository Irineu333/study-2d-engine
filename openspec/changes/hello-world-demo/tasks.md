## 1. Scaffold do módulo `:games:hello-world`

- [x] 1.1 Registrar `include(":games:hello-world")` em `settings.gradle.kts` (após o `include(":games:demos")`).
- [x] 1.2 Criar `games/hello-world/build.gradle.kts` com plugin `kotlinJvm` + `application`, dependências `projects.engine` e `projects.engineSkiko`, e `application.mainClass = "com.neoutils.engine.games.helloworld.MainKt"`. NÃO incluir `kotlinSerialization`, `projects.engineBundle*`, nem `projects.engineCompose`.
- [x] 1.3 Criar a estrutura de pastas `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/`.

## 2. `CenteredLabel`

- [x] 2.1 Criar `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/CenteredLabel.kt` declarando `class CenteredLabel : Label()`.
- [x] 2.2 Em `CenteredLabel`, sobrescrever `onDraw(renderer: Renderer)`: ler `val surface = tree?.size ?: return`, medir `val measured = renderer.measureText(text, size)`, e chamar `renderer.drawText(text, Vec2((surface.x - measured.x) / 2f, (surface.y - measured.y) / 2f), size, color)`. NÃO chamar `super.onDraw(renderer)`.
- [x] 2.3 Confirmar que `CenteredLabel.kt` não contém literais numéricos representando dimensões de texto (qualquer largura/altura vem de `measureText` ou `tree.size`).

## 3. `Main.kt`

- [x] 3.1 Criar `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/Main.kt` com `fun main()` que instancia `val label = CenteredLabel().apply { text = "Hello, world!"; size = 32f; color = Color.WHITE }` e em seguida chama `SkikoHost().run(SceneTree(root = label), GameConfig(title = "Hello, world!", width = 800, height = 600))`.
- [x] 3.2 Confirmar que `Main.kt` NÃO contém: `Camera2D`, `Node()`, `object : Label`, `BundleLoader`, `PythonScriptHost`, `ScriptHost`, `NodeRegistry`, `getResource`, `classLoader`, `private const val`.

## 4. Documentação

- [x] 4.1 Atualizar `CLAUDE.md`, seção "Module Structure & How to Run", adicionando `:games:hello-world` na árvore de módulos com descrição: "exemplo code-only mínimo — único Label centralizado em Skiko, sem bundle nem scripting".
- [x] 4.2 No mesmo `CLAUDE.md`, adicionar bloco "Para rodar Hello World" antes (ou depois) dos blocos existentes de Pong/Velha/Demos, com o comando `./gradlew :games:hello-world:run` e uma linha sobre o que o usuário verá ("janela 800×600 com `Hello, world!` centralizado; sem input — o texto se recentraliza ao redimensionar").

## 5. Verificação

- [x] 5.1 Executar `./gradlew :games:hello-world:run` na raiz do projeto e confirmar visualmente que a janela abre e `"Hello, world!"` está centralizado horizontal e verticalmente.
- [x] 5.2 Redimensionar a janela arrastando uma borda e confirmar que o texto continua centralizado em tempo real.
- [x] 5.3 Executar `./gradlew :games:pong:run`, `:games:tictactoe:run` e `:games:demos:run` para confirmar que nenhum dos samples existentes regrediu.
- [x] 5.4 Rodar `./gradlew build` para confirmar que toda a árvore compila e os testes existentes continuam verdes.
- [x] 5.5 Rodar `openspec validate hello-world-demo --strict` e corrigir qualquer pendência apontada.
