## 1. Reescrever o sample

- [ ] 1.1 Remover o arquivo `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/CenteredLabel.kt`
- [ ] 1.2 Reescrever `Main.kt`: instanciar `Label` direto (`text = "Hello, world!"`, `fontSize = 32f`, `color = Color.WHITE`), aplicar `applyPreset(LayoutPreset.FULL_RECT)`, adicionar como único filho do `CanvasLayer` root e rodar via `SkikoHost().run(SceneTree(root = hud), GameConfig(...))`
- [ ] 1.3 Ajustar imports em `Main.kt` (`Label`, `LayoutPreset` de `com.neoutils.engine.scene`; remover import de `CenteredLabel`)

## 2. Verificação

- [ ] 2.1 `./gradlew :games:hello-world:run` abre a janela com `"Hello, world!"` centralizado em 800×600
- [ ] 2.2 Redimensionar a janela mantém o texto centralizado e escalando (sem salto)
- [ ] 2.3 Confirmar que o módulo não tem mais nenhuma subclasse de `Label`, nenhum `onDraw`, nenhum `measureText` e nenhuma leitura de `tree.size`/`tree.designSize`

## 3. Documentação

- [ ] 3.1 Revisar a linha do `:games:hello-world` em `CLAUDE.md` (a descrição "único Label centralizado" segue válida; garantir que nada mencione `CenteredLabel`)
