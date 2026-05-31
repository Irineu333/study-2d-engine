## 1. Math primitive — `Transform.applyInverse`

- [ ] 1.1 Add `fun applyInverse(p: Vec2): Vec2` to `Transform`: `rotate(p - position, -rotation)` then component-wise divide by `scale` (`q.x/scale.x`, `q.y/scale.y`).
- [ ] 1.2 KDoc: exact inverse of `apply`; used to bring a world point into a node's local frame for oriented hit-testing.
- [ ] 1.3 Unit tests: `applyInverse(apply(p)) ≈ p` across translation/scale/rotation cases; matches hand-computed values for a rotated+scaled transform.

## 2. Inspect property enumeration helper

- [ ] 2.1 Add `data class InspectEntry(val displayName: String, val value: Any?)` and `fun inspectProperties(node: Node): List<InspectEntry>` in `com.neoutils.engine.serialization`, reusing the `memberProperties` + `findAnnotation<Inspect>()` + getter pattern; `displayName` = annotation when non-empty, else property name. `SceneLoader` left untouched.
- [ ] 2.2 Unit tests: enumerates `@Inspect` with current values (e.g. a set `position`); excludes `@Transient` fields.

## 3. Pick hit-test in the core loop — `SceneTree.hitTestPick`

- [ ] 3.1 Add `fun hitTestPick(input: Input)` to `SceneTree`, gated on `debug.scenePicker.enabled`: no-op (no walk, no selection change, no `mouseClickConsumed` touch) when disabled.
- [ ] 3.2 On left click not already consumed: DFS from `root` collecting `Node2D` with `localBounds() != null`, **skipping `CanvasLayer` subtrees**; broad-phase filter by `worldBounds().contains(clickWorld)`, confirm by `localBounds().contains(world().applyInverse(clickWorld))`, where `clickWorld = screenToWorld(input.pointerPosition)`.
- [ ] 3.3 Order candidates by DFS draw-order; front-most = last painted. Maintain `(lastPickPoint, cycleIndex)`: fresh point (beyond epsilon) selects front-most and resets index; near-same point advances index modulo candidate count. Write the result into `debug.scenePicker`.
- [ ] 3.4 Set `input.mouseClickConsumed = true` when a pick is performed while enabled.
- [ ] 3.5 Call `hitTestPick(input)` from `GameLoop.tick` immediately after `hitTestUI` and before `tree.process(dt)`.
- [ ] 3.6 Unit tests: runs after `hitTestUI`/before `process`; disabled → no-op (no walk, flag untouched); enabled click selects node under cursor and consumes the click; rotated node hit precisely (inside box selects, empty AABB corner does not); `localBounds == null` never selected; `CanvasLayer` subtree skipped; front-most wins on fresh click; repeated near-same click cycles and wraps; no tree mutation.

## 4. `ScenePickerWidget` — selection owner, breadcrumb + panel

- [ ] 4.1 Create `ScenePickerWidget : ScreenDebugWidget` (`title = "Picker"`, `enabled = false`) holding the selection by instance identity and the cycle state read/written by `hitTestPick`; expose `selected: Node?`.
- [ ] 4.2 Each frame, clear the selection when `selected` is no longer `isLive`/in the tree.
- [ ] 4.3 `drawDebug`: breadcrumb of the path root→selected, then a read-only panel — type (`::class.simpleName`), `name`, world transform (`world().position/rotation/scale`) when `Node2D`, and `@Inspect` props via `inspectProperties` (`displayName = value.toString()`). Fixed line height, anchored in a corner, clipped to surface height with explicit overflow indicator.
- [ ] 4.4 Unit tests: panel lists `@Inspect` of the selection with current values; breadcrumb is the ancestor chain root→selected; `Node2D` shows world transform; selection cleared on detach; read-only (no edit surface); disabled → zero draws.

## 5. `SelectionGizmoWidget` — OBB highlight

- [ ] 5.1 Create `SelectionGizmoWidget : WorldDebugWidget` (`title = "Selection"`, `enabled = false`) reading `debug.scenePicker.selected`.
- [ ] 5.2 `drawDebug`: draw the oriented box = `selected.localBounds().corners()` projected through `selected.world().apply`; nothing when no selection or `localBounds == null`.
- [ ] 5.3 Unit tests: oriented box drawn around the selection (corners match `world().apply`); nothing drawn without a selection.

## 6. Built-in registration + HUD

- [ ] 6.1 Add `scenePicker: ScenePickerWidget` and `selectionGizmo: SelectionGizmoWidget` fields to `DebugRegistry`; register both in `bindLayer` (default `enabled = false`) — picker to the screen container, gizmo to the world container.
- [ ] 6.2 Ensure both surface as toggle rows in `DebugHud`.
- [ ] 6.3 Tests: both built-ins non-null after `start()`; correct parent containers; present in `widgets`; rows in the HUD; disabled → no pick walk and no draws.

## 7. Verification

- [ ] 7.1 Run the full `:engine` suite; confirm green (no collision/UI/serialization regressions).
- [ ] 7.2 Verify invariants: #1 (pick selects, never mutates the tree), #4 (`GameHost` untouched; `hitTestPick` is engine-internal like `hitTestUI`), #6 (`CanvasLayer` skipped).
- [ ] 7.3 `openspec validate debug-scene-picker --strict` passes; review specs↔implementation coherence.
