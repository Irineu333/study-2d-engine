## Why

O módulo `:games:demos` cresceu para **10 slots** trocados por tecla (`1`–`0`), com forte sobreposição de cobertura, código duplicado (`hue()` copiada em 3 arquivos, tracking de FPS + `drawText` repetido) e informação redundante com ferramentas de debug já existentes (FPS no canto vs. profiler em `F1`). Várias telas são triviais isoladas (escala, sprite estático, tilemap estático) e agregariam mais valor fundidas. Consolidar para **5 demos densas + um menu de navegação real** torna o módulo mais didático, elimina redundância, e — de brinde — adiciona a primeira cobertura de `Camera2D` entre as demos.

## What Changes

- **BREAKING**: o catálogo de cenas cai de 10 para 5; navegação por teclas `1`–`0` é substituída por um **menu de UI** (botões, um por demo) com botão "← Menu" em cada demo. Vários nomes/slots de cena deixam de existir.
- **Funde 1 + 2 → "Transforms"**: sistema solar (rotação aninhada) ganha **zoom/pan interativo via `Camera2D`** (scroll/teclas) e um corpo com pulso de escala. Remove `ScaleHierarchyDemo` como slot próprio.
- **Funde 3 + 4 → "Spawn & Collide"**: `Area2D` trap + `RigidBody2D` quicando + spawn/despawn vivo no scene graph. Remove `CollisionStressDemo` como slot próprio.
- **Mantém 5 standalone → "Rotating Frame"**: `CharacterBody2D.moveAndCollide` varrido em frame local rotativo (invariante sutil, preservado).
- **Mantém 6 → "Tumbling Swarm"**: solver `RigidBody2D` completo (linear + angular + Coulomb).
- **Funde 8 + 9 + 0 → "Sprites & Tiles"**: `TileMap` (chão) + `AnimatedSprite2D` correndo + `Sprite2D` decorativo; player como `CharacterBody2D` sobre `StaticBody2D`. Remove `SpriteDemo`, `AnimatedSpriteDemo`, `TileMapDemo` como slots próprios.
- **Absorve a demo 7 (UI)**: o próprio menu + o botão "← Menu" presente em toda demo passam a exercitar `CanvasLayer`/`Button`/`Panel`/`Label`/anchors/z-order/click-consumption continuamente. Remove `UiPlaygroundDemo` como slot dedicado.
- **Remove redundância transversal**: extrai `hue()` para um helper compartilhado; remove o tracking de FPS + `drawText` por demo (profiler em `F1` é a fonte de verdade); título/descrição de cada demo migram de `drawText` cru para `Label` em `CanvasLayer`; métricas (contagem de corpos, contatos, velocidade) passam por `tree.debug` (gizmos/profiler) em vez de texto no canto.
- **BREAKING**: a convenção "nenhuma demo usa `Camera2D`" é **removida** — a demo "Transforms" passa a usar `Camera2D` deliberadamente.
- `MainLwjgl` continua apontando para a mesma raiz, mantendo a sentinela do invariante #4 (a demo "Sprites & Tiles" segue rodando cross-backend).

## Capabilities

### New Capabilities

(nenhuma — o refactor reorganiza capacidades já cobertas e adiciona cobertura de `Camera2D` dentro de uma capability existente.)

### Modified Capabilities

- `demos-sample`: catálogo de cenas reescrito (10 → 5 demos + menu de UI); navegação por teclas `1`–`0` substituída por menu de botões + botão "voltar"; requirement "No Camera2D in any demo" **removido**; FPS overlay por demo removido (profiler `F1` assume); título/descrição via `CanvasLayer`/`Label`; `hue()` extraída para helper compartilhado; cenas de sprite/animação/tilemap fundidas numa única "Sprites & Tiles"; cena de UI absorvida pelo menu/back-button.
- `solar-system-demo`: passa a ser a demo "Transforms" — adiciona zoom/pan interativo via `Camera2D` e um corpo com pulso de escala (cobrindo o que era a demo 2); a "no-Camera2D convention" que esta capability fixava é removida.

## Impact

- **Código**: `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/` — remoção de `ScaleHierarchyDemo.kt`, `CollisionStressDemo.kt`, `SpriteDemo.kt`, `AnimatedSpriteDemo.kt`, `TileMapDemo.kt`, `UiPlaygroundDemo.kt`; reescrita de `DemoSwitcherRoot.kt` (menu + troca de cena + back button) e `Main.kt`/`MainLwjgl.kt` (registro de widgets inalterado); novos arquivos para "Spawn & Collide" e "Sprites & Tiles"; `SolarSystemDemo.kt` ganha câmera + pulso de escala; novo helper de `hue()`/paleta compartilhado.
- **Specs**: `demos-sample` e `solar-system-demo` recebem deltas.
- **Docs**: tabela "Games" do `CLAUDE.md` e seção de demos do `README.md` (resumo de uma linha por demo) precisam refletir o novo catálogo de 5.
- **Backends**: nenhuma mudança em `:engine`/`:engine-skiko`/`:engine-lwjgl`; LWJGL segue como sentinela cross-backend via a demo "Sprites & Tiles".
- **Dependências**: nenhuma nova.
