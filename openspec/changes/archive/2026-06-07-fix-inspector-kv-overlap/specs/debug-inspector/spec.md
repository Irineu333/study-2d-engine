## MODIFIED Requirements

### Requirement: Node Detail Panel

The engine SHALL provide a `NodeInspectorWidget` (`ScreenDebugWidget`) that, in
screen space, draws a read-only detail of the selected node: its type and
`name`, its world transform (when `Node2D`), and its `@Inspect` properties with
current values, sharing the same node-row visualization vocabulary as the tree
view. It is a slave arm: it does not own the selection, derives its `enabled`
from the Inspector master, and SHALL NOT carry a breadcrumb. When there is no
selection it SHALL report empty size (occupy no space).

Each key/value property line SHALL render its value in a column shared by the
whole panel, positioned after the widest property name among the panel's
key/value lines (so the name never overlaps the value), with the column never
narrower than the default. The panel width SHALL account for both the name and
the value of each line.

#### Scenario: Panel lists the selected node's inspect properties

- **WHEN** a node is selected
- **THEN** the panel shows its type and `name`, its world transform when it is
  a `Node2D`, and each `@Inspect` property with its current value

#### Scenario: Properties are read-only

- **WHEN** the panel is shown
- **THEN** it does not provide any means to edit property values

#### Scenario: No breadcrumb in the detail panel

- **WHEN** a node is selected
- **THEN** the detail panel SHALL NOT draw a `root → selected` breadcrumb (the
  tree view conveys the lineage instead)

#### Scenario: Empty when nothing is selected

- **WHEN** the Inspector is enabled and there is no selection
- **THEN** the detail panel reports empty size and draws no body

#### Scenario: Long property name does not overlap its value

- **WHEN** a selected node has an `@Inspect` property whose name is wider than
  the default value column
- **THEN** the value is drawn starting after the name (no overlap) and the
  panel width grows to fit the name plus the value

#### Scenario: Values share a single aligned column

- **WHEN** a node with multiple key/value lines is selected
- **THEN** all values begin at the same horizontal column, positioned after the
  widest property name in the panel
