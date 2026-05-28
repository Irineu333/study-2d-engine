## Context

`Node` expõe `name`, `children: List<Node>`, `parent`, `tree` e `isLive` —
caminhar a árvore viva é um DFS trivial a partir de `tree.root`. `@Inspect`
tem `RUNTIME` retention e `displayName`; o `SceneLoader.extractInspectProperties`
(privado) já enumera as `@Inspect` de um node via `kClass.memberProperties` +
`findAnnotation<Inspect>()` + getter, produzindo `name → value`. `:engine` já
depende de `kotlin-reflect`. A `ui-foundation` traz `Label`/`Panel`/`Button`,
mas o inspector tem necessidades de layout dinâmico (lista de altura
variável, seleção) que não casam bem com instanciar widgets por node.

`@Transient` cobre estado runtime (`linearVelocity`, etc.): fora do contrato
`@Inspect`, logo invisível a uma reflexão baseada em `@Inspect`.

## Goals / Non-Goals

**Goals:**

- Mostrar a hierarquia viva (nome + tipo, indentada) a partir de `tree.root`.
- Selecionar um node por clique e listar suas `@Inspect` + transform world.
- Refletir só o node selecionado por frame (custo controlado).
- Reusar a enumeração `@Inspect` da serialização (uma fonte de verdade).
- Built-in togglável; overhead zero quando off.

**Non-Goals:**

- Edição de propriedades (read-only no MVP).
- Watch de `@Transient`/runtime (`velocity`) — candidato a `@DebugWatch`
  futuro; velocidade já tem o `VelocityGizmoWidget`.
- Expand/collapse, scroll, busca/filtro da lista.
- IDs estáveis de node / seleção persistente entre re-attach.

## Decisions

### D1 — Widget self-contained, sem Button por node

`SceneInspectorWidget : ScreenDebugWidget`. Desenha a lista e o painel
diretamente via `Renderer.drawText`/`drawRect` em `drawDebug`, e trata o
clique em `onProcess` lendo `tree.input` (posição + clique) e mapeando para
a linha sob o cursor com o mesmo layout que desenha. **Não** instancia
`Button`/`Label` por node.

**Por quê:** uma árvore com dezenas de nodes geraria dezenas de `Button`s
recriados a cada mudança da árvore — churn de scene graph caro e frágil. Um
widget que desenha texto e faz seu próprio hit-test de linha é mais simples,
sem mutação de árvore, e mantém o inspector como um único node.

**Trade-off:** reimplementa um hit-test de linha simples em vez de reusar o
sistema de `Button`. É trivial (retângulos de linha de altura fixa) e isola
o inspector da evolução da UI.

### D2 — Seleção por identidade de instância

A seleção guarda a referência `Node` selecionada (identidade). A cada frame,
se o node selecionado não está mais em `isLive`/na árvore, a seleção é
limpa. Sem IDs estáveis (a engine não tem); identidade de instância é
suficiente para uma sessão de debug.

### D3 — Reflexão só do node selecionado, layout O(N) por frame

O walk da árvore para montar as linhas é O(N) por frame enquanto habilitado
— aceitável para debug. A **reflexão** (cara) roda só para o **node
selecionado**, um por frame. Sem cache: valores sempre frescos. Documentado
que habilitar o inspector tem custo proporcional ao tamanho da árvore.

### D4 — Helper público de enumeração `@Inspect`

Adiciona-se um helper público em `com.neoutils.engine.serialization`, ex.:

```kotlin
fun inspectProperties(node: Node): List<InspectEntry>  // (displayName, value)
```

reusando a mesma reflexão de `SceneLoader.extractInspectProperties`. O
inspector o consome. O `SceneLoader` **permanece intacto** no MVP (não
refatorado para delegar) para não perturbar a serialização testada;
converger as duas implementações fica como limpeza futura anotada.

**Alternativa rejeitada — refatorar `SceneLoader` para delegar agora:**
risco desnecessário sobre código de serialização testado, sem ganho para
esta change. Duplicação pequena e localizada é aceitável.

### D5 — Conteúdo do painel do node selecionado

Para o node selecionado: tipo (`::class.simpleName`), `name`, e — se
`Node2D` — o transform world (`world().position/rotation/scale`, sempre
significativo e computado, não via `@Inspect`), seguido das `@Inspect`
(`displayName` ou nome da property = valor `toString()`). Valores
complexos (Vec2, Color) usam seu `toString()` de data class.

### D6 — Layout e truncamento honesto

Lista indentada por profundidade, linhas de altura fixa, ancorada num canto
(ex.: esquerda), clipada à altura da surface. Se a árvore não cabe, desenha
"+N more" no fim — truncamento **explícito** (convenção do projeto: nada de
corte silencioso). Painel do selecionado num bloco separado (ex.: base ou
lado direito).

## Risks / Trade-offs

- **[Walk O(N) + reflexão por frame]** → custo cresce com a árvore.
  Mitigação: só quando habilitado; reflexão limitada a um node; documentado.
  Cache de layout é otimização futura se o profiler apontar.
- **[Duplicação da enumeração `@Inspect`]** → helper novo vs o privado do
  `SceneLoader`. Mitigação: lógica pequena; convergência anotada como
  cleanup futuro; serialização não é tocada (menor risco).
- **[`@Transient` invisível frustra "ver a velocidade"]** → o inspector não
  mostra runtime state. Mitigação: `VelocityGizmoWidget` cobre velocidade;
  `@DebugWatch` é o caminho futuro para runtime fields, anotado como
  não-objetivo.
- **[Hit-test próprio diverge do sistema de `Button`/HiDPI]** → o inspector
  faz seu hit-test em coordenadas de tela. Mitigação: usa as mesmas
  coordenadas lógicas que o resto da UI screen-space; alinha com a
  convenção de `tree.size`.
- **[Seleção perdida em re-attach]** → identidade de instância, sem IDs.
  Mitigação: limpa a seleção ao desanexar; aceitável para debug de sessão.
