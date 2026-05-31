## Context

A `node-local-bounds` (essencialmente pronta) entregou os primitivos de
extensão espacial: `Node2D.localBounds(): Rect?` (frame local, orientável),
`worldBounds(): Rect?` (AABB world-space), `Transform.apply(p)` e
`Rect.corners()`/`Rect.contains(p)`. A `Camera2D`/`SceneTree` já expõem
`screenToWorld(input.pointerPosition)`. O `Input` expõe `pointerPosition`,
`wasMouseClickedRaw`, `wasMouseClicked` e `mouseClickConsumed`; o
`SceneTree.hitTestUI(input)` já roda no `GameLoop.tick` **antes** de
`tree.process` e é dono do `mouseClickConsumed` (reset no início, set quando
um `Button` absorve o clique). A infra de debug roteia `WorldDebugWidget`
(`Node2D`, world pass com a view da câmera) e `ScreenDebugWidget` (`Node` sob
`ScreenDebugCanvas`, UI pass em pixels) via `DebugRegistry`, com rows no
`DebugHud` e custo zero quando `enabled = false`.

O que **não** existe: inverso de `Transform.apply` (só `apply`/`compose`); um
passo de hit-test para debug; enumeração pública de `@Inspect`
(`SceneLoader.extractInspectProperties` é privado).

A tentativa anterior (`debug-scene-inspector`) foi uma lista de texto da
árvore com clique em linha — descartada por "limitada demais"; o pivô
explícito (commit `38521c7`) é world-space picking.

## Goals / Non-Goals

**Goals:**

- Clicar num objeto desenhado e selecionar o `Node2D` correspondente, com
  precisão sob rotação (OBB justo, não AABB folgado).
- Resolver empilhamento: front-most vence; clique repetido cicla pelos nodes
  embaixo.
- Destacar o selecionado (OBB) e listar tipo/`name`/transform world +
  `@Inspect` (read-only).
- Mostrar onde o selecionado vive na árvore (breadcrumb root→selecionado)
  sem obstruir a interação com a cena.
- Roubar o clique do gameplay quando o pick está ativo.
- Built-ins togglávies, overhead zero quando off; reusar a infra de debug.

**Non-Goals:**

- Edição de propriedades (read-only no MVP).
- Watch de `@Transient`/runtime (`linearVelocity` etc.) — fora do contrato
  `@Inspect`; `VelocityGizmoWidget` cobre velocidade; `@DebugWatch` é futuro.
- Lista de árvore **clicável** completa (só breadcrumb no MVP; lista
  interativa fica para depois, e exige resolver obstrução de clique).
- Picking de UI/`CanvasLayer` (screen-space) — anotado como evolução.
- Marquee/multi-seleção, zoom-to-fit, IDs estáveis entre re-attach.

## Decisions

### Decision 1: Hit-test OBB justo via `applyInverse`, com `worldBounds` de broad-phase

O pick leva o clique-world ao frame **local** do node e testa contra o
`localBounds()` retangular:

```
clickWorld = tree.screenToWorld(input.pointerPosition)
candidatos = DFS(root) de Node2D com localBounds != null, pulando CanvasLayer
  filtra por worldBounds().contains(clickWorld)         // broad-phase AABP barato
  confirma por localBounds().contains(world().applyInverse(clickWorld))  // OBB justo
```

`applyInverse` é o inverso exato de `apply` (`q = p - position; q =
rotate(q, -rotation); q = (q.x/scale.x, q.y/scale.y)`). Isso dá precisão sob
rotação — requisito vindo de `RotatingBoxDemo`/`TumblingSwarmDemo`.

**Por quê:** AABB (`worldBounds`) acerta o canto vazio de um node
rotacionado. Para a engine ser honesta sobre "esse pixel é esse node", o
teste tem que ser orientado. O `worldBounds` ainda serve: é o broad-phase
que descarta a maioria antes do `applyInverse` (mais caro).

**Alternativas consideradas:**
- **Só AABB (`worldBounds.contains`)**: mais simples, sem `applyInverse`, mas
  impreciso sob rotação — falha o caso que motiva a precisão. Rejeitado.
- **`Transform.inverse(): Transform` completo**: inverter um TRS com scale
  não-uniforme não devolve um TRS limpo; `applyInverse` (ponto→ponto) é
  exato, mínimo e simétrico ao `apply` existente. Preferido.

### Decision 2: Hit-test de pick no core, em `SceneTree.hitTestPick`, após `hitTestUI`

O hit-test roda num passo novo `SceneTree.hitTestPick(input)`, chamado pelo
`GameLoop.tick` **logo após `hitTestUI` e antes de `tree.process`**, gated em
`scenePicker.enabled`. Quando ativo e há clique, ele resolve a seleção e seta
`input.mouseClickConsumed = true`.

**Por quê:** "roubar o clique do gameplay" (decisão do usuário) só é correto
se o pick pré-emptar o `process`. O `hitTestUI` já é exatamente essa fase
(pré-`process`, dona do `mouseClickConsumed`); pendurar o pick ao lado é
coerente e análogo. É engine-internal, então respeita o invariante #4 (o
`GameHost` não toca debug por frame). Custo zero quando off (um `if`).

**Alternativas consideradas:**
- **`onProcess` de um `Node` de debug**: não tocaria o core, mas a ordem de
  consumo vs. os scripts de gameplay depende da posição em DFS — frágil e
  semanticamente errado (gameplay processado antes já viu o clique).
  Rejeitado.

### Decision 3: Front-most + ciclagem por ponto de clique

Os candidatos confirmados são ordenados por **draw-order** (ordem de
visitação DFS = ordem de pintura; o último desenhado é o da frente). O pick
mantém `(lastPickPoint, cycleIndex)`: clique num ponto novo (além de um
epsilon) seleciona o front-most e zera o índice; clique repetido
aproximadamente no mesmo ponto avança o índice (mod nº de candidatos),
revelando os nodes empilhados embaixo.

**Por quê:** corresponde à intuição (Godot faz igual) e resolve overlap sem
UI extra. Epsilon evita que micro-tremores do mouse reiniciem o ciclo.

**Alternativas consideradas:**
- **Sempre front-most, sem ciclo**: nodes cobertos ficam inacessíveis ao
  clique. Rejeitado para o MVP por custo trivial do ciclo.

### Decision 4: Dois widgets — `ScenePickerWidget` (screen) + `SelectionGizmoWidget` (world)

A seleção é estado único, mas o desenho mora em dois espaços: o OBB
highlight precisa da view da câmera (world), o painel/breadcrumb são pixels
de tela. Logo:
- `SelectionGizmoWidget : WorldDebugWidget` desenha o OBB (cantos de
  `localBounds` via `world().apply`) no world pass.
- `ScenePickerWidget : ScreenDebugWidget` é o "dono" — guarda a seleção e o
  estado de ciclo, é lido pelo `hitTestPick`, e desenha breadcrumb + painel.

A fonte de verdade da seleção fica no `ScenePickerWidget`; o gizmo lê dela
(via `tree.debug.scenePicker.selected`).

**Por quê:** espelha a separação world/screen que a infra de debug já impõe
(um widget não pode desenhar nos dois espaços). Mantém cada widget togglável
de forma independente (ver o OBB sem o painel, p.ex.).

**Alternativas consideradas:**
- **Um único widget**: impossível desenhar OBB world e painel screen do
  mesmo `onDraw` sob a arquitetura de passes. Rejeitado.

### Decision 5: Seleção por identidade de instância; breadcrumb (não lista clicável)

A seleção referencia a instância `Node` (não há IDs estáveis na engine). A
cada frame, se o selecionado não está mais `isLive`/na árvore, limpa. O painel
mostra o **caminho** (breadcrumb) root→selecionado como texto não-interativo,
em vez de uma lista clicável de toda a árvore.

**Por quê:** identidade basta para uma sessão de debug. O breadcrumb dá o
contexto hierárquico (atende ao "híbrido") **sem** uma lista que cobriria a
cena e roubaria área de clique do picking — preocupação explícita do usuário.
Lista interativa completa fica para change futura.

**Alternativas consideradas:**
- **Lista de árvore clicável (a abordagem descartada)**: obstrui a cena e
  reintroduz o problema que matou a `debug-scene-inspector`. Rejeitado para o
  MVP.

### Decision 6: Helper público `inspectProperties`, `SceneLoader` intacto

Adiciona-se `data class InspectEntry(displayName, value)` e
`fun inspectProperties(node: Node): List<InspectEntry>` em
`com.neoutils.engine.serialization`, reusando o padrão de reflexão
(`memberProperties` + `findAnnotation<Inspect>()` + getter; `displayName` =
annotation se não-vazia, senão nome da property). O `SceneLoader` **não** é
refatorado para delegar.

**Por quê:** o painel precisa enumerar `@Inspect` com valores correntes; é a
mesma lógica já testada no `SceneLoader`. Expor um helper evita duplicar a
intenção, mas mexer no `SceneLoader` (serialização testada) é risco sem
ganho para esta change. Convergência futura anotada.

## Risks / Trade-offs

- **[Walk O(N) + OBB por candidato no clique]** → custo no instante do
  clique cresce com a árvore. Mitigação: só quando `enabled`; broad-phase
  `worldBounds` antes do `applyInverse`; é um evento de clique, não por frame.
- **[`localBounds == null` = invisível ao pick]** → pivôs de transform puro e
  `Node` cru não são clicáveis. Mitigação: é inerente ao picking geométrico;
  o breadcrumb expõe ancestrais não-clicáveis; lista completa (futuro) cobre
  o resto. Documentado.
- **[Roubar o clique surpreende quem testa gameplay]** → com o pick on, o
  jogo não recebe o `Left`. Mitigação: gated em `enabled` (off por default) e
  visível como row no HUD; `wasMouseClickedRaw` continua disponível.
- **[Reflexão expõe `toString()` de tipos complexos]** → `Vec2`/`Color` saem
  via `toString()` de data class. Aceitável (legível); formatação fina é
  cosmético futuro.
- **[Ciclagem com epsilon]** → epsilon mal calibrado reinicia/gruda o ciclo.
  Mitigação: epsilon pequeno em unidades de tela; coberto por teste.
- **[Seleção perdida em re-attach]** → identidade de instância, sem IDs.
  Mitigação: limpa ao desanexar; aceitável para debug de sessão.

## Migration Plan

Sem breaking changes. `Transform.applyInverse` e `SceneTree.hitTestPick` são
superfícies novas e aditivas; `hitTestPick` é no-op quando o picker está
desabilitado (default). Built-ins entram desligados, sem efeito nos jogos
existentes.

## Open Questions

- Nenhuma bloqueante. Tecla modificadora opcional para "pick" sem toggle de
  HUD (ex.: segurar uma tecla) é uma conveniência futura, fora do MVP.
