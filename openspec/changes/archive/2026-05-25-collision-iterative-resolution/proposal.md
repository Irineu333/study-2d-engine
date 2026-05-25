## Why

`collision-overhaul` adotou semântica Godot-style enter-only para colisão: `_entered` dispara uma vez no início da sobreposição, `_exited` uma vez no fim, nada entre. Funciona para o caso ideal — par de corpos colide, script responde com swap de velocidade + separação, próxima frame o par sai da `currentOverlapping` e está pronto para o próximo encontro.

Mas em **pile-ups de 3+ corpos**, o despacho do `PhysicsSystem.step` itera pares numa ordem fixa: para cada par novo, chama o hook nos dois lados, espera que o script resolva. O problema é que a resposta de **um** par pode reintroduzir overlap em **outro** par já processado nesse mesmo step. Como esse par já está em `previousOverlapping`, nenhum novo `_entered` dispara — e como nunca saiu (ainda overlaping após reintrodução), nenhum `_exited` também. O par fica grudado indefinidamente, e em frames seguintes tunela quando as velocidades os atravessam.

Isso é a regressão **KR1** documentada em `collision-overhaul/design.md`: Demo 4 (CollisionStress) exibe algumas bolinhas atravessando outras em pile-ups densos.

Esta change resolve introduzindo **iteração-até-convergência** dentro do `PhysicsSystem.step`: o sistema re-calcula `currentOverlapping`, dispatch enter/exit, e repete enquanto algum dispatch novo aparecer ou desaparecer, até estabilizar (ou até hit de teto de iterações). Cada pile-up converge em poucas iterações; o caso comum (0 ou 1 par novo por step) gasta uma iteração só, igual a hoje.

## What Changes

- `engine/.../physics/PhysicsSystem.kt`: `step(tree)` ganha loop interno até `currentOverlapping == previousOverlapping_atDispatch` (i.e., nenhum dispatch novo apareceu nesta iteração). Teto rígido `MAX_RESOLUTION_ITERATIONS = 8` para fail-safe (loop infinito impossível mas finito).
- Métrica/log: se o teto for atingido, `Log.w` com a contagem de pares ainda em transição (didático — sinaliza pile-up patológico sem crashar).
- A semântica externa (hooks + signals) é preservada: para o script, ainda é "uma chamada de `_entered` por begin-of-overlap real, uma de `_exited` por end-of-overlap real". A iteração interna só garante que essas transições sejam detectadas mesmo quando emergem de respostas de outros pares no mesmo step.
- Teste unitário cobrindo: três corpos enfileirados (A, B, C) onde resposta de (A,B) reintroduz overlap de (B,C) — pós-fix, B.onBodyEntered(C) dispara antes do step terminar; pré-fix (verificável removendo o loop) falha.
- Demo 4 (`CollisionStressDemo`) na change anterior tem comentário marcando KR1; remover esse comentário porque a regressão fica resolvida.

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `engine-core`: requisito de despacho de `PhysicsSystem.step` ganha cláusula sobre iteração-até-convergência.

## Impact

- **Código tocado:**
  - `engine/.../physics/PhysicsSystem.kt` — reestruturação do `step()` em loop convergente.
  - `engine/src/test/kotlin/com/neoutils/engine/physics/PhysicsSystemTest.kt` — novo cenário "3-body pile-up".
  - `games/demos/.../CollisionStressDemo.kt` — remoção do comentário "Known regression KR1".
  - `openspec/changes/collision-overhaul/design.md` (na archive): KR1 marcada como resolvida.
- **Performance:** caso comum (sem pile-up) gasta uma iteração — paridade com hoje. Pile-ups densos gastam 2-4 iterações tipicamente, todas O(N²) no número de corpos. Imperceptível em demos didáticos.
- **Sem impacto em:** API de scripts (hooks idênticos), `bounds()`, `DebugOverlay`, broad-phase, Pong/TicTacToe/Demos 1-3 (não exercitam pile-ups).
