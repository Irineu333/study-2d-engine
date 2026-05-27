## Why

A SPI `ScriptHost` em `:engine-bundle` foi desenhada explicitamente como agnóstica de linguagem, mas hoje só existe uma implementação concreta (`PythonScriptHost` via GraalPy). Sem uma segunda implementação, a abstração não está provada — ela poderia silenciosamente ter dependências escondidas de Python. Adicionar **Lua via LuaJ** como segundo backend de scripting prova o polimorfismo da SPI, traz uma linguagem reconhecidamente popular em scripting de jogos (LÖVE2D, Defold, Roblox), e mantém o mesmo princípio de "zero hassle de distribuição" da engine — LuaJ é JAR puro, sem libs nativas, igual ao Skiko e Compose.

A migração de `:games:tictactoe` (hoje Python+Compose) para Lua+Compose serve como prova viva: troca-se apenas o scripting do jogo da velha, e `ComposeHost` continua engolindo o bundle sem ajuste algum. Isso reforça dois invariantes da fundação: `ScriptHost` é agnóstico de linguagem, e o backend de render (`:engine-compose`) é agnóstico de scripting.

## What Changes

- Adiciona o módulo Gradle `:engine-bundle-lua` (irmão de `:engine-bundle-python`), com a classe `LuaScriptHost : ScriptHost` cuja `extension` é `.lua`.
- LuaJ 3.0.x como runtime, embarcado puro JVM (sem libs nativas). LuaJIT fica fora do escopo como melhoria futura.
- Define o contrato de script Lua: chunk retorna uma **tabela** com campos `extends`, `exports`, `signals` (opcional) e hooks Godot-style (`_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`, `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`).
- Define **tabela global `nengine`** como único namespace de injeção: `nengine.Vec2(x, y)`, `nengine.Color(r, g, b, a)`, `nengine.MouseButton.Left`, `nengine.NodeRef(path)`, `nengine.signal("string")`, e bindings de tipos (`nengine.Node2D`, `nengine.Camera2D`, etc.). Filosofia LÖVE-style: scripts não fazem `require` de tipos da engine; eles são alcançáveis via `nengine.*`.
- Resolve `require "scripts.utils"` dentro do bundle via `package.searchers` customizado que delega leitura ao `BundleSource`. Sem isso, `require` cai para o caminho default de LuaJ (que busca no filesystem real e não vê o bundle classpath).
- `LuaScriptHost.attach(node, script)` cria uma userdata Lua que envolve o Node Kotlin, expondo properties (`self.position`, `self.rotation`, `self.scale`, `self.world()`, `self.tree`, `self.scripted` etc.) e signals built-in (`area_entered`, `body_entered`, …) via `__index`/`__newindex` metatables.
- Publica stubs **LuaCATS** em `:engine-bundle-lua/src/main/resources/stubs/engine/*.lua` cobrindo `Node`, `Node2D`, `Camera2D`, `Label`, `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`, `MouseButton`, `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `RigidBody2D`, `CollisionShape2D`, `Shape2D`, `RectangleShape2D`, `CircleShape2D`, `Timer`, `Signal`, `Renderer`, `Input` — formato `---@class` consumido por `sumneko-lua` / VSCode Lua language server.
- **BREAKING**: `:games:tictactoe` deixa de depender de `:engine-bundle-python` e passa a depender de `:engine-bundle-lua`. `scripts/board.py` é removido; `scripts/board.lua` é criado com semântica idêntica. `Main.kt` troca `PythonScriptHost.create()` por `LuaScriptHost.create()`. `scene.json` muda apenas o path `"scripts/board.py"` → `"scripts/board.lua"`.
- Atualiza `CLAUDE.md` e `ROADMAP.md` para refletir o novo papel de TTT (sentinela do segundo backend de render **e** do segundo backend de scripting) e a coexistência das duas implementações de `ScriptHost`.

## Capabilities

### New Capabilities

- `lua-scripting`: implementação concreta de `ScriptHost` para scripts `.lua` no módulo `:engine-bundle-lua`, usando LuaJ 3.0.x. Define o contrato de script (chunk retorna tabela), o namespace `nengine.*` injetado em cada chunk, a resolução de `require` via `BundleSource`, a discovery estática de exports/signals lendo a tabela retornada, e o despacho de hooks Godot-style do Kotlin para Lua.

### Modified Capabilities

- `tictactoe-sample`: jogo da velha migra de Python para Lua. Todos os requisitos que mencionam `:engine-bundle-python`, `PythonScriptHost`, ou `scripts/board.py` são substituídos pelos equivalentes Lua. A descrição do propósito do módulo é atualizada para refletir o novo papel "sentinela do segundo backend de scripting".

## Impact

- **Código novo**: novo módulo Gradle `:engine-bundle-lua` (build script + Kotlin source + runtime helpers Lua + stubs LuaCATS).
- **Código modificado**: `:games:tictactoe` (build script, `Main.kt`, `scene.json`, scripts).
- **Código removido**: `:games:tictactoe/src/main/resources/tictactoe/scripts/board.py`.
- **Dependências**: adiciona `org.luaj:luaj-jse:3.0.x` (ou versão equivalente publicada) em `:engine-bundle-lua`. Nenhum outro módulo recebe nova dependência transitiva.
- **APIs**: `:engine` e `:engine-bundle` ficam intactos — a SPI `ScriptHost` já cobre o caso. `BundleLoader` segue aceitando `scripting: ScriptHost?` como antes; o único requisito é `host.extension` casar com cada `script` path do bundle.
- **Documentação**: atualizações no `CLAUDE.md` (seção "Module Structure", "Scripting contract", instruções de IDE para stubs LuaCATS) e `ROADMAP.md` (marca Lua como segundo scripting feito).
- **Riscos**: LuaJ é interpretado e sem JIT em JDKs padrão; performance pode ser menor que GraalPy em scripts com loops pesados. O TTT é leve (poucas mutações por frame) e não estressa esse eixo — está intencionalmente fora do escopo da change (sem benchmark formal).
