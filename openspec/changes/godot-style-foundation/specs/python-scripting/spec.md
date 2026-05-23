## MODIFIED Requirements

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

### Requirement: AST inspector discovers @export via top-level type annotations

`PythonScriptHost.load` MUST descobrir `Script.exports` parseando o source do script com o módulo `ast` do Python (rodando dentro do Context Polyglot, mas sem executar o módulo do script). Cada nó `ast.AnnAssign` no top-level do módulo cujo target é um `Name`, cuja anotação resolve para um dos tipos suportados, e cujo `value` é uma expressão estaticamente avaliável (literal numérico, string, booleano, `None`, ou chamada simples de um tipo conhecido como `Vec2(0, 0)`) MUST virar um `ExportedProperty`. Quando a anotação top-level é exatamente o identificador `Signal`, o item MUST NÃO ser tratado como `ExportedProperty` (não vai para o serializado `props`); em vez disso, MUST ser registrado como signal-slot descoberto via `Script.signals: Map<String, SignalDeclaration>`. O valor associado MUST ser uma chamada `signal(<typeHint?>)` — outras formas falham com mensagem clara nomeando o script e a linha.

#### Scenario: Signal annotation discovers a signal slot

- **GIVEN** script Python com top-level `scored: Signal = signal(str)`
- **WHEN** `load` é chamado
- **THEN** `Script.signals` contém uma entrada com nome `scored`
- **AND** `Script.exports` NÃO contém entrada para `scored`

#### Scenario: Top-level annotated assignment becomes an export

- **GIVEN** script Python com `speed: float = 360.0` no top-level
- **WHEN** `load` é chamado
- **THEN** `exports` contém `(name="speed", type=Float::class, default=360.0)`

#### Scenario: Signal without signal() call fails fast

- **GIVEN** script com top-level `scored: Signal = None`
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o script, a linha e o slot `scored`
- **AND** a mensagem indica que o valor esperado é `signal(<typeHint>)`

## ADDED Requirements

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
