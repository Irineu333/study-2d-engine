# CLAUDE.md

Orientação perene para contribuidores (humanos ou agentes). Mantenha este arquivo atualizado quando decisões fundamentais mudarem.

## Purpose

`nengine` é uma 2D game engine **construída para aprender arquitetura de engine**. Começa em modo code-only com jogos de exemplo (Pong é o primeiro) e evolui em direção a um editor visual. A meta é clareza didática e evolução incremental — não performance prematura.

Stack: Kotlin + Compose Multiplatform Desktop (JVM). Compose entra como **primeiro backend** da engine, não como modelo de programação.

## Architectural Invariants

Toda mudança deve respeitar os quatro invariantes abaixo. Eles vêm das decisões arquiteturais consolidadas em `openspec/changes/archive/2026-05-18-engine-foundation/design.md` e não podem ser quebrados sem uma nova change OpenSpec discutindo a revisão.

1. **Scene graph estilo Godot, por herança.** Comportamento de gameplay é adicionado por subclasses de `Node` / `Node2D`. **Sem** `List<Component>` ou ECS. Cada Node tem sua identidade de tipo (`class Paddle : Node2D()`).
2. **`:engine` não depende de Compose.** O módulo `:engine` não declara nenhum artefato `org.jetbrains.compose.*` ou `androidx.compose.*`, direta ou transitivamente. Quem precisa de Compose é o `:engine-compose`.
3. **Colisão via `Collider`-como-Node + `PhysicsSystem` central.** `Collider` é um tipo de `Node`; o `PhysicsSystem.step(scene)` enumera todos os colliders ativos, testa pares e invoca `onCollide`. Broad phase é O(N²) intencionalmente.
4. **`Renderer` e `Input` são SPIs.** Definidos como interfaces em `:engine`. Compose é apenas o primeiro backend. Nós nunca tocam tipos de backend; usam só a interface.

## Module Structure & How to Run

```
:engine            ← núcleo Kotlin puro (scene graph, math, SPIs, física, loop, DX)
:engine-compose    ← backend Compose Multiplatform Desktop (Renderer, Input, GameSurface)
:games:pong        ← jogo Pong executável (humano vs IA) — prova viva da fundação
:games:tictactoe   ← jogo Velha (humano vs humano) — prova de interação discreta / hit-test
:games:demos       ← cenas de demonstração visual das melhorias da engine (não é um jogo)
```

Os módulos `:shared` e `:desktopApp` do template KMP foram **removidos** durante a change `engine-foundation`.

Para rodar Pong:

```sh
./gradlew :games:pong:run
```

Durante o jogo:

- `W`/`S` movem o paddle esquerdo
- `F1` liga/desliga overlay de FPS
- `F2` liga/desliga visualização de colliders

Para rodar Velha:

```sh
./gradlew :games:tictactoe:run
```

Durante o jogo:

- Clique esquerdo numa célula vazia faz a jogada do jogador atual (X começa, depois alterna O)
- Quando a partida termina (vitória ou empate), o próximo clique esquerdo em qualquer lugar reinicia (esse clique só reinicia — não joga)
- `F1` liga/desliga overlay de FPS
- `F2` liga/desliga visualização de colliders (Velha não usa colliders, mas o overlay continua disponível)

Para rodar Demos:

```sh
./gradlew :games:demos:run
```

Durante a execução:

- `1` Transform orbit — pai rotacionando faz os filhos orbitarem (composição de rotação sobre posição, A1)
- `2` Scale hierarchy — pai com `scale` oscilando faz o filho crescer e encolher (composição de scale via `Shape.onRender`, A1)
- `3` Spawner — clique do mouse adiciona bolinhas durante `onUpdate`; o trap central remove durante `onCollide` (mutação durante traversal, A4); F2 mostra que o overlay de colliders sai do `GameSurface` (A2)
- `F1` liga/desliga overlay de FPS
- `F2` liga/desliga visualização de colliders

## Coding Conventions

- **Comentários só para o "por quê" não-óbvio.** Nunca para o "o quê" (nomes já dizem). Evite docstrings cerimoniais.
- **Identificadores em inglês.** Texto in-game e mensagens ao usuário podem ser em português; nomes de classe/função/variável devem permanecer em inglês.
- **API pública de `:engine` documentada com KDoc** quando o uso pretendido não for óbvio.
- **Imutabilidade onde for barata.** `Vec2`, `Rect`, `Transform`, `Color` são data classes; operações retornam novas instâncias.
- **Sem dependências escondidas.** Se um módulo precisa de Compose, declara no `build.gradle.kts` daquele módulo. Se `:engine` ganhar uma dependência transitiva proibida, é bug.
- **Em `:engine-compose`, use APIs do Compose, não Skia direto.** `org.jetbrains.skia.*` só com justificativa documentada.
- **Testes para regras invariantes.** Cada decisão arquitetural com risco de regressão (lifecycle ordering, broad phase) tem teste unitário.

## OpenSpec Workflow

Mudanças materiais (arquitetura, API pública, novos módulos, novas capabilities) **passam por uma change OpenSpec antes da implementação**. Roteiro padrão:

1. Explore mode: `/opsx:explore <tema>` — gera notas e perguntas.
2. Propose: `/opsx:propose <change-name>` — gera proposal, design, specs, tasks.
3. Apply: `/opsx:apply <change-name>` — implementa tarefa por tarefa.
4. Verify: `/opsx:verify <change-name>` — confere se a implementação fecha com os artefatos.
5. Archive: `/opsx:archive <change-name>` — congela a change e atualiza specs principais.

Para uma feature nova ou refator significativo: abra uma change OpenSpec, **não** um PR direto de código.

## Roadmap

| Change                | Status   | Resumo                                                                 |
| --------------------- | -------- | ---------------------------------------------------------------------- |
| `engine-foundation`   | Archived | Scene graph, math, SPIs, física O(N²), game loop, Compose runtime, Pong, DX e CLAUDE.md. |
| `add-tictactoe`       | Archived | Mouse buttons na SPI de `Input`, `drawLine` e `measureText` no `Renderer`, `Rect.contains`, e jogo da Velha humano vs humano em `:games:tictactoe`. |
| `engine-consistency`  | Archived | Composição de `Transform` por ancestralidade, cache de `Scene` em `Node`, mutação segura durante traversal, overlay de colliders migrado de `Scene` para `GameSurface`. Inclui `:games:demos` para validação visual. |
| editor (placeholder)  | Planned  | Editor visual estilo Godot. Vai dirigir decisões sobre serialização de cena, inspetor de propriedades e potencialmente composição. |

Atualize a tabela acima quando uma change avançar de Planned → Active → Archived.
