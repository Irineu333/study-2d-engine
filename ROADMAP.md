# Roadmap

Plano de evolução do `nengine`. **Active** = changes OpenSpec em andamento; **Planned** = intenção firmada mas sem change aberta ainda. Histórico de changes concluídas vive em [`openspec/changes/archive/`](./openspec/changes/archive/) — não duplicar aqui.

## Active

| Change              | Resumo                                                                                                                                                                                              |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bundle-tictactoe`  | Migra `:games:tictactoe` para o pipeline bundle (`scene.json` + Python), apagando os Nodes Kotlin do jogo. Prova viva de que `BundleLoader` + `PythonScriptHost` rodam idênticos no backend Compose. |
| `game-snake`        | Jogo Snake como `:games:snake`. Validador de gameplay discreto/grid-based, mutação dinâmica do scene graph via script, wraparound em `Camera2D.bounds`, e da ponte Kotlin Signal → Python (consome `Timer.timeout`). |
| `add-rigid-body-2d` | `RigidBody2D` como terceiro `PhysicsBody2D`, integrado pela engine: integrator + impulse solver (linear+angular+Coulomb), gravity global, diagnósticos `Σp`/`ΣL`/`ΣKE` com overlay F3, migrando demos 4/6 pro novo path. |

## Planned

| Change           | Resumo                                                                                                                                                                                                                                                                                                                                                                                   |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `lua-scripting`  | Segunda implementação do `ScriptHost` SPI usando Lua. Prova que a SPI é genuinamente agnóstica de linguagem (não acomodada ao GraalPy); bundle de exemplo com `.lua` carregado lado-a-lado com `.py`.                                                                                                                                                                                    |
| `game-asteroids` | Validador da `collision-overhaul` + integração com a fundação Godot-style: `Area2D` para balas, `CharacterBody2D` para nave/asteróides, `CollisionShape2D` + `CircleShape2D`, múltiplas shapes por objeto, signal cascade (asteróide quebra em pedaços), `Camera2D.bounds` para wrap-around, `Polygon2D`/`Line2D` wireframe; vai puxar `Renderer.withTransform` quando for implementado. |
| `game-billiards` | Jogo de bilhar como validador do impulso elástico + transferência angular da `TumblingSwarm`: 16 `CharacterBody2D` circulares numa mesa de 4 `StaticBody2D`, taco aplicando impulso linear no clique, 6 caçapas como `Area2D` removendo bolas no enter; exercita pair-hit simétrico, fricção Coulomb contra as tabelas e regras de turno em script Python.                               |
| `editor-visual`  | Vai dirigir decisões sobre serialização de cena, inspetor de propriedades e potencialmente composição.                                                                                                                                                                                                                                                                                   |

## Como manter

- **Nova change criada** (`/opsx:propose <name>`) → adiciona linha em **Active** com resumo de uma frase.
- **Change archived** (`/opsx:archive <name>`) → remove a linha daqui. O histórico passa a viver em `openspec/changes/archive/<date>-<name>/`.
- **Ideia firmada sem change ainda** → linha em **Planned**. Quando virar change, promove para Active.
- Resumos devem caber numa linha do tipo "o que muda + por quê", não a lista completa de tasks.
