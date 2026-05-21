## 1. Refatorar dispatch de colisão do Ball

- [x] 1.1 Em `games/pong/src/main/resources/scripts/ball.nengine.kts`, substituir o `when (otherClassName)` por três checks estruturais: `other is Goal` (gol), `other.parent is Paddle` (paddle), `else` para `BoxCollider` (parede). Manter a leitura reflexiva de `getSide()` no ramo do gol — não é objetivo desta change. Remover a variável `otherClassName`.
- [x] 1.2 Confirmar que a ordem dos checks faz sentido: gol primeiro (caso especial), depois paddle (estrutural via parent), depois parede (fallback). Adicionar um único comentário curto explicando "o fall-through corresponde às paredes" — só o "porquê" não-óbvio.

## 2. Trocar PaddleCollider por BoxCollider no Paddle

- [x] 2.1 Em `paddle.nengine.kts`, mudar `@Transient private var collider: PaddleCollider?` para `BoxCollider?`.
- [x] 2.2 No `onEnter` do `Paddle`, mudar `PaddleCollider()` para `BoxCollider()`. Manter o `apply { size = this@Paddle.size }`.
- [x] 2.3 Verificar que `com.neoutils.engine.physics.BoxCollider` resolve via import implícito do `KotlinScriptingHost` (pacote `physics.*` já está nos imports padrão).

## 3. Remover scripts redundantes

- [x] 3.1 Apagar `games/pong/src/main/resources/scripts/paddle-collider.nengine.kts`.
- [x] 3.2 Apagar `games/pong/src/main/resources/scripts/walls.nengine.kts`.

## 4. Atualizar o pong.scene.json

- [x] 4.1 Em `games/pong/src/main/resources/pong.scene.json`, mudar `"type"` de `topWall` de `scripts/walls.nengine.kts` para `com.neoutils.engine.physics.BoxCollider`. Manter `name`, `properties` e `children` inalterados.
- [x] 4.2 Repetir para `bottomWall`.

## 5. Atualizar o Main.kt do Pong

- [x] 5.1 Em `games/pong/src/main/kotlin/.../Main.kt`, remover `scripts/paddle-collider.nengine.kts` da lista da manifest passada ao `KotlinScriptingHost`.
- [x] 5.2 Remover `scripts/walls.nengine.kts` da mesma manifest.
- [x] 5.3 Confirmar que a manifest restante segue a ordem `goal, score, center-line, ball, paddle, pong-scene` (leaves → mid → root).

## 6. Validar comportamento

- [ ] 6.1 Limpar caches de script (`rm -rf games/pong/build/scripting-cache` e `games/pong/build/resources/main/scripts/paddle-collider.nengine.kts games/pong/build/resources/main/scripts/walls.nengine.kts`) para evitar resíduos.
- [ ] 6.2 Rodar `./gradlew :games:pong:run` e jogar uma partida: paddles se movem, bola ricocheteia em paredes e paddles, gol incrementa o placar do lado oposto e reposiciona a bola.
- [ ] 6.3 Rodar `./gradlew :games:pong:test` (e `:engine:test`, `:engine-scripting:test`) e confirmar verde.

## 7. Sincronizar specs e change

- [ ] 7.1 Rodar `openspec validate drop-pong-tag-only-scripts --strict` e corrigir o que aparecer.
- [ ] 7.2 Rodar `openspec status --change drop-pong-tag-only-scripts` e conferir que tudo está `done` antes de chamar `/opsx:verify`.
