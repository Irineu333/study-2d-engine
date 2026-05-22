## Why

Os scripts `paddle-collider.nengine.kts` (`class PaddleCollider : BoxCollider()`) e `walls.nengine.kts` (`class Wall : BoxCollider()`) só existem para servir como **tags de tipo** em `ball.nengine.kts`, que faz dispatch por `other::class.java.simpleName`. Como `BoxCollider` já é um tipo registrado pela engine no `NodeRegistry`, esses dois scripts viraram cerimônia: cada nó pode ser declarado diretamente como `com.neoutils.engine.physics.BoxCollider` no `pong.scene.json`, desde que o `Ball` aprenda a se localizar na cena sem depender do nome de classe.

A simplificação reduz a manifest de scripts, deixa o `pong.scene.json` mais direto, ensina o leitor que tipos sem comportamento devem usar a engine, e desencoraja o padrão "subclasse vazia como tag" que tende a se espalhar sem necessidade.

## What Changes

- **BREAKING** Remoção dos arquivos `paddle-collider.nengine.kts` e `walls.nengine.kts` de `:games:pong/src/main/resources/scripts/`.
- **BREAKING** Remoção das entradas correspondentes da manifest do `KotlinScriptingHost` em `Main.kt`.
- Os nós `topWall`, `bottomWall` e o `PaddleCollider` filho de cada `Paddle` passam a usar diretamente o tipo da engine `com.neoutils.engine.physics.BoxCollider`:
  - No `pong.scene.json`, `topWall` e `bottomWall` mudam de `"type": "scripts/walls.nengine.kts"` para `"type": "com.neoutils.engine.physics.BoxCollider"`.
  - Em `paddle.nengine.kts`, o filho colisor passa a ser criado como `BoxCollider()` em vez de `PaddleCollider()`.
- O dispatch de colisão em `ball.nengine.kts` deixa de comparar `other::class.java.simpleName` com strings literais e passa a usar a estrutura da cena:
  - `other is Goal` ⇒ evento de gol (lógica atual de score/reset).
  - `other.parent is Paddle` ⇒ ricochete de paddle.
  - Demais colisões com `BoxCollider` ⇒ ricochete de parede (inverte Y).
- O spec `pong-sample` é atualizado para refletir a nova composição (paddle colide com um `BoxCollider` filho qualquer; manifest e diretório de scripts ficam sem `paddle-collider` e `walls`; tipos de engine podem aparecer por FQN no JSON quando o nó não tem comportamento próprio).

## Capabilities

### New Capabilities

_(nenhuma)_

### Modified Capabilities

- `pong-sample`: composição da cena permite usar `BoxCollider` da engine diretamente; manifest e diretório de scripts deixam de exigir `paddle-collider.nengine.kts` e `walls.nengine.kts`; ball.onCollide passa a despachar por estrutura (instância de `Goal`, parent é `Paddle`, ou colisor genérico) em vez de comparar nomes de classe.

## Impact

- Código:
  - `games/pong/src/main/resources/scripts/paddle-collider.nengine.kts` — removido.
  - `games/pong/src/main/resources/scripts/walls.nengine.kts` — removido.
  - `games/pong/src/main/resources/scripts/paddle.nengine.kts` — substitui `PaddleCollider()` por `BoxCollider()`; remove campo tipado como `PaddleCollider`.
  - `games/pong/src/main/resources/scripts/ball.nengine.kts` — reescreve o `when` de dispatch para usar `is Goal` / `parent is Paddle` / `else BoxCollider`.
  - `games/pong/src/main/resources/pong.scene.json` — atualiza `type` de `topWall`, `bottomWall`.
  - `games/pong/src/main/kotlin/.../Main.kt` — remove `paddle-collider.nengine.kts` e `walls.nengine.kts` da manifest do `KotlinScriptingHost`.
- Specs:
  - `openspec/specs/pong-sample/spec.md` — requisitos de composição, scripts shippados, manifest e referências em `pong.scene.json` deixam de citar os dois nomes.
- Sem mudanças na API pública de `:engine`, `:engine-skiko`, `:engine-scripting`.
- Sem impacto em `:games:tictactoe`, `:games:demos`, ou nos backends.
- Riscos: o spec `pong-sample` é central; a atualização precisa cobrir todos os cenários que mencionam `Wall` / `PaddleCollider` por nome para que `/opsx:verify` continue passando.
