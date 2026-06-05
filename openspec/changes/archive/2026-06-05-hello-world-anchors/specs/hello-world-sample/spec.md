## MODIFIED Requirements

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
- **THEN** o corpo de `main()` instancia `Label` e `CanvasLayer`, monta a hierarquia (`canvasLayer.addChild(label)` ou equivalente), e termina em uma única chamada a `SkikoHost().run(SceneTree(root = canvasLayer), GameConfig(...))`
- **AND** o source NÃO contém referência aos identificadores `BundleLoader`, `PythonScriptHost`, `ScriptHost`, `NodeRegistry`, `getResource`, `classLoader`, nem leitura de arquivos JSON

### Requirement: Scene root is a CanvasLayer with a single Label

A `SceneTree` do Hello World SHALL ter como root um **`CanvasLayer`** com **um único filho**: uma instância de `com.neoutils.engine.scene.Label` (a classe shipped pela engine, sem subclasse no módulo). Não MUST existir um `Node` wrapper além do `CanvasLayer`. NÃO MUST existir um `Camera2D` na árvore. Além do `CanvasLayer` root e do `Label` filho, NÃO MUST existir nenhum outro nó. O módulo MUST NOT declarar nenhuma subclasse de `Label` (em particular, o arquivo `CenteredLabel.kt` e a classe `CenteredLabel` NÃO MUST existir): a centralização é obtida via anchors/preset, não via subtipo.

A escolha de `CanvasLayer` como root reflete o invariante de UI: textos in-screen vivem em screen-space via `CanvasLayer`, esticados em design-space. Isso torna Hello World o exemplo canônico do par CanvasLayer + Label.

#### Scenario: Scene tree has CanvasLayer root with one Label child

- **WHEN** a cena é montada em `main()`
- **THEN** `SceneTree(root = canvasLayer)` recebe uma instância de `CanvasLayer` como root
- **AND** o root tem exatamente um filho do tipo `Label`
- **AND** esse filho não tem filhos próprios

#### Scenario: Module declares no Label subclass

- **WHEN** o source do módulo é inspecionado
- **THEN** NÃO existe o arquivo `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/CenteredLabel.kt`
- **AND** nenhum arquivo do módulo declara uma classe que estende `Label` (`class ... : Label()` ou `object : Label()`)

#### Scenario: No Camera2D in the scene

- **WHEN** o source do módulo é inspecionado
- **THEN** nenhum arquivo do módulo importa nem instancia `com.neoutils.engine.scene.Camera2D`

## REMOVED Requirements

### Requirement: CenteredLabel centers text in screen-space via measureText and tree.size

**Reason**: A centralização manual via `onDraw` + `renderer.measureText` + `tree.size` é o `_draw` hack que o KDoc de `Label` declara obsoleto desde `ui-controls-base`. O sample passa a centralizar declarativamente via anchor layout pass da engine, coberto pelo novo requisito "Label centers in design-space via FULL_RECT preset".

**Migration**: Remover a subclasse `CenteredLabel` e seu `onDraw`. Instanciar `Label` diretamente e chamar `applyPreset(LayoutPreset.FULL_RECT)`; o `SceneTree.runAnchorLayout` resolve a posição central via slack-centering em `Control.resolveLayout`.

## ADDED Requirements

### Requirement: Label centers in design-space via FULL_RECT preset

O `Label` do Hello World SHALL ser centralizado pelo **anchor layout pass** da engine, não por desenho manual. A montagem em `main()` MUST configurar `text`, `fontSize` e `color` no `Label` e aplicar o preset `LayoutPreset.FULL_RECT` (via `applyPreset(LayoutPreset.FULL_RECT)` ou setando os quatro anchors para `0,0,1,1` com offsets zero). Como `Label` é um `Control` min-size e filho direto de um `CanvasLayer` com `followStretch = true`, `Control.resolveLayout` resolve seu rect contra `Rect(Vec2.ZERO, tree.designSize)` e centraliza o slack positivo em ambos os eixos. O módulo MUST NOT sobrescrever `onDraw`, MUST NOT chamar `renderer.measureText`, e MUST NOT ler `tree.size` para posicionar o texto — toda medição e centralização é interna ao `Label`/`Control`.

#### Scenario: Centering uses the preset, not a draw hack

- **WHEN** o source do módulo é inspecionado
- **THEN** `main()` aplica `LayoutPreset.FULL_RECT` ao `Label` (via `applyPreset` ou anchors `0,0,1,1` + offsets zero)
- **AND** nenhum arquivo do módulo sobrescreve `onDraw`, chama `renderer.measureText`, ou lê `tree.size`/`tree.designSize` para posicionar o texto

#### Scenario: Text is centered at startup

- **GIVEN** o `GameConfig` declara `width = 800`, `height = 600` e a cena não tem `Camera2D` (então `designSize` rastreia a surface e o UI stretch é identidade)
- **WHEN** o primeiro frame é renderizado
- **THEN** `"Hello, world!"` aparece visualmente centralizado horizontal e verticalmente na surface da janela

#### Scenario: Text remains centered and scales when window is resized

- **GIVEN** o usuário redimensiona a janela arrastando a borda
- **WHEN** o frame é renderizado em qualquer tamanho de janela
- **THEN** `"Hello, world!"` permanece visualmente centralizado horizontal e verticalmente na surface
- **AND** a recentralização é resolvida pelo anchor layout pass do `SceneTree` antes do UI render pass (não há `onDraw` customizado), com o `CanvasLayer` estabelecendo o design rect como parent rect
- **AND** a recentralização é contínua (não há "salto" pré ou pós-resize)
