## 1. Helper de enumeração @Inspect

- [ ] 1.1 Criar `data class InspectEntry(displayName: String, value: Any?)` e `fun inspectProperties(node: Node): List<InspectEntry>` em `com.neoutils.engine.serialization`, reusando o padrão `memberProperties` + `findAnnotation<Inspect>()` + getter; `displayName` = annotation se não-vazio, senão nome da property.
- [ ] 1.2 Testes: enumera as `@Inspect` com valores correntes (ex.: `position` setado); exclui campos `@Transient`.

## 2. SceneInspectorWidget — lista da árvore

- [ ] 2.1 Criar `SceneInspectorWidget : ScreenDebugWidget` (`title = "Inspector"`, `enabled = false`).
- [ ] 2.2 `drawDebug`: DFS de `tree.root` montando linhas indentadas por profundidade (nome + `::class.simpleName`), altura de linha fixa, ancorada num canto, clipada à altura da surface.
- [ ] 2.3 Truncamento explícito: desenhar `"+N more"` quando a árvore não cabe (sem corte silencioso).
- [ ] 2.4 Testes: lista reflete add/remove em runtime; indicador de overflow desenhado; desabilitado → zero draws.

## 3. Seleção por clique + painel de propriedades

- [ ] 3.1 `onProcess`: ler `tree.input` (clique + posição), mapear para a linha sob o cursor com o mesmo layout do draw, selecionar o `Node` por identidade de instância.
- [ ] 3.2 Limpar a seleção quando o node selecionado não está mais `isLive`/na árvore.
- [ ] 3.3 Painel do selecionado: tipo, `name`, transform world se `Node2D` (position/rotation/scale via `world()`), e as `@Inspect` via `inspectProperties` (displayName = valor.toString()).
- [ ] 3.4 Testes: clique seleciona o node sob o cursor e lista suas `@Inspect`; Node2D mostra transform world; seleção limpa ao desanexar.

## 4. Registro como built-in

- [ ] 4.1 Campo de conveniência no `DebugRegistry`; registrar no container screen-space via `bindLayer` (default `enabled = false`).
- [ ] 4.2 Teste: built-in não-nulo após `start()`, `parent` = container screen-space, presente em `widgets` e como row no HUD.

## 5. Fechamento

- [ ] 5.1 Rodar a suíte do `:engine`; garantir verde.
- [ ] 5.2 `openspec validate debug-scene-inspector --strict` e revisar coerência specs↔implementação.
