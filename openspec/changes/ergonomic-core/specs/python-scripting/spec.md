## ADDED Requirements

### Requirement: Python scripts use ergonomic accessors for local transform and world()

Python scripts attached to `Node2D` (or any subclass) SHALL access local transform components via the Kotlin-provided properties `position`, `rotation`, and `scale`, which are surfaced by GraalPy interop without additional binding glue. Scripts SHALL access world-space transform via `self.world()` (function call, returning a `Transform`), and world-space position via `self.world().position`. The legacy script-side helper `self.worldPosition()` MUST NOT appear in any engine-shipped Python script after this change. Writes through `self.position = Vec2(...)`, `self.rotation = ...`, and `self.scale = Vec2(...)` MUST propagate to the host `Node2D.transform` and invalidate the world-transform cache identically to a Kotlin-side assignment.

#### Scenario: Python read of self.position mirrors host transform.position

- **GIVEN** a Python script attached to a `Node2D` whose `transform.position = Vec2(10f, 20f)` was set from Kotlin
- **WHEN** the script reads `self.position` inside any hook
- **THEN** the result equals `Vec2(10, 20)`

#### Scenario: Python write of self.position updates host transform and invalidates cache

- **GIVEN** a Python script attached to a parent `Node2D` that has a `Node2D` child, both with cached `world()` populated
- **WHEN** the script's `_physics_process` assigns `self.position = Vec2(99.0, 99.0)`
- **THEN** the host's `node.transform.position` equals `Vec2(99, 99)` after the call returns
- **AND** the next read of `child.world()` reflects the new parent position

#### Scenario: Partial component write fails fast in Python

- **GIVEN** a Python script attached to a `Node2D`
- **WHEN** the script executes `self.position.y = 50.0`
- **THEN** Python raises `AttributeError` because `Vec2.y` is a Kotlin `val` with no setter
- **AND** the error propagates per the existing fail-fast script contract

#### Scenario: self.world() returns the composed world transform

- **GIVEN** a Python script attached to a child `Node2D` whose parent has `transform.position = Vec2(100f, 0f)` and identity otherwise, and the child's local `transform.position = Vec2(5f, 0f)`
- **WHEN** the script reads `self.world().position`
- **THEN** the result equals `Vec2(105, 0)`

#### Scenario: worldPosition() does not exist on Python-side Node2D

- **WHEN** any engine-shipped Python script under `games/*/src/main/resources/*/scripts/` is grepped for `worldPosition`
- **THEN** no matches are found

## MODIFIED Requirements

### Requirement: PyI stubs are published as module resources

`:engine-bundle-python` SHALL publicar arquivos `.pyi` (PEP 561 stubs) em `src/main/resources/stubs/engine/` cobrindo no mínimo: `Node`, `Node2D`, `BoxCollider`, `Renderer`, `Input`, `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`. Os stubs MUST refletir a API Kotlin pública de cada um desses tipos, incluindo as três properties ergonômicas de `Node2D` (`position: Vec2`, `rotation: float`, `scale: Vec2`) e a função `world(self) -> Transform`. Os stubs MUST NÃO declarar `worldPosition` em nenhum tipo após esta change. A docstring de `Vec2` no stub MUST documentar que a classe é imutável (atribuição de componente individual via `v.y = ...` lança `AttributeError`).

#### Scenario: Stubs resource directory exists

- **WHEN** o jar de `:engine-bundle-python` é construído
- **THEN** contém o diretório `stubs/engine/` com pelo menos `__init__.pyi`, `scene.pyi`, `math.pyi`, `render.pyi`, `input.pyi`, `physics.pyi`, `serialization.pyi`

#### Scenario: Stubs reflect public Kotlin API

- **GIVEN** o stub `engine/math.pyi`
- **WHEN** ele é inspecionado
- **THEN** declara `class Vec2: x: float; y: float; def __init__(self, x: float, y: float) -> None: ...`
- **AND** as assinaturas batem com as propriedades públicas da `Vec2` Kotlin

#### Scenario: Node2D stub exposes ergonomic accessors and world()

- **GIVEN** o stub `engine/scene.pyi` (ou `engine/__init__.pyi`, conforme organização)
- **WHEN** o tipo `Node2D` é inspecionado
- **THEN** declara `position: Vec2`, `rotation: float`, `scale: Vec2` como atributos públicos mutáveis
- **AND** declara `def world(self) -> Transform: ...`
- **AND** NÃO declara `worldTransform` nem `worldPosition`

#### Scenario: Vec2 stub documents immutability

- **GIVEN** o stub de `Vec2`
- **WHEN** sua docstring é inspecionada
- **THEN** menciona que `Vec2` é um data class Kotlin imutável
- **AND** menciona que atribuir um componente individual (`v.y = ...`) lança `AttributeError`
