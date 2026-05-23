## MODIFIED Requirements

### Requirement: Signal primitive for inter-node communication

The engine SHALL provide `Signal<T>` (in `com.neoutils.engine.serialization` for source-compatibility with existing imports, but conceptually an event hub used throughout `:engine`) as a runtime event hub with `connect(handler) -> Disposable`, `disconnect(disposable)`, and `emit(value)` operations. `Signal` instances appearing on `@Serializable Node` subclasses MUST be annotated with `@Transient` because their handlers are runtime-only and cannot be serialized; the static configuration that a future editor would want (e.g. "this signal is wired to that handler in another node") MUST live in a separate serializable structure (out of scope for this change). The previous `Signal` API that conflated wiring (`var path: NodeRef`) with the signal contract SHALL be removed; routing in the editor era will use `NodeRef` + `Signal` composition, not a hybrid type.

#### Scenario: Signal has connect/emit/disconnect runtime API

- **WHEN** code instantiates `Signal<String>()` and calls `signal.connect { ... }`
- **THEN** the call returns a `Disposable`
- **AND** subsequent `signal.emit("hello")` invokes the registered handler with `"hello"`

#### Scenario: Signal field on a Serializable Node is Transient

- **WHEN** any class in `:engine` extending `Node` and annotated `@Serializable` declares a `Signal` field
- **THEN** that field is annotated `@Transient`
- **AND** the field is not present in the JSON produced by `SceneLoader.save`

#### Scenario: Old Signal-with-NodeRef shape is removed

- **WHEN** the source of `Signal.kt` (or its replacement) is inspected
- **THEN** there is no `var path: NodeRef` property on `Signal`
- **AND** there is no constructor of `Signal` accepting a `NodeRef`

### Requirement: @Inspect and @Transient discipline on Node subclasses

Every `var` on a `@Serializable Node` subclass shipped by `:engine` SHALL be annotated either with `@Inspect` (configuration that appears in `scene.json` and will be editable in the future visual editor) or with `@Transient` (runtime state that MUST NOT be persisted). This rule applies recursively to subclasses but does NOT apply to game-side code (games are encouraged to follow it but no engine machinery enforces). `Signal` properties, callback fields, current-tick caches, and similar runtime-only state MUST be `@Transient`.

#### Scenario: Every engine var is annotated

- **WHEN** any class in `:engine` extending `Node` and annotated `@Serializable` is inspected
- **THEN** every `var` property is annotated either with `@Inspect` (`com.neoutils.engine.serialization.Inspect`) or with `@Transient` (`kotlinx.serialization.Transient`)

#### Scenario: Signal fields are Transient

- **WHEN** any engine-shipped Node has a `Signal<*>` field
- **THEN** the field is `@Transient`
- **AND** it does not appear in serialized JSON
