## Why

A engine não tem **nenhuma** renderização rasterizada: o `Renderer` SPI só conhece primitivas vetoriais (`drawRect`, `drawCircle`, `drawLine`, `drawText`, `drawPolygon`). Não existe `drawImage`, não existe conceito de textura, não existe asset de imagem em jogo nenhum. Sem isso, a engine não desenha sprites — e portanto não suporta a classe inteira de jogos pixel-art/plataforma que motiva esta cadeia de changes.

Esta change estabelece a **fundação mínima de texturas**: a capacidade de carregar um PNG num handle reutilizável e desenhá-lo (ou um recorte dele) na tela, com filtragem **nearest-neighbor** (pixel-art não pode borrar). Ela segue exatamente o molde de SPI de plataforma já consagrado por `Renderer`/`Input`/`TextMeasurer`/`AudioBackend`, abrindo caminho para animação de sprite (`sprite-animation`), tilemap (`tilemap-visual`) e a demo de plataforma (`game-platformer`) **sem refazer a base**.

É a primeira de uma cadeia de quatro changes. As demais (`sprite-animation`, `tilemap-visual`, `game-platformer`) **dependem** desta: nenhuma consegue desenhar um pixel de sprite sem `Renderer.drawImage` e `tree.textures`.

## What Changes

- **Nova operação `drawImage` no `Renderer` SPI** (`:engine`, Kotlin puro): `fun drawImage(texture: Texture, src: Rect, dst: Rect, flipH: Boolean = false)`. `src` é o recorte em pixels da textura; `dst` é o retângulo de destino em local space (sob a transform stack corrente); `flipH` espelha horizontalmente (vira o personagem) sem usar `scale.x` negativo. A amostragem é **nearest-neighbor** (sem suavização) — requisito de pixel-art.
- **Nova SPI de textura em `:engine`**: `interface TextureBackend { fun load(path: String): Texture; fun dispose() }` e `interface Texture { val width: Int; val height: Int }`. Server-style, espelhando `AudioBackend`. Diferente do `Sound` (handle opaco), `Texture` **expõe dimensões** — a lógica de sprite/tilemap precisa de `width`/`height` para calcular recortes; nenhum tipo de backend (Skia/NanoVG) vaza por ela.
- **Novo campo `SceneTree.textures: TextureBackend?`** — nullable, injetado pelo host no startup, espelhando `textMeasurer`/`audio`. Nodes resolvem texturas via `node.tree.textures?.load(...)`. `null` ⇒ degradação graciosa (headless/teste não desenha sprite, não quebra). `tree.stop()` chama `textures?.dispose()` exatamente uma vez.
- **`load(path)` cacheia por path** — o mesmo PNG carregado N vezes devolve o **mesmo** handle (decode único). `dispose()` libera todas as texturas nativas.
- **Backend concreto por módulo de render** (diferente de áudio): `SkikoTextureBackend` em `:engine-skiko` (`Texture` = `org.jetbrains.skia.Image`) e `LwjglTextureBackend` em `:engine-lwjgl` (`Texture` = imagem NanoVG). Não há módulo neutro compartilhado porque uma textura é específica do pipeline gráfico. `SkikoRenderer.drawImage` e `LwjglRenderer.drawImage` implementam o desenho com nearest-neighbor.
- **`SkikoHost` e `LwjglHost` passam a wirar `tree.textures`** no startup, ao lado de `tree.textMeasurer`/`tree.audio`, tolerando falha de init.
- **Novo node `Sprite2D : Node2D`** (`:engine`): carrega `texturePath: String` (`@Inspect`), resolve o handle no `onEnter` via `tree.textures`, e desenha a textura **centrada na origem local** (com `flipH`). É a unidade visual mínima — a sentinela da capacidade.
- **`:games:demos` ganha uma cena sentinela** com um `Sprite2D` estático, rodando **identicamente nos dois backends** (Skiko default + `runLwjgl`), provando a capacidade ponta-a-ponta antes de qualquer animação.
- **`Sprite2D` registrado nos bindings Lua** (`nengine.Sprite2D` + stub LuaCATS), para uso futuro pela demo de plataforma.

Sem breaking changes: tudo é adição. O campo `textures` é nullable e default `null`, então jogos e testes existentes seguem idênticos.

## Capabilities

### New Capabilities
- `texture-rendering`: SPI `TextureBackend` + handle `Texture` (com `width`/`height`) em `:engine`, campo `SceneTree.textures` e seu lifecycle (`dispose` no `stop`, cache por path no `load`), e o node `Sprite2D` (resolve `texturePath` via `tree.textures`, desenha centrado com `flipH`).

### Modified Capabilities
- `engine-core`: o `Renderer` SPI ganha a operação `drawImage(texture, src, dst, flipH)` com amostragem nearest-neighbor (nova requirement, sem alterar as primitivas existentes).
- `skiko-runtime`: `SkikoRenderer` implementa `drawImage`; `:engine-skiko` ganha `SkikoTextureBackend` (`Texture` = `org.jetbrains.skia.Image`); `SkikoHost` injeta `tree.textures` no startup.
- `lwjgl-runtime`: `LwjglRenderer` implementa `drawImage` sobre NanoVG; `:engine-lwjgl` ganha `LwjglTextureBackend` (imagem NanoVG); `LwjglHost` injeta `tree.textures` no startup.
- `demos-sample`: nova cena com `Sprite2D` estático, sentinela cross-backend da renderização de textura.
- `lua-scripting`: `Sprite2D` exposto como `nengine.Sprite2D` + entrada no stub LuaCATS.

## Impact

- **Código novo**: SPI `TextureBackend`/`Texture` em `:engine` (`com.neoutils.engine.render.*`); campo `textures` + dispose em `SceneTree`; node `Sprite2D`; `SkikoTextureBackend`; `LwjglTextureBackend`; cena sentinela em demos; asset PNG em `games/demos/src/main/resources/`.
- **Código tocado**: `Renderer` (assinatura `drawImage`), `SkikoRenderer`, `LwjglRenderer`, `SkikoHost.run`, `LwjglHost.run`, registro de tipo + stub em `:engine-bundle-lua`, `settings`/`build.gradle.kts` conforme necessário.
- **Dependências**: nenhuma nova de terceiros — Skia (`Image.makeFromEncoded`) e NanoVG (`nvgCreateImageMem`) já estão nos respectivos backends.
- **Invariantes**: respeita #2 (`:engine` só declara interfaces; `Texture` não vaza `org.jetbrains.skia.*`/`org.lwjgl.*`), #4 (Renderer/Input/GameHost intactos; `drawImage` estende o Renderer existente, `tree.textures` é SPI ortogonal nullable), #1 (serviço server-style; `Sprite2D` é Node por herança, sem componente/ECS).
- **Docs**: `CLAUDE.md` (nota sobre a SPI de textura `tree.textures` espelhando `audio`/`textMeasurer`; menção a `drawImage`/`Sprite2D`), `ROADMAP.md` (linha em Active).
