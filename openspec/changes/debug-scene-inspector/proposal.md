## Why

A ferramenta de debug mais usada do Godot é o **Remote scene tree** enquanto
o jogo roda: ver a hierarquia viva de Nodes e inspecionar os valores de um
node selecionado. A engine hoje não tem nada disso — você vê FPS, colliders
e momento, mas não consegue responder "esse node existe? qual o transform
dele agora? o script setou a property que eu esperava?". Como o scene graph
estilo Godot (invariante #1) é o coração da engine, tornar a árvore viva
observável é o gizmo de maior valor didático ainda faltando: mostra o scene
graph como **dado vivo**, não como código.

## What Changes

- **`SceneInspectorWidget`** (novo `ScreenDebugWidget`, built-in): quando
  habilitado, desenha a hierarquia viva a partir de `tree.root` como uma
  lista indentada (nome + tipo por node), e para o node **selecionado**
  lista o transform world (se `Node2D`) e as propriedades `@Inspect` com
  seus valores correntes.
- **Seleção por clique self-contained**: o widget lê o clique de mouse em
  `tree.input` no `onProcess` e mapeia para a linha sob o cursor usando o
  mesmo layout que desenha — sem instanciar `Button` por node (evita churn
  de scene graph). A seleção referencia a instância `Node`; some quando o
  node se desanexa.
- **Leitura de propriedades via reflexão `@Inspect`**: reusa o padrão já
  existente no `SceneLoader.extractInspectProperties` (`memberProperties` +
  `findAnnotation<Inspect>()` + getter), exposto como um helper público
  reutilizável. Apenas o node selecionado é reflexado por frame (barato).
- **Integração com a `DebugRegistry`**: built-in auto-inserido, campo de
  conveniência, row togglável no `DebugHud`.

## Capabilities

### New Capabilities

- `debug-scene-inspector`: o `SceneInspectorWidget` (lista da árvore viva,
  seleção por clique, painel de propriedades), o helper público de
  enumeração `@Inspect`, e o registro como built-in.

### Modified Capabilities
<!-- Nenhuma. O widget é mais um built-in (ADDED na nova capability, padrão
     das changes anteriores). O helper de reflexão é adicionado novo; o
     SceneLoader permanece intacto (convergência opcional anotada no design,
     fora do escopo para não perturbar a serialização testada). -->

## Impact

- **Código afetado:**
  - `:engine` `com.neoutils.engine.debug` — `SceneInspectorWidget`; campo no
    `DebugRegistry`.
  - `:engine` `com.neoutils.engine.serialization` — novo helper público de
    enumeração `@Inspect` (`name → value` para um node), reusando a lógica
    de reflexão hoje privada no `SceneLoader`.
- **Dependência:** nenhuma nova — `:engine` já declara `kotlin-reflect`
  (`api(libs.kotlin.reflect)`), usado pelo `SceneLoader`/`NodeRegistry`.
- **Custo em produção:** zero quando desabilitado. Habilitado: um walk O(N)
  da árvore por frame (só para layout) + reflexão de **um** node (o
  selecionado).
- **Não-objetivos (ver design):** edição de propriedades (read-only no MVP),
  watch de campos runtime `@Transient` como `linearVelocity` (fora do
  contrato `@Inspect`; candidato a um `@DebugWatch` futuro — velocidade já é
  coberta visualmente pelo `VelocityGizmoWidget`), expand/collapse e scroll
  da lista, e busca/filtro por nome.
- **Testes:** lista reflete a árvore viva (inclui nodes adicionados/removidos
  em runtime); clique seleciona o node sob o cursor; painel mostra as
  `@Inspect` do selecionado com valores correntes; seleção limpa ao
  desanexar; helper de reflexão enumera as `@Inspect` esperadas; built-in e
  row no HUD.
