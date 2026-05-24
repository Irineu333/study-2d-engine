## Why

Hoje cada `NodeEntry` em `scene.json` tem **dois bags** separados: `properties` (que vai para os `@Inspect var` do Node Kotlin nativo) e `props` (que vai para os exports do script Python anexado). Essa separação vaza um detalhe de implementação ("essa propriedade vem do Node ou do script?") para o formato do arquivo e para o futuro editor visual. Em Godot, um nó com script tem **um único conjunto de propriedades** — exports do script aparecem lado a lado com as propriedades nativas, e o editor não distingue origem. Esse desenho é mais simples de entender, mais simples de explicar no `CLAUDE.md`, e prepara terreno para um inspetor unificado.

A engine está no momento certo: ainda não há editor, há um único bundle de exemplo (`pong/`), e a quebra é local ao formato do arquivo e ao roteamento no loader.

## What Changes

- **BREAKING**: O campo `props` desaparece de `NodeEntry`. Todas as propriedades — nativas (`@Inspect`) e de script (exports) — convivem em `properties`.
- **BREAKING**: `SceneFile.version` sobe de `1` para `2`. Carregar um arquivo com `version: 1` MUST falhar com mensagem explícita instruindo migração manual; **não há leitor legacy**.
- O `SceneLoader` (passando por `BundleLoader`) ganha um passo de **roteamento**: para cada chave em `properties`, decide se ela é `@Inspect` do Node ou export do script, e aplica no destino correto.
- **Colisão é fatal**: se uma chave existe tanto como `@Inspect` no Node quanto como export no script, o loader MUST lançar erro nomeando o nó, a chave, e os dois candidatos.
- **Chave desconhecida é fatal**: se uma chave em `properties` não corresponde a nenhum `@Inspect` do Node nem a nenhum export do script (incluindo quando não há script), o loader MUST lançar erro nomeando o nó, a chave, e os destinos consultados.
- A SPI `ScriptHost` ganha `declaredExports(script: Script): Set<String>` para que o `BundleLoader` possa rotear antes de instanciar o script. O `PythonScriptHost` já descobre exports via AST — o método apenas expõe esse conjunto.
- `SceneLoader.save(scene)` emite um único bag `properties` por nó. Para nós com script, o save consulta `ScriptInstance.currentExports()` (novo método na SPI) e mescla com os valores `@Inspect` do Node.
- O `pong/scene.json` é atualizado para `version: 2` com bags unificados.
- `CLAUDE.md` é atualizado: a seção "Wiring no `scene.json`" descreve o bag único, a regra de colisão, e o erro de chave desconhecida.

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `scene-serialization`: `NodeEntry.props` removido; `properties` vira o bag unificado; `SceneFile.version` sobe para `2`; loader rejeita `version: 1` com mensagem explícita.
- `bundle-loading`: roteamento `properties` → (`@Inspect` do Node | export do script) com colisão fatal e chave desconhecida fatal; o campo `props` é removido do formato.
- `script-host`: SPI ganha `declaredExports(script): Set<String>` (descoberta estática) e `ScriptInstance.currentExports(): Map<String, JsonElement>` (snapshot pra save).
- `python-scripting`: `PythonScriptHost` implementa `declaredExports` expondo o conjunto descoberto pelo AST scan que já existe; `ScriptInstance` Python implementa `currentExports`.

## Impact

**Código (`:engine`)**
- `serialization/SceneFile.kt`: remove `props`, bump version constant para `2`.
- `serialization/SceneLoader.kt`: rejeita `version != 2`; remove `applyProperties` direto; introduz `routeAndApplyProperties` que consulta `@Inspect` names + (se houver script) exports declarados.
- `serialization/SceneLoader.kt::save`: emite bag unificado; usa `ScriptInstance.currentExports()` quando houver script.

**Código (`:engine-bundle`)**
- `BundleLoader.kt`: `attachScript` callback recebe o `Script` antes de instanciar, devolve os exports declarados pro `SceneLoader` rotear; após roteamento, o loader aplica o subconjunto de chaves de script via `ScriptInstance.setExport`.
- `script/ScriptHost.kt` (SPI): adiciona `declaredExports(script): Set<String>` e `ScriptInstance.currentExports(): Map<String, JsonElement>`.

**Código (`:engine-bundle-python`)**
- `PythonScriptHost`: implementa `declaredExports` retornando `Script.exports.keys`.
- `ScriptInstance` Python: implementa `currentExports` consultando os atributos atuais da instância Python para cada export declarado.

**Conteúdo**
- `games/pong/src/main/resources/pong/scene.json`: bump `version: 2`, fundir `properties` + `props` em cada nó com script (8 nós afetados).

**Testes**
- `engine/src/test/.../SceneLoaderTest.kt`: atualizar fixtures (sem `props`), adicionar caso `version: 1 → erro`, adicionar caso `chave desconhecida → erro`.
- `engine-bundle/src/test/.../BundleLoaderTest.kt`: substituir fixtures `props`-based por unificadas; adicionar testes de colisão fatal e chave desconhecida fatal.
- `engine-bundle-python/src/test/...`: smoke test pra `declaredExports` e `currentExports`.

**Documentação**
- `CLAUDE.md`: atualizar a seção "Wiring no `scene.json`"; atualizar o exemplo; documentar regras de colisão e chave desconhecida; atualizar tabela do roadmap quando archived.

**Não afeta**
- `:games:tictactoe` (não usa scripts nem `scene.json`).
- `:engine-skiko`, `:engine-compose` (backends, não tocam serialização).
- A SPI `ScriptHost` em pontos não relacionados a exports (carregamento de script, hooks de lifecycle, signals).
