## MODIFIED Requirements

### Requirement: Hooks delegate from Node to ScriptInstance

`PythonScriptHost.attach` MUST retornar um `ScriptInstance` cujos métodos invocam os métodos Python correspondentes no objeto-instância. Após esta change, o conjunto de hooks despachados é:

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

O hook `onCollide(other)` / `_on_collide(self, other)` SHALL NOT existir mais e MUST NÃO ser despachado. Scripts que ainda definam `_on_collide` MUST resultar em no-op silencioso (o dispatcher não chama esse nome).

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

### Requirement: Engine types are pre-bound in the Polyglot Context

`PythonScriptHost` MUST registrar bindings no Polyglot Context para que scripts referenciem tipos da engine sem `import`. Após esta change, os bindings de física MUST ser:

- **Removidos:** `BoxCollider`, `Collider`.
- **Adicionados:** `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`, `Shape2D`, `RectangleShape2D`, `CircleShape2D`.

Os bindings das mudanças anteriores (`Vec2`, `Rect`, `Color`, `Transform`, `Key`, `MouseButton`, `NodeRef`, `Signal`, `Node2D`, `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, factory `signal(...)`) MUST permanecer disponíveis.

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

### Requirement: Extends declarations support new collision types

A primeira linha não-vazia de um script Python na forma `# extends <NodeType>` MUST resolver `<NodeType>` contra o `NodeRegistry`. Após esta change, os tipos válidos de física são:

- **Removido:** `BoxCollider`.
- **Adicionados:** `Area2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D` (raro mas permitido).

Tentar `# extends BoxCollider` MUST falhar com erro fatal idêntico ao de qualquer tipo desconhecido (mensagem nomeando o script e a linha).

#### Scenario: extends BoxCollider is rejected

- **GIVEN** um script Python com `# extends BoxCollider` na primeira linha
- **WHEN** `BundleLoader` tenta carregar o script
- **THEN** uma exceção fatal é lançada
- **AND** a mensagem nomeia o script e indica que `BoxCollider` não é um tipo conhecido

#### Scenario: extends CharacterBody2D works

- **GIVEN** um script `ship.py` com `# extends CharacterBody2D` na primeira linha
- **WHEN** o script é carregado e instanciado em um nó da cena
- **THEN** o `NodeRegistry` resolve para a classe Kotlin `CharacterBody2D` sem erro

### Requirement: PyI stubs reflect renamed collision types and hooks

Os stubs `.pyi` publicados em `engine-bundle-python/src/main/resources/stubs/engine/` MUST refletir esta change:

- **Stubs removidos:** `box_collider.pyi`, `collider.pyi` (qualquer um que exista hoje).
- **Stubs adicionados:** `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`, `Shape2D`, `RectangleShape2D`, `CircleShape2D` (cada um com seus campos `@Inspect` e métodos relevantes).
- **Hook stubs:** `_on_collide` removido; `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited` adicionados ao stub de `CollisionObject2D`.
- **Signal stubs:** os atributos built-in `area_entered`, `area_exited`, `body_entered`, `body_exited` (todos `Signal`) declarados em `CollisionObject2D`.

#### Scenario: BoxCollider stub does not exist

- **WHEN** os stubs sob `engine/` são listados
- **THEN** nenhum arquivo `box_collider.pyi` ou declaração `class BoxCollider` aparece

#### Scenario: CollisionObject2D stub declares new hooks

- **WHEN** o stub de `CollisionObject2D` (ou seu equivalente) é inspecionado
- **THEN** contém declarações `_on_area_entered`, `_on_area_exited`, `_on_body_entered`, `_on_body_exited`
- **AND** NÃO contém `_on_collide`

#### Scenario: Shape2D stub is polymorphic

- **WHEN** o stub de `Shape2D` é inspecionado
- **THEN** define `Shape2D` como base e `RectangleShape2D`, `CircleShape2D` como subtipos com seus campos
