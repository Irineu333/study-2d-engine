## 1. SPI de áudio em :engine

- [ ] 1.1 Criar pacote `com.neoutils.engine.audio` em `:engine` com `interface Sound` (handle opaco, sem membros) e KDoc explicando que é um handle de áudio pré-decodificado.
- [ ] 1.2 Criar `interface AudioBackend` em `com.neoutils.engine.audio` com `load(path: String): Sound`, `play(sound: Sound, volume: Float = 1f)`, `dispose()` e KDoc por método.
- [ ] 1.3 Adicionar `var audio: AudioBackend? = null` em `SceneTree` (`@Volatile`/anotação coerente com `input`/`textMeasurer`), espelhando o padrão de `textMeasurer`.
- [ ] 1.4 Em `SceneTree.stop()`, chamar `audio?.dispose()` exatamente uma vez no teardown.
- [ ] 1.5 Teste unitário em `:engine`: `tree.audio` default `null`; `tree.audio?.play(...)` no-op sem lançar; `stop()` chama `dispose()` exatamente uma vez (fake `AudioBackend`).

## 2. Módulo :engine-audio-javasound

- [ ] 2.1 Criar diretório do módulo `engine-audio-javasound/` com `build.gradle.kts` dependendo apenas de `:engine` (sem `:engine-skiko`/`:engine-lwjgl`, sem libs de áudio de terceiro).
- [ ] 2.2 Registrar `include(":engine-audio-javasound")` em `settings.gradle.kts`.
- [ ] 2.3 Implementar a impl concreta de `Sound` (interna ao módulo) guardando PCM decodificado + `AudioFormat`.
- [ ] 2.4 Implementar `class JavaSoundAudio : AudioBackend`: `load(path)` resolve via classpath (`getResourceAsStream`), lê WAV via `AudioSystem.getAudioInputStream`, materializa PCM em memória, fecha o stream; falha fast (exceção descritiva) em path ausente/ilegível/formato não suportado.
- [ ] 2.5 Implementar `play(sound, volume)`: abre uma linha de saída nova a partir do PCM compartilhado, aplica ganho via `FloatControl.Type.MASTER_GAIN` a partir de `volume`, inicia (fire-and-forget) e fecha a linha no `LineEvent.Type.STOP`; suporta vozes sobrepostas do mesmo handle.
- [ ] 2.6 Implementar `dispose()`: fecha todas as linhas vivas e libera recursos nativos; tolerar dispose duplo.
- [ ] 2.7 Teste no módulo: `load` de um WAV de fixture devolve handle reutilizável; `load` de path inexistente lança; dois `play` rápidos do mesmo handle não lançam e abrem duas linhas (vozes sobrepostas).

## 3. Wiring nos hosts

- [ ] 3.1 `:engine-skiko` (e/ou `:engine-lwjgl`) declarar dependência em `:engine-audio-javasound` no `build.gradle.kts`.
- [ ] 3.2 `SkikoHost.run`: setar `tree.audio = JavaSoundAudio()` antes do primeiro frame, ao lado de `tree.textMeasurer`; tolerar falha de init (log + segue com `null`).
- [ ] 3.3 `LwjglHost.run`: setar `tree.audio = JavaSoundAudio()` no passo de startup (após `tree.debugHudKey`), com a mesma tolerância a falha de init.
- [ ] 3.4 Confirmar que `tree.stop()` (já chamado no teardown de ambos os hosts) dispõe o áudio — sem código de dispose adicional no host.

## 4. Sentinela viva: SFX no Pong (Python)

- [ ] 4.1 Adicionar asset(s) WAV em `games/pong/src/main/resources/pong/sfx/` (rebatida e gol).
- [ ] 4.2 Garantir que `:games:pong` tenha o `:engine-audio-javasound` no classpath (via host ou dependência direta).
- [ ] 4.3 No script da bola/raquete: em `_ready`, `self._hit_sfx = self.tree.audio.load("pong/sfx/<file>.wav")` (null-safe); no `_on_body_entered` da rebatida, `self.tree.audio.play(self._hit_sfx)` null-safe.
- [ ] 4.4 No fluxo de gol (`goal.py`/handler de ponto): pré-carregar e tocar o SFX de gol via `self.tree.audio`, null-safe.
- [ ] 4.5 Rodar o Pong manualmente e confirmar que rebatida e gol soam, e que rebatidas rápidas se sobrepõem sem cortar.

## 5. Verificação e docs

- [ ] 5.1 Teste/asserção de invariante: nenhum arquivo de `:engine` importa `javax.sound`/`org.lwjgl.openal`; `:engine` não depende de `:engine-audio-javasound`.
- [ ] 5.2 Confirmar suíte verde (`:engine`, `:engine-audio-javasound`, `:engine-skiko`, `:games:pong`) e que testes headless do Pong seguem passando com `tree.audio == null`.
- [ ] 5.3 Atualizar `CLAUDE.md`: linha de `:engine-audio-javasound` na tabela de Module Layout e nota curta sobre a SPI de áudio (`tree.audio`, server-style, espelha `textMeasurer`).
- [ ] 5.4 Atualizar `ROADMAP.md`: mover/registrar `audio-foundation` em Active.
- [ ] 5.5 Rodar `/opsx:verify audio-foundation` e fechar pendências.
