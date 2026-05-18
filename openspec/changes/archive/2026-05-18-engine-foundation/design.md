## Context

O repositório é um template Kotlin Multiplatform + Compose Multiplatform com apenas um `App.kt` "Hello, World" em `:shared` e um launcher em `:desktopApp`. Vamos transformá-lo na fundação de uma 2D game engine code-only.

Restrições e premissas levantadas no explore mode:

- **Propósito principal**: aprender arquitetura de engine. Otimizar pra clareza didática e evolução incremental, não pra performance prematura.
- **Roadmap**: esta change → `event-driven-games` (jogo da velha, campo minado) → editor visual.
- **Inspiração de design**: Godot. Cada nó carrega lógica via herança (`Node` → `Node2D` → `Paddle`), em contraste com componentes anexáveis estilo Unity.
- **Stack escolhida**: Kotlin + Compose Multiplatform porque é o que o autor domina. Compose entra como primeiro **backend** da engine, não como modelo de programação dela.
- **Alvo de plataforma**: JVM Desktop nesta change. Multiplataforma é evolução futura.
- **Pong como prova viva**: dos três jogos do roadmap (velha, Pong, minado), só Pong força game loop + input contínuo + física + render simultaneamente. Daí a escolha como teste de aceitação.

## Goals / Non-Goals

**Goals:**

- Scene graph estilo Godot funcionando ponta a ponta com lifecycle (`onEnter`, `onUpdate`, `onRender`, `onExit`).
- Fronteira limpa entre engine e runtime: SPI de `Renderer` e `Input` com backend Compose como primeira (e única, por enquanto) implementação.
- Sistema de colisão minimamente útil: `Collider` como tipo de nó, `PhysicsSystem` central iterando colliders e emitindo `onCollide`.
- Game loop estável com delta time, suficiente pra rodar Pong a 60fps em desktop sem stutter perceptível.
- Pong jogável, autoexplicativo no código, exercitando toda a API pública da engine.
- DX inicial em produção desde o primeiro frame: FPS overlay, log estruturado, debug de colliders.
- `CLAUDE.md` como decision log perene, refletindo o que foi decidido nesta change.

**Non-Goals:**

- Editor visual (change separada).
- Multiplataforma além de JVM Desktop (Android/iOS/Web ficam pra depois).
- Sinais/eventos como primitiva da engine — esta change usa **referência direta** entre nós.
- Sprites com textura (apenas shapes primitivos: rect, circle, text).
- Áudio, animação por tween/keyframe, partículas.
- Serialização de cena (necessária pro editor, não pro code-only).
- Broad phase de colisão escalável (spatial hashing, BVH). Mantemos O(N²) ingênuo.
- Backends adicionais de Renderer/Input (Skiko puro, LWJGL).
- Hot reload de cena ou de código de gameplay.
- Múltiplas cenas/transições, save state, asset pipeline.

## Decisions

### Decisão 1: Scene graph estilo Godot (herança), não componentes (Unity)

`Node` é classe base abstrata. Comportamento de gameplay é adicionado por herança (`class Paddle : Node2D()` sobreescrevendo `onUpdate`/`onRender`). Não há lista de `Component` anexável por nó.

**Por quê:**
- Mais didático e mais idiomático em Kotlin (`sealed`, `abstract`, override) do que reinventar composição manual.
- Casa diretamente com editor visual estilo Godot no futuro: cada classe de Node tem suas propriedades inspetoráveis.
- Os 3 jogos do roadmap não exigem combinação dinâmica de comportamentos que justifique composição.

**Alternativas consideradas:**
- Componentes estilo Unity (`Node` carrega `List<Component>`): mais flexível, mas tende a virar ECS de mentira; overhead conceitual desnecessário para o escopo.
- ECS puro (Bevy/Unity DOTS): excelente performance, mas curva alta e não casa com objetivo didático nem com editor estilo Godot.

### Decisão 2: Comunicação entre nós por referência direta (sem sinais)

Pong tem comunicação trivial: `Ball` segura referências para os goals e walls; `Paddle` IA segura referência pra `Ball`. Sem sistema de mensagens nesta change.

**Por quê:**
- Para Pong, sinais seriam overengineering — referência direta é mais legível e mais fácil de debugar.
- Introduzir sinais aqui sem necessidade real travaria o design numa decisão prematura.

**Alternativas consideradas:**
- Sinais/eventos (estilo Godot `signal`): muito útil para jogos event-driven (velha, minado). Postergado para a próxima change, onde a necessidade vai aparecer organicamente.
- Lookup por path/nome na árvore (`$Path`): stringly-typed, frágil. Pula.

**Evolução documentada:** a change `event-driven-games` provavelmente vai introduzir sinais. Quando isso acontecer, este `design.md` é referência histórica do estado anterior.

### Decisão 3: Colisão como Collider-as-Node + PhysicsSystem central, broad phase O(N²)

`Collider` é um tipo de `Node`. `BoxCollider` é a única implementação concreta nesta change. `PhysicsSystem` itera todos os colliders ativos a cada tick, faz par-a-par, e invoca `onCollide` nos envolvidos. Não há resposta de física (impulsos, massa) — apenas detecção.

**Por quê:**
- Casa com a tese "tudo é um nó" do estilo Godot.
- Centralizar no `PhysicsSystem` mantém o `Node` agnóstico de colisão e permite trocar a fase ampla depois sem refatoração massiva.
- O(N²) com N pequeno (Pong tem ~6 colliders) é trivialmente correto e debugável.
- Resposta de colisão (reflexão da bola, por exemplo) fica como responsabilidade do nó de jogo (`Ball.onCollide(wall) { velocity = reflect(velocity, normal) }`).

**Alternativas consideradas:**
- Lógica de colisão dentro do `onUpdate` do nó (caminho 1 do explore): rápido pra escrever pra Pong, péssimo pra reuso e escala.
- Spatial hashing / quadtree: prematuro. Documentado como ponto de evolução.

### Decisão 4: Renderer e Input como SPI; Compose é o primeiro backend, não o modelo

`Renderer` e `Input` vivem em `:engine` como interfaces. `:engine-compose` implementa ambas usando `DrawScope` e eventos Compose. `Node.onRender(renderer)` recebe a interface; nunca toca `androidx.compose.*`.

**Por quê:**
- Reconcilia "Opção A é mais fácil, Opção C é mais correta" do explore mode: a fronteira fica desde o dia 1, e migrar pra Skiko/LWJGL depois é trocar de backend, não reescrever jogos.
- Mantém `:engine` testável sem precisar levantar Compose.
- Disciplina arquitetural barata (~10 métodos no `Renderer`).

**Alternativas consideradas:**
- Engine acoplada em Compose (Opção A pura): tira o custo da indireção, mas amarra a engine numa tecnologia específica permanentemente. Rejeitado.
- Engine puramente Compose-declarativa (Opção B): experimental e contra-intuitivo pra game loops. Rejeitado já no explore mode.

### Decisão 5: Game loop dirigido por `withFrameNanos` no `:engine-compose`

`GameSurface` composable chama `withFrameNanos` em loop dentro de um `LaunchedEffect`. Cada tick: calcula `dt`, propaga `onUpdate` pela árvore, roda `PhysicsSystem.step`, redesenha via `DrawScope`. `GameLoop` no `:engine` é a coordenação pura (dado um `dt`, atualizar e renderizar); quem fornece o pulso é o runtime.

**Por quê:**
- `withFrameNanos` já sincroniza com o vsync do Compose, evita criar thread separada e disputa com a render thread.
- Separa "tem um pulso" (runtime) de "o que acontece em cada pulso" (engine) — facilita backends futuros (LWJGL com `glfwSwapBuffers`, por exemplo).

**Alternativas consideradas:**
- Thread dedicada com `Thread.sleep(16)`: simples, mas dessincroniza com Compose e introduz tearing.
- Fixed timestep com acumulador (estilo Glenn Fiedler): mais robusto pra física determinística, mas overkill agora. Documentado como evolução.

### Decisão 6: Estrutura de módulos `:engine` / `:engine-compose` / `:games:<jogo>`, sem `:desktopApp`

Cada jogo é um módulo executável (`application` plugin) com seu próprio `main()`. Não há launcher unificado nesta fase.

**Por quê:**
- Mantém boundaries arquiteturais explícitas (a regra "`:engine` não importa Compose" vira `dependencies { }` no Gradle, verificável mecanicamente).
- Elimina o `:desktopApp` boilerplate do template KMP que existiria só pra hospedar uma referência ao Pong.
- Cada jogo de exemplo tem entrada óbvia: `./gradlew :games:pong:run`.

**Trade-off aceito:** múltiplos `main()` à medida que jogos forem adicionados. Quando o editor entrar, ele vira o "launcher" e essa duplicação some.

### Decisão 7: DX como cidadã de primeira classe desde o início

FPS overlay, log estruturado simples e visualização de colliders são parte do escopo da change, não "nice-to-have depois". Todos togglable via flag de debug; nenhum impacto em release-mode.

**Por quê:**
- Engine sem DX é engine que você não consegue debugar — e o propósito da change é aprender, o que exige observabilidade.
- Custo é pequeno (cada feature ~50-100 linhas) e o retorno em debug das próximas changes é alto.

## Risks / Trade-offs

- **[Risco] Decisão por herança trava combinação dinâmica de comportamentos** → Mitigação: o roadmap (puzzle games + editor) não exige composição combinatória. Se aparecer pressão por composição na change do editor, revisitamos com dados reais, não especulação.

- **[Risco] Broad phase O(N²) limita densidade de colliders** → Mitigação: nenhum jogo do roadmap chega a dezenas de colliders. Documentado em `engine-core` spec e em `CLAUDE.md` como ponto de evolução.

- **[Risco] Falta de sinais força acoplamento explícito entre nós (referência direta)** → Mitigação: explícito >>> implícito num escopo pequeno. A change `event-driven-games` é o gatilho natural para introduzir sinais quando o atrito for real.

- **[Risco] Acoplamento acidental com Compose dentro de `:engine`** → Mitigação: invariante "`:engine` não depende de `androidx.compose.*`" expressa no `build.gradle.kts` (nada de dependência Compose no módulo) e reafirmada em `CLAUDE.md`. Code review verifica. Em changes futuras, considerar Konsist ou regra de ArchUnit equivalente.

- **[Risco] `withFrameNanos` ligado ao ciclo Compose pode mascarar problemas de timing** → Mitigação: `dt` é exposto como `Float` em segundos e usado uniformemente; quando aparecer demanda por física determinística, migramos para fixed timestep dentro do mesmo `GameLoop`, sem mudar API pública dos nós.

- **[Trade-off] Múltiplos módulos `:games:<jogo>` aumentam ruído em `settings.gradle.kts`** → Aceito. Cada jogo se autocontém, é a unidade natural de exemplo.

- **[Trade-off] DX togglable via flag (não removida em release)** → Aceito. Para a fase code-only, "release" não é uma preocupação real; o custo de carregar 200 linhas extras é irrelevante.

## Migration Plan

Não há produção, usuários ou dados a migrar. Migração é puramente estrutural do template:

1. Criar módulos novos vazios (`:engine`, `:engine-compose`, `:games:pong`) e atualizar `settings.gradle.kts`.
2. Implementar engine, runtime e Pong (sequência detalhada em `tasks.md`).
3. Remover `:desktopApp` e `:shared` apenas depois que `:games:pong:run` estiver funcional, para garantir que nada do template ainda esteja sendo usado como referência.
4. Atualizar `README.md` para refletir a nova forma de rodar (`./gradlew :games:pong:run`).

Não há rollback formal — se algo der errado, `git revert` da change.

## Open Questions

- **Nome do pacote raiz da engine**: manter `com.neoutils.engine` (já usado no template) ou renomear (ex.: `com.neoutils.gengine`) para refletir que o módulo é a engine pura, separada da raiz do projeto. Decisão atual: manter `com.neoutils.engine` para reduzir churn; revisitar se causar confusão.
- **Verificação automática da invariante "`:engine` sem Compose"**: avaliar adoção de Konsist nesta change ou postergar para uma change menor depois que ela "doer" pela primeira vez. Decisão atual: postergar, confiar em `dependencies { }` + code review.
- **Granularidade do log estruturado**: níveis fixos (`Debug`/`Info`/`Warn`/`Error`) bastam, ou já entra com `tag` por subsistema? Decisão atual: `tag` por subsistema (custo trivial, ganho imediato em filtragem).
- **Render de texto sem fonte customizada**: aceitar fonte default do backend para HUD do Pong. Asset pipeline de fontes fica para change futura.