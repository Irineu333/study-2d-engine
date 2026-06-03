## Why

`CharacterBody2D.moveAndCollide` **descarta o motion pretendido num overlap inicial** (`toi == 0`): a posição avança só por `motion * 0 + depenetration`, jogando fora o deslocamento mesmo quando ele aponta para **fora** do colisor. Consequência: dois `CharacterBody2D` presos num overlap marginal sustentado — um deles re-pressionando o contato a cada frame — nunca conseguem aplicar sua velocidade de separação e **congelam no lugar**. Foi exatamente o trap da quina do Pong (`fix-pong-paddle-as-characterbody`), contornado lá por uma **gambiarra de jogo**: o `ball.py` teleporta a bola na mão (`self.position = ...`) quando detecta `remainder ≈ motion`. Para uma engine didática ("não pode ensinar o uso errado de nó"), isso é um footgun: **todo** jogo que mova um corpo kinematic pressionado contra outro vai precisar do mesmo teleporte. A raiz é da engine, não do jogo.

## What Changes

- **`moveAndCollide` ganha recovery de overlap inicial.** Num starting overlap (`toi == 0`), depois de aplicar a depenetração, o método **re-varre o motion restante a partir da posição já depenetrada** (até um número pequeno e fixo de iterações), avançando o corpo pela parte do motion que não re-entra num colisor. Um corpo sempre consegue **deixar um overlap que está tentando largar** — alinhado ao comportamento do Godot (recovery + slide). Quando o motion aponta para dentro do colisor (ou é zero), o comportamento atual se preserva: só depenetra, sem progresso indevido.
- **`remainder` reflete o motion realmente não-consumido** após o recovery (o que sobra depois de varrer a partir da posição depenetrada), em vez de `Vec2.ZERO` fixo no caso `toi == 0`. **BREAKING (sutil)**: o cenário atual "starting-overlap ⇒ `remainder == Vec2.ZERO`" muda; código que dependa desse zero exato é revisado.
- **Remove a gambiarra do `ball.py`.** O "nudge de escape" (escrita direta de `self.position` em starting overlap) é deletado; o `moveAndCollide` passa a carregar a bola para fora sozinho. Validado com o mesmo harness headless de ablation usado na change do Pong: a remoção mantém **0 traps**.
- **Sem mudança de scene-graph nem de tipos de backend.** Refina a SPI `moveAndCollide` dentro de `:engine`; o invariante #3 (colisão via `CollisionObject2D`/`PhysicsSystem`) permanece intacto.

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova. -->

### Modified Capabilities
- `kinematic-move-and-collide`: a requirement "CharacterBody2D exposes moveAndCollide" passa a exigir, em `toi == 0`, que o corpo avance pela depenetração **e** pelo componente de motion que separa (via re-varredura limitada a partir da posição depenetrada); `remainder` reflete o motion não-consumido pós-recovery. O cenário de starting-overlap é reescrito de acordo, e um novo cenário garante que um corpo re-pressionado por um peer ainda escapa.
- `pong-sample`: a "Ball physics" deixa de exigir o nudge de escape (escrita direta de posição em starting overlap) no `ball.py`; o classificador face-vs-edge por x e o `h_sign` por lado permanecem.

## Impact

- **Engine (`:engine`)**: `CharacterBody2D.moveAndCollide` (loop de recovery), possivelmente helper compartilhado com o TOI loop de `RigidBody2D` em `PhysicsSystem`. `SweepResult`/`KinematicCollision2D` provavelmente inalterados em forma; muda o uso.
- **Testes da engine**: `CharacterBody2DTest` (cenário de starting-overlap reescrito + novo cenário de escape sob re-pressão), `BehavioralSweepTest` (harness de spawn-overlap já existe — estender para o caso de re-pressão sustentada), suíte de física verde.
- **Demos Kotlin** (`RotatingBoxDemo`, `TumblingSwarmDemo`, `CollisionStressDemo`, `BoundaryWalls`): usam `moveAndCollide`; conferir que o recovery não altera trajetórias esperadas (são contatos limpos, `toi > 0`, fora do caminho do recovery).
- **Jogo Pong** (`games/pong/.../ball.py`): remove o nudge de escape; revalida quina/lateral com 0 traps.
- **Risco**: localizado no caminho `toi == 0` do `moveAndCollide`. O caminho comum (`toi > 0`, contato limpo) é inalterado. O loop de recovery é limitado por um teto fixo de iterações (sem risco de loop infinito).
