# python-scripting Specification

## Purpose

Implementação concreta de `ScriptHost` para scripts Python `.py` no módulo `:engine-bundle-python`, usando GraalPy 24.x. É a primeira impl da SPI definida em `script-host`. Encapsula tipos `org.graalvm.polyglot.*` — esses não vazam para `:engine`, `:engine-bundle` nem para os jogos. Jogos que dependem desse módulo herdam o custo do runtime GraalPy; jogos que não dependem (ex.: `:games:tictactoe`) ficam livres dele.

## Requirements

### Requirement: engine-bundle-python module hosts the Python ScriptHost

O projeto SHALL prover um módulo Gradle `:engine-bundle-python` que depende de `:engine`, `:engine-bundle` e de GraalPy 24.x (`org.graalvm.polyglot:polyglot` + `org.graalvm.polyglot:python`). Esse módulo MUST ser o único local que conhece tipos de `org.graalvm.polyglot.*` no projeto. O módulo MUST NOT ser dependência de `:engine`, `:engine-bundle`, `:engine-skiko`, `:engine-compose`, ou de jogos que não usem scripting Python.

#### Scenario: engine-bundle-python exists with the right dependencies

- **WHEN** a build configuration de `:engine-bundle-python` é inspecionada
- **THEN** declara dependência em `:engine`
- **AND** declara dependência em `:engine-bundle`
- **AND** declara dependência em GraalPy (polyglot + python language)

#### Scenario: engine modules do not depend on engine-bundle-python

- **WHEN** a configuração de build de `:engine`, `:engine-bundle`, `:engine-skiko` e `:engine-compose` é inspecionada
- **THEN** nenhum deles declara `:engine-bundle-python` como dependência

#### Scenario: GraalPy is contained in engine-bundle-python

- **WHEN** o classpath compilação de `:engine`, `:engine-bundle`, `:engine-skiko` e `:engine-compose` é resolvido
- **THEN** nenhum artefato `org.graalvm.polyglot:*` está presente

### Requirement: PythonScriptHost implements ScriptHost for .py files

`:engine-bundle-python` SHALL expor a classe `PythonScriptHost : ScriptHost` cuja `extension` é `.py`. A classe MUST ser construída via a factory pública `PythonScriptHost.create()`, que MUST montar um `org.graalvm.polyglot.Context` default (Python language, `allowAllAccess`, opções `PosixModuleBackend=java`, `EmulateJython=true`, `WarnInterpreterOnly=false`). O construtor primário MUST ter visibilidade `internal` e aceitar um `Context` pré-construído, para que testes do módulo possam injetar `Context` customizado sem expor `org.graalvm.polyglot.*` na API pública. Cada `PythonScriptHost` MUST manter um único `Context` Polyglot compartilhado entre todos os scripts que carrega. Instâncias de `PythonScriptHost` MUST ser repassadas explicitamente ao `BundleLoader` pelo caller (via parâmetro `scripting`); a classe MUST NOT registrar a si mesma em nenhum singleton global.

#### Scenario: PythonScriptHost.create returns a usable host for .py

- **WHEN** código chama `PythonScriptHost.create()`
- **THEN** a função retorna uma instância de `PythonScriptHost` cuja `extension == ".py"`
- **AND** essa instância pode ser passada diretamente como `scripting` em `BundleLoader.fromResources("pong", scripting = host)`
- **AND** nenhuma função de registro global é invocada

#### Scenario: Primary constructor is internal

- **WHEN** a API pública de `PythonScriptHost` é inspecionada
- **THEN** o construtor primário tem visibilidade `internal`
- **AND** nenhum construtor `public` aceitando `org.graalvm.polyglot.Context` é exposto
- **AND** o único entry point público de construção é a factory `create()`

#### Scenario: Multiple scripts share one Polyglot Context per host

- **GIVEN** um `PythonScriptHost` construído via `create()` e dois scripts `a.py` e `b.py` no bundle
- **WHEN** ambos são carregados pelo mesmo host (via `BundleLoader.fromResources(name, scripting = host)`)
- **THEN** o `Context` Polyglot criado pelo host é exatamente um
- **AND** ambos os scripts são avaliados nesse contexto

### Requirement: extends declaration in Python module

Todo script Python carregado por `PythonScriptHost` MUST declarar o tipo Node que estende como **primeira linha não-vazia** do módulo, em um dos dois formatos: como docstring (`"""extends <NodeType>"""`) ou como comentário (`# extends <NodeType>`). `<NodeType>` MUST ser o nome simples (`Node2D`, `BoxCollider`, etc.) ou FQN (`com.neoutils.engine.scene.Node2D`) de um tipo registrado no `NodeRegistry`. Scripts sem declaração `extends` MUST falhar no `load`. O resolvedor MUST consultar `NodeRegistry` por nome simples primeiro (varrendo tipos registrados) e cair para FQN se houver match.

#### Scenario: Docstring extends form is accepted

- **GIVEN** um script começando com `"""extends Node2D"""`
- **WHEN** `PythonScriptHost.load(path, bundle)` é chamado
- **THEN** `Script.extendsType` é `Node2D::class`

#### Scenario: Comment extends form is accepted

- **GIVEN** um script começando com `# extends Node2D`
- **WHEN** `load` é chamado
- **THEN** `Script.extendsType` é `Node2D::class`

#### Scenario: Missing extends fails fast

- **GIVEN** um script sem `extends` na primeira linha não-vazia
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o path do script e indica a falta da declaração `extends`

#### Scenario: Unknown extends type fails fast

- **GIVEN** um script com `# extends BananaNode` e `BananaNode` não registrado
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia `BananaNode` e o path do script

### Requirement: AST inspector discovers @export via top-level type annotations

`PythonScriptHost.load` MUST descobrir `Script.exports` parseando o source do script com o módulo `ast` do Python (rodando dentro do Context Polyglot, mas sem executar o módulo do script). Cada nó `ast.AnnAssign` no top-level do módulo cujo target é um `Name`, cuja anotação resolve para um dos tipos suportados, e cujo `value` é uma expressão estaticamente avaliável (literal numérico, string, booleano, `None`, ou chamada simples de um tipo conhecido como `Vec2(0, 0)`) MUST virar um `ExportedProperty`. Quando a anotação top-level é exatamente o identificador `Signal`, o item MUST NÃO ser tratado como `ExportedProperty` (não vai para o bag `properties` serializado); em vez disso, MUST ser registrado como signal-slot descoberto via `Script.signals: Map<String, SignalDeclaration>`. O valor associado MUST ser uma chamada `signal(<typeHint?>)` — outras formas falham com mensagem clara nomeando o script e a linha.

#### Scenario: Signal annotation discovers a signal slot

- **GIVEN** script Python com top-level `scored: Signal = signal(str)`
- **WHEN** `load` é chamado
- **THEN** `Script.signals` contém uma entrada com nome `scored`
- **AND** `Script.exports` NÃO contém entrada para `scored`

#### Scenario: Signal without signal() call fails fast

- **GIVEN** script com top-level `scored: Signal = None`
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o script, a linha e o slot `scored`
- **AND** a mensagem indica que o valor esperado é `signal(<typeHint>)`

#### Scenario: Top-level annotated assignment becomes an export

- **GIVEN** script Python com `speed: float = 360.0` no top-level
- **WHEN** `load` é chamado
- **THEN** `exports` contém `(name="speed", type=Float::class, default=360.0)`

#### Scenario: Nested annotated assignment is ignored

- **GIVEN** script com `def foo(): x: int = 1`
- **WHEN** `load` é chamado
- **THEN** `exports` NÃO contém entrada para `x`

#### Scenario: Vec2 default literal is parsed

- **GIVEN** script com `size: Vec2 = Vec2(16.0, 96.0)`
- **WHEN** `load` é chamado
- **THEN** `exports` contém `(name="size", type=Vec2::class, default=Vec2(16f, 96f))`

#### Scenario: Optional type is detected as nullable

- **GIVEN** script com `up_key: Optional[Key] = None`
- **WHEN** `load` é chamado
- **THEN** `exports` contém `(name="up_key", type=Key::class, default=null)`
- **AND** o ExportedProperty é tratado como nullable na rotina de roteamento de `properties`

### Requirement: ScriptInstance attaches self as the host Node

`PythonScriptHost.attach(node, script)` MUST instanciar o módulo Python no Context (executando seu top-level uma única vez se ainda não foi executado), depois injetar `node` como `self` para as chamadas de hook. A injeção MUST garantir que dentro de um hook `_process(self, dt)` a expressão `self.transform` chama o getter Kotlin `Node2D.transform` (não cria atributo Python). Atributos `@export` MUST ser visíveis em `self` (leitura e escrita), correspondendo aos campos do Node ou a um proxy.

#### Scenario: self references the host Node

- **GIVEN** um script Python que dentro de `_process` faz `self.transform.position.y += 1.0`
- **AND** o script anexado a um `Node2D` com `position = (0, 0)`
- **WHEN** `_process(dt=1.0)` é chamado
- **THEN** o `Node2D.transform.position.y` agora é `1.0` (mutação refletida no Node Kotlin)

#### Scenario: Exported props readable via self

- **GIVEN** um script com `speed: float = 360.0` e `properties: {"speed": 480.0}` no scene.json (roteado como export pelo loader)
- **WHEN** `_process` lê `self.speed`
- **THEN** o valor lido é `480.0` (override do scene.json)

### Requirement: PythonScriptInstance implements currentValue for round-trip

O `ScriptInstance` Python MUST implementar `currentValue(name: String): Any?` lendo o atributo Python da instância pelo mesmo nome e devolvendo o valor convertido para o tipo Kotlin declarado em `ExportedProperty.type`. A conversão MUST ser o inverso de `PropCoercion.coerce`: floats Python viram `Float`, ints viram `Int`/`Long` conforme o tipo declarado, `Vec2` proxy vira `com.neoutils.engine.math.Vec2`, etc. Se o atributo Python não existe (porque o export nunca foi acessado e o Python ainda não materializou o slot), `currentValue` MUST devolver o `ExportedProperty.default`.

Esse método MUST ser usado apenas por `SceneLoader.save`. Não tem efeito colateral no script (não executa hooks, não dispara `_ready`).

#### Scenario: currentValue returns Python attribute converted to Kotlin type

- **GIVEN** script com `speed: float = 360.0`, anexado a um Node, com `setExport("speed", 480.0f)` previamente chamado
- **WHEN** código chama `instance.currentValue("speed")`
- **THEN** o valor devolvido é o `Float` Kotlin `480.0f`
- **AND** NÃO é um `PolyglotValue` ou `Double` cru do GraalPy

#### Scenario: currentValue returns default when attribute is absent

- **GIVEN** script com `speed: float = 360.0`, anexado mas sem qualquer `setExport` nem leitura prévia
- **WHEN** código chama `instance.currentValue("speed")`
- **THEN** o valor devolvido é `360.0f` (o default declarado)

#### Scenario: currentValue on unknown name fails

- **GIVEN** script cujo `exports` não inclui `mystery`
- **WHEN** código chama `instance.currentValue("mystery")`
- **THEN** uma `IllegalArgumentException` é lançada
- **AND** a mensagem nomeia `mystery` e o path do script

### Requirement: Hooks delegate from Node to ScriptInstance

`PythonScriptHost.attach` MUST retornar um `ScriptInstance` cujos métodos (`onEnter`, `onProcess`, `onPhysicsProcess`, `onDraw`, `onExit`, `onCollide`) invocam os métodos Python correspondentes (`_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`, `_on_collide`) no objeto-instância. Métodos Python ausentes MUST resultar em no-op no `ScriptInstance` (não exceção). A conversão de nomes MUST ser fixa e segue 100% a convenção Godot:

```
Kotlin / SPI                Python (objeto-instância)
─────────────────────────────────────────────────────
onEnter()                   _ready(self)
onProcess(dt)               _process(self, dt)
onPhysicsProcess(dt)        _physics_process(self, dt)
onDraw(renderer)            _draw(self, renderer)
onExit()                    _exit_tree(self)
onCollide(other)            _on_collide(self, other)
```

Os antigos nomes Python `on_enter`, `on_update`, `on_render`, `on_exit`, `on_collide` SHALL NOT ser reconhecidos. Scripts que ainda os usem MUST resultar em no-op silencioso (o `ScriptInstance` chama apenas os nomes acima).

#### Scenario: _process is dispatched

- **GIVEN** um script Python que define `_process(self, dt)` que incrementa um contador
- **WHEN** o engine invoca `instance.onProcess(0.016f)`
- **THEN** `_process` é chamado com `dt ≈ 0.016`
- **AND** o contador no Python é incrementado

#### Scenario: _physics_process is dispatched at fixed step

- **GIVEN** um script Python que define `_physics_process(self, dt)`
- **WHEN** o engine invoca `instance.onPhysicsProcess(1f / 60f)`
- **THEN** `_physics_process` é chamado com `dt == 1/60`

#### Scenario: _draw is dispatched with the renderer

- **GIVEN** um script Python que define `_draw(self, renderer)` que chama `renderer.drawRect(...)`
- **WHEN** o engine invoca `instance.onDraw(renderer)`
- **THEN** `_draw` é chamado com o renderer Kotlin
- **AND** `renderer.drawRect` é executado no backend

#### Scenario: _ready is dispatched

- **GIVEN** um script com `_ready(self)` que toca atributos do node
- **WHEN** o engine invoca `instance.onEnter()`
- **THEN** `_ready` é chamado uma vez antes de qualquer `_process`

#### Scenario: _exit_tree is dispatched

- **GIVEN** um script com `_exit_tree(self)` que loga
- **WHEN** o engine invoca `instance.onExit()`
- **THEN** `_exit_tree` é chamado uma vez

#### Scenario: _on_collide is dispatched

- **GIVEN** um script Kotlin-subclasse `BoxCollider` com `_on_collide(self, other)`
- **WHEN** o `PhysicsSystem` detecta sobreposição com outro `Collider`
- **THEN** `_on_collide` é chamado com o outro `Collider` como `other`

#### Scenario: Missing hooks are no-ops

- **GIVEN** um script Python que NÃO define `_physics_process`
- **WHEN** o engine invoca `instance.onPhysicsProcess(1f / 60f)`
- **THEN** nenhuma exceção é lançada
- **AND** o tick segue normalmente

#### Scenario: Legacy on_* names are not recognized

- **GIVEN** um script Python com `on_update(self, dt)` (nome antigo) mas sem `_process`
- **WHEN** o engine invoca `instance.onProcess(dt)`
- **THEN** `on_update` NÃO é chamado
- **AND** o tick comporta como se o hook fosse ausente (no-op)

### Requirement: Engine types are pre-bound in the Polyglot Context

`PythonScriptHost` MUST registrar bindings no Polyglot Context para que scripts referenciem tipos da engine sem `import`. Os bindings MUST incluir, no mínimo: `Vec2`, `Rect`, `Color`, `Transform`, `Key`, `MouseButton`, `NodeRef`, `Signal`, `BoxCollider`, `Node2D`, `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`. Adicionalmente, MUST expor uma factory function `signal(typeHint=None)` que constrói uma instância `Signal` (typeHint é apenas documentação, ignorada runtime). Os bindings MUST estar disponíveis dentro do top-level dos scripts (para AnnAssign `points: Polygon2D = ...` ou declarações `my_signal: Signal = signal(str)`) e dentro dos hooks (para uso em runtime).

#### Scenario: Signal factory creates instances

- **GIVEN** um script Python com top-level `scored: Signal = signal(str)`
- **WHEN** `PythonScriptHost.load` instancia o módulo
- **THEN** `module.scored` é uma instância de `Signal` que aceita `connect`/`emit`

#### Scenario: Camera2D is bound

- **WHEN** um script faz `c = Camera2D()` em qualquer hook
- **THEN** `c` é uma instância de `Camera2D` Kotlin acessível via interop GraalPy

#### Scenario: Color and visual primitives are bound

- **WHEN** um script faz `ColorRect()`, `Circle2D()`, `Line2D()`, `Polygon2D()`, `Label()` em algum hook
- **THEN** cada chamada retorna uma instância válida do tipo Kotlin correspondente

#### Scenario: Vec2 is usable without import

- **GIVEN** um script Python que contém `v = Vec2(3.0, 4.0)`
- **WHEN** o script é executado
- **THEN** `v` é uma instância de `com.neoutils.engine.math.Vec2` cuja `x=3f` e `y=4f`

#### Scenario: Key enum is usable without import

- **GIVEN** um script Python que contém `if self.input.is_key_down(Key.W): ...`
- **WHEN** o script é executado
- **THEN** `Key.W` resolve para o enum constant `com.neoutils.engine.input.Key.W`

### Requirement: PyI stubs are published as module resources

`:engine-bundle-python` SHALL publicar arquivos `.pyi` (PEP 561 stubs) em `src/main/resources/stubs/engine/` cobrindo no mínimo: `Node`, `Node2D`, `BoxCollider`, `Renderer`, `Input`, `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`. Os stubs MUST refletir a API Kotlin pública de cada um desses tipos.

#### Scenario: Stubs resource directory exists

- **WHEN** o jar de `:engine-bundle-python` é construído
- **THEN** contém o diretório `stubs/engine/` com pelo menos `__init__.pyi`, `scene.pyi`, `math.pyi`, `render.pyi`, `input.pyi`, `physics.pyi`, `serialization.pyi`

#### Scenario: Stubs reflect public Kotlin API

- **GIVEN** o stub `engine/math.pyi`
- **WHEN** ele é inspecionado
- **THEN** declara `class Vec2: x: float; y: float; def __init__(self, x: float, y: float) -> None: ...`
- **AND** as assinaturas batem com as propriedades públicas da `Vec2` Kotlin

### Requirement: Polyglot Context is eagerly initialized

Para evitar cold start visível durante o primeiro frame, `PythonScriptHost.create()` MUST inicializar o `Context` Polyglot e instalar bindings + runtime Python **antes** de retornar. Quando o caller passa essa instância ao `BundleLoader`, o primeiro `load` MUST encontrar o `Context` já pronto, sem incluir custo de boot.

#### Scenario: Context is ready when first load runs

- **WHEN** o tempo entre o retorno de `PythonScriptHost.create()` e a primeira chamada de `BundleLoader.fromResources(..., scripting = host)` é medido
- **THEN** o Context já está construído
- **AND** o `load` em si não inclui custo de boot do Context (apenas parse + eval do módulo)

### Requirement: ScriptInstance exposes declared signals

`ScriptInstance` SHALL expose `signals: Map<String, Signal<*>>` populada durante `attach`. Cada signal declarado em `Script.signals` MUST resultar em uma instância `Signal<*>` ÚNICA por `ScriptInstance` (ou seja, por Node) — duas instâncias do mesmo `Script` em Nodes diferentes têm `Signal`s independentes. O Python MUST acessar os signals via `self.<name>` (ex.: `self.scored.emit("Left")`); por baixo do capô, isso é roteado para a instância Kotlin compartilhada por aquele Node.

#### Scenario: Same script on two nodes yields independent signals

- **GIVEN** um script `paddle.py` com `scored: Signal = signal(str)` aplicado a dois Nodes diferentes na cena
- **WHEN** código conecta um handler em `node1.scriptInstance.signals["scored"]` e emite em `node2.scriptInstance.signals["scored"]`
- **THEN** o handler em `node1` NÃO é invocado

#### Scenario: Python accesses self.<signal_name>

- **GIVEN** um script com `scored: Signal = signal(str)` e um hook `_process(self, dt): self.scored.emit("Left")`
- **WHEN** `instance.onProcess(0.016f)` é invocado
- **THEN** o `Signal` Kotlin recebe `emit("Left")`
- **AND** handlers conectados em Kotlin são notificados

### Requirement: Python connect() accepts Python callables

`Signal<T>.connect(handler)` MUST aceitar como `handler`:
- uma função `(T) -> Unit` Kotlin,
- uma função Python (lambda ou `def`), via interop GraalPy (que expõe Python callables como instâncias de `org.graalvm.polyglot.Value` invocáveis pelo lado Kotlin).

Quando o handler é Python, a invocação MUST funcionar de ambos os lados:
- Python invoca `signal.emit(value)` → handler Python recebe `value`;
- Kotlin invoca `signal.emit(value)` → handler Python recebe `value` via conversão `Value.execute(value)`.

#### Scenario: Python handler connected and emitted from Python

- **GIVEN** um script com `self.target.connect(self._on_event)` onde `target: Signal = signal(int)` em outro nó
- **WHEN** o outro nó faz `self.target.emit(42)` no Python
- **THEN** `_on_event(self, 42)` é invocado

#### Scenario: Python handler connected, emitted from Kotlin

- **GIVEN** um Python handler conectado a um `Signal<Int>` declarado em script Python
- **WHEN** código Kotlin invoca `signal.emit(99)` diretamente
- **THEN** o handler Python é invocado com `99`

### Requirement: PyI stubs reflect renamed hooks

Os stubs `.pyi` publicados como recursos em `engine-bundle-python/src/main/resources/stubs/engine/` MUST refletir os novos nomes de hook (`_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`, `_on_collide`). Os stubs MUST incluir `Signal` (com `connect`, `disconnect`, `emit`) e `signal(typeHint=None) -> Signal`. Os stubs MUST incluir tipos novos: `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`. Os stubs MUST NÃO incluir `Shape` (removido) nem `Text` (renomeado para `Label`).

#### Scenario: Stub of Node has new hook names

- **WHEN** o stub `engine/node.pyi` (ou equivalente) é inspecionado
- **THEN** contém declarações `_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`
- **AND** NÃO contém `on_enter`, `on_update`, `on_render`, `on_exit`

#### Scenario: Stub of Signal exposes connect/emit/disconnect

- **WHEN** o stub `engine/signal.pyi` (ou onde Signal reside) é inspecionado
- **THEN** contém método `connect(self, handler: Callable[..., None]) -> Disposable`
- **AND** contém método `emit(self, value) -> None`

#### Scenario: Shape stub does not exist

- **WHEN** os stubs sob `engine/` são listados
- **THEN** nenhum arquivo `shape.pyi` ou declaração `class Shape` aparece
