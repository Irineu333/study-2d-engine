## Context

Pong é a prova de fogo da engine e foi totalmente migrado para `.nengine.kts` na change `add-scripting`. Hoje o `:games:pong/src/main/resources/scripts/` contém oito scripts. Dois deles — `paddle-collider.nengine.kts` e `walls.nengine.kts` — declaram apenas a herança vazia:

```kotlin
class PaddleCollider : BoxCollider()
class Wall : BoxCollider()
```

Eles existem porque o `ball.nengine.kts` faz dispatch de colisão por nome de classe:

```kotlin
val otherClassName = other::class.java.simpleName
when (otherClassName) {
    "Goal"           -> /* gol */
    "Wall"           -> velocity = velocity.copy(y = -velocity.y)
    "PaddleCollider" -> /* ricochete de paddle */
}
```

`BoxCollider` já é tipo de engine registrado em `NodeRegistry.registerEngineTypes()` e pode ser referenciado no JSON pelo seu FQN. O que falta para apagar os dois scripts é dar ao `Ball` um critério estrutural — não nominal — para distinguir os três casos.

## Goals / Non-Goals

**Goals:**

- Remover `paddle-collider.nengine.kts` e `walls.nengine.kts` sem mudar nada visível ao jogador.
- Manter o `Ball` capaz de distinguir: gol, paddle, parede.
- Documentar e expor a ideia de que tipos sem comportamento devem usar a engine — subclasse vazia como tag é antipattern nessa codebase.
- Atualizar o spec `pong-sample` para refletir a nova composição.

**Non-Goals:**

- Introduzir um sistema de tags / categorias / layers em `Collider` (poderia ser feito depois, fora desta change).
- Refatorar a dispatch via reflexão de `Goal` em `ball.nengine.kts` (segue funcional; mexer nela não é objetivo aqui — escopo focado).
- Mudar a SPI de física, broad phase, ou a forma como `PhysicsSystem.step` enumera colliders.
- Mexer em `:games:tictactoe`, `:games:demos`, `:engine-compose` ou qualquer outro módulo.

## Decisions

### Decision 1: Dispatch por estrutura, não por nome de classe

`ball.onCollide(other: Collider)` passa a usar três checks em ordem:

1. `other is Goal` ⇒ gol (mesma lógica de score/reset que hoje, inclusive a reflexão para ler `side`).
2. `other.parent is Paddle` ⇒ ricochete de paddle.
3. caso contrário (qualquer outro `BoxCollider`) ⇒ ricochete de parede (inverte Y).

**Por quê:** a ordem reflete a estrutura real da cena.

- O `Paddle` cria seu próprio colisor filho via `addChild(BoxCollider())`, então `parent is Paddle` é um invariante estável da composição.
- `Goal` é uma classe script com `@Inspect var side`, então usar `is Goal` é direto e sobrevive ao refator.
- Tudo o que sobrar — `topWall` e `bottomWall` — entra no `else`. Como o spec proíbe `object : BoxCollider` no código Pong e a cena não tem outros colisores genéricos, o `else` é exatamente "parede".

**Alternativa considerada:** dispatch por `node.name` (`"topWall"`, `"leftGoal"`). Rejeitada — nomes são identidade humana, não contrato. Quebrar a regra "comportamento depende de nome" abre porta para erros silenciosos quando alguém renomear um nó.

**Alternativa considerada:** adicionar `Collider.tag: String` na engine para classificação genérica. Rejeitada por escopo — resolve um problema mais geral e seria mais coerente como sua própria change OpenSpec. Para Pong, o critério estrutural já basta.

### Decision 2: `Paddle` constrói um `BoxCollider` genérico, não uma subclasse

O `onEnter` do `Paddle` muda de:

```kotlin
val c = PaddleCollider().apply { size = this@Paddle.size }
```

para:

```kotlin
val c = BoxCollider().apply { size = this@Paddle.size }
```

E o campo `@Transient private var collider: PaddleCollider?` passa a ser `BoxCollider?`. Como `paddle.nengine.kts` é compilado depois de `goal.nengine.kts` (a manifest é preservada para os scripts que sobram), o tipo `BoxCollider` continua disponível por import implícito (`com.neoutils.engine.physics.*` está no contrato de imports do `KotlinScriptingHost`).

### Decision 3: Tipos engine podem aparecer por FQN no `pong.scene.json`

`topWall` e `bottomWall` passam a ser:

```json
{
  "type": "com.neoutils.engine.physics.BoxCollider",
  "name": "topWall",
  "properties": { "transform": { … }, "size": { "x": 10.0, "y": 10.0 } },
  "children": []
}
```

`SceneLoader.entryToNode` já trata FQNs via `NodeRegistry.create` quando `type` não termina em `.kts`. Não precisa de nenhuma mudança em `:engine`.

O spec `pong-sample` hoje exige que **todos** os tipos Pong-owned no JSON sejam `.kts`. Como `topWall`/`bottomWall` deixam de ser "Pong-owned" e passam a usar diretamente um tipo de engine, isso é coerente — o spec já permite "engine-provided types ... MAY continue to appear by FQN".

### Decision 4: Manifest do `Main.kt` perde duas entradas

A manifest atual lista, em ordem de compilação: `paddle-collider, walls, goal, score, center-line, ball, paddle, pong-scene`. Após esta change: `goal, score, center-line, ball, paddle, pong-scene`. Nenhuma reordenação necessária — só remoção.

### Decision 5: Spec `pong-sample` recebe um delta amplo

Os seguintes requirements do spec mencionam `PaddleCollider` / `Wall` por nome e precisam ser editados:

- "Pong scene composition" — frase "Each `Paddle` MUST carry a child `PaddleCollider` (which is a `BoxCollider`)" vira "Each `Paddle` MUST carry a child `BoxCollider`".
- "Pong validates the engine surface end to end" — Scenario "Pong exposes @Inspect properties" cita `Wall, PaddleCollider`; precisa retirar esses dois nomes.
- "Pong ships gameplay nodes as scripts under resources" — lista de arquivos do diretório `scripts/` perde `paddle-collider.nengine.kts` e `walls.nengine.kts`.
- "Pong manifest declares script compilation order" — ordem da manifest é reescrita para `goal, score, center-line, ball, paddle, pong-scene`. Cenário "Manifest places dependencies before dependents" perde a frase sobre `paddle-collider.nengine.kts`.
- "pong.scene.json references scripts by path" — sem mudança no texto principal; o cenário "All Pong-owned types in pong.scene.json are script paths" continua válido porque `BoxCollider` deixa de ser "Pong-owned".

## Risks / Trade-offs

- **[Risco] A regra "qualquer `BoxCollider` que não seja `Goal` nem filho de `Paddle` é parede" é implícita.** Se alguém adicionar um terceiro colisor genérico à cena no futuro (ex.: um power-up), o `Ball` vai tratá-lo como parede sem feedback claro. → **Mitigação:** documentar a regra no `onCollide` do `Ball` e, se a cena crescer, abrir uma change OpenSpec para introduzir um sistema de tags em `Collider`.

- **[Risco] Quebrar a regra "Pong tem script para todo gameplay node".** Hoje o spec `pong-sample` afirma "Every gameplay node type referenced from `pong.scene.json` resolves through `ScriptHost`". → **Mitigação:** o texto já tem a ressalva que tipos da engine podem aparecer por FQN. O delta de spec deixa essa ressalva explícita no cenário e remove a obrigação no caso de wall/paddle-collider.

- **[Risco] Cache de scripts.** O `KotlinScriptingHost` mantém cache SHA-256 em `build/scripting-cache/`. Apagar scripts não invalida nada — só sobram arquivos órfãos no cache. → **Mitigação:** opcional, mas saudável: limpar `build/scripting-cache/` na primeira run pós-merge. Não é bloqueante.

- **[Trade-off] Não introduzir `Collider.tag`.** Resolve menos do que poderia. Aceito porque o escopo da change é "remover scripts redundantes em Pong", não "criar primitivas novas de classificação na engine".
