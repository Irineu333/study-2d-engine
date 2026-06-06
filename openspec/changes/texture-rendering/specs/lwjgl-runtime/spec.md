## ADDED Requirements

### Requirement: LwjglRenderer implements drawImage with nearest-neighbor sampling over NanoVG

`LwjglRenderer` SHALL implement `Renderer.drawImage(texture, src, dst, flipH)` over the active NanoVG context. The implementation MUST treat `texture` as an `LwjglTexture` wrapping a NanoVG image handle, painting the `src` pixel rectangle into the `dst` rectangle under the current NanoVG transform (the transform stack), using an `nvgImagePattern` (or equivalent) so an arbitrary sub-rectangle and scale can be drawn. The NanoVG image MUST be created with the `NVG_IMAGE_NEAREST` flag so sampling is **nearest-neighbor**, never the default smoothing. `flipH` MUST mirror the drawn region horizontally about the center of `dst`. A `Texture` that is not an `LwjglTexture` MUST fail fast with a descriptive exception.

#### Scenario: LwjglRenderer scales pixel-art crisply

- **WHEN** `LwjglRenderer.drawImage` draws a 16x16 texture region into a 64x64 `dst`
- **THEN** the result is a 4x nearest-neighbor upscale (hard pixel edges, no blur)

#### Scenario: LwjglRenderer rejects a foreign texture handle

- **WHEN** `LwjglRenderer.drawImage` is called with a `Texture` that is not an `LwjglTexture`
- **THEN** a descriptive exception is thrown

### Requirement: engine-lwjgl provides a NanoVG-backed TextureBackend wired at startup

The `:engine-lwjgl` module SHALL provide an `LwjglTextureBackend : TextureBackend` whose `Texture` implementation (`LwjglTexture`) wraps a NanoVG image handle and exposes its `width`/`height`. `load(path)` MUST resolve the asset via the classpath, decode it once into a NanoVG image (e.g. `nvgCreateImageMem` with `NVG_IMAGE_NEAREST`) on the render thread/context, cache the handle by path, and fail fast on a missing/unreadable asset. `dispose()` MUST delete every cached NanoVG image (`nvgDeleteImage`). `LwjglHost.run` MUST set `tree.textures = LwjglTextureBackend(...)` during startup, alongside the other tree services; failure to initialize MUST be tolerated (log and leave `tree.textures` as `null`). The teardown path (`tree.stop()`) disposes it.

#### Scenario: LwjglHost wires the texture backend into the tree

- **WHEN** `LwjglHost().run(tree, config)` starts
- **THEN** `tree.textures` is set to an `LwjglTextureBackend` before the first frame
- **AND** nodes can reach it via `node.tree.textures`
- **AND** when the window closes, `tree.stop()` disposes the texture backend

#### Scenario: NanoVG image is created with nearest sampling

- **WHEN** `LwjglTextureBackend.load` decodes a PNG
- **THEN** the NanoVG image is created with the `NVG_IMAGE_NEAREST` flag

### Requirement: engine-lwjgl keeps LWJGL confined to the backend module

`LwjglTexture`/`LwjglTextureBackend` MAY reference `org.lwjgl.*` (they live in `:engine-lwjgl`), but `:engine` MUST NOT — the `Texture`/`TextureBackend` interfaces it consumes stay LWJGL-free.

#### Scenario: NanoVG image handle does not leak into :engine

- **WHEN** the `:engine` module is compiled
- **THEN** no `:engine` source references `org.lwjgl.*` for textures
