## MODIFIED Requirements

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

## ADDED Requirements

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
