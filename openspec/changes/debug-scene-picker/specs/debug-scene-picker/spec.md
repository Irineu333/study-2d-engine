## ADDED Requirements

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
the active `Camera2D`.

#### Scenario: Oriented box drawn around the selection

- **WHEN** the gizmo is enabled and a node is selected
- **THEN** it draws the node's oriented box (the corners of `localBounds()`
  projected through `world().apply`)

#### Scenario: Nothing drawn without a selection

- **WHEN** the gizmo is enabled and there is no selection
- **THEN** it draws nothing

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

The `ScenePickerWidget` and `SelectionGizmoWidget` SHALL be auto-registered
built-ins reachable via the `DebugRegistry`, default-disabled, each with a
toggle row in the debug HUD, and SHALL impose no per-frame cost while
disabled.

#### Scenario: Built-ins registered and toggleable

- **WHEN** the tree starts
- **THEN** both widgets are present in the registry's `widgets`, default
  disabled, parented under the correct debug containers (screen for the
  picker, world for the gizmo), and exposed as rows in the HUD

#### Scenario: Zero cost while disabled

- **WHEN** both widgets are disabled
- **THEN** no pick hit-test walk runs and neither widget draws
