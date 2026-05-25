## Why

A engine ainda não tem um exemplo executável **mínimo e code-only** que sirva como ponto de partida didático. Pong, Velha e os Demos são úteis como provas vivas dos invariantes, mas todos pulam direto para colisão, IA, scripts Python ou múltiplos slots — um leitor novo precisa engolir vários conceitos antes de ver a primeira janela. Falta um "hello world" que mostre, em poucas linhas de Kotlin puro, como abrir uma janela e desenhar texto centralizado.

## What Changes

- Adicionar um novo módulo Gradle `:games:hello-world` registrado em `settings.gradle.kts`.
- Esse módulo depende **apenas** de `:engine` e `:engine-skiko` (sem `:engine-bundle`, sem `:engine-bundle-python`, sem scripts Python, sem `scene.json`).
- O módulo provê um `Main.kt` executável via `./gradlew :games:hello-world:run` que abre uma janela Skiko exibindo a string `"Hello, world!"` centralizada horizontal e verticalmente.
- A cena é construída inteiramente em Kotlin e a árvore tem **um único nó**: uma subclasse `CenteredLabel : Label()` como raiz da `SceneTree`. Não há `Camera2D`, não há `Node` wrapper, não há constantes top-level — o `main()` configura `text`, `size`, `color` inline e entrega ao `SkikoHost`.
- `CenteredLabel` mora num arquivo dedicado (herança simples, classe nomeada — não anônima); só sobrescreve `onDraw` para calcular o offset de centralização via `Renderer.measureText` lendo `tree.size`.
- Adicionar atalho documentado em `CLAUDE.md` (seção "Module Structure & How to Run") explicando como rodar o sample.

## Capabilities

### New Capabilities

- `hello-world-sample`: módulo executável code-only que serve como exemplo mínimo de uso da engine (um único `CenteredLabel` como root da `SceneTree`, sem `Camera2D`, sem bundles, sem scripting).

### Modified Capabilities

<!-- nenhuma -->

## Impact

- **Código novo**: `games/hello-world/build.gradle.kts`, `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/Main.kt` e `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/CenteredLabel.kt`.
- **Build**: `settings.gradle.kts` ganha `include(":games:hello-world")`.
- **Docs**: `CLAUDE.md` lista o novo módulo e o comando `./gradlew :games:hello-world:run`.
- **Engine core**: única mudança trivial em `:engine` — `Label` passa de `class` para `open class` para permitir a subclasse `CenteredLabel`. Nenhuma mudança em `:engine-skiko`, `:engine-bundle*` ou `:engine-compose`.
- **Riscos**: baixo — é um sample isolado; quebrar este módulo não afeta `:games:pong`, `:games:tictactoe`, `:games:demos` nem a engine.
