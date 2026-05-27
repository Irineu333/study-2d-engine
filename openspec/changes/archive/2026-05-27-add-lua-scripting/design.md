## Context

A engine define `ScriptHost` em `:engine-bundle` como SPI agnóstica de linguagem (`val extension: String; fun load(...); fun attach(...)`). Hoje só `PythonScriptHost` (em `:engine-bundle-python`, via GraalPy) realiza essa SPI. `BundleLoader.fromResources(name, scripting = host)` é o único ponto de uso e despacha por `host.extension`: ele exige que cada `script` path do bundle termine com a extensão do host fornecido.

A change adiciona uma segunda realização da SPI usando **Lua via LuaJ**, e migra `:games:tictactoe` (a sentinela vivente do `ComposeHost`) de Python para Lua. Essa migração simultânea exercita o invariante "backend de render é agnóstico de scripting" sem mais conversa.

Stakeholders e constraints relevantes:

- `:engine` e `:engine-bundle` não podem ganhar nenhuma dependência transitiva de LuaJ (invariante "scripting é opt-in por módulo de jogo").
- Tipos `org.luaj.vm2.*` ficam estritamente confinados a `:engine-bundle-lua`, da mesma forma que `org.graalvm.polyglot.*` está confinado a `:engine-bundle-python`.
- A SPI atual aceita um único `ScriptHost` por bundle. Multi-host por bundle (`.py` + `.lua` na mesma cena) é **fora de escopo**; cada bundle continua single-host.
- Filosofia adotada: ao adotar Lua, adota-se sua cultura — namespace único `nengine.*`, tabela retornada por chunk, `require` via `package.searchers`, LuaCATS para IDE.

## Goals / Non-Goals

**Goals:**

- Provar concretamente que `ScriptHost` é polimórfica: a mesma SPI suporta duas linguagens drasticamente diferentes (dinâmica/OO vs prototype-based-com-tabela).
- Trazer Lua como segundo backend de scripting, idiomático para a comunidade Lua (LÖVE-style).
- Confinar LuaJ a `:engine-bundle-lua`, sem vazamento para `:engine`, `:engine-bundle`, backends de render, ou jogos que não optem por Lua.
- Migrar `:games:tictactoe` para Lua, transformando-o na sentinela viva da combinação Compose+Lua.
- Publicar stubs LuaCATS para autocomplete e type-check em `sumneko-lua` / VSCode.

**Non-Goals:**

- LuaJIT (via JNLua, GraalVM `lua`, ou outro runtime). Fica como evolução futura quando/se performance vier a importar; a SPI já permite trocar a impl sem mexer no contrato.
- Multi-host por bundle (`.py` + `.lua` na mesma cena). YAGNI; bundles continuam single-host. A SPI não muda.
- Benchmark Python vs Lua. Sem ferramenta de medição nova; sem números no design.
- Migração de outros jogos (`:games:pong`, demos). Pong segue Python+Skiko; demos seguem Skiko code-only. Só TTT muda.
- Hot-reload de scripts. Cada `load` continua sendo one-shot, igual ao Python.
- Tipagem estática em runtime (LuaCATS é só doc para IDE).

## Decisions

### D1. LuaJ 3.0.x como runtime, embarcado puro JVM

**Decisão:** depender de `org.luaj:luaj-jse:3.0.2` (ou versão estável corrente equivalente) em `:engine-bundle-lua`.

**Alternativas consideradas:**

| Opção | Por quê não |
|---|---|
| LuaJIT via JNLua | Exige libs nativas por OS → quebra o princípio "zero hassle de distribuição" |
| GraalVM `lua` polyglot | Linguagem não-oficial, abandonada na prática, churn alto |
| LuaJ 2.x | Lua 5.1, sem `goto`/integer-division; 3.x é Lua 5.2-ish, mais próximo do moderno |

**Rationale:** LuaJ é JAR puro, exatamente o análogo de Skiko/Compose no eixo de render. Performance é inferior a runtimes com JIT, mas TTT é leve e a engine inteira é interpretativa em sentido amplo (gameplay-sized). O usuário aceitou o trade explicitamente: LuaJIT fica como melhoria futura.

### D2. Chunk de script retorna uma tabela (Lua-OO, LÖVE-style)

**Decisão:** cada arquivo `.lua` é um chunk LuaJ cujo último `return` MUST ser uma tabela com forma:

```lua
return {
  extends = "Node2D",                       -- obrigatório: string
  exports = {                               -- opcional
    speed = { type = "float", default = 360 },
    ai    = { type = "bool",  default = false },
  },
  signals = {                               -- opcional
    scored = "string",                      -- valor é typeHint puramente documental
  },
  _ready          = function(self) ... end,
  _process        = function(self, dt) ... end,
  _physics_process= function(self, dt) ... end,
  _draw           = function(self, renderer) ... end,
  _exit_tree      = function(self) ... end,
  _on_area_entered= function(self, area) ... end,
  _on_area_exited = function(self, area) ... end,
  _on_body_entered= function(self, body) ... end,
  _on_body_exited = function(self, body) ... end,
}
```

**Alternativas consideradas:**

| Opção | Por quê não |
|---|---|
| `-- extends Node2D` + funções top-level (espelhar Python) | Sem tabela retornada, descoberta de exports exigiria regex/AST manual — exatamente o que escolhemos evitar |
| Defold-style `go.property("speed", 100)` chamado em init | Exige rodar código pra descobrir exports, dois pés num só estilo (Lua-OO híbrido com declarativo) |
| EmmyLua `---@field` annotations | Anotações são para IDE, não runtime |

**Rationale:** descoberta de exports e signals fica trivial — execute o chunk uma vez (no `load`), leia `.extends`, `.exports`, `.signals` da tabela retornada. Sem AST, sem regex, sem ambiguidade. É também a forma mais idiomática para um luaista de jogos vindo de LÖVE/Defold.

### D3. Tabela global `nengine.*` como único namespace

**Decisão:** `LuaScriptHost` injeta em `_G` uma única tabela `nengine` que carrega todos os símbolos da engine acessíveis ao script:

```lua
-- factories de valor (data classes Kotlin imutáveis)
nengine.Vec2(x, y)
nengine.Color(r, g, b, a)
nengine.Rect(position, size)
nengine.Transform(position, scale, rotation)
nengine.NodeRef(path)
nengine.signal(typeHint)              -- factory de Signal<Any?>

-- enums
nengine.Key.W, nengine.Key.Space, ...
nengine.MouseButton.Left, .Right, .Middle

-- tipos de Node (binding direto para a KClass; usado em `extends` strings)
nengine.Node2D, nengine.Camera2D, nengine.Label, nengine.ColorRect, ...
nengine.Area2D, nengine.StaticBody2D, nengine.CharacterBody2D, nengine.RigidBody2D
nengine.CollisionShape2D, nengine.RectangleShape2D, nengine.CircleShape2D
nengine.Timer

-- lookup utilitário (cross-script)
nengine.script_of(node)               -- equivalente a Python `script_of(...)`
```

**Alternativas consideradas:**

| Opção | Por quê não |
|---|---|
| Símbolos top-level globais (`Vec2`, `Color`, …) | Imita Python via GraalPy, mas não é como bibliotecas Lua vivem. Polui `_G` e torna confuso o que é da engine vs do script |
| `local engine = require("nengine")` em cada script | Verboso e exige resolver `require` antes do chunk rodar; sem ganho ergonômico |

**Rationale:** "Ao adotar uma linguagem, adota-se sua filosofia." LÖVE expõe `love.*`, Defold expõe `vmath.*`/`go.*`. Um namespace único é o padrão da comunidade. Bonus: deprecação ou migração futura é mais cirúrgica — `nengine.Foo` é grep-able.

### D4. `require` dentro do bundle via `package.searchers` customizado

**Decisão:** `LuaScriptHost` registra um novo searcher em `package.searchers` (primeiro da lista, antes do searcher default de filesystem) que delega ao `BundleSource`. Para `require "scripts.utils"`:

1. Converte `"scripts.utils"` para `"scripts/utils.lua"`.
2. Chama `bundle.read("scripts/utils.lua")` (mesmo path API que o load principal usa).
3. Compila o conteúdo com LuaJ e devolve a função-chunk.

Searchers default (filesystem, `package.path`, `package.cpath`) MUST ser removidos para impedir acesso fora do bundle — o sandbox é parte do contrato.

**Alternativas consideradas:**

| Opção | Por quê não |
|---|---|
| Deixar para v2 (TTT é arquivo único) | O usuário decidiu v1; e é barato fazer agora |
| Implementar via `loader` global, não `package.searchers` | LuaJ implementa `package.searchers` corretamente; reaproveitar é mais barato e correto |

**Rationale:** isola o sandbox, mantém o paralelismo com Python (que também só vê o bundle), e é trivial em LuaJ.

### D5. Wrapper Lua do Node via `LuaUserdata` + metatable `__index`/`__newindex`

**Decisão:** cada `attach(node, script)` cria um `LuaTable` "instância" que combina:

- A tabela retornada pelo chunk (com hooks e exports).
- Uma metatable cujo `__index(self, key)` resolve, em ordem:
  1. Signal slots declarados em `script.signals` (per-Node `Signal<Any?>` instanciado no attach).
  2. Signal slots reflection-discovered em campos `val signal: Signal<*>` no Node Kotlin (espelhando o que `PythonScriptHost` faz pra `Timer.timeout`).
  3. Exports declarados em `script.exports`.
  4. Properties ergonômicas de `Node2D` (`position`, `rotation`, `scale`) via getter Kotlin direto.
  5. Campos públicos Kotlin do Node via reflexão (`node.name`, `node.tree`, etc.).
  6. Métodos do Node (`world()`, `findChild()`, etc.).
- `__newindex(self, key, value)` espelha a hierarquia para escrita (exports primeiro, depois properties de Node2D, depois fields de Node).

`self` passado a cada hook (`_ready(self)`, `_process(self, dt)`) é essa userdata. Não há "instância Lua separada do Node"; o wrapper é puro açúcar sobre o Node Kotlin.

**Alternativas consideradas:**

| Opção | Por quê não |
|---|---|
| Userdata pura (sem tabela) | Não tem como guardar exports/hooks por-instância sem reimplementar tabela manualmente |
| Tabela + acesso direto sem metatable | Forçaria copiar todos os fields do Node pra tabela no attach — frágil, fora-de-sincronia |

**Rationale:** metatable resolve dispatch em tempo natural; é o idioma Lua. `__index` em ordem garante que exports do script vencem fields homônimos do Node (consistente com a regra de colisão em `BundleLoader`).

### D6. Hook dispatch via campo de tabela, não método

**Decisão:** `LuaScriptInstance.onProcess(dt)` faz:

```kotlin
val fn = instanceTable.get("_process")
if (fn.isnil()) return
fn.invoke(varargsOf(instanceTable, valueOf(dt.toDouble())))
```

Note: `instanceTable` é passado como **primeiro arg**, e o hook usa a syntax `function(self, dt)` (não `function tbl:_process(dt)`). Isso casa com o que o usuário viu nos exemplos, é equivalente em runtime, e mantém o paralelismo com o despacho Python.

**Rationale:** zero ambiguidade entre "função top-level" e "método". `fn(self, dt)` chama o callable de qualquer lugar — pelo engine ou por outro script via `nengine.script_of(other_node)._process(other, dt)` (não que isso seja recomendado).

### D7. Discovery de exports e signals via execução do chunk no `load`

**Decisão:** `LuaScriptHost.load(path, bundle)`:

1. Lê fonte via `bundle.read(path)`.
2. Compila com LuaJ (`Globals.load`).
3. Executa o chunk **uma vez** num `Globals` com `nengine` injetado e o searcher customizado ativo.
4. Lê o `LuaTable` retornado:
   - `extends`: string, resolve via `NodeRegistry`. Falha-rápido com mensagem clara se ausente ou desconhecido.
   - `exports`: tabela `{ name = { type, default } }`. Cada entry vira um `ExportedProperty` (validado contra os mesmos tipos do Python: `Float`, `Int`, `Boolean`, `String`, `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`).
   - `signals`: tabela `{ name = typeHint }`. Cada entry vira `SignalDeclaration(name)`.
   - Hooks (`_ready`, `_process`, …): apenas notados como presentes/ausentes; armazenados como referência para dispatch.
5. Cacheia a tabela retornada por `path` para reuse no `attach` (cada attach cria um wrapper, mas reaproveita os mesmos hooks).

**Alternativas consideradas:**

| Opção | Por quê não |
|---|---|
| AST do Lua via parser próprio | LuaJ não expõe AST estável; reimplementar parser é overkill |
| Lazy load (executa só no attach) | Atrasa erros de `extends` desconhecido até o jogo rodar; quebra fail-fast |

**Rationale:** execução do chunk é barata, idiomática, e captura tudo de uma vez. Diferente do Python (que usa AST porque o módulo Python tem side effects), o chunk Lua é projetado para retornar uma tabela — executar é literalmente "carregar".

### D8. Cada attach instancia novos `Signal<Any?>` por slot

**Decisão:** `signals` em `script.signals` é descritor estático; em `attach`, cada slot vira uma `Signal<Any?>` Kotlin nova, indexada na userdata. Dois Nodes que compartilham o mesmo `script` têm signals independentes (paridade com Python).

**Rationale:** signals são event hubs por-instância. Mesmo padrão do `PythonScriptHost`.

### D9. Migração de TTT — substituição direta

**Decisão:** `:games:tictactoe` passa a depender de `:engine-bundle-lua` em vez de `:engine-bundle-python`. `scripts/board.py` é apagado. `scripts/board.lua` é criado portando a lógica (estado das 9 células, hit-test contra cells via mouse, vitória/empate, ghost, linha vencedora). `scene.json` muda **apenas** o path do script (`"scripts/board.py"` → `"scripts/board.lua"`); `properties` continuam idênticas. `Main.kt` troca a factory.

**Rationale:** mais limpo; reforça que o backend de render é agnóstico de scripting. A prova "Python+Compose funciona" passa a ser implícita (não há nada em `:engine-compose` que toque scripting). O CLAUDE.md é atualizado pra refletir.

### D10. Stubs LuaCATS publicados como resources

**Decisão:** `:engine-bundle-lua/src/main/resources/stubs/engine/*.lua` contém arquivos LuaCATS:

```lua
---@class Node2D : Node
---@field position Vec2
---@field rotation number
---@field scale Vec2
---@field tree SceneTree
local Node2D = {}

---@return Transform
function Node2D:world() end

return Node2D
```

Um arquivo por tipo (`node.lua`, `node2d.lua`, `camera2d.lua`, `vec2.lua`, `color.lua`, etc.), mais um `nengine.lua` que descreve a tabela global e seus campos. O `CLAUDE.md` ganha uma sub-seção paralela à de Python explicando como apontar `sumneko-lua` para esse diretório (`.luarc.json` com `workspace.library`).

**Rationale:** paridade de experiência com o Python (`.pyi` em `engine-bundle-python/resources/stubs/`). LuaCATS é o padrão de fato do ecossistema (sumneko consome).

## Risks / Trade-offs

- **[Risco] LuaJ é interpretado e mais lento que GraalPy em loops pesados.** → Mitigação: TTT é leve (poucas operações por frame); a primeira impl não estressa esse eixo. Se um jogo Lua futuro sofrer, dá pra plugar LuaJIT via JNLua (substituindo só `:engine-bundle-lua` ou adicionando um irmão `:engine-bundle-lua-jit`), sem mexer na SPI ou nos scripts. Documentado no proposal como melhoria futura.

- **[Risco] Bridges Java↔Lua (`LuaUserdata`/`CoerceJavaToLua`) têm casos de borda — em particular conversão de `Float`/`Double`/`Int` e propagação de exceções Kotlin.** → Mitigação: testes unitários cobrindo (a) leitura/escrita de `Vec2` via `self.position`, (b) chamada de `apply_impulse` em `RigidBody2D`, (c) exceção lançada de dentro de um hook propagando para o caller. Espelhar a cobertura que existe no `PythonScriptHost`.

- **[Risco] Sem benchmark, "Lua é mais leve" fica sem evidência.** → Mitigação: o proposal explicitamente diz que performance está fora do escopo. Nenhuma claim de performance no design ou nas specs. Se virar discussão depois, abre uma change separada com benchmark.

- **[Risco] Perder TTT-Python remove a única prova viva de Python+Compose.** → Mitigação: o invariante "ComposeHost é agnóstico de scripting" é arquitetural (`:engine-compose` não menciona scripting em lugar nenhum). Pong cobre Skiko+Python; TTT (após migração) cobre Compose+Lua. Cobertura cruzada é implícita pelo design da SPI, não exemplificada por jogo — aceito.

- **[Risco] Discrepâncias de comportamento sutis entre Python e Lua TTT (ex.: ordem de hit-test, ghost behavior).** → Mitigação: o spec delta de `tictactoe-sample` mantém todos os requisitos comportamentais existentes; apenas a sub-stack de scripting muda. Testar manualmente cada interação do jogo após a migração (clique em célula, vitória, empate, restart).

- **[Trade-off] Sintaxe Lua de tabela é mais verbosa que Python para estado por-instância** (`self._cells = { 0, 0, 0, ... }` vs `self._cells = [0]*9`). → Aceito; quem prefere uma é uma minoria fixa.

- **[Trade-off] `require` sandboxado significa que scripts não conseguem usar libs Lua de mercado** (luasocket, etc.). → Aceito; gameplay-scripting não precisa, e abrir o sandbox seria contraditório com o contrato atual do Python (que também sandboxa).

## Migration Plan

1. **Spike de fundação** (módulo `:engine-bundle-lua` skeleton + LuaJ dep + hello-world script Lua avulso compilando). Garante que a stack monta.
2. **`LuaScriptHost` com `extends` + exports + `nengine.*`** (sem signals, sem `require`). Suficiente pra anexar um script simples a um Node2D.
3. **Hooks de scene-tree** (`_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`).
4. **Hooks de colisão** (`_on_area_entered` etc.) + signals declaráveis em `signals = {...}`.
5. **Signal reflection** (acessar `timer.timeout` de Lua).
6. **`require` via `package.searchers`** com bundle source.
7. **Stubs LuaCATS** publicados em resources.
8. **TTT migration**: portar `board.py` → `board.lua`, trocar `Main.kt`, atualizar `scene.json` + `build.gradle.kts`.
9. **CLAUDE.md + ROADMAP.md update** refletindo Lua como segundo backend de scripting e novo papel do TTT.

Rollback é trivial: a change é aditiva em todos os módulos exceto `:games:tictactoe`. Se algo der errado no TTT, restaurar `board.py` + revisar `Main.kt`/`build.gradle.kts` é uma reversão de poucos arquivos.

## Open Questions

- **Q1.** O `extends` da tabela deve aceitar tanto string (`extends = "Node2D"`) quanto a KClass binding (`extends = nengine.Node2D`)?
  - Decisão proposta: **só string** na v1, pra simetria com Python (`# extends Node2D` também é string). KClass binding pode entrar depois sem quebra retroativa.

- **Q2.** Qual a ordem de signal-attribute discovery quando há colisão de nome entre signal declarado em `signals = {...}` e signal reflexivo do Kotlin Node?
  - Decisão proposta: declaração explícita em `signals = {...}` **ganha**. Falha-rápido com mensagem clara se um script tenta declarar `timeout` num Node `Timer` (Kotlin já tem `val timeout`).

- **Q3.** Stubs LuaCATS devem ficar num jar separado pra reuso por outros bundles, ou ficam dentro de `:engine-bundle-lua`?
  - Decisão proposta: **dentro de `:engine-bundle-lua/resources/stubs/`**, paralelo ao que `:engine-bundle-python` faz. Reuso futuro pode mover, sem urgência.
