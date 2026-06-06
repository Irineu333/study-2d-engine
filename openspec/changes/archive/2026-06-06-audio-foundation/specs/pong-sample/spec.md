## ADDED Requirements

### Requirement: Pong plays SFX through the audio backend from Python scripts

O módulo `:games:pong` SHALL servir como sentinela viva da capability `audio`, tocando efeitos sonoros a partir dos scripts Python via `self.tree.audio`. O bundle SHALL conter ao menos um asset WAV sob `src/main/resources/pong/sfx/` (ex.: rebatida na raquete e gol). Cada script que toca som SHALL pré-carregar o handle uma vez em `_ready` (`self._sfx = self.tree.audio.load("pong/sfx/<file>.wav")` quando `self.tree.audio` não é `None`) e dispará-lo no signal handler de gameplay correspondente (`_on_body_entered` para rebatida; o handler de gol para ponto), usando o padrão null-safe que vira no-op quando não há backend de áudio. O acesso SHALL funcionar sem glue de binding novo — `self.tree.audio` é alcançável por interop GraalPy (`allowAllAccess`). Nenhum `.nengine.kts` SHALL ser introduzido; a mudança é restrita aos `.py` de gameplay e aos assets WAV.

#### Scenario: Pong bundle ships a WAV SFX asset

- **WHEN** o conteúdo de `:games:pong/src/main/resources/pong/` é listado
- **THEN** há ao menos um arquivo `.wav` sob `sfx/`

#### Scenario: Pong scripts reach the audio backend via self.tree.audio

- **WHEN** os scripts Python do Pong que tocam som são inspecionados
- **THEN** carregam o som via `self.tree.audio.load("pong/sfx/<file>.wav")` em `_ready`
- **AND** disparam via `self.tree.audio.play(...)` no signal handler de gameplay
- **AND** o acesso é null-safe (no-op quando `self.tree.audio` é `None`), sem código de binding adicional

#### Scenario: Paddle hit emits a sound when audio is available

- **GIVEN** `tree.audio` está setado e a bola colide com uma raquete
- **WHEN** o `_on_body_entered` (ou handler equivalente de rebatida) executa
- **THEN** `tree.audio.play(...)` é invocado com o handle do SFX de rebatida

#### Scenario: Pong runs silently when no audio backend is present

- **GIVEN** `tree.audio` é `None` (ex.: ambiente headless de teste)
- **WHEN** ocorre uma rebatida ou um gol
- **THEN** nenhum erro é lançado e o gameplay segue idêntico
