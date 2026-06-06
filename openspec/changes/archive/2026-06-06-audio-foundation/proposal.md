## Why

A engine não tem **nenhuma** saída de áudio: zero SPI, zero campo na tree, zero asset de som em qualquer jogo. Sem som, o circuito de feedback de gameplay fica pela metade — um Pong que bate na raquete e marca gol em silêncio. Esta change estabelece a **fundação mínima de áudio** (SFX curto, fire-and-forget) seguindo o mesmo molde de SPI de plataforma que `Renderer`/`Input`/`TextMeasurer` já consagraram, abrindo caminho para música e áudio posicional em changes futuras sem refazer a base.

## What Changes

- **Nova SPI de áudio em `:engine`** (Kotlin puro, sem dependência de som): `interface AudioBackend { fun load(path): Sound; fun play(sound, volume); fun dispose() }` e `interface Sound` (handle opaco de áudio decodificado). Server-style: nada de `Component`, nada de Node novo — é um serviço de tree, coerente com o invariante #1.
- **Novo campo `SceneTree.audio: AudioBackend?`** — nullable, injetado pelo host, espelhando exatamente `SceneTree.textMeasurer`. Consumido off-frame por nodes/scripts via `node.tree.audio?.play(...)`. `null` ⇒ no-op (testes/headless silenciosos).
- **Novo módulo `:engine-audio-javasound`** — implementação concreta de `AudioBackend` em JDK puro (`javax.sound.sampled`), **host-agnóstica**: roda sob qualquer `GameHost`. Decodifica WAV uma vez no `load`, guarda PCM no `Sound`, e abre uma linha nova por `play` (permite vozes sobrepostas — rebatidas rápidas não se cortam). Formato **WAV-only** no v1 (decodável só com o JDK, sem dependência nova); OGG/MP3 ficam para change futura.
- **`SkikoHost` e `LwjglHost` passam a wirar `tree.audio`** com uma instância de `JavaSoundAudio` no startup, provando na prática que render e áudio são eixos ortogonais (o mesmo módulo JDK serve os dois backends).
- **`tree.stop()` chama `audio?.dispose()`** — fecha linhas/recursos nativos no encerramento.
- **`:games:pong` vira a sentinela viva do áudio**: ganha asset(s) WAV e dispara SFX de rebatida e de gol via `self.tree.audio` nos scripts Python (binding automático — `allowAllAccess` já expõe membros públicos da tree).

Sem breaking changes: tudo é adição. O campo é nullable e default `null`, então jogos e testes existentes seguem idênticos.

## Capabilities

### New Capabilities
- `audio`: SPI `AudioBackend` + handle `Sound` em `:engine`, campo `SceneTree.audio` e seu lifecycle (`dispose` no `stop`), e o módulo backend `:engine-audio-javasound` (impl JDK pura `javax.sound.sampled`, WAV-only, vozes sobrepostas, resolução de asset por classpath).

### Modified Capabilities
- `skiko-runtime`: `SkikoHost` passa a injetar `tree.audio = JavaSoundAudio()` no startup (novo passo de wiring, ao lado de `tree.input`/`tree.textMeasurer`).
- `lwjgl-runtime`: `LwjglHost` passa a injetar `tree.audio = JavaSoundAudio()` no startup (mesmo wiring host-agnóstico).
- `pong-sample`: Pong passa a tocar SFX (rebatida na raquete, gol) via `self.tree.audio`, exercitando a SPI de áudio ponta-a-ponta a partir de scripts Python.

## Impact

- **Código novo**: módulo `:engine-audio-javasound` (`build.gradle.kts` + `JavaSoundAudio`/`Sound` impl). SPI `AudioBackend`/`Sound` em `:engine` (`com.neoutils.engine.audio.*`). Campo `audio` + dispose em `SceneTree`.
- **Código tocado**: `SkikoHost.run`, `LwjglHost.run` (uma linha de wiring + dependência de módulo nos `build.gradle.kts` dos hosts ou do jogo). Scripts Python do Pong (`ball.py`/`paddle.py`/`goal.py`) + asset(s) WAV em `games/pong/src/main/resources/pong/sfx/`.
- **Dependências**: nenhuma nova de terceiros — `javax.sound.sampled` é parte do JDK. Os módulos de host (`:engine-skiko`, `:engine-lwjgl`) ou `:games:pong` passam a depender de `:engine-audio-javasound`.
- **Invariantes**: respeita #2 (`:engine` só declara interfaces, sem `javax.sound`), #4 (Renderer/Input/GameHost intactos; áudio é SPI nova ortogonal), #1 (serviço server-style, não componente/ECS).
- **Docs**: `CLAUDE.md` (Module Layout ganha `:engine-audio-javasound`; nota sobre a SPI de áudio), `ROADMAP.md` (linha em Active → arquivada no fim).
