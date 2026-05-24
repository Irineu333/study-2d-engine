## Context

A engine usa **dois bags** distintos no `scene.json` para configuração de cada nó:

```
NodeEntry {
  properties: JsonObject    ← consumido pelo Node Kotlin (via @Inspect)
  props: JsonObject?         ← consumido pelo script Python anexado (via exports)
}
```

Cada bag fala com um sistema diferente: `properties` é aplicado por reflexão filtrando `@Inspect` (`SceneLoader.applyProperties`); `props` é aplicado iterando o `JsonObject` e chamando `ScriptInstance.setExport` (`BundleLoader.load`). O bag de `props` exige `script != null` — `props` órfão é erro fatal hoje.

Em Godot, exports de script aparecem no inspetor junto com as propriedades nativas do nó — não há ideia de "bag separado". A fonte da verdade pro editor é uma única lista por nó. Esse desenho é simples de explicar (uma "propriedade do nó" e ponto), e abre caminho pra um inspetor unificado quando o editor existir.

O custo de manter a separação atual é didático e estético, não técnico — a engine funciona. Mas o `CLAUDE.md` precisa explicar dois conceitos onde um basta, e o futuro editor teria que decidir se mostra duas seções ou funde mesmo assim.

A change `prepare-for-serialization` introduziu `@Inspect`. A change `add-python-scripting` introduziu `props`. As duas decisões foram boas em seus momentos, mas ao convergirem deixaram a forma do arquivo com costura visível. Esse refator costura sob a superfície e mantém só `properties`.

## Goals / Non-Goals

**Goals:**

- Substituir os dois bags por um único `properties` em `NodeEntry`.
- Rotear cada chave pra `@Inspect` do Node nativo ou pra export do script anexado, com regras de erro explícitas e fatais.
- Manter a SPI `ScriptHost` mínima: aproveitar `Script.exports` que já é público; adicionar **um** método em `ScriptInstance` (`currentValue`) só pra `save` funcionar.
- Manter round-trip estável (`save(load(json)) ≡ json`).
- Atualizar `pong/scene.json` e os testes.

**Non-Goals:**

- **Retrocompatibilidade.** Carregar `version: 1` MUST falhar com mensagem clara. Não há leitor legacy, não há migração automática.
- Mudanças no formato dos valores dentro do bag — o que é `transform`, `Vec2`, etc. continua igual.
- Mudar a SPI de carga de scripts (`ScriptHost.load`, `attach`) ou de hooks de lifecycle.
- Inspetor / editor visual — esta change só prepara o formato; a interface do editor é trabalho futuro.
- Validação de tipos das propriedades além do que já existe (`PropCoercion` para scripts; `kotlinx.serialization` para `@Inspect`).
- `:games:tictactoe`, `:engine-skiko`, `:engine-compose`.

## Decisions

### Decision 1 — Roteamento por união de nomes, decisão antes de instanciar

Para cada nó com script, o `BundleLoader` consulta `Script.exports` (já existe, `List<ExportedProperty>`) e extrai o `Set<String>` de nomes de export. Em paralelo, o `SceneLoader` consulta `@Inspect` names via reflexão (já faz). A união determina o roteamento:

```
para cada chave k em properties:
    is_inspect = k ∈ inspectNames(Node)
    is_export  = script != null && k ∈ exportNames(script)

    (true , false) → aplica via @Inspect setter
    (false, true ) → aplica via ScriptInstance.setExport
    (true , true ) → erro fatal (colisão)
    (false, false) → erro fatal (chave desconhecida)
```

**Alternativa considerada**: precedência (Node ganha, script ganha, ou script-sobrepõe-Node). Rejeitada — Godot resolve por convenção implícita de naming (exports de script costumam ter snake_case, propriedades nativas seguem o tipo). Aqui o convite é fail-fast pra contributor renomear, em vez de errar silenciosamente.

**Alternativa considerada**: namespace explícito (`properties.node.size` vs `properties.script.size`). Rejeitada — devolve o problema do bag duplo via outra sintaxe.

### Decision 2 — Decisão antes de instanciar o script

O loader precisa conhecer `exportNames` antes de aplicar qualquer chave. `Script.exports` já é exposto pelo `Script` interface; o `BundleLoader` já carrega todos os scripts em `ScriptHostRegistry.loadAll` antes de `SceneLoader.load`. **Nenhum novo método de SPI é necessário pro roteamento.**

A ordem por nó vira:

```
1. node = NodeRegistry.create(entry.type)
2. node.name = entry.name
3. script = if entry.script != null then scripts[entry.script] else null
4. exportNames = script?.exports.map(it.name).toSet() ?: emptySet()
5. inspectNames = inspectMembers(node).map(it.name).toSet()
6. instance = script?.let { host.attach(node, it) }
7. roteia entry.properties → (Node @Inspect | instance.setExport)
8. recurse children
```

`attach` continua acontecendo antes do roteamento porque `setExport` precisa de instância. O custo é nulo — Python já instancia rápido, e a falha de export type ainda dispara em `setExport` como hoje.

### Decision 3 — Versão bumpa para `2` com rejeição explícita de `1`

O `SceneFile.version` constante sobe de `1` pra `2`. `SceneLoader.load` MUST rejeitar `version != 2` com mensagem do tipo:

```
SceneFile version 1 is not supported. This loader requires version 2
(field 'props' was merged into 'properties' in change
godot-style-properties). Migrate manually.
```

**Alternativa considerada**: leitor dual-format que aceita `version: 1` lendo `props` e mergeando. Rejeitada por decisão explícita ("sem retrocompatibilidade") — o único `scene.json` de produção é `pong/scene.json`, então o custo de migrar manualmente é < 5 minutos e o ganho é loader simples.

### Decision 4 — `ScriptInstance.currentValue(name): Any?` pro `save`

Pra `SceneLoader.save` emitir um bag unificado, ele precisa, para cada nó com script, ler o valor atual de cada export e serializá-lo. Adiciona-se à SPI `ScriptInstance`:

```kotlin
interface ScriptInstance : ScriptInstanceContract {
    fun setExport(name: String, value: Any?)
    fun currentValue(name: String): Any?   // ← novo, opcional dos exports
}
```

O `save` walka `script.exports`, lê via `currentValue`, serializa via `kotlinx.serialization` usando o `ExportedProperty.type` (já presente) pra escolher serializer. Mescla com o map de `@Inspect` props na ordem: primeiro `@Inspect`, depois exports do script.

**Alternativa considerada**: `currentExports(): Map<String, JsonElement>` (lote, já serializado). Rejeitada — empurra coerção Kotlin → JSON pro lado do host (Python via GraalPy), enquanto o `SceneLoader` já tem `Json` configurado e sabe serializar os tipos dos exports. Manter o host devolvendo `Any?` raw e o loader serializando é mais simétrico com `PropCoercion` (host devolve raw; loader/`PropCoercion` faz coerção).

**Alternativa considerada**: não suportar save round-trip pra exports nesta change, deixando `save` emitir só `@Inspect`. Rejeitada — quebraria o requisito existente de round-trip estável, e adicionar o método agora é barato (uma linha no Python, uma no Kotlin).

### Decision 5 — Erros nomeiam nó, chave, e candidatos

Mensagens fatais devem ser acionáveis. Padrão:

```
[colisão]
Property 'size' on node 'Ball' (path '/PongScene/Ball') is declared both
as @Inspect on com.neoutils.engine.physics.BoxCollider and as an export
in scripts/ball.py. Property names must be unique across Node and script.

[chave desconhecida]
Unknown property 'ballSizr' on node 'Ball' (path '/PongScene/Ball').
Candidates from @Inspect on com.neoutils.engine.physics.BoxCollider:
[transform, size]. Candidates from exports in scripts/ball.py:
[ballSize, initialSpeed, maxSpeed, speedupPerHit, fieldCenter].
```

O caminho do nó (`/PongScene/Ball`) é computável durante a descida recursiva — o loader já passa `parent` implicitamente; vamos passar um `path: String` acumulado pra mensagens.

## Risks / Trade-offs

- **[Risco]** Esquecer de bumpar `version` em algum lugar (constante, fixtures de teste, `pong/scene.json`) faz os testes passarem com `version: 1` mesmo após o refator → **Mitigação**: o próprio loader rejeita `version: 1` com erro fatal; qualquer fixture esquecida explode no primeiro teste de carga. Não há caminho silencioso.

- **[Risco]** Exports declarados pelo script mas não setados pelo `scene.json` somem do round-trip se `currentValue` devolver `null` por engano → **Mitigação**: `save` emite todos os exports declarados, com valor atual; `currentValue` MUST devolver o default mesmo se nunca foi sobrescrito. Teste: `save(load(json_sem_alguns_exports))` produz JSON com os exports default presentes.

- **[Risco]** `currentValue` em GraalPy pode bater em atributos Python que não são serializáveis (objetos complexos criados em `_ready`) → **Mitigação**: `currentValue` consulta apenas exports declarados (lista finita conhecida estaticamente via AST). Não vasculha atributos arbitrários. Tipos não-serializáveis fora dos exports declarados nunca aparecem.

- **[Risco]** Colisão entre `@Inspect` e export pode aparecer organicamente quando alguém promove um export pra `@Inspect` no Node (ou vice-versa) → **Mitigação**: erro fatal de colisão deixa claro o que renomear. Como isso é raro (ainda não tem ocorrência real no Pong), o custo é aceitável.

- **[Risco]** O contributor que clonar o repo após o merge pode rodar `git pull` e ter um `pong/scene.json` antigo em workdir/branch → **Mitigação**: nada a fazer; o erro do loader nomeia a versão e a change pelo nome, então a busca leva ao mesmo `CLAUDE.md` atualizado.

- **[Trade-off]** O loader fica mais acoplado ao `Script` (precisa de `exports`) antes do roteamento. Hoje só o `BundleLoader` toca isso; com o refator, o callback `attachScript` passado pra `SceneLoader.load` precisa também fornecer o conjunto de exports do script anexado. Isso significa uma assinatura nova:

  ```kotlin
  // antes
  attachScript: (node, scriptPath, props: JsonObject?) -> ScriptInstanceContract?

  // depois
  attachScript: (node, scriptPath) -> ScriptAttachment?
  data class ScriptAttachment(val instance: ScriptInstance, val exportNames: Set<String>, val applyExport: (name, JsonElement) -> Unit)
  ```

  É uma SPI maior. **Mitigação**: o callback fica em `:engine`, com tipos próprios de `:engine` (`ScriptInstanceContract`); o `BundleLoader` continua sendo o único implementador e mantém todo o roteamento Python coerente. A SPI cresce mas continua coesa.
