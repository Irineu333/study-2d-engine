## Why

`Scene` hoje é um `Node` (`engine/scene/Scene.kt:11` — `open class Scene : Node()`) — uma decisão tomada lá atrás em `godot-style-foundation` que carregou duas identidades sobrepostas: ser raiz da árvore *e* ser o dono dos concerns do loop/host (`input`, `size`, `viewport`, flags de fase, `process/physicsProcess/render`, `getNodesInGroup`, projeção `screen<->world`). Isso destoa explicitamente do modelo Godot, onde "cena" é arquivo (`PackedScene`) e a árvore viva é dona de um `SceneTree` **não-Node**. O sintoma é triplo: (a) os jogos code-only acabam subclassando `Scene` só para popular a árvore inicial (`TicTacToeScene`, `DemoSwitcherScene`) — herança no lugar errado; (b) o `SceneLoader.load(...)` exige `root as? Scene`, fixando o tipo do root da cena serializada num conceito que nem deveria estar na árvore; (c) o `pong/scene.json` já declara `engine.Scene` como root só para satisfazer esse contrato, mesmo que conceitualmente seu root pudesse ser um `Node` qualquer.

Esta change separa os dois conceitos no estilo Godot: introduz `SceneTree` em `:engine` como dono da árvore viva (não é Node, não é `@Serializable`), apaga `Scene`, libera o tipo do root, e migra jogos+bundles para o novo modelo. Também abre o caminho do editor visual sem inviabilizar uso direto via código — a API `Host.run(SceneTree(root = ...))` continua simples para code-only, e o `.json` deixa de carregar a marca "Scene" no root.

## What Changes

### Núcleo: `SceneTree` em `:engine`

- **NEW** `com.neoutils.engine.tree.SceneTree` — classe Kotlin pura, **não estende `Node`**, **não é `@Serializable`**. Construtor `SceneTree(root: Node)`. Recolhe da `Scene` antiga: `input`, `size: Vec2`, `width`/`height`, `viewport: Rect`, `isMutationDeferred`, `isRendering`, `start()`, `stop()`, `process(dt)`, `physicsProcess(dt)`, `render(renderer)`, `applyPending()`, `getNodesInGroup(name)`, `currentCamera()`, `screenToWorld(p)`, `worldToScreen(p)`, `resize(w, h)`, e o slot `onResize`.
- **NEW** `var onResize: ((Float, Float) -> Unit)? = null` em `SceneTree`. Slot opcional disparado por `resize()` quando `width`/`height` mudam. (Listener-style em vez de método aberto; `SceneTree` não foi feito para ser subclassado. Promoção a `Signal<Vec2>` fica fora de escopo para não conflitar com `node-timer`, que reivindica ser o primeiro Kotlin Signal.)
- **NEW** `Node.tree: SceneTree?` (`@Transient`, setter `internal`). Cacheado em `attachToLiveTree` recursivamente, zerado em `detachFromLiveTree`. Substitui o caminho `Node.scene`/`rootScene()` e roda em O(1).

### Remoções no `:engine`

- **BREAKING** `com.neoutils.engine.scene.Scene` é apagado. Sem alias, sem typealias, sem shim.
- **BREAKING** `Node.rootScene(): Scene?` é apagado. Substituído por `Node.tree: SceneTree?` (campo cacheado direto). Quem hoje fazia `rootScene()?.width` passa a `tree?.width`.
- **MODIFIED** `NodeRegistry.registerEngineTypes()` deixa de registrar `Scene::class`. O identificador `com.neoutils.engine.scene.Scene` deixa de existir no registry; tentar instanciá-lo via `NodeRegistry.create("com.neoutils.engine.scene.Scene")` falha com a mensagem padrão "no factory registered".

### Loop, hosts, física, debug

- **BREAKING** `GameLoop` muda assinatura: campo `scene: Scene` → `tree: SceneTree`. Chamadas internas `scene.process/physicsProcess/render/applyPending` viram `tree.process/...`.
- **BREAKING** `GameHost.run(scene: Scene, config)` → `GameHost.run(tree: SceneTree, config)`. `SkikoHost` e `ComposeHost` implementam a nova assinatura.
- **MODIFIED** `PhysicsSystem.step(...)` passa a receber `SceneTree` (ou o `root: Node` da árvore — definido em design.md, ver D2). A enumeração de colliders ativos parte do `root` da árvore.
- **MODIFIED** `DebugOverlay`/`renderDebugOverlay` aceita `SceneTree` no lugar de `Scene`. O ponto de chamada nos hosts não muda; só o tipo.

### Carregamento e bundle

- **BREAKING** `SceneLoader.load(text, attachScript)` retorna `Node` (root livre), não `Scene`. O cast `root as? Scene` é removido junto com a mensagem "Root node is not a Scene".
- **BREAKING** `SceneLoader.save(...)` passa a aceitar `root: Node` (não `Scene`). Não há mudança no schema do `SceneFile` — `version` permanece `2`.
- **BREAKING** `BundleLoader.fromResources(...)` retorna `Node` (root livre). Quem ia entregar a uma `Scene` agora envolve em `SceneTree(root = ...)`.

### Migração: Pong (bundle ativo)

- **MODIFIED** `games/pong/src/main/resources/pong/scene.json`: root `"type": "com.neoutils.engine.scene.Scene"` → `"type": "com.neoutils.engine.scene.Node"`. O `Camera2D` filho continua carregando `bounds` e a view transform; o root virou container puro. Sem bump de `version`.
- **MODIFIED** `games/pong/src/main/kotlin/.../Main.kt`: chamada `SkikoHost.run(bundle, config)` é envolvida em `SkikoHost.run(SceneTree(root = bundle), config)`.

### Migração: TicTacToe (code-only, hoje)

- **BREAKING** `games/tictactoe/.../TicTacToeScene.kt` apagado. Substituído por `TicTacToeRoot : Node()` com o setup atual movido para `onEnter()`.
- **MODIFIED** `Main.kt`: `ComposeHost.run(TicTacToeScene(), config)` → `ComposeHost.run(SceneTree(root = TicTacToeRoot()), config)`.
- **MODIFIED** `Board.kt` e `StatusText.kt`: `rootScene()?.width` → `tree?.width`. Cast `rootScene() as? TicTacToeScene` (se houver) → `tree?.root as? TicTacToeRoot`.
- **NOTE** Esta mudança não conflita com `bundle-tictactoe` (que substituirá `TicTacToeRoot` por bundle JSON); apenas garante que durante o intervalo Velha continua executável. `bundle-tictactoe` retoma após `scene-tree` arquivar.

### Migração: Demos

- **BREAKING** `games/demos/.../DemoSwitcherScene.kt` apagado. Substituído por `DemoSwitcherRoot : Node()` com `onEnter()` populando os slots.
- **MODIFIED** `Main.kt`: `SkikoHost.run(DemoSwitcherScene(), config)` → `SkikoHost.run(SceneTree(root = DemoSwitcherRoot()), config)`.
- **MODIFIED** Todos os `rootScene()?.x` em demos (`TransformOrbitDemo`, `ScaleHierarchyDemo`, `SpawnerDemo`, `CollisionStressDemo`, `RotatingBoxDemo`) viram `tree?.x`. Cast `as TicTacToeScene`/`as DemoSwitcherScene` removidos onde houver.

### Invariantes adicionados ao `CLAUDE.md`

- Novo invariante (numerado 5, após os quatro atuais): *"A árvore viva é dona de `SceneTree`, não de uma `Scene` que é Node. `Scene` como tipo não existe mais. Nodes alcançam a árvore via `node.tree` (set no attach, null no detach). `SceneTree` não é subclassável para customizar setup — para popular a árvore inicial, escreva um Node root com `onEnter`. `SceneLoader.load` e `BundleLoader` devolvem `Node` (root livre); o host envolve em `SceneTree(root = ...)`."*

## Capabilities

### New Capabilities

(nenhuma — o novo `SceneTree` substitui requisitos existentes de `Scene` em `engine-core`, não cria capability nova)

### Modified Capabilities

- `engine-core`: substitui inteiramente os requisitos "Scene as root container", "Scene reference cached on Node", "Scene rendering decoupled from DX surface" e o requisito de `GameHost` que tipava `run(scene: Scene, ...)`. Reformula requisitos de `Camera2D`/`viewport`/`screenToWorld` para referirem-se a `SceneTree` e ao `root`. Adiciona requisito novo sobre `SceneTree` como dono da árvore viva e sobre `Node.tree` cacheado.
- `scene-serialization`: muda contrato de `SceneLoader.load`/`save` — root livre tipado como `Node`, mensagens de erro do legado removidas, `engine.Scene` deixa de ser tipo registrado.
- `bundle-loading`: `BundleLoader.fromResources` passa a devolver `Node`, não `Scene`.
- `skiko-runtime`: `SkikoHost.run` muda assinatura para `(tree: SceneTree, config)`.
- `compose-runtime`: `ComposeHost.run` muda assinatura para `(tree: SceneTree, config)`.
- `dx-tooling`: `renderDebugOverlay` aceita `SceneTree` no lugar de `Scene`.
- `pong-sample`: `pong/scene.json` root migra de `engine.Scene` para `engine.Node`; `Main.kt` envolve o bundle em `SceneTree`.
- `tictactoe-sample`: `TicTacToeScene` deixa de existir como classe; root passa a ser um `Node` puro (`TicTacToeRoot`) com setup em `onEnter`; `Main.kt` envolve em `SceneTree`.
- `project-conventions`: incorpora o invariante novo (`SceneTree` é dono da árvore viva; `Scene` não é Node).

## Impact

- **Código tocado:**
  - `:engine` — `scene/Scene.kt` (DELETED), `tree/SceneTree.kt` (NEW), `scene/Node.kt` (campo `tree`, remoção de `rootScene()`), `loop/GameLoop.kt` (campo + assinatura), `runtime/GameHost.kt` (interface), `physics/PhysicsSystem.kt` (assinatura/uso), `dx/DebugOverlay.kt` (assinatura), `serialization/NodeRegistry.kt` (remove `Scene::class`), `serialization/SceneLoader.kt` (retorno `Node`).
  - `:engine-skiko` — `SkikoHost.kt` (assinatura `run`).
  - `:engine-compose` — `ComposeHost.kt` (assinatura `run`).
  - `:games:pong` — `Main.kt` (envelope `SceneTree`), `scene.json` (root type).
  - `:games:tictactoe` — `TicTacToeScene.kt` DELETED, `TicTacToeRoot.kt` NEW, `Main.kt`, `Board.kt`, `StatusText.kt`.
  - `:games:demos` — `DemoSwitcherScene.kt` DELETED, `DemoSwitcherRoot.kt` NEW, `Main.kt`, todos os arquivos de demo (`rootScene()` → `tree?`).
- **Testes tocados:** `SceneLoaderTest`, `NodeRegistryTest`, `DebugOverlayTest`, `PhysicsSystemTest` — todos quatro têm referência direta a `Scene` ou ao registro `engine.Scene`. Auditar e adaptar para `SceneTree` + root livre.
- **Documentação:** `CLAUDE.md` recebe o invariante novo + atualiza referências "Scene" no texto narrativo. `ROADMAP.md` recebe `scene-tree` em Active.
- **Coordenação com changes ativas:**
  - `bundle-tictactoe` fica em espera; será retomada após `scene-tree` arquivar e adaptará seu plano (já não precisará tipar root como `Scene`).
  - `collision-overhaul`, `node-timer`, `game-snake` permanecem ativas em paralelo — ortogonais a esta change.
- **Backward compatibility:** zero. Esta change é deliberadamente *breaking* em toda a superfície que toca `Scene`. Nenhum jogo externo depende do código (engine é monorepo de aprendizado). Não há shim, alias, ou camada de tradução.
- **Editor futuro:** liberar o tipo do root no JSON é prerrequisito para o editor abrir `.scene.json` arbitrários sem categoria especial. Esta change paga essa dívida agora, quando há 3 jogos para migrar — escolha barata.
