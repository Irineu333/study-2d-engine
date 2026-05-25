## Context

`PhysicsSystem.step(tree)` em `collision-overhaul` segue um pipeline de uma passada:

1. Limpa pares com endpoints detached.
2. Coleta `objects` (CollisionObject2D não-disabled), e para cada par (i, j) com `i < j`, testa shapes; se algum par-de-shapes intersecta, adiciona `UnorderedPair(a, b)` em `currentOverlapping`.
3. Dispatch `_exited` para pares em `previousOverlapping - currentOverlapping`.
4. Dispatch `_entered` para pares em `currentOverlapping - previousOverlapping`.
5. `previousOverlapping = currentOverlapping`.

O dispatch invoca hooks Kotlin (`onAreaEntered`, ...) que delegam para scripts (`_on_area_entered`, ...). Scripts respondem mutando transforms — empurram corpos, swappam velocidades, etc.

O problema: o `currentOverlapping` é **computado uma vez no passo 2 com os transforms anteriores ao dispatch**. Quando o dispatch do passo 4 muda transforms de A e B (resolve overlap deles), o snapshot já registrado não reflete esses transforms novos. Para o par (A, C) que vinha no snapshot como overlapping mas que A acabou de "sair" via empurrão lateral, ainda dispatchamos `_entered`. Reciprocamente, se a resposta de (A, B) **reintroduz** overlap em (B, D) que estava limpo no snapshot — não dispatchamos nada nesse step. Esse último é o caso que faz KR1: cadeias de pile-ups deixam transições enter/exit invisíveis dentro do mesmo step.

## Goals / Non-Goals

**Goals:**

- `_entered` / `_exited` são chamados para toda transição real de overlap, mesmo quando emergem do mid-step (resposta de outro par muda essa situação).
- O snapshot final `previousOverlapping` ao fim do `step()` reflete o estado real após o último dispatch.
- Caso comum (sem pile-up) mantém uma única passada — sem custo extra.
- Pile-ups patológicos têm fail-safe (teto de iterações + log de warning).

**Non-Goals:**

- **Não** introduz constraint solver iterativo estilo Box2D/Bullet. O engine continua sem física de corpo rígido — a resposta a contatos segue sendo do script.
- **Não** muda a API de scripts (hooks, signals, payloads).
- **Não** garante "última snapshot é vazia" — pares ainda podem ficar overlapping no fim do step (overlap sustentado é estado válido; só não dispara eventos extras).
- **Não** garante determinismo de ordem do dispatch entre iterações — `UnorderedPair` continua hash-based, ordem de iteração de `HashSet` não é especificada. Documentado.
- **Não** resolve tunneling de alta velocidade (continua deferred a CCD/raycast em change futura).

## Decisions

### D1. Loop convergente com snapshot por iteração

**Decisão:** o `step()` vira:

```kotlin
fun step(tree: SceneTree) {
    previousOverlapping.removeAll { !it.a.isLive || !it.b.isLive }
    val objects = collectObjects(tree).filter { !it.disabled }
    tree.beginPhysicsPhase()
    try {
        var iteration = 0
        var dispatchedSomething = true
        while (dispatchedSomething && iteration < MAX_RESOLUTION_ITERATIONS) {
            val currentOverlapping = computeOverlapping(objects)
            val newlyEntered = currentOverlapping - previousOverlapping
            val newlyExited = previousOverlapping - currentOverlapping
            dispatchedSomething = newlyEntered.isNotEmpty() || newlyExited.isNotEmpty()
            for (pair in newlyExited) dispatchExit(pair)
            for (pair in newlyEntered) dispatchEnter(pair)
            previousOverlapping.clear()
            previousOverlapping.addAll(currentOverlapping)
            iteration++
        }
        if (iteration == MAX_RESOLUTION_ITERATIONS && dispatchedSomething) {
            Log.w(TAG, "PhysicsSystem: hit MAX_RESOLUTION_ITERATIONS=$MAX_RESOLUTION_ITERATIONS")
        }
    } finally {
        tree.endPhysicsPhase()
    }
}
```

**Por quê:** simples, didático, sem alocação extra além do snapshot por iteração. `previousOverlapping` é mutado no fim de cada iteração — a iteração seguinte vê "tudo já dispatched" e só dispatcha o que mudou em resposta ao dispatch anterior. Convergência: se ninguém respondeu (nenhum script mutou nada relevante), `currentOverlapping` da iteração N+1 == `currentOverlapping` da iteração N → `newlyEntered` e `newlyExited` vazios → loop sai.

**Custo:** caso comum (script não muda transforms ou os pares novos não cascateiam) gasta exatamente 1 iteração. Pile-ups densos: ~2-4 iterações típicas. Cada iteração é O(N²) na quantidade de corpos.

**Alternativa rejeitada — dispatch incremental:** detectar a mudança específica que o script fez e recomputar só os pares afetados. Mais eficiente em teoria, mas exige rastrear quais transforms mudaram entre hooks; complexidade alta para ganho marginal nas densidades atuais.

### D2. `MAX_RESOLUTION_ITERATIONS = 8`

**Decisão:** teto rígido em 8 iterações por step. Se atingir o teto e ainda houve `dispatchedSomething`, `Log.w` com a contagem de pares em transição.

**Por quê:** Box2D/Bullet usam valores similares (8-10) para constraint iteration. Pile-ups didáticos convergem em ≤4; o teto cobre patologias sem deixar o engine travar num loop. O log é didático: sinaliza "tem algo errado no script, está oscilando" sem crashar gameplay.

**Custo:** zero no caso comum. Configurável via constante interna; futuro pode promover a `@Inspect` em algum `PhysicsConfig`.

### D3. Ordem entre `exit` e `enter` dentro de uma iteração

**Decisão:** dentro de cada iteração, dispatch **exit** primeiro, depois **enter**, como hoje. Não muda.

**Por quê:** o invariante já documentado em `engine-core` spec ("enter dispatches MUST run after exit dispatches within the same step") segue valendo. Múltiplas iterações apenas significam que esse pareamento exit-then-enter roda múltiplas vezes; nenhuma quebra do invariante para o observador externo.

### D4. `previousOverlapping` muta entre iterações, exit cleanup roda só uma vez

**Decisão:** o `previousOverlapping.removeAll { !it.a.isLive || !it.b.isLive }` (cleanup de detached) roda **uma vez no início** do step, não por iteração. Iterações internas não removem por liveness — a `applyPending` do GameLoop entre steps dá conta de detached entre frames.

**Por quê:** detached durante o dispatch de um step é caso raro (script chamando `removeChild` num corpo do pair); mesmo se acontecer, a próxima iteração não consegue dispatcha-lo via cache `tree?.input`, mas a verificação `if (!a.isLive) continue` no loop de coleta protege. Manter exit cleanup fora do loop interno simplifica.

### D5. SAT (collision-rotated-shapes) e iteração são ortogonais

**Decisão:** esta change é independente de `collision-rotated-shapes`. Implementação não depende da outra; arquivamento pode ser em qualquer ordem.

**Por quê:** Demo 4 (KR1) é axis-aligned — não tem rotação. Demo 5 (KR2) sofre KR2 não-KR1. Embora os dois fixes melhorem o conjunto, são problemas separados em arquivos separados. Documentado para evitar acoplamento gratuito.

## Risks / Trade-offs

- **R1. Oscilação patológica.** Script pode reagir a `_entered` empurrando A para fora de B, e a `_exited` (imediatamente seguinte na próxima iteração) empurrando de volta — loop sem convergência. Mitigação: `MAX_RESOLUTION_ITERATIONS = 8` + `Log.w` quando atingido. Diagnóstico via log; não crasha.
- **R2. Tempo gasto em pile-ups.** Pile-up de 30 corpos × 4 iterações × O(900) pares = 3600 testes de overlap por step. Em 60Hz, 216k/s — irrelevante para SAT 4-eixos. Documentado.
- **R3. Ordem não-determinística entre iterações.** `HashSet` não garante ordem. Scripts cuja resposta depende da ordem do dispatch (e que entreviram cadeias específicas) podem ver comportamento ligeiramente diferente. Aceitável — Godot também não promete ordem.
- **R4. Diferença sutil de comportamento para overlap sustentado quando script muta o ambiente.** Hoje: se par (A, B) está sobreposto e script muda C (em outro par), nada acontece. Pós-fix: idem, *exceto* se essa mudança de C reintroduzir overlap em outro lugar; aí o novo `_entered` dispara nesse step em vez do próximo. Mais cedo, mas mantém "exatamente uma chamada por begin-of-overlap real". Documentado.

## Migration Plan

1. Em `PhysicsSystem.kt`, extrair o corpo atual do `step()` (do `objects = ...` até `previousOverlapping.addAll(...)`) para `private fun computeOverlapping(objects)`.
2. Reescrever `step(tree)` com o loop `while (dispatchedSomething && iteration < MAX_RESOLUTION_ITERATIONS)` conforme D1.
3. Adicionar `companion object { private const val MAX_RESOLUTION_ITERATIONS = 8; private const val TAG = "PhysicsSystem" }` se ainda não houver.
4. Cobrir com teste novo: três `StaticBody2D` com `CollisionShape2D + RectangleShape2D` enfileirados de modo que resposta de (A,B) cause overlap (B,C) — verificar que `B.bodyEnters` contém ambos `A` e `C` ao fim do step. Deve falhar pré-fix e passar pós-fix.
5. Remover comentário "Known regression KR1" no `CollisionStressDemo.Ball.onAreaEntered`.
6. Smoke manual: `./gradlew :games:demos:run`, tecla 4, 5 segundos rodando — observar que bolinhas não tunelam mais.
