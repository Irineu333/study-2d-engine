## REMOVED Requirements

### Requirement: Compose-based Renderer implementation

**Reason**: O módulo `:engine-compose` é deletado nesta change. Compose Multiplatform Desktop renderiza por baixo via Skia (= mesmo backend que `:engine-skiko`), então o "segundo backend" nunca foi arquiteturalmente distinto. Custo de manutenção (Compose Compiler atrelado à versão de Kotlin, plugin extra no build) deixou de compensar.

**Migration**: Use `SkikoRenderer` em `:engine-skiko` para todas as renderizações. Não há substituto necessário — a API `Renderer` continua igual; apenas a implementação Compose-based desaparece.

### Requirement: Compose-based Input implementation

**Reason**: Removido junto com `:engine-compose` (mesma justificativa acima).

**Migration**: Use `SkikoInput` em `:engine-skiko`. A SPI `Input` permanece inalterada.

### Requirement: GameSurface composable drives the game loop

**Reason**: Removido junto com `:engine-compose`. `GameSurface` era o composable que integrava o `GameLoop` com `withFrameNanos`; sem Compose, não há composable para hospedar.

**Migration**: Use `SkikoHost` em `:engine-skiko`, que monta `SkiaLayer` + `JFrame` e dirige o `GameLoop` via `Window.AnimationTimer` equivalente. `:games:tictactoe/Main.kt` migra de `ComposeHost()` para `SkikoHost()`.

### Requirement: Compose-runtime module is the only Compose-aware engine boundary

**Reason**: Não existe mais um módulo Compose-aware no projeto após esta change. O invariante de "boundary único para Compose" perde objeto.

**Migration**: A regra equivalente para Skiko vive em `skiko-runtime/spec.md` (`Skiko-runtime module is a Compose-free engine boundary`, agora reformulado para refletir Skiko como único backend de render ativo).

### Requirement: GameSurface applies collider debug overlay

**Reason**: Removido junto com `:engine-compose`. A responsabilidade do collider overlay vive no `GameHost` ativo via `Debug.colliderVisualization` + `renderDebugOverlay` (capability `dx-tooling`); `SkikoHost` já cumpre esse papel sem mudança.

**Migration**: Nenhuma — o overlay continua disponível via `F2` em qualquer `GameHost` (Skiko hoje), conforme `engine-core/Requirement: Toggle keys flip debug flags through the host`.

### Requirement: ComposeHost implements GameHost over GameSurface

**Reason**: Removido junto com `:engine-compose`.

**Migration**: Use `SkikoHost()` em `:engine-skiko` em vez de `ComposeHost()`. A interface `GameHost` em `:engine` é idêntica para os dois.
