# debug-scene-picker Specification

## Purpose
TBD - created by archiving change debug-scene-picker. Update Purpose after archive.
## Requirements
### Requirement: World-Space Node Picking

The picker SHALL select the `Node2D` under the pointer by an oriented
hit-test: the world-space click is projected into each candidate's local
frame and tested against its `localBounds()`, so rotated nodes are picked
precisely (not by their loose world-space AABB). The AABB (`worldBounds()`)
MAY be used only as a broad-phase filter before the oriented test.

#### Scenario: Click selects the node under the cursor

- **WHEN** the picker is enabled and a left click lands inside a node's
  oriented box
- **THEN** that node becomes the selection

#### Scenario: Rotated node hit precisely

- **WHEN** a node is rotated and the click lands inside its rotated box but
  outside the rotated box yet inside the enclosing AABB (an empty AABB corner)
- **THEN** the rotated node is selected for a click inside its box, and is NOT
  selected for a click in the empty AABB corner

#### Scenario: Nodes without extent are not pickable

- **WHEN** a node has `localBounds() == null` (a transform-only pivot or a
  plain `Node`)
- **THEN** the picker never selects it geometrically

#### Scenario: Picking does not mutate the scene tree

- **WHEN** any pick occurs
- **THEN** no node is added, removed, or reparented as a result

### Requirement: Front-Most Resolution With Cycling

When multiple nodes overlap under the pointer, the picker SHALL select the
front-most (last-drawn in DFS order) on a fresh click, and SHALL cycle
through the stacked candidates on repeated clicks at approximately the same
point.

#### Scenario: Front-most wins on a fresh click

- **WHEN** several nodes overlap the click point and the click point differs
  from the previous one
- **THEN** the front-most (last-painted) node is selected

#### Scenario: Repeated click cycles to the node behind

- **WHEN** the click point is approximately unchanged (within an epsilon) and
  the user clicks again
- **THEN** the next candidate behind the current one is selected, wrapping
  around after the last

### Requirement: World-Only Picking Scope

The picker SHALL hit-test only world-space nodes, skipping `CanvasLayer`
subtrees (screen-space UI), consistent with the `treeBounds` boundary.

#### Scenario: CanvasLayer subtrees are skipped

- **WHEN** the click overlaps a `Node2D` that lives under a `CanvasLayer`
- **THEN** the picker does not select it

### Requirement: Selection Lifetime By Instance Identity

The selection SHALL reference the selected `Node` by instance identity and
SHALL be cleared when that node is no longer live in the tree.

#### Scenario: Selection cleared when the node detaches

- **WHEN** the selected node is removed from the tree (no longer `isLive`)
- **THEN** the selection becomes empty

### Requirement: Selection Gizmo

The engine SHALL provide a `SelectionGizmoWidget` (`WorldDebugWidget`) that
draws the oriented bounding box of the selected node in world space, following
the active `Camera2D`. The gizmo is the world-space arm of the single picker
tool: its visibility SHALL derive from the picker being enabled (it has no
independent toggle), so the picker and the gizmo turn on and off together.

#### Scenario: Oriented box drawn around the selection

- **WHEN** the picker is enabled and a node is selected
- **THEN** the gizmo draws the node's oriented box (the corners of
  `localBounds()` projected through `world().apply`)

#### Scenario: Nothing drawn without a selection

- **WHEN** the picker is enabled and there is no selection
- **THEN** the gizmo draws nothing

#### Scenario: Gizmo follows the picker toggle

- **WHEN** the picker is disabled
- **THEN** the gizmo draws nothing regardless of any prior selection

### Requirement: Picker Panel With Breadcrumb And Inspect Properties

The engine SHALL provide a `ScenePickerWidget` (`ScreenDebugWidget`) that owns
the selection and, in screen space, draws a breadcrumb of the selected node's
path (root→selected) and a read-only panel of its type, `name`, world
transform (when `Node2D`), and `@Inspect` properties with current values.

#### Scenario: Panel lists the selected node's inspect properties

- **WHEN** a node is selected
- **THEN** the panel shows its type and `name`, its world transform when it is
  a `Node2D`, and each `@Inspect` property with its current value

#### Scenario: Breadcrumb shows the path to the selection

- **WHEN** a node is selected
- **THEN** the panel shows the chain of ancestors from the root to the
  selected node

#### Scenario: Properties are read-only

- **WHEN** the panel is shown
- **THEN** it does not provide any means to edit property values

### Requirement: Public Inspect Property Enumeration

The engine SHALL expose a public helper that enumerates a node's `@Inspect`
properties as `(displayName, value)` entries, reusing the reflection pattern
of the loader, where `displayName` is the annotation value when non-empty and
the property name otherwise.

#### Scenario: Enumerates inspect properties with current values

- **WHEN** `inspectProperties(node)` is called
- **THEN** it returns one entry per `@Inspect` property with the property's
  current value

#### Scenario: Excludes transient runtime state

- **WHEN** `inspectProperties(node)` is called on a node with `@Transient`
  fields
- **THEN** those fields are not included in the result

### Requirement: Built-In Registration And HUD Rows

The picker tool SHALL be an auto-inserted built-in reachable via the
`DebugRegistry`, default-disabled, exposed as a **single** toggle row in the
debug HUD (the `ScenePickerWidget`), and SHALL impose no per-frame cost while
disabled. The `SelectionGizmoWidget` SHALL be auto-inserted under the world
debug container so it draws in the world pass, but SHALL NOT appear as its own
HUD row nor in the registry's `widgets` list — it is controlled entirely
through the picker.

#### Scenario: Picker registered as a single toggle

- **WHEN** the tree starts
- **THEN** the `ScenePickerWidget` is present in the registry's `widgets`,
  default disabled, parented under the screen debug container, and exposed as
  one row in the HUD

#### Scenario: Gizmo auto-inserted but not a separate row

- **WHEN** the tree starts
- **THEN** the `SelectionGizmoWidget` is parented under the world debug
  container, is reachable via `DebugRegistry.selectionGizmo`, but is absent
  from the registry's `widgets` list and from the HUD rows

#### Scenario: Zero cost while disabled

- **WHEN** the picker is disabled
- **THEN** no pick hit-test walk runs and neither the panel nor the gizmo
  draws

