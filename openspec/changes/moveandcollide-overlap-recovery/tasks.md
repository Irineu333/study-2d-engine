## 0. Pré-requisito de sequência

- [ ] 0.1 Garantir que a change `fix-pong-paddle-as-characterbody` esteja arquivada antes de aplicar esta (o delta `pong-sample` daqui remove o nudge que aquela introduziu). Se ainda não estiver, arquivar primeiro (`/opsx:archive fix-pong-paddle-as-characterbody`).

## 1. Engine — recovery de overlap inicial no moveAndCollide

- [ ] 1.1 Em `CharacterBody2D.moveAndCollide`, transformar a varredura única num loop de recovery limitado (teto fixo, alinhar com `TOI_ITERATIONS = 4`): em `toi == 0`, aplicar `depenetration` e **re-varrer o motion restante a partir da posição depenetrada** até o teto; gastar motion que aponta para fora do colisor, sem progresso para motion que aponta para dentro (D1).
- [ ] 1.2 Reportar no `KinematicCollision2D` o `point`/`normal`/`collider` do **primeiro** contato, com `position` avançada por todo o recovery e `remainder` = motion não-consumido pós-recovery (D2). Preservar o caminho `toi > 0` (contato limpo) inalterado e o caso `motion == Vec2.ZERO` (só depenetra, `remainder == Vec2.ZERO`).
- [ ] 1.3 Avaliar extrair um helper privado compartilhado com o TOI loop de `RigidBody2D` em `PhysicsSystem`; se não ficar claramente mais limpo, manter local no `CharacterBody2D` (D4). Garantir terminação (sem loop infinito) e nenhum vazamento de tipo de backend.

## 2. Testes da engine

- [ ] 2.1 Em `CharacterBody2DTest`: atualizar o cenário de starting-overlap com `motion == Vec2.ZERO` (segue só depenetrando) e **adicionar** cenários novos — motion para fora aplicado (corpo escapa), motion para dentro sem progresso (não tunela), e o `remainder` pós-recovery.
- [ ] 2.2 Em `BehavioralSweepTest` (ou novo harness de física): cenário multi-frame de **re-pressão sustentada** — um peer re-crava um overlap marginal todo frame e o corpo dirigido para fora separa em poucos frames (guarda contra o freeze da quina), sem depender dos scripts do jogo.
- [ ] 2.3 `./gradlew :engine:test` verde (suíte de física inteira intacta: `PhysicsSystemTest`, `SweepTest`, `ContactNormalizationTest`, `CcdStressBenchmark`, gizmos).

## 3. Demos Kotlin — regressão

- [ ] 3.1 Conferir que os demos que usam `moveAndCollide` (`RotatingBoxDemo`, `TumblingSwarmDemo`, `CollisionStressDemo`, `BoundaryWalls`) mantêm trajetória/comportamento (contatos limpos `toi > 0`, fora do caminho do recovery). `./gradlew :games:demos:build` passa.

## 4. Jogo Pong — remover a gambiarra

- [ ] 4.1 Em `games/pong/src/main/resources/pong/scripts/ball.py`, remover o bloco "starting-overlap escape" (escrita direta de `self.position`). Manter o classificador face-vs-edge por x e o `h_sign` por lado.
- [ ] 4.2 Revalidar quina/lateral com o harness headless de ablation (mesmo formato da change do Pong): **0 traps** sem o nudge. Remover o harness após validar (era diagnóstico).
- [ ] 4.3 `./gradlew :games:pong:build` passa; rodar o Pong e confirmar manualmente que quina/lateral seguem limpas.

## 5. Sincronização e fechamento

- [ ] 5.1 Atualizar a doc da SPI: KDoc de `CharacterBody2D.moveAndCollide` descrevendo o recovery em starting overlap; conferir se algo em `CLAUDE.md`/`README` referencia o comportamento antigo.
- [ ] 5.2 Rodar `openspec validate moveandcollide-overlap-recovery` e `/opsx:verify`.
