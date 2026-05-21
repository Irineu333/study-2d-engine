## Why

O editor visual planejado da `nengine` precisa conseguir **adicionar comportamento novo** a uma cena sem recompilar o módulo do jogo. Hoje, todo `Node` com lógica é uma subclasse Kotlin compilada (`Paddle : Node2D()`, `Ball : Node2D()`), descoberta via `NodeRegistry` em código no startup. Isso impede que o editor crie tipos novos, ou que um designer ajuste lógica sem passar pelo build do Gradle. Sem scripting, o editor fica restrito a posicionar e parentear instâncias dos tipos pré-existentes — não consegue cumprir o papel "estilo Godot" que está no roadmap. Esta change destrava esse caminho introduzindo scripts Kotlin (`.kts`) como fonte alternativa de tipos de `Node`, mantendo o invariante "comportamento via herança, não via componentes".

## What Changes

- Novo módulo `:engine-scripting` que embute `kotlin-scripting-jvm-host` e expõe um `ScriptHost` capaz de compilar arquivos `.nengine.kts` em subclasses de `Node`. **Não** é dep do `:engine` nem dos backends — só jogos que optarem por scripting o incluem.
- Nova SPI `ScriptHost` declarada em `:engine` (Kotlin puro, sem dependência do compilador), registrável globalmente assim como `NodeRegistry`.
- `SceneLoader` passa a aceitar entradas cujo `type` é o caminho de um script (heurística: termina em `.kts`). Quando encontrado, o loader delega ao `ScriptHost` em vez do `NodeRegistry` in-process. Os dois caminhos coexistem.
- ScriptDefinition customizado `.nengine.kts` que pré-importa o pacote da engine (`com.neoutils.engine.*`), de modo que scripts sejam concisos. Um script define exatamente uma classe top-level que estende `Node` (ou subclasse); zero ou mais que uma é erro fatal.
- Topologia de dependências entre scripts é resolvida por **manifest explícito** (lista ordenada de scripts a compilar, do mais profundo ao mais externo). Resolução automática fica como evolução documentada.
- Cache de compilação por hash do source em `build/scripting-cache/` dentro do módulo do jogo.
- Erros de compilação ou de runtime de script **crasham o processo** com a mensagem do compilador no stack. Sem fallback, sem stub visual.
- **Sem hot reload nesta change.** Editar um script só tem efeito ao reiniciar o jogo. Hot reload (níveis 2 e 3) fica documentado como evolução.
- **BREAKING (interno ao Pong, não à engine)**: o módulo `:games:pong` migra **todas** as suas classes de gameplay (`Paddle`, `Ball`, `Walls`, `Score`, `CenterLine`, `PaddleCollider`, `PongScene`) para scripts `.nengine.kts` em etapas testadas manualmente pelo usuário. O Kotlin restante no módulo Pong fica reduzido a `Main.kt` e ao registro do `ScriptHost`.
- `:games:tictactoe` e `:games:demos` **não migram** nesta change — continuam puro Kotlin, validando que a scripting é opt-in.

## Capabilities

### New Capabilities

- `scripting`: SPI `ScriptHost`, ScriptDefinition `.nengine.kts`, contrato de "um script = uma classe `Node`", manifest de ordem de compilação, política de erro fail-fast, cache de compilação, regras de classpath.

### Modified Capabilities

- `scene-serialization`: a forma `NodeEntry.type` ganha um segundo papel — pode ser um nome qualificado de classe (caminho atual) **ou** um caminho de script (`*.kts`). `SceneLoader` consulta o `ScriptHost` quando o `type` parece um caminho de script. O contrato de `@Inspect` / `properties` permanece inalterado: scripts são lidos via reflexão exatamente como classes compiladas.
- `pong-sample`: Pong deixa de ter classes Kotlin de gameplay próprias. Todo o gameplay vive em `src/main/resources/scripts/*.nengine.kts`. O `pong.scene.json` referencia scripts por `type`. O comportamento observável do jogo permanece **idêntico**.

## Impact

- **Novo módulo**: `:engine-scripting` — depende de `org.jetbrains.kotlin:kotlin-scripting-jvm-host` (puxa `kotlin-compiler-embeddable`, ~40MB). Dependência opt-in.
- **`:engine`**: nova interface `ScriptHost`, novo objeto `ScriptHosts` (ou similar) para registro global, ajuste em `SceneLoader` para roteamento por tipo. Sem dependências novas.
- **`:games:pong`**: ganha dependência de `:engine-scripting`, ganha `resources/scripts/`, perde classes Kotlin de gameplay (uma a uma, em etapas). `Main.kt` ganha registro do `ScriptHost`.
- **`:games:tictactoe`, `:games:demos`**: sem impacto.
- **Build**: aumento de ~40MB no fat-jar / classpath do `:games:pong` por causa do `kotlin-compiler-embeddable`. Outros jogos não pagam o custo.
- **CLAUDE.md**: atualizar a seção "Module Structure" com `:engine-scripting`, e adicionar uma seção curta "Scripting" descrevendo o contrato `.nengine.kts`.
- **Specs principais**: criar `openspec/specs/scripting/`, atualizar `openspec/specs/scene-serialization/` e `openspec/specs/pong-sample/` no arquivamento.
