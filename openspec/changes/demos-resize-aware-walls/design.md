## Context

Os demos do módulo `:games:demos` rodam em pixels brutos da surface por design (sem `Camera2D`). Isso significa que o "mundo" do demo é literalmente o retângulo da janela e qualquer mutação de `tree.size` precisa ser propagada para os elementos geométricos do demo.

Hoje, três padrões coexistem para lidar com `tree.size`:

1. **Demo 3 (Spawner)** usa polling em `onProcess`: guarda `lastSize`, se mudou reposiciona o `Trap` no centro. Funciona, mas a lógica vive misturada no game-loop do demo.
2. **Demo 5 (RotatingBox)** evita o problema colocando suas paredes no frame local de um wrapper rotativo cujo bouncing já lê `tree.size` por frame.
3. **Demos 4 (CollisionStress) e 6 (TumblingSwarm)** posicionam paredes uma única vez em `onEnter` e nunca mais atualizam — bug funcional: bolinhas escapam ou batem em barreiras invisíveis após resize.

Além disso, a função `private fun makeWall(position: Vec2, size: Vec2): StaticBody2D` aparece duplicada literal em três arquivos (`CollisionStressDemo.kt`, `RotatingBoxDemo.kt`, `TumblingSwarmDemo.kt`).

A decisão arquitetural prévia de **não usar `Camera2D` nos demos** foi confirmada pelo usuário ao validar esta change — demos continuam expressos em pixels da surface, e quem precisa reagir a resize reage explicitamente.

## Goals / Non-Goals

**Goals:**

- Eliminar o bug funcional dos demos 4 e 6: paredes acompanham `tree.size` em tempo real.
- DRY: extrair `makeWall` duplicado em um único utilitário compartilhado.
- Manter cada demo legível como exercício standalone — o uso do helper é uma única chamada (`addChild(BoundaryWalls())`).
- Preservar comportamento idêntico em demo 5 (paredes em frame local não devem reagir a `tree.size`).

**Non-Goals:**

- Promover `SceneTree.onResize` a `Signal<Vec2>` para multi-listener. O KDoc do slot já antecipa isso "when demand emerges"; demanda não emergiu nessa change (apenas um listener por demo).
- Adotar `Camera2D` nos demos. Decisão re-confirmada nesta change.
- Refatorar demo 3 (`SpawnerDemo`) para usar `BoundaryWalls`. Demo 3 não tem paredes de fronteira — só um `Trap` central e clamping inline em `SpawnerBall.onProcess`. Migrá-lo seria mudança de escopo.
- Adicionar política de "teleportar bolinhas que ficaram fora do novo retângulo". O sweep do `moveAndCollide` tolera overlap inicial e o reflect normal corrige no próximo frame; ganho não justifica complexidade.
- Refatorar a estrutura de `:games:demos` ou criar capability nova para cada demo individual. A capability `demos-sample` introduzida aqui cobre o módulo como um todo; demos individuais ganham requirements conforme novas demandas surgem.

## Decisions

### Decision 1: `BoundaryWalls` é uma classe `Node2D` reutilizável, não um mixin/trait

`BoundaryWalls` herda de `Node2D` (consistente com invariante #1 do projeto — comportamento por herança, sem ECS). Os demos a usam via `addChild(BoundaryWalls())` no `onEnter` do próprio demo. Internamente, `BoundaryWalls` mantém referências aos 4 `StaticBody2D` filhos (criados no `init` block) e ao `RectangleShape2D` de cada um; em `onPhysicsProcess` faz polling: se `tree.size != lastSize`, atualiza `transform.position` e `shape.size` das 4 paredes.

**Alternativa considerada**: deixar cada demo manualmente fazer polling em `onPhysicsProcess` (mesma forma do demo 3 hoje). Rejeitada porque mantém a duplicação intacta (3 cópias de `makeWall` + 3 lugares com a mesma lógica de polling).

**Alternativa considerada**: hook em `SceneTree.onResize` instalado no `onEnter` e limpo no `onExit`. Rejeitada por dois motivos: (a) o slot é single-listener — se mais de um demo ou o `DemoSwitcherRoot` quiser escutar, o último ganha; (b) polling é mais simples (sem cleanup obrigatório, sem ordem-de-lifecycle frágil) e o custo é trivial (uma comparação `Vec2` por frame).

### Decision 2: Polling em `onPhysicsProcess`, não em `onProcess`

`onPhysicsProcess` roda no fixed-step (60Hz default). Atualizar as paredes antes do sweep da física garante que o frame que detecta o resize já tem geometria consistente para `moveAndCollide`. Se atualizássemos em `onProcess` (frame-step), poderia haver um sub-frame em que a física vê paredes antigas e o render desenha as novas.

**Trade-off**: demos com `physicsHz` muito baixo veriam o resize com latência. Não é o caso (default 60Hz é alto o suficiente para resize ser percebido como instantâneo). Demo 3 fez a escolha oposta (`onProcess`); ele permanece como está — não há valor em "uniformizar" porque demo 3 reposiciona apenas o `Trap` visual, não geometria participando da física.

### Decision 3: `makeStaticWall` é função top-level (não método de companion)

Função `internal fun makeStaticWall(position: Vec2, size: Vec2): StaticBody2D` no mesmo arquivo `BoundaryWalls.kt`. Demo 5 chama `wrapper.addChild(makeStaticWall(...))` no lugar do antigo `wrapper.addChild(makeWall(...))`.

**Alternativa considerada**: tornar `BoundaryWalls` companion `BoundaryWalls.makeStatic(position, size)`. Rejeitada — função top-level é idiomática em Kotlin para utilitários sem estado, e mantém o helper acessível sem importar a classe.

### Decision 4: `BoundaryWalls` recebe `thickness` por construtor

`class BoundaryWalls(private val thickness: Float = 10f) : Node2D()`. Default casa com `WALL_THICKNESS = 10f` usado pelos demos 4 e 6 hoje. Demos que quiserem outra espessura passam explicitamente.

**Alternativa considerada**: hardcoded `WALL_THICKNESS = 10f`. Rejeitada porque uma constante exigiria refator se algum demo futuro quiser espessura diferente; parâmetro com default é mais flexível sem custo de complexidade.

### Decision 5: `BoundaryWalls` é `@Serializable`

Marcada `@Serializable` para futuro carregamento via bundle, mesmo que hoje os demos sejam code-only. `thickness` no construtor é `val`, então não precisa de `@Inspect`/`@Transient` — kotlinx.serialization aceita parâmetros do construtor primário com default value.

## Risks / Trade-offs

- **Mutar `RectangleShape2D.size` em runtime** → o `PhysicsSystem.step` recomputa AABBs a cada frame (não cacheia entre frames). Verificado: zero estado entre-frames no broad phase. Mitigação: nenhuma necessária; documentado no `tasks.md` como ponto a confirmar via inspeção do `PhysicsSystem`.
- **Bolinhas penetrando paredes após janela encolher** → durante o frame do resize, uma bolinha pode ficar dentro da parede recém-encolhida. O sweep do `moveAndCollide` tolera overlap inicial (retorna `null` se já não há movimento, ou reflect normal se há). Mitigação: aceitar; testar visualmente para confirmar que o frame de "preso" não é perceptível em uso real.
- **Demo 5 fica "diferente" dos demos 4/6** → demo 5 usa `makeStaticWall` direto (sem `BoundaryWalls`), enquanto 4 e 6 usam o wrapper. Pode confundir leitor. Mitigação: KDoc de `BoundaryWalls` explica que ele só faz sentido para paredes que devem acompanhar `tree.size`; demo 5 tem paredes locais a um wrapper rotativo, daí o tratamento diferente.
- **Polling roda mesmo quando janela não muda** → comparação `Vec2 == Vec2` por frame é O(1) e barato. Mitigação: nenhuma; custo é trivial frente ao trabalho do `PhysicsSystem`.
