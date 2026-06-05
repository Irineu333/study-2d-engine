## Context

`:games:hello-world` foi escrito antes (ou em paralelo a) a change `ui-controls-base`, que transformou `Label` em um `Control` com anchors/offsets Godot-style e introduziu o **anchor layout pass** (`SceneTree.runAnchorLayout` → `Control.resolveLayout`). Hoje o sample centraliza com um `_draw` hack: a subclasse `CenteredLabel` sobrescreve `onDraw`, mede o texto via `renderer.measureText` e posiciona contra `tree.size`. A própria engine documenta esse padrão como obsoleto (KDoc de `Label`).

Estado atual relevante já verificado no código:
- `Label.layoutSize` reporta o tamanho medido do texto (min-size leaf).
- `Control.resolveLayout` (`Control.kt:215-220`) centraliza o **slack positivo**: um `Label` menor que seu anchor rect fica centrado.
- `SceneTree.layoutWalk` (`SceneTree.kt:419-420`) resolve filhos diretos de um `CanvasLayer` com `followStretch = true` contra `Rect(ZERO, designSize)`.

## Goals / Non-Goals

**Goals:**
- Tornar o sample o exemplo canônico do caminho idiomático: `Label` + preset, sem subclasse nem `onDraw`.
- Centralizar em design-space (correto sob `CanvasLayer` com `followStretch`), eliminando a mistura de espaços atual.
- Reescrever a spec para deixar de exigir o `_draw` hack.

**Non-Goals:**
- Nenhuma mudança em `:engine` (usa só API pública existente).
- Não alterar o invariante #6 nem o sistema de anchors.
- Não introduzir bundle/scripting/`Camera2D` no sample.

## Decisions

### `FULL_RECT` em vez de `CENTER`

Para centralizar um `Label` (min-size) via anchors, há dois candidatos:

- **`FULL_RECT`** (anchors `0,0,1,1`, offsets `0`): o anchor rect é a surface inteira; o `Label` é menor, então `resolveLayout` centraliza o slack positivo em ambos os eixos. ✅ centraliza.
- **`CENTER`** (anchors `0.5,0.5,0.5,0.5`, offsets `0`): o anchor rect tem tamanho `(0,0)`, slack zero; o texto ancora no ponto central como **top-left** e vaza pra direita/baixo. ❌ não centraliza sem offsets de meio-texto — que exigiriam medir o texto, reintroduzindo o hack.

Escolhido **`FULL_RECT`**. É exatamente o caso que o KDoc do `Label` cita ("a full-rect Label centers its text on the surface").

### Manter o `CanvasLayer` root explícito

O invariante #6 e a spec querem o sample como exemplo canônico do par `CanvasLayer` + `Label`. Mantemos o `CanvasLayer` como root, com `followStretch` no default (`true`), de modo que o texto viva em design-space e escale no resize.

### `Label` direto, sem subclasse

`CenteredLabel` existia só para hospedar o `onDraw` customizado. Com a centralização declarativa via preset, não há comportamento a adicionar — `Label` é instanciado direto e o arquivo `CenteredLabel.kt` é removido.

## Risks / Trade-offs

- **[Mudança visual sutil no resize]** Antes o texto centralizava em pixels de tela crus; agora centraliza em design-space e escala (fontSize acompanha o stretch). No caso sem-câmera do sample, `designSize` rastreia a surface e o stretch é identidade, então em 800×600 o resultado inicial é idêntico — o ganho aparece ao redimensionar. → É o comportamento desejado pelo invariante #6; documentado na spec.
- **[Spec delta amplo]** Três requisitos da spec mencionam `CenteredLabel`/`measureText`. → Tratado com MODIFIED nos requisitos de módulo/árvore e REMOVED+ADDED no requisito de centralização, preservando os cenários ainda válidos.
