## Context

A engine hoje força dois rituais cerimoniais que conflitam com a estética Godot-like declarada nos invariantes:

1. **Folhas finais**: `Camera2D`, `Polygon2D`, `Circle2D`, `ColorRect`, `Line2D`, `Timer` são `class` (não `open class`) por puro default-do-Kotlin. O invariante #1 diz "comportamento de gameplay é adicionado por subclasses de `Node`/`Node2D`", mas na prática você não pode `class Bullet : Circle2D()` — é obrigado a embrulhar como filho de um `Node2D`. A política implícita está inconsistente: `Label` e `BoxCollider` já são `open`, as outras folhas não.

2. **Transform imutável força reconstrução total**: para mudar só `position.y`, o código atual (`paddle.py`, demos, qualquer call site) precisa fazer:

   ```python
   self.transform = Transform(
       Vec2(pos.x, new_y),
       self.transform.scale,
       self.transform.rotation,
   )
   ```

   Cinco linhas para uma operação trivial. Em Godot/Unity: `self.position.y = new_y`. A imutabilidade de `Transform` e `Vec2` é genuinamente valiosa para passagem por valor (`compose()`, `worldTransform()`, math libs), mas a aplicação dela ao **estado** do nó cria fricção em escrita.

Os dois problemas têm a mesma natureza: a engine penaliza modificações pequenas e óbvias.

## Goals / Non-Goals

**Goals:**
- Tornar `Node2D` ergonômico em escrita sem sacrificar imutabilidade das primitivas de math.
- Padronizar política de extensibilidade: folhas `Node2D` `open` por default, alinhada com o invariante #1.
- Manter performance: zero overhead frente ao caminho atual (`transform = transform.copy(...)`).
- Manter o cache de `worldTransform()` correto sob todas as novas vias de escrita.
- Tornar scripts Python mais legíveis — `self.position = Vec2(...)` em vez do construtor de 3 args.

**Non-Goals:**
- **NÃO** introduzir `MutableTransform` ou `MutableVec2`. Mantemos imutabilidade dos valores.
- **NÃO** adicionar proxy mágico no binding Python para fazer `self.position.y = 5` "funcionar" — esse caminho continua falhando com `AttributeError` (fail-fast).
- **NÃO** adicionar helpers além de `position`/`rotation`/`scale` (ex: `translate(delta)`, `rotateBy(rad)`, `lookAt(target)`) nesta change. Cada um carrega decisão semântica (local vs global, ordem de composição) que merece ser pensada em isolado. Fica para change futura se a dor surgir.
- **NÃO** mexer em outros caminhos de mutação (ex: `Renderer.pushTransform`).
- **NÃO** mexer em `BoxCollider`/`Collider`/`Label` (já `open` ou `abstract`).

## Decisions

### Decision 1: Folhas viram `open` por default; política passa a ser explícita

**Mudança**: `Camera2D`, `Polygon2D`, `Circle2D`, `ColorRect`, `Line2D`, `Timer` declaradas `open class` em vez de `class`.

**Política nova**: "Toda folha de `Node2D` (e `Node`) shipped por `:engine` é `open` por default. Tornar uma folha `final` exige justificativa documentada no header da classe explicando o motivo (ex: contrato fechado com runtime, invariante quebraria sob herança)."

**Alternativas consideradas:**
- (i) Manter algumas final por princípio "use composição". Rejeitado: o invariante #1 já tomou essa decisão na direção oposta; ser inconsistente é o pior dos mundos.
- (ii) Tornar todas `abstract`. Rejeitado: `Camera2D` etc. são utilizáveis as-is em 99% dos casos; forçar subclasse é trabalho gratuito.
- (iii) Usar `sealed class` por hierarquia. Rejeitado: contraria a meta de permitir subclasses arbitrárias em jogos third-party.

**Implicações de serialização**: `@Serializable open class` funciona normalmente. Subclasses de jogo precisam se registrar em `NodeRegistry` se quiserem aparecer em `scene.json` — comportamento já existente. Jogos code-only puros (sem bundle) não são afetados pela registry.

### Decision 2: Properties ergonômicas em `Node2D`, `Transform` segue imutável

**Mudança**: adicionar três properties à `Node2D`, cada uma com getter delegando a `transform.<campo>` e setter delegando a `transform = transform.copy(<campo> = value)`:

```kotlin
var position: Vec2
    get() = transform.position
    set(value) { transform = transform.copy(position = value) }

var rotation: Float
    get() = transform.rotation
    set(value) { transform = transform.copy(rotation = value) }

var scale: Vec2
    get() = transform.scale
    set(value) { transform = transform.copy(scale = value) }
```

**Garantia de correção do cache**: como cada setter passa por `transform = ...`, o setter custom de `Node2D.transform` (que chama `invalidateWorldTransformRecursive()`) é executado automaticamente. Nenhum caminho de escrita "atalha" o cache.

**Alternativas consideradas:**
- (B) `MutableTransform` como estado do nó, mantendo `Transform` imutável como valor de retorno. Rejeitado: dois tipos confunde a API; alocação por escrita não é o gargalo.
- (C) Proxy mágico em Python (`self.position.y = 5` re-escreveria via Kotlin). Rejeitado: viola o princípio de transparência; mascara o fato de que Vec2 é imutável.
- (D) Apenas setters `setPosition(x = ..., y = ...)` com named args. Rejeitado parcialmente: funciona em Kotlin, mas não traduz limpo para Python (defaults Python avaliam em def-time). Properties cobrem ambos os mundos.

### Decision 3: `worldTransform()` → `world()` ; remover `worldPosition()`

**Mudança**: renomear `Node2D.worldTransform(): Transform` para `Node2D.world(): Transform`. Remover `Node2D.worldPosition(): Vec2` — call sites migram para `node.world().position`.

**Por que função, não property:**
- `world()` é computado (compõe a cadeia de ancestrais, mesmo que cacheado). Property `world` sinalizaria leitura barata de campo e seria atribuível (`node.world = ...`), o que não faz sentido.
- Convenção Java/Bean: `getWorld()` em Kotlin viraria property `world` via convenção JVM, criando exatamente a ambiguidade que queremos evitar para callers cross-language.
- Função explicitamente parenteses-call deixa o "isso computa" visível na call site.

**Por que remover `worldPosition()` (em vez de mantê-lo como alias):**
- A API fica menor e mais coerente. Uma vez que `world()` existe, `worldPosition()` é redundância equivalente a `world().position`.
- Reduz a superfície de bindings Python (menos stubs `.pyi`).
- Migração é mecânica (`worldPosition()` → `world().position`) e o blast radius está confinado ao monorepo.

**Alternativas consideradas:**
- Manter ambos (`world()` + `worldPosition()`). Rejeitado: redundância sem ganho.
- Property `world: Transform`. Rejeitado pelo argumento de transparência acima.

### Decision 4: Migração das callsites é varredura única (sem deprecation period)

A engine ainda não tem usuários externos / versão pública. Os call sites afetados vivem todos no monorepo (`:engine`, `:engine-bundle`, `:engine-bundle-python`, `:games:*`, testes). Migração é grep + sed + compilar; sem necessidade de período de coexistência com aliases @Deprecated.

## Risks / Trade-offs

**[R1] `self.position.y = X` em Python lança `AttributeError`** → Documentar explicitamente nos stubs `.pyi` (docstring de `Vec2`) e na seção "Scripting contract" do CLAUDE.md. O caminho correto (`self.position = Vec2(self.position.x, novo_y)`) deve aparecer em exemplo no `paddle.py` migrado. Fail-fast com mensagem clara > silenciosamente ignorar.

**[R2] Quebra dupla (`worldTransform` removido, `worldPosition` removido)** → Compilação não passa até todos os call sites serem migrados. Mitigação: a tarefa de migração é explicitada em `tasks.md` com lista exaustiva de arquivos. Garante que rodar `./gradlew build` valida a varredura completa.

**[R3] Folhas `open` permitem subclasses não-serializáveis acidentalmente** → Comportamento idêntico ao já existente de `Label` e `BoxCollider` hoje. Se uma subclasse de jogo quiser entrar num `scene.json`, ela precisa se registrar no `NodeRegistry` (sem mudança). Subclasses puramente code-only não precisam.

**[R4] Property `rotation` é `Float` (não `Angle` tipo seguro)** → Mantém simetria com `Transform.rotation: Float`. Introduzir tipo `Angle` seria mudança ortogonal e maior, não cabe nesta change. Documentar nas KDoc "radianos".

**[R5] `world()` como função pode confundir quem espera property simétrica a `position`** → Fica explícito na KDoc: "function, not property — sinaliza composição/cache subjacente". É exatamente o trade-off escolhido por transparência.

## Migration Plan

**Fase 1 — Engine API** (`:engine`):
1. Adicionar properties `position`, `rotation`, `scale` em `Node2D.kt`.
2. Renomear `worldTransform()` → `world()` em `Node2D.kt`; remover `worldPosition()`.
3. Trocar modificadores para `open class` nas folhas listadas.
4. Atualizar call sites internos em `SceneTree`, `PhysicsSystem`, `BoxCollider`, `Camera2D`, `DebugOverlay*`.

**Fase 2 — Tests**:
5. Renomear `WorldTransformTest`/`WorldTransformCacheTest` (ou ajustar conteúdo mantendo nome) para usar `world()`.
6. Atualizar `Camera2DTest`, `SceneRenderCameraTest`.
7. Adicionar teste cobrindo cada um dos três novos accessors (set propaga para cache, leitura consistente).

**Fase 3 — Bundles & jogos**:
8. Atualizar `:engine-bundle` se referenciar nomes antigos (provavelmente não).
9. Atualizar stubs `.pyi` em `:engine-bundle-python/src/main/resources/stubs/engine/`.
10. Migrar scripts Python: `paddle.py`, `ball.py`, `goal.py`, `score.py`, `pong_scene.py`.
11. Migrar Kotlin de `:games:demos`, `:games:tictactoe`, `:games:hello-world`.

**Fase 4 — Validação**:
12. `./gradlew build` deve passar.
13. Rodar `:games:pong:run`, `:games:demos:run`, `:games:tictactoe:run`, `:games:hello-world:run` — sanity visual.

**Rollback**: se algo der errado, `git revert` da change inteira. Sem migração de dados (apenas código + scripts texto + stubs); `scene.json` não tem schema relacionado a essa mudança.
