## MODIFIED Requirements

### Requirement: Timer node extends Node and is registered in NodeRegistry

The engine SHALL provide a `Timer` class in `com.neoutils.engine.scene.Timer` that extends `Node` (not `Node2D`), is annotated `@Serializable`, and is declared `open class` so game code MAY subclass it (matching the default extensibility policy for shipped leaf nodes). `Timer` MUST be registered in `NodeRegistry` under the type tag `engine.Timer` so scene files can declare it. `Timer` MUST have a public no-args constructor as required by `Serializable Node` invariants.

#### Scenario: Timer class exists and extends Node

- **WHEN** the `:engine` source tree is inspected
- **THEN** a class `Timer` exists at `com.neoutils.engine.scene.Timer`
- **AND** `Timer` extends `Node` directly (not `Node2D`)
- **AND** `Timer` is annotated `@Serializable`
- **AND** `Timer` is declared `open class` (subclassable)

#### Scenario: Timer is registered under engine.Timer

- **WHEN** code calls `NodeRegistry.create("engine.Timer")`
- **THEN** the result is a fresh `Timer` instance with default property values

#### Scenario: Timer carries no transform

- **WHEN** code inspects a `Timer` instance
- **THEN** no `transform` field is accessible
- **AND** the instance is not assignable to `Node2D`

#### Scenario: Game code can subclass Timer

- **WHEN** game code declares `class IntervalTimer : Timer()`
- **THEN** the declaration compiles
- **AND** `IntervalTimer` inherits `waitTime`, `autostart`, `oneShot`, `processCallback`, and `timeout: Signal<Unit>`
