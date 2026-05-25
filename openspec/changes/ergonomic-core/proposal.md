## Why

O núcleo da engine penaliza modificações triviais. Subclassificar uma folha visual (`Circle2D`, `ColorRect`, `Camera2D`, `Polygon2D`, `Line2D`, `Timer`) é proibido porque essas classes ficaram `final` por default do Kotlin, contradizendo o invariante #1 ("comportamento de gameplay é adicionado por subclasses"). E qualquer ajuste de posição precisa reconstruir o `Transform` inteiro (`node.transform = Transform(Vec2(pos.x, newY), transform.scale, transform.rotation)`) — cinco linhas para "muda só o y", tanto em Kotlin quanto em Python. Ambas as fricções têm a mesma natureza: ritual cerimonial para mudança óbvia.

## What Changes

- **BREAKING** Folhas hoje finais viram `open`: `Camera2D`, `Polygon2D`, `Circle2D`, `ColorRect`, `Line2D`, `Timer`. Política passa a ser "todas as folhas `open` salvo justificativa documentada" (alinhada com o invariante #1).
- Adicionar três properties ergonômicas em `Node2D` que delegam ao `Transform` imutável:
  - `var position: Vec2` (get/set via `transform.copy(position = ...)`)
  - `var rotation: Float` (idem)
  - `var scale: Vec2` (idem)
- **BREAKING** Renomear `Node2D.worldTransform(): Transform` → `Node2D.world(): Transform`. Função (não property) para sinalizar transparência de "isto é computado e cacheado".
- **BREAKING** Remover `Node2D.worldPosition(): Vec2`. Substituto idiomático: `node.world().position`.
- `Transform` e `Vec2` permanecem imutáveis (`data class` com `val`). A ergonomia é puramente sintática — todo set passa pelo setter de `transform`, que já dispara `invalidateWorldTransformRecursive()`.
- Bindings Python passam a expor `position`, `rotation`, `scale` e `world()` naturalmente; scripts migram de `self.transform = Transform(...)` para `self.position = ...`.

## Capabilities

### New Capabilities
<!-- nenhuma capability nova; é refinamento ergonômico de superfície existente -->

### Modified Capabilities
- `engine-core`: política de extensibilidade das folhas `Node2D` muda (todas open por default); API de `Node2D` ganha properties `position`/`rotation`/`scale` e renomeia `worldTransform()`/`worldPosition()` para `world()`.
- `python-scripting`: bindings ergonômicos novos disponíveis nos scripts; scripts existentes do projeto migram.
- `timer-node`: `Timer` deixa de ser `final` (passa a `open`) para alinhar com a política.
- `pong-sample`: scripts Python do Pong migram (`paddle.py` usa `self.position`/`self.world()` no lugar do ritual atual).

## Impact

**Código tocado**:
- `engine/src/main/kotlin/com/neoutils/engine/scene/Node2D.kt` — adiciona properties, renomeia `worldTransform()`→`world()`, remove `worldPosition()`.
- `engine/src/main/kotlin/com/neoutils/engine/scene/{Camera2D,Polygon2D,Circle2D,ColorRect,Line2D,Timer}.kt` — viram `open class`.
- Call sites internos da engine que chamam `worldTransform()`/`worldPosition()`: `engine/src/main/kotlin/com/neoutils/engine/tree/SceneTree.kt`, `engine/src/main/kotlin/com/neoutils/engine/physics/{BoxCollider,PhysicsSystem}.kt`, `engine/src/main/kotlin/com/neoutils/engine/dx/DebugOverlay*`, `engine/src/main/kotlin/com/neoutils/engine/scene/Camera2D.kt`.
- Testes a renomear: `WorldTransformTest`, `WorldTransformCacheTest`, `Camera2DTest`, `SceneRenderCameraTest`.
- Bundles e jogos: `:games:pong` (scripts Python), `:games:demos` (cenas Kotlin de demonstração), `:games:tictactoe`, `:games:hello-world`.
- Stubs Python: `engine-bundle-python/src/main/resources/stubs/engine/__init__.pyi` (assinaturas de `Node2D` e `world()`).

**Dependências**: nenhuma adição ou alteração de dependência externa.

**Surface de API pública**: quebra dupla — remoção de `worldPosition()` e renomeação de `worldTransform()`. Como a engine ainda não é versionada/publicada e os call sites vivem todos no monorepo, o blast radius é controlado por uma varredura única e uma compilação.

**Pegadinha documentada (não-bug)**: em Python, `self.position.y = 5` lança `AttributeError` porque `Vec2.y` é `val` Kotlin sem setter. Comportamento intencional e alinhado ao scripting contract (fail-fast). O caminho correto é `self.position = Vec2(self.position.x, 5)`.

**Performance**: zero overhead — properties são puro açúcar sobre `transform = transform.copy(...)`, que já era o caminho recomendado.