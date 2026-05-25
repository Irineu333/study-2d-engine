# timer-node Specification

## Purpose

Nó nativo `Timer` (estende `Node` puro, não `Node2D`) que cadencia eventos em intervalo via signal `timeout`. Cobre `_process` vs `_physics_process` (selecionável por `processCallback: TimerMode`), `autostart`, `oneShot`, `start(override)`/`stop()`, e `isStopped`. Primeiro nó lógico não-visual da engine — precedente arquitetural para `AudioPlayer`, `AnimationPlayer` futuros. Também é o primeiro signal nascido em Kotlin que cruza a ponte para Python via reflection.

## Requirements

### Requirement: Timer node extends Node and is registered in NodeRegistry

The engine SHALL provide a `Timer` class in `com.neoutils.engine.scene.Timer` that extends `Node` (not `Node2D`) and is annotated `@Serializable`. `Timer` MUST be registered in `NodeRegistry` under the type tag `engine.Timer` so scene files can declare it. `Timer` MUST have a public no-args constructor as required by `Serializable Node` invariants.

#### Scenario: Timer class exists and extends Node

- **WHEN** the `:engine` source tree is inspected
- **THEN** a class `Timer` exists at `com.neoutils.engine.scene.Timer`
- **AND** `Timer` extends `Node` directly (not `Node2D`)
- **AND** `Timer` is annotated `@Serializable`

#### Scenario: Timer is registered under engine.Timer

- **WHEN** code calls `NodeRegistry.create("engine.Timer")`
- **THEN** the result is a fresh `Timer` instance with default property values

#### Scenario: Timer carries no transform

- **WHEN** code inspects a `Timer` instance
- **THEN** no `transform` field is accessible
- **AND** the instance is not assignable to `Node2D`

### Requirement: Timer exposes Godot-style properties

The `Timer` node SHALL expose the following `@Inspect var` properties: `waitTime: Float` (default `1.0`), `autostart: Boolean` (default `false`), `oneShot: Boolean` (default `false`), and `processCallback: TimerMode` (default `TimerMode.PHYSICS`). `TimerMode` MUST be an enum class with exactly two values: `PHYSICS` and `IDLE`. `Timer` SHALL also expose `@Transient var timeLeft: Float` (default `0.0`) reflecting the remaining time until the next `timeout` emission; `timeLeft` MUST NOT be persisted to `scene.json`. `Timer` SHALL expose a read-only `val isStopped: Boolean` that reports `true` when `timeLeft <= 0` and the timer is not autostarted-and-pending.

#### Scenario: Default property values

- **GIVEN** a fresh `Timer` instance created via `NodeRegistry.create("engine.Timer")`
- **THEN** `timer.waitTime == 1.0f`
- **AND** `timer.autostart == false`
- **AND** `timer.oneShot == false`
- **AND** `timer.processCallback == TimerMode.PHYSICS`
- **AND** `timer.timeLeft == 0.0f`
- **AND** `timer.isStopped == true`

#### Scenario: TimerMode is a binary enum

- **WHEN** the `TimerMode` enum is inspected
- **THEN** it contains exactly two entries: `PHYSICS` and `IDLE`

#### Scenario: timeLeft is transient

- **WHEN** a `Timer` instance with `timeLeft = 0.5f` is serialized to JSON via `SceneLoader`
- **THEN** the resulting JSON does not contain a `timeLeft` key

### Requirement: Timer emits the timeout signal at fixed intervals

The `Timer` node SHALL expose a public `val timeout: Signal<Unit>` that emits each time `timeLeft` reaches zero or below while the timer is running. When the timer is started (via `autostart` or `start()`), `timeLeft` is initialized to `waitTime` (or to a positive `override` if provided to `start`). On each tick of its active process callback the engine MUST decrement `timeLeft` by the callback `dt`; when `timeLeft <= 0`, the engine MUST emit `timeout` exactly once and, if `oneShot == false`, reset `timeLeft = timeLeft + waitTime` (preserving overshoot for drift correction). If `oneShot == true`, the timer MUST stop after the single emission.

#### Scenario: Timer emits timeout once per interval

- **GIVEN** a `Timer` in a live scene with `waitTime = 0.1f`, `processCallback = PHYSICS`, `oneShot = false`, and a handler connected to `timeout`
- **WHEN** the scene runs `_physics_process` for one second at `60Hz`
- **THEN** the handler is invoked approximately `10` times (with at most ±1 due to tick alignment)

#### Scenario: oneShot stops after one emission

- **GIVEN** a `Timer` with `waitTime = 0.1f`, `oneShot = true`, started in a live scene
- **WHEN** the scene runs `_physics_process` past `0.1` seconds
- **THEN** the `timeout` signal is emitted exactly once
- **AND** subsequent ticks do not emit `timeout`
- **AND** `timer.isStopped == true`

#### Scenario: timeout signal is a Signal of Unit

- **WHEN** the type of `Timer.timeout` is inspected
- **THEN** it is exactly `Signal<Unit>`

### Requirement: processCallback selects the active tick

`Timer` SHALL decrement `timeLeft` in `onPhysicsProcess` when `processCallback == PHYSICS`, and in `onProcess` when `processCallback == IDLE`. The check MUST be performed on every tick (not cached at `onEnter`), so changing `processCallback` at runtime takes effect on the next tick of the new callback. When in `PHYSICS` mode, the `Timer` MUST NOT advance during `onProcess`; when in `IDLE` mode, it MUST NOT advance during `onPhysicsProcess`.

#### Scenario: PHYSICS mode advances only during physics tick

- **GIVEN** a `Timer` with `processCallback = PHYSICS`, `waitTime = 0.5f`, in a live scene that calls `onProcess` ten times with `dt = 0.1f` and zero physics ticks
- **THEN** `timeout` is never emitted
- **AND** `timer.timeLeft` is unchanged from its initial `0.5f`

#### Scenario: IDLE mode advances only during process tick

- **GIVEN** a `Timer` with `processCallback = IDLE`, `waitTime = 0.5f`, in a live scene that calls `onPhysicsProcess` ten times with `dt = 0.1f` and zero process ticks
- **THEN** `timeout` is never emitted
- **AND** `timer.timeLeft` is unchanged from its initial `0.5f`

#### Scenario: Switching processCallback at runtime takes effect next tick

- **GIVEN** a running `Timer` initially in `PHYSICS` mode
- **WHEN** code sets `timer.processCallback = TimerMode.IDLE`
- **AND** the next `onProcess` tick fires
- **THEN** `timeLeft` decrements on that `onProcess` call
- **AND** the next `onPhysicsProcess` call leaves `timeLeft` unchanged

### Requirement: autostart begins counting on onEnter

When `autostart == true`, the `Timer` SHALL initialize `timeLeft = waitTime` in `onEnter` so the first `timeout` fires after the full interval (not immediately). When `autostart == false`, `timeLeft` MUST remain at `0.0f` (stopped) after `onEnter` until the caller invokes `start()`.

#### Scenario: autostart=true schedules first emit after waitTime

- **GIVEN** a `Timer` with `autostart = true`, `waitTime = 0.2f`, `processCallback = PHYSICS`
- **WHEN** the timer is added to a live scene and `_physics_process` advances by `0.1` seconds
- **THEN** `timeout` has not yet been emitted
- **WHEN** another `0.1` seconds of `_physics_process` elapses
- **THEN** `timeout` has been emitted exactly once

#### Scenario: autostart=false leaves the timer stopped

- **GIVEN** a fresh `Timer` with `autostart = false` added to a live scene
- **THEN** `timer.isStopped == true`
- **AND** subsequent ticks do not emit `timeout`

### Requirement: start and stop control the timer manually

The `Timer` node SHALL expose `fun start(override: Float? = null)` and `fun stop()`. Calling `start()` with no argument MUST set `timeLeft = waitTime`. Calling `start(x)` with `x > 0` MUST set `timeLeft = x` for the next emission only — subsequent emissions (when `oneShot == false`) MUST use `waitTime`. Calling `start(x)` with `x <= 0` MUST throw `IllegalArgumentException` with a message that includes the offending value. Calling `stop()` MUST set `timeLeft = 0` and mark `isStopped = true`; pending emissions that would have fired on the current tick MUST NOT fire after `stop()` returns within that tick.

#### Scenario: start with no argument uses waitTime

- **GIVEN** a stopped `Timer` with `waitTime = 0.5f`
- **WHEN** code calls `timer.start()`
- **THEN** `timer.timeLeft == 0.5f`
- **AND** `timer.isStopped == false`

#### Scenario: start with positive override applies only to next emission

- **GIVEN** a `Timer` with `waitTime = 0.5f`, `oneShot = false`, in a live scene with `PHYSICS` mode
- **WHEN** code calls `timer.start(0.1f)` and the scene runs `_physics_process` until first `timeout`
- **THEN** the first `timeout` fires at approximately `0.1f` elapsed
- **AND** the second `timeout` fires approximately `0.5f` after the first

#### Scenario: start with non-positive override throws

- **WHEN** code calls `timer.start(0.0f)` or `timer.start(-1.0f)`
- **THEN** an `IllegalArgumentException` is thrown
- **AND** the message contains the offending value

#### Scenario: stop halts further emissions

- **GIVEN** a running `Timer`
- **WHEN** code calls `timer.stop()`
- **THEN** `timer.timeLeft == 0.0f`
- **AND** `timer.isStopped == true`
- **AND** subsequent ticks do not emit `timeout` until `start()` is called

### Requirement: Timer stops automatically on onExit

When a `Timer` leaves the live tree, the engine SHALL call `stop()` internally so that no count survives detachment. If the same `Timer` is re-attached via `addChild`, `onEnter` MUST re-honor `autostart` (restarting if `autostart == true`, leaving stopped otherwise).

#### Scenario: Removing a Timer stops it

- **GIVEN** a running `Timer` whose parent removes it via `removeChild`
- **WHEN** the deferred removal completes
- **THEN** `timer.isStopped == true`
- **AND** no further `timeout` emissions occur

#### Scenario: Re-attaching with autostart restarts the timer

- **GIVEN** a stopped `Timer` with `autostart = true` that was previously detached
- **WHEN** the timer is re-added as a child of a live node
- **THEN** `timer.timeLeft == timer.waitTime`
- **AND** `timer.isStopped == false`

### Requirement: Timer properties are loaded from scene.json properties bag

`SceneLoader` SHALL route Timer properties (`waitTime`, `autostart`, `oneShot`, `processCallback`) declared inside the unified `properties` bag of a `scene.json` node entry typed `engine.Timer`. The string values for `processCallback` MUST be exactly `"PHYSICS"` or `"IDLE"`; any other value MUST cause `SceneLoader` to fail fast naming the offending node, property, and the set of valid enum values.

#### Scenario: Valid Timer entry loads with all properties

- **GIVEN** a `scene.json` containing `{ "type": "engine.Timer", "name": "MoveTimer", "properties": { "waitTime": 0.125, "autostart": true, "oneShot": false, "processCallback": "PHYSICS" } }`
- **WHEN** `SceneLoader` loads the scene
- **THEN** the resulting `Timer` has `waitTime == 0.125f`, `autostart == true`, `oneShot == false`, `processCallback == TimerMode.PHYSICS`

#### Scenario: Invalid processCallback value fails fast

- **GIVEN** a `scene.json` entry with `"processCallback": "physics"` (lowercase)
- **WHEN** `SceneLoader` attempts to load
- **THEN** loading fails with an error message that includes the node name, the property name `processCallback`, the offending value `"physics"`, and the valid enum names (`PHYSICS`, `IDLE`)
