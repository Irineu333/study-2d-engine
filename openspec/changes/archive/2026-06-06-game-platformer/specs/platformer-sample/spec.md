## ADDED Requirements

### Requirement: Platformer is an executable standalone Skiko + Lua module

O projeto SHALL conter um módulo `:games:platformer`, registrado em `settings.gradle.kts`, que roda como aplicação desktop sobre o backend Skiko com scripting Lua, espelhando a estrutura de `:games:tictactoe`. Um `Main.kt` SHALL carregar o bundle (`scene.json` + `scripts/`) via `BundleLoader`, envolver o root num `SceneTree`, e rodar via `SkikoHost`. O módulo SHALL depender de `:engine`, `:engine-skiko`, `:engine-bundle` e `:engine-bundle-lua`.

#### Scenario: Platformer module is registered and runnable

- **WHEN** o projeto é configurado
- **THEN** `:games:platformer` aparece em `settings.gradle.kts`
- **AND** existe um entrypoint `Main.kt` que carrega o bundle e roda via `SkikoHost`

### Requirement: Scene composes sky, tilemap terrain, hand-placed ground colliders, player and camera

A `scene.json` do platformer SHALL compor declarativamente:

- um fundo de **céu** (`ColorRect` cobrindo o design-space, ou um `Sprite2D`/background);
- um **`TileMap`** desenhando o terreno a partir de um atlas de tiles importado;
- um ou mais **`StaticBody2D`** com `RectangleShape2D` (`CollisionShape2D`) posicionados à mão, formando o chão/plataformas sólidos alinhados ao terreno desenhado (o `TileMap` é visual-only);
- o **player**: um `CharacterBody2D` com um `CollisionShape2D` (retângulo) e um `AnimatedSprite2D` filho (sheets do personagem), com `scripts/player.lua` anexado;
- uma **`Camera2D`** corrente enquadrando a cena num design-space pequeno (pixel-art escala nítida via nearest-neighbor).

#### Scenario: Scene contains the platformer structural nodes

- **WHEN** a `scene.json` do platformer é carregada
- **THEN** ela contém um `TileMap` (terreno visual), ao menos um `StaticBody2D` com `RectangleShape2D` (chão sólido), e um `CharacterBody2D` (player) com um `AnimatedSprite2D` filho e uma `Camera2D` corrente

#### Scenario: Terrain is drawn by the tilemap, collision is the hand-placed bodies

- **WHEN** o jogo roda
- **THEN** o terreno visível vem do `TileMap`
- **AND** o player só colide com os `StaticBody2D` posicionados à mão (o `TileMap` não participa da física)

### Requirement: player.lua implements gravity, horizontal movement and jump via move_and_collide

`scripts/player.lua` SHALL implementar a física de plataforma usando `CharacterBody2D` (sem física automática). Em `_physics_process(dt)` SHALL:

- aplicar **gravidade**: `velocity.y` aumenta por `GRAVITY * dt` (y para baixo);
- ler `tree.input` para **movimentação horizontal**: teclas esquerda/direita setam `velocity.x` (e zeram quando nenhuma é pressionada);
- **pular**: quando o player está no chão e a tecla de pulo é pressionada, setar `velocity.y` para um valor negativo (impulso de subida);
- mover via `move_and_collide(velocity * dt)`, **zerando** o componente de `velocity` na direção do contato (chão zera `velocity.y` na aterrissagem; parede zera `velocity.x`);
- determinar **estar no chão** pelo contato cujo normal aponta para cima, habilitando o próximo pulo.

#### Scenario: Gravity pulls the player down when airborne

- **WHEN** o player está no ar e nenhuma superfície o sustenta
- **THEN** seu `velocity.y` cresce a cada `_physics_process` e ele desce até colidir com um chão

#### Scenario: Player lands and stops on the ground

- **WHEN** o player caindo encontra um `StaticBody2D` de chão
- **THEN** `move_and_collide` para o movimento no contato e `velocity.y` é zerado (não atravessa o chão)

#### Scenario: Player moves horizontally with input

- **WHEN** a tecla de direita (ou esquerda) é mantida pressionada
- **THEN** o player se move horizontalmente nessa direção
- **AND** para de mover no eixo x ao soltar a tecla

#### Scenario: Player jumps only when grounded

- **WHEN** o player está no chão e a tecla de pulo é pressionada
- **THEN** ele ganha velocidade vertical para cima e sobe
- **AND** pressionar pulo no ar não inicia um novo pulo (sem double jump no v1)

#### Scenario: Player stops at walls

- **WHEN** o player se move horizontalmente contra um `StaticBody2D` vertical
- **THEN** `move_and_collide` o para no contato e ele não atravessa a parede

### Requirement: Player animation reflects movement state with horizontal flip

`player.lua` SHALL atualizar o `AnimatedSprite2D` filho conforme o estado de movimento: parado ⇒ animação de **idle**; movendo no chão ⇒ **run**; subindo ⇒ **jump**; caindo ⇒ **fall** (trocando `texture_path` + `frame_count` do sheet correspondente). O `flip_h` SHALL refletir a direção horizontal (olhando para o lado do movimento).

#### Scenario: Run animation plays while moving on the ground

- **WHEN** o player se move horizontalmente no chão
- **THEN** o `AnimatedSprite2D` exibe a animação de corrida (sheet run, frames avançando)

#### Scenario: Sprite faces the movement direction

- **WHEN** o player se move para a esquerda e depois para a direita
- **THEN** `flip_h` alterna de modo que o personagem sempre olha para o lado em que anda

#### Scenario: Jump and fall animations reflect vertical motion

- **WHEN** o player sobe (logo após pular) e depois cai
- **THEN** o sprite mostra a animação de jump na subida e de fall na descida
