## MODIFIED Requirements

### Requirement: Physics gizmos are registered built-ins toggled via the HUD

The engine SHALL register `VelocityGizmoWidget` and `ContactGizmoWidget` as
built-in widgets during `DebugLayer` auto-insertion, hosted in the
`WorldDebugContainer`, and SHALL expose them as convenience fields on
`DebugRegistry`. Each SHALL appear as its own togglable row in the `DebugHud`.
Both SHALL default to `enabled = false`. The engine SHALL NOT register a
separate `ShapeGizmoWidget` — real collider geometry is drawn by
`ColliderWidget` via its `REAL`/`BOTH` mode (see capability `debug-overlay`).

#### Scenario: The two gizmos are present and world-hosted

- **WHEN** `SceneTree.start()` has completed
- **THEN** the `DebugRegistry` convenience fields for the velocity and contact gizmos SHALL be non-null
- **AND** each gizmo's `parent` SHALL be the `WorldDebugContainer` instance
- **AND** each SHALL appear in `tree.debug.widgets`
- **AND** no `ShapeGizmoWidget` instance SHALL exist under `DebugLayer`

#### Scenario: HUD lists a row per gizmo

- **WHEN** the `DebugHud` is opened
- **THEN** rows for the velocity and contact gizmos SHALL each be present and individually togglable

## REMOVED Requirements

### Requirement: ShapeGizmoWidget draws real collider geometry

**Reason**: O desenho de geometria real foi absorvido pelo `ColliderWidget` através do modo ciclável `ColliderDrawMode { AABB, REAL, BOTH }` (default `REAL`), eliminando o toggle duplicado "Shapes" no HUD. Manter um widget separado para a forma real e outro para o AABB confundia o catálogo sem ganho pedagógico — agora um único `ColliderWidget` mostra forma real, envelope, ou ambos.

**Migration**: Trocar `tree.debug.find<ShapeGizmoWidget>()!!.enabled = true` por `tree.debug.colliders.apply { mode = ColliderDrawMode.REAL; enabled = true }`. Para ver forma real e AABB lado a lado, use `mode = ColliderDrawMode.BOTH`. O campo `DebugRegistry.shapeGizmo` deixa de existir.
