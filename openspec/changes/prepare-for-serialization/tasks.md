## 1. Build setup

- [x] 1.1 Apply `org.jetbrains.kotlin.plugin.serialization` no `:engine/build.gradle.kts`
- [x] 1.2 Adicionar `org.jetbrains.kotlinx:kotlinx-serialization-json` em `libs.versions.toml` e como `api` dependency em `:engine`
- [x] 1.3 Confirmar via `./gradlew :engine:dependencies` que nada do Compose entrou transitivamente

## 2. Math and render primitives become @Serializable

- [x] 2.1 Anotar `Vec2`, `Rect`, `Transform` com `@Serializable`
- [x] 2.2 Anotar `Color` com `@Serializable`
- [x] 2.3 Adicionar teste `MathSerializationTest`: round-trip JSON dos quatro tipos confere equality
- [x] 2.4 Garantir que testes existentes (`Vec2Test`, `RectTest`, `TransformComposeTest`) continuam passando

## 3. Node identity — sibling name uniqueness

- [x] 3.1 Implementar auto-suffix em `Node.applyAdd` quando há conflito de nome entre irmãos
- [x] 3.2 Garantir que auto-suffix aplica também em drenagem de pending adds (`Node.drainPending`)
- [x] 3.3 Implementar `Node.findChild(name: String): Node?` (lookup single-level O(n))
- [x] 3.4 Adicionar testes em `NodeTest`: auto-suffix em conflito, incremento sucessivo, preservação sem conflito, sobrevivência ao detach, sem renumeração na remoção
- [x] 3.5 Adicionar testes em `NodeTest`: `findChild` happy path, retorno `null`, não recursivo

## 4. Signal primitive

- [x] 4.1 Criar `com.neoutils.engine.serialization.Signal<T>` com `plusAssign`, `minusAssign`, `emit`
- [x] 4.2 Implementar `emit` iterando sobre snapshot da lista (tolerar mutação durante emissão)
- [x] 4.3 Adicionar testes `SignalTest`: registro/desregistro, ordem de invocação, registro durante emit só vale na próxima, remoção durante emit não afeta o snapshot atual

## 5. NodeRef primitive

- [x] 5.1 Criar `com.neoutils.engine.serialization.NodeRef<T : Node>` como `@Serializable` com `path: String`
- [x] 5.2 Implementar `resolve(from: Node): T?` com walk relativo (`..`, segmentos por `/`, path vazio = self)
- [x] 5.3 Implementar cache lazy de resolução com invalidação ao re-attach do bearer (hook em `attachToLiveTree`)
- [x] 5.4 Adicionar testes `NodeRefTest`: walk up/down, tipo incompatível devolve `null`, path inválido devolve `null`, empty path resolve to bearer, round-trip JSON, invalidação após re-attach

## 6. @Inspect annotation

- [x] 6.1 Criar `com.neoutils.engine.serialization.Inspect` (`@Target(PROPERTY)`, `@Retention(RUNTIME)`, `displayName: String = ""`)
- [x] 6.2 Documentar no `CLAUDE.md` o contrato: classes serializáveis levam `@Serializable`; toda `var` deve ter `@Inspect` ou `@Transient` explícito

## 7. Engine Node classes — no-args + @Serializable + @Inspect

- [x] 7.1 `Node` e `Node2D` ganham `@Serializable`. `transform` em `Node2D` vira `@Inspect`. `name` em `Node` vira `@Inspect` (com `@Transient` ou serializável conforme decidido — provavelmente `@Inspect`, faz parte da identidade)
- [x] 7.2 Marcar campos internos de `Node` (`parent`, `_children`, `isLive`, `scene`, `pendingAdd`, `pendingRemove`) com `@Transient`
- [x] 7.3 `Shape`: construtor no-args; `kind`, `size`, `color`, `filled` viram `@Inspect var` com defaults atuais
- [x] 7.4 `Text`: construtor no-args; `text`, `size`, `color` viram `@Inspect var`
- [x] 7.5 `BoxCollider`: construtor no-args; `size` vira `@Inspect var = Vec2(10f, 10f)`
- [x] 7.6 `Collider` (abstrato) anotado `@Serializable` para permitir polimorfismo das subclasses
- [x] 7.7 `Scene` ganha `@Serializable`; `width`, `height` ficam `@Transient` (vão ser recalculados no `resize`); `input` continua `@Transient`
- [x] 7.8 Garantir que todos os testes existentes de `Node`/`Scene`/`Shape`/`Text`/`Collider` ainda passam após mudança de construtores

## 8. NodeRegistry

- [x] 8.1 Criar `com.neoutils.engine.serialization.NodeRegistry` objeto/singleton com `register(KClass<out Node>, () -> Node)` e `create(typeName: String): Node`
- [x] 8.2 Criar `UnknownNodeTypeException` carregando o nome do tipo desconhecido
- [x] 8.3 Pré-registrar tipos do `:engine` em uma chamada `NodeRegistry.registerEngineTypes()` documentada (jogos chamam isso + os seus próprios)
- [x] 8.4 Testes `NodeRegistryTest`: registro + criação, tipo não registrado lança exceção com mensagem útil

## 9. SceneLoader

- [x] 9.1 Definir o modelo de nó serializado: `data class NodeEntry(type: String, name: String, properties: JsonObject, children: List<NodeEntry>)` em `:engine`
- [x] 9.2 Definir wrapper `data class SceneFile(version: Int = 1, root: NodeEntry)`
- [x] 9.3 Implementar `SceneLoader.save(scene: Scene): String` caminhando a árvore e extraindo as propriedades `@Inspect` via reflexão (Kotlin reflect ou serializer da classe)
- [x] 9.4 Implementar `SceneLoader.load(json: String): Scene` parseando, criando nodes via `NodeRegistry`, populando propriedades, e anexando children por `addChild`
- [x] 9.5 Garantir que `load` retorna scene detached (`isLive == false`)
- [x] 9.6 Garantir que `onEnter` só corre após `Scene.start()` posterior ao load
- [x] 9.7 Testes `SceneLoaderTest`: save produz JSON válido com `version`/`root`; load preserva ordem e propriedades; round-trip estável; `onEnter` não roda no load

## 10. Refactor :games:pong

- [x] 10.1 `Ball` passa a estender `BoxCollider`. Remover `Ball.collider` (subclasse anônima). Mover `handleCollision` para `override fun onCollide` do próprio `Ball`
- [x] 10.2 `Ball.onScore` vira `Signal<Goal.Side>` (campo `@Transient`)
- [x] 10.3 Construtor de `Ball` vira no-args. Propriedades `size`, `initialSpeed`, `maxSpeed`, `speedupPerHit`, `fieldCenter` viram `@Inspect var` com defaults. `velocity`, `scoredThisTick`, `random` viram `@Transient` (ou `random` injetável via método `setRandom` se for o caso de teste)
- [x] 10.4 `Paddle.aiTargetY` lambda → `var target: NodeRef<Node2D> = NodeRef("")`. Computação do AI usa `target.resolve(this)` + `worldPosition().y`
- [x] 10.5 Construtor de `Paddle` vira no-args. `size`, `playFieldHeight`, `upKey`, `downKey`, `ai`, `speed`, `aiMaxSpeed`, `aiTolerance` viram `@Inspect var`. Recriar `PaddleCollider` como child no `init {}` ou via `onEnter` (verificar fluxo de lifecycle)
- [x] 10.6 `Score` continua simples: `textSize`, `color` viram `@Inspect var` com defaults. `value` continua `@Transient` (estado runtime)
- [x] 10.7 `Wall`, `Goal`, `PaddleCollider`: construtores no-args. `size` herda de `BoxCollider`. `Goal.side` vira `@Inspect var Side = Side.Left`
- [x] 10.8 `CenterLine` (private class em `PongScene.kt`) promovida a top-level com `@Serializable`. Construtor no-args; `x`, `height` viram `@Inspect var`
- [x] 10.9 `PongScene`: construtor no-args. Layout inicial via `onEnter` (já que `width`/`height` chegam por `onResize`). Conexão Ball→Score via `ball.onScore += { side -> ... }` dentro de `onEnter`
- [x] 10.10 Registrar todos os tipos de Pong em `NodeRegistry` no `Main.kt` (e na nova `MainFromFile.kt`)
- [x] 10.11 Rodar `./gradlew :games:pong:run` e verificar gameplay igual ao anterior

## 11. Refactor :games:demos (SpawnerDemo)

- [x] 11.1 Promover `Spawner` (object anônimo) a classe top-level `Spawner : Node2D` no mesmo arquivo ou em arquivo separado. Construtor no-args; propriedades como `autoSpawnInterval`, `spawnArea` viram `@Inspect var`
- [x] 11.2 `Trap` (private class) já é classe nomeada — adicionar `@Serializable` + `@Inspect` para `size` etc.
- [x] 11.3 `Ball` interno (private class) idem. Propriedades `velocity` ficam `@Transient`
- [x] 11.4 Rodar `./gradlew :games:demos:run` e verificar comportamento idêntico (transform orbit, scale hierarchy, spawner)

## 12. Ajustes mínimos em :games:tictactoe

- [x] 12.1 Anotar `Board`, `StatusText`, `TicTacToeScene` com `@Serializable`. Construtores no-args. Estado runtime (`cells`, `currentPlayer`, `winner`, `winningLine`, `isDraw`, `hoveredCell`) marcado como `@Transient`
- [x] 12.2 Propriedades configuráveis (`textSize` se existir, dimensões iniciais) viram `@Inspect var`
- [x] 12.3 Rodar `./gradlew :games:tictactoe:run` e verificar comportamento idêntico

## 13. Pong file end-to-end

> Redireção durante aplicação: `pong.scene.json` foi escrito à mão (não via
> `SceneLoader.save`) e o arquivo passou a ser o **único** entry point de Pong
> — `Main.kt` carrega `pong.scene.json` via `SceneLoader.load`. Não há
> `MainFromFile.kt` separado nem task `runFromFile`. Os subitens abaixo ficam
> marcados pela nova leitura.

- [x] 13.1 Escrever `pong.scene.json` à mão no formato `SceneFile`/`NodeEntry` em `:games:pong/src/main/resources/`
- [x] 13.2 `Main.kt` lê o resource, registra tipos, faz `SceneLoader.load`, executa via `SkikoHost`
- [x] 13.3 Sem entry adicional no `build.gradle.kts`: o `application { mainClass }` já aponta para `MainKt`, que agora carrega do arquivo
- [x] 13.4 Documentar no `CLAUDE.md` que `./gradlew :games:pong:run` carrega `pong.scene.json` como fonte da verdade da árvore
- [x] 13.5 Rodar `./gradlew :pong:pong:run` e verificar comportamento idêntico

## 14. Tests and verification

- [ ] 14.1 Rodar `./gradlew test` em todos os módulos — todos verdes
- [ ] 14.2 Validar manualmente: Pong (code-only), Pong (from file), Demos, Tic Tac Toe
- [ ] 14.3 Rodar `openspec validate --strict --change prepare-for-serialization`
- [ ] 14.4 Atualizar `CLAUDE.md` (Roadmap table, regras de @Inspect/@Transient, instruções de run from file)
