## 1. Engine core — introduce SceneTree

- [x] 1.1 Criar `engine/src/main/kotlin/com/neoutils/engine/tree/SceneTree.kt` migrando todos os concerns de driver/host/query que estavam em `Scene` (campos `input`, `size`, `viewport`, `isMutationDeferred`, `isRendering`, métodos `start`, `stop`, `process`, `physicsProcess`, `render`, `applyPending`, `getNodesInGroup`, `currentCamera`, `screenToWorld`, `worldToScreen`, `resize`). Classe `class SceneTree(val root: Node)` — não `open`, não `@Serializable`, sem herdar de Node.
- [x] 1.2 Adicionar slot `var onResize: ((Float, Float) -> Unit)? = null` em `SceneTree`. Disparar em `resize(...)` quando `width`/`height` mudam de fato (não disparar quando ambos são iguais ao valor anterior).
- [x] 1.3 Em `SceneTree.start()`, chamar `root.attachToLiveTree(this)`. Em `stop()`, chamar `root.detachFromLiveTree()`. Garantir que `isRendering` é setado apenas durante `render`, e `isMutationDeferred` durante todos os traversals (process/physicsProcess/render).

## 2. Engine core — Node back-pointer

- [x] 2.1 Adicionar `@Transient var tree: SceneTree? = null` em `Node` (setter `internal`).
- [x] 2.2 Refatorar `Node.attachToLiveTree(...)` para receber `tree: SceneTree`, assinar `this.tree = tree`, disparar `onEnter()`, recurse para filhos passando o mesmo `tree`.
- [x] 2.3 Refatorar `Node.detachFromLiveTree()` para disparar `onExit()` (e recurse para filhos) e ao final zerar `this.tree = null`. Ordem: children primeiro (`onExit` em post-order como hoje), depois o próprio.
- [x] 2.4 Derivar `Node.isLive` de `tree != null` (eliminar o campo independente). Auditar todos os pontos que setavam `isLive` manualmente — devem agora ser função de `tree`.
- [x] 2.5 Atualizar `Node.addChild` e `Node.removeChild` para, quando o pai está vivo, propagar via `child.attachToLiveTree(this.tree!!)` / `child.detachFromLiveTree()`. Atualizar `applyAdd`/`applyRemove` (caminhos deferidos via `pendingAdd`/`pendingRemove`) na mesma chave.
- [x] 2.6 Remover `fun rootScene(): Scene?` de `Node` e qualquer campo `scene` legado. Remover imports de `Scene` em `Node.kt`.

## 3. Engine core — delete Scene class

- [x] 3.1 Apagar `engine/src/main/kotlin/com/neoutils/engine/scene/Scene.kt`.
- [x] 3.2 Apagar imports de `com.neoutils.engine.scene.Scene` em todos os arquivos `:engine` que ainda importem.

## 4. Engine core — wire GameLoop, GameHost, PhysicsSystem, DebugOverlay

- [x] 4.1 `GameLoop`: trocar campo `scene: Scene` → `tree: SceneTree`. Atualizar `tick(...)` para chamar `tree.process/physicsProcess/render/applyPending`. Atualizar `physics.step(scene)` → `physics.step(tree)`.
- [x] 4.2 `GameHost` (interface): mudar assinatura `run(scene: Scene, config)` → `run(tree: SceneTree, config)`.
- [x] 4.3 `PhysicsSystem.step(...)`: mudar parâmetro de `Scene` para `SceneTree`. Atualizar enumeração de colliders para partir de `tree.root`.
- [x] 4.4 `DebugOverlay` / `renderDebugOverlay(...)`: mudar segundo parâmetro de `Scene` para `SceneTree`. Atualizar leituras (`scene.size` → `tree.size`, `scene.currentCamera()` → `tree.currentCamera()`, etc.).

## 5. Engine core — NodeRegistry cleanup

- [x] 5.1 Em `NodeRegistry.registerEngineTypes()`, remover a linha `register(Scene::class) { Scene() }`. Conferir que a sequência registrada bate com o spec atualizado: `Node`, `Node2D`, `Camera2D`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `Label`, `BoxCollider`.

## 6. Engine core — SceneLoader return type

- [x] 6.1 `SceneLoader.load(...)`: tipo de retorno passa a ser `Node`. Apagar o cast final `root as? Scene ?: error("Root node is not a Scene: ...")`.
- [x] 6.2 `SceneLoader.save(...)`: parâmetro `scene: Scene` → `root: Node`. Atualizar caminhos internos para tree-walk a partir do `Node` recebido.
- [x] 6.3 KDoc do `SceneLoader` atualizado para refletir "root livre" e "caller envolve em `SceneTree`".

## 7. Engine-bundle — BundleLoader return type

- [x] 7.1 `BundleLoader.fromResources(...)` e `BundleLoader.fromPath(...)`: tipo de retorno passa a ser `Node`. Sem outras mudanças semânticas além da assinatura.
- [x] 7.2 KDoc atualizado para "devolve o nó raiz destacado".

## 8. Skiko backend

- [x] 8.1 `SkikoHost.run(tree: SceneTree, config: GameConfig)`: mudar assinatura. Atualizar callback `skikoView.onRender` para chamar `tree.resize(...)`, `loop.tick(...)`, `renderDebugOverlay(renderer, tree)`.
- [x] 8.2 Conferir que `SkikoHost.kt` não importa nem referencia o símbolo `Scene` após a mudança.

## 9. Compose backend

- [ ] 9.1 `GameSurface(tree: SceneTree)`: mudar assinatura. Atualizar `withFrameNanos` para alimentar `loop.tick(...)` com o `tree` recebido.
- [ ] 9.2 `ComposeHost.run(tree: SceneTree, config: GameConfig)`: mudar assinatura. Atualizar wiring de `Window`, key handling e chamada para `renderDebugOverlay(renderer, tree)`.
- [ ] 9.3 Conferir que `:engine-compose` não importa nem referencia o símbolo `Scene` após a mudança.

## 10. Pong sample

- [ ] 10.1 Editar `games/pong/src/main/resources/pong/scene.json`: mudar `root.type` de `com.neoutils.engine.scene.Scene` para `com.neoutils.engine.scene.Node`. Manter `version: 2`. Conferir que o `Camera2D` filho continua presente.
- [ ] 10.2 Editar `games/pong/src/main/kotlin/.../Main.kt`: envolver o resultado de `BundleLoader.fromResources("pong", ...)` em `SceneTree(root = ...)` antes de chamar `SkikoHost().run(tree, config)`. Conferir que o tipo declarado da variável é `Node` ou inferido.

## 11. Tic-tac-toe sample

- [ ] 11.1 Criar `games/tictactoe/.../TicTacToeRoot.kt` (`class TicTacToeRoot : Node()`) cujo `onEnter()` instancia `Board` e `StatusText` e adiciona como filhos. Migrar setup que estava em `TicTacToeScene` para esse `onEnter`.
- [ ] 11.2 Apagar `games/tictactoe/.../TicTacToeScene.kt`.
- [ ] 11.3 Editar `games/tictactoe/.../Main.kt`: `ComposeHost().run(SceneTree(root = TicTacToeRoot()), config)`.
- [ ] 11.4 Em `Board.kt` e `StatusText.kt`, substituir `rootScene()?.x` por `tree?.x`. Atualizar quaisquer casts `rootScene() as? TicTacToeScene` para `tree?.root as? TicTacToeRoot` (se ainda forem necessários).
- [ ] 11.5 Migrar o resize handler que estava em `TicTacToeScene.onResize` para `tree.onResize = { w, h -> ... }` instalado dentro de `TicTacToeRoot.onEnter()`.

## 12. Demos sample

- [ ] 12.1 Criar `games/demos/.../DemoSwitcherRoot.kt` (`class DemoSwitcherRoot : Node()`) cujo `onEnter()` popula a árvore como o `DemoSwitcherScene` fazia.
- [ ] 12.2 Apagar `games/demos/.../DemoSwitcherScene.kt`.
- [ ] 12.3 Editar `games/demos/.../Main.kt`: `SkikoHost().run(SceneTree(root = DemoSwitcherRoot()), config)`.
- [ ] 12.4 Em todos os demos (`TransformOrbitDemo.kt`, `ScaleHierarchyDemo.kt`, `SpawnerDemo.kt`, `CollisionStressDemo.kt`, `RotatingBoxDemo.kt`), substituir cada `rootScene()?.x` por `tree?.x`. Atualizar `rootScene() as? DemoSwitcherScene` para `tree?.root as? DemoSwitcherRoot` quando o tipo do root for consultado.
- [ ] 12.5 Auditar HUD overlays e similares dentro de demos — qualquer chamada residual a `Scene`/`rootScene()` deve estar 0.

## 13. Testes existentes

- [x] 13.1 `engine/src/test/kotlin/.../serialization/SceneLoaderTest.kt`: substituir construções de `Scene` por construções de `Node` root. Validar que `load` devolve `Node` (não `Scene`). Adicionar caso novo: load com root `engine.Node2D` funciona.
- [x] 13.2 `engine/src/test/kotlin/.../serialization/NodeRegistryTest.kt`: remover qualquer assertion que dependia de `engine.Scene` estar registrado. Adicionar caso negativo: `NodeRegistry.create("com.neoutils.engine.scene.Scene")` lança `UnknownNodeTypeException`.
- [x] 13.3 `engine/src/test/kotlin/.../dx/DebugOverlayTest.kt`: trocar parâmetro `Scene` por `SceneTree` em todas as chamadas a `renderDebugOverlay`.
- [x] 13.4 `engine/src/test/kotlin/.../physics/PhysicsSystemTest.kt`: trocar `Scene` por `SceneTree` na construção dos cenários de teste, e ajustar `physics.step(scene)` → `physics.step(tree)`.
- [x] 13.5 Rodar `./gradlew :engine:test` e confirmar verde. Auditar saída por warnings de deprecation ou imports não-utilizados.

## 14. Testes novos cobrindo SceneTree

- [x] 14.1 Criar `engine/src/test/kotlin/.../tree/SceneTreeTest.kt` cobrindo:
  - `SceneTree(root).start()` propaga `tree` para todos os descendentes e dispara `onEnter` em pre-order.
  - `SceneTree.stop()` zera `tree` em todos os descendentes e dispara `onExit` em post-order.
  - `addChild` num nó vivo propaga `tree` para o filho (e descendentes).
  - `removeChild` num nó vivo zera `tree` no filho (e descendentes).
  - `onResize` listener é chamado quando size muda; NÃO é chamado quando size não muda.
  - `getNodesInGroup` retorna nós em pre-order partindo do root.
  - `currentCamera()` faz pre-order tree-walk partindo do root.
- [x] 14.2 Confirmar que o campo `Node.tree` não aparece em JSON produzido por `SceneLoader.save` (cobertura do contrato `@Transient`).

## 15. Documentação

- [ ] 15.1 `CLAUDE.md`: adicionar invariante 5 sob "Architectural Invariants" exatamente como descrito no spec `project-conventions`. Atualizar referências narrativas a "Scene" para "SceneTree"/"root" onde aplicável.
- [ ] 15.2 `ROADMAP.md`: mover `scene-tree` para a seção Active enquanto a change está em andamento.
- [ ] 15.3 `CLAUDE.md` — seção "Module Structure & How to Run": atualizar texto que descreve carregamento de bundles para mencionar que `BundleLoader` devolve `Node` (root) e o `Main.kt` envolve em `SceneTree`.

## 16. Validação manual

- [ ] 16.1 `./gradlew :engine:test :engine-bundle:test :engine-bundle-python:test` — todos verdes.
- [ ] 16.2 `./gradlew :games:pong:run` — janela abre, paddles funcionam, AI persegue bola, F1/F2 alternam overlays.
- [ ] 16.3 `./gradlew :games:tictactoe:run` — janela Compose abre, cliques colocam marcas, resize recentraliza o board.
- [ ] 16.4 `./gradlew :games:demos:run` — todos os 5 demos (`1`–`5`) rodam idênticos ao comportamento pré-change. F1/F2 funcionam.
- [ ] 16.5 `grep -r "rootScene\|class Scene\|: Scene()" engine/ engine-bundle*/ engine-skiko/ engine-compose/ games/ --include="*.kt"` — zero resultados (exceto strings de teste apontando para `UnknownNodeTypeException`).
- [ ] 16.6 Conferir que `pong/scene.json` não contém mais `com.neoutils.engine.scene.Scene`.

## 17. Validação OpenSpec

- [ ] 17.1 `openspec validate scene-tree` — passa sem erros.
- [ ] 17.2 `openspec status --change scene-tree` — todos artifacts done; change pronta para `/opsx:apply`.

## 18. Coordenação com `bundle-tictactoe`

- [ ] 18.1 Adicionar nota no `bundle-tictactoe/proposal.md` ou `tasks.md` indicando que a change está em espera até `scene-tree` arquivar, e que após retomar o autor revisita o plano para refletir o retorno `Node` de `BundleLoader.fromResources`.
