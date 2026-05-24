## REMOVED Requirements

### Requirement: NodeEntry supports script and props fields

**Reason**: Substituído pelo novo formato Godot-style com bag único `properties`. O campo `props` deixa de existir; exports do script convivem em `properties` ao lado das propriedades `@Inspect` do Node. A validação "`props` sem `script` é erro" deixa de fazer sentido porque o campo `props` não existe mais; o equivalente passa a ser "chave em `properties` que não casa com `@Inspect` nem com export do script é erro", coberto pelo novo requirement em `scene-serialization`.

**Migration**: Documentos com `version: 1` (contendo `props`) MUST ser migrados manualmente para `version: 2` mesclando `props` em `properties`. O loader rejeita `version: 1` com mensagem explícita; não há leitor legacy. O `pong/scene.json` é o único arquivo de produção e é migrado nesta change.

## ADDED Requirements

### Requirement: NodeEntry supports script field with unified properties routing

O formato `scene.json` SHALL aceitar um campo opcional `script: String?` em cada `NodeEntry`, ao lado dos campos existentes (`type`, `name`, `properties`, `children`). NÃO há campo `props` separado.

Quando `script` é não-nulo, o `BundleLoader` MUST:

1. Instanciar o Node nativo via `NodeRegistry.create(type)`.
2. Atachar o `ScriptInstance` via `ScriptHostRegistry.hostFor(script).attach(node, script)`.
3. Construir um `ScriptAttachment` cuja `exportNames` é o conjunto `Script.exports.map { it.name }.toSet()`, e cujo `applyExport(name, jsonEl)` chama `PropCoercion.coerce(jsonEl, export.type, export.nullable)` seguido de `instance.setExport(name, value)`.
4. Devolver esse `ScriptAttachment` para o `SceneLoader` rotear `properties`.
5. Armazenar `node.scriptInstance = instance`.

Quando `script` é nulo, o Node se comporta como antes — apenas o tipo nativo, sem `ScriptInstance`, e o `SceneLoader` roteia `properties` exclusivamente contra `@Inspect`.

O roteamento de `properties` (decisão `@Inspect` vs export, colisão fatal, chave desconhecida fatal) MUST ser delegado ao `SceneLoader` conforme requirement em `scene-serialization`. O `BundleLoader` não duplica essa lógica.

#### Scenario: Node with script slot is instantiated, attached, and routed

- **GIVEN** `scene.json` contém um nó `{ "type": "engine.Node2D", "script": "scripts/paddle.py", "properties": { "speed": 360.0 } }` onde `speed` é export do `paddle.py` e não há `@Inspect var speed` em `Node2D`
- **AND** o `PythonScriptHost` está registrado
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** um `Node2D` é instanciado
- **AND** `node.scriptInstance` é não-nulo
- **AND** `scriptInstance.setExport("speed", 360.0f)` foi chamado (após coerção via `PropCoercion`)

#### Scenario: Node without script slot has no scriptInstance

- **GIVEN** `scene.json` contém um nó `{ "type": "engine.Node2D", "properties": {} }` (sem `script`)
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** um `Node2D` é instanciado
- **AND** `node.scriptInstance` é nulo

#### Scenario: Unknown key in properties is rejected via SceneLoader

- **GIVEN** `scene.json` contém um nó `{ "type": "engine.Node2D", "properties": { "ballSize": 16.0 } }` (sem `script`)
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** uma exceção é lançada (vinda do `SceneLoader` route step)
- **AND** a mensagem indica `ballSize` como chave desconhecida no `Node2D`

#### Scenario: Prop type mismatch fails fast during routing

- **GIVEN** um script declara `speed: float = 360.0` e `scene.json` traz `"properties": { "speed": "fast" }`
- **WHEN** `BundleLoader` aplica a chave (via `applyExport` → `PropCoercion`)
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia `speed`, o tipo esperado e o valor recebido
