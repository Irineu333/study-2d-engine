## ADDED Requirements

### Requirement: Node supports raising a child to the top of the sibling order

`Node` SHALL provide `raiseChildToTop(child: Node)` that moves an existing
direct child to the end of the parent's children list — the top of the paint and
DFS order among its siblings — without changing `child.parent` nor firing
lifecycle hooks (`onEnter`/`onExit`). If `child` is not a direct child of the
node, the call SHALL be a no-op. The reorder SHALL preserve the relative order of
all other children. The method SHALL respect the same mutation-during-traversal
contract as `addChild`/`removeChild`: when invoked while a `SceneTree` traversal
is in progress (`tree.isMutationDeferred == true`) the reorder MUST be deferred to
a drain point within the same tick rather than mutating the list under iteration;
when invoked outside traversal it MUST take effect immediately. `onDraw` MUST NOT
be used to reorder children.

#### Scenario: Raising a child moves it to the end of the list

- **GIVEN** a parent `Node` with children `[a, b, c]`
- **WHEN** code calls `parent.raiseChildToTop(a)` outside any traversal
- **THEN** `parent.children` is `[b, c, a]`
- **AND** `a.parent` is unchanged and no lifecycle hook fired

#### Scenario: Raising a non-child is a no-op

- **WHEN** code calls `parent.raiseChildToTop(x)` where `x` is not a direct child of `parent`
- **THEN** `parent.children` is unchanged

#### Scenario: Raise during traversal does not corrupt the list

- **WHEN** a `Node`'s `onProcess(dt)` calls `parent.raiseChildToTop(sibling)` while the tree traversal is in progress
- **THEN** no exception is raised
- **AND** the children list is consistent (no partial state), with the reorder visible no later than the next drain point of the same tick
