## 1. Math primitives

- [x] 1.1 Add `Transform.apply(p: Vec2): Vec2` = `position + rotate(Vec2(scale.x*p.x, scale.y*p.y), rotation)`
- [x] 1.2 Add `Rect.corners(): List<Vec2>` returning `[TL, TR, BR, BL]`
- [x] 1.3 Unit tests: `Transform.apply` (translation+scale+rotation cases), `Rect.corners` order

## 2. Node2D bounds contract

- [x] 2.1 Add `open fun localBounds(): Rect?` to `Node2D` with default `null`
- [x] 2.2 Add `final fun worldBounds(): Rect?` deriving `AABB(world().apply(c) for c in localBounds().corners())`, `null` when `localBounds()` is `null`
- [x] 2.3 Add `final fun treeBounds(): Rect?` unioning `worldBounds()` of self + descendants via DFS, **not descending into `CanvasLayer`**, `null` when empty
- [x] 2.4 KDoc on all three: local/orientable vs world-AABB vs tree-AABB; `null` = pivot without extent; OBB is composed by consumers, not a method
- [x] 2.5 Unit tests: plain Node2D → null; worldBounds composes translation/scale; rotated worldBounds is enclosing AABB; treeBounds unions children and stops at CanvasLayer; empty subtree → null

## 3. TextMeasurer SPI

- [x] 3.1 Declare `interface TextMeasurer { fun measureText(text: String, size: Float): Vec2 }` in `:engine` (no render-framework types)
- [x] 3.2 Add `var textMeasurer: TextMeasurer? = null` to `SceneTree`
- [x] 3.3 Unit test: `SceneTree` defaults `textMeasurer` to `null`; engine module references only `:engine` math types in `TextMeasurer`

## 4. Leaf overrides — visual

- [x] 4.1 `Panel.localBounds()` → `Rect(Vec2.ZERO, size)`
- [x] 4.2 `ColorRect.localBounds()` → `Rect(Vec2.ZERO, size)`
- [x] 4.3 `Circle2D.localBounds()` → `Rect(Vec2(-radius, -radius), Vec2(2*radius, 2*radius))`
- [x] 4.4 `Label.localBounds()` → `Rect(Vec2.ZERO, tree.textMeasurer.measureText(text, size))` when reachable, else `null`
- [x] 4.5 Unit tests for 4.1–4.4 including Label-with-fake-measurer (non-null even when never drawn) and Label-without-measurer (null)

## 5. Button rect rewrite

- [x] 5.1 Override `Button.localBounds()` → `Rect(Vec2.ZERO, size)`
- [x] 5.2 Rewrite `Button.screenRect()` to derive from `localBounds()` + `world()` (equivalent to `worldBounds()`), respecting rotation; remove the scale-only computation
- [x] 5.3 Unit tests: unrotated rect equals translated+scaled size; rotated rect is the enclosing AABB; hit-test registers a click inside the rotated rect

## 6. Collision bounds bridge

- [x] 6.1 Add `fun localBounds(): Rect` to `Shape2D`: `RectangleShape2D` → `Rect(-size/2, size)`; `CircleShape2D` → `Rect(Vec2(-radius,-radius), Vec2(2*radius,2*radius))` (no world scale)
- [x] 6.2 Override `CollisionShape2D.localBounds()` → `shape?.localBounds()`, `null` when `shape == null` or `disabled`
- [x] 6.3 Refactor `obbCorners`/`worldCorners` in `Shape2D.kt` to reuse `Transform.apply` without behavior change
- [x] 6.4 Unit tests: RectangleShape2D centered local bounds; `CollisionObject2D.treeBounds()` encloses child shape via recursion (no method on CollisionObject2D); disabled/shapeless → null; full physics suite still green after 6.3

## 7. Backend TextMeasurer wiring — Skiko

- [x] 7.1 Implement Skiko `TextMeasurer` (Skia `Font` + `TextLine`), off-frame, matching `SkikoRenderer.measureText`
- [x] 7.2 Wire it into `SceneTree.textMeasurer` at Skiko host startup, before the first frame
- [x] 7.3 Test/verify: Skiko measurer matches renderer; a `Label` reports non-null `localBounds()` after startup

## 8. Backend TextMeasurer wiring — LWJGL

- [x] 8.1 Implement LWJGL `TextMeasurer` (`nvgTextBounds` + `nvgTextMetrics`), callable off-frame, matching `LwjglRenderer.measureText`
- [x] 8.2 Wire it into `SceneTree.textMeasurer` at LWJGL host startup, before the first frame
- [x] 8.3 Verify via `:games:demos` LWJGL entrypoint: measurer non-null and `Label.localBounds()` non-null _(manual run confirmed)_

## 9. Verification

- [x] 9.1 Run full engine + backend test suites; confirm no collision/UI regressions
- [x] 9.2 Verify invariants: `:engine` has no render-framework leak from `TextMeasurer` (#2); both backends implement it (#4); `treeBounds` stops at `CanvasLayer` (#6); `localBounds` is `open` (#1)
- [x] 9.3 `openspec validate node-local-bounds` passes
