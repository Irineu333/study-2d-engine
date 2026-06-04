# debug-inspector Specification

## Purpose

Fornecer uma ferramenta de depuração de scene graph — o **Inspector** — que une
uma view tree navegável, um painel de detalhe do nó selecionado, um gizmo
world-space de seleção e o pick por click no mundo, sob um único toggle na HUD.
Substitui o antigo `debug-scene-picker`, promovendo o picker a uma ferramenta com
hierarquia navegável e seleção compartilhada entre os braços (mestre/escravos).

## Requirements

### Requirement: Inspector Tool Identity And Single Toggle

The engine SHALL provide a debug **Inspector** tool reachable via the
`DebugRegistry` field `inspector`, exposed as a **single** togglable row in the
debug HUD. The Inspector is composed of arms — a master scene-tree view
(`SceneTreeWidget`), a slave detail panel (`NodeInspectorWidget`), a world-space
selection gizmo (`SelectionGizmoWidget`), and world-click picking — that share
one selection and turn on and off together. The `SceneTreeWidget` SHALL be the
master: it owns the `enabled` state and the HUD row; the other arms SHALL derive
their `enabled` from it and SHALL NOT add their own HUD rows. The tool SHALL be
default-disabled and SHALL impose no per-frame cost while disabled.

#### Scenario: Inspector exposed as one HUD row

- **WHEN** the tree starts
- **THEN** `tree.debug.inspector` SHALL be non-null, default disabled, and SHALL
  appear as exactly one row in the HUD

#### Scenario: Arms follow the master toggle

- **WHEN** the Inspector is disabled
- **THEN** the tree view, the detail panel, and the selection gizmo SHALL all
  draw nothing, and no pick hit-test walk SHALL run

#### Scenario: Zero cost while disabled

- **WHEN** the Inspector is disabled
- **THEN** no scene-tree enumeration, no pick hit-test, and no panel draw SHALL
  occur

### Requirement: Scene Tree Navigation View

The engine SHALL provide a `SceneTreeWidget` (`ScreenDebugWidget`) that draws,
in screen pixels, the live hierarchy of the scene graph as an indented list of
node rows (one row per node, indented by depth), rooted at `SceneTree.root`. The
view SHALL be the master arm: it owns the selection (`selected` plus
`select(node)`), is the HUD-registered row, and carries the full window chrome
(collapse and close). The tree SHALL be recomputed each frame so it reflects a
mutating scene graph.

#### Scenario: Tree lists the scene graph hierarchy

- **WHEN** the Inspector is enabled
- **THEN** the tree view SHALL show one row per reachable node, indented by its
  depth, in DFS order from the root

#### Scenario: Tree reflects mutation

- **WHEN** a node is added to or removed from the tree while the Inspector is
  enabled
- **THEN** the next frame's tree view SHALL include or omit the corresponding
  row

#### Scenario: Selected row is highlighted

- **WHEN** a node is selected
- **THEN** its row in the tree view SHALL be visually highlighted distinctly
  from the other rows

#### Scenario: Closing the tree disables the Inspector

- **WHEN** the user activates the tree view's close (`[x]`) control
- **THEN** the Inspector SHALL become disabled (all arms turn off), reopenable
  from the HUD

#### Scenario: Collapsing the tree hides the hierarchy

- **WHEN** the user activates the tree view's collapse (`[_]`) control
- **THEN** the hierarchy body SHALL be hidden while the header remains, and
  expanding SHALL restore it

### Requirement: Tree Excludes The Debug Layer

The scene-tree view SHALL NOT include the engine-inserted `DebugLayer` (the node
named `"__debug"`) nor any of its descendants, so the hierarchy shows only game
content and not the debug plumbing.

#### Scenario: Debug layer is absent from the tree

- **WHEN** the Inspector is enabled on any started tree
- **THEN** no row for `"__debug"` or any of its descendants SHALL appear in the
  tree view

### Requirement: Tree Fits Vertically With Overflow

The tree view SHALL fit the available screen height: when the hierarchy has more
rows than fit, it SHALL show the rows that fit and collapse the remaining tail
into a single overflow row reporting how many rows were hidden, rather than
overflowing the screen. The view SHALL NOT silently drop rows without indicating
the count.

#### Scenario: Overflow tail is summarized

- **WHEN** the hierarchy has more rows than fit the available height
- **THEN** the tree view SHALL draw the rows that fit followed by one overflow
  row indicating the number of hidden rows

### Requirement: Select By Tree Row Click

A left click on a node's row in the tree view SHALL select that node via
`select(node)`, setting it as the selection and resetting any world-pick cycling
state, so a tree selection is not treated as a geometric pick. The click SHALL
be consumed as UI so it does not fall through to world picking or gameplay.

#### Scenario: Clicking a row selects the node

- **WHEN** the Inspector is enabled and the user left-clicks a node's row
- **THEN** that node SHALL become the selection, and the detail panel and gizmo
  SHALL update to it

#### Scenario: Tree click does not re-pick the world

- **WHEN** a tree row is clicked
- **THEN** the click SHALL be consumed and `SceneTree.hitTestPick` SHALL NOT
  re-resolve a world selection from the same click

#### Scenario: Tree selection resets pick cycling

- **WHEN** a node is selected by a tree-row click and the user then clicks the
  same world point twice
- **THEN** the world-pick cycling SHALL start fresh from the front-most
  candidate, not continue a cycle from before the tree selection

### Requirement: World-Space Node Picking

The Inspector SHALL select the `Node2D` under the pointer by an oriented
hit-test: the world-space click is projected into each candidate's local
frame and tested against its `localBounds()`, so rotated nodes are picked
precisely (not by their loose world-space AABB). The AABB (`worldBounds()`)
MAY be used only as a broad-phase filter before the oriented test.

#### Scenario: Click selects the node under the cursor

- **WHEN** the Inspector is enabled and a left click lands inside a node's
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
- **THEN** the Inspector never selects it geometrically

#### Scenario: Picking does not mutate the scene tree

- **WHEN** any pick occurs
- **THEN** no node is added, removed, or reparented as a result

### Requirement: Front-Most Resolution With Cycling

When multiple nodes overlap under the pointer, world picking SHALL select the
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

World picking SHALL hit-test only world-space nodes, skipping `CanvasLayer`
subtrees (screen-space UI), consistent with the `treeBounds` boundary.

#### Scenario: CanvasLayer subtrees are skipped

- **WHEN** the click overlaps a `Node2D` that lives under a `CanvasLayer`
- **THEN** world picking does not select it

### Requirement: Selection Lifetime By Instance Identity

The selection SHALL reference the selected `Node` by instance identity and
SHALL be cleared when that node is no longer live in the tree, regardless of
whether it was selected by world pick or by a tree-row click.

#### Scenario: Selection cleared when the node detaches

- **WHEN** the selected node is removed from the tree (no longer `isLive`)
- **THEN** the selection becomes empty

### Requirement: Selection Gizmo

The engine SHALL provide a `SelectionGizmoWidget` (`WorldDebugWidget`) that
draws the oriented bounding box of the selected node in world space, following
the active `Camera2D`. The gizmo is a slave arm of the Inspector: its visibility
SHALL derive from the Inspector being enabled (it has no independent toggle), so
the Inspector and the gizmo turn on and off together.

#### Scenario: Oriented box drawn around the selection

- **WHEN** the Inspector is enabled and a node is selected
- **THEN** the gizmo draws the node's oriented box (the corners of
  `localBounds()` projected through `world().apply`)

#### Scenario: Nothing drawn without a selection

- **WHEN** the Inspector is enabled and there is no selection
- **THEN** the gizmo draws nothing

#### Scenario: Gizmo follows the Inspector toggle

- **WHEN** the Inspector is disabled
- **THEN** the gizmo draws nothing regardless of any prior selection

### Requirement: Node Detail Panel

The engine SHALL provide a `NodeInspectorWidget` (`ScreenDebugWidget`) that, in
screen space, draws a read-only detail of the selected node: its type and
`name`, its world transform (when `Node2D`), and its `@Inspect` properties with
current values, sharing the same node-row visualization vocabulary as the tree
view. It is a slave arm: it does not own the selection, derives its `enabled`
from the Inspector master, and SHALL NOT carry a breadcrumb. When there is no
selection it SHALL report empty size (occupy no space).

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

### Requirement: Inspector Built-In Registration

The Inspector arms SHALL be auto-inserted built-ins. The `SceneTreeWidget`
(master) SHALL be registered under the screen debug container and SHALL appear
in `DebugRegistry.widgets` and as the single Inspector HUD row. The
`NodeInspectorWidget` (slave detail panel) SHALL be auto-inserted under the
screen debug container and the `SelectionGizmoWidget` under the world debug
container, both reachable via the registry but ABSENT from `DebugRegistry.widgets`
and from the HUD rows — they are controlled entirely through the master.

#### Scenario: Master registered as the single toggle

- **WHEN** the tree starts
- **THEN** the `SceneTreeWidget` SHALL be present in `tree.debug.widgets`,
  default disabled, parented under the screen debug container, and exposed as
  one HUD row

#### Scenario: Slave arms auto-inserted but not separate rows

- **WHEN** the tree starts
- **THEN** the `NodeInspectorWidget` SHALL be parented under the screen debug
  container and the `SelectionGizmoWidget` under the world debug container, both
  reachable via the registry, but neither SHALL appear in `tree.debug.widgets`
  nor add a HUD row
