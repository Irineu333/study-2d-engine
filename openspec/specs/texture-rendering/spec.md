# texture-rendering Specification

## Purpose

Define the engine's texture SPI (`TextureBackend`/`Texture`) — a server-style tree service for loading image assets — and the `Sprite2D` node that draws a decoded texture centered in local space. Keeps native graphics bindings out of `:engine`; backends (Skiko, LWJGL) provide concrete implementations.

## Requirements

### Requirement: TextureBackend SPI lives in :engine without native graphics dependencies

O módulo `:engine` SHALL declarar a SPI de textura no pacote `com.neoutils.engine.render`, composta por uma interface `TextureBackend` e um handle `Texture`. A interface `TextureBackend` SHALL expor exatamente duas operações:

- `fun load(path: String): Texture` — resolve e decodifica um asset de imagem num handle reutilizável.
- `fun dispose()` — libera todas as texturas nativas vivas.

`Texture` SHALL expor exatamente `val width: Int` e `val height: Int` (dimensões do bitmap em pixels) e **nenhum outro membro** — nenhum byte, pixel, handle nativo ou tipo de backend vaza por ela. `:engine` MUST NOT declarar — direta ou transitivamente — `org.jetbrains.skia.*`, `org.lwjgl.*` ou qualquer binding gráfico nativo; apenas as interfaces puras.

#### Scenario: Texture SPI is Kotlin-pure in :engine

- **WHEN** o módulo `:engine` é inspecionado
- **THEN** `com.neoutils.engine.render.TextureBackend` e `com.neoutils.engine.render.Texture` existem como interfaces
- **AND** nenhum arquivo de `:engine` importa `org.jetbrains.skia`, `org.lwjgl` ou outro binding gráfico nativo

#### Scenario: Texture handle exposes only dimensions

- **WHEN** a interface `Texture` é inspecionada
- **THEN** ela declara apenas `width: Int` e `height: Int` (sem getters de bytes, pixels ou handle nativo)

### Requirement: SceneTree exposes a nullable texture backend disposed on stop

A `SceneTree` SHALL expor um campo `textures: TextureBackend?` (default `null`), injetado pelo host no startup, espelhando o padrão de `textMeasurer` e `audio`. Nodes SHALL alcançar o serviço via `node.tree.textures`. Quando `textures` é `null`, a ausência de imagem MUST ser graciosa: chamadas no padrão `node.tree.textures?.load(...)` são no-op (retornam `null`) e nenhum erro é lançado; nodes que dependem de textura simplesmente não desenham. `SceneTree.stop()` SHALL chamar `textures?.dispose()` exatamente uma vez ao encerrar.

#### Scenario: textures field defaults to null and is settable

- **WHEN** uma `SceneTree(root)` é construída sem host
- **THEN** `tree.textures` é `null`
- **AND** o campo aceita atribuição de uma implementação de `TextureBackend`

#### Scenario: Null texture backend is a graceful no-op

- **WHEN** `tree.textures` é `null` e um `Sprite2D` entra na árvore e é renderizado
- **THEN** nenhuma exceção é lançada e nada é desenhado para o sprite

#### Scenario: stop disposes the texture backend

- **WHEN** `tree.textures` está setado e `tree.stop()` é chamado
- **THEN** `TextureBackend.dispose()` é invocado exatamente uma vez

### Requirement: load decodes once, caches by path, and fails fast

`TextureBackend.load(path)` SHALL resolver o asset via classpath (`getResourceAsStream`) usando a convenção de path dos demais assets do projeto (ex.: `"demos/sprites/idle.png"`), decodificar o PNG **uma única vez**, e devolver um `Texture` cujo `width`/`height` refletem o bitmap. Chamadas repetidas de `load` com o **mesmo path** SHALL devolver o **mesmo** handle (cache por path; sem re-decode). Um path inexistente, ilegível ou não-decodificável MUST falhar fast com exceção descritiva (não retornar handle silencioso). `dispose()` SHALL liberar todas as texturas do cache.

#### Scenario: load returns a reusable handle with correct dimensions

- **WHEN** `load("...idle.png")` é chamado para um PNG de classpath de 352x32
- **THEN** retorna um `Texture` não-nulo com `width == 352` e `height == 32`

#### Scenario: load caches by path

- **WHEN** `load("...idle.png")` é chamado duas vezes com o mesmo path
- **THEN** o segundo retorno é o **mesmo** handle do primeiro (sem re-decode)

#### Scenario: Missing asset fails fast

- **WHEN** `load("does/not/exist.png")` é chamado
- **THEN** uma exceção descritiva é lançada (não há retorno de handle nulo silencioso)

### Requirement: Sprite2D is a Node2D that draws a texture centered in local space

`:engine` SHALL prover `Sprite2D : Node2D` (`@Serializable`, `open`) com:

- `texturePath: String` (`@Inspect`) — caminho do asset.
- `flipH: Boolean` (`@Inspect`, default `false`) — espelha horizontalmente.

`Sprite2D` SHALL resolver o handle de textura no `onEnter` via `tree.textures?.load(texturePath)` (guardado em campo `@Transient`), e em `onDraw` SHALL desenhar a textura **inteira** centrada na origem local — `src = Rect(Vec2.ZERO, Vec2(w, h))`, `dst = Rect(Vec2(-w/2, -h/2), Vec2(w, h))` — via `renderer.drawImage(texture, src, dst, flipH)`, sob a transform stack empilhada pela `SceneTree`. `localBounds` SHALL ser o retângulo centrado `Rect(Vec2(-w/2, -h/2), Vec2(w, h))` quando há textura, e `Rect(ZERO, ZERO)` quando `tree.textures`/handle é `null`. Quando o handle é `null`, `onDraw` MUST ser no-op.

#### Scenario: Sprite2D draws its texture centered

- **WHEN** um `Sprite2D` com `texturePath` válido (textura WxH) é renderizado em `position = (100, 100)` sem rotação/escala de ancestral
- **THEN** a textura aparece centrada em `(100, 100)`, ocupando `[100 - W/2, 100 + W/2] x [100 - H/2, 100 + H/2]` na superfície

#### Scenario: flipH mirrors horizontally without negative scale

- **WHEN** um `Sprite2D` com `flipH = true` é renderizado
- **THEN** a imagem aparece espelhada na horizontal
- **AND** o `transform.scale.x` do node permanece positivo (o espelho é puramente visual, no `drawImage`)

#### Scenario: Sprite2D with no backend is invisible but safe

- **WHEN** `tree.textures` é `null` e um `Sprite2D` é renderizado
- **THEN** `onDraw` não desenha nada e não lança
- **AND** `localBounds` é `Rect(ZERO, ZERO)`
