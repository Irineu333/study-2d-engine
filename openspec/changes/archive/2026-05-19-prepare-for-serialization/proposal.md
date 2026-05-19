## Why

A engine hoje é otimizada para code-only: lambdas em construtores, subclasses anônimas, identidade frágil por `name` e nenhum modelo declarativo para o que entra num arquivo. Antes de qualquer editor visual fazer sentido, a biblioteca precisa ser **amigável a serialização** — cenas que vivem em arquivos JSON, conexões entre nodes que sobrevivem ao round-trip salva/carrega, e propriedades inspecionáveis declaradas de forma explícita. Esta change refatora o núcleo e os jogos de exemplo até esse ponto, fechando o trabalho com um scene loader experimental que prova o caminho ponta-a-ponta com Pong.

## What Changes

- Introduz capability `scene-serialization` no `:engine` com três primitivas:
  - **`NodeRef<T : Node>`** — referência tipada a outro node, expressa por path relativo (`"../Ball"`, `"Child/Bar"`). Resolução lazy com cache; invalidado quando a árvore muda.
  - **`Signal<T>`** — event bus mínimo por node: `operator fun plusAssign((T) -> Unit)` + `fun emit(T)`. Substitui lambdas-em-construtor para conexões node-a-node.
  - **`@Inspect`** — anotação que marca propriedade como inspecionável pelo editor **e** incluída na serialização. Propriedades não anotadas ficam fora do arquivo (state interno marcado com `@Transient`).
- Introduz scene loader experimental: `SceneLoader.save(scene): String` e `SceneLoader.load(json): Scene`, sobre `kotlinx.serialization` JSON.
- Adiciona dependência `org.jetbrains.kotlinx:kotlinx-serialization-json` em `:engine` (não viola invariante de "sem Compose").
- **BREAKING**: `Node.addChild` valida unicidade de nome entre irmãos; em conflito, sufixa automaticamente (`Ball` → `Ball_2`). `parent.findChild(name): Node?` exposto para resolução de path.
- **BREAKING**: Math/render primitives ganham `@Serializable` (`Vec2`, `Rect`, `Transform`, `Color`). Classes de Node candidatas à árvore serializável ganham `@Serializable` e construtores no-args.
- **BREAKING**: Pong refatorado para a nova superfície:
  - `Ball` passa a estender `BoxCollider` (padrão "node IS-A collider", elimina subclasse anônima).
  - `onScore` deixa de ser lambda construtor e vira `Signal<Goal.Side>` exposta na bola.
  - `aiTargetY` no `Paddle` deixa de ser lambda e vira `NodeRef<Node2D>` (`target`); a bola é resolvida via path.
  - Construtores ricos viram no-args + propriedades `@Inspect` com defaults.
- **BREAKING**: SpawnerDemo refatorado para classes nomeadas (`Spawner : Node2D`, `Trap : BoxCollider`, `Ball : BoxCollider` — sem subclasses anônimas). Comportamento idêntico.
- Scene loader é provado ponta-a-ponta salvando e carregando `PongScene`: o jogo carregado de `pong.scene.json` é indistinguível do code-only.

**Fora do escopo** — explicitado para não vazar:
- Estado runtime (`Ball.velocity`, `Board.cells`, `Score.value`) **não** é serializado. Blueprint inicial apenas.
- Save game / snapshot de partida em andamento — change futura.
- UI de editor — change futura. Esta change só prepara o terreno (identidade, refs, signals, @Inspect, formato).
- Conexão de signals por arquivo (sem editor visual ainda) — adiada; signals só conectáveis em code-only nesta change.

## Capabilities

### New Capabilities

- `scene-serialization`: primitivas que tornam cenas serializáveis sem sacrificar DX code-only — `NodeRef<T>` (referência tipada por path), `Signal<T>` (event bus por node), anotação `@Inspect` (propriedade inspecionável + serializada), `SceneLoader` (save/load JSON via `kotlinx.serialization`), validação de nome único entre irmãos com auto-suffix, formato de arquivo `*.scene.json` documentado.

### Modified Capabilities

- `engine-core`: `Node` ganha validação de nome único entre irmãos (auto-suffix em conflito) e `findChild(name): Node?` para resolução de path; classes de Node passam a ter construtores no-args com defaults; math primitives (`Vec2`, `Rect`, `Transform`) e `Color` ganham `@Serializable`; lambdas-em-construtor deixam de ser idioma recomendado (signals e `NodeRef` substituem).
- `pong-sample`: `Ball` reescrita como `Ball : BoxCollider` (sem subclasse anônima de collider); `onScore` vira `Signal<Goal.Side>`; `Paddle.aiTargetY` vira `NodeRef<Node2D>` (`target`); construtores no-args + propriedades `@Inspect`; `pong.scene.json` passa a existir e Pong pode ser lançado tanto code-only quanto carregando o arquivo.

> Tic Tac Toe recebe ajustes pontuais de implementação (anotações `@Serializable`/`@Inspect`/no-args onde aplicável) mas **nenhum requisito de `tictactoe-sample` muda** — comportamento e composição de cena ficam idênticos. Por isso a capability não aparece na lista de modificadas.

## Impact

- **Módulo `:engine`**: nova dependência `kotlinx-serialization-json`; plugin `org.jetbrains.kotlin.plugin.serialization` aplicado no `build.gradle.kts`. Novo subpackage `com.neoutils.engine.serialization` (NodeRef, Signal, @Inspect, SceneLoader). `Node.addChild` muda comportamento em conflito de nome (auto-suffix em vez de aceitar duplicado silencioso).
- **API pública**: `Vec2`, `Rect`, `Transform`, `Color`, `Node`, `Node2D`, `Shape`, `Text`, `BoxCollider` ganham `@Serializable`. Construtores ricos perdem parâmetros obrigatórios em favor de no-args + `var` propriedades anotadas. Quem instanciava `Paddle(playFieldHeight = ...)` passa a fazer `Paddle().apply { playFieldHeight = ... }` ou usar uma factory function de conveniência.
- **Jogos**: `:games:pong` e `:games:demos` reescritos para a nova superfície. `:games:tictactoe` recebe ajustes pontuais.
- **DX code-only**: ligeira verbosidade em chamadas de construção (apply blocks em vez de named arguments). Mitigado por factories/builder functions opcionais quando a DX justificar.
- **Invariantes preservados**: scene graph por herança; `:engine` sem Compose; Collider-como-Node + PhysicsSystem central; Renderer/Input/GameHost como SPIs.
- **Testes**: novos testes unitários para `NodeRef` (resolução, cache, invalidação), `Signal` (emit/handler ordering), unicidade de nome entre irmãos, round-trip de scene loader (save → load → comparação de árvore).
