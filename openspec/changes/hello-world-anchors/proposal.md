## Why

O sample `:games:hello-world` é o primeiro contato didático com a engine, mas hoje ensina um anti-padrão: centraliza o texto com um `_draw` hack (subclasse `CenteredLabel` que sobrescreve `onDraw`, mede o texto via `renderer.measureText` e calcula a posição central contra `tree.size`). Esse é exatamente o hack que o KDoc do `Label` declara **obsoleto** desde a change `ui-controls-base` — *"a full-rect Label centers its text on the surface (the slack-centering rule in `Control.resolveLayout`), which is how a game-over banner centers without measuring text in a `_draw` hack"*. O exemplo canônico do par `CanvasLayer` + `Label` está ensinando o oposto do caminho idiomático da engine.

## What Changes

- **BREAKING (sample-level):** Remove a classe `CenteredLabel` e o arquivo `CenteredLabel.kt`. O sample passa a usar `Label` diretamente.
- `Main.kt` monta a árvore com `Label().apply { text/fontSize/color; applyPreset(LayoutPreset.FULL_RECT) }` como único filho do `CanvasLayer` root. O anchor layout pass + slack-centering centralizam o texto — sem `onDraw`, sem `measureText`, sem `tree.size`.
- Centralização passa a viver nativamente em **design-space** (o `Label` é resolvido contra `Rect(ZERO, designSize)` na fronteira do `CanvasLayer` com `followStretch = true`), corrigindo a mistura de espaços atual (centralizava em `tree.size` cru enquanto desenhava em design-space — só não quebrava pelo caso degenerado sem-câmera) e fazendo o texto escalar junto no resize.
- Reescreve a spec `hello-world-sample`: o requisito que hoje **exige** o `_draw` hack (`onDraw` + `measureText` + `tree.size`) é substituído por um requisito que exige centralização via anchors/preset, sem subclasse de `Label`.

## Capabilities

### New Capabilities
<!-- nenhuma -->

### Modified Capabilities
- `hello-world-sample`: o requisito de centralização muda de "subclasse `Label`, sobrescreve `onDraw`, mede texto e centraliza contra `tree.size`" para "usa `Label` direto com `applyPreset(FULL_RECT)`; a centralização é resolvida pelo anchor layout pass da engine em design-space". Some o requisito que proíbe magic numbers/exige `measureText` no sample (a medição agora é interna ao `Label`). O requisito de árvore (`CanvasLayer` root + único filho) permanece, mas o filho deixa de ser `CenteredLabel` e passa a ser `Label`.

## Impact

- **Código:** `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/Main.kt` (reescrito), `CenteredLabel.kt` (removido).
- **Specs:** `openspec/specs/hello-world-sample/spec.md` (delta).
- **Docs:** descrição em `CLAUDE.md` (linha do `:games:hello-world`) permanece válida ("único `Label` centralizado"), mas a menção implícita ao `CenteredLabel` deixa de existir — revisar.
- **Engine:** nenhuma mudança em `:engine`. Usa apenas API pública já existente (`Label`, `Control.applyPreset`, `LayoutPreset.FULL_RECT`).
- **Sem impacto** em outros jogos, backends ou na física.
