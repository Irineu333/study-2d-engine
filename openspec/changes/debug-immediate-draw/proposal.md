## Why

Para desenhar um gizmo de debug hoje — uma linha, um círculo num ponto de
contato, um vetor de velocidade — é preciso subclassar `WorldDebugWidget`
ou `ScreenDebugWidget`, registrar a instância e gerir seu ciclo de vida.
Cerimônia demais para o caso dominante: "só quero ver essa linha esse
frame". Falta uma API immediate-mode — estilo `Debug.DrawLine` (Unity) /
`draw_line` (Godot) — em que game code e scripts emitem primitivas que
aparecem por um frame e somem sozinhas. Além de ergonomia, ela é a
**primitiva** sobre a qual a próxima change (`debug-physics-gizmos`) vai
desenhar contatos, normais e sweeps sem inventar widgets dedicados.

## What Changes

- **Nova facade `tree.debug.draw`** com dois espaços simétricos —
  `world` (segue a view transform da `Camera2D`) e `screen` (pixels de
  tela) — cada um expondo verbos immediate-mode espelhando o `Renderer`:
  `line`, `rect`, `circle`, `polygon`, `text`. Cada chamada **acumula** um
  comando num buffer por frame.
- **Limpeza automática por frame.** Os buffers são desenhados durante o
  `SceneTree.render` (world buffer no world pass, screen buffer no UI
  pass) e **esvaziados no fim do `render`**. Comandos emitidos em
  `_process`/`_physics_process` aparecem no frame seguinte e somem — sem
  acúmulo entre frames, sem cleanup manual.
- **Dois nodes de backing auto-inseridos** sob o `DebugLayer`: um
  `Node2D` no `WorldDebugContainer` que flusha o world buffer, e um node
  no `ScreenDebugCanvas` que flusha o screen buffer. Internos — não são
  `DebugWidget`s públicos individuais.
- **Toggle único no HUD.** A facade tem um `enabled` que aparece como uma
  row "Debug Draw" no `DebugHud`. Quando desabilitada, os verbos são
  **no-ops** (não acumulam — custo zero); quando habilitada, acumulam e
  desenham. Mantém a disciplina de frame de produção limpo por default.
- **Exposição a Python e Lua.** Scripts alcançam `self.tree.debug.draw`
  e emitem gizmos com os mesmos `Vec2`/`Color` já ligados nos hosts.

## Capabilities

### New Capabilities

- `debug-immediate-draw`: a facade `DebugDraw` com espaços `world`/`screen`,
  os verbos immediate-mode, a semântica de acúmulo-e-limpeza por frame, o
  gating por `enabled` (no-op quando off), a exposição via
  `tree.debug.draw`, a auto-inserção dos nodes de backing sob o
  `DebugLayer`, o flush nos passes de render + limpeza no tail do
  `SceneTree.render`, e a exposição a scripts.

### Modified Capabilities
<!-- Nenhuma. A integração com debug-overlay (acessor no registry, nodes de
     backing, row no HUD) é descrita como requirements ADICIONADAS na nova
     capability, deliberadamente para NÃO modificar as mesmas requirements
     que a change debug-log-overlay já altera ("SceneTree exposes a
     DebugRegistry" / "auto-inserts DebugLayer") e evitar conflito de delta.
     A row "Debug Draw" no DebugHud é automática pela requirement genérica
     existente "DebugHud lists registered widgets as togglable rows". -->

## Impact

- **Código afetado:**
  - `:engine` `com.neoutils.engine.debug` — nova `DebugDraw` + `DebugCanvas`
    (um por espaço) + os dois nodes de backing; `DebugRegistry.draw`.
  - `:engine` `SceneTree.render` — chamada de limpeza dos buffers no tail
    do método (depois dos dois passes). Sem mudança no `GameHost` nem no
    `GameLoop` (a limpeza vive dentro de `render`).
  - `:engine-bundle-python` / `:engine-bundle-lua` — garantir que
    `self.tree.debug.draw.world/screen.*` seja navegável sob o HostAccess
    atual (e bindar explicitamente se a política for restritiva); stubs
    `.pyi` / LuaCATS atualizados.
- **Semântica de frame:** comandos são single-frame (sem duração/TTL no
  MVP). Emitir em `onDraw` de nodes que pintam depois do container é
  comportamento de borda não suportado — emita em process/physics.
- **Não-objetivos (ver design):** persistência multi-frame/TTL, depth
  sorting entre gizmos e cena, e qualquer alocação por-comando que pressione
  o GC ficam fora do MVP.
- **Testes:** acúmulo e flush por frame; limpeza ao fim do render; no-op
  quando desabilitado; roteamento world vs screen para o pass certo;
  emissão a partir de script Python e Lua. Sem novas dependências externas.
