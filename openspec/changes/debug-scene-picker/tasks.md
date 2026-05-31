## 1. Math primitive — `Transform.applyInverse`

- [x] 1.1 Add `fun applyInverse(p: Vec2): Vec2` to `Transform`: `rotate(p - position, -rotation)` then component-wise divide by `scale` (`q.x/scale.x`, `q.y/scale.y`).
- [x] 1.2 KDoc: exact inverse of `apply`; used to bring a world point into a node's local frame for oriented hit-testing.
- [x] 1.3 Unit tests: `applyInverse(apply(p)) ≈ p` across translation/scale/rotation cases; matches hand-computed values for a rotated+scaled transform.

## 2. Inspect property enumeration helper

- [x] 2.1 Add `data class InspectEntry(val displayName: String, val value: Any?)` and `fun inspectProperties(node: Node): List<InspectEntry>` in `com.neoutils.engine.serialization`, reusing the `memberProperties` + `findAnnotation<Inspect>()` + getter pattern; `displayName` = annotation when non-empty, else property name. `SceneLoader` left untouched.
- [x] 2.2 Unit tests: enumerates `@Inspect` with current values (e.g. a set `position`); excludes `@Transient` fields.

## 3. Pick hit-test in the core loop — `SceneTree.hitTestPick`

- [x] 3.1 Add `fun hitTestPick(input: Input)` to `SceneTree`, gated on `debug.scenePicker.enabled`: no-op (no walk, no selection change, no `mouseClickConsumed` touch) when disabled.
- [x] 3.2 On left click not already consumed: DFS from `root` collecting `Node2D` with `localBounds() != null`, **skipping `CanvasLayer` subtrees**; broad-phase filter by `worldBounds().contains(clickWorld)`, confirm by `localBounds().contains(world().applyInverse(clickWorld))`, where `clickWorld = screenToWorld(input.pointerPosition)`.
- [x] 3.3 Order candidates by DFS draw-order; front-most = last painted. Maintain `(lastPickPoint, cycleIndex)`: fresh point (beyond epsilon) selects front-most and resets index; near-same point advances index modulo candidate count. Write the result into `debug.scenePicker`.
- [x] 3.4 Set `input.mouseClickConsumed = true` when a pick is performed while enabled.
- [x] 3.5 Call `hitTestPick(input)` from `GameLoop.tick` immediately after `hitTestUI` and before `tree.process(dt)`.
- [x] 3.6 Unit tests: runs after `hitTestUI`/before `process`; disabled → no-op (no walk, flag untouched); enabled click selects node under cursor and consumes the click; rotated node hit precisely (inside box selects, empty AABB corner does not); `localBounds == null` never selected; `CanvasLayer` subtree skipped; front-most wins on fresh click; repeated near-same click cycles and wraps; no tree mutation.

## 4. `ScenePickerWidget` — selection owner, breadcrumb + panel

- [x] 4.1 Create `ScenePickerWidget : ScreenDebugWidget` (`title = "Picker"`, `enabled = false`) holding the selection by instance identity and the cycle state read/written by `hitTestPick`; expose `selected: Node?`.
- [x] 4.2 Each frame, clear the selection when `selected` is no longer `isLive`/in the tree.
- [x] 4.3 `drawDebug`: breadcrumb of the path root→selected, then a read-only panel — type (`::class.simpleName`), `name`, world transform (`world().position/rotation/scale`) when `Node2D`, and `@Inspect` props via `inspectProperties` (`displayName = value.toString()`). Fixed line height, anchored in a corner, clipped to surface height with explicit overflow indicator.
- [x] 4.4 Unit tests: panel lists `@Inspect` of the selection with current values; breadcrumb is the ancestor chain root→selected; `Node2D` shows world transform; selection cleared on detach; read-only (no edit surface); disabled → zero draws.

## 5. `SelectionGizmoWidget` — OBB highlight

- [x] 5.1 Create `SelectionGizmoWidget : WorldDebugWidget` (`title = "Selection"`) reading `debug.scenePicker.selected`; its `enabled` is **derived** from `scenePicker.enabled` (no independent state/setter), so the gizmo is the world-space arm of the single picker tool.
- [x] 5.2 `drawDebug`: draw the oriented box = `selected.localBounds().corners()` projected through `selected.world().apply`; nothing when no selection or `localBounds == null`.
- [x] 5.3 Unit tests: oriented box drawn around the selection when the picker is enabled (corners match `world().apply`); nothing drawn without a selection; nothing drawn when the picker is disabled.

## 6. Built-in registration + HUD

- [x] 6.1 Add `scenePicker: ScenePickerWidget` and `selectionGizmo: SelectionGizmoWidget` fields to `DebugRegistry`; in `bindLayer` register the picker through the screen container (so it joins `widgets`/HUD) and attach the gizmo straight to the world container **without** adding it to `widgets` — it is controlled via the picker.
- [x] 6.2 Ensure the picker surfaces as a single toggle row in `DebugHud`; the gizmo has no row of its own.
- [x] 6.3 Tests: picker present in `widgets` and as one HUD row under the screen container; gizmo reachable via `debug.selectionGizmo`, parented under the world container, but absent from `widgets` and from the HUD rows; disabled → no pick walk and no draws (panel nor gizmo).

## 7. Verification

- [x] 7.1 Run the full `:engine` suite; confirm green (no collision/UI/serialization regressions).
- [x] 7.2 Verify invariants: #1 (pick selects, never mutates the tree), #4 (`GameHost` untouched; `hitTestPick` is engine-internal like `hitTestUI`), #6 (`CanvasLayer` skipped).
- [x] 7.3 `openspec validate debug-scene-picker --strict` passes; review specs↔implementation coherence.

## 8. Manual-test follow-ups

- [x] 8.1 Pong manual test confirmed: ball selects with correct OBB; paddle OBB appears offset (the known `RectangleShape2D` anchoring divergence resolved by the separate `center-rectangle-shape` change, not a picker bug) and `CanvasLayer` HUD labels are correctly non-pickable (world-only scope, invariant #6).
- [x] 8.2 Demos `SpawnerDemo` (scene 3) leaked the picker's claimed click: it edge-detected raw `isMouseDown` instead of `wasMouseClicked`, bypassing `mouseClickConsumed`. Switched to `input.wasMouseClicked(MouseButton.Left)` so the spawn honors UI/picker consumption (same one-spawn-per-click behavior). Swept all games — it was the only consumer using the raw pattern.
- [x] 8.3 UX: `Picker` and `Selection` showed as two separate HUD toggles for what is one tool. Folded into a single `Picker` toggle — `SelectionGizmoWidget.enabled` now derives from `scenePicker.enabled` and the gizmo is auto-inserted under the world container but kept out of `widgets`/HUD (Decision 4 + the Selection-Gizmo / Built-In-Registration requirements updated to match).
