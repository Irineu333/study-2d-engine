## Context

A engine desenha tudo vetorialmente. Toda capability de plataforma (render de primitivas, input, métrica de texto, áudio) segue o mesmo molde: uma **SPI Kotlin-pura em `:engine`**, uma **implementação concreta num módulo de backend**, e o **host injetando a implementação na `SceneTree`** no startup. Nodes alcançam o serviço via `node.tree.<servico>`.

O precedente direto é o par `AudioBackend`/`tree.audio` (off-frame, server-style, nullable, disposed no `stop`) somado ao `Renderer` (per-frame, com transform/clip stack). Textura é uma **mistura dos dois**: o *load/dispose* é server-style como áudio (`tree.textures`), mas o *draw* é per-frame e passa pela transform stack como qualquer primitiva (`Renderer.drawImage`).

Restrições inegociáveis:
- **Invariante #2**: `:engine` não pode declarar `org.jetbrains.skia.*` nem `org.lwjgl.*`. `Texture` e `TextureBackend` são interfaces puras; o tipo concreto (Skia `Image`, imagem NanoVG) só existe no backend.
- **Invariante #4**: `Renderer`/`Input`/`GameHost` continuam as SPIs obrigatórias. `drawImage` **estende** o `Renderer` existente (não cria um pilar novo); `tree.textures` é uma SPI **nova, ortogonal e nullable**, como `audio`.
- **Invariante #1**: server-style para load. `Sprite2D` é um `Node2D` por herança (não componente).

## Goals / Non-Goals

**Goals:**
- Carregar um PNG num handle reutilizável e cacheado, e desenhá-lo (inteiro ou um recorte `src`) num retângulo `dst`, sob a transform stack.
- **Nearest-neighbor obrigatório** — pixel-art não pode amostrar borrado.
- `flipH` para espelhar horizontalmente (virar personagem) sem `scale.x` negativo.
- SPI mínima e estável (`TextureBackend` + `Texture` com `width`/`height`) que comporte atlas, animação e tilemap depois sem quebrar.
- Funcionar **identicamente** nos dois backends (Skiko e LWJGL), provado por uma cena sentinela em `:games:demos`.
- Degradação graciosa com `tree.textures == null` (headless/teste).

**Non-Goals:**
- Animação de sprite (frames/fps) — é a change `sprite-animation`.
- Tilemap / atlas estruturado — é a change `tilemap-visual`.
- `modulate`/tint, rotação de billboard, blend modes, tiling/parallax de background — fora do v1 mínimo.
- Filtragem bilinear/suave configurável — v1 é nearest-only (a engine mira pixel-art); um flag `smooth` é extensão futura sem quebra de SPI.
- Formatos além de PNG (o decode usa o que cada backend já lê; PNG é o do pacote de assets).
- Binding Python dos novos nodes — a demo de plataforma é Lua; Python entra quando precisar.
- Hot-reload / streaming de textura, mipmaps, compressão de GPU.

## Decisions

### D1 — `drawImage` no `Renderer`, não numa SPI separada de "sprite render"

`fun drawImage(texture: Texture, src: Rect, dst: Rect, flipH: Boolean = false)` entra no `Renderer`, ao lado de `drawRect`/`drawPolygon`.

- **Por quê**: desenhar é per-frame e tem que compor com `pushTransform`/`pushClip` exatamente como as primitivas. Pôr fora do `Renderer` duplicaria a transform stack. `src`/`dst` explícitos (em vez de só `position`) permitem que tilemap e animação desenhem **recortes** do atlas reusando a mesma operação.
- **`src` em pixels da textura, `dst` em local space**: `src` indexa o bitmap (origem top-left, eixo y pra baixo, como os PNGs); `dst` é onde aparece no frame local, sob a transform stack. Desacopla resolução do asset da escala em tela.
- **Alternativa rejeitada**: `drawImage(texture, position)` desenhando a textura inteira no tamanho nativo. Simples demais — tilemap e animação **precisam** de `src`/`dst` arbitrários; introduzi-los já evita refazer a assinatura em `sprite-animation`/`tilemap-visual`.

### D2 — `Texture` expõe `width`/`height` (não é handle opaco como `Sound`)

`interface Texture { val width: Int; val height: Int }`.

- **Por quê**: para desenhar um sprite "inteiro centrado" o `Sprite2D` precisa saber o tamanho do PNG; para fatiar um sheet/atlas, `sprite-animation`/`tilemap-visual` precisam das dimensões. Expor `width`/`height` é o mínimo que a lógica de jogo consome — e são ints neutros, não vazam tipo de backend.
- **Limite**: nenhum outro membro. Bytes/pixels/handle nativo ficam internos ao backend. Qualquer query além de dimensão entra como método do `TextureBackend`, não do handle (mesma disciplina do `Sound`).

### D3 — `TextureBackend` server-style em `tree.textures`, nullable, cache por path

`SceneTree.textures: TextureBackend?` (default `null`), injetado pelo host, espelhando `audio`/`textMeasurer`. `load(path)` resolve via classpath (convenção dos demais assets, ex. `"demos/sprites/idle.png"`), decodifica, e **cacheia por path** (mesmo path ⇒ mesmo handle). `dispose()` libera todas as texturas.

- **Por quê cache**: a demo de plataforma carrega o mesmo atlas/sheet de vários nodes; decode único por path é o esperado e barato. Cache interno mantém a SPI de uma linha (`load(path)`), diferente do áudio que separou `load`/`play` por causa do hot-path de re-disparo — aqui o "hot path" é o `drawImage`, que já recebe o handle pronto.
- **Por quê nullable + no-op**: testes headless e `SceneTree` sem host não têm backend gráfico. `Sprite2D` resolve `tree.textures?.load(...)`; `null` ⇒ não desenha, não quebra.
- **Fail-fast**: path inexistente/ilegível/não-decodificável lança exceção descritiva (coerente com o fail-fast do scripting e do `AudioBackend.load`).

### D4 — Backend concreto **por módulo de render**, não num módulo neutro

`SkikoTextureBackend` vive em `:engine-skiko`; `LwjglTextureBackend` em `:engine-lwjgl`. Não há `:engine-texture-*` compartilhado.

- **Por quê**: diferente do áudio (decode WAV é JDK puro, um módulo serve os dois hosts), uma textura É o objeto do pipeline gráfico — `org.jetbrains.skia.Image` no Skiko, handle de imagem NanoVG no LWJGL. Não há representação neutra compartilhável que não seja "bytes do PNG" (e aí cada backend redecodifica). Logo, cada backend tem o seu.
- **Consequência**: o `drawImage` do renderer e o `TextureBackend` do mesmo módulo conhecem o tipo concreto de `Texture`; `drawImage` faz cast do handle para o tipo do backend e falha fast em handle estrangeiro (só haveria handle estrangeiro se dois backends coexistissem no mesmo processo — não acontece).

### D5 — Nearest-neighbor sempre (v1)

Skiko: amostragem `SamplingMode.DEFAULT` é bilinear; usar o modo **nearest** (`SamplingMode.MITCHELL`? não — `FilterMipmap(FilterMode.NEAREST, ...)` / `SamplingMode.DEFAULT` substituído por nearest) ao desenhar a `Image`. LWJGL/NanoVG: criar a imagem com a flag `NVG_IMAGE_NEAREST`.

- **Por quê**: 16px/32px escalados com bilinear viram um borrão. Pixel-art exige nearest. Como a engine mira esse estilo (assets Pixel Adventure), nearest é o default e único modo do v1.
- **Extensão futura**: um parâmetro `smooth: Boolean = false` no `drawImage` (ou flag no `load`) adiciona bilinear sem quebrar chamadas existentes.

### D6 — `flipH` no `drawImage`, não `scale.x` negativo no node

Espelhar = trocar as bordas esquerda/direita do `src` (ou negar a largura de `dst` em torno do centro) dentro do `drawImage`.

- **Por quê**: `scale.x` negativo no `Node2D.transform` propagaria pela transform stack e pela física/bounds (determinante negativo, normais invertidas) — quebraria colisão e hit-test. Espelhar é puramente visual; mora no `drawImage`/`Sprite2D`, não na transform do node.

### D7 — `Sprite2D` resolve `texturePath` no `onEnter`, desenha em local space centrado

`Sprite2D : Node2D` com `texturePath: String` (`@Inspect`) e `flipH: Boolean` (`@Inspect`). No `onEnter` resolve `tree.textures?.load(texturePath)` e guarda o handle (`@Transient`). `onDraw` desenha `src = (0,0,w,h)` em `dst` centrado na origem local (`Rect(Vec2(-w/2, -h/2), Vec2(w, h))`), respeitando `flipH`. `localBounds` = o retângulo centrado (para pick/hit-test futuros).

- **Por quê resolver no enter**: cena declarativa (`scene.json` só tem a string do path); o handle vem do `tree.textures` que já existe no enter. Mantém o padrão dos outros nodes que leem serviços de tree no enter.
- **Por quê centrado**: convenção Godot (`Sprite2D` centraliza por default via `centered = true`); facilita posicionar/escalar/rotacionar em torno do meio. (`centered`/`offset` configuráveis ficam para extensão futura — v1 centra.)
- **`tree.textures == null` ⇒ handle `null` ⇒ `onDraw` no-op**: sprite invisível em headless, sem erro.

### D8 — Sentinela cross-backend em `:games:demos`

Uma cena com um `Sprite2D` estático (ex.: o sheet idle do Pink Man, primeiro frame via `src`, ou a textura inteira) entra no rol de cenas do demos, que **já** roda nos dois backends (Skiko default + task `runLwjgl`).

- **Por quê demos e não a demo de plataforma**: a fundação tem que se provar **antes** de animação/tilemap existirem. `:games:demos` é o test bed vivo de invariantes e já exercita os dois backends — é o lugar natural pra sentinela "um PNG aparece igual nos dois". A demo de plataforma (change 4) só assembla o que já foi validado.

## Risks / Trade-offs

- **Carregar imagem NanoVG fora da thread/contexto GL correto** → no LWJGL a imagem é criada no contexto NVG, que vive na main thread do loop. `load` disparado no `onEnter` roda dentro do tick (na thread certa). Documentar que `tree.textures.load` no LWJGL só é válido durante o loop (não em init off-thread).
- **Cast de `Texture` para o tipo concreto do backend** → v1 tem um backend por processo; `drawImage` valida o tipo e falha fast em handle estrangeiro (rede de segurança, não cenário real).
- **Vazamento de textura nativa** → liberação amarrada a `tree.textures.dispose()` no `tree.stop()` (já chamado no teardown dos hosts). Cache por path evita handles órfãos por recarga.
- **Nearest-only frustra quem quer arte suave** → decisão consciente (D5); a SPI comporta um flag `smooth` futuro sem quebra.
- **`Texture.width/height` tenta vazar mais detalhe depois** → manter a interface em exatamente dois ints; qualquer coisa além entra no `TextureBackend`.
- **`:engine` acidentalmente referenciar `org.jetbrains.skia.*`/`org.lwjgl.*`** → teste de invariante (grep/classpath) garante que só as interfaces puras vivem em `:engine`, como já há para áudio.
