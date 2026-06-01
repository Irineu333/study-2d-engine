# engine-core

## ADDED Requirements

### Requirement: Pointer drag consumption
`Input` SHALL expose a per-tick drag-consumption signal, mirroring
`mouseClickConsumed`: it SHALL be reset to not-consumed at the start of each
tick (at the same pipeline point as the click signal) and set when a debug
panel captures a drag. Gameplay drag consumers SHALL be able to read it to
avoid acting on a pointer drag that the debug UI already owns.

#### Scenario: Drag signal is reset each tick
- **WHEN** a new tick begins
- **THEN** the drag-consumption signal reads not-consumed until something sets it

#### Scenario: Captured drag is observable as consumed
- **WHEN** a debug panel is being dragged during a tick
- **THEN** the drag-consumption signal reads consumed for that tick
