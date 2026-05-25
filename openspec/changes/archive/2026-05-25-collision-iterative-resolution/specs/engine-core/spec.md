## ADDED Requirements

### Requirement: PhysicsSystem iterates dispatch until convergence within a step

`PhysicsSystem.step(tree)` MUST repeatedly recompute the set of currently overlapping `CollisionObject2D` pairs and dispatch the `_entered` / `_exited` events that emerge between iterations, **within the same step**, until no new event would be dispatched (the set stabilises) or a fail-safe maximum number of iterations is reached.

The semantics observed by user-level scripts MUST be: for every real begin-of-overlap that exists at the end of the step, exactly one `_entered` event was dispatched on each side during the step; for every real end-of-overlap, exactly one `_exited`. The internal iteration is invisible to scripts and to signal subscribers.

When the fail-safe maximum is reached and the set is still changing, the engine MUST log a warning naming the iteration count and the pair count still in transition. It MUST NOT crash.

Pre-existing invariants from `collision-overhaul` MUST hold per iteration: exits are dispatched before enters; deferred mutation rules continue to apply; detached endpoints are cleaned out of the tracked pair set once at the start of the step.

#### Scenario: Three-body pile-up resolves enter events for cascading overlaps

- **GIVEN** three `StaticBody2D` A, B, C in a live scene, each with a `CollisionShape2D` holding a `RectangleShape2D`
- **AND** initially (A, B) overlap but (B, C) and (A, C) do not
- **AND** `A.onBodyEntered` is overridden to set `B.transform = ...` such that after that mutation, (B, C) overlaps
- **WHEN** `PhysicsSystem.step(tree)` runs once
- **THEN** `A.onBodyEntered(B)` is called once during the step
- **AND** `B.onBodyEntered(C)` is also called during the same step (not on the next step)
- **AND** every `*Entered` callback fired in the step corresponds to a pair that truly overlaps at the moment of dispatch

#### Scenario: Steady-state overlap does not refire entered

- **GIVEN** two `CollisionObject2D` whose shapes overlap and remain overlapping through multiple iterations of the convergence loop within a single step
- **WHEN** `PhysicsSystem.step(tree)` runs
- **THEN** `*Entered` is dispatched at most once per side for that pair across the entire step

#### Scenario: Fail-safe iteration cap logs and exits cleanly

- **GIVEN** a script whose response to `_entered` reintroduces overlap that the next iteration's `_exited` removes, oscillating without converging
- **WHEN** `PhysicsSystem.step(tree)` runs
- **THEN** the engine logs a warning naming `MAX_RESOLUTION_ITERATIONS` and the count of pairs still in transition
- **AND** `step` returns normally (no exception, no infinite loop)

#### Scenario: No-pile-up step costs one iteration

- **GIVEN** a scene whose scripts do not mutate transforms in response to `_entered` / `_exited` (or whose mutations do not produce new overlap transitions)
- **WHEN** `PhysicsSystem.step(tree)` runs
- **THEN** the convergence loop executes exactly one iteration before exiting
