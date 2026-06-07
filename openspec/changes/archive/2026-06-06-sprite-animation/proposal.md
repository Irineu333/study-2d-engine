## Why

Com `texture-rendering`, a engine desenha uma textura estática (ou um recorte dela). Mas todo personagem de plataforma é **animado**: o Pink Man tem sheets de Idle (11 frames), Run (12 frames), Jump, Fall — strips horizontais de quadros 32x32. Sem um node que cicla frames de um sheet ao longo do tempo, a demo de plataforma teria um boneco congelado.

Esta change adiciona o **node de animação de sprite** sobre a fundação de `texture-rendering`: um `AnimatedSprite2D` que conhece o layout de quadros de um sheet, avança o quadro corrente pelo `dt` (na engine, não no script), e desenha o recorte do quadro via o `Renderer.drawImage` que já existe. É a peça que dá vida ao personagem.

Segunda de quatro changes. **Depende de `texture-rendering`** (usa `Texture`, `tree.textures`, `drawImage`). É pré-requisito da `game-platformer` (o player troca de animação conforme estado: idle/run/jump).

## What Changes

- **Novo node `AnimatedSprite2D : Node2D`** (`:engine`). Modelo de **sheet horizontal** (o formato dos assets): carrega `texturePath: String` + `frameCount: Int` (quantos quadros no sheet) — a largura de cada quadro é `texture.width / frameCount`, altura = `texture.height`. Campos: `fps: Float` (taxa de avanço), `loop: Boolean` (default `true`), `playing: Boolean` (default `true`), `currentFrame: Int`, `flipH: Boolean`.
- **A engine avança os quadros**, não o script: em `onProcess(dt)` (frame-time), `AnimatedSprite2D` acumula tempo e avança `currentFrame` a `fps` quadros/segundo; com `loop = false`, para no último quadro e zera `playing`. Isso mantém a animação independente do script (Godot-style `AnimatedSprite2D` com `SpriteFrames`).
- **`onDraw` desenha o quadro corrente**: `src = Rect(Vec2(currentFrame * frameW, 0), Vec2(frameW, frameH))`, `dst` centrado na origem local — reusa `Renderer.drawImage(tex, src, dst, flipH)` sem nova operação de render.
- **API de controle mínima**: `play()` (liga `playing`, opcionalmente reinicia), `stop()`/`pause` via `playing = false`. Trocar de animação no v1 = trocar `texturePath` + `frameCount` (o player guarda os parâmetros de cada estado e os aplica). Um catálogo nomeado de animações (`SpriteFrames` com nomes "idle"/"run") fica para extensão futura — o v1 mira o mínimo para a demo.
- **`:games:demos` ganha uma cena sentinela** com um `AnimatedSprite2D` ciclando um sheet real (ex.: a fruta animada de 17 frames, ou o Run do Pink Man), rodando **nos dois backends**, provando o avanço de quadro + recorte antes da demo de plataforma.
- **`AnimatedSprite2D` registrado nos bindings Lua** (`nengine.AnimatedSprite2D` + stub LuaCATS), para o player Lua da change 4 trocar animação/`flipH`.

Sem breaking changes: adição pura, construída sobre `texture-rendering`.

## Capabilities

### New Capabilities
- `sprite-animation`: o node `AnimatedSprite2D` (sheet horizontal por `frameCount`, `fps`/`loop`/`playing`/`currentFrame`/`flipH`), o avanço de quadro dirigido pela engine no `onProcess`, e o desenho do quadro corrente via `Renderer.drawImage`.

### Modified Capabilities
- `demos-sample`: nova cena com `AnimatedSprite2D` ciclando um sheet real, sentinela cross-backend da animação.
- `lua-scripting`: `AnimatedSprite2D` exposto como `nengine.AnimatedSprite2D` + entrada no stub LuaCATS.

## Impact

- **Código novo**: node `AnimatedSprite2D` em `com.neoutils.engine.scene`; cena sentinela em demos; asset PNG animado em `games/demos/src/main/resources/`.
- **Código tocado**: registro de tipo + stub em `:engine-bundle-lua`.
- **Dependências**: nenhuma nova — usa `Texture`/`tree.textures`/`drawImage` de `texture-rendering`.
- **Pré-requisito**: change `texture-rendering` aplicada.
- **Invariantes**: respeita #1 (`AnimatedSprite2D` é Node por herança; o avanço de quadro é lógica do node, não um sistema/componente externo), #2 (sem tipo de backend; só usa a SPI pura), #4 (nenhuma SPI nova; reusa `drawImage`).
- **Docs**: `CLAUDE.md` (menção a `AnimatedSprite2D` na lista de nodes visuais), `ROADMAP.md`.
