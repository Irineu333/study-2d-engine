# Roadmap

Plano de evolução do `nengine`. **Active** = changes OpenSpec em andamento; **Planned** = intenção firmada mas sem change aberta ainda. Histórico de changes concluídas vive em [`openspec/changes/archive/`](./openspec/changes/archive/) — não duplicar aqui.

## Active

| Change                     | Resumo                                                                                                                                                                                                                                  |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `collision-overhaul`       | Reescreve colisão no estilo Godot: `CollisionObject2D` ramificado em `Area2D`/`StaticBody2D`/`CharacterBody2D`, `CollisionShape2D` carregando `Shape2D` polimórfica (`Rectangle`/`Circle`), `PhysicsSystem` com eventos entered/exited pareados. |
| `collision-rotated-shapes` | Follow-up que fecha KR2 da `collision-overhaul`: `overlap(RectangleShape2D, RectangleShape2D)` quando `rotation != 0` usa SAT/OBB-vs-OBB exato em vez do AABB-envelope, desbloqueando Demo 5 (RotatingBox) e jogos com retângulos rotacionados. |
| `collision-iterative-resolution` | Follow-up que fecha KR1 da `collision-overhaul`: `PhysicsSystem.step` itera até convergência (re-snapshot + re-dispatch enquanto a set muda) para que pile-ups de 3+ corpos não deixem pares grudados sem evento. Desbloqueia Demo 4 (CollisionStress) sob carga densa. |
| `bundle-tictactoe`         | Migra `:games:tictactoe` para o pipeline bundle (`scene.json` + Python), apagando os Nodes Kotlin do jogo. Prova viva de que `BundleLoader` + `PythonScriptHost` rodam idênticos no backend Compose.                                    |
| `node-timer`               | Adiciona `Timer` Node estilo Godot estendendo `Node` puro (primeiro nó lógico não-visual) com signal `timeout` conectável do Python — primeira ponte de Signal nascido em Kotlin. Pré-requisito de `game-snake`.                       |
| `game-snake`               | Jogo Snake como `:games:snake`. Validador de gameplay discreto/grid-based, mutação dinâmica do scene graph via script, wraparound em `Camera2D.bounds`, e da ponte Kotlin Signal → Python (consome `Timer.timeout`).                    |
| `camera2d-view-transform`  | `Camera2D` vira view transform de verdade: `Renderer` ganha `pushTransform/popTransform`, `Scene.render` projeta `bounds` sobre a surface via `aspectMode` (FIT default), Pong autoria posições no mundo 800×600 e `_layout` morre.    |
| `scene-tree`               | Introduz `SceneTree` em `:engine` como dono da árvore viva (não-Node, não-`@Serializable`), apaga a classe `Scene`, adiciona `Node.tree` cacheado, e migra Pong/TicTacToe/Demos para `Host.run(SceneTree(root = ...))`.                |

## Planned

| Change           | Resumo                                                                                                                                                                                                |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `lua-scripting`               | Segunda implementação do `ScriptHost` SPI usando Lua. Prova que a SPI é genuinamente agnóstica de linguagem (não acomodada ao GraalPy); bundle de exemplo com `.lua` carregado lado-a-lado com `.py`. |
| `game-asteroids`              | Validador da `collision-overhaul` + integração com a fundação Godot-style: `Area2D` para balas, `CharacterBody2D` para nave/asteróides, `CollisionShape2D` + `CircleShape2D`, múltiplas shapes por objeto, signal cascade (asteróide quebra em pedaços), `Camera2D.bounds` para wrap-around, `Polygon2D`/`Line2D` wireframe; vai puxar `Renderer.withTransform` quando for implementado. |
| editor                        | Editor visual estilo Godot. Vai dirigir decisões sobre serialização de cena, inspetor de propriedades e potencialmente composição.                                                                    |

## Como manter

- **Nova change criada** (`/opsx:propose <name>`) → adiciona linha em **Active** com resumo de uma frase.
- **Change archived** (`/opsx:archive <name>`) → remove a linha daqui. O histórico passa a viver em `openspec/changes/archive/<date>-<name>/`.
- **Ideia firmada sem change ainda** → linha em **Planned**. Quando virar change, promove para Active.
- Resumos devem caber numa linha do tipo "o que muda + por quê", não a lista completa de tasks.
