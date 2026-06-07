## Context

`:games:demos` hoje é um `DemoSwitcherRoot : Node` que mantém um `Map<Slot, () -> Node>` de 10 fábricas, troca a cena ativa por `addChild`/`removeChild` ao pressionar `1`–`0`, e desenha um `HudOverlay` (texto cru via `renderer.drawText`) com o nome da demo + dica de teclas. Cada demo é um sentinela de invariante (composição de transform, mutação durante traversal, solver físico, UI, texturas). O módulo expõe dois entrypoints (`Main` Skiko, `MainLwjgl` LWJGL) sobre a mesma raiz, ancorando o invariante #4 (cross-backend).

Problemas: cobertura sobreposta (3 demos de física quase-irmãs; 3 demos de textura triviais isoladas), código duplicado (`hue()` em 3 arquivos, FPS-tracking + `drawText` em 3), e informação redundante com o debug shipped (FPS no canto vs. `ProfilerWidget` em `F1`). As specs `demos-sample` e `solar-system-demo` fixam o catálogo atual, a navegação por teclas e a convenção "sem `Camera2D`".

## Goals / Non-Goals

**Goals:**

- Reduzir de 10 slots para **5 demos densas**, preservando a cobertura de invariantes e **ganhando** cobertura de `Camera2D`. A escala-composição da antiga demo Scale (ancestor scale → tamanho renderizado do filho) é exercida pelo **zoom da câmera**, que escala a hierarquia aninhada de 4 níveis em uníssono pela mesma `Renderer.pushTransform` — não precisa de um corpo de pulso dedicado.
- Substituir a navegação por teclas + HUD de texto cru por um **menu de UI real** (botões) com botão "← Menu" em cada demo — fazendo do próprio launcher a vitrine viva de `ui-foundation`.
- Eliminar redundância: `hue()` compartilhada; FPS por demo removido (profiler assume); título/descrição via `Label`/`CanvasLayer`; métricas via `tree.debug`.
- Manter os dois entrypoints e a sentinela cross-backend (#4) intactos.

**Non-Goals:**

- Alterar `:engine`/`:engine-skiko`/`:engine-lwjgl` (nenhuma mudança de engine; só consumo de API já existente).
- Adicionar novos widgets de UI ou de debug.
- Tocar em outros jogos (`:games:pong`, `:games:platformer`, etc.).
- Persistência/serialização de cena de demo (continuam code-only).

## Decisions

### D1 — Catálogo de 5 demos por fusão dirigida a invariante

| Demo nova | Funde | Invariantes preservados | Ganho |
| --- | --- | --- | --- |
| **Transforms** | 1 | composição de transform aninhada (position + rotation); o **zoom da câmera** exercita ancestor-scale → tamanho renderizado do filho (escala-composição, ex-demo Scale) | **+ `Camera2D` zoom/pan** |
| **Spawn & Collide** | 3 + 4 | mutação durante traversal; `Area2D` trigger; `RigidBody2D` solver + cache | — |
| **Rotating Frame** | 5 (mantida) | `CharacterBody2D.moveAndCollide` em frame rotativo | — |
| **Tumbling Swarm** | 6 | solver `RigidBody2D` completo (linear+angular+Coulomb) | — |
| **Sprites & Tiles** | 8 + 9 + 0 | `Sprite2D` + `AnimatedSprite2D` + `TileMap`; `CharacterBody2D` sobre `StaticBody2D` | — |

Mantemos **5 e 6 separadas** de propósito: o CLAUDE.md distingue `CharacterBody2D` (controle direto, sweep) de `RigidBody2D` (integração + solver). Uma única "caixa de coisas quicando" misturaria os dois modelos e turvaria a lição. Alternativa considerada (fundir 5+6): rejeitada por esse motivo pedagógico.

### D2 — O menu é a demo de UI; navegação 100% por `CanvasLayer`

`DemoSwitcherRoot` deixa de desenhar `HudOverlay` por `drawText` e de pollar teclas `1`–`0`. Em vez disso:

- A raiz mostra um **menu** (`CanvasLayer`) com um `Button` por demo (5 botões) + `Panel`/`Label` de título. Selecionar um botão (`pressed`) troca a cena ativa.
- Cada demo carrega um **overlay** `CanvasLayer` com `Label` de título, `Label` de descrição e um `Button` "← Menu" que volta ao menu.

Isso absorve a antiga demo 7: `Button` (estados hover/press/disabled, `pressed` signal, hit-test, click-consumption), `Panel`, `Label`, anchors e z-order ficam exercitados **continuamente, em toda tela**. Alternativa (manter teclas `1`–`0` como atalho): descartada — o usuário pediu navegação por menu + botão voltar; manter teclas duplicaria caminhos e enfraqueceria o papel do menu como sentinela de UI.

A troca de cena continua via `addChild`/`removeChild` (mesmo stress de lifecycle de hoje), guardada contra re-attach.

### D3 — `Camera2D` na demo Transforms, escopo local à cena

A demo Transforms instala uma `Camera2D` `current = true` cujo `bounds: Rect` define o retângulo de mundo enquadrado. **Zoom = escalar `bounds`** (bounds menor → mais perto); **pan = transladar `bounds.origin`**. Input: scroll do mouse ajusta zoom em torno do cursor; teclas (e.g. setas / `+`/`-`) fazem pan/zoom. A câmera é **local à cena** — ao voltar ao menu e entrar noutra demo, ela é desmontada com a cena, então as demais demos seguem em pixels de surface crus. Isso inverte a convenção "sem `Camera2D`" apenas onde faz sentido. O HUD/overlay de UI vive em `CanvasLayer` (ignora a view transform), então título/descrição/back-button **não** sofrem zoom — exatamente o que valida o invariante #6.

### D4 — FPS sai; métricas viram debug

Remove o `instantFps`/`drawText` por demo. `F1` (ProfilerWidget) é a fonte de FPS. Contagens (nº de corpos), contatos e vetores de velocidade, quando úteis, passam por `tree.debug` (`VelocityGizmoWidget`, `ContactGizmoWidget`, profiler) — não por texto no canto. Título/descrição da demo viram `Label`.

### D5 — Dedup de helpers compartilhados

`hue(h: Float): Color` (hoje copiada em `CollisionStressDemo`, `RotatingBoxDemo`, `TumblingSwarmDemo`) vira um único helper top-level/objeto compartilhado no módulo. `BoundaryWalls`/`makeStaticWall` permanecem como estão (já compartilhados). O idiom de `lastSize` repetido fica como está onde ainda for necessário (não é redundância de código duplicado, e sim padrão local de cada cena).

### D6 — Entrypoints e sentinela cross-backend preservados

`Main` (Skiko) e `MainLwjgl` (LWJGL) seguem construindo a **mesma** raiz com a **mesma** `GameConfig`, registrando `AxesWidget`. A demo "Sprites & Tiles" é o novo carregador cross-backend (texturas/sprite/tilemap em ambos). Nada em `build.gradle.kts` muda exceto o necessário (nenhuma nova dependência).

## Risks / Trade-offs

- **[Quebra de testes/specs existentes]** As specs `demos-sample` e `solar-system-demo` têm cenários amarrados aos nomes/slots/teclas atuais e à convenção "sem Camera2D". → Mitigação: deltas `MODIFIED`/`REMOVED`/`RENAMED` explícitos; atualizar/recriar os testes de unidade que verificam topologia/slots para o novo catálogo.
- **[Perda do nº fixo de nós do solar system]** A demo Transforms adiciona um `Camera2D`, mudando a contagem de nós que `solar-system-demo` fixa. → Mitigação: a spec delta atualiza as contagens/escopo; a câmera é um nó nomeado verificável. (Um corpo de pulso de escala foi cogitado para fundir a demo Scale, mas descartado por poluir a cena — o zoom da câmera já exercita a escala-composição ancestor→filho.)
- **[Menu como ponto único de navegação]** Se o `Button`/hit-test regredir, todas as demos ficam inacessíveis. → Mitigação: o menu usa o mesmo `Button` shipped já coberto por `ui-foundation`; teste de fumaça garante que o menu monta e que "← Menu" volta.
- **[Camera2D só numa demo]** Cobertura de câmera fica restrita a uma cena. → Aceitável: Pong já cobre câmera em gameplay; aqui o objetivo é didático/visual, não exaustivo.
- **[Esforço de reescrita]** Remoção de 6 arquivos + reescrita de 2 + 2 novos. → Mitigação: fusões são recombinação de código existente (helpers já prontos), não lógica nova de física/render.

## Open Questions

- Teclas de pan/zoom exatas na demo Transforms (setas + `+`/`-` vs. arrastar com o mouse) — detalhe de implementação, decidir no apply.
- Se o menu deve listar as demos em grid ou coluna — cosmético, decidir no apply conforme `Button`/anchors disponíveis.
