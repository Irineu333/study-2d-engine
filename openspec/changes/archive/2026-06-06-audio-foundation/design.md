## Context

A engine hoje não emite som algum. Toda capability de plataforma (desenho, input, métrica de texto) segue o mesmo molde consagrado: uma **SPI Kotlin-pura em `:engine`**, uma **implementação concreta num módulo de backend**, e o **host injetando a implementação na `SceneTree`** no startup. Nodes e scripts alcançam o serviço via `node.tree.<servico>`.

O precedente mais próximo do áudio **não** é o `Renderer` (per-frame, stateful, com transform stack) e sim o `TextMeasurer`: `SceneTree.textMeasurer: TextMeasurer?` é uma SPI de plataforma nullable, injetada pelo `SkikoHost` (`tree.textMeasurer = SkikoTextMeasurer()`), consumida **off-frame**. Áudio é exatamente isso — só que disparado por evento de gameplay em vez de por layout.

Restrições inegociáveis:
- **Invariante #2**: `:engine` não pode declarar `javax.sound.*` nem qualquer binding de áudio nativo — só a interface.
- **Invariante #4**: `Renderer`/`Input`/`GameHost` continuam as SPIs obrigatórias; áudio é uma SPI **nova e ortogonal**, não um quarto pilar obrigatório (é opcional/nullable).
- **Invariante #1**: server-style. Sem `AudioStreamPlayer` Node neste v1 (decisão de escopo), sem `List<Component>`, sem ECS. É um serviço de tree.

## Goals / Non-Goals

**Goals:**
- Tocar SFX curto, **fire-and-forget**, a partir de Kotlin e de scripts Python.
- SPI mínima e estável em `:engine` (`AudioBackend` + `Sound`) que comporte música/posicional depois sem quebrar.
- Backend **host-agnóstico** em JDK puro (`javax.sound.sampled`) compartilhado por `:engine-skiko` e `:engine-lwjgl` — provar que render e áudio são eixos independentes.
- Vozes sobrepostas: re-disparos rápidos do mesmo som (bola batendo na raquete) não se cortam.
- Silêncio gracioso quando não há backend (testes/headless).

**Non-Goals:**
- Música em streaming, loop, crossfade.
- Áudio posicional 2D (atenuação/pan por distância), buses/mixer, efeitos (reverb).
- `AudioStreamPlayer`/`AudioStreamPlayer2D` como Nodes.
- Formatos comprimidos (OGG/MP3) — exigem decoder de terceiro.
- Binding Lua (tictactoe). O v1 valida via Python (Pong). Lua entra quando precisar.
- Controle fino de latência / mixagem por amostra própria da engine.

## Decisions

### D1 — SPI server-style (`tree.audio`), não Node

`SceneTree.audio: AudioBackend?` nullable, espelhando `textMeasurer`. Consumo: `node.tree.audio?.play(sound)`.

- **Por quê**: SFX nasce de signal handlers (`_on_body_entered`) onde já se tem `self.tree`. Um `tree.audio.play(...)` de uma linha é o caminho mais curto e didático. Não cria ciclo de vida de node novo, hit-test, nem serialização.
- **Alternativa rejeitada**: `AudioStreamPlayer : Node` (Godot-style). Casa com invariante #1 e seria ergonômico para áudio configurado em cena, mas é escopo grande (Node, autoplay, persistência `@Inspect`, binding de scripting do node) para o ganho de um v1 de SFX. Fica registrado como caminho natural de uma change futura — a SPI server-style **não** o impede (um `AudioStreamPlayer` futuro chamaria `tree.audio` por baixo).

### D2 — Handle pré-decodificado (`load(path) -> Sound`), decode fora do hot path

A SPI tem dois passos: `load(path: String): Sound` decodifica uma vez; `play(sound, volume)` dispara. `Sound` é um **handle opaco** (interface-marcador em `:engine`); a impl concreta guarda o PCM já decodificado + `AudioFormat`.

- **Por quê**: re-disparo rápido (rebatida) não pode pagar I/O+decode por play. Pré-carregar no `_ready` e guardar o handle em `self` é o padrão Godot (`preload`). Separar `load`/`play` também torna o custo explícito e ensinável.
- **Alternativa rejeitada**: `play(path)` com cache interno por path (uma linha só). Mais ergonômico, mas esconde o custo do primeiro disparo (decode lazy no hot path) e acopla o backend a uma estratégia de cache. `play(bytes)` foi rejeitado por empurrar I/O e parsing de container pro script.

### D3 — Módulo independente `:engine-audio-javasound` (Opção B), não dentro do render

Backend mora em módulo próprio, **não** dentro de `:engine-skiko`/`:engine-lwjgl`.

- **Por quê**: `:engine-skiko` é Skia (gráficos) — não tem nada de áudio; embutir áudio nele mistura eixos. Um módulo separado prova a ortogonalidade: `SkikoHost` e `LwjglHost` dependem do **mesmo** `:engine-audio-javasound`. Didaticamente honesto.
- **Alternativa rejeitada**: áudio por backend de render (`SkikoAudio` em `:engine-skiko`, `LwjglAudio` via OpenAL em `:engine-lwjgl`). Menos módulos e o host único já wira tudo, mas acopla áudio ao render e duplicaria o decode de WAV. OpenAL (via `org.lwjgl.openal`) seria mais capaz para posicional futuro, mas arrastaria `org.lwjgl.*` pro caminho de áudio e mataria a independência de host — reconsiderar só quando posicional 2D virar requisito.

### D4 — `javax.sound.sampled`, WAV-only no v1

Decode e playback via `javax.sound.sampled` (parte do JDK). Formato suportado: **WAV/PCM** (o `AudioSystem` do JDK lê WAV/AIFF/AU nativamente, sem SPI extra).

- **Por quê**: zero dependência nova de terceiro; mantém a fundação limpa. OGG/MP3 exigiriam um decoder externo (Vorbis SPI etc.) — escopo de change futura.
- **Risco coberto em D6** (concorrência do `Clip`).

### D5 — Resolução de asset por classpath, convenção de path do bundle

`load(path)` resolve o recurso via classloader (`getResourceAsStream`), com path no estilo dos assets existentes (`"pong/sfx/blip.wav"`). O módulo de áudio **não** depende de `:engine-bundle`.

- **Por quê**: todos os assets do projeto (scene.json, scripts) já são resources de classpath; áudio segue a mesma convenção sem inventar um VFS. Path ausente/ilegível ⇒ falha fast (coerente com o fail-fast do scripting).

### D6 — Uma linha nativa nova por `play` (vozes sobrepostas)

Cada `play` abre um `Clip` (ou `SourceDataLine`) novo a partir do PCM cacheado no `Sound`, inicia, e libera a linha ao terminar (via listener `LineEvent.Type.STOP` → `close()`).

- **Por quê**: um único `Clip` **não** toca a si mesmo de forma sobreposta — re-`start()` durante a reprodução corta/reinicia. Para rebatidas rápidas no Pong, cada disparo precisa da própria linha. O PCM é compartilhado (lido uma vez); só a linha de saída é por-voz.
- **Trade-off**: abrir linha por disparo tem custo; aceitável para SFX esporádico. Sem pool no v1 (simplicidade); se virar gargalo, um pool de linhas é otimização interna que não muda a SPI.

### D7 — Lifecycle: `tree.stop()` → `audio?.dispose()`

`SceneTree.stop()` chama `audio?.dispose()`, que fecha quaisquer linhas vivas e libera mixer/recursos nativos.

- **Por quê**: simetria com o teardown que os hosts já fazem (`tree.stop()` no `windowClosed`/fim do loop). Evita vazar linhas de áudio nativas ao fechar a janela.

### D8 — Binding de scripting automático via `allowAllAccess`

Python alcança `self.tree.audio.load(...)`/`play(...)` **sem glue novo**: o `PythonScriptHost` cria o `Context` com `allowAllAccess(true)`, então membros públicos de `SceneTree`/`AudioBackend`/`Sound` são expostos por interop. O contrato fica registrado no delta de `pong-sample`.

- **Por quê**: zero código de binding; `AudioBackend`/`Sound` só precisam ter membros públicos. Stubs `.pyi` podem ganhar a superfície depois (não bloqueante para o v1).

## Risks / Trade-offs

- **`javax.sound` indisponível/headless (CI, container sem placa de som)** → `tree.audio` é nullable e os hosts toleram falha de init do backend (log + segue sem áudio); testes nunca dependem de som real. `node.tree.audio?.play(...)` no-op quando `null`.
- **Latência do `javax.sound` (abrir `Clip` por disparo tem overhead, ~ms)** → aceitável para SFX de jogo didático; se incomodar, pool de linhas é otimização interna sem mudança de SPI (D6).
- **Vazamento de linhas nativas se um som nunca dispara `STOP`** → liberação amarrada a `LineEvent.STOP` + `dispose()` global no `tree.stop()` (D7) como rede de segurança.
- **WAV-only frustra quem quer OGG** → decisão consciente de escopo (D4); a SPI `load(path)` não muda quando OGG entrar — só o backend ganha um decoder.
- **`Sound` como interface-marcador opaco** pode tentar futuros a vazar detalhes do backend (formato, bytes) na interface → manter `Sound` sem membros (puro handle); qualquer query de duração/formato entra como método do `AudioBackend`, não do handle.
- **Acoplamento de `play(sound)` a um `Sound` de outro backend** (se houvessem 2 backends) → v1 tem um backend só; quando houver mais, `play` valida o tipo concreto e falha fast em handle estrangeiro.
