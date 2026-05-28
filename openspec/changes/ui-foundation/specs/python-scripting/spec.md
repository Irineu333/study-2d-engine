## MODIFIED Requirements

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
