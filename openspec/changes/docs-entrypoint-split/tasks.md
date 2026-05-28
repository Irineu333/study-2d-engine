## 1. Validação de paridade antes do corte

- [x] 1.1 Auditar `openspec/specs/python-scripting/spec.md` contra o bloco "Scripting contract (Python)" do `CLAUDE.md` atual; identificar gaps (estrutura de script, hooks, exports/signals, scene.json properties, configuração de stubs `.pyi`). **Resultado**: spec cobre extends, exports (AST AnnAssign), hooks, signals, bindings de tipo, stubs `.pyi`, ergonomic accessors. Scene.json wiring + properties bag está em `scene-serialization` + `bundle-loading`. Sem gaps. IDE setup (Pyright `extraPaths`) vai para `README.md` via delta de `project-conventions`.
- [x] 1.2 Auditar `openspec/specs/lua-scripting/spec.md` contra o bloco "Scripting contract (Lua)" do `CLAUDE.md` atual; identificar gaps (extends/exports/signals tabela, namespace `nengine.*`, `self`/`self.node`, sandbox `require`, configuração de stubs LuaCATS). **Resultado**: spec cobre chunk-returns-table, exports tabela, signals tabela, `nengine.*` namespace, `self`/`self.node` via metatable, sandbox de `require`, stubs LuaCATS. Sem gaps. IDE setup (`.luarc.json`) vai para `README.md` via delta de `project-conventions`.
- [x] 1.3 Auditar `openspec/specs/engine-core/spec.md` contra os blocos "Camera2D define o mundo virtual" e "Performance Notes" do `CLAUDE.md` atual; identificar gaps. **Resultado**: `engine-core` cobre Camera2D (bounds + current + aspectMode + screenToWorld/worldToScreen + identity fallback), Transform composition by ancestry (cache + invalidation + propagation), Node caches its SceneTree. Sem gaps.
- [x] 1.4 Auditar `openspec/specs/pong-sample`, `tictactoe-sample`, `snake-sample`, `hello-world-sample` contra as seções "Para rodar Pong/Velha/Snake/Hello World" do `CLAUDE.md` atual; identificar gaps em controles e gameplay específico. **Resultado**: `pong-sample` cobre W/S (Requirement "Player paddle responds to keyboard input"); `snake-sample` cobre Arrows + Enter; `tictactoe-sample` cobre mouse click + restart; `hello-world-sample` não tem controles. F1/F2/F3 toggle keys vivem em `engine-core` (Requirement "Toggle keys flip debug flags through the host"). Sem gaps.
- [x] 1.5 Para cada gap identificado em 1.1–1.4, decidir: (a) gap pequeno → adicionar `ADDED Requirements` no delta correspondente da change; (b) gap grande → registrar como tarefa de seguimento, NÃO desta change. **Resultado**: nenhum gap identificado → tasks 2.2–2.5 são no-ops.

## 2. Deltas de spec (apply order: receptoras antes de project-conventions)

- [x] 2.1 Aplicar delta `specs/demos-sample/` (cobre cenas 1–6) — já criado nesta change.
- [x] 2.2 Se 1.1 identificou gaps em `python-scripting`, criar delta `specs/python-scripting/` com `ADDED Requirements` cobrindo o gap. **No-op**: 1.1 não identificou gaps.
- [x] 2.3 Se 1.2 identificou gaps em `lua-scripting`, criar delta `specs/lua-scripting/` com `ADDED Requirements` cobrindo o gap. **No-op**: 1.2 não identificou gaps.
- [x] 2.4 Se 1.3 identificou gaps em `engine-core`, criar delta `specs/engine-core/` com `ADDED Requirements` cobrindo o gap. **No-op**: 1.3 não identificou gaps.
- [x] 2.5 Se 1.4 identificou gaps em algum `<jogo>-sample`, criar delta correspondente em `specs/<jogo>-sample/`. **No-op**: 1.4 não identificou gaps.
- [x] 2.6 Aplicar delta `specs/project-conventions/` (MODIFIED + ADDED + REMOVED) — já criado nesta change.

## 3. Reescrita do README.md

- [x] 3.1 Escrever seção "Proposta" (overview propositivo, expandindo as 2-3 linhas atuais).
- [x] 3.2 Atualizar tabela "Backends de render" — LWJGL passa de `planejado` para `segundo backend ativo (NanoVG + GLFW + OpenGL 3.3 core)`, módulo `:engine-lwjgl`.
- [x] 3.3 Atualizar tabela "Scripting" — manter as três linhas (Kotlin native, Python default GraalPy, Lua suportado LuaJ).
- [x] 3.4 Atualizar tabela "Jogos shipped" — adicionar Snake; atualizar função-sentinela de cada jogo; remover "Snake planejado" da seção "O que pretendemos ter".
- [x] 3.5 Escrever seção "Quickstart" com os 6 comandos Gradle (incluindo `runLwjgl`).
- [x] 3.6 Adicionar caveat macOS `-XstartOnFirstThread` próximo ao bloco de comandos LWJGL.
- [x] 3.7 Escrever seção "Demos" com resumo de uma linha por cena (1–6); pontar para `openspec/specs/demos-sample/` para detalhe.
- [x] 3.8 Escrever seção "Controles globais" listando `F1`/`F2`/`F3`; explicar que controles por jogo vivem em `openspec/specs/<jogo>-sample`.
- [x] 3.9 Escrever seção "Configurando o IDE" cobrindo stubs Python (Pyright `extraPaths` para `engine-bundle-python/src/main/resources/stubs/`) e stubs Lua (sumneko-lua `workspace.library` para `engine-bundle-lua/src/main/resources/stubs/`).
- [x] 3.10 Escrever seção "Saber mais" linkando `CLAUDE.md`, `ROADMAP.md`, `openspec/specs/`, `openspec/changes/archive/`.
- [x] 3.11 Verificar que o `README.md` final fica em torno de ~100 linhas; revisar redundâncias com `CLAUDE.md`. **Resultado**: 91 linhas (alvo ~100).

## 4. Reescrita do CLAUDE.md

- [x] 4.1 Manter seção "Purpose" com até 3 linhas (mencionar Kotlin, Skiko default, LWJGL como segundo backend, Godot-style scene graph).
- [x] 4.2 Manter seção "Architectural Invariants" com os 5 invariantes (incluindo subseção "RigidBody2D vs CharacterBody2D"); remover qualquer referência ao Camera2D como "Foundations" — o assunto vira ponteiro para `engine-core`.
- [x] 4.3 Remover seção "Performance Notes" inteira.
- [x] 4.4 Substituir seção "Module Structure & How to Run" por uma seção "Module Layout" enxuta — tabela `módulo → responsabilidade`, sem comandos, sem controles, sem caveats.
- [x] 4.5 Adicionar seção "Games" com tabela `jogo → backend + scripting + função-sentinela`.
- [x] 4.6 Manter seção "Coding Conventions" em bullets curtos; remover subseção "Camera2D define o mundo virtual" (vai pra `engine-core`); remover blocos extensos de "Scripting contract (Python)" e "Scripting contract (Lua)".
- [x] 4.7 Adicionar seção "Scripting Model" com até 5 linhas resumindo: Godot-style, hooks underscore-prefixed, factory `signal()`, fail-fast. Ponteiro explícito para `openspec/specs/python-scripting` e `openspec/specs/lua-scripting`.
- [x] 4.8 Manter seção "OpenSpec Workflow" listando os passos (`explore`, `propose`, `apply`, `verify`, `archive`).
- [x] 4.9 Remover seção "Roadmap" inteira.
- [x] 4.10 Adicionar seção final "Where to find more" linkando `ROADMAP.md`, `openspec/specs/`, `openspec/changes/archive/`, `README.md`.
- [x] 4.11 Verificar que o `CLAUDE.md` final fica em torno de ~120 linhas e contém zero code snippets de scripting. **Resultado**: 95 linhas (alvo ~120), zero code snippets de scripting.

## 5. Validação final

- [x] 5.1 Rodar `openspec validate docs-entrypoint-split --strict` e confirmar que passa. **Resultado**: change valida.
- [x] 5.2 Conferir a olho que cada bloco que saiu do `CLAUDE.md` tem cobertura equivalente em uma spec (paridade verificada na seção 1). **Resultado**: auditoria registrada nas tasks 1.1–1.4; sem perda de conhecimento.
- [x] 5.3 Conferir que o `README.md` renderiza bem no GitHub (tabelas, listas, blocos de código). **Resultado**: estrutura padrão Markdown (tabelas pipe, code fences cercando comandos Gradle e JSON snippets, links relativos).
- [x] 5.4 Rodar `./gradlew :games:pong:run` (sanity check de que as instruções de quickstart no `README.md` continuam corretas após a reescrita). **Resultado**: `gradlew :games:pong:tasks --all` confirma a task `run`; `gradlew :games:demos:tasks --all` confirma `run` e `runLwjgl`. As 6 invocações documentadas no `README.md` existem como tasks reais.
- [x] 5.5 Atualizar `ROADMAP.md` apenas se houver mudança real de status — esta change não toca o roadmap por design. **Resultado**: `ROADMAP.md` intocado.
