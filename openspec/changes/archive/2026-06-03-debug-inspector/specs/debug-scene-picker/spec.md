## REMOVED Requirements

### Requirement: World-Space Node Picking

**Reason**: Superseded by the `debug-inspector` capability, which carries the
identical world-pick behavior under the Inspector tool.
**Migration**: See `debug-inspector` → "World-Space Node Picking".

### Requirement: Front-Most Resolution With Cycling

**Reason**: Superseded by the `debug-inspector` capability.
**Migration**: See `debug-inspector` → "Front-Most Resolution With Cycling".

### Requirement: World-Only Picking Scope

**Reason**: Superseded by the `debug-inspector` capability.
**Migration**: See `debug-inspector` → "World-Only Picking Scope".

### Requirement: Selection Lifetime By Instance Identity

**Reason**: Superseded by the `debug-inspector` capability, which adds tree-row
selection as a second source under the same lifetime rule.
**Migration**: See `debug-inspector` → "Selection Lifetime By Instance Identity".

### Requirement: Selection Gizmo

**Reason**: Superseded by the `debug-inspector` capability, where the gizmo is a
slave arm of the Inspector instead of the picker.
**Migration**: See `debug-inspector` → "Selection Gizmo".

### Requirement: Picker Panel With Breadcrumb And Inspect Properties

**Reason**: Replaced by the Inspector's split into a master tree view and a
slave detail panel; the breadcrumb is dropped because the tree conveys lineage.
**Migration**: See `debug-inspector` → "Node Detail Panel" and "Scene Tree
Navigation View".

### Requirement: Public Inspect Property Enumeration

**Reason**: Superseded by the `debug-inspector` capability, which retains the
`inspectProperties` helper verbatim.
**Migration**: See `debug-inspector` → "Public Inspect Property Enumeration".

### Requirement: Built-In Registration And HUD Rows

**Reason**: Replaced by the Inspector's registration model (tree master is the
single HUD row; detail panel and gizmo are non-HUD slave arms).
**Migration**: See `debug-inspector` → "Inspector Built-In Registration" and
"Inspector Tool Identity And Single Toggle".
