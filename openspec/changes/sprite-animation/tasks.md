## 1. Node AnimatedSprite2D

- [x] 1.1 Criar `AnimatedSprite2D : Node2D` (`@Serializable`, `open`) em `com.neoutils.engine.scene` com os campos `@Inspect`: `texturePath`, `frameCount`, `fps`, `loop`, `playing`, `currentFrame`, `flipH`; acumulador de tempo + handle `@Transient`.
- [x] 1.2 `onEnter`: resolver `tree.textures?.load(texturePath)`; suportar re-resolução quando `texturePath` muda em runtime (cacheado).
- [x] 1.3 Derivar `frameW = texture.width / frameCount`, `frameH = texture.height`; validar `frameCount >= 1` e avisar/logar se `width % frameCount != 0`.

## 2. Avanço de quadro na engine

- [x] 2.1 `onProcess(dt)`: se `playing && fps > 0 && frameCount > 1`, acumular `dt` e avançar 1 quadro a cada `1/fps`s.
- [x] 2.2 `loop = true` ⇒ wrap (`% frameCount`); `loop = false` ⇒ saturar em `frameCount-1` e `playing = false`.
- [x] 2.3 `playing = false` ⇒ `currentFrame` imutável.
- [x] 2.4 (Opcional) helper `play()`/`pause()` por ergonomia (liga/desliga `playing`).

## 3. Desenho do quadro

- [x] 3.1 `onDraw`: com handle, `drawImage(tex, Rect(Vec2(currentFrame*frameW,0), Vec2(frameW,frameH)), Rect(Vec2(-frameW/2,-frameH/2), Vec2(frameW,frameH)), flipH)`; `null` ⇒ no-op.
- [x] 3.2 `localBounds`: retângulo centrado do quadro quando há textura; `Rect(ZERO, ZERO)` quando não.

## 4. Testes em :engine

- [x] 4.1 Avanço: `fps`/`dt` produzem o número certo de avanços; wrap com `loop`; saturação + `playing=false` sem `loop`; `playing=false` não avança.
- [x] 4.2 Recorte: `currentFrame = k` ⇒ `drawImage` recebe `src` do k-ésimo quadro (fake `Renderer`/`TextureBackend`).
- [x] 4.3 `flipH` propaga ao `drawImage`; sem backend ⇒ `onDraw` no-op, `localBounds` zero.

## 5. Sentinela cross-backend em :games:demos

- [x] 5.1 Importar 1 sheet animado real para `games/demos/src/main/resources/demos/sprites/` (ex.: `Items/Fruits/Apple.png` 544x32 = 17 frames, ou `Run (32x32).png`).
- [x] 5.2 Criar cena `AnimatedSpriteDemo` com um `AnimatedSprite2D` ciclando o sheet; registrar no rol de cenas (Skiko default).
- [x] 5.3 Confirmar avanço de quadro visível nos dois backends (Skiko + `runLwjgl`).

## 6. Binding Lua + stub

- [x] 6.1 Registrar `put("AnimatedSprite2D", AnimatedSprite2D::class.java)` no `LuaScriptHost`.
- [x] 6.2 Adicionar entrada `AnimatedSprite2D : Node2D` nos stubs LuaCATS com os campos de animação.
- [x] 6.3 Teste: `nengine.AnimatedSprite2D` resolve não-nil.

## 7. Verificação e docs

- [x] 7.1 Suíte verde: `:engine`, `:engine-bundle-lua`, `:games:demos`.
- [x] 7.2 Atualizar `CLAUDE.md` (menção a `AnimatedSprite2D`) e `ROADMAP.md`.
- [x] 7.3 Rodar `/opsx:verify sprite-animation` e fechar pendências.
