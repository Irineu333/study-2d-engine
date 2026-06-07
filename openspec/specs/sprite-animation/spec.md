# sprite-animation Specification

## Purpose

`AnimatedSprite2D` builds on the texture-rendering foundation (`Sprite2D` + `Renderer.drawImage` + `tree.textures`) to cycle a horizontal sheet of equal-size frames. The engine — not a script — advances the current frame over time in `onProcess(dt)`, honoring `fps`/`loop`/`playing`, and reuses `Renderer.drawImage` (no new renderer op) to draw the current frame centered on the local origin, optionally mirrored via `flipH`.

## Requirements

### Requirement: AnimatedSprite2D cycles frames of a horizontal sheet

`:engine` SHALL prover `AnimatedSprite2D : Node2D` (`@Serializable`, `open`) em `com.neoutils.engine.scene`, modelando um **sheet horizontal** de quadros de mesmo tamanho, com os campos `@Inspect`:

- `texturePath: String` — sheet a carregar.
- `frameCount: Int` (>= 1) — número de quadros lado a lado no sheet.
- `fps: Float` (default p.ex. `10f`) — taxa de avanço de quadro.
- `loop: Boolean` (default `true`).
- `playing: Boolean` (default `true`).
- `currentFrame: Int` (default `0`).
- `flipH: Boolean` (default `false`).

A largura de cada quadro SHALL ser `texture.width / frameCount` e a altura `texture.height`. O handle de textura SHALL ser resolvido no `onEnter` via `tree.textures?.load(texturePath)` (cacheado), e re-resolvido se `texturePath` mudar. Trocar `texturePath` + `frameCount` em runtime SHALL trocar a animação exibida.

#### Scenario: frame size derives from texture width and frameCount

- **WHEN** um `AnimatedSprite2D` carrega um sheet de 384x32 com `frameCount = 12`
- **THEN** cada quadro tem `frameW == 32` e `frameH == 32`

#### Scenario: AnimatedSprite2D with no backend is invisible but safe

- **WHEN** `tree.textures` é `null` e um `AnimatedSprite2D` é renderizado
- **THEN** `onDraw` não desenha nada e não lança

### Requirement: The engine advances the current frame over time

`AnimatedSprite2D` SHALL avançar `currentFrame` na própria engine, em `onProcess(dt)` (frame-time), **sem** depender de script. Quando `playing` é `true`, `fps > 0` e `frameCount > 1`, o node SHALL acumular `dt` e avançar um quadro a cada `1/fps` segundos. Com `loop = true`, o avanço SHALL dar wrap (`currentFrame = (currentFrame + 1) % frameCount`). Com `loop = false`, ao passar do último quadro o node SHALL saturar em `frameCount - 1` e setar `playing = false`. Quando `playing` é `false`, `currentFrame` MUST NOT mudar.

#### Scenario: looping animation wraps around

- **WHEN** um `AnimatedSprite2D` com `frameCount = 4`, `fps = 10`, `loop = true`, `currentFrame = 3` recebe `onProcess` com `dt` suficiente para um avanço
- **THEN** `currentFrame` torna-se `0` (wrap)

#### Scenario: non-looping animation stops on the last frame

- **WHEN** um `AnimatedSprite2D` com `frameCount = 4`, `loop = false`, `currentFrame = 3` recebe `onProcess` com `dt` suficiente para um avanço
- **THEN** `currentFrame` permanece `3`
- **AND** `playing` torna-se `false`

#### Scenario: paused animation does not advance

- **WHEN** um `AnimatedSprite2D` com `playing = false` recebe `onProcess` com qualquer `dt`
- **THEN** `currentFrame` não muda

#### Scenario: fps controls the advance rate

- **WHEN** um `AnimatedSprite2D` com `fps = 10`, `loop = true`, `currentFrame = 0` recebe `onProcess` totalizando `0.25s` em ticks
- **THEN** `currentFrame` avançou exatamente 2 quadros (um a cada `0.1s`)

### Requirement: onDraw renders the current frame via Renderer.drawImage

`AnimatedSprite2D.onDraw` SHALL desenhar o quadro corrente reusando `Renderer.drawImage`, com `src = Rect(Vec2(currentFrame * frameW, 0), Vec2(frameW, frameH))` e `dst = Rect(Vec2(-frameW/2, -frameH/2), Vec2(frameW, frameH))` (centrado na origem local), passando `flipH`. Não SHALL introduzir nenhuma operação nova no `Renderer`. `localBounds` SHALL ser o retângulo centrado do quadro quando há textura, e `Rect(ZERO, ZERO)` quando não.

#### Scenario: onDraw selects the current frame's source rectangle

- **WHEN** um `AnimatedSprite2D` (sheet 384x32, `frameCount = 12`, `currentFrame = 3`) é renderizado
- **THEN** `drawImage` é chamado com `src` cobrindo `[96, 128] x [0, 32]` (o quarto quadro)
- **AND** `dst` é o retângulo 32x32 centrado na origem local

#### Scenario: flipH mirrors the rendered frame

- **WHEN** um `AnimatedSprite2D` com `flipH = true` desenha o quadro corrente
- **THEN** `drawImage` recebe `flipH = true` e o quadro aparece espelhado, sem `scale.x` negativo no node
