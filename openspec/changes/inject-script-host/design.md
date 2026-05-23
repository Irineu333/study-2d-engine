## Context

Hoje a engine resolve `ScriptHost` por extensão via um singleton mutável `ScriptHostRegistry`. O `Main.kt` do jogo chama `PythonScriptHost.install()` que tem dois efeitos: constrói o `Context` GraalPy (caro) e registra a instância no singleton global por extensão `.py`. O `BundleLoader.fromResources(...)` então faz lookup pela extensão dentro do `ScriptHostRegistry.loadAll(...)` para cada path coletado no tree-walk do `scene.json`.

Restrições e estado relevantes:

- **Apenas Python existe.** Nenhum segundo `ScriptHost` está implementado nem proposto a curto prazo.
- **Interop multi-linguagem não existe.** `script_of(node)` em `PythonScriptHost` é intra-host (mapa `Node → _ScriptNode` Python). Não há protocolo de comunicação script-Lua ↔ script-Python.
- **Bundles podem ser script-less.** O código atual já trata isso (`if scriptPaths.isNotEmpty()`).
- **`PythonScriptHost.install()` é o único registrador atual.** Construtor é `private`. Companion `install()` constrói `Context` + chama `ScriptHostRegistry.register(this)`.
- **Specs publicadas mencionam o singleton.** `script-host`, `bundle-loading`, `python-scripting` e `pong-sample` precisam ser modificados de forma coerente.

## Goals / Non-Goals

**Goals:**

- Eliminar estado global mutável da SPI de scripting.
- Tornar a dependência `BundleLoader → ScriptHost` explícita no call site.
- Manter `PythonScriptHost` testável (injeção de `Context` permanece possível).
- Preservar comportamento de bundles script-less (carregam sem custo de runtime de scripting).
- Manter o reuso do `Context` GraalPy entre múltiplos loads viável e idiomático.

**Non-Goals:**

- Suporte plural a `ScriptHost` na mesma chamada (`List<ScriptHost>`). Singular agora; plural é evolução não-quebrante futura quando um segundo host existir.
- Interop entre scripts de linguagens diferentes.
- Mudar a forma de despacho de hooks, descoberta de exports, ou contrato de `extends`. Nada disso muda — só muda *como o host chega no loader*.
- Manter qualquer back-compat com `install()`. Quebra explícita; consumidor único (`:games:pong`) é atualizado junto.

## Decisions

### Decision 1: assinatura singular `scripting: ScriptHost? = null`

`BundleLoader.fromResources/fromPath` ganham um parâmetro nomeado `scripting: ScriptHost? = null` ao final, depois de `types`. A escolha por singular (e não `List<ScriptHost>`) reflete:

- Não há segundo host hoje. Plural genera ergonomia ruim (`listOf(host)`) sem benefício real.
- Interop multi-linguagem não está desenhada — vender plural agora seria vender capacidade não testada.
- Migrar para `List<ScriptHost>` no futuro é overload aditivo; o overload singular pode delegar `listOf(scripting)` ao plural sem quebrar caller.

Nullable porque cenas estáticas (fixtures de teste, primeiros tutoriais, possíveis cenas de menu) não precisam carregar o runtime GraalPy só para validar JSON.

**Alternativas consideradas:**

- `scripting: ScriptHost` não-nullable, sem default. Mais explícito, mas força bundles script-less a inventar um `NullScriptHost` ou aceitar custo de boot de GraalPy. Rejeitada — fricção desnecessária para o caso comum mais simples.
- `scripting: List<ScriptHost> = emptyList()`. Marginalmente futuro-proof, marginalmente mais ruidoso no call site. Não compensa enquanto não houver dois hosts reais.
- `scripting: ScriptHostRegistry` (registry como parâmetro, não singleton). Mantém a indireção de dispatch para o caso de N hosts. Mas com singular acordado, essa indireção não tem mais usuário.

### Decision 2: deletar `ScriptHostRegistry`

Sem singleton, sem registry. A lógica de despacho que vive em `ScriptHostRegistry.loadAll` migra para um helper privado dentro de `BundleLoader`:

```kotlin
private fun loadScripts(
    scriptPaths: Set<String>,
    host: ScriptHost?,
    bundle: BundleSource,
): Map<String, Script> {
    if (scriptPaths.isEmpty()) return emptyMap()
    if (host == null) {
        val firstPath = scriptPaths.first()
        error(
            "Bundle references script '$firstPath' but no ScriptHost was provided. " +
            "Pass `scripting = PythonScriptHost.create()` (or another ScriptHost) to BundleLoader."
        )
    }
    return scriptPaths.associateWith { path ->
        if (!path.endsWith(host.extension)) {
            error(
                "Bundle references script '$path' but the provided ScriptHost handles " +
                "'${host.extension}'. Bundle and ScriptHost extensions must match."
            )
        }
        host.load(path, bundle)
    }
}
```

`UnsupportedScriptExtensionException` some junto — a nova mensagem de erro é diretamente a `error(...)` acima, sem tipo dedicado. Não há consumer reutilizando essa exceção.

**Alternativa considerada:** manter `ScriptHostRegistry` como helper privado de `BundleLoader`. Rejeitada — manter um tipo público inteiro só para abstrair uma indireção que ninguém mais usa é mau cheiro.

### Decision 3: `PythonScriptHost.create()` factory + construtor `internal`

```kotlin
class PythonScriptHost internal constructor(
    private val context: Context,
) : ScriptHost {

    init { /* ... bindings + eval runtime ... */ }

    override val extension = ".py"

    // ... resto do código atual ...

    companion object {
        fun create(): PythonScriptHost = PythonScriptHost(defaultContext())

        private fun defaultContext(): Context = Context.newBuilder("python")
            .allowAllAccess(true)
            .allowExperimentalOptions(true)
            .option("python.PosixModuleBackend", "java")
            .option("python.EmulateJython", "true")
            .option("engine.WarnInterpreterOnly", "false")
            .build()
    }
}
```

Razões:

- `create()` é mais expressivo que `PythonScriptHost()` direto — sinaliza "isso constrói algo pesado, não é só um data holder".
- Construtor `internal` permite que testes do mesmo módulo passem `Context` customizado (ex.: limites de timeout, módulos pré-carregados) sem expor `Context` na API pública.
- `install()` some inteiro. Não há mais side effect global; toda inicialização do `Context` acontece dentro do construtor da instância, **antes** do primeiro `load`. Isso preserva o requirement existente de eager init (cumprido pelo mesmo `init {}` bloco que já existe).

**Alternativa considerada:** construtor `public` sem args (`PythonScriptHost()`). Funciona, mas perde o sinal de "factory pesada"; também limita a injeção de `Context` em testes (precisaria de overload). `create()` é uma decisão de naming, não estrutural.

### Decision 4: caller é dono do ciclo de vida do host

O `BundleLoader` **não** mantém referência ao `ScriptHost` entre chamadas. Cada `fromResources/fromPath` recebe o host como argumento; cabe ao caller decidir se constrói um por load ou um por processo.

Pong (load único) é indiferente. Editor (loads repetidos) **precisa** reutilizar — por isso vai ficar documentado claramente no KDoc:

```kotlin
/**
 * ...
 * @param scripting A `ScriptHost` instance used to load scripts referenced by the bundle.
 *   Pass `null` for script-less bundles. **Reuse the same instance across multiple loads**
 *   when possible — constructing a `PythonScriptHost` (or any other host) typically incurs
 *   significant cost (e.g. GraalPy `Context` boot), and the host is safe to share.
 */
```

Não há lock interno no host. Concorrência (loads paralelos a partir do mesmo host) está fora do escopo desta change — o uso atual é serial.

### Decision 5: erro fast quando script existe e host está ausente

Bundle com `script: ...` mas `scripting = null` deve falhar imediatamente, no `BundleLoader.load`, antes de instanciar qualquer Node. Mensagem inclui o primeiro path encontrado e a recomendação direta de passar `PythonScriptHost.create()`.

Bundle script-less + `scripting = ScriptHost` não-nulo é silenciosamente OK. Não vamos exigir `null` nesse caso — força o caller a saber de antemão se a cena tem scripts, o que vai contra o ideal de "passe o host uma vez e esqueça".

## Risks / Trade-offs

- **[Risco] Caller esquece de reusar o host e paga o boot de GraalPy a cada load** → KDoc + nota no `CLAUDE.md`. Não há remédio em código sem reintroduzir cache global. Aceitável: o caller do editor verá o sintoma na primeira otimização.
- **[Risco] Mensagem de erro de "script existe mas host ausente" precisa ser ótima — é o failure mode mais comum durante migração** → Mensagem inclui (a) o path do script encontrado, (b) instrução literal de qual host usar. Caso teste cobre o cenário.
- **[Trade-off] Singular trava interop multi-linguagem por enquanto** → Aceitável; quando o segundo host nascer (e a interop for desenhada), adicionar um overload `List<ScriptHost>` é não-quebrante. A SPI atual deixa o caminho aberto via `host.extension`.
- **[Risco] Cenários com `script` sem `props` quando `scripting = null`** → Cai na mesma checagem do (Decision 5). Mensagem genérica é suficiente.
- **[Trade-off] Construtor `internal` em `PythonScriptHost` limita injeção a partir de testes em outros módulos** → Os únicos testes que injetam `Context` hoje estão dentro de `:engine-bundle-python`. Se um dia houver necessidade externa, expomos um overload em `create(context: Context)`.

## Migration Plan

Esta change é breaking mas tem **um único call site externo** (`:games:pong/Main.kt`) e três módulos tocados. Migração em um único PR, sem flags:

1. Adicionar `scripting` em `BundleLoader.fromResources/fromPath` (com default `null`) e mover lógica de despacho para o helper privado.
2. Deletar `ScriptHostRegistry` + `UnsupportedScriptExtensionException` e seus testes.
3. Refatorar `PythonScriptHost`: construtor `internal`, factory `create()`, remover `install()` (e o método `defaultContext()` privado fica no companion).
4. Atualizar `PythonScriptHostTest`, `PythonRenderingIntegrationTest`, `GraalPySmokeTest` para usar `PythonScriptHost.create()` (ou o construtor `internal` quando precisarem de Context customizado).
5. Atualizar `BundleLoaderTest` (cenários novos: bundle script-less com `scripting = null`; erro quando script existe mas `scripting = null`; erro quando extensão não bate).
6. Atualizar `:games:pong/Main.kt`.
7. Atualizar `CLAUDE.md` (seção "Instalando o ScriptHost" + tabela de roadmap).
8. Rodar smoke completo (`./gradlew check` + executar Pong, Velha, Demos manualmente).

Rollback: revert do PR. Não há estado persistido que precise de cleanup.

## Open Questions

(nenhuma — decisões 1–3 confirmadas com o usuário; 4–5 são detalhes derivados dessas)
