## 1. SPI: ScriptAttachment + ScriptInstance.currentValue

- [x] 1.1 Em `:engine`, criar `ScriptAttachment` em `com.neoutils.engine.serialization` (data class com `instance: ScriptInstanceContract`, `exportNames: Set<String>`, `applyExport: (String, JsonElement) -> Unit`).
- [x] 1.2 Em `:engine-bundle`, adicionar `fun currentValue(name: String): Any?` à interface `ScriptInstance` (`com.neoutils.engine.bundle.script.ScriptInstance`).
- [x] 1.3 Documentar em KDoc nos dois tipos: `ScriptAttachment` é o bridge loader↔host; `currentValue` existe apenas pra `SceneLoader.save` round-trip.

## 2. SceneFile + SceneLoader: bag único, version 2, roteamento fatal

- [x] 2.1 Em `SceneFile.kt`: bumpar a constante `version: Int = 1` para `2`; remover `val props: JsonObject? = null` de `NodeEntry`.
- [x] 2.2 Em `SceneLoader.load`: rejeitar `file.version != 2` com mensagem nomeando versão encontrada, esperada (`2`) e a change `godot-style-properties`.
- [x] 2.3 Em `SceneLoader.load`: trocar a assinatura do callback `attachScript` para `(node, scriptPath) -> ScriptAttachment?`; remover o parâmetro `props: JsonObject?`.
- [x] 2.4 Em `SceneLoader.entryToNode`: se `entry.script != null` e `attachScript == null`, lançar exceção indicando que nenhum host foi fornecido.
- [x] 2.5 Substituir `applyProperties` por `routeAndApplyProperties(node, attachment, properties, path)`: iterar chaves, decidir destino (inspect vs export vs colisão vs desconhecida), lançar exceções fatais com nó/path/candidatos conforme requirement.
- [x] 2.6 Em `SceneLoader.save` (`nodeToEntry`): emitir bag único mesclando `@Inspect` (já feito) com exports do script anexado (via `node.scriptInstance.currentValue(name)` para cada `ExportedProperty` declarado).
- [x] 2.7 Acumular o `path` hierárquico (`/SceneRoot/.../NodeName`) durante a descida recursiva e passá-lo para as mensagens de erro.

## 3. BundleLoader: provê ScriptAttachment, sem aplicar props direto

- [x] 3.1 Em `BundleLoader.load`, trocar a lambda passada para `SceneLoader.load`: parar de iterar `props`; em vez disso, construir e devolver `ScriptAttachment(instance, exportNames = script.exports.map { it.name }.toSet(), applyExport = { name, jsonEl -> ... })`.
- [x] 3.2 No `applyExport`, manter a coerção via `PropCoercion` (já existe) seguida de `instance.setExport(name, value)`.
- [x] 3.3 Verificar que o ramo "script sem PropCoercion" e o erro "Script não loaded" continuam disparando exceção igual ao comportamento atual.

## 4. PythonScriptHost: implementar currentValue

- [x] 4.1 Em `:engine-bundle-python`, na classe `PythonScriptInstance` (ou equivalente), implementar `currentValue(name)`: lookup em `Script.exports`, leitura do atributo Python correspondente, conversão pro tipo Kotlin declarado (inverso de `PropCoercion`).
- [x] 4.2 Tratar o caso "atributo Python ainda não materializado": devolver `ExportedProperty.default`.
- [x] 4.3 Para `name` não declarado em `exports`: lançar `IllegalArgumentException` nomeando `name` e `script.path`.

## 5. Conteúdo: migrar pong/scene.json

- [x] 5.1 Em `games/pong/src/main/resources/pong/scene.json`: bumpar `"version": 1` para `"version": 2`.
- [x] 5.2 Para cada um dos 8 nós com `props`, fundir todas as chaves de `props` em `properties` (mesmo objeto JSON) e remover o campo `props`.
- [x] 5.3 Rodar `./gradlew :games:pong:run` manualmente e validar: paddles se movem com W/S, IA joga, bola colide, gols incrementam placar. Anotar resultado em `openspec/changes/godot-style-properties/proposal.md` ou comentário do PR.

## 6. Testes: SceneLoader

- [x] 6.1 Em `engine/src/test/.../SceneLoaderTest.kt`: atualizar todos os fixtures JSON pra `"version": 2` e bag único `properties` (remover `props` onde aparecer).
- [x] 6.2 Adicionar teste: `load` de `"version": 1` lança com mensagem contendo `version 1`, `version 2` e `godot-style-properties`.
- [x] 6.3 Adicionar teste: chave desconhecida em `properties` (sem script) → exceção nomeia a chave e o nó.
- [x] 6.4 Adicionar teste: `attachScript == null` mas `entry.script != null` → exceção indicando ausência de host.
- [x] 6.5 Adicionar teste round-trip estável (`save → load → save` produz JSONs equivalentes), incluindo um nó com script anexado fictício que devolve `currentValue` consistente.

## 7. Testes: BundleLoader

- [x] 7.1 Em `engine-bundle/src/test/.../BundleLoaderTest.kt`: atualizar todos os fixtures pra bag unificado (sem `props`, `version: 2`).
- [x] 7.2 Adicionar teste: chave em `properties` que casa com `@Inspect` E com export do script → exceção fatal nomeando ambos os candidatos.
- [x] 7.3 Adicionar teste: chave em `properties` que não casa com nada (com script anexado) → exceção fatal listando os candidatos de Node e script.
- [x] 7.4 Adicionar teste: `properties` válidas (chaves nativas + chaves de script) → ambos os destinos são chamados nos valores corretos.

## 8. Testes: Python ScriptInstance.currentValue

- [x] 8.1 Em `engine-bundle-python/src/test/...`: smoke test que carrega um script Python com `speed: float = 360.0`, chama `attach`, chama `setExport("speed", 480f)`, e verifica `currentValue("speed") == 480f` (tipo Kotlin `Float`).
- [x] 8.2 Teste do caso default: anexa e imediatamente chama `currentValue("speed")` (sem `setExport`) → devolve `360f`.
- [x] 8.3 Teste de erro: `currentValue("mystery")` em script sem esse export → `IllegalArgumentException` nomeando `mystery` e o path do script.

## 9. Documentação e cleanup

- [x] 9.1 Atualizar `CLAUDE.md` — seção "Wiring no `scene.json`": substituir exemplo com `properties` + `props` por exemplo com bag único `properties`; documentar a regra de colisão fatal e a regra de chave desconhecida fatal; mencionar `version: 2`.
- [x] 9.2 Atualizar `CLAUDE.md` — tabela do roadmap: adicionar linha `godot-style-properties | Active` (vira `Archived` no `/opsx:archive`).
- [x] 9.3 Rodar `./gradlew test` no projeto inteiro e confirmar verde.
- [x] 9.4 Rodar `./gradlew :games:pong:run` (smoke manual) e `./gradlew :games:tictactoe:run` (sanity check do segundo backend não regrediu, embora não use `scene.json`).
- [x] 9.5 `openspec validate godot-style-properties` MUST passar; resolver qualquer drift entre specs e implementação antes de `/opsx:verify`.
