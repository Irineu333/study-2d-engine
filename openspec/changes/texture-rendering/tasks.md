## 1. SPI de textura em :engine

- [ ] 1.1 Criar `interface Texture` em `com.neoutils.engine.render` com `val width: Int` e `val height: Int` (nada além) + KDoc explicando que é handle de bitmap decodificado, opaco quanto ao backend.
- [ ] 1.2 Criar `interface TextureBackend` em `com.neoutils.engine.render` com `load(path: String): Texture` e `dispose()`, KDoc por método (resolução por classpath, cache por path, fail-fast, dispose libera tudo).
- [ ] 1.3 Adicionar `var textures: TextureBackend? = null` em `SceneTree` (anotação coerente com `audio`/`textMeasurer`).
- [ ] 1.4 Em `SceneTree.stop()`, chamar `textures?.dispose()` exatamente uma vez (ao lado do `audio?.dispose()`).
- [ ] 1.5 Teste em `:engine`: `tree.textures` default `null`; `stop()` chama `dispose()` exatamente uma vez (fake `TextureBackend`); `tree.textures?.load(...)` null-safe.

## 2. drawImage no Renderer SPI

- [ ] 2.1 Adicionar `fun drawImage(texture: Texture, src: Rect, dst: Rect, flipH: Boolean = false)` ao `interface Renderer` com KDoc (src em px da textura, dst em local space sob a transform stack, nearest-neighbor, flipH visual).
- [ ] 2.2 Teste de assinatura/contrato em `:engine` (o que for testável sem backend): o método existe na interface; um fake `Renderer` registra os args; composição com push/popTransform documentada.

## 3. Node Sprite2D

- [ ] 3.1 Criar `Sprite2D : Node2D` (`@Serializable`, `open`) em `com.neoutils.engine.scene` com `texturePath: String` (`@Inspect`) e `flipH: Boolean` (`@Inspect`, default `false`); handle resolvido `@Transient`.
- [ ] 3.2 `onEnter`: resolver `tree.textures?.load(texturePath)` e guardar; `onExit`: limpar referência local (sem dispor — o backend é dono do cache).
- [ ] 3.3 `onDraw`: quando há handle, `renderer.drawImage(tex, Rect(ZERO, Vec2(w,h)), Rect(Vec2(-w/2,-h/2), Vec2(w,h)), flipH)`; quando `null`, no-op.
- [ ] 3.4 `localBounds`: retângulo centrado quando há textura; `Rect(ZERO, ZERO)` quando não.
- [ ] 3.5 Teste em `:engine` com fake `TextureBackend`/`Texture`: `Sprite2D` desenha src/dst esperados (fake `Renderer` captura args); `flipH` propaga; sem backend ⇒ `onDraw` no-op e `localBounds` zero.

## 4. Backend Skiko

- [ ] 4.1 Criar `SkikoTexture(image: org.jetbrains.skia.Image) : Texture` em `:engine-skiko` expondo `image.width`/`image.height`.
- [ ] 4.2 Criar `SkikoTextureBackend : TextureBackend`: `load` resolve via classpath, `Image.makeFromEncoded(bytes)`, cacheia por path, fail-fast em ausente/ilegível; `dispose` fecha todas as `Image`.
- [ ] 4.3 Implementar `SkikoRenderer.drawImage` com `Canvas.drawImageRect` usando `SamplingMode` nearest (`FilterMode.NEAREST`); aplicar `flipH` espelhando em torno do centro de `dst`; cast de `Texture` para `SkikoTexture` com fail-fast em handle estrangeiro.
- [ ] 4.4 `SkikoHost.run`: setar `tree.textures = SkikoTextureBackend()` antes do primeiro frame, tolerando falha de init (log + `null`).
- [ ] 4.5 Teste em `:engine-skiko`: `load` de PNG de fixture devolve `width`/`height` corretos e cacheia por path; `load` inexistente lança; (se viável headless) `drawImage` não lança.

## 5. Backend LWJGL

- [ ] 5.1 Criar `LwjglTexture(handle: Int, width: Int, height: Int) : Texture` em `:engine-lwjgl`.
- [ ] 5.2 Criar `LwjglTextureBackend(nvg: Long) : TextureBackend`: `load` resolve via classpath, `nvgCreateImageMem(..., NVG_IMAGE_NEAREST, ...)` na thread/contexto do loop, cacheia por path, fail-fast; `dispose` chama `nvgDeleteImage` em todas.
- [ ] 5.3 Implementar `LwjglRenderer.drawImage` via `nvgImagePattern` + `nvgFill` no `dst` sob a transform NanoVG corrente; aplicar `flipH`; cast para `LwjglTexture` com fail-fast.
- [ ] 5.4 `LwjglHost.run`: instanciar `LwjglTextureBackend` com o handle NVG e setar `tree.textures`, tolerando falha de init.
- [ ] 5.5 Verificação manual: a cena sentinela (tarefa 6) desenha o sprite no `runLwjgl`.

## 6. Sentinela cross-backend em :games:demos

- [ ] 6.1 Importar 1 PNG de Pixel Adventure 1 para `games/demos/src/main/resources/demos/sprites/` (ex.: `Main Characters/Pink Man/Idle (32x32).png` → `idle.png`).
- [ ] 6.2 Criar uma cena `SpriteDemo` (Kotlin) com um `Sprite2D` estático apontando para o asset, registrada no rol de cenas do demos (Skiko default).
- [ ] 6.3 Confirmar que a cena aparece no `runLwjgl` (mesmo asset, mesma posição, nearest crisp).
- [ ] 6.4 Rodar manualmente nos dois backends e confirmar paridade visual.

## 7. Binding Lua + stub

- [ ] 7.1 Registrar `put("Sprite2D", Sprite2D::class.java)` no `LuaScriptHost`.
- [ ] 7.2 Adicionar entrada `Sprite2D : Node2D` (com `texture_path`/`flip_h`) nos stubs LuaCATS (`node2d.lua`/`nengine.lua`).
- [ ] 7.3 Teste em `:engine-bundle-lua`: `nengine.Sprite2D` resolve não-nil.

## 8. Verificação e docs

- [ ] 8.1 Teste/asserção de invariante: nenhum arquivo de `:engine` importa `org.jetbrains.skia`/`org.lwjgl`; `Texture` expõe só `width`/`height`.
- [ ] 8.2 Suíte verde: `:engine`, `:engine-skiko`, `:engine-lwjgl`, `:engine-bundle-lua`, `:games:demos`; testes headless seguem passando com `tree.textures == null`.
- [ ] 8.3 Atualizar `CLAUDE.md`: nota sobre `tree.textures` (SPI de textura server-style, backend por módulo de render, espelha `audio`/`textMeasurer`) e `Renderer.drawImage` (nearest-neighbor).
- [ ] 8.4 Atualizar `ROADMAP.md`: registrar `texture-rendering` em Active.
- [ ] 8.5 Rodar `/opsx:verify texture-rendering` e fechar pendências.
