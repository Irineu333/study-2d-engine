## ADDED Requirements

### Requirement: Renderer SPI exposes image drawing with nearest-neighbor sampling

The `Renderer` interface SHALL additionally expose an image-drawing operation:

```kotlin
fun drawImage(texture: Texture, src: Rect, dst: Rect, flipH: Boolean = false)
```

`drawImage` MUST draw the sub-rectangle `src` (in texture pixel coordinates, origin top-left) of `texture` into the destination rectangle `dst` (interpreted under the current transform stack, exactly like `drawRect`). The image MUST be sampled with **nearest-neighbor** filtering (no smoothing/bilinear) so that scaled pixel-art stays crisp. When `flipH` is `true`, the drawn region MUST be mirrored horizontally about the center of `dst`, with no change to the transform stack. `drawImage` MUST compose with `pushTransform`/`pushClip` like every other `draw*` call, and MUST NOT leak any backend-specific type through its signature — `Texture` is the engine-pure handle from `com.neoutils.engine.render`. Backends MAY assume the `Texture` is one they produced and fail fast on a foreign handle.

#### Scenario: drawImage renders a texture region into the destination rect

- **WHEN** a node calls `renderer.drawImage(texture, src = Rect(Vec2(0f, 0f), Vec2(32f, 32f)), dst = Rect(Vec2(0f, 0f), Vec2(64f, 64f)))`
- **THEN** the 32x32 top-left region of the texture appears at surface rectangle `(0, 0)`–`(64, 64)`, scaled 2x with nearest-neighbor sampling (no blur)

#### Scenario: drawImage respects the transform stack

- **WHEN** code calls `renderer.pushTransform(translation = Vec2(100f, 50f), rotation = 0f, scale = Vec2(1f, 1f))` then `renderer.drawImage(texture, Rect(Vec2(0f,0f), Vec2(W,H)), Rect(Vec2(0f,0f), Vec2(W,H)))` then `renderer.popTransform()`
- **THEN** the image appears at surface position `(100, 50)`

#### Scenario: flipH mirrors horizontally

- **WHEN** a node draws the same `src`/`dst` once with `flipH = false` and once with `flipH = true`
- **THEN** the two results are horizontal mirror images of each other about the center of `dst`
- **AND** neither call alters the transform stack
