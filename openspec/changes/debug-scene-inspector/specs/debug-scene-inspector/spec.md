## ADDED Requirements

### Requirement: Public @Inspect property enumeration helper

The engine SHALL expose a public helper in
`com.neoutils.engine.serialization` that, given a `Node`, returns its
`@Inspect` properties as ordered `(displayName, value)` entries, where
`displayName` is the annotation's `displayName` when non-empty or the
property name otherwise, and `value` is the property's current runtime
value read via its getter. The helper SHALL reuse the same reflection
approach already used for serialization (`memberProperties` +
`findAnnotation<Inspect>()`).

#### Scenario: Helper lists a node's @Inspect properties with current values

- **GIVEN** a `Node2D` whose `@Inspect` `position` has been set to `(5, 7)`
- **WHEN** the helper is called on that node
- **THEN** the returned entries SHALL include an entry for `position` whose value reflects `(5, 7)`

#### Scenario: Non-@Inspect fields are excluded

- **GIVEN** a node with a `@Transient` runtime field
- **WHEN** the helper is called
- **THEN** the `@Transient` field SHALL NOT appear in the returned entries

### Requirement: SceneInspectorWidget lists the live tree

`SceneInspectorWidget` SHALL extend `ScreenDebugWidget` (default
`enabled = false`). When enabled, it SHALL draw, from `tree.root`, an
indented list of the live hierarchy with each node's `name` and type
(`::class.simpleName`), indentation reflecting depth. The list SHALL
reflect runtime mutations — nodes added or removed during play SHALL appear
or disappear on the next frame. When the list exceeds the available height,
the widget SHALL draw an explicit truncation indicator (e.g. `"+N more"`)
rather than silently dropping rows.

#### Scenario: List reflects added and removed nodes

- **GIVEN** the inspector enabled and a tree with a known root
- **WHEN** a new child node is added to the tree at runtime
- **THEN** the next rendered list SHALL include a row for that node
- **WHEN** that node is later removed
- **THEN** the next rendered list SHALL NOT include a row for it

#### Scenario: Overflow is indicated, not silently truncated

- **GIVEN** the inspector enabled and a tree with more nodes than fit the surface height
- **WHEN** a frame is rendered
- **THEN** a truncation indicator SHALL be drawn showing how many rows were omitted

#### Scenario: Disabled inspector draws nothing

- **GIVEN** `SceneInspectorWidget.enabled = false`
- **WHEN** a frame is rendered against a recording `Renderer`
- **THEN** zero draw calls SHALL be attributed to the widget

### Requirement: Clicking a row selects a node and shows its properties

`SceneInspectorWidget` SHALL handle mouse clicks during `process` by reading
`tree.input` and mapping the click position to the list row under the
cursor, selecting that node by instance identity. For the selected node, the
widget SHALL display its type, `name`, its world transform when it is a
`Node2D` (world position, rotation, scale), and its `@Inspect` properties
with current values (via the enumeration helper). When the selected node is
no longer live (detached from the tree), the selection SHALL be cleared.

#### Scenario: Click selects the node under the cursor

- **GIVEN** the inspector enabled with a multi-node tree
- **WHEN** the user clicks the row for a specific node
- **THEN** that node SHALL become the selected node
- **AND** the property panel SHALL list that node's `@Inspect` properties with their current values

#### Scenario: Selected Node2D shows its world transform

- **GIVEN** a `Node2D` selected in the inspector
- **WHEN** a frame is rendered
- **THEN** the panel SHALL display the node's world position, rotation, and scale

#### Scenario: Selection clears when the node detaches

- **GIVEN** a selected node
- **WHEN** that node is removed from the tree
- **THEN** by the next frame the selection SHALL be cleared and the property panel SHALL show no node

### Requirement: SceneInspectorWidget is a registered built-in

The engine SHALL register `SceneInspectorWidget` as a built-in widget during
`DebugLayer` auto-insertion, hosted in the screen-space container, exposed as
a convenience field on `DebugRegistry`, and listed as a togglable row in the
`DebugHud`. It SHALL default to `enabled = false`.

#### Scenario: Inspector is present and screen-hosted

- **WHEN** `SceneTree.start()` has completed
- **THEN** the `DebugRegistry` convenience field for the inspector SHALL be non-null
- **AND** the inspector's `parent` SHALL be the screen-space debug container
- **AND** it SHALL appear in `tree.debug.widgets` and as a HUD row
