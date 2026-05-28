## MODIFIED Requirements

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

### Requirement: Main.kt is code-only and assembles CanvasLayer + Label

O `Main.kt` MUST construir a cena inteiramente em Kotlin: instanciar `CenteredLabel`, configurar `text`, `size` e `color` inline via `apply { ... }` (ou atribuição direta); instanciar `CanvasLayer` e adicionar o `CenteredLabel` como filho; entregar em uma única chamada a `SkikoHost().run(SceneTree(root = canvasLayer), GameConfig(...))`. O source NÃO MUST referenciar `BundleLoader`, `ScriptHost`, `PythonScriptHost`, `NodeRegistry`, nem carregar nenhum recurso de classpath (`scene.json`, `.py`, etc.).

#### Scenario: Main.kt builds CanvasLayer with CenteredLabel child code-only

- **WHEN** o source de `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/Main.kt` é inspecionado
- **THEN** o corpo de `main()` instancia `CenteredLabel` e `CanvasLayer`, monta a hierarquia (`canvasLayer.addChild(label)` ou equivalente), e termina em uma única chamada a `SkikoHost().run(SceneTree(root = canvasLayer), GameConfig(...))`
- **AND** o source NÃO contém referência aos identificadores `BundleLoader`, `PythonScriptHost`, `ScriptHost`, `NodeRegistry`, `getResource`, `classLoader`, nem leitura de arquivos JSON

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
