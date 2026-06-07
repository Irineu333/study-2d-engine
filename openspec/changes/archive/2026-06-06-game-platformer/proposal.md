## Why

As três changes anteriores deram à engine os tijolos de um jogo 2D rasterizado: desenhar texturas (`texture-rendering`), animar sprites (`sprite-animation`) e montar terreno com tiles (`tilemap-visual`). Falta a **prova viva** de que esses tijolos compõem um jogo de plataforma de verdade — do mesmo jeito que `:games:pong` validou a fundação de física/scripting e `:games:snake` validou gameplay tick-based.

Esta change cria **`:games:platformer`**: um platformer mínimo com céu, terreno, e um personagem jogável (Pink Man) com **gravidade, pulo e movimentação horizontal**, rodando em Skiko com scripting Lua. É o validador que fecha a cadeia e o entregável que o usuário pediu.

Quarta e última change. **Depende das três anteriores** aplicadas.

## What Changes

- **Novo módulo `:games:platformer`** (Skiko + Lua), espelhando a estrutura de `:games:tictactoe`: `build.gradle.kts`, `Main.kt` (carrega o bundle, envolve em `SceneTree`, roda via `SkikoHost`), e um bundle em `src/main/resources/platformer/` (`scene.json` + `scripts/`).
- **Assets importados** de `/Users/aiqfome/Documents/assets/Pixel Adventure 1` para `games/platformer/src/main/resources/platformer/assets/` — só os que o jogo usa: o personagem (Pink Man: Idle, Run, Jump, Fall), o atlas de terreno (`Terrain (16x16)`), e um fundo de céu (`Background/*` ou cor sólida).
- **Cena (`scene.json`) declarativa** monta:
  - **Céu**: `ColorRect` de fundo (ou `Sprite2D`/background tiled) cobrindo o design-space.
  - **Terreno (visual)**: um `TileMap` com o atlas de terreno desenhando o chão e plataformas.
  - **Chão (colisor)**: poucos `StaticBody2D` + `RectangleShape2D` posicionados à mão, alinhados ao terreno desenhado (visual-only do tilemap, conforme `tilemap-visual`).
  - **Player**: um `CharacterBody2D` com um `CollisionShape2D` (retângulo) e um `AnimatedSprite2D` filho (sheets do Pink Man), dirigido por `scripts/player.lua`.
  - **`Camera2D`** enquadrando a cena em design-space pequeno (ex.: 320x180) para a pixel-art escalar nítida.
- **`scripts/player.lua`** implementa a física de plataforma usando o que a engine já oferece (invariante #3, `CharacterBody2D`):
  - **Gravidade**: em `_physics_process(dt)`, `velocity.y += GRAVITY * dt`.
  - **Movimentação**: lê `tree.input` (esquerda/direita) e seta `velocity.x`.
  - **Pulo**: ao detectar chão e tecla de pulo, seta `velocity.y` negativo.
  - **Movimento + colisão**: `move_and_collide(velocity * dt)`; reflete/zera componente ao bater (chão/parede), detecta "no chão" pelo contato com normal pra cima.
  - **Animação + flip**: troca o sheet do `AnimatedSprite2D` por estado (idle/run/jump/fall) e ajusta `flip_h` pela direção.
- **Registro do módulo** em `settings.gradle.kts` e na tabela **Games** do `CLAUDE.md`; linha no `ROADMAP.md`.

Sem breaking changes na engine: esta change só **consome** as capacidades já criadas e adiciona um módulo de jogo.

## Capabilities

### New Capabilities
- `platformer-sample`: o módulo `:games:platformer` (Skiko + Lua) — composição de cena (céu, `TileMap` de terreno, `StaticBody2D` de chão à mão, player `CharacterBody2D` + `AnimatedSprite2D`, `Camera2D`), e o `player.lua` com gravidade, movimentação horizontal, pulo e troca de animação/flip via `move_and_collide`.

### Modified Capabilities
_(nenhuma — a engine não muda; só ganha um consumidor.)_

## Impact

- **Código novo**: módulo `:games:platformer` (`build.gradle.kts`, `Main.kt`, `scene.json`, `scripts/player.lua`), assets PNG importados em `games/platformer/src/main/resources/platformer/assets/`.
- **Código tocado**: `settings.gradle.kts` (`include(":games:platformer")`), `CLAUDE.md` (tabela Games), `ROADMAP.md`.
- **Dependências**: `:engine`, `:engine-skiko`, `:engine-bundle`, `:engine-bundle-lua` (como os outros jogos Lua). Pré-requisito: changes `texture-rendering`, `sprite-animation`, `tilemap-visual` aplicadas.
- **Invariantes**: respeita #1 (gameplay por herança/Node + script Godot-style), #3 (player é `CharacterBody2D` com `move_and_collide`, sem física automática; chão sólido via `StaticBody2D`/`RectangleShape2D`), #4 (Skiko backend default; nenhuma SPI nova).
- **Docs**: `CLAUDE.md` Games table ganha a linha do platformer; `ROADMAP.md`.
