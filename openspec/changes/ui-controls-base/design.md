## Context

A UI in-game da engine (`ui-foundation`) entregou um MVP de `CanvasLayer` +
`Panel` + `Button` deliberadamente sem posicionamento relativo. A consequência
está visível no código dos jogos: `UiPlaygroundDemo.onProcess` recomputa as
posições de HUD/menu todo frame contra `tree.size`, `snake/gameover.py`
centraliza um `Label` com um hack de `_draw` (frame 2 + medição + flag), e não há
flag `visible` (snake usa `color.a = 0`). Todas as folhas visuais
(`Panel`/`Button`/`Label`/`ColorRect`/`Circle2D`) hoje estendem `Node2D`, cujo
modelo de render é uma **stack de transform** (`pushTransform`/`popTransform`,
`world()`), com hit-test via `screenRect()`/`localBounds()`.

O ROADMAP já previa três changes separadas (`ui-controls-base`, `ui-anchors`,
`ui-focus`/`ui-layout`) e marcava `ui-controls-base` como "promovido quando
`ui-anchors`/`ui-focus` chegar". Esta change cumpre esse gatilho: promove
`Control` e entrega **anchors** como a primeira capability ativa.

Restrições que moldam o design:
- Invariante #1 (scene graph por herança, sem ECS) — `Control` é uma classe base
  por herança, coerente.
- Invariante #6 (UI screen-space via `CanvasLayer`, `Node2D` no UI pass, dois
  passes de render) — **não pode ser reaberto** sem nova change discutindo a
  revisão.
- Convenção `@Inspect`/`@Transient` para toda `var` em classe `@Serializable`.

## Goals / Non-Goals

**Goals:**
- Introduzir `Control` como base abstrata Godot-style dos widgets de UI, sem
  reabrir o invariante #6.
- Entregar anchors/offsets/presets como um **layout pass** que alimenta o
  render-stack atual intacto, matando o relayout-por-frame.
- Entregar `visible` (render + hit-test) e `mouse_filter` (STOP/PASS/IGNORE).
- Nascer com a base **completa** (declarar `focus_mode`/`size_flags`) para não
  refatorar `Control` de novo, mantendo esses campos **inertes** até
  `ui-focus`/`ui-layout`.
- Expor o novo API em Python e Lua.

**Non-Goals:**
- Comportamento de focus (Tab/keyboard nav, `grab_focus`, signals de focus) →
  `ui-focus`.
- Containers (`HBox`/`VBox`/`Grid`/`Margin`) e o efeito de `size_flags` →
  `ui-layout`.
- Theme/`StyleBox`/font system → `ui-theme`.
- `TextEdit`, `Slider` e demais widgets → changes próprias.
- Modelo de eventos enfileirados (`_gui_input`, `event.accept()`) →
  `ui-input-events`. `PASS` aqui significa apenas "observado, não consumido".

## Decisions

### Decisão 1 — `Control : Node2D` (Opção 1), não galho paralelo

No Godot, `Control` e `Node2D` são irmãos sob `CanvasItem`, com modelos de
coordenada distintos (rect+anchor vs. transform). Avaliamos replicar isso (Opção
2: `Control : Node` com render path/hit-test/serialização próprios) e
**rejeitamos**: reabriria o invariante #6, exigiria um segundo render path e
descartaria `world()`/`screenRect()`/`localBounds()` que já estão provados — alto
custo para ~20% de fidelidade que, numa engine **didática**, não paga.

Escolha: `abstract class Control : Node2D`. Control herda o `transform` e
contribui para a render-stack como qualquer `Node2D`. Anchors são um **passo de
layout** que produz `position`/`size`; o render permanece o mesmo. Justificativa
adicional: o Godot real também deixa Control ter rotation/scale (via pivot) — o
que ele separa é o *fluxo de layout* não usar rotation, e aqui o anchor pass só
mexe em `position`/`size`, deixando rotation/scale como override manual.

_Alternativa considerada:_ Opção 2 (galho paralelo fiel). Custo de refator e
quebra do invariante #6 não justificado para o objetivo de aprendizado.

### Decisão 2 — Anchors/offsets são a fonte de verdade; position/size derivados e com write-back

Modelo Godot 4: `anchor_*` (fração `0..1` do parent rect) + `offset_*` (pixels)
definem as bordas do rect; `position`/`size` são computados. Para preservar a API
imperativa atual (`button.position = Vec2(x, y)`) e a compat com scene files que
setam `position`/`size`, escrever `position`/`size` faz **write-back** nos
offsets sob os anchors correntes (igual ao Godot). Default anchors = `0`
(top-left), então um widget que só seta `position`/`size` mantém rect fixo
pinado no canto superior-esquerdo — comportamento idêntico ao de hoje.

_Alternativa:_ `position`/`size` como fonte de verdade e anchors como restrição
aplicada depois. Rejeitada: diverge do mental model do Godot e complica o caso
"estica com o parent".

### Decisão 3 — Anchor layout pass: top-down, parent rect = ancestor Control ou surface

Um passo de layout resolve cada `Control` **antes** de render/hit-test no tick. O
parent rect é (1) o rect resolvido do ancestral `Control` mais próximo, senão (2)
`Rect(ZERO, tree.size)` (surface) — caso do `Control` filho direto de
`CanvasLayer`. O walk é top-down para o parent resolver antes dos filhos. A spec
descreve o **comportamento observável** (após o pass, `position`/`size` refletem
o parent rect atual); dirty-flagging fica como otimização opcional não-normativa.

_Onde encaixar no tick:_ antes do UI render pass (pass 2) e antes/junto do
`hitTestUI`, ambos já existentes em `SceneTree`. Reusa o enumerador de
`CanvasLayer`/DFS que o UI pass já faz.

### Decisão 4 — `size` sobe para Control; Circle2D fica fora; Label é min-size

`Panel`/`Button`/`ColorRect` movem seu `size` para `Control` (uma única fonte do
rect, base do `localBounds`). `Circle2D` **permanece `Node2D` puro** — é uma
forma geométrica centrada (`radius`), não um retângulo ancorável; forçá-lo a
Control distorceria o modelo. `Label` vira `Control` mas **não** tem `size`
setável de rect: seu `localBounds` (min-size) deriva do texto via `TextMeasurer`,
e anchors posicionam esse min-size. Isso resolve o hack de centralização do snake
sem inventar um `size` artificial para texto.

### Decisão 5 — Base completa, comportamento faseado

`Control` declara `focus_mode`/`focus_neighbor_*`/`size_flags_*` **agora** (base
nasce inteira, sem reabrir depois), porém **inertes**: setá-los não muda nada
observável neste change; serializam normalmente. `ui-focus` acende
`focus_mode`/navegação; `ui-layout` acende `size_flags` nos containers. A spec
inclui cenários que **asseguram a inércia** (regressão se algum efeito vazar
antes da hora).

### Decisão 6 — `mouse_filter` com defaults por widget

`STOP`/`PASS`/`IGNORE`. Defaults: `Button` e `Panel` → `STOP` (opaco, igual ao
comportamento atual de painel/botão consumindo clique); `Label`/`ColorRect` →
`IGNORE` (transparentes ao clique, como hoje). `PASS` neste change = "registra
hover/press mas não consome"; semântica plena de propagação fica em
`ui-input-events`. O `hitTestUI` existente passa a pular `Control` invisível e a
respeitar `mouse_filter` ao decidir consumo.

## Risks / Trade-offs

- **[Mudança de superclasse de Panel/Button/ColorRect quebra subclasses de jogo]**
  → É BREAKING interno; os jogos shipped (demos/snake) são atualizados na mesma
  change como prova viva. Nenhuma API pública estável fora do repo depende disso.
- **[`Control : Node2D` não é 100% Godot-fiel — mistura rotation com anchors]** →
  Aceito e documentado: o anchor pass só resolve `position`/`size`; rotation/scale
  ficam como override manual (o Godot real também permite). Risco de confusão
  mitigado por KDoc explicando que anchors operam em local rect pré-rotação.
- **[Write-back de position/size sob anchors não-triviais pode surpreender]** →
  Escopo do write-back é "preservar o rect atual sob os anchors correntes",
  testado por cenários; documentado que mudar anchors depois reinterpreta os
  offsets (igual ao Godot).
- **[Custo do layout pass por frame]** → Engine didática, N de Controls baixo;
  começamos simples (resolver no tick) e deixamos dirty-flag como otimização
  futura não-normativa. Coerente com "clareza acima de performance prematura".
- **[Campos inertes confundindo (parecem funcionar mas não fazem nada)]** →
  Mitigado por KDoc explícito ("reservado para `ui-focus`/`ui-layout`") e por
  cenários de inércia na spec.
- **[`visible` só em Control, não em Node2D]** → Decisão consciente: o escopo é UI.
  Um `visible` geral em `Node2D` seria outra discussão (afeta world pass) e fica
  fora. Documentar no CLAUDE.md que `visible` é, por ora, conceito de Control.

## Migration Plan

1. Criar `abstract class Control : Node2D` com size/anchors/offsets/presets/
   visible/mouse_filter e os campos inertes (focus/size_flags), todos com
   `@Inspect`.
2. Implementar o anchor layout pass em `SceneTree` (resolução top-down, parent
   rect = ancestor Control ∨ surface) e plugá-lo antes do UI render/hit-test.
3. Migrar `Panel`/`Button`/`ColorRect` para `: Control` (remover `size` próprio);
   ajustar `localBounds` para vir de `Control`. `Circle2D` inalterado. `Label`
   passa a `: Control` com min-size do texto.
4. Atualizar `hitTestUI`/render walk para respeitar `visible` e `mouse_filter`.
5. Expor bindings em `:engine-bundle-python` e `:engine-bundle-lua` + stubs
   (`.pyi`, LuaCATS).
6. Migrar `UiPlaygroundDemo` e os scripts de UI do snake para anchors/visible
   como prova viva (remove o relayout-por-frame e o hack de `_draw`).
7. Atualizar `CLAUDE.md` (invariante #6 menciona `Control` como base; existência
   do anchor layout pass) e `ROADMAP.md` (remove `ui-anchors`; redefine
   `ui-focus`/`ui-layout` como "acendem campos já declarados em Control").

Rollback: a change é coesa; reverter = remover `Control` e voltar
`Panel`/`Button`/`ColorRect`/`Label` para `Node2D`. Sem migração de dados (scene
files com anchors/visible default round-trip como antes).

## Open Questions

- **Presets**: subconjunto mínimo (`TOP_LEFT`…`FULL_RECT`, `CENTER`) é suficiente
  para o MVP, ou já incluir os "wide" (`TOP_WIDE`/`HCENTER_WIDE`/…)? Proposta:
  incluir só o subconjunto da spec agora; "wide" entram quando `ui-layout` pedir.
- **`applyPreset` zera offsets?** Decisão atual: não zera (deixa ao caller). Vale
  revisitar se na prática todo uso quer `setAnchorsAndOffsetsPreset` (zera) — pode
  virar um segundo método.
- **Label com anchors `right`/`bottom` diferentes do `left`/`top`**: como um
  min-size text-derived reage a um anchor que pediria stretch? Provável: Label
  ignora stretch (min-size manda) até `ui-layout`. Confirmar no apply.
