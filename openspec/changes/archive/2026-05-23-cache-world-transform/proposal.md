## Why

`Node2D.worldTransform()` hoje recomputa a cadeia inteira do nó até a raiz a cada chamada, alocando uma `ArrayDeque`, percorrendo todos os ancestrais e produzindo um `Transform` novo a cada `compose`. O `PhysicsSystem` invoca `BoxCollider.aabb()` em todos os pares de colliders ativos (broad phase O(N²) intencional), e cada `aabb()` chama `worldTransform()` — o que multiplica o custo: com N colliders, são ~2·N² recálculos completos por frame, mesmo quando nada se moveu. O mesmo `Transform` é recomputado dezenas a centenas de vezes por frame. Render e scripts que consultam `worldPosition()` pagam o mesmo preço.

A solução é cachear o resultado em cada `Node2D` e invalidar o cache (self + descendentes) sempre que o `transform` local muda ou o nó é re-parentado. Próximas leituras retornam o cache em O(1); recomputações ficam restritas ao subgrafo realmente afetado pela mudança.

## What Changes

- `Node2D` ganha um cache interno `cachedWorldTransform: Transform?` (null = dirty), populado de forma lazy na primeira chamada de `worldTransform()` após uma invalidação.
- `Node2D.transform` deixa de ser uma `var` simples e passa a expor um setter custom que invalida `this` e todos os descendentes `Node2D` (incluindo descendentes que não são `Node2D` no meio do caminho, atravessando-os).
- Mudanças de hierarquia (re-parenting via `Node.parent` setter, `addChild`, `removeChild`) invalidam o subgrafo afetado.
- `worldTransform()` continua determinístico e com a mesma assinatura/retorno; apenas o caminho de cálculo muda. Sem mudança no contrato público.
- O algoritmo de invalidação é **eager**: setter percorre descendentes marcando `cachedWorldTransform = null`. Decisão didática — abre porta para variantes lazy (generation counter) se hierarquias profundas virarem real no editor.
- `Transform`, `Vec2`, `compose` e demais primitivas matemáticas permanecem imutáveis e inalteradas.

## Capabilities

### New Capabilities

(nenhuma — esta change apenas refina um requisito existente)

### Modified Capabilities

- `engine-core`: o requisito "Hierarchical world-space transforms" passa a exigir que `worldTransform()` cacheie o resultado por nó e invalide o cache em mudanças locais de transform e em mudanças de hierarquia, sem alterar o resultado observável.

## Impact

- **Código tocado**: `engine/src/main/kotlin/com/neoutils/engine/scene/Node2D.kt` (cache + setter custom + invalidação), `engine/src/main/kotlin/com/neoutils/engine/scene/Node.kt` (gancho em mudança de hierarquia — `parent`/`addChild`/`removeChild` conforme onde mora hoje).
- **Beneficiários automáticos**: `BoxCollider.aabb()` (corta o multiplicador N² do broad phase), `Shape.onRender`, `Text.onRender`, scripts Python que consultam `worldPosition()` via binding.
- **APIs públicas**: nenhuma adicionada nem removida. Apenas comportamento de cache; chamadores não precisam saber.
- **Risco de regressão**: cache obsoleto se algum caminho de mutação for esquecido (escrita direta em campo, reparent fora do setter). Mitigação: setter custom + cobertura de testes para reparent e mudança de transform intermediário.
- **Performance esperada**: leituras consecutivas de `worldTransform()` no mesmo frame caem para O(1); recomputação acontece apenas no subgrafo realmente mutado, na profundidade dele. Para Pong (~6 nodes, 1 nível de profundidade), a invalidação é praticamente nop; o ganho vem das N² leituras do broad phase.
- **Dependências externas**: nenhuma.
- **Compatibilidade**: jogos existentes (`pong`, `tictactoe`, `demos`) não precisam de mudança — o setter custom intercepta o padrão atual de `node.transform = node.transform.copy(...)`.
