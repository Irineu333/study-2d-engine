## 1. Módulo :games:platformer

- [x] 1.1 Criar `games/platformer/build.gradle.kts` dependendo de `:engine`, `:engine-skiko`, `:engine-bundle`, `:engine-bundle-lua` (espelhar `:games:tictactoe`).
- [x] 1.2 Registrar `include(":games:platformer")` em `settings.gradle.kts`.
- [x] 1.3 Criar `Main.kt`: `BundleLoader.fromResources("platformer")` → `SceneTree(root)` → `SkikoHost().run(tree, GameConfig(...))`.

## 2. Importar assets

- [x] 2.1 Importar do Pixel Adventure 1 para `games/platformer/src/main/resources/platformer/assets/characters/`: Pink Man `Idle (32x32).png`, `Run (32x32).png`, `Jump (32x32).png`, `Fall (32x32).png` (renomear sem espaços/parênteses, ex.: `idle.png`).
- [x] 2.2 Importar `Terrain/Terrain (16x16).png` → `platformer/assets/tiles/terrain.png`.
- [x] 2.3 Importar um fundo de céu (`Background/Blue.png` ou similar) → `platformer/assets/bg/sky.png` (ou usar `ColorRect`).
- [x] 2.4 (Opcional) Importar uma fruta animada para enfeite (`Items/Fruits/Apple.png`).

## 3. Cena declarativa (scene.json)

- [x] 3.1 Root + `Camera2D` corrente com `bounds` em design-space pequeno (ex.: 320x180), `aspectMode` FIT.
- [x] 3.2 Céu: `ColorRect` (ou `Sprite2D`/background) cobrindo o design-space, atrás de tudo.
- [x] 3.3 Terreno: `TileMap` com `tileSet` (terrain.png, 16x16) e a grade (`columns`/`rows`/`tiles`) montando chão + 1–2 plataformas.
- [x] 3.4 Chão sólido: `StaticBody2D` + `CollisionShape2D`(`RectangleShape2D`) alinhados às faixas de chão/plataforma do tilemap.
- [x] 3.5 Player: `CharacterBody2D` (`name="Player"`, `script="scripts/player.lua"`) com `CollisionShape2D`(`RectangleShape2D`) e um `AnimatedSprite2D` filho (sheet idle inicial).

## 4. scripts/player.lua

- [x] 4.1 `_ready`: cachear referência ao `AnimatedSprite2D` filho e os pares estado→(sheet, frameCount); constantes GRAVITY/MOVE_SPEED/JUMP_SPEED.
- [x] 4.2 `_physics_process(dt)`: gravidade (`velocity.y += GRAVITY*dt`); input horizontal (esquerda/direita ⇒ `velocity.x`); pulo quando `on_floor` e tecla de pulo.
- [x] 4.3 Movimento: `move_and_collide(velocity * dt)`; zerar componente normal no contato; setar `on_floor` pelo contato com normal pra cima.
- [x] 4.4 Animação: escolher estado (idle/run/jump/fall) por `velocity`/`on_floor` e aplicar `texture_path`+`frame_count`; `flip_h` por `sign(velocity.x)`. **Correção**: as bindings Lua resolvem propriedades pelo nome exato do Kotlin (camelCase, sem conversão snake_case em `LuaReflect`); usar `texturePath`/`frameCount`/`currentFrame`/`flipH` — snake_case cai no fallback `rawset` e não chega ao `AnimatedSprite2D` (trava no idle, sem correr/espelhar).
- [x] 4.5 Tratar `frameCount == 1` (Jump/Fall) como quadro estático.

## 5. Tuning e validação manual

- [x] 5.1 Rodar o jogo (Skiko) e ajustar GRAVITY/JUMP/MOVE para um game feel razoável.
- [x] 5.2 Validar: anda esquerda/direita, cai por gravidade, pula só no chão, para no chão e nas paredes.
- [x] 5.3 Validar: animações trocam por estado e o personagem olha para a direção do movimento.
- [x] 5.4 Validar: pixel-art nítida (nearest), terreno e colisores alinhados.
- [x] 5.5 **Correção** — issue "não consigo pular entre as plataformas flutuantes": o salto `PlatformB → PlatformA` exigia 80px horizontais, acima do alcance do arco de pulo (~70px com `JUMP_SPEED=330`/`GRAVITY=900`/`MOVE_SPEED=95`), tornando-o impossível. `PlatformA` reposicionada de cols 12–14 (x 192) para cols 10–12 (x 160) em `scene.json` — tiles visuais e `StaticBody2D` collider movidos juntos (invariante D3). Vão B→A: 80px → 48px (subida 32px), dentro do arco. Tuning de nível, não de engine (seção *Risks* do `design.md`).

## 6. Docs

- [x] 6.1 Atualizar `CLAUDE.md`: linha de `:games:platformer` na tabela **Games** (Skiko, Lua, função: validador de plataforma — sprites/animação/tilemap + `CharacterBody2D`).
- [x] 6.2 Atualizar `README.md` (se lista demos) e `ROADMAP.md`.
- [x] 6.3 Rodar `/opsx:verify game-platformer` e fechar pendências.
