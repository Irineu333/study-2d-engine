## 1. Engine API — `Node2D` ergonomics & rename

- [ ] 1.1 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Node2D.kt`, adicionar as três properties `var position: Vec2`, `var rotation: Float`, `var scale: Vec2`, cada uma com getter delegando a `transform.<campo>` e setter delegando a `transform = transform.copy(<campo> = value)`. Adicionar KDoc curta destacando que escrita passa pelo setter de `transform` e portanto invalida o cache de `world()` automaticamente.
- [ ] 1.2 Em `Node2D.kt`, renomear `fun worldTransform(): Transform` → `fun world(): Transform` (preservar implementação e cache interno; renomear o campo `cachedWorldTransform` para `cachedWorld` por coerência).
- [ ] 1.3 Em `Node2D.kt`, remover `fun worldPosition(): Vec2`.

## 2. Engine API — folhas viram `open`

- [ ] 2.1 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Camera2D.kt`, trocar `class Camera2D` por `open class Camera2D`.
- [ ] 2.2 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Polygon2D.kt`, trocar `class Polygon2D` por `open class Polygon2D`.
- [ ] 2.3 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Circle2D.kt`, trocar `class Circle2D` por `open class Circle2D`.
- [ ] 2.4 Em `engine/src/main/kotlin/com/neoutils/engine/scene/ColorRect.kt`, trocar `class ColorRect` por `open class ColorRect`.
- [ ] 2.5 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Line2D.kt`, trocar `class Line2D` por `open class Line2D`.
- [ ] 2.6 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Timer.kt`, trocar `class Timer` por `open class Timer`.

## 3. Engine internal callsites — migrar para `world()`

- [ ] 3.1 Em `engine/src/main/kotlin/com/neoutils/engine/physics/BoxCollider.kt`, substituir `worldTransform()` por `world()`.
- [ ] 3.2 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Label.kt`, substituir `worldPosition()` por `world().position`.
- [ ] 3.3 Em `engine/src/main/kotlin/com/neoutils/engine/scene/ColorRect.kt`, substituir `worldTransform()` por `world()`; atualizar KDoc referenciando `worldTransform()`.
- [ ] 3.4 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Circle2D.kt`, substituir `worldTransform()` por `world()`; atualizar KDoc referenciando `worldPosition()`.
- [ ] 3.5 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Polygon2D.kt`, substituir `worldPosition()` por `world().position`; atualizar KDoc referenciando `worldPosition()`.
- [ ] 3.6 Em `engine/src/main/kotlin/com/neoutils/engine/scene/Line2D.kt`, substituir `worldPosition()` por `world().position`; atualizar KDoc referenciando `worldPosition()`.
- [ ] 3.7 Varrer `engine/src/main/kotlin` por menções residuais de `worldTransform` ou `worldPosition()` (excluindo nomes de parâmetro como `Camera2D.worldToScreen(worldPosition: Vec2, ...)` que NÃO mudam) e migrar.

## 4. Engine tests — renomear e cobrir novos accessors

- [ ] 4.1 Em `engine/src/test/kotlin/com/neoutils/engine/scene/WorldTransformTest.kt`, substituir todas as chamadas `worldTransform()` por `world()`.
- [ ] 4.2 Em `engine/src/test/kotlin/com/neoutils/engine/scene/WorldTransformCacheTest.kt`, substituir `worldTransform()` por `world()`; renomear o nome do método de teste `consecutive worldTransform calls return equal result` para `consecutive world calls return equal result` (e variantes equivalentes).
- [ ] 4.3 Em `engine/src/test/kotlin/com/neoutils/engine/scene/NodeTest.kt`, substituir `worldPosition()` por `world().position`; renomear o teste `worldPosition sums ancestor transforms` para `world().position sums ancestor transforms` (ou nome equivalente).
- [ ] 4.4 Adicionar um novo teste `Node2DErgonomicAccessorsTest.kt` em `engine/src/test/kotlin/com/neoutils/engine/scene/` cobrindo, no mínimo, os cinco cenários da nova requirement "Node2D exposes ergonomic local transform accessors" do spec `engine-core`: read mirror, set preserva os outros campos para cada um dos três accessors, e que set via accessor invalida o cache de descendente.
- [ ] 4.5 Em `engine/src/test/kotlin/com/neoutils/engine/scene/Camera2DTest.kt` e `SceneRenderCameraTest.kt`, migrar quaisquer chamadas residuais.
- [ ] 4.6 Rodar `./gradlew :engine:test` e confirmar verde.

## 5. Bindings Python — stubs e runtime

- [ ] 5.1 Em `engine-bundle-python/src/main/resources/stubs/engine/` (provavelmente `scene.pyi` ou `__init__.pyi`), atualizar o tipo `Node2D` para declarar `position: Vec2`, `rotation: float`, `scale: Vec2` como atributos públicos mutáveis, declarar `def world(self) -> Transform: ...`, e remover quaisquer declarações de `worldTransform` ou `worldPosition`.
- [ ] 5.2 No stub de `Vec2` (provavelmente `math.pyi`), adicionar docstring explicando imutabilidade e que `v.y = ...` lança `AttributeError`.
- [ ] 5.3 Em `engine-bundle-python/src/main/resources/_nengine_runtime.py`, remover/atualizar a docstring que menciona `self.worldPosition()` (linha ~207) para refletir o novo idioma (`self.world().position`).
- [ ] 5.4 Em `engine-bundle-python/src/test/kotlin/com/neoutils/engine/bundle/python/PythonRenderingIntegrationTest.kt` (linha ~118, dentro do script embarcado), substituir `self.worldPosition()` por `self.world().position`.
- [ ] 5.5 Rodar `./gradlew :engine-bundle-python:test` e confirmar verde.

## 6. Bundles e jogos — migração de scripts e Kotlin

- [ ] 6.1 Em `games/pong/src/main/resources/pong/scripts/paddle.py`: (a) na linha 41-45, substituir o bloco `self.transform = Transform(Vec2(pos.x, new_y), self.transform.scale, self.transform.rotation)` por `self.position = Vec2(pos.x, new_y)`; (b) na linha 49, substituir `wp = self.worldPosition()` por `wp = self.world().position`; (c) na linha 72, substituir `resolved.worldPosition().y` por `resolved.world().position.y`.
- [ ] 6.2 Em `games/pong/src/main/resources/pong/scripts/ball.py`: substituir `self.worldPosition()` por `self.world().position`; aplicar a mesma migração de `self.transform = Transform(...)` para `self.position = ...` (e idem `rotation`/`scale` se houver).
- [ ] 6.3 Em `games/pong/src/main/resources/pong/scripts/score.py`: substituir `self.worldPosition()` por `self.world().position`.
- [ ] 6.4 Em `games/pong/src/main/resources/pong/scripts/goal.py` e `pong_scene.py` e `center_line.py`: varrer por `worldPosition` / `worldTransform` e migrar (mesmo critério).
- [ ] 6.5 Em `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/RotatingBoxDemo.kt`, substituir as duas chamadas `worldTransform()` (linhas ~126 e ~188) por `world()`.
- [ ] 6.6 Em `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/ScaleHierarchyDemo.kt` e `TransformOrbitDemo.kt`, atualizar KDocs que referenciam `worldTransform()`.
- [ ] 6.7 Em `games/tictactoe/src/main/kotlin/` e `games/hello-world/src/main/kotlin/`, varrer por `worldTransform` / `worldPosition` e migrar (provavelmente zero hits, mas confirmar).
- [ ] 6.8 Varredura final: `grep -rn "worldTransform\|worldPosition()" .` (excluindo `build/`, `worldToScreen(worldPosition:`) deve voltar vazia.

## 7. Validação end-to-end

- [ ] 7.1 `./gradlew build` passa em todos os módulos.
- [ ] 7.2 `./gradlew :games:pong:run` — Pong roda; W/S movem o paddle esquerdo; IA funciona; gols incrementam score; bola reseta.
- [ ] 7.3 `./gradlew :games:demos:run` — todas as cenas (1-5) rodam sem erro; cena 5 (rotating box) confirma que `world()` cacheado se mantém correto sob rotação+translação de ancestral.
- [ ] 7.4 `./gradlew :games:tictactoe:run` — joga normalmente; backend Compose ainda vivo.
- [ ] 7.5 `./gradlew :games:hello-world:run` — janela 800×600 com `Hello, world!` centralizado.

## 8. Documentação

- [ ] 8.1 Em `CLAUDE.md`, atualizar a seção "Coding Conventions" / "Imutabilidade onde for barata" mencionando que `Node2D` expõe `position`/`rotation`/`scale` como properties ergonômicas, mas `Transform` e `Vec2` continuam imutáveis (e que `v.y = X` é proibido).
- [ ] 8.2 Em `CLAUDE.md`, atualizar a seção "Scripting contract (Python)" para usar `self.position = Vec2(...)` e `self.world().position` no exemplo de hooks; adicionar nota sobre o `AttributeError` em escrita de componente individual.
- [ ] 8.3 Em `CLAUDE.md`, registrar a política nova "todas as folhas `Node2D` shipped por `:engine` são `open` por default; tornar uma folha `final` exige justificativa documentada".
