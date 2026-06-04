## Why

A UI in-game da engine parou num MVP de dois widgets (`Panel`, `Button`) sem
nenhum posicionamento relativo, e a dor já é concreta no código dos jogos:
`UiPlaygroundDemo.onProcess` recomputa as posições de HUD/menu **todo frame**
contra `tree.size`; `snake/gameover.py` centraliza um `Label` com um hack de
`_draw` (espera o frame 2, mede o texto, reposiciona, levanta uma flag); e não
existe sequer uma flag `visible` — o snake liga/desliga o game-over via
`color.a = 0`. As três gambiarras são sintoma da mesma falta: não há uma base de
controle que carregue **anchors** e visibilidade. Este é o gatilho que o ROADMAP
já previu para promover `ui-controls-base` ("promovido quando `ui-anchors` /
`ui-focus` chegar").

## What Changes

- Introduz `Control` como **base abstrata Godot-style** dos widgets de UI
  in-game. Modelo decidido na explore: **`Control : Node2D`** (Opção 1) — Control
  herda o `transform` existente; anchors viram um **layout pass** que resolve
  `position`/`size` de cada Control contra o rect do parent quando este muda
  (resize de surface ou do parent), alimentando o render-stack atual
  (`pushTransform`/`world()`/`screenRect()`/`localBounds()`) **intacto**. Sem
  novo render path; **não toca o invariante #6**.
- Control declara o conjunto completo de campos de uma vez (base nasce inteira,
  sem refator depois), mas o **comportamento é faseado**:
  - **Ativo agora**: `anchor_left/top/right/bottom` + `offset_left/top/right/bottom`
    + presets Godot 4-style (anchor layout pass); `visible` (pula render **e**
    hit-test); `mouse_filter` (`STOP` / `PASS` / `IGNORE`) no hit-test de UI.
  - **Declarado mas inerte** (campo existe, comportamento ligado por change
    futura): `focus_mode` / `focus_neighbor_*` → acesos por `ui-focus`;
    `size_flags_horizontal` / `size_flags_vertical` → acesos por `ui-layout`.
- **BREAKING (interno ao módulo)**: `Panel`, `Button` e `ColorRect` passam a
  estender `Control` e **sobem seu `size` para a base**. `Circle2D` permanece
  `Node2D` puro (não é Control). `Label` é caso especial de **min-size** derivado
  do texto via `TextMeasurer`, ganhando `visible`/anchors mas sem `size` setável.
- Expõe os bindings de `anchors`/`offsets`/presets/`visible`/`mouse_filter` nos
  dois scripting hosts (Python e Lua).
- Absorve a linha planejada `ui-anchors` do ROADMAP; preserva `ui-focus` e
  `ui-layout` como changes futuras que apenas **acendem** campos que `Control` já
  expõe.

Fora de escopo (cada um na sua change): comportamento de focus (Tab/keyboard
nav), containers (`HBox`/`VBox`/`Grid`), theme/`StyleBox`, `TextEdit`.

## Capabilities

### New Capabilities
- `ui-controls-base`: a classe base `Control : Node2D`; modelo de anchors/offsets
  Godot 4-style e presets; o anchor layout pass (quando dispara, como resolve
  rect a partir do parent rect, ordem vs. render); a flag `visible` (semântica de
  render + hit-test, propagação a descendentes); `mouse_filter`
  (`STOP`/`PASS`/`IGNORE`) no hit-test; e os campos `focus_mode`/`size_flags`
  **declarados-inertes** com contrato explícito de "ligado por change futura".

### Modified Capabilities
- `ui-foundation`: `Panel`/`Button`/`ColorRect` passam a estender `Control` e o
  `size` sobe para a base; o UI pass de `CanvasLayer` pula subárvore de Control
  invisível; o hit-test de `Button` respeita `visible` e `mouse_filter`; `Label`
  reporta min-size e ganha `visible`/anchors.
- `python-scripting`: bindings de `anchors`/`offsets`/presets/`visible`/
  `mouse_filter` expostos ao host Python; stubs `.pyi` atualizados.
- `lua-scripting`: mesmos bindings expostos ao host Lua; stubs LuaCATS
  atualizados.

## Impact

- **`:engine`**: nova classe `Control` em `com.neoutils.engine.scene`; mudança de
  superclasse de `Panel`/`Button`/`ColorRect`; novo passo de layout no
  `SceneTree`/render walk; `NodeRegistry` (Control é abstrato — não registrável
  direto, mas as folhas continuam registradas); disciplina `@Inspect`/`@Transient`
  nos novos campos.
- **`:engine-bundle-python` / `:engine-bundle-lua`**: novos bindings e stubs.
- **`:games:demos`** (`UiPlaygroundDemo`) e **`:games:snake`** (`gameover.py`,
  `score.py`): podem migrar do relayout manual para anchors como prova viva
  (validação da change), embora a migração dos jogos seja opcional ao núcleo.
- **`CLAUDE.md` / invariante #6**: atualizar para registrar `Control` como base de
  `Panel`/`Button`/`Label` e a existência do anchor layout pass.
- **`ROADMAP.md`**: remove `ui-anchors` (absorvida); mantém `ui-focus`/`ui-layout`
  redefinidas como "acendem campos já declarados em Control".
