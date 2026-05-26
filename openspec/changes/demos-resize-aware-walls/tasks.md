## 1. Verificar premissas técnicas

- [ ] 1.1 Inspecionar `PhysicsSystem.step` (em `:engine`) e confirmar que AABBs não são cacheados entre frames — mutar `RectangleShape2D.size` em runtime é seguro
- [ ] 1.2 Confirmar que `tree.size` é atualizado pelo host **antes** de `loop.tick(...)` em cada frame (já visível em `SkikoHost.kt:61` — apenas registrar)

## 2. Implementar `BoundaryWalls.kt` em `:games:demos`

- [ ] 2.1 Criar arquivo `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/BoundaryWalls.kt`
- [ ] 2.2 Declarar `internal fun makeStaticWall(position: Vec2, size: Vec2): StaticBody2D` top-level construindo `StaticBody2D` + `CollisionShape2D` + `RectangleShape2D` conforme contrato do spec
- [ ] 2.3 Declarar `@Serializable class BoundaryWalls(private val thickness: Float = 10f) : Node2D()` com `name = "BoundaryWalls"`
- [ ] 2.4 No `init` block, criar (se `children.isEmpty()`) 4 `StaticBody2D` nomeados (`topWall`, `bottomWall`, `leftWall`, `rightWall`) via `makeStaticWall`, com `position`/`size` placeholders (serão sobrescritos pelo primeiro relayout)
- [ ] 2.5 Adicionar `@Transient private var lastSize: Vec2 = Vec2.ZERO`
- [ ] 2.6 Sobrescrever `onPhysicsProcess(dt: Float)`: early-return se `tree?.size == lastSize`, senão chamar `relayout(width, height)` e atualizar `lastSize`
- [ ] 2.7 Implementar `private fun relayout(w: Float, h: Float)` que muta `transform.position` de cada body e `RectangleShape2D.size` de cada shape conforme as 4 fórmulas do spec
- [ ] 2.8 Adicionar KDoc curto na classe explicando: (a) que serve para demos sem `Camera2D` que precisam de fronteira física acompanhando a janela; (b) que demos com paredes em frame local (caso do demo 5) devem usar `makeStaticWall` direto

## 3. Migrar `CollisionStressDemo` (demo 4)

- [ ] 3.1 Remover `private fun makeWall(...)` de `CollisionStressDemo.kt`
- [ ] 3.2 Em `onEnter`, substituir as 4 chamadas a `addChild(makeWall(...).apply { name = "..." })` por uma única `addChild(BoundaryWalls())`
- [ ] 3.3 Remover a constante `WALL_THICKNESS` se não for mais referenciada (verificar antes — se demo ainda usa, ajustar)
- [ ] 3.4 Verificar que imports não-utilizados foram removidos (`StaticBody2D`, `RectangleShape2D` podem deixar de ser necessários se nada mais os referencia)

## 4. Migrar `TumblingSwarmDemo` (demo 6)

- [ ] 4.1 Remover `private fun makeWall(...)` de `TumblingSwarmDemo.kt`
- [ ] 4.2 Em `onEnter`, substituir as 4 chamadas a `addChild(makeWall(...).apply { name = "..." })` por uma única `addChild(BoundaryWalls())`
- [ ] 4.3 Avaliar `WALL_THICKNESS` e imports não-utilizados, ajustar igual ao demo 4

## 5. Migrar `RotatingBoxDemo` (demo 5)

- [ ] 5.1 Remover `private fun makeWall(...)` de `RotatingBoxDemo.kt`
- [ ] 5.2 Substituir as 4 chamadas a `wrapper.addChild(makeWall(...).apply { name = "..." })` por `wrapper.addChild(makeStaticWall(...).apply { name = "..." })` — comportamento idêntico (paredes continuam em frame local do wrapper)
- [ ] 5.3 Ajustar imports não-utilizados

## 6. Atualizar documentação

- [ ] 6.1 Em `CLAUDE.md`, na seção "Para rodar Demos", atualizar a descrição do demo `4` adicionando nota: "As paredes acompanham `tree.size` em tempo real durante resize da janela (via `BoundaryWalls`)."
- [ ] 6.2 Idem para o demo `6` Tumbling swarm
- [ ] 6.3 Demo `5` mantém descrição (comportamento idêntico — refator interno apenas)

## 7. Build & smoke test manual

- [ ] 7.1 `./gradlew :games:demos:compileKotlin` passa sem warnings novos
- [ ] 7.2 `./gradlew :games:demos:run`: pressionar `4`, redimensionar janela (aumentando E diminuindo), verificar que bolinhas batem no novo perímetro
- [ ] 7.3 Mesmo teste no demo `6` (tecla `6` + resize)
- [ ] 7.4 Demo `5` (tecla `5`): wrapper continua rotacionando dentro da janela, paredes locais giram junto, bolinhas continuam contidas — sem regressão visual
- [ ] 7.5 Demos `1`, `2`, `3` (sem mudança) continuam funcionando como antes
- [ ] 7.6 F2 (colliders debug overlay) mostra as 4 paredes desenhadas alinhadas com a janela atual em demos `4` e `6`

## 8. Verificação final

- [ ] 8.1 `openspec validate demos-resize-aware-walls --strict` passa
- [ ] 8.2 `/opsx:verify demos-resize-aware-walls` confirma alinhamento entre artifacts e código
