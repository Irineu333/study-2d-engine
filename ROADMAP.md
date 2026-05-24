# Roadmap

Plano de evolução do `nengine`. **Active** = changes OpenSpec em andamento; **Planned** = intenção firmada mas sem change aberta ainda. Histórico de changes concluídas vive em [`openspec/changes/archive/`](./openspec/changes/archive/) — não duplicar aqui.

## Active

| Change                     | Resumo                                                                                                                                                                                                                                  |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `godot-style-properties`   | Unifica `properties` + `props` num único bag em `scene.json` (estilo Godot); bump `SceneFile.version` para 2 com fail-fast em colisão de chave ou chave desconhecida; SPI `ScriptHost` ganha `declaredExports` e `currentExports`.       |
| `collision-overhaul`       | Reescreve colisão no estilo Godot: `CollisionObject2D` ramificado em `Area2D`/`StaticBody2D`/`CharacterBody2D`, `CollisionShape2D` carregando `Shape2D` polimórfica (`Rectangle`/`Circle`), `PhysicsSystem` com eventos entered/exited pareados. |
| `bundle-tictactoe`         | Migra `:games:tictactoe` para o pipeline bundle (`scene.json` + Python), apagando os Nodes Kotlin do jogo. Prova viva de que `BundleLoader` + `PythonScriptHost` rodam idênticos no backend Compose.                                    |
| `inject-script-host`       | Remove `ScriptHostRegistry` singleton e `PythonScriptHost.install()`; `BundleLoader.fromResources/fromPath` passa a receber `ScriptHost` por parâmetro; `PythonScriptHost.create()` vira a entry point explícita.                       |

## Planned

| Change           | Resumo                                                                                                                                                                                                |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `game-snake`     | Validador da fundação Godot-style: fixed-step, signals, `Camera2D.bounds`, primitivas visuais, sem dependência de colisão nova.                                                                       |
| `lua-scripting`  | Segunda implementação do `ScriptHost` SPI usando Lua. Prova que a SPI é genuinamente agnóstica de linguagem (não acomodada ao GraalPy); bundle de exemplo com `.lua` carregado lado-a-lado com `.py`. |
| editor           | Editor visual estilo Godot. Vai dirigir decisões sobre serialização de cena, inspetor de propriedades e potencialmente composição.                                                                    |

## Como manter

- **Nova change criada** (`/opsx:propose <name>`) → adiciona linha em **Active** com resumo de uma frase.
- **Change archived** (`/opsx:archive <name>`) → remove a linha daqui. O histórico passa a viver em `openspec/changes/archive/<date>-<name>/`.
- **Ideia firmada sem change ainda** → linha em **Planned**. Quando virar change, promove para Active.
- Resumos devem caber numa linha do tipo "o que muda + por quê", não a lista completa de tasks.
