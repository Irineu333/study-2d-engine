# hello-world-sample Specification

## Purpose

Exemplo executável mínimo da engine: um módulo `:games:hello-world` code-only com um único `CenteredLabel` como root da `SceneTree`. Serve como primeiro contato didático — abre uma janela Skiko com `"Hello, world!"` centralizado, sem bundle, sem scripting e sem `Camera2D`.

## Requirements

### Requirement: Hello World is an executable standalone module

O projeto SHALL prover um módulo `:games:hello-world` que depende exclusivamente de `:engine` e `:engine-skiko`, e contém um entry point `main()` que abre uma janela Skiko hospedando uma cena montada inteiramente em Kotlin. O módulo MUST ser executável via `./gradlew :games:hello-world:run`. O módulo MUST NOT declarar dependência em `:engine-bundle`, `:engine-bundle-python` nem em qualquer outro módulo `:games:*`. O `Main.kt` MUST NOT referenciar `BundleLoader`, `ScriptHost`, `PythonScriptHost`, `NodeRegistry`, nem carregar nenhum recurso de classpath (`scene.json`, `.py`, etc.) — a cena é construída inteiramente in-code.

#### Scenario: Hello World runs from Gradle

- **WHEN** um desenvolvedor executa `./gradlew :games:hello-world:run` da raiz do projeto
- **THEN** uma janela desktop Skiko abre exibindo a string `"Hello, world!"`
- **AND** a janela permanece responsiva (clique no botão de fechar encerra o processo limpamente)

#### Scenario: Hello World module dependencies are minimal

- **WHEN** `games/hello-world/build.gradle.kts` é inspecionado
- **THEN** o bloco `dependencies` declara `implementation(projects.engine)` e `implementation(projects.engineSkiko)`
- **AND** NÃO declara `projects.engineBundle` nem `projects.engineBundlePython`
- **AND** NÃO declara nenhum outro módulo `:games:*`

#### Scenario: Main.kt is code-only

- **WHEN** o source de `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/Main.kt` é inspecionado
- **THEN** o corpo de `main()` instancia `CenteredLabel` e `CanvasLayer`, monta a hierarquia (`canvasLayer.addChild(label)` ou equivalente), e termina em uma única chamada a `SkikoHost().run(SceneTree(root = canvasLayer), GameConfig(...))`
- **AND** o source NÃO contém referência aos identificadores `BundleLoader`, `PythonScriptHost`, `ScriptHost`, `NodeRegistry`, `getResource`, `classLoader`, nem leitura de arquivos JSON

### Requirement: Scene root is a CanvasLayer with a single CenteredLabel

A `SceneTree` do Hello World SHALL ter como root um **`CanvasLayer`** com **um único filho**: uma instância de `CenteredLabel`, classe declarada no próprio módulo `:games:hello-world` como `class CenteredLabel : Label()`. Não MUST existir um `Node` wrapper além do `CanvasLayer`. NÃO MUST existir um `Camera2D` na árvore. Além do `CanvasLayer` root e do `CenteredLabel` filho, NÃO MUST existir nenhum outro nó. O `CenteredLabel` SHALL ser uma classe nomeada top-level (em arquivo dedicado `CenteredLabel.kt`); MUST NOT ser declarado como `object : Label()` anônimo dentro de `Main.kt`.

A escolha de `CanvasLayer` como root reflete o invariante de UI: textos in-screen vivem em screen-space via `CanvasLayer`, mesmo no exemplo didático mínimo. Isso torna Hello World o exemplo canônico do par CanvasLayer + Label.

#### Scenario: Scene tree has CanvasLayer root with one Label child

- **WHEN** a cena é montada em `main()`
- **THEN** `SceneTree(root = canvasLayer)` recebe uma instância de `CanvasLayer` como root
- **AND** o root tem exatamente um filho do tipo `CenteredLabel`
- **AND** esse filho não tem filhos próprios

#### Scenario: CenteredLabel is a named class in a dedicated file

- **WHEN** o source do módulo é inspecionado
- **THEN** existe um arquivo `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/CenteredLabel.kt`
- **AND** esse arquivo declara `class CenteredLabel : Label()`
- **AND** `Main.kt` NÃO contém a expressão `object : Label()` (nem qualquer outra classe anônima estendendo `Label`)

#### Scenario: No Camera2D in the scene

- **WHEN** o source do módulo é inspecionado
- **THEN** nenhum arquivo do módulo importa nem instancia `com.neoutils.engine.scene.Camera2D`

### Requirement: CenteredLabel centers text in screen-space via measureText and tree.size

`CenteredLabel` SHALL sobrescrever `onDraw(renderer: Renderer)` para desenhar `text` de modo que o centro do bounding box medido pelo renderer coincida com o centro da surface da `SceneTree`. Como `CenteredLabel` agora é filho de um `CanvasLayer`, ele recebe identity transform no início do walk de UI pass — coordenadas são pixels screen-space puros. A implementação MUST: (a) ler `tree?.size` (early-return se `null`); (b) medir o texto via `renderer.measureText(text, size)`; (c) chamar `renderer.drawText(text, Vec2((surface.x - measured.x) / 2f, (surface.y - measured.y) / 2f), size, color)`. A implementação MUST NOT chamar `super.onDraw(renderer)` (o `Label` base desenharia o texto numa segunda posição). A implementação MUST NOT usar magic numbers para offset de centralização (qualquer dimensão de texto deve vir de `measureText`, não de constantes literais como `60f` ou `8f`).

#### Scenario: onDraw uses measureText and tree.size

- **WHEN** o source de `CenteredLabel.kt` é inspecionado
- **THEN** `onDraw` chama `renderer.measureText(text, size)` e usa o resultado no cálculo da posição de desenho
- **AND** `onDraw` lê `tree?.size` (ou propriedade equivalente da `SceneTree`) para descobrir a surface
- **AND** `onDraw` NÃO contém literais numéricos representando largura/altura do texto (ex.: `60f`, `8f`, `100f`)

#### Scenario: onDraw does not call super

- **WHEN** o source de `CenteredLabel.kt` é inspecionado
- **THEN** o corpo de `onDraw` NÃO contém `super.onDraw(renderer)` (evita desenho duplicado do `Label` base)

#### Scenario: Text remains centered when window is resized

- **GIVEN** o `GameConfig` declara `width = 800`, `height = 600` e o usuário redimensiona a janela arrastando a borda
- **WHEN** o frame é renderizado em qualquer tamanho de janela
- **THEN** `"Hello, world!"` permanece visualmente centralizado horizontal e verticalmente na surface da janela
- **AND** a recentralização é contínua (não há "salto" pré ou pós-resize)
- **AND** o cálculo ocorre durante o UI pass do `SceneTree.render`, com `CanvasLayer` estabelecendo identity transform — independente de qualquer view transform que pudesse existir (não há Camera2D nesta cena, mas o invariante vale)

### Requirement: Documentation lists the new sample

`CLAUDE.md` SHALL listar `:games:hello-world` na seção "Module Structure & How to Run" com o comando `./gradlew :games:hello-world:run` e uma descrição curta do propósito didático (cena code-only mínima, único nó na árvore, sem bundle, sem scripting, sem câmera).

#### Scenario: CLAUDE.md mentions the new module

- **WHEN** `CLAUDE.md` é inspecionado
- **THEN** a árvore de módulos lista `:games:hello-world` com descrição curta (ex.: "exemplo code-only mínimo — único Label centralizado em Skiko, sem bundle nem scripting")
- **AND** o comando `./gradlew :games:hello-world:run` aparece como exemplo de execução
