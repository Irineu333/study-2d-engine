## 1. Extract Rotator into its own file

- [x] 1.1 Criar `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/Rotator.kt` declarando `@Serializable class Rotator : Node2D()` com `var angularVelocity: Float = 1f` e `override fun onProcess(dt: Float) { transform = transform.copy(rotation = transform.rotation + angularVelocity * dt) }`. Mantém `name` default herdado (sem `init` que escreva `name`).
- [x] 1.2 Remover a declaração `class Rotator` do antigo `TransformOrbitDemo.kt` (será deletado em 2.1, mas o passo 1.x precisa funcionar isoladamente; rodar `./gradlew :games:demos:compileKotlin` para garantir build verde antes de prosseguir).
- [ ] 1.3 Rodar `./gradlew :games:demos:run` para confirmar que o demo atual continua funcional com o `Rotator` extraído (ainda usando `TransformOrbitDemo`).

## 2. Replace TransformOrbitDemo with SolarSystemDemo

- [x] 2.1 Deletar `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/TransformOrbitDemo.kt`.
- [x] 2.2 Criar `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/SolarSystemDemo.kt` declarando:
  - `@Serializable class SolarSystemDemo : Node2D()` com `init { name = "SolarSystemDemo"; if (children.isEmpty()) buildTree() }`.
  - Campo `@Transient private var lastSize: Vec2 = Vec2.ZERO`.
  - `override fun onProcess(dt: Float)` que reposiciona o nó `Center` quando `tree.size` muda (mesmo idiom do antigo demo).
  - Companion objects `Radii`, `Speeds`, `Sizes`, `Palette` contendo todas as constantes numéricas (raios orbitais por fração de `unit`, ω por corpo, raios visuais em px, cores RGBA). Valores conforme D4/D5/D7/D8 do design.
  - Método privado `buildTree()` que monta a topologia conforme D3 do design.
- [x] 2.3 No mesmo arquivo `SolarSystemDemo.kt`, declarar `class SaturnRing : Node2D()` (top-level) com:
  - `init { transform = Transform(scale = Vec2(1f, RING_FLATTEN)) }` onde `RING_FLATTEN = 0.4f`.
  - `override fun onDraw(renderer: Renderer)` chamando `renderer.drawCircle(Vec2.ZERO, RING_RADIUS, RING_COLOR, filled = false, thickness = RING_THICKNESS)`.
  - Constantes locais (`RING_RADIUS`, `RING_THICKNESS`, `RING_COLOR`) em uma `companion object` ou no top do arquivo.

## 3. Wire SolarSystemDemo into DemoSwitcherRoot

- [x] 3.1 Em `games/demos/.../DemoSwitcherRoot.kt`, renomear `Slot.Orbit` para `Slot.SolarSystem` no enum.
- [x] 3.2 Atualizar o `factories` map de `Slot.Orbit to ::TransformOrbitDemo` para `Slot.SolarSystem to ::SolarSystemDemo`.
- [x] 3.3 Atualizar o valor inicial `private var active: Slot = Slot.Orbit` para `Slot.SolarSystem`.
- [x] 3.4 Atualizar o `when` em `onProcess` da branch `Key.DIGIT_1 -> select(Slot.Orbit)` para `select(Slot.SolarSystem)`.
- [x] 3.5 Atualizar o `when` em `HudOverlay.onDraw`: branch `DemoSwitcherRoot.Slot.Orbit -> "1. Transform orbit (rotation -> position)"` para `DemoSwitcherRoot.Slot.SolarSystem -> "1. Solar system (nested transform composition)"`. Demais branches inalterados.

## 4. Update CLAUDE.md

- [x] 4.1 Em `CLAUDE.md`, na seção "Para rodar Demos" (sob "Durante a execução:"), substituir o item `1` por uma descrição do novo demo. O texto novo MUST: (a) nomear "Solar system"; (b) mencionar Sol, 8 planetas, e luas conhecidas (Lua, Galileanas de Júpiter, Titã, Tritão); (c) mencionar o anel de Saturno como `SaturnRing` (ou apenas "anel achatado via scale não-uniforme"); (d) reforçar que a pedagogia é composição aninhada de transform (até 4 níveis profundos: Sol → órbita-planeta → planeta → órbita-lua → lua), exercitando o invariante A1. Os itens `2` a `6` MUST permanecer literalmente inalterados.

## 5. Smoke-test the demo end-to-end

- [ ] 5.1 Rodar `./gradlew :games:demos:run`. Confirmar visualmente:
  - Sol amarelo no centro da janela.
  - 8 órbitas concêntricas com planetas diferenciáveis por cor.
  - Júpiter com 4 luas pequenas orbitando-o.
  - Saturno com anel elíptico achatado e Titã orbitando.
  - Terra com Lua, Netuno com Tritão.
  - Mercúrio mais rápido que Netuno (verificação por tempo de uma volta completa).
- [ ] 5.2 Pressionar `1`, `2`, `3`, `4`, `5`, `6` em sequência — confirmar que o switcher continua trocando demos sem crash e que o slot 1 mostra o sistema solar.
- [ ] 5.3 Pressionar `F1` (toggle FPS) e `F2` (toggle colliders) — confirmar que F1 funciona e que F2 não desenha nada (sistema solar não tem `CollisionObject2D`).
- [ ] 5.4 Redimensionar a janela e confirmar que o `Center` segue para o novo centro da viewport (planetas movem em bloco; raios não escalam, conforme D4).
- [ ] 5.5 Rodar `./gradlew :games:pong:run` e `./gradlew :games:tictactoe:run` rapidamente para confirmar que mudanças no `:games:demos` não vazaram para outros jogos (sanity check, deve ser instantâneo já que nenhuma dependência cruza).

## 6. Validate change

- [x] 6.1 Rodar `openspec validate solar-system-demo --strict` e endereçar qualquer warning/erro.
- [x] 6.2 Rodar `./gradlew :games:demos:build` (compilação + qualquer task default) para garantir build verde no módulo afetado.
