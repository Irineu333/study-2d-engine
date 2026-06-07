## Context

A engine, após as três changes anteriores, sabe desenhar texturas, animar sprites e montar tilemaps — e já sabia fazer física de personagem controlado (`CharacterBody2D` + `moveAndCollide`, invariante #3). O padrão de jogo Lua está consagrado em `:games:tictactoe`: módulo Skiko, `Main.kt` que carrega um bundle (`scene.json` + `scripts/*.lua`) e roda via `SkikoHost`, com tipos de node referenciados por FQN na cena e hooks Godot-style (`_ready`, `_physics_process`, `_process`) nos scripts.

Um platformer mínimo é a composição natural disso: cena declarativa para o estático (céu, terreno, chão, câmera) e um único script para o dinâmico (o player).

## Goals / Non-Goals

**Goals:**
- Um platformer jogável: anda esquerda/direita, cai por gravidade, pula, para no chão e nas paredes.
- Personagem animado (idle/run/jump/fall) com flip por direção, usando `AnimatedSprite2D`.
- Terreno visual via `TileMap` + chão sólido via `StaticBody2D` à mão (consistente com `tilemap-visual`).
- Pixel-art nítida (camera em design-space pequeno, nearest-neighbor da fundação).
- Validar ponta-a-ponta as três changes anteriores num jogo real, em Skiko + Lua.

**Non-Goals:**
- Múltiplas fases, inimigos, IA, dano/vida, coletáveis com pontuação (a fruta pode aparecer como enfeite animado, sem lógica de coleta no v1).
- Colisão gerada do tilemap (o chão é à mão, por `tilemap-visual`).
- Plataformas móveis, one-way platforms, ladders, wall-jump, double-jump (o sheet de Double/Wall Jump existe, mas o v1 mira pulo simples).
- Backend LWJGL para este jogo (default é Skiko; o demos já é o sentinela cross-backend das capacidades de render).
- Menu, HUD de jogo, áudio (poderia usar `tree.audio`, mas é opcional e fora do escopo mínimo).

## Decisions

### D1 — Estático na cena, dinâmico no script (um único `player.lua`)

`scene.json` declara céu, `TileMap`, `StaticBody2D`s de chão, `Camera2D`, e a árvore do player (`CharacterBody2D` > `CollisionShape2D` + `AnimatedSprite2D`). Só o player tem script.

- **Por quê**: maximiza o que a fundação declarativa já entrega. Toda a parte visual e os colisores são dados; a única lógica é o movimento do player. Espelha como tictactoe põe a grade na cena e a lógica num script.

### D2 — Player é `CharacterBody2D` com `move_and_collide` (invariante #3)

`player.lua` em `_physics_process(dt)`: aplica gravidade a `velocity.y`, lê input para `velocity.x`, pula setando `velocity.y`, e chama `move_and_collide(velocity * dt)`. Ao bater, zera o componente normal (chão para a queda, parede para o x) e usa o contato com normal "pra cima" para marcar `on_floor`.

- **Por quê**: é literalmente o caso de uso que o invariante #3 descreve para `CharacterBody2D` ("player platformer"). Sem física automática: o script é dono da velocidade. `moveAndCollide` dá o sweep CCD-correto (sem tunelar em quedas rápidas).
- **Detecção de chão**: após `move_and_collide`, se houve contato com normal apontando para cima (y negativo no sistema y-down), `on_floor = true`. Pulo só quando `on_floor`.
- **Deslizar**: opcionalmente, com o `remainder` do contato, um segundo `move_and_collide` desliza ao longo da superfície (Godot-style). O v1 pode simplesmente zerar o componente e seguir — mínimo viável.

### D3 — Terreno visual (`TileMap`) + chão sólido (`StaticBody2D`) separados

O `TileMap` desenha o terreno; o sólido são `StaticBody2D` + `RectangleShape2D` posicionados sobre as faixas de chão/plataforma.

- **Por quê**: `tilemap-visual` é, por decisão, visual-only. A cena alinha à mão poucos retângulos colisores ao terreno desenhado (ex.: um retângulo largo no chão, um por plataforma). A cena documenta a correspondência.
- **Trade-off**: divergência visual↔colisor é possível; aceitável num v1 com poucos colisores. Auto-geração futura (extensão de `tilemap-visual`) elimina o ajuste manual.

### D4 — Câmera em design-space pequeno para pixel-art nítida

`Camera2D.bounds` num design-space tipo 320x180 (16:9 baixo). Como `designSize` deriva do `bounds` da câmera corrente (invariante #6), o HUD/stretch fica estável e os tiles de 16px escalam por inteiros, mantendo o nearest-neighbor nítido.

- **Por quê**: pixel-art de 16/32px num design grande ficaria minúscula; o zoom natural vem de um design-space pequeno mapeado na janela pelo stretch (FIT). Sem escala nos nodes — só a câmera.

### D5 — Animação por estado, trocando sheet do `AnimatedSprite2D`

`player.lua` mantém um mapa estado→(sheet, frameCount) (idle 11, run 12, jump 1–N, fall 1–N) e, a cada frame, escolhe o estado por `velocity`/`on_floor` e aplica `texture_path`+`frame_count` no `AnimatedSprite2D` filho; ajusta `flip_h` por `sign(velocity.x)`.

- **Por quê**: é o que `sprite-animation` v1 oferece (troca por path+count; sem catálogo nomeado). Encapsular o mapa no script é suficiente e didático. Quando `SpriteFrames` nomeado entrar, o player troca por nome.

### D6 — Skiko + Lua, estrutura de bundle igual tictactoe

Módulo `:games:platformer` com `Main.kt` (BundleLoader → SceneTree → SkikoHost) e bundle Lua. Tipos por FQN na `scene.json`; nodes novos já registrados no host Lua pelas changes anteriores.

- **Por quê**: reusa o pipeline de jogo Lua já validado; nenhuma infra nova. O usuário escolheu Lua.

## Risks / Trade-offs

- **Sentir do pulo/gravidade (game feel)** → constantes (GRAVITY, JUMP_SPEED, MOVE_SPEED) ajustadas por iteração manual; documentar valores iniciais. Não é correção de engine, é tuning de jogo.
- **`move_and_collide` + detecção de chão jittery** (micro-quedas alternando `on_floor`) → usar um pequeno `snap`/tolerância ou checar contato logo após o movimento; manter simples no v1 e ajustar se tremular.
- **Alinhar colisor à mão com o tilemap** → erro de pixel possível; manter o layout simples (chão reto + 1–2 plataformas) para o alinhamento ser trivial e verificável a olho.
- **Coordenadas y-down vs sinais de gravidade/normal** → fixar a convenção (y para baixo, gravidade `+y`, chão tem normal `-y`) e testar pulo/queda cedo.
- **Assets faltando frames de estado** (Jump/Fall do Pink Man têm 1 frame) → tratar `frameCount = 1` como estático (o `AnimatedSprite2D` não avança com `frameCount <= 1`); ok.
