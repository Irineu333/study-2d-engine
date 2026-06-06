## ADDED Requirements

### Requirement: SkikoRenderer implements drawImage with nearest-neighbor sampling

`SkikoRenderer` SHALL implement `Renderer.drawImage(texture, src, dst, flipH)` over the bound Skia `Canvas`. The implementation MUST treat `texture` as a `SkikoTexture` wrapping an `org.jetbrains.skia.Image`, drawing the `src` pixel rectangle into the `dst` rectangle under the current Skia matrix (the transform stack). Sampling MUST be **nearest-neighbor** (e.g. `SamplingMode` with `FilterMode.NEAREST`), never the default bilinear, so scaled pixel-art stays crisp. `flipH` MUST mirror the drawn region horizontally about the center of `dst`. A `Texture` that is not a `SkikoTexture` MUST fail fast with a descriptive exception.

#### Scenario: SkikoRenderer scales pixel-art crisply

- **WHEN** `SkikoRenderer.drawImage` draws a 16x16 texture region into a 64x64 `dst`
- **THEN** the result is a 4x nearest-neighbor upscale (hard pixel edges, no blur)

#### Scenario: SkikoRenderer rejects a foreign texture handle

- **WHEN** `SkikoRenderer.drawImage` is called with a `Texture` that is not a `SkikoTexture`
- **THEN** a descriptive exception is thrown

### Requirement: engine-skiko provides a Skia-backed TextureBackend wired at startup

The `:engine-skiko` module SHALL provide a `SkikoTextureBackend : TextureBackend` whose `Texture` implementation (`SkikoTexture`) wraps an `org.jetbrains.skia.Image` and exposes the image's `width`/`height`. `load(path)` MUST resolve the asset via the classpath, decode it once via Skia (e.g. `Image.makeFromEncoded`), cache the handle by path, and fail fast on a missing/unreadable asset. `dispose()` MUST close every cached Skia `Image`. `SkikoHost.run` MUST set `tree.textures = SkikoTextureBackend(...)` once before the first frame, alongside `tree.textMeasurer`/`tree.audio`; failure to initialize MUST be tolerated (log and leave `tree.textures` as `null`). The teardown path (`tree.stop()`) disposes it.

#### Scenario: SkikoHost wires the texture backend into the tree

- **WHEN** `SkikoHost().run(tree, config)` starts
- **THEN** `tree.textures` is set to a `SkikoTextureBackend` before the first frame
- **AND** nodes can reach it via `node.tree.textures`
- **AND** when the window closes, `tree.stop()` disposes the texture backend

#### Scenario: SkikoTexture reports the decoded image dimensions

- **WHEN** `SkikoTextureBackend.load` decodes a 352x32 PNG
- **THEN** the returned `Texture` reports `width == 352` and `height == 32`

### Requirement: engine-skiko keeps Skia confined to the backend module

`SkikoTexture`/`SkikoTextureBackend` MAY reference `org.jetbrains.skia.*` (they live in `:engine-skiko`), but `:engine` MUST NOT — the `Texture`/`TextureBackend` interfaces it consumes stay Skia-free.

#### Scenario: Skia image type does not leak into :engine

- **WHEN** the `:engine` module is compiled
- **THEN** no `:engine` source references `org.jetbrains.skia.Image` or any other Skia type for textures
