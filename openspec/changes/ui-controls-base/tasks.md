## 1. Control base class

- [ ] 1.1 Criar `abstract class Control : Node2D` em `com.neoutils.engine.scene` com `size: Vec2` (`@Inspect`) e `localBounds()` retornando `Rect(Vec2.ZERO, size)`.
- [ ] 1.2 Adicionar anchors `anchorLeft/Top/Right/Bottom` e offsets `offsetLeft/Top/Right/Bottom` (`Float`, `@Inspect`), com defaults top-left (anchors `0`).
- [ ] 1.3 Adicionar `visible: Boolean = true` e `mouseFilter: MouseFilter` (enum `STOP`/`PASS`/`IGNORE`), ambos `@Inspect`.
- [ ] 1.4 Declarar os campos inertes `focusMode` (enum `NONE`/`CLICK`/`ALL`), `focusNeighborLeft/Top/Right/Bottom`, `sizeFlagsHorizontal`, `sizeFlagsVertical` (`@Inspect`), com KDoc marcando "reservado para `ui-focus`/`ui-layout`".
- [ ] 1.5 Implementar o enum `LayoutPreset` (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`, `CENTER_LEFT`, `CENTER_TOP`, `CENTER_RIGHT`, `CENTER_BOTTOM`, `CENTER`, `FULL_RECT`) e `applyPreset(preset)` setando os quatro anchors.
- [ ] 1.6 Implementar write-back: setar `position`/`size` recomputa os offsets para reproduzir o rect sob os anchors correntes.

## 2. Anchor layout pass

- [ ] 2.1 Implementar a resolução de rect de um `Control` a partir do parent rect (fórmula `edge = anchor*parentDim + offset`), produzindo `position`/`size`.
- [ ] 2.2 Definir o parent rect: rect resolvido do ancestral `Control` mais próximo, senão `Rect(ZERO, tree.size)` (surface).
- [ ] 2.3 Implementar o layout pass top-down em `SceneTree`, reusando o enumerador de `CanvasLayer`/DFS, e plugá-lo antes do UI render pass e do `hitTestUI` no tick.
- [ ] 2.4 Garantir reflow em resize de surface/parent sem código de script (resolução roda no tick).

## 3. Migração das folhas UI

- [ ] 3.1 Migrar `Panel` para `: Control`, removendo seu `size` próprio; default `mouseFilter = STOP`; manter `color`/`border` com `@Inspect`.
- [ ] 3.2 Migrar `Button` para `: Control`, removendo seu `size` próprio; default `mouseFilter = STOP`; `pressed` não emite quando `disabled` ou `!visible`.
- [ ] 3.3 Migrar `ColorRect` para `: Control`, removendo seu `size` próprio; default `mouseFilter = IGNORE`.
- [ ] 3.4 Migrar `Label` para `: Control` como caso min-size (bounds do texto via `TextMeasurer`), default `mouseFilter = IGNORE`; sem `size` de rect setável.
- [ ] 3.5 Confirmar que `Circle2D` permanece `Node2D` puro (não vira Control) e mantém seu `localBounds` centrado.
- [ ] 3.6 Ajustar `localBounds()` das folhas migradas para herdar de `Control` (remover overrides individuais de Panel/Button/ColorRect).

## 4. Visibilidade e hit-test

- [ ] 4.1 Fazer o UI render pass pular `Control` com `visible = false` junto com sua subárvore.
- [ ] 4.2 Fazer `hitTestUI` pular `Control` invisível (com subárvore) e respeitar `mouseFilter` (`IGNORE` nunca testado, `STOP` consome, `PASS` registra sem consumir).
- [ ] 4.3 Garantir que `Button` só absorve clique quando `visible`, `!disabled` e `mouseFilter != IGNORE`.

## 5. Scripting bindings

- [ ] 5.1 Expor anchors/offsets/`size`/`visible`/`mouseFilter`/`applyPreset` e os enums `MouseFilter`/`LayoutPreset` ao host Python (namespace pré-bound).
- [ ] 5.2 Atualizar os stubs `.pyi`: `Control` com o novo API e `Panel`/`Button`/`Label` como subclasses.
- [ ] 5.3 Expor o mesmo API ao host Lua sob o global `nengine`.
- [ ] 5.4 Atualizar os stubs LuaCATS: `Control` com o novo API e `Panel`/`Button`/`Label` estendendo-o.

## 6. Provas vivas (jogos) e docs

- [ ] 6.1 Migrar `UiPlaygroundDemo` para anchors/presets, removendo o relayout de `onProcess`.
- [ ] 6.2 Migrar `snake/gameover.py` para `visible` + anchor center, removendo o hack de `_draw`/`_centered` e o `color.a = 0`.
- [ ] 6.3 Atualizar `CLAUDE.md` (invariante #6: `Control` como base de `Panel`/`Button`/`Label`; existência do anchor layout pass).
- [ ] 6.4 Atualizar `ROADMAP.md`: remover `ui-anchors` (absorvida); redefinir `ui-focus`/`ui-layout` como "acendem campos já declarados em Control".

## 7. Testes

- [ ] 7.1 Testes do anchor pass: top-left fixo, stretch com parent, centro, reflow em resize, nesting Control-em-Control.
- [ ] 7.2 Testes de write-back de `position`/`size` sob anchors top-left.
- [ ] 7.3 Testes de `visible`: subárvore não desenhada e não hit-testada.
- [ ] 7.4 Testes de `mouseFilter`: `STOP` consome, `IGNORE` passa, `PASS` registra sem consumir; defaults por widget.
- [ ] 7.5 Testes de inércia dos campos `focusMode`/`sizeFlags` (setar não muda render/layout/hit-test; round-trip de serialização).
- [ ] 7.6 Testes de serialização: anchors/offsets/`visible`/`mouseFilter` round-trip via scene.json; compat de widgets que só setam `position`/`size`.
