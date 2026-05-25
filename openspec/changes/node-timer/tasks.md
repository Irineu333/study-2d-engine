## 1. Engine: Timer node

- [x] 1.1 Criar `com.neoutils.engine.scene.TimerMode` (enum `{PHYSICS, IDLE}`) anotado `@Serializable`.
- [x] 1.2 Criar `com.neoutils.engine.scene.Timer : Node` (não `Node2D`), anotado `@Serializable`, com no-args constructor público; declarar campos `@Inspect`: `waitTime: Float = 1f`, `autostart: Boolean = false`, `oneShot: Boolean = false`, `processCallback: TimerMode = TimerMode.PHYSICS`.
- [x] 1.3 Adicionar campo `@Transient var timeLeft: Float = 0f` ao `Timer`.
- [x] 1.4 Adicionar `val timeout: Signal<Unit> = Signal()` ao `Timer`.
- [x] 1.5 Implementar `val isStopped: Boolean` derivado (`timeLeft <= 0f`).
- [x] 1.6 Implementar `fun start(override: Float? = null)`: validar `override > 0` (throw `IllegalArgumentException` com valor); setar `timeLeft` para `override ?: waitTime`.
- [x] 1.7 Implementar `fun stop()`: zera `timeLeft`.
- [x] 1.8 Sobrescrever `onEnter()`: se `autostart`, chamar `start()`; caso contrário, deixar parado.
- [x] 1.9 Sobrescrever `onPhysicsProcess(dt: Float)`: se `processCallback == PHYSICS` e `timeLeft > 0f`, decrementar e emitir `timeout` quando cruzar zero (preservando overshoot ou parando se `oneShot`).
- [x] 1.10 Sobrescrever `onProcess(dt: Float)`: lógica simétrica para `processCallback == IDLE`.
- [x] 1.11 Sobrescrever `onExit()`: chamar `stop()` internamente.

## 2. Engine: NodeRegistry

- [x] 2.1 Registrar `Timer` no `NodeRegistry` sob a tag `engine.Timer` (no mesmo arquivo onde `Camera2D`/`Label`/etc. são registrados).
- [x] 2.2 Verificar que `NodeRegistry.create("engine.Timer")` retorna um `Timer` com defaults.

## 3. Engine: testes unitários do Timer

- [x] 3.1 Teste: defaults após construção via `NodeRegistry`.
- [x] 3.2 Teste: `timeLeft` é `@Transient` (`SceneLoader` não emite a chave no JSON).
- [x] 3.3 Teste: `autostart=true` agenda primeiro emit após `waitTime` (não imediato).
- [x] 3.4 Teste: `autostart=false` mantém `isStopped=true` após `onEnter`.
- [x] 3.5 Teste: `oneShot=true` emite uma vez e para; `isStopped=true` depois.
- [x] 3.6 Teste: `oneShot=false` emite `~10` vezes em 1s a `waitTime=0.1f` (tolerância ±1).
- [x] 3.7 Teste: `PHYSICS` mode não avança em `onProcess`; `IDLE` mode não avança em `onPhysicsProcess`.
- [x] 3.8 Teste: mudar `processCallback` em runtime altera o callback ativo no próximo tick.
- [x] 3.9 Teste: `start(0f)` e `start(-1f)` lançam `IllegalArgumentException` com a mensagem incluindo o valor.
- [x] 3.10 Teste: `start(override)` aplica `override` à primeira emissão, segunda usa `waitTime`.
- [x] 3.11 Teste: `stop()` zera `timeLeft` e marca `isStopped`; sem novas emissões.
- [x] 3.12 Teste: re-attach com `autostart=true` reinicia limpo (`timeLeft = waitTime`).
- [x] 3.13 Teste: `removeChild` chama `stop()` automaticamente.

## 4. Engine-bundle: coerção de enum

- [x] 4.1 Atualizar `PropCoercion` em `:engine-bundle` para detectar quando o target é `enum class` (via `KClass.java.isEnum` ou Kotlin reflection) e usar `valueOf(stringValue)`.
- [x] 4.2 Em falha de `valueOf`, capturar e re-lançar com mensagem incluindo: nome da propriedade, valor JSON ofensivo, nome do enum class, e lista de entry names válidos.
- [x] 4.3 Verificar (ou ajustar) `SceneLoader` para passar o nome do node ao erro (para a mensagem final dizer "node MyTimer, propriedade mode, valor 'physics' inválido").
- [x] 4.4 Teste em `:engine-bundle`: coerção de string `"IDLE"` para `TimerMode.IDLE`.
- [x] 4.5 Teste em `:engine-bundle`: string `"physics"` (minúsculo) falha com mensagem contendo node name, prop name, valor, classe enum, e entries válidos.
- [x] 4.6 Teste em `:engine-bundle`: coerção é genérica — declarar um enum dummy local e provar que funciona sem hardcode.

## 5. Engine-bundle: round-trip via SceneLoader

- [x] 5.1 Teste: carregar `scene.json` contendo `{ "type": "engine.Timer", "name": "MoveTimer", "properties": { "waitTime": 0.125, "autostart": true, "oneShot": false, "processCallback": "PHYSICS" } }` produz `Timer` com valores corretos.
- [x] 5.2 Teste: round-trip (serialize → deserialize) preserva os 4 campos `@Inspect`.
- [x] 5.3 Teste: `timeLeft` populado em runtime não aparece no JSON serializado.

## 6. Engine-bundle-python: signal Kotlin → Python

- [x] 6.1 Identificar local atual onde wrappers Python são construídos para Nodes Kotlin (provavelmente `:engine-bundle-python` host code que cria os bindings).
- [x] 6.2 Implementar lookup reflection-based: ao construir wrapper Python para um Node Kotlin, varrer suas `val`s públicas de tipo `Signal<*>` e expor cada uma como atributo do wrapper.
- [x] 6.3 Construir proxy Python sobre `Signal<*>` com métodos `connect(callable)` e `disconnect(callable)` que roteiam para o `Signal` Kotlin.
- [x] 6.4 Adaptar o `connect`: quando o `Signal` é `Signal<Unit>`, o handler Python é chamado com zero args; para `Signal<T>` (T != Unit), com o valor emitido como único arg.
- [x] 6.5 Garantir que exceptions Python em handlers propagam (não swallow) através do `Signal.emit`.
- [x] 6.6 Teste integrado (em `:engine-bundle-python`): cena programática com um `Timer` autostart, script Python conecta um handler, roda N ticks, verifica handler foi chamado.
- [x] 6.7 Teste integrado: `disconnect` realmente remove o handler.
- [x] 6.8 Teste integrado: handler que lança `ValueError` propaga até o caller (não silenciado).

## 7. Engine-bundle-python: stubs .pyi

- [x] 7.1 Localizar `resources/stubs/engine/` e identificar estilo dos stubs existentes (provavelmente um arquivo por tipo ou um agregado).
- [x] 7.2 Adicionar `Timer` stub com campos `wait_time`, `autostart`, `one_shot`, `process_callback`, `time_left`, `is_stopped`, `timeout: Signal[None]`, métodos `start(override: Optional[float] = None) -> None`, `stop() -> None`.
- [x] 7.3 Adicionar `TimerMode` stub (enum-like com `PHYSICS` e `IDLE`).
- [x] 7.4 Validar que `Signal[None]` (ou equivalente) já existe nos stubs; se não, criar tipo `Signal[T]` minimal.
- [x] 7.5 Verificar nomenclatura snake_case ↔ camelCase: o bridge **não** converte automaticamente (Polyglot/EmulateJython mantém camelCase). Pong scripts já usam camelCase (`worldPosition`, `addChild`, `upKey`); stubs antigos com snake_case (`add_child`, `aspect_mode`) são aspiracionais e não refletem o runtime. Timer stubs adotam camelCase para casar com o runtime; uma ponte snake_case→camelCase global fica para change futura.

## 8. Demos: cena de Timer

- [x] 8.1 Adicionar tecla `6` em `:games:demos` para abrir cena "Timer".
- [x] 8.2 Cena contém dois `Timer`s — um `PHYSICS` (`waitTime=0.5f`), outro `IDLE` (`waitTime=0.5f`) — cada um conectado a um `Label` que alterna o texto entre "TICK" / "TOCK" a cada `timeout`.
- [x] 8.3 Implementação 100% em script Python (`scripts/timer_demo.py`) consumindo `Timer.timeout` para validar o fluxo Kotlin signal → Python handler de ponta a ponta.
- [x] 8.4 Adicionar entrada no `CLAUDE.md` (seção "Para rodar Demos") descrevendo a tecla `6`.

## 9. Documentação

- [x] 9.1 Atualizar `openspec/specs/engine-core/spec.md` (na archive) — esta change ADICIONA "Node base class supports non-visual logical nodes"; verificar como o archive merge vai resolver. (Será aplicado no `/opsx:archive`; o delta spec já existe em `specs/engine-core/spec.md` desta change.)
- [x] 9.2 Atualizar comentário do KDoc de `Timer` documentando: drift em IDLE mode, semântica de `start(override)`, re-amostragem de `processCallback` a cada tick.
- [x] 9.3 Atualizar `ROADMAP.md`: `node-timer` sai de "Planned" implícito (não existia explícito) — adicionar em "Active" durante a vida da change. (Já estava listado em Active.)

## 10. Verificação

- [x] 10.1 Rodar `./gradlew :engine:test :engine-bundle:test :engine-bundle-python:test :games:demos:run` e confirmar que tudo passa. (Testes passam; `:games:demos:run` lança UI interativa — substituído por `:games:demos:assemble` que valida o classpath completo.)
- [x] 10.2 Rodar `openspec verify node-timer` e confirmar que não há gaps. (Usado `openspec validate node-timer` — CLI atual; "Change 'node-timer' is valid".)
- [ ] 10.3 Confirmar manualmente em `:games:demos` (tecla `6`) que ambos `PHYSICS` e `IDLE` Timers piscam labels — `IDLE` continua piscando se F1 desligar FPS overlay (Process roda), `PHYSICS` deve continuar piscando independente. **Pendente: requer execução manual da UI; pedi para você rodar `./gradlew :games:demos:run` e pressionar 6.**
