# CLAUDE.md

Orientação perene para contribuidores (humanos ou agentes). Mantenha este arquivo atualizado quando decisões fundamentais mudarem.

## Purpose

`nengine` é uma 2D game engine **construída para aprender arquitetura de engine**. Começa em modo code-only com jogos de exemplo (Pong é o primeiro) e evolui em direção a um editor visual. A meta é clareza didática e evolução incremental — não performance prematura.

Stack: Kotlin + Skiko (JVM Desktop). Skiko é o **backend padrão** da engine; Compose Multiplatform é o **segundo backend**, mantido vivo via `:games:tictactoe`.

## Architectural Invariants

Toda mudança deve respeitar os quatro invariantes abaixo. Eles vêm das decisões arquiteturais consolidadas em `openspec/changes/archive/2026-05-18-engine-foundation/design.md` e não podem ser quebrados sem uma nova change OpenSpec discutindo a revisão.

1. **Scene graph estilo Godot, por herança.** Comportamento de gameplay é adicionado por subclasses de `Node` / `Node2D`. **Sem** `List<Component>` ou ECS. Cada Node tem sua identidade de tipo (`class Paddle : Node2D()`).
2. **`:engine` não depende de Compose.** O módulo `:engine` não declara nenhum artefato `org.jetbrains.compose.*` ou `androidx.compose.*`, direta ou transitivamente. Quem precisa de Compose é o `:engine-compose`.
3. **Colisão via `Collider`-como-Node + `PhysicsSystem` central.** `Collider` é um tipo de `Node`; o `PhysicsSystem.step(scene)` enumera todos os colliders ativos, testa pares e invoca `onCollide`. Broad phase é O(N²) intencionalmente.
4. **`Renderer`, `Input` e `GameHost` são SPIs.** Skiko é o backend padrão (`:engine-skiko`); Compose é o segundo backend (`:engine-compose`). Jogos novos devem usar Skiko por default.

## Performance Notes

`Node2D.worldTransform()` cacheia o resultado por nó (campo `@Transient cachedWorldTransform`). O cache começa `null` e é populado na primeira leitura; leituras consecutivas sem mutação retornam em O(1). O cache é invalidado de forma **eager** — o nó mutado e todos os seus descendentes `Node2D` (atravessando `Node` puros no meio do caminho) são marcados dirty imediatamente — nos seguintes momentos:

1. **Atribuição do `transform` local** (`node.transform = ...`) — o setter custom de `Node2D.transform` chama `invalidateWorldTransformRecursive()`.
2. **Mudança de hierarquia** (`addChild`/`removeChild`, incluindo caminhos diferidos via `pendingAdd`/`pendingRemove`) — `Node.applyAdd` e `Node.applyRemove` chamam `invalidateWorldTransformRecursive()` no filho.
3. **Mutação de ancestral** — como a invalidação propaga recursivamente pelos descendentes, alterar o transform de um pai invalida automaticamente todos os `Node2D` filhos/netos.

O cache é **estado runtime puro**: nunca persiste em `scene.json` (anotado com `@Transient` do `kotlinx.serialization`). Se você adicionar um novo caminho de mutação de transform ao engine, deve chamar `invalidateWorldTransformRecursive()` para manter a coerência.

## Module Structure & How to Run

```
:engine                ← núcleo Kotlin puro (scene graph, math, SPIs, física, loop, DX, GameHost SPI)
:engine-bundle         ← carregamento de cena via bundle (scene.json + scripts/) + ScriptHost SPI agnóstica de linguagem
:engine-bundle-python  ← implementação Python do ScriptHost via GraalPy 24.x; distribui stubs .pyi em resources/stubs/
:engine-compose        ← backend Compose Multiplatform Desktop (Renderer, Input, GameSurface, ComposeHost) — segundo backend
:engine-skiko          ← backend Skiko puro sobre SkiaLayer + JFrame (SkikoRenderer, SkikoInput, SkikoHost) — backend padrão
:games:pong            ← jogo Pong executável (humano vs IA), roda em Skiko — prova viva da fundação
:games:tictactoe       ← jogo Velha (humano vs humano), roda em Compose — sentinela do segundo backend
:games:demos           ← cenas de demonstração visual das melhorias da engine (roda em Skiko)
```

Os módulos `:shared` e `:desktopApp` do template KMP foram **removidos** durante a change `engine-foundation`.

Para rodar Pong:

```sh
./gradlew :games:pong:run
```

`Main.kt` instala o `PythonScriptHost` via `PythonScriptHost.install()`, carrega o bundle `pong/` (em `:games:pong/src/main/resources/pong/`, com `scene.json` na raiz e `scripts/*.py`) via `BundleLoader.fromResources("pong")` e entrega a `Scene` ao `SkikoHost`. O `scene.json` é a fonte da verdade da árvore — editar o JSON ou os `.py` altera o comportamento sem recompilar Kotlin.

Durante o jogo:

- `W`/`S` movem o paddle esquerdo
- `F1` liga/desliga overlay de FPS (tratado pelo `GameHost`, configurável via `GameConfig.toggleFpsKey`)
- `F2` liga/desliga visualização de colliders (idem, via `GameConfig.toggleCollidersKey`)

Para rodar Velha:

```sh
./gradlew :games:tictactoe:run
```

Durante o jogo:

- Clique esquerdo numa célula vazia faz a jogada do jogador atual (X começa, depois alterna O)
- Quando a partida termina (vitória ou empate), o próximo clique esquerdo em qualquer lugar reinicia (esse clique só reinicia — não joga)
- `F1` liga/desliga overlay de FPS (tratado pelo `GameHost`, configurável via `GameConfig.toggleFpsKey`)
- `F2` liga/desliga visualização de colliders (idem, via `GameConfig.toggleCollidersKey`; Velha não usa colliders, mas o overlay continua disponível)

Para rodar Demos:

```sh
./gradlew :games:demos:run
```

Durante a execução:

- `1` Transform orbit — pai rotacionando faz os filhos orbitarem (composição de rotação sobre posição, A1)
- `2` Scale hierarchy — pai com `scale` oscilando faz o filho crescer e encolher (composição de scale via `Shape.onRender`, A1)
- `3` Spawner — clique do mouse adiciona bolinhas durante `onUpdate`; o trap central remove durante `onCollide` (mutação durante traversal, A4); F2 mostra que o overlay de colliders sai do `GameHost` (A2)
- `4` Collision stress — 30 `BoxCollider`s colidindo em broad phase O(N²); valida o cache de `worldTransform()` com invalidação eager a cada frame; overlay no-screen mostra contagem e FPS
- `F1` liga/desliga overlay de FPS (tratado pelo `GameHost`, configurável via `GameConfig.toggleFpsKey`)
- `F2` liga/desliga visualização de colliders (idem, via `GameConfig.toggleCollidersKey`)

## Coding Conventions

- **Comentários só para o "por quê" não-óbvio.** Nunca para o "o quê" (nomes já dizem). Evite docstrings cerimoniais.
- **Identificadores em inglês.** Texto in-game e mensagens ao usuário podem ser em português; nomes de classe/função/variável devem permanecer em inglês.
- **API pública de `:engine` documentada com KDoc** quando o uso pretendido não for óbvio.
- **Imutabilidade onde for barata.** `Vec2`, `Rect`, `Transform`, `Color` são data classes; operações retornam novas instâncias.
- **Sem dependências escondidas.** Se um módulo precisa de Compose, declara no `build.gradle.kts` daquele módulo. Se `:engine` ganhar uma dependência transitiva proibida, é bug.
- **Em `:engine-compose`, use APIs do Compose, não Skia direto.** `org.jetbrains.skia.*` só com justificativa documentada.
- **Testes para regras invariantes.** Cada decisão arquitetural com risco de regressão (lifecycle ordering, broad phase) tem teste unitário.

### Serialization contract (`@Inspect` / `@Transient`)

Classes candidatas a aparecer numa scene file levam `@Serializable` (do `kotlinx.serialization`). Para essas classes vale a disciplina abaixo — exigida porque a engine ainda não tem lint custom para fazê-la cumprir:

- Toda `var` é anotada **ou** com `@Inspect` (configuração inicial: vai para o arquivo e é exposta ao editor futuro) **ou** com `@Transient` (estado runtime: nunca persiste).
- `@Inspect` mora em `com.neoutils.engine.serialization.Inspect`; `@Transient` é o do `kotlinx.serialization`.
- Não deixe uma `var` sem anotação. Mesmo que o serializer atual aceite, fica armadilha para o próximo refator.
- `val` não precisa de anotação — não é mutável e por padrão fica de fora do JSON gerado por `SceneLoader`.

Exemplo:

```kotlin
@Serializable
class Paddle : Node2D() {
    @Inspect var speed: Float = 360f
    @Inspect var ai: Boolean = false

    @Transient internal var lastInputDirection: Float = 0f
}
```

### Scripting contract (Python)

A engine usa **Python via GraalPy** como mecanismo de scripting. O modelo é Godot-like: o Node Kotlin permanece a instância nativa; um script `.py` é **anexado** a ele via slot `Node.scriptInstance`. Somente jogos que dependem de `:engine-bundle-python` têm custo do runtime GraalPy — é estritamente opt-in.

#### Estrutura de um script

```python
# extends Node2D          ← obrigatório: tipo Node nativo que o script adorna

speed: float = 360.0      ← export: anotação top-level, descoberta estaticamente via AST
ai: bool = False
target: NodeRef = NodeRef("")

def on_enter(self):        ← hooks snake_case, todos opcionais
    ...

def on_update(self, dt: float):
    ...

def on_render(self, renderer):
    ...

def on_collide(self, other):
    ...
```

- **`# extends <NodeType>`** na primeira linha não vazia. `<NodeType>` é resolvido contra o `NodeRegistry` (ex.: `Node2D`, `BoxCollider`). Falhar nesta linha é erro fatal.
- **Exports** são atribuições anotadas no top-level. Tipos suportados: `int`, `float`, `bool`, `str`, `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`, `Optional[T]` (ou `T | None`). Qualquer outro tipo é silenciosamente ignorado.
- **Estado runtime** fica em `self._private` (convenção: atributos com prefixo `_` não são exports, são instâncias por-nó).
- **Hooks fixos**: `on_enter`, `on_update(dt)`, `on_render(renderer)`, `on_collide(other)`. Hooks ausentes são no-ops.
- **Bindings implícitos no Context**: `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`, `BoxCollider`, `Node2D`.
- **Fail-fast**: qualquer erro (parse, `extends` desconhecido, exception num hook) propaga até o `Main.kt` e crasha o processo.

#### Wiring no `scene.json`

```json
{
  "type": "engine.Node2D",
  "script": "scripts/paddle.py",
  "props": {
    "speed": 360.0,
    "ai": false
  }
}
```

`BundleLoader` faz tree-walk no JSON, coleta todos os `script` paths, carrega via `ScriptHostRegistry` (despachado por extensão de arquivo) e aplica `props` nos exports da instância.

#### Instalando o ScriptHost

O `Main.kt` do jogo precisa chamar `PythonScriptHost.install()` **antes** de `BundleLoader.fromResources(...)`. Sem isso, a extensão `.py` não tem host registrado e o carregamento falha.

#### Inspecting Python scripts with IDE

O `:engine-bundle-python` publica stubs `.pyi` em `src/main/resources/stubs/engine/`. Para habilitar autocompletar e type-checking no seu editor (Pyright/Pylance):

1. Extraia os stubs do JAR ou localize o source em `engine-bundle-python/src/main/resources/stubs/`.
2. Adicione o diretório `stubs/` ao `extraPaths` do Pyright (em `pyrightconfig.json`):
   ```json
   { "extraPaths": ["engine-bundle-python/src/main/resources/stubs"] }
   ```
3. Para Pylance (VS Code), configure `python.analysis.extraPaths` no `settings.json`.

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
| `add-skiko-runtime`   | Archived | Runtime Skiko puro (sem Compose) como backend padrão; `ComposeHost`/`SkikoHost` implementando o novo `GameHost` SPI; overlay de debug unificado. |
| `prepare-for-serialization` | Archived | Primitivas `NodeRef`/`Signal`/`@Inspect`, `NodeRegistry`, `SceneLoader` (`save`/`load` JSON via `kotlinx.serialization`); refactor de Pong/Demos/Velha para construtores no-args + `@Inspect` var; `pong.scene.json` como entry point principal de Pong. |
| `add-scripting`       | Archived | Compilador e cache de scripts Kotlin `.nengine.kts` via Kotlin Scripting, e migração completa de Pong para scripts. |
| `drop-pong-tag-only-scripts` | Archived | Remove scripts vazios (`paddle-collider`, `walls`) que serviam só como tag; usa `BoxCollider` da engine por FQN no `pong.scene.json`; `Ball.onCollide` despacha por estrutura da cena; rename `Goal.GoalSide` → `Goal.Side`. |
| `add-bundle-loader`   | Archived | Substitui `:engine-scripting` por `:engine-bundle`; introduz `BundleLoader.fromResources`/`fromPath`; `NodeRegistry` bidirecional; descoberta de scripts via tree-walk + round-robin no host. Pong passa a viver em `resources/pong/`. |
| `add-python-scripting` | Active  | Substitui Kotlin Scripting por ScriptHost SPI agnóstica + GraalPy como primeira impl (`:engine-bundle-python`). Migra Pong inteiro para `.py`. Remove `kotlin-scripting-*` deps. Publica stubs `.pyi`. |
| `cache-world-transform` | Active | Cache lazy + invalidação eager em `Node2D.worldTransform()`: corta o multiplicador O(N²) parasitário do broad phase; setter custom no `transform`; hooks em `applyAdd`/`applyRemove`; demo de stress (`4`) com 260 colliders. |
| editor (placeholder)  | Planned  | Editor visual estilo Godot. Vai dirigir decisões sobre serialização de cena, inspetor de propriedades e potencialmente composição. |

Atualize a tabela acima quando uma change avançar de Planned → Active → Archived.
