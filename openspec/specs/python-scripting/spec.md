# python-scripting Specification

## Purpose

Implementação concreta de `ScriptHost` para scripts Python `.py` no módulo `:engine-bundle-python`, usando GraalPy 24.x. É a primeira impl da SPI definida em `script-host`. Encapsula tipos `org.graalvm.polyglot.*` — esses não vazam para `:engine`, `:engine-bundle` nem para os jogos. Jogos que dependem desse módulo herdam o custo do runtime GraalPy; jogos que não dependem (ex.: `:games:tictactoe`) ficam livres dele.

## Requirements

### Requirement: engine-bundle-python module hosts the Python ScriptHost

O projeto SHALL prover um módulo Gradle `:engine-bundle-python` que depende de `:engine`, `:engine-bundle` e de GraalPy 24.x (`org.graalvm.polyglot:polyglot` + `org.graalvm.polyglot:python`). Esse módulo MUST ser o único local que conhece tipos de `org.graalvm.polyglot.*` no projeto. O módulo MUST NOT ser dependência de `:engine`, `:engine-bundle`, `:engine-skiko`, ou de jogos que não usem scripting Python.

#### Scenario: engine-bundle-python exists with the right dependencies

- **WHEN** a build configuration de `:engine-bundle-python` é inspecionada
- **THEN** declara dependência em `:engine`
- **AND** declara dependência em `:engine-bundle`
- **AND** declara dependência em GraalPy (polyglot + python language)

#### Scenario: engine modules do not depend on engine-bundle-python

- **WHEN** a configuração de build de `:engine`, `:engine-bundle` e `:engine-skiko` é inspecionada
- **THEN** nenhum deles declara `:engine-bundle-python` como dependência

#### Scenario: GraalPy is contained in engine-bundle-python

- **WHEN** o classpath compilação de `:engine`, `:engine-bundle` e `:engine-skiko` é resolvido
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

### Requirement: Extends declarations support new collision types

A primeira linha não-vazia, não-comentário de um script Python que **não** seja `# extends <Type>` MUST falhar com mensagem clara nomeando o erro e o path do script. O tipo após `# extends` MUST resolver via `NodeRegistry`. Tipos suportados incluem `Node2D`, `Area2D`, `StaticBody2D`, `CharacterBody2D`, `RigidBody2D`, `CollisionShape2D`, `Camera2D`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `Label`, `Timer`, e qualquer subclasse de `Node` registrada. O tipo antigo `BoxCollider` MUST NOT ser reconhecido — tentar `# extends BoxCollider` MUST falhar com erro fatal idêntico ao de qualquer tipo desconhecido (mensagem nomeando o script e a linha).

#### Scenario: extends BoxCollider is rejected

- **GIVEN** um script Python com `# extends BoxCollider` na primeira linha
- **WHEN** `BundleLoader` tenta carregar o script
- **THEN** uma exceção fatal é lançada
- **AND** a mensagem nomeia o script e indica que `BoxCollider` não é um tipo conhecido

#### Scenario: extends CharacterBody2D works

- **GIVEN** um script `ship.py` com `# extends CharacterBody2D` na primeira linha
- **WHEN** o script é carregado e instanciado em um nó da cena
- **THEN** o `NodeRegistry` resolve para a classe Kotlin `CharacterBody2D` sem erro

#### Scenario: A script extending RigidBody2D loads and attaches

- **GIVEN** um script `ball.py` cuja primeira linha é `# extends RigidBody2D` e que define exports e hooks legítimos
- **WHEN** o `PythonScriptHost.load` parseia o script e o `BundleLoader` o anexa a um Node `RigidBody2D` carregado de `scene.json`
- **THEN** o attach completa sem erros
- **AND** os hooks `_ready`, `_physics_process`, `_on_body_entered`, etc. ficam disponíveis para a engine invocar

#### Scenario: A script with unknown extends type fails fast

- **GIVEN** um script cuja primeira linha é `# extends UnknownType123`
- **WHEN** `PythonScriptHost.load` é chamado
- **THEN** o load falha com mensagem que nomeia `UnknownType123` e o path do script

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

`PythonScriptHost.attach` MUST retornar um `ScriptInstance` cujos métodos invocam os métodos Python correspondentes no objeto-instância. O conjunto de hooks despachados é:

```
Kotlin / SPI                Python (objeto-instância)
─────────────────────────────────────────────────────
onEnter()                   _ready(self)
onProcess(dt)               _process(self, dt)
onPhysicsProcess(dt)        _physics_process(self, dt)
onDraw(renderer)            _draw(self, renderer)
onExit()                    _exit_tree(self)
onAreaEntered(area)         _on_area_entered(self, area)
onAreaExited(area)          _on_area_exited(self, area)
onBodyEntered(body)         _on_body_entered(self, body)
onBodyExited(body)          _on_body_exited(self, body)
```

Métodos Python ausentes MUST resultar em no-op no `ScriptInstance` (não exceção). O hook `onCollide(other)` / `_on_collide(self, other)` SHALL NOT existir mais e MUST NÃO ser despachado. Scripts que ainda definam `_on_collide` MUST resultar em no-op silencioso (o dispatcher não chama esse nome). Os antigos nomes Python `on_enter`, `on_update`, `on_render`, `on_exit`, `on_collide` também SHALL NOT ser reconhecidos.

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

#### Scenario: _on_area_entered is dispatched

- **GIVEN** um script Python que define `_on_area_entered(self, area)` que registra o evento
- **WHEN** o `PhysicsSystem` detecta que esta `CollisionObject2D` entrou em sobreposição com uma `Area2D`
- **THEN** `_on_area_entered` é chamado exatamente uma vez com o `Area2D` como `area`

#### Scenario: _on_body_entered is dispatched for body pairs

- **GIVEN** um script Python que define `_on_body_entered(self, body)`
- **WHEN** o `PhysicsSystem` detecta entrada em sobreposição com um `PhysicsBody2D`
- **THEN** `_on_body_entered` é chamado com o `PhysicsBody2D` como `body`

#### Scenario: Exit hooks fire on overlap end

- **GIVEN** um script que define `_on_area_exited` e `_on_body_exited`
- **WHEN** uma sobreposição previamente ativa termina
- **THEN** o hook correspondente é chamado uma vez

#### Scenario: _on_collide is no longer dispatched

- **GIVEN** um script Python com `_on_collide(self, other)` (nome legado)
- **WHEN** o `PhysicsSystem` detecta uma colisão envolvendo este Node
- **THEN** `_on_collide` NÃO é chamado
- **AND** a tentativa antiga não aparece em nenhum caminho de dispatch

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

`PythonScriptHost` MUST registrar bindings no Polyglot Context para que scripts referenciem tipos da engine sem `import`. Os bindings MUST incluir, no mínimo: `Vec2`, `Rect`, `Color`, `Transform`, `Key`, `MouseButton`, `NodeRef`, `Signal`, `Node2D`, `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `CanvasLayer`, `Panel`, `Button`, `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `RigidBody2D`, `CollisionShape2D`, `Shape2D`, `RectangleShape2D`, `CircleShape2D`. Os bindings de física antigos (`BoxCollider`, `Collider`) MUST NOT estar presentes. Adicionalmente, MUST expor uma factory function `signal(typeHint=None)` que constrói uma instância `Signal` (typeHint é apenas documentação, ignorada runtime). Os bindings MUST estar disponíveis dentro do top-level dos scripts (para AnnAssign `points: Polygon2D = ...` ou declarações `my_signal: Signal = signal(str)`) e dentro dos hooks (para uso em runtime).

#### Scenario: Binding RigidBody2D is exposed in the Context

- **WHEN** o `PythonScriptHost` é inicializado e um script declara `# extends RigidBody2D`
- **THEN** o parse do `# extends` resolve `RigidBody2D` contra o `NodeRegistry` com sucesso
- **AND** dentro do corpo do script, referenciar `RigidBody2D` como tipo (e.g. em annotated assignment) não levanta `NameError`

#### Scenario: Area2D and StaticBody2D are bound

- **WHEN** um script faz `a = Area2D(); b = StaticBody2D(); c = CharacterBody2D()` em qualquer hook
- **THEN** cada chamada retorna uma instância válida do tipo Kotlin correspondente

#### Scenario: Shape2D subtypes are bound

- **WHEN** um script faz `r = RectangleShape2D(); r.size = Vec2(10.0, 20.0)` e `c = CircleShape2D(); c.radius = 8.0`
- **THEN** ambas as instâncias são válidas
- **AND** os campos `size`/`radius` são lidos/escritos via interop

#### Scenario: BoxCollider binding is removed

- **WHEN** um script tenta `BoxCollider()`
- **THEN** o binding não existe e GraalPy levanta erro de nome
- **AND** scripts que ainda referenciem `BoxCollider` falham com mensagem clara

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

#### Scenario: UI nodes CanvasLayer, Panel, Button are bound

- **WHEN** um script faz `cl = CanvasLayer(); p = Panel(); b = Button()` em algum hook (ou referencia esses tipos em annotated assignment top-level)
- **THEN** cada chamada retorna uma instância válida do tipo Kotlin correspondente
- **AND** o script pode escrever `# extends Button` para anexar a um Button declarado em `scene.json`

#### Scenario: Button.pressed signal is connectable from Python

- **GIVEN** um script Python com `# extends Button` e `def _ready(self): self.pressed.connect(self._on_pressed)`
- **WHEN** o `Button` é clicado e a UI hit-test consome o clique
- **THEN** `self._on_pressed` é invocado exatamente uma vez por click cycle

#### Scenario: Vec2 is usable without import

- **GIVEN** um script Python que contém `v = Vec2(3.0, 4.0)`
- **WHEN** o script é executado
- **THEN** `v` é uma instância de `com.neoutils.engine.math.Vec2` cuja `x=3f` e `y=4f`

#### Scenario: Key enum is usable without import

- **GIVEN** um script Python que contém `if self.input.is_key_down(Key.W): ...`
- **WHEN** o script é executado
- **THEN** `Key.W` resolve para o enum constant `com.neoutils.engine.input.Key.W`

### Requirement: Python scripts use ergonomic accessors for local transform and world()

Python scripts attached to `Node2D` (or any subclass) SHALL access local transform components via the Kotlin-provided properties `position`, `rotation`, and `scale`, which are surfaced by GraalPy interop without additional binding glue. Scripts SHALL access world-space transform via `self.world()` (function call, returning a `Transform`), and world-space position via `self.world().position`. The legacy script-side helper `self.worldPosition()` MUST NOT appear in any engine-shipped Python script. Writes through `self.position = Vec2(...)`, `self.rotation = ...`, and `self.scale = Vec2(...)` MUST propagate to the host `Node2D.transform` and invalidate the world-transform cache identically to a Kotlin-side assignment.

#### Scenario: Python read of self.position mirrors host transform.position

- **GIVEN** a Python script attached to a `Node2D` whose `transform.position = Vec2(10f, 20f)` was set from Kotlin
- **WHEN** the script reads `self.position` inside any hook
- **THEN** the result equals `Vec2(10, 20)`

#### Scenario: Python write of self.position updates host transform and invalidates cache

- **GIVEN** a Python script attached to a parent `Node2D` that has a `Node2D` child, both with cached `world()` populated
- **WHEN** the script's `_physics_process` assigns `self.position = Vec2(99.0, 99.0)`
- **THEN** the host's `node.transform.position` equals `Vec2(99, 99)` after the call returns
- **AND** the next read of `child.world()` reflects the new parent position

#### Scenario: Partial component write fails fast in Python

- **GIVEN** a Python script attached to a `Node2D`
- **WHEN** the script executes `self.position.y = 50.0`
- **THEN** Python raises `AttributeError` because `Vec2.y` is a Kotlin `val` with no setter
- **AND** the error propagates per the existing fail-fast script contract

#### Scenario: self.world() returns the composed world transform

- **GIVEN** a Python script attached to a child `Node2D` whose parent has `transform.position = Vec2(100f, 0f)` and identity otherwise, and the child's local `transform.position = Vec2(5f, 0f)`
- **WHEN** the script reads `self.world().position`
- **THEN** the result equals `Vec2(105, 0)`

#### Scenario: worldPosition() does not exist on Python-side Node2D

- **WHEN** any engine-shipped Python script under `games/*/src/main/resources/*/scripts/` is grepped for `worldPosition`
- **THEN** no matches are found

### Requirement: PyI stubs are published as module resources

`:engine-bundle-python` SHALL publicar arquivos `.pyi` (PEP 561 stubs) em `src/main/resources/stubs/engine/` cobrindo no mínimo: `Node`, `Node2D`, `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `CanvasLayer`, `Panel`, `Button`, `CollisionObject2D`, `Area2D`, `StaticBody2D`, `CharacterBody2D`, `RigidBody2D`, `CollisionShape2D`, `RectangleShape2D`, `CircleShape2D`, `Renderer`, `Input`, `Vec2`, `Color`, `Rect`, `Transform`, `NodeRef`, `Key`, `MouseButton`, `Signal`, `Timer`, `TimerMode`. Os stubs MUST refletir a API Kotlin pública de cada um desses tipos, incluindo as três properties ergonômicas de `Node2D` (`position: Vec2`, `rotation: float`, `scale: Vec2`) e a função `world(self) -> Transform`. Os stubs MUST NÃO declarar `worldPosition` em nenhum tipo. A docstring de `Vec2` no stub MUST documentar que a classe é imutável (atribuição de componente individual via `v.y = ...` lança `AttributeError`). `Button` no stub MUST declarar o atributo `pressed: Signal` (signal built-in instanciado por instância).

#### Scenario: Stubs resource directory exists

- **WHEN** o jar de `:engine-bundle-python` é construído
- **THEN** contém o diretório `stubs/engine/` com pelo menos `__init__.pyi`, `scene.pyi`, `math.pyi`, `render.pyi`, `input.pyi`, `physics.pyi`, `serialization.pyi`

#### Scenario: Stubs reflect public Kotlin API

- **GIVEN** o stub `engine/math.pyi`
- **WHEN** ele é inspecionado
- **THEN** declara `class Vec2: x: float; y: float; def __init__(self, x: float, y: float) -> None: ...`
- **AND** as assinaturas batem com as propriedades públicas da `Vec2` Kotlin

#### Scenario: Node2D stub exposes ergonomic accessors and world()

- **GIVEN** o stub `engine/scene.pyi` (ou `engine/__init__.pyi`, conforme organização)
- **WHEN** o tipo `Node2D` é inspecionado
- **THEN** declara `position: Vec2`, `rotation: float`, `scale: Vec2` como atributos públicos mutáveis
- **AND** declara `def world(self) -> Transform: ...`
- **AND** NÃO declara `worldTransform` nem `worldPosition`

#### Scenario: UI stubs are present

- **WHEN** os stubs em `stubs/engine/` são inspecionados
- **THEN** `CanvasLayer`, `Panel`, `Button` aparecem declarados com seus campos públicos (`layer`, `size`, `color`, `border`, `text`, `normalColor`, `hoverColor`, `pressedColor`, `disabledColor`, `disabled`)
- **AND** `Button` declara `pressed: Signal` como atributo de instância

#### Scenario: Vec2 stub documents immutability

- **GIVEN** o stub de `Vec2`
- **WHEN** sua docstring é inspecionada
- **THEN** menciona que `Vec2` é um data class Kotlin imutável
- **AND** menciona que atribuir um componente individual (`v.y = ...`) lança `AttributeError`

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

### Requirement: PyI stubs reflect renamed hooks and collision types

Os stubs `.pyi` publicados como recursos em `engine-bundle-python/src/main/resources/stubs/engine/` MUST refletir os nomes de hook Godot-style (`_ready`, `_process`, `_physics_process`, `_draw`, `_exit_tree`) e os quatro hooks de colisão enter/exit (`_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`). Os stubs MUST NÃO incluir `_on_collide`. Os stubs MUST incluir `Signal` (com `connect`, `disconnect`, `emit`) e `signal(typeHint=None) -> Signal`. Os stubs MUST incluir tipos visuais: `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`. Os stubs MUST incluir os tipos de colisão novos: `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`, `Shape2D`, `RectangleShape2D`, `CircleShape2D`. O stub de `CollisionObject2D` MUST declarar os quatro signal-attributes built-in (`area_entered`, `area_exited`, `body_entered`, `body_exited`), todos `Signal`. Os stubs MUST NÃO incluir `Shape` (removido), `Text` (renomeado para `Label`), `BoxCollider` ou `Collider` (removidos).

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

#### Scenario: BoxCollider stub does not exist

- **WHEN** os stubs sob `engine/` são listados
- **THEN** nenhum arquivo `box_collider.pyi` ou declaração `class BoxCollider` aparece

#### Scenario: CollisionObject2D stub declares new hooks and signals

- **WHEN** o stub de `CollisionObject2D` (ou seu equivalente) é inspecionado
- **THEN** contém declarações `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`
- **AND** declara os atributos `area_entered`, `area_exited`, `body_entered`, `body_exited` como `Signal`
- **AND** NÃO contém `_on_collide`

#### Scenario: Shape2D stub is polymorphic

- **WHEN** o stub de `Shape2D` é inspecionado
- **THEN** define `Shape2D` como base e `RectangleShape2D`, `CircleShape2D` como subtipos com seus campos

### Requirement: Python scripts can connect to Kotlin-declared Signal fields

When a Kotlin `Node` subclass exposes a public `val` of type `Signal<T>` (e.g. `Timer.timeout: Signal<Unit>`), the Python wrapper for that node SHALL expose the field as an attribute readable from a Python script. Reading the attribute MUST return a Python-side proxy whose `.connect(handler)` and `.disconnect(handler)` methods route to the underlying Kotlin `Signal<T>`. The proxy's `.connect` MUST accept any Python callable; the proxy MUST adapt the callable so that emission from the Kotlin side calls into the Python callable with the emitted value (or with no arguments when `T == Unit`). The lookup of `Signal<*>` fields MUST be reflection-based — Kotlin code SHALL NOT be required to annotate signals with a `@PythonExposed` (or similar) marker for them to be visible. Errors during handler invocation (Python exceptions) MUST propagate out of `Signal.emit` so the engine fails fast at the call site, consistent with current Python script error policy.

#### Scenario: Python connects to Timer.timeout

- **GIVEN** a `Timer` Kotlin instance in a live scene with `waitTime = 0.1f`, `autostart = true`
- **AND** a Python script attached to the parent node calling `timer.timeout.connect(my_handler)` in `_ready`
- **WHEN** the timer emits `timeout`
- **THEN** `my_handler` is invoked with no arguments

#### Scenario: Python disconnect removes the handler

- **GIVEN** a Python script that connected `my_handler` to `timer.timeout`
- **WHEN** the script later calls `timer.timeout.disconnect(my_handler)`
- **THEN** subsequent emissions do NOT invoke `my_handler`

#### Scenario: Reflection discovers Signal fields without annotation

- **WHEN** the Python wrapper for a `Timer` instance is constructed
- **THEN** the attribute `timeout` is available without any `@PythonExposed` (or equivalent) annotation on the Kotlin `val timeout`
- **AND** reading `timer.timeout` returns the proxy bound to the same `Signal<Unit>` instance as `kotlinTimer.timeout`

#### Scenario: Python handler exception propagates

- **GIVEN** a Python handler connected to `timer.timeout` that raises `ValueError("boom")`
- **WHEN** the timer emits `timeout`
- **THEN** the exception propagates out of `Signal.emit` and ultimately crashes the loop with a Python traceback visible to the user

### Requirement: PyI stubs include Timer and TimerMode

The `.pyi` stubs published by `:engine-bundle-python` SHALL include a `Timer` class with the attributes `waitTime: float`, `autostart: bool`, `oneShot: bool`, `processCallback: TimerMode`, `timeLeft: float`, `isStopped: bool`, the method `start(override: Optional[float] = None) -> None`, `stop() -> None`, and the attribute `timeout: Signal`. The stubs SHALL include a `TimerMode` enum-like type with the entries `PHYSICS` and `IDLE`. Property names on the Python side MUST match the Kotlin camelCase names verbatim: the Polyglot/EmulateJython bridge does not translate identifiers, so `timer.wait_time` would fail at runtime — scripts MUST use `timer.waitTime`. A global `snake_case` ↔ `camelCase` bridge is deferred to a future change; until it ships, every stub published by `:engine-bundle-python` follows the same camelCase rule.

#### Scenario: Timer stub is published

- **WHEN** the `:engine-bundle-python` JAR is inspected at `resources/stubs/engine/`
- **THEN** a stub file declaring `class Timer` exists
- **AND** the stub declares `waitTime`, `autostart`, `oneShot`, `processCallback`, `timeLeft`, `isStopped`, `timeout`, `start`, `stop`

#### Scenario: TimerMode stub is published

- **WHEN** the same stub source is inspected
- **THEN** a `TimerMode` type with members `PHYSICS` and `IDLE` is declared

### Requirement: RigidBody2D Python scripts expose force, impulse, and velocity APIs

Quando o tipo nativo do Node anexado é `RigidBody2D`, o script Python MUST poder:

- Ler e escrever `self.linear_velocity: Vec2`.
- Ler e escrever `self.angular_velocity: float`.
- Ler e escrever `self.mass: float`, `self.inertia: float`, `self.restitution: float`, `self.friction: float`, `self.gravity_scale: float`, `self.linear_damping: float`, `self.angular_damping: float`.
- Chamar `self.apply_force(force: Vec2)`, `self.apply_impulse(impulse: Vec2)`, `self.apply_force_at(force: Vec2, world_point: Vec2)`, `self.apply_impulse_at(impulse: Vec2, world_point: Vec2)`, `self.apply_torque(torque: float)`. Aliases `apply_central_force` (= `apply_force`) e `apply_central_impulse` (= `apply_impulse`) MUST ser expostos para conformidade nominal com Godot.

Escrever `self.linear_velocity.x = X` deve falhar com `AttributeError` (consistência com o invariante de `Vec2.x` ser `val` Kotlin); o idioma correto é `self.linear_velocity = Vec2(X, self.linear_velocity.y)`. Hooks `_on_body_entered(self, body)` e `_on_area_entered(self, area)` continuam sendo disparados via `PhysicsSystem` enter/exit dispatch normalmente.

#### Scenario: A Python RigidBody2D script applies an impulse

- **GIVEN** um Node `RigidBody2D` com script Python que em `_physics_process(self, dt)` chama `self.apply_central_impulse(Vec2(10.0, 0.0))`
- **WHEN** o physics tick é executado
- **THEN** após o tick, `self.linear_velocity.x` aumenta em `10.0 / self.mass`

#### Scenario: Reading and writing linear_velocity from Python

- **GIVEN** um Node `RigidBody2D` com script Python
- **WHEN** o script executa `vel = self.linear_velocity` e em seguida `self.linear_velocity = Vec2(0.0, 0.0)`
- **THEN** `vel` reflete o valor antes da escrita
- **AND** `self.linear_velocity` após a escrita é `Vec2(0.0, 0.0)`

#### Scenario: Component assignment to linear_velocity raises AttributeError

- **GIVEN** um Node `RigidBody2D` com script Python
- **WHEN** o script executa `self.linear_velocity.x = 5.0`
- **THEN** Python levanta `AttributeError` (consistência com o protocolo de imutabilidade de `Vec2`)

### Requirement: PyI stubs include RigidBody2D and its force API

Os arquivos `.pyi` publicados em `engine-bundle-python/src/main/resources/stubs/engine/` MUST incluir uma declaração de classe `RigidBody2D` herdando de `PhysicsBody2D`, com as properties `linear_velocity`, `angular_velocity`, `mass`, `inertia`, `restitution`, `friction`, `gravity_scale`, `linear_damping`, `angular_damping` e os métodos `apply_force`, `apply_impulse`, `apply_central_force`, `apply_central_impulse`, `apply_force_at`, `apply_impulse_at`, `apply_torque`. Tipos devem refletir as assinaturas Kotlin (`Vec2` para vetores, `float` para escalares).

#### Scenario: PyI stubs declare RigidBody2D

- **WHEN** o arquivo de stub `engine/__init__.pyi` (ou equivalente) é inspecionado
- **THEN** ele contém uma classe `RigidBody2D(PhysicsBody2D)` com pelo menos os métodos `apply_force`, `apply_impulse`, `apply_central_impulse`, `apply_torque`
- **AND** as properties `linear_velocity: Vec2`, `angular_velocity: float`, `mass: float`, `restitution: float`, `friction: float` aparecem na classe


### Requirement: Python scripts can drive Control anchors, visibility, and mouse filter

Python scripts extending a `Control`-derived widget SHALL be able to read and
write the new `Control` API via the host Node accessors: the four anchors
(`anchorLeft`/`anchorTop`/`anchorRight`/
`anchorBottom`), the four offsets (`offsetLeft`/`offsetTop`/`offsetRight`/
`offsetBottom`), `size`, `visible`, `mouseFilter`, and the preset method
(`applyPreset(...)` with the `LayoutPreset` enum). The `MouseFilter` and
`LayoutPreset` enums SHALL be reachable in the same pre-bound namespace as the
other engine types (e.g. `Vec2`, `Color`). Writing these from a hook SHALL be
reflected by the next anchor layout pass — a script SHALL NOT need to recompute
position every frame to keep a widget anchored.

#### Scenario: Script anchors a Label to the screen center via preset

- **WHEN** a Python `_ready` hook calls `self.applyPreset(LayoutPreset.CENTER)` and sets symmetric offsets on a `Label`
- **THEN** the label SHALL resolve centered on the surface after the next anchor layout pass, with no `_draw`/`_process` repositioning code.

#### Scenario: Script toggles visibility instead of alpha

- **WHEN** a Python hook sets `self.visible = False` and later `self.visible = True` on a Control-derived widget
- **THEN** the widget SHALL hide and show at full color, replacing the `color.a = 0` hide pattern.

### Requirement: PyI stubs include Control, anchors, visible, mouse_filter and presets

The published `.pyi` stubs SHALL include the `Control` base surface — anchors,
offsets, `size`, `visible`, `mouseFilter`, `applyPreset`, and the `MouseFilter`
and `LayoutPreset` enums — and SHALL show `Panel`/`Button`/`Label` inheriting it,
so editor autocomplete reflects the new API.

#### Scenario: Stub exposes the anchor and visibility API

- **WHEN** the `.pyi` stubs are generated and inspected
- **THEN** they SHALL declare `Control` with the anchor/offset fields, `size`, `visible`, `mouseFilter`, and `applyPreset`, and SHALL declare `Panel`, `Button`, and `Label` as subclasses of `Control`.
