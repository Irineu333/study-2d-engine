## 0. Pré-requisito de sequência

- [x] 0.1 Garantir que a change `fix-pong-paddle-as-characterbody` esteja arquivada antes de aplicar esta (o delta `pong-sample` daqui remove o nudge que aquela introduziu). Se ainda não estiver, arquivar primeiro (`/opsx:archive fix-pong-paddle-as-characterbody`).

## 1. Engine — recovery de overlap inicial no moveAndCollide

- [x] 1.1 Em `CharacterBody2D.moveAndCollide`, transformar a varredura única num loop de recovery limitado (teto fixo, alinhar com `TOI_ITERATIONS = 4`): em `toi == 0`, aplicar `depenetration` e **re-varrer o motion restante a partir da posição depenetrada** até o teto; gastar motion que aponta para fora do colisor, sem progresso para motion que aponta para dentro (D1).
- [x] 1.2 Reportar no `KinematicCollision2D` o `point`/`normal`/`collider` do **primeiro** contato, com `position` avançada por todo o recovery e `remainder` = motion não-consumido pós-recovery (D2). Preservar o caminho `toi > 0` (contato limpo) inalterado e o caso `motion == Vec2.ZERO` (só depenetra, `remainder == Vec2.ZERO`).
- [x] 1.3 Avaliar extrair um helper privado compartilhado com o TOI loop de `RigidBody2D` em `PhysicsSystem`; se não ficar claramente mais limpo, manter local no `CharacterBody2D` (D4). Garantir terminação (sem loop infinito) e nenhum vazamento de tipo de backend. → **Mantido local** (`scanBestHit` privado em `CharacterBody2D`): o `sweepBestHit` de `PhysicsSystem` é tipado a `RigidBody2D` e acoplado ao impulse solver; compartilhar acoplaria o caminho kinematic (sem impulso) aos internos do `PhysicsSystem` sem deixar nenhum dos dois mais limpo. Terminação garantida pelo teto `RECOVERY_ITERATIONS = 4` + guarda de no-progress.

## 2. Testes da engine

- [x] 2.1 Em `CharacterBody2DTest`: atualizar o cenário de starting-overlap com `motion == Vec2.ZERO` (segue só depenetrando) e **adicionar** cenários novos — motion para fora aplicado (corpo escapa), motion para dentro sem progresso (não tunela), e o `remainder` pós-recovery.
- [x] 2.2 Em `BehavioralSweepTest` (ou novo harness de física): cenário multi-frame de **re-pressão sustentada** — um peer re-crava um overlap marginal todo frame e o corpo dirigido para fora separa em poucos frames (guarda contra o freeze da quina), sem depender dos scripts do jogo.
- [x] 2.3 `./gradlew :engine:test` verde (suíte de física inteira intacta: `PhysicsSystemTest`, `SweepTest`, `ContactNormalizationTest`, `CcdStressBenchmark`, gizmos).

## 3. Demos Kotlin — regressão

- [x] 3.1 Conferir que os demos que usam `moveAndCollide` (`RotatingBoxDemo`, `TumblingSwarmDemo`, `CollisionStressDemo`, `BoundaryWalls`) mantêm trajetória/comportamento (contatos limpos `toi > 0`, fora do caminho do recovery). `./gradlew :games:demos:build` passa.

## 4. Jogo Pong — remover a gambiarra

- [x] 4.1 Em `games/pong/src/main/resources/pong/scripts/ball.py`, remover o bloco "starting-overlap escape" (escrita direta de `self.position`). Manter o classificador face-vs-edge por x e o `h_sign` por lado.
- [x] 4.2 Revalidar quina/lateral com o harness headless de ablation (mesmo formato da change do Pong): **0 traps** sem o nudge. Remover o harness após validar (era diagnóstico). → Harness `PongCornerAblationHarness` varreu **600** condições de press na quina/lateral com paddle AI re-cravando, dirigindo o bundle real (`ball.py` + `paddle.py`) contra o `moveAndCollide` real: **0 traps**. Removido após validar. (Revelou um caso que o nudge mascarava: bola encostada de raspão na quina com penetração ~1e-5px e motion de saída — corrigido via skip de contato de raspão não-bloqueante em `scanBestHit`.)
- [x] 4.3 `./gradlew :games:pong:build` passa; rodar o Pong e confirmar manualmente que quina/lateral seguem limpas. → Build verde; verificação manual da GUI fica a cargo do usuário (não roda headless).

## 5. Sincronização e fechamento

- [x] 5.1 Atualizar a doc da SPI: KDoc de `CharacterBody2D.moveAndCollide` descrevendo o recovery em starting overlap; conferir se algo em `CLAUDE.md`/`README` referencia o comportamento antigo. → KDoc reescrito (seção "Starting-overlap recovery"). `CLAUDE.md`/`README` não descrevem o comportamento antigo de descarte; a descrição genérica de `CharacterBody2D` segue válida.
- [x] 5.2 Rodar `openspec validate moveandcollide-overlap-recovery` e `/opsx:verify`. → `openspec validate` passou ("is valid"). `/opsx:verify` é o próximo passo do workflow (a seguir).
