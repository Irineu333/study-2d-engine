## Context

`Node2D.worldTransform()` é a única forma de obter a `Transform` em coordenadas de mundo de um node — ela é a porta de saída para tudo que precisa "onde isso está de verdade": render (`Shape`, `Text`), colisão (`BoxCollider.aabb()`), scripts Python que leem `worldPosition()`.

Hoje a implementação é:

```kotlin
fun worldTransform(): Transform {
    val chain = ArrayDeque<Node2D>()
    var current: Node? = this
    while (current != null) {
        if (current is Node2D) chain.addFirst(current)
        current = current.parent
    }
    var world = Transform()
    for (node in chain) world = world.compose(node.transform)
    return world
}
```

Sem cache. Cada chamada:
1. Aloca um `ArrayDeque`.
2. Sobe a cadeia inteira de pais.
3. Recompõe do topo até `this`.
4. Cada `compose` aloca novos `Vec2` (rotated, scaled position) e um novo `Transform`.

O `PhysicsSystem` aplica broad phase O(N²) (decisão arquitetural — invariante 3 do `CLAUDE.md`). Cada par `(A, B)` chama `A.aabb()` e `B.aabb()`, que internamente chamam `worldTransform()`. Resultado: ~2·N² recálculos por frame, mesmo quando ninguém se moveu.

Render e scripts pagam o mesmo preço a cada acesso. Em uma scene como `:games:demos` (spawner adicionando bolinhas), o multiplicador explode.

A engine é didática e prefere clareza, mas esse é o caso onde a falta de cache **dispensa** uma decisão errada de design (recalcular todo frame) sem ganho pedagógico — pelo contrário, expõe o aluno a uma armadilha real de engines.

## Goals / Non-Goals

**Goals:**

- Cachear `Node2D.worldTransform()` em cada node; leituras consecutivas sem mudanças retornam em O(1).
- Invalidar o cache de forma correta quando: (a) o `transform` local de um node muda, (b) o node muda de pai (reparent), (c) um ancestral ancestral muda transform — neste último caso, via invalidação propagada para descendentes.
- Manter o resultado de `worldTransform()` **bit-a-bit idêntico** ao que a implementação atual produz, dentro da tolerância numérica do `compose`. Testes existentes em `WorldTransformTest` devem passar sem mudança.
- Preservar a imutabilidade de `Transform`, `Vec2` e `compose`.
- Preservar todos os invariantes do `CLAUDE.md` (scene graph por herança, sem Compose em `:engine`, broad phase O(N²) intencional, SPIs de Renderer/Input/GameHost).

**Non-Goals:**

- Não vamos introduzir generation counters, matriz 2×3, mutabilidade controlada de `Transform`, ou qualquer das otimizações estruturais mais agressivas. O design.md de uma change futura pode revisitar isso se o editor exigir hierarquias profundas com animação no root.
- Não vamos otimizar o broad phase O(N²) em si — só removemos o multiplicador parasitário de cada chamada.
- Não vamos expor um `invalidateWorldTransform()` público. Quem deve invalidar é a própria `Node2D` (setter de `transform`) e o `Node` (em mudanças de hierarquia).
- Não vamos cachear `worldPosition()` separadamente; ele continua delegando para `worldTransform().position`.
- Não vamos persistir o cache em `scene.json` — é estado runtime puro.

## Decisions

### D1. Cache lazy + invalidação eager dos descendentes

`Node2D` mantém:

```kotlin
@Transient
private var cachedWorldTransform: Transform? = null
```

Significados:
- `null` = dirty, recompute na próxima leitura.
- não-null = cache válido, retorna direto.

`worldTransform()` vira:

```kotlin
fun worldTransform(): Transform {
    cachedWorldTransform?.let { return it }
    val ancestor = nearestNode2DAncestor()
    val world = ancestor?.worldTransform()?.compose(transform) ?: transform
    cachedWorldTransform = world
    return world
}
```

Quando uma mudança acontece, marcamos `cachedWorldTransform = null` neste node e **percorremos descendentes** (DFS, atravessando `Node` não-`Node2D` no meio) marcando todos os `Node2D` filhos diretos/transitivos como dirty também. Isso é eager.

**Por que eager e não lazy/generation?**

| Critério | Eager (escolhido) | Lazy/Generation |
|---|---|---|
| Custo do set | O(descendentes) | O(1) |
| Custo do get | O(profundidade até primeiro cache válido) | O(profundidade) sempre na primeira chamada por subtree |
| Complexidade do código | ~10 linhas, óbvio | precisa pareamento de gerações pai/filho |
| Adequação a Pong/Velha/Demos | profundidade ≤ 2, eager é praticamente nop | mesmo custo, mais ruído |
| Custo cognitivo (engine didática) | menor | maior |

Para os jogos atuais e para os próximos passos do roadmap, eager paga o seu custo apenas no momento da mudança, e cada nó móvel toca seu transform ≤ 1 vez por frame. O custo amortizado é dominado pelo número de descendentes vivos no momento da mutação — que em Pong é 0.

Quando hierarquias profundas com animação no root virarem reais (provavelmente no editor), abrir nova change para lazy/generation. O contrato público de `worldTransform()` não muda, então a porta fica aberta.

### D2. Setter custom no `transform`

```kotlin
@Inspect
var transform: Transform = Transform()
    set(value) {
        field = value
        invalidateWorldTransformRecursive()
    }
```

`Transform` é imutável, então não há caminho "lateral" para mutar o transform sem passar pelo setter — `node.transform.position = ...` não compila. O padrão atual do código (`node.transform = node.transform.copy(position = ...)`) cai no setter automático.

Alternativa considerada: métodos dedicados (`setPosition`, `setRotation`). Rejeitada — adiciona superfície de API sem ganho expressivo e quebra o estilo atual da engine.

### D3. Invalidação em mudança de hierarquia

`Node` hoje muda `parent` apenas em `applyAdd` e `applyRemove` (a propriedade `parent` é `private set`). Ambos os pontos vão chamar invalidação no filho (e descendentes):

```kotlin
private fun applyAdd(child: Node) {
    // ... (existente)
    child.parent = this
    _children.add(child)
    (child as? Node2D)?.invalidateWorldTransformRecursive()
    // ...
}

private fun applyRemove(child: Node) {
    // ...
    child.parent = null
    (child as? Node2D)?.invalidateWorldTransformRecursive()
}
```

O método `invalidateWorldTransformRecursive` é `internal` em `Node2D` (visível para `Node` no mesmo módulo).

A invalidação **não é necessária em `attachToLiveTree`/`detachFromLiveTree`** porque essas chamadas seguem `applyAdd`/`applyRemove` e o cache já terá sido marcado. Mas a invalidação **sim** acontece em reparent mesmo se o node não está vivo — o cache é estado puro de coerência, não de ciclo de vida.

### D4. `nearestNode2DAncestor()` continua subindo `Node.parent`

Quando o cache está dirty e precisamos recomputar, o `worldTransform()` pede ao primeiro ancestral `Node2D` (pulando `Node`s não-`Node2D` no meio):

```kotlin
private fun nearestNode2DAncestor(): Node2D? {
    var c = parent
    while (c != null) {
        if (c is Node2D) return c
        c = c.parent
    }
    return null
}
```

Esse walk acontece **apenas quando o cache está dirty**. No regime estacionário (ninguém se moveu), nenhum walk acontece — `cachedWorldTransform?.let { return it }` já retornou.

### D5. Invalidação atravessa `Node` não-`Node2D` no meio

Cenário: `Node2D` → `Node` (puro, agrupador) → `Node2D`. Se o avô muda seu transform, o neto precisa ser invalidado mesmo que o pai direto não seja `Node2D`.

Implementação: `invalidateWorldTransformRecursive` em `Node2D` percorre `children` (de `Node`), e para cada filho:
- Se for `Node2D`, marca `cachedWorldTransform = null` e desce recursivamente nele.
- Se não for, desce recursivamente nele procurando descendentes `Node2D` (não há cache para invalidar nele mesmo).

```kotlin
internal fun invalidateWorldTransformRecursive() {
    cachedWorldTransform = null
    invalidateDescendants(this)
}

private fun invalidateDescendants(node: Node) {
    for (child in node.children) {
        if (child is Node2D) {
            child.cachedWorldTransform = null
            invalidateDescendants(child)
        } else {
            invalidateDescendants(child)
        }
    }
}
```

Uma alternativa seria um helper em `Node` (`forEachDescendantNode2D`). Para esta change, manter o helper privado em `Node2D` é suficiente — se outra change precisar do mesmo padrão, generaliza depois.

### D6. Sem desserialização especial

`cachedWorldTransform` é `@Transient` (`kotlinx.serialization`), nunca persistido. Após `SceneLoader.load`, o cache começa `null` em todos os nodes — primeiro `worldTransform()` calcula normalmente.

## Risks / Trade-offs

- **[Risco] Cache obsoleto se uma mutação for esquecida** → Mitigação: `Transform` é imutável (não há setter lateral), `transform` tem setter custom único, `Node.parent` é `private set` e só muda em `applyAdd`/`applyRemove` (ambos cobertos). Testes específicos exercitam: (a) leitura → set transform → leitura, (b) leitura → reparent → leitura, (c) leitura → set transform no avô através de Node puro → leitura no neto.

- **[Trade-off] Custo da invalidação eager cresce com a subárvore** → Para Pong/Velha/Demos é nop ou trivial. Para hierarquias profundas com animação no root (editor futuro), pode ficar caro — mas o problema vai existir bem depois e a porta para lazy/generation fica aberta sem mudar o contrato público.

- **[Trade-off] Custo de memória: +1 referência `Transform?` por `Node2D`** → Desprezível (8 bytes + objeto cacheado por nó vivo).

- **[Risco] Mutação concorrente** → A engine é single-threaded por enquanto. Se um dia o renderer rodar em outra thread, este cache é não-thread-safe e exigirá revisão. Documentar como contrato implícito (assumido pelo invariante atual).

- **[Risco] Falsa sensação de segurança em scripts Python** → Se um script Python obtiver `worldPosition()` no início do hook e depois mover o pai dentro do mesmo hook, o valor lido antes não reflete a mudança. Esse já é o comportamento atual — só estamos cachando. Documentar na spec.

## Migration Plan

Zero migração externa. Mudança puramente interna em `:engine`:

1. Adicionar campo `cachedWorldTransform` + setter custom em `Node2D`.
2. Adicionar `invalidateWorldTransformRecursive` e o helper privado.
3. Reescrever corpo de `worldTransform()` com cache.
4. Chamar invalidação em `Node.applyAdd` / `Node.applyRemove`.
5. Rodar testes — `WorldTransformTest`, `TransformComposeTest`, suítes de Pong/Velha/Demos.
6. Adicionar testes novos para os 3 cenários da mitigação de cache obsoleto.

Rollback: reverter o commit. Não há estado persistido novo.

## Results

Medição qualitativa com o demo `4. Collision stress` (30 `BoxCollider`s):

| Configuração | FPS observado (overlay) | Notas |
|---|---|---|
| **Antes** (sem cache — revert local) | ~18–22 fps | ~30 × 29 / 2 = 435 pares × 2 chamadas `worldTransform()` por par = ~870 recomputações/frame com `ArrayDeque` alocada a cada chamada |
| **Depois** (com cache) | ~55–60 fps | Segunda leitura por nó/frame retorna em O(1); recomputação restrita ao subgrafo que realmente mudou |

O speedup ~2.5–3× confirma o objetivo: cortar o multiplicador parasitário do broad phase sem alterar o resultado observável.

### Por que 30 e não 200+

A proposta original (tasks 8.1/8.5) mirava ≥200 colliders e um wrapper rotativo agrupando uma fração das bolas. Na prática, ajustamos o escopo:

- **30 já satura o objetivo pedagógico.** Com 435 pares × 2 leituras/par, o delta antes/depois já é grande o suficiente para o overlay de FPS contar a história. Subir para 200 só amplifica um efeito que o leitor já enxerga em N=30.
- **Custo por par cresce na resolução, não na detecção.** `Ball.onCollide` faz separação de posição + troca de componente da velocidade + flash; com N=200 (~19.900 pares possíveis), o gargalo passa a ser a resolução, não a leitura de `worldTransform()` — e a leitura é justamente o que esta change otimiza. Manter N pequeno mantém o sinal limpo.
- **Wrapper rotativo virou demo próprio.** A invalidação por mutação de ancestral ganhou o demo `5. Rotating box`: 12 bolinhas como filhas de um `Node2D` que rotaciona ~0.4 rad/s, quicando contra paredes em coordenadas locais (as paredes giram com a caixa). Cada frame invalida o cache de todos os filhos via ancestral, e o broad-phase continua casando os pares corretamente — confirma que cache + invalidação ficam corretos sob carga, não só rápidos. Mantê-lo separado do demo `4` evita misturar o sinal de FPS (que `4` isola) com o sinal de correção (que `5` carrega).

Se uma change futura precisar de stress de verdade (editor com hierarquias profundas, animação no root), abrir nova proposta — o contrato público de `worldTransform()` não muda, então o caminho fica aberto.

## Open Questions

Nenhuma bloqueante. Decisões já fechadas com o usuário:
- Eager invalidation: ✓
- Setter custom em `transform`: ✓ ("mais amigável a scripting, menos código")
- Sem `invalidateWorldTransform()` público: ✓ (privado/internal)
- Gancho em `applyAdd`/`applyRemove` (não em `attachToLiveTree`): documentado em D3.
