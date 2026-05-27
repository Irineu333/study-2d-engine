# Roadmap

Plano de evolução do `nengine`. **Active** = changes OpenSpec em andamento; **Planned** = intenção firmada mas sem change aberta ainda. Histórico de changes concluídas vive em [`openspec/changes/archive/`](./openspec/changes/archive/) — não duplicar aqui.

## Active

| Change              | Resumo                                                                                                                                                                                              |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `game-snake`        | Jogo Snake como `:games:snake`. Validador de gameplay discreto/grid-based, mutação dinâmica do scene graph via script, wraparound em `Camera2D.bounds`, e da ponte Kotlin Signal → Python (consome `Timer.timeout`). |

## Planned

| Change           | Resumo                                                                                                                                                                                                                                                                                                                                                                                   |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `game-asteroids` | Validador da `collision-overhaul` + integração com a fundação Godot-style: `Area2D` para balas, `CharacterBody2D` para nave/asteróides, `CollisionShape2D` + `CircleShape2D`, múltiplas shapes por objeto, signal cascade (asteróide quebra em pedaços), `Camera2D.bounds` para wrap-around, `Polygon2D`/`Line2D` wireframe; vai puxar `Renderer.withTransform` quando for implementado. |
| `game-billiards` | Jogo de bilhar como validador do impulso elástico + transferência angular da `TumblingSwarm`: 16 `CharacterBody2D` circulares numa mesa de 4 `StaticBody2D`, taco aplicando impulso linear no clique, 6 caçapas como `Area2D` removendo bolas no enter; exercita pair-hit simétrico, fricção Coulomb contra as tabelas e regras de turno em script Python.                               |
| `engine-lwjgl`   | Segundo backend experimental de render via LWJGL — sucessor do papel de `:engine-compose` no invariante #4. Sem Skia no caminho; valida que `Renderer`/`Input`/`GameHost` continuam SPIs honestas para um caminho arquiteturalmente distinto.                                                                                                                                            |
| `surface-units-spec` | Formaliza o espaço de coordenadas das SPIs: `tree.size`, `Renderer` e `Input.pointerPosition` em unidades lógicas (= surface AWT); HiDPI absorvido pelo backend via `canvas.scale(contentScale)`. Hoje a engine roda em pixels físicos por convenção tácita do `SkikoHost` — funciona, mas vaza `contentScale` como abstração da Skiko para `:engine`. Resolve o débito introduzido pelo fix de hit-test em monitores HiDPI. |
| `editor-visual`  | Vai dirigir decisões sobre serialização de cena, inspetor de propriedades e potencialmente composição.                                                                                                                                                                                                                                                                                   |

## Como manter

- **Nova change criada** (`/opsx:propose <name>`) → adiciona linha em **Active** com resumo de uma frase.
- **Change archived** (`/opsx:archive <name>`) → remove a linha daqui. O histórico passa a viver em `openspec/changes/archive/<date>-<name>/`.
- **Ideia firmada sem change ainda** → linha em **Planned**. Quando virar change, promove para Active.
- Resumos devem caber numa linha do tipo "o que muda + por quê", não a lista completa de tasks.
