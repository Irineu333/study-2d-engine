## Context

A engine evoluiu para um pipeline declarativo `scene.json + scripts/*.py` orquestrado por `BundleLoader` e `PythonScriptHost`. Pong nasceu Kotlin-puro e migrou para esse pipeline em changes anteriores (`add-bundle-loader`, `add-python-scripting`). TicTacToe **nunca passou** por essa migração — continua sendo `Board: Node2D`, `StatusText: Node`, `TicTacToeScene: Scene` em Kotlin, instanciados manualmente no `Main.kt` e rodando em `ComposeHost`.

A invariante arquitetural relevante: o pipeline bundle+Python é **backend-agnóstico**. Quem implementa isso é o fato de `BundleLoader.fromResources(name): Scene` retornar uma `Scene` que é apenas uma árvore de `Node`s; backends consomem `Scene` via `GameHost` SPI sem precisar saber se ela veio de bundle ou Kotlin.

Hoje, essa invariante é **plausível mas não verificada** — Pong roda em Skiko, TicTacToe (que está em Compose) é Kotlin-puro. Não há proof-by-execution.

Esta change fecha esse gap migrando TicTacToe para bundle. O backend Compose passa a engolir o mesmo pipeline que Skiko engole, sem mudança no `:engine-compose` em si.

## Goals / Non-Goals

**Goals:**

- TicTacToe roda via `BundleLoader.fromResources("tictactoe")` carregando `scene.json + scripts/board.py`.
- ComposeHost recebe a `Scene` resultante sem ajuste.
- Tipos Kotlin específicos do jogo (`Board`, `Mark`, `StatusText`, `TicTacToeScene`) apagados.
- `Main.kt` reduzido a wiring (instala `PythonScriptHost`, carrega bundle, entrega ao `ComposeHost`).
- Grade 3×3 declarativa via `Line2D` no `scene.json`.
- Label de status declarativo.
- Toda lógica de gameplay em Python (`board.py`).

**Non-Goals:**

- **Não** introduz `InputMap` (actions). TicTacToe usa mouse direto; `InputMap` entra quando algum jogo precisar (provavelmente Asteroids).
- **Não** introduz `Timer`. Velha não tem temporização.
- **Não** muda nada em `:engine` (núcleo) ou `:engine-compose` ou `:engine-skiko`. A mudança é totalmente dentro de `:engine-bundle` (mínima) + `:games:tictactoe`.
- **Não** adiciona um jogo novo ao roadmap. A própria Velha é a validação.

## Decisions

### D1. Script no root via `# extends Scene`

**Decisão:** Permitir que um script Python use `# extends Scene` na primeira linha. O `scene.json` declara o root explicitamente como `type: "com.neoutils.engine.scene.Scene"` e aponta `script: "scripts/board.py"`.

**Por quê isso é importante:** o `BundleLoader` hoje assume que o root é uma `Scene` "default" — instancia uma `Scene` sem permitir customização. Para o Velha, precisamos que o **orquestrador do jogo** (que sabia em Kotlin `TicTacToeScene : Scene()`) viva no script Python do root. Sem `# extends Scene`, teríamos que ter um `Node2D` orquestrador filho da `Scene` — mais nível de indireção sem ganho.

**Mudança em `BundleLoader`:** quando o `scene.json` root declarar `type: "com.neoutils.engine.scene.Scene"`, o loader **deve** aceitar isso (instanciar uma `Scene` plain), aplicar `script: ...` se houver, e usar essa instância como a `Scene` retornada por `fromResources`. Sem essa flexibilidade, o root permanece sempre "Scene anônima sem script".

Concretamente, o `NodeRegistry` é estendido com o registro de `com.neoutils.engine.scene.Scene → Scene::class`. O dispatcher do BundleLoader para o root passa a verificar: se o JSON root tem `type`, usa o registry; se não, usa default `Scene()` (compatibilidade com cenas antigas tipo Pong se elas continuarem omitindo `type` no root — mas Pong já declara `type: "com.neoutils.engine.scene.Scene"`, então sem regressão).

**Alternativa rejeitada:** introduzir um nó orquestrador `Node2D` filho do root, com script. Funciona, mas adiciona nível de indireção e contraria o paralelo Godot (em Godot a Scene/root **é** scriptável).

### D2. Grade declarativa em `scene.json` via Line2D

**Decisão:** As 4 linhas da grade 3×3 são `Line2D` filhos declarados em `scene.json`. Cada `Line2D` tem `points: [Vec2(a, b), Vec2(c, d)]` em coordenadas absolutas (do play field 600×600) e `thickness: 4f`, `color: Color(0.9, 0.9, 0.9, 1)`.

**Cálculo das posições:** o tabuleiro ocupa `(60, 60)` a `(540, 540)` (cellSize = 160, board side = 480, centralizado em 600×600 com 60px de gap pra Label de status no topo). As 4 linhas internas são em x=220, x=380 (verticais full-height interno), y=220, y=380 (horizontais full-width interno).

**Por quê não em `board.py._draw`:** declarativo é mais Godot. O script só desenha o que muda (marks, linha vencedora). A grade é estática.

**Por quê não Layout dinâmico (responsive):** o `Camera2D.bounds` está fixado em 600×600. Resize do host não muda o play field — fica letterboxed se necessário. Velha não tem requisito de responsividade dinâmica. Simplifica o JSON.

### D3. Label de status declarativo, texto via NodeRef

**Decisão:** O `Label` `status` é declarado em `scene.json` com `text: "Vez de X"` (placeholder inicial). O script `board.py` mantém um `NodeRef` para ele e atualiza `self._status.text = "..."` no `_process` quando o estado muda.

**Por quê não `Signal`-driven:** seria over-engineering. O status muda durante `_process` no mesmo script que detém o estado — atualizar diretamente é claro.

### D4. Marks e linha vencedora em `_draw` direto

**Decisão:** Marks (X e O) e a linha vencedora são desenhadas via `_draw(self, renderer)` do `board.py` em vez de filhos declarativos.

**Por quê:** estado dinâmico de 9 células com X/O/None mapeia mal a "nós filhos visíveis/invisíveis" sem complicar o tree. Em Godot, padrão Velha em tutoriais Godot é desenhar marks via `_draw` por exatamente esse motivo. Mantém board.py auto-contido.

**Custo:** `board.py._draw` é mais denso (lógica de desenhar X = duas linhas cruzadas, O = círculo outline). Aceitável.

### D5. Mark como string ("X" ou "O"), não enum

**Decisão:** Em Python o estado é `list[str | None]` de 9 elementos, com `"X"` ou `"O"` ou `None`. Não precisamos do enum `Mark` Kotlin.

**Por quê:** Python é duck-typed, strings são naturais. O enum Kotlin servia para serialização forte e exhaustive `when`, nenhum dos quais se aplica aqui. `Mark.kt` é apagada.

### D6. ComposeHost permanece **intocada**

**Decisão:** Nenhuma linha de `:engine-compose` muda. A prova viva é que rodar Velha agora exercita `BundleLoader → Scene → ComposeHost.run(scene)` sem ajustes em Compose.

**Por quê isso é importante:** se precisássemos mudar Compose para suportar bundle, é sinal de que o pipeline **não** é backend-agnóstico — e teríamos descoberto antes (em uma change anterior, ou nunca). A migração da Velha valida a invariante por uso.

### D7. Sem InputMap, sem Timer — escopo apertado

**Decisão:** `InputMap` (actions Godot) seria valioso aqui (`scene.input.is_action_pressed("place")` vs. `wasMouseClicked(MouseButton.Left)`), mas adiciona uma capability nova e empurra o escopo. Empurrado para change futura (`game-asteroids` provavelmente puxa).

Mesma decisão para `Timer`: Velha não usa, fica para quando precisar.

## Risks / Trade-offs

- **R1. Python `board.py` é mais denso que Board.kt** — ~150 linhas de gameplay+rendering em vez de Kotlin estruturado. Mitigação: estilo claro, comentários só onde "por quê" é não-óbvio.
- **R2. `# extends Scene` é um caso novo** — só Velha usa hoje. Risco de bug no `BundleLoader.attachScript` quando o target é o root. Mitigação: teste manual end-to-end (rodar a Velha) é suficiente; cobertura de testes unitários custosa demais para essa escala.
- **R3. Layout estático sem responsividade** — janela menor que 600×600 corta o tabuleiro. Aceitável (`config.width = 600, height = 600` mantém o mínimo).
- **R4. GraalPy startup adiciona ~500ms ao boot** — TicTacToe antes era Kotlin-puro com boot quase instantâneo. Agora paga o custo do warmup do GraalPy. **Custo aceito** — explicitamente sinalizado em CLAUDE.md (ver invariante "estritamente opt-in" de `:engine-bundle-python`).
- **R5. `Scene` no NodeRegistry pode confundir** — alguém pode achar que `Scene` é um Node "comum". Mitigação: docstring no registro explicando que é especial (só faz sentido como root).

## Migration Plan

1. Adicionar deps `engineBundle` + `engineBundlePython` em `games/tictactoe/build.gradle.kts`.
2. Registrar `com.neoutils.engine.scene.Scene` no `NodeRegistry` (em `:engine-bundle` ou onde mora hoje).
3. Estender `BundleLoader` para aceitar `type` no root via registry (verificar se já faz — pode ser que sim).
4. Criar `src/main/resources/tictactoe/scene.json` declarando root `Scene + script`, `Camera2D`, `Line2D × 4`, `Label`.
5. Criar `src/main/resources/tictactoe/scripts/board.py`.
6. Reescrever `games/tictactoe/.../Main.kt`.
7. Apagar `Board.kt`, `Mark.kt`, `StatusText.kt`, `TicTacToeScene.kt`.
8. Smoke test: `./gradlew :games:tictactoe:run`. Clique coloca X/O, vitória mostra linha, restart funciona, status texto correto. Compare comportamento ao Kotlin-puro anterior.
9. CLAUDE.md atualizado (instruções de Velha + tabela roadmap).
