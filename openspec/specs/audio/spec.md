# audio Specification

## Purpose

Saída de som como serviço de `SceneTree` (server-style, não Node/Component): a SPI `AudioBackend` vive em `:engine` (Kotlin puro), injetada pelo host no startup e alcançada por nodes/scripts via `node.tree.audio`. A implementação concreta WAV-only mora em `:engine-audio-javasound` sobre `javax.sound.sampled`.

## Requirements

### Requirement: AudioBackend SPI lives in :engine without native audio dependencies

O módulo `:engine` SHALL declarar a SPI de áudio `AudioBackend` no pacote `com.neoutils.engine.audio`, composta por uma interface `AudioBackend` e um handle opaco `Sound`. A interface `AudioBackend` SHALL expor exatamente três operações:

- `fun load(path: String): Sound` — resolve e decodifica um asset de áudio em um handle reutilizável.
- `fun play(sound: Sound, volume: Float = 1f)` — dispara a reprodução fire-and-forget do handle; `volume` em `[0f, 1f]` escala o ganho (1f = ganho nominal).
- `fun dispose()` — libera todos os recursos nativos vivos.

`Sound` SHALL ser uma interface-marcador **sem membros** (handle opaco): nenhum detalhe de backend (bytes, formato, duração) vaza por ela. `:engine` MUST NOT declarar — direta ou transitivamente — `javax.sound.*`, `org.lwjgl.openal.*` ou qualquer binding de áudio nativo; apenas as interfaces puras.

#### Scenario: Audio SPI is Kotlin-pure in :engine

- **WHEN** o módulo `:engine` é inspecionado
- **THEN** `com.neoutils.engine.audio.AudioBackend` e `com.neoutils.engine.audio.Sound` existem como interfaces
- **AND** nenhum arquivo de `:engine` importa `javax.sound`, `org.lwjgl.openal` ou outro binding de áudio nativo

#### Scenario: Sound handle is opaque

- **WHEN** a interface `Sound` é inspecionada
- **THEN** ela não declara nenhum membro público (sem getters de bytes, formato ou duração)

### Requirement: SceneTree exposes a nullable audio backend disposed on stop

A `SceneTree` SHALL expor um campo `audio: AudioBackend?` (default `null`), injetado pelo host no startup, espelhando o padrão de `textMeasurer`. Nodes e scripts SHALL alcançar o serviço via `node.tree.audio`. Quando `audio` é `null`, a ausência de som MUST ser graciosa: chamadas no padrão `node.tree.audio?.play(...)` são no-op e nenhum erro é lançado. `SceneTree.stop()` SHALL chamar `audio?.dispose()` exatamente uma vez ao encerrar.

#### Scenario: audio field defaults to null and is settable

- **WHEN** uma `SceneTree(root)` é construída sem host
- **THEN** `tree.audio` é `null`
- **AND** o campo aceita atribuição de uma implementação de `AudioBackend`

#### Scenario: Null audio backend is a graceful no-op

- **WHEN** `tree.audio` é `null` e um node executa `tree.audio?.play(sound)`
- **THEN** nenhuma exceção é lançada e nada toca

#### Scenario: stop disposes the audio backend

- **WHEN** `tree.audio` está setado e `tree.stop()` é chamado
- **THEN** `AudioBackend.dispose()` é invocado exatamente uma vez

### Requirement: engine-audio-javasound provides a JDK-pure AudioBackend

O projeto SHALL conter um módulo `:engine-audio-javasound` com uma classe pública `JavaSoundAudio : AudioBackend` implementada sobre `javax.sound.sampled` (parte do JDK). O módulo MUST depender de `:engine` (para a SPI) e MUST NOT depender de nenhum módulo de render (`:engine-skiko`, `:engine-lwjgl`) nem de bibliotecas de áudio de terceiros — é host-agnóstico e roda sob qualquer `GameHost`. `:engine` MUST NOT depender de `:engine-audio-javasound`.

#### Scenario: Module exists and is host-agnostic

- **WHEN** os módulos do projeto são listados
- **THEN** `:engine-audio-javasound` existe com `JavaSoundAudio : AudioBackend`
- **AND** seu `build.gradle.kts` depende de `:engine` e não depende de `:engine-skiko` nem de `:engine-lwjgl`
- **AND** nenhuma dependência de áudio de terceiro é declarada (apenas `javax.sound` do JDK)

#### Scenario: engine does not depend on the audio backend

- **WHEN** o `build.gradle.kts` de `:engine` é inspecionado
- **THEN** não há dependência em `:engine-audio-javasound`

### Requirement: load decodes WAV once into a reusable handle and fails fast

`JavaSoundAudio.load(path)` SHALL resolver o asset via classpath (`getResourceAsStream`) usando a convenção de path dos demais assets do projeto (ex.: `"pong/sfx/blip.wav"`), decodificar o áudio **uma única vez** em PCM mantido no `Sound` retornado, e fechar o stream de origem. O formato suportado no v1 SHALL ser WAV/PCM (lido nativamente pelo `AudioSystem` do JDK). Um path inexistente, ilegível ou em formato não suportado MUST falhar fast com exceção descritiva (não retornar um handle silencioso).

#### Scenario: load returns a reusable handle for a WAV asset

- **WHEN** `load("...valid.wav")` é chamado para um WAV de classpath
- **THEN** retorna um `Sound` não-nulo cujo PCM já está decodificado em memória
- **AND** o mesmo handle pode ser passado a `play` múltiplas vezes sem re-decodificar

#### Scenario: Missing asset fails fast

- **WHEN** `load("does/not/exist.wav")` é chamado
- **THEN** uma exceção descritiva é lançada (não há retorno de handle nulo silencioso)

### Requirement: play is fire-and-forget and supports overlapping voices

`JavaSoundAudio.play(sound, volume)` SHALL iniciar a reprodução e retornar imediatamente (fire-and-forget), sem bloquear o chamador até o fim do som. Cada `play` SHALL abrir uma linha de saída nova a partir do PCM compartilhado no `Sound`, de modo que **disparos sobrepostos do mesmo `Sound` tocam simultaneamente** sem cortar reproduções em andamento. A linha SHALL ser liberada automaticamente ao terminar a reprodução (`LineEvent.Type.STOP`). `volume` SHALL escalar o ganho da reprodução; `dispose()` SHALL fechar quaisquer linhas ainda vivas.

#### Scenario: play returns immediately

- **WHEN** `play(sound)` é chamado com um som de duração não-trivial
- **THEN** a chamada retorna antes do fim da reprodução (não bloqueia)

#### Scenario: Overlapping plays of the same sound do not cut each other

- **WHEN** `play(sound)` é chamado duas vezes em sucessão rápida com o mesmo handle
- **THEN** ambas as vozes tocam (a segunda não interrompe nem reinicia a primeira)

#### Scenario: dispose closes live lines

- **WHEN** há reproduções em andamento e `dispose()` é chamado
- **THEN** todas as linhas de saída são fechadas e os recursos nativos liberados
