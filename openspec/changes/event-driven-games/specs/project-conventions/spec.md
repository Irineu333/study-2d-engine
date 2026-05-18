## ADDED Requirements

### Requirement: CLAUDE.md documents signals as engine primitive

The `CLAUDE.md` SHALL include guidance on signals: when to prefer them over direct references (decoupled communication, event-driven flows), how connections are created and disconnected, and the synchronous-same-thread emission contract. Direct references MUST still be presented as the simpler default when coupling is acceptable.

#### Scenario: Signals section is present

- **WHEN** `CLAUDE.md` is opened after this change
- **THEN** a "Signals" (or equivalently titled) subsection exists under the conventions or architecture area
- **AND** it states the connection/disconnect contract and the synchronous emission model

### Requirement: CLAUDE.md documents RenderMode and tick-on-demand

The `CLAUDE.md` SHALL describe `Scene.renderMode`, the two modes (`Continuous`, `OnDemand`), the wake conditions for `OnDemand` (queued input, `requestRender()`, active animation), and the DX override (`Debug.renderModeOverride` + `F8` shortcut).

#### Scenario: RenderMode section is present and accurate

- **WHEN** a developer reads the section
- **THEN** the three wake conditions are listed
- **AND** the F8 debug override is documented

### Requirement: Roadmap reflects post-change state

The `CLAUDE.md` roadmap SHALL be updated to reflect the new state: `engine-foundation` archived, `event-driven-games` active or just completed, editor change still planned. Status labels MUST be unambiguous.

#### Scenario: Roadmap labels match repository state

- **WHEN** the roadmap section is read at the end of this change
- **THEN** `engine-foundation` is marked as completed/archived
- **AND** `event-driven-games` is marked according to its current status (active during implementation, completed at archive time)
- **AND** the editor change appears as a planned placeholder

### Requirement: Invariants section reflects new primitives

The architectural invariants section in `CLAUDE.md` SHALL be amended to record the additions of this change while preserving the existing invariants. Specifically: (a) signals as preferred mechanism for decoupled communication, (b) `Scene.renderMode` default remains `Continuous`, (c) pointer event hit-test lives in the input system, not the physics system, (d) all additions in this change are aditive — Pong continues to work without source modifications.

#### Scenario: Invariants section enumerates the new clauses

- **WHEN** `CLAUDE.md` is read after this change
- **THEN** the invariants section explicitly mentions the four clauses above
- **AND** the original five invariants from `engine-foundation` remain visible
