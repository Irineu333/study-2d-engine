## ADDED Requirements

### Requirement: Pong wraps the loaded root in a SceneTree

`Main.kt` em `:games:pong` MUST construir uma `SceneTree(root = bundleRoot)` ao redor do `Node` devolvido por `BundleLoader.fromResources("pong", scripting = python)` antes de chamar `SkikoHost.run(tree, config)`. O `Main.kt` MUST NOT armazenar o resultado de `BundleLoader.fromResources` em uma variável tipada como `Scene` (a classe não existe mais) — o tipo declarado MUST ser `Node` ou inferido.

O arquivo `games/pong/src/main/resources/pong/scene.json` MUST declarar `root.type` como um identificador registrado em `NodeRegistry` que NÃO seja `com.neoutils.engine.scene.Scene`. O valor padrão MUST ser `com.neoutils.engine.scene.Node` (root como container puro; o `Camera2D` filho carrega bounds e view transform). Tentativas de carregar o `scene.json` com `root.type = "com.neoutils.engine.scene.Scene"` MUST falhar com `UnknownNodeTypeException`.

#### Scenario: Pong Main wraps the bundle root in a SceneTree

- **WHEN** o source de `games/pong/src/main/kotlin/.../Main.kt` é inspecionado
- **THEN** existe uma chamada com a forma `SkikoHost().run(SceneTree(root = ...), GameConfig(...))` (ou `host.run(SceneTree(root = ...), ...)` equivalente)
- **AND** o tipo declarado da variável que recebe `BundleLoader.fromResources("pong", ...)` é `Node` ou inferido
- **AND** o source NÃO contém referência ao símbolo `Scene`

#### Scenario: Pong scene.json root type is not engine.Scene

- **WHEN** `pong/scene.json` é inspecionado após esta change
- **THEN** o campo `root.type` é `com.neoutils.engine.scene.Node` (ou outro tipo concreto registrado, mas NÃO `com.neoutils.engine.scene.Scene`)
- **AND** o `Camera2D` continua presente como filho do root com `current = true`
- **AND** `version` permanece `2` (sem bump de schema)

#### Scenario: Pong still runs end-to-end on Skiko after the change

- **WHEN** desenvolvedor executa `./gradlew :games:pong:run` após esta change
- **THEN** uma janela Skiko abre e renderiza a cena Pong
- **AND** entradas `W`/`S` movem o paddle esquerdo
- **AND** o paddle direito (AI) acompanha a bola
- **AND** F1/F2 alternam overlays de FPS e colliders, idêntico ao comportamento pré-change
