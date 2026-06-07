## Context

`texture-rendering` deu à engine `Texture` (com `width`/`height`), `tree.textures.load(path)` (cacheado) e `Renderer.drawImage(texture, src, dst, flipH)` (nearest-neighbor, sob a transform stack). `Sprite2D` já desenha uma textura inteira centrada.

Os assets de personagem do Pixel Adventure 1 são **strips horizontais**: `Idle (32x32).png` = 352x32 = 11 quadros lado a lado; `Run (32x32).png` = 384x32 = 12 quadros. Cada quadro é `width / frameCount` × `height`. Animar = desenhar o recorte do quadro corrente e avançar o índice no tempo.

Restrições: invariante #1 (node por herança, não sistema/componente), #2 (só a SPI pura), #4 (sem SPI nova; reusar `drawImage`).

## Goals / Non-Goals

**Goals:**
- Um node que cicla quadros de um **sheet horizontal** a uma taxa `fps`, com `loop`, e desenha o quadro corrente reusando `drawImage`.
- Avanço de quadro **na engine** (`onProcess`), independente do script — o script só liga/desliga e troca o sheet/estado.
- `flipH` para virar o personagem (herda a semântica de `drawImage`).
- Sentinela cross-backend em `:games:demos`.

**Non-Goals:**
- Catálogo nomeado de animações (`SpriteFrames` com "idle"/"run"/"jump" e troca por nome) — v1 troca por `texturePath`+`frameCount`. Fica para extensão futura, sem quebrar a SPI.
- Sheets em grade 2D (múltiplas linhas), margens/spacing entre quadros, trimming — os assets são strips 1D; grade entra se um asset exigir.
- Sinais de animação (`animation_finished`), inversão de playback, velocidade por animação além de `fps`.
- Sincronizar avanço de quadro com `physics_process` (tick fixo) — animação é cosmética, roda em frame-time como o render.

## Decisions

### D1 — Modelo "sheet horizontal por `frameCount`", não grade nem catálogo

`AnimatedSprite2D` carrega `texturePath` + `frameCount`. `frameW = texture.width / frameCount`, `frameH = texture.height`. Quadro `i` ⇒ `src = Rect(Vec2(i*frameW, 0), Vec2(frameW, frameH))`.

- **Por quê**: bate 1:1 com o formato dos assets (strips 1D). É o mínimo que anima o personagem. Derivar `frameW` de `texture.width/frameCount` evita um campo redundante e usa o `width` que `Texture` expõe.
- **Alternativa rejeitada**: `SpriteFrames` (recurso Godot com animações nomeadas, cada uma uma lista de frames/atlas). Mais poderoso e o caminho natural depois, mas é escopo grande (recurso serializável, editor, troca por nome) para o v1. A troca de estado da demo (idle↔run↔jump) se resolve trocando `texturePath`+`frameCount`; quando o catálogo nomeado entrar, `AnimatedSprite2D` ganha um campo `frames: SpriteFrames` sem quebrar os existentes.
- **Alternativa rejeitada**: grade 2D (`hframes`/`vframes`). Os assets de personagem são 1D; adicionar a dimensão vertical agora seria YAGNI.

### D2 — Avanço de quadro na engine (`onProcess`), não no script

Em `onProcess(dt)`: se `playing` e `fps > 0` e `frameCount > 1`, acumula `dt` num acumulador `@Transient`; a cada `1/fps` segundos avança `currentFrame`. Com `loop`, faz wrap (`% frameCount`); sem `loop`, satura no último quadro e seta `playing = false`.

- **Por quê**: animação é responsabilidade do node (Godot-style), não do script. O script de gameplay decide *qual* animação e *quando* tocar; *como* avançar os quadros é da engine. Mantém scripts enxutos e o comportamento testável sem scripting.
- **Por quê frame-time, não physics-time**: animação é cosmética; rodar no `onProcess` (mesmo relógio do render) evita acoplá-la ao tick fixo de física.

### D3 — `onDraw` reusa `drawImage`, centrado, com `flipH`

`onDraw`: com handle resolvido, `renderer.drawImage(tex, srcDoQuadro, Rect(Vec2(-frameW/2, -frameH/2), Vec2(frameW, frameH)), flipH)`. `null` ⇒ no-op. `localBounds` = retângulo centrado do quadro.

- **Por quê centrado**: mesma convenção do `Sprite2D` (Godot centra por default), facilita posicionar o personagem pelo centro/pés.
- **Reuso**: nenhuma operação de render nova — a animação é "qual `src` desenhar a cada frame".

### D4 — Resolução de textura no `onEnter`, igual `Sprite2D`

Resolve `tree.textures?.load(texturePath)` no `onEnter`. Trocar `texturePath` em runtime (troca de estado) re-resolve via `load` (cacheado — sem custo se já carregado).

- **Por quê**: consistente com `Sprite2D`. O cache por path de `tree.textures` torna a troca de animação barata (o sheet de "run" carrega uma vez e fica).

### D5 — Sentinela em `:games:demos`, sheet real

Cena com um `AnimatedSprite2D` ciclando um sheet real (ex.: fruta 17-frames ou Run 12-frames), nos dois backends.

- **Por quê**: prova o avanço temporal + recorte de quadro **antes** da demo de plataforma. Reaproveita o test bed cross-backend do demos. Visualmente óbvio (frame errado/sem avanço salta aos olhos).

## Risks / Trade-offs

- **`texture.width` não divisível por `frameCount`** → `frameW` truncaria; validar no `onEnter` (logar/avisar) ou exigir divisibilidade. Para os assets reais a divisão é exata (352/11, 384/12, 544/17, 32×17=544). Documentar a expectativa.
- **Troca de `texturePath` sem ajustar `frameCount`** → quadros errados; é responsabilidade do chamador trocar o par junto. A demo encapsula isso (guarda pares estado→(path,count)).
- **Avanço dependente de `dt` grande (stutter/pausa)** → com `dt` enorme, vários quadros pulam de uma vez; aceitável para SFX visual. Sem clamp no v1.
- **Sem `SpriteFrames` nomeado** → trocar animação é mais verboso (trocar path+count); decisão consciente de escopo (D1), extensível depois sem quebra.
