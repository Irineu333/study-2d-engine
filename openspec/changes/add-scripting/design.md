## Context

A `nengine` adota scene graph estilo Godot, com lógica de gameplay sempre via subclasses de `Node`. O carregamento de cenas hoje funciona em duas pernas: o `SceneLoader` decodifica o JSON em `NodeEntry`s, o `NodeRegistry` mapeia o nome qualificado da classe Kotlin para uma factory `() -> Node` registrada em código, e o loader aplica `@Inspect` properties via reflexão (`SceneLoader.kt:56-69`). O caminho é puramente reflexivo no momento da extração de propriedades — a classe do `Node` em si **não** precisa ser `@Serializable`; basta que os tipos das propriedades anotadas com `@Inspect` o sejam. Esse detalhe é central pra esta change: significa que um script `.kts` cuja classe não tem `@Serializable` (porque o kotlin-scripting-jvm-host roda sem o compiler plugin `kotlinx-serialization`) **ainda funciona** com o loader atual sem nenhuma alteração contratual de `@Inspect`/`properties`.

O usuário ainda não tem editor visual, mas o roadmap o coloca como próximo grande marco. Sem scripting, o editor poderia posicionar e parentear instâncias de tipos existentes, mas não criar comportamento novo — e isso é o que motiva esta change agora, antes do editor, pra que ele já nasça com a peça crítica disponível.

Stakeholder único é o usuário (irineu) operando como autor e revisor manual.

## Goals / Non-Goals

**Goals:**

- Permitir que um arquivo `.nengine.kts` defina uma subclasse de `Node` que é instanciável pelo `SceneLoader` exatamente como uma classe Kotlin compilada.
- Manter o invariante "comportamento via herança" — scripts são subclasses dinâmicas, não componentes.
- Manter `:engine` Kotlin puro, sem dependência do kotlin-compiler-embeddable.
- Manter `:engine-skiko`, `:engine-compose` e jogos que não usam scripts (`:games:tictactoe`, `:games:demos`) totalmente livres do custo de runtime de scripting.
- Migrar Pong inteiro pra scripts como prova de fogo, em etapas que o usuário valida manualmente jogando o jogo entre cada etapa.
- Mensagens de erro de compilação visíveis e fail-fast — bug em script crasha o processo com a saída do compilador.

**Non-Goals:**

- Hot reload de qualquer nível (entre sessões basta — editar `.kts` exige reiniciar o jogo).
- Sandboxing de scripts. Um script é código de jogo; tem o mesmo poder que código Kotlin compilado.
- Suporte a outras linguagens (Lua, JS, DSL próprio). Apenas Kotlin scripting.
- Resolução automática de dependências entre scripts. Topologia é declarada explicitamente.
- Visual scripting, blueprints, gráficos de eventos.
- Migrar `:games:tictactoe` ou `:games:demos`. Eles continuam puro Kotlin e servem de sentinela de que scripting é opt-in.
- Otimizar tamanho do fat-jar do Pong. O custo do `kotlin-compiler-embeddable` (~40MB) é aceito como o preço de scripting.

## Decisions

### Decisão 1: Script = subclasse dinâmica de `Node`, não componente

Um arquivo `.nengine.kts` define **exatamente uma** classe top-level que estende `Node` (ou qualquer subclasse). Essa classe é o tipo do script. Zero, duas ou mais é erro fatal de compilação no `ScriptHost`.

**Alternativa considerada:** scripts como "behaviors" anexáveis (lista de scripts por node). Rejeitada — quebra o invariante 1 (herança, sem `List<Component>`) e introduz a complexidade de ordem de execução de múltiplos behaviors.

**Alternativa considerada:** scripts como top-level statements que mutam um `this: Node` implícito. Rejeitada — não permite `@Inspect var` (precisa de classe pra ter propriedades anotáveis) e perde o paralelo com herança.

### Decisão 2: SPI `ScriptHost` em `:engine`, implementação em `:engine-scripting`

`:engine` declara:

```kotlin
interface ScriptHost {
    fun compile(path: String): KClass<out Node>
    fun factoryFor(path: String): () -> Node
}

object ScriptHosts {
    fun register(host: ScriptHost)
    fun current(): ScriptHost?
}
```

`:engine-scripting` (novo módulo) implementa `KotlinScriptingHost : ScriptHost` usando `kotlin-scripting-jvm-host`. Game registra o host no `Main.kt` antes de chamar `SceneLoader.load`.

**Alternativa considerada:** botar a impl direto em `:engine`. Rejeitada — viola o invariante "engine core mínimo" (puxa ~40MB de compilador) e força jogos que não usam scripting a pagar o custo.

**Alternativa considerada:** SPI via `java.util.ServiceLoader`. Rejeitada — adiciona reflexão de classpath sem ganho real; registro explícito no `Main.kt` é mais didático e está alinhado com como `NodeRegistry` funciona hoje.

### Decisão 3: Roteamento no `SceneLoader` por sufixo do `type`

`SceneLoader.entryToNode` passa a verificar o `type`:

- Se termina em `.kts` → consulta `ScriptHosts.current()?.factoryFor(type)`.
- Caso contrário → caminho atual `NodeRegistry.create(type)`.

Os dois caminhos coexistem indefinidamente; nada na change remove `NodeRegistry`.

**Alternativa considerada:** uma chave separada `"script"` no `NodeEntry` ao lado de `"type"`. Rejeitada — adiciona estado de "duas chaves em conflito" (qual ganha?) e complica o reader. Uma string só, com heurística óbvia (`endsWith(".kts")`), é mais simples.

**Alternativa considerada:** prefixo de URI como `script://`. Funcionalmente equivalente, mas o sufixo `.kts` já é convenção universal Kotlin e dispensa documentação extra.

### Decisão 4: ScriptDefinition customizado `.nengine.kts`

A change registra um `ScriptDefinition` próprio (extensão `nengine.kts`) que:

- Pré-importa `com.neoutils.engine.scene.*`, `com.neoutils.engine.math.*`, `com.neoutils.engine.render.*`, `com.neoutils.engine.input.*`, `com.neoutils.engine.serialization.*`.
- Adiciona ao classpath o JAR do `:engine`, os JARs dos backends que o game usa, e o output compilado do próprio módulo do game (pra que scripts vejam classes Kotlin remanescentes do jogo, ex.: `PaddleCollider` antes de também virar script).
- Configura o resultado da compilação esperando uma única classe top-level subclasse de `Node`.

**Alternativa considerada:** usar `.kts` puro sem ScriptDefinition customizado. Funcionaria mas exigiria `import` completos em cada script, poluindo o arquivo. O ScriptDefinition custa pouco e melhora muito a leitura.

### Decisão 5: Dependências entre scripts via manifest explícito

Quando um script A referencia uma classe definida em script B, a ordem de compilação importa. Esta change adota a abordagem mais simples possível:

- O game registra os scripts numa lista ordenada: `ScriptHosts.register(KotlinScriptingHost(manifest = listOf("scripts/paddle-collider.nengine.kts", "scripts/paddle.nengine.kts", ...)))`.
- A ordem é "do mais profundo (dependência) ao mais externo (dependente)".
- Cada script é compilado nessa ordem; o output de cada um vira parte do classpath do próximo.
- Ciclos resultam em erro de compilação natural (a classe ainda não existe) — sem detecção custom.

**Alternativa considerada:** resolução automática via descoberta de imports. Mais inteligente mas exige parsing do AST do script antes de compilar, ou tentativas de compilação iterativas. Custo desproporcional para um Pong com ~7 scripts.

**Alternativa considerada:** topologia inferida pelo `pong.scene.json` (ordem de aparição no JSON dita ordem de compilação). Rejeitada — acopla ordem de execução à ordem de instanciação na cena, que são conceitos diferentes.

### Decisão 6: Cache de compilação em `build/scripting-cache/`

`KotlinScriptingHost` mantém um cache em disco no módulo do game:

- Chave: SHA-256 do conteúdo do script + versão do `:engine-scripting`.
- Valor: classes compiladas (`.class`) e metadata (FQN da classe `Node` encontrada).
- Invalidado por `./gradlew clean`.

**Alternativa considerada:** sem cache. Rejeitada — primeira compilação por script leva 1-3s; rodar Pong com 7 scripts levaria 7-20s no startup. Inaceitável durante desenvolvimento.

**Alternativa considerada:** cache em memória apenas (sem persistência). Útil dentro de uma execução, mas não ajuda no ciclo "edita Kotlin não-script → rebuild → roda Pong". Cache em disco é estritamente melhor.

### Decisão 7: Fail-fast em qualquer erro de script

Qualquer falha do `ScriptHost` (script não encontrado, erro de compilação, classe top-level não estende `Node`, mais de uma classe top-level) lança exceção que o `SceneLoader` deixa propagar até o `Main.kt`, encerrando o processo com o stack trace no stderr. Sem stub visual, sem placeholder. Combina com a postura "engine não esconde bug de gameplay" e simplifica o trabalho desta change.

**Alternativa considerada:** node placeholder vermelho que loga e renderiza um `Rect` de erro. Rejeitada por enquanto — útil em editor live mas escopo extra agora.

### Decisão 8: Migração de Pong em etapas com gates manuais

Em vez de migrar todas as classes de Pong de uma vez, a change avança em fatias E0-E7 (ver `tasks.md`). Cada fatia termina num momento de "rode `./gradlew :games:pong:run` e jogue 30 segundos pra ver se nada mudou". O usuário valida cada gate manualmente antes do próximo.

Essa cadência preserva a possibilidade de identificar regressões cedo e de pausar a change caso uma etapa exponha um problema de design que não foi previsto.

**Alternativa considerada:** big-bang (migra tudo numa só etapa). Rejeitada — risco de regressão difícil de bisseccionar e nenhum check-in intermediário com o usuário.

### Decisão 9: Pong é a única migração; tictactoe e demos ficam Kotlin

`:games:tictactoe` e `:games:demos` continuam puro Kotlin nesta change. Isso valida que scripting é estritamente opt-in (o módulo `:engine-scripting` não aparece como dep transitiva deles).

**Alternativa considerada:** migrar todos os jogos. Rejeitada — Pong é suficiente como prova de fogo, e manter dois jogos não-scripted é um teste vivo do contrato "opt-in".

## Risks / Trade-offs

- **[`kotlin-compiler-embeddable` é grande (~40MB) e aumenta startup time do Pong]** → mitigação: dep opt-in, só Pong paga; cache em disco amortiza compilação após primeiro run; manter como evolução documentada uma opção futura de "ship-mode" que pré-compila scripts em `.class` no build do Gradle.

- **[ScriptDefinition customizado é frágil — versões de kotlin-scripting mudam contrato]** → mitigação: pin de versão estrita; teste de smoke no CI que compila um script trivial e verifica que a classe sai com a FQN esperada.

- **[Cache em disco pode ficar inconsistente se o usuário trocar de branch sem clean]** → mitigação: chave inclui hash do source do script, então mudança de conteúdo invalida; mas mudança em uma classe Kotlin **referenciada pelo script** não invalida — documentar e aceitar como limitação (workaround: `./gradlew clean` no startup do app).

- **[Manifest manual de ordem de compilação é tedioso e propenso a erro]** → mitigação: Pong tem ~7 scripts; manifest fica pequeno e legível. Documentar como evolução resolução automática quando a inconveniência exceder o ganho.

- **[Scripts dependem do classpath do `:games:pong` que mudou (ex.: `PaddleCollider` migrou)]** → durante migração, etapa por etapa, sempre existe uma forma consistente do classpath. Gate manual entre etapas pega regressões aqui.

- **[Hosts JVM headless ou outras plataformas (Android, iOS) no futuro]** → fora de escopo agora; toda a engine é JVM Desktop. Caso a engine evolua para outras plataformas, `kotlin-scripting-jvm-host` é JVM-only — scripting precisaria de outro caminho. Documentar na seção "evolution" da spec.

- **[`:engine-scripting` introduz reflexão em runtime sobre o resultado da compilação]** → unavoidable na natureza de scripting; manter contido no módulo `:engine-scripting`, sem vazar para `:engine` ou para jogos.

## Migration Plan

Execução em fatias com gate manual em cada uma. Detalhamento completo em `tasks.md`. Sumário:

- **E0**: criar `:engine-scripting` e um script `Hello : Node()` de fumaça. Pong ainda 100% Kotlin. Gate: Pong roda igual.
- **E1**: `SceneLoader` aprende a rotear `type` terminado em `.kts` ao `ScriptHost`. Pong ainda Kotlin, mas `pong.scene.json` poderia misturar. Gate: Pong roda igual; testes do `SceneLoader` cobrem ambos os caminhos.
- **E2**: primeira migração de folha (`CenterLine` ou `Score`). Gate: visual idêntico, score continua atualizando.
- **E3**: migra `Walls`, `Ball`. Gate: jogabilidade idêntica.
- **E4**: migra `Paddle` (referencia `PaddleCollider` ainda em Kotlin). Gate: W/S, AI e colisão funcionam.
- **E5**: migra `PaddleCollider`. Primeiro caso de script-referencia-script — manifest entra em ação. Gate: jogabilidade idêntica.
- **E6**: migra `PongScene`. Pong inteiro vira `Main.kt` + scripts. Gate: Pong inteiro via scripts.
- **E7**: atualizar `CLAUDE.md`, sincronizar `openspec/specs/scripting`, `scene-serialization`, `pong-sample` no arquivamento.

Rollback é trivial em qualquer ponto: o caminho `NodeRegistry` nunca é removido, então reverter um script para classe Kotlin equivalente é "reimplementa a classe, registra no NodeRegistry, troca o `type` no JSON, deleta o `.kts`".

## Open Questions

Nenhuma bloqueia a abertura da change. Resolvíveis durante apply:

- Caminho exato do cache (`build/scripting-cache/` é tentativo; pode virar `.gradle/scripting-cache/`).
- Extensão final do arquivo: `.nengine.kts` ou `.engine.kts`? Decisão menor; vou começar com `.nengine.kts` pra deixar inequívoco.
- O `ScriptHost` deve receber a base directory (`src/main/resources/`) como construção, ou interpretar paths sempre relativos ao classpath resource loading? Tendência: relativo ao classpath, simétrico com como `pong.scene.json` é hoje carregado.
- Manifest mora onde: hardcoded no `Main.kt` ou num arquivo `scripts.manifest` ao lado dos scripts? Tendência: hardcoded por enquanto; arquivo externo é dispensável pra 7 scripts.
