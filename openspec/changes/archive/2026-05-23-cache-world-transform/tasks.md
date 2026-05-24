## 1. Cache field and lazy read on Node2D

- [x] 1.1 Add `@Transient private var cachedWorldTransform: Transform? = null` to `Node2D`
- [x] 1.2 Extract `private fun nearestNode2DAncestor(): Node2D?` that walks `parent` chain and returns the first `Node2D` (or `null` if none)
- [x] 1.3 Rewrite `worldTransform()` to short-circuit on cache hit, otherwise compute via `nearestNode2DAncestor()?.worldTransform()?.compose(transform) ?: transform` and store the result in `cachedWorldTransform` before returning

## 2. Invalidation on local mutation

- [x] 2.1 Convert `var transform: Transform = Transform()` in `Node2D` to a property with a custom setter that assigns `field = value` and then calls `invalidateWorldTransformRecursive()`
- [x] 2.2 Implement `internal fun invalidateWorldTransformRecursive()` in `Node2D` that sets `cachedWorldTransform = null` on `this` and walks descendants
- [x] 2.3 Implement the descendant walk so it traverses `children` regardless of node type, invalidates `cachedWorldTransform` on every `Node2D` encountered, and continues recursing through both `Node2D` and non-`Node2D` children to reach deeper `Node2D` descendants

## 3. Invalidation on hierarchy change

- [x] 3.1 In `Node.applyAdd(child)`, after `child.parent = this; _children.add(child)`, call `(child as? Node2D)?.invalidateWorldTransformRecursive()`
- [x] 3.2 In `Node.applyRemove(child)`, after `child.parent = null`, call `(child as? Node2D)?.invalidateWorldTransformRecursive()`
- [x] 3.3 Verify that the existing deferred-mutation paths (`pendingAdd`/`pendingRemove` drained via `drainPending`) end up routing through `applyAdd`/`applyRemove`, so no additional hook is needed in the deferred path

## 4. Tests for cache correctness

- [x] 4.1 Add `WorldTransformCacheTest` under `engine/src/test/kotlin/com/neoutils/engine/scene/`
- [x] 4.2 Test: two consecutive `worldTransform()` calls without mutation return equal `Transform`s (regression for cache returning stale value never)
- [x] 4.3 Test: after `parent.transform = parent.transform.copy(position = ...)`, `parent.worldTransform()` and `child.worldTransform()` both reflect the new position
- [x] 4.4 Test: with a hierarchy `grandparent (Node2D) → middle (raw Node) → grandchild (Node2D)`, reassigning `grandparent.transform` causes `grandchild.worldTransform()` to reflect the new value on the next call
- [x] 4.5 Test: after computing `child.worldTransform()` under parent `P1`, removing from `P1` and adding to `P2` (which has a different transform) yields a new `child.worldTransform()` consistent with `P2`
- [x] 4.6 Test: reassigning a node's local transform does not affect siblings' cached world transforms (regression for over-invalidation)
- [x] 4.7 Test: `SceneLoader.save(scene)` JSON does not include the cached world transform field (use `kotlinx.serialization` JSON output assertion)

## 5. Tests for cache observability

- [~] 5.1 Add a test that counts ancestor walks: subclass `Node2D` in test only, or use a counter on a test ancestor's getter for `transform`, to assert that the second consecutive `worldTransform()` call does not trigger ancestor traversal — superseded by 5.2 (reflection variant chosen to avoid intrusive test-only subclass)
- [x] 5.2 Alternative if 5.1 proves intrusive: assert via reflection / package-private access that `cachedWorldTransform` is non-null after the first read and unchanged after the second

## 6. Existing-test regression sweep

- [x] 6.1 Run `./gradlew :engine:test` and confirm `WorldTransformTest` and `TransformComposeTest` still pass unchanged
- [x] 6.2 Run `./gradlew :games:pong:run`, play one rally to first paddle/ball collision and one goal — observe no behavioral regression and `F1`/`F2` overlays still work
- [x] 6.3 Run `./gradlew :games:tictactoe:run`, complete one match — confirm no regression
- [x] 6.4 Run `./gradlew :games:demos:run`, exercise demos `1`, `2`, `3` — confirm spawner + trap (the worst case for collider count) still removes/adds correctly and that hierarchy invalidation in demo `1`/`2` reflects parent rotation/scale changes

## 7. Documentation

- [x] 7.1 Update KDoc on `Node2D.worldTransform()` to mention the cache, its invalidation triggers (local `transform` assignment, reparent, ancestor `transform` assignment), and its scope (per-node, runtime-only, not serialized)
- [x] 7.2 Update KDoc on `Node2D.transform` setter to mention that assignment invalidates `worldTransform()` cache on this node and all descendants
- [x] 7.3 Add a one-paragraph note to `CLAUDE.md` under "Architectural Invariants" or as a new "Performance Notes" subsection explaining that `worldTransform()` is cached and what invalidates it — useful for future contributors who add new mutation paths

## 8. Collision stress demo (trial-by-fire)

- [x] 8.1 Add a new demo scene to `:games:demos` (bound to key `4`) that spawns a configurable population of `BoxCollider`-bearing nodes bouncing inside the viewport with axis-aligned velocity and reflecting off the walls on collision. Initial population set to 30 balls — see `design.md` §Results for why a smaller N was chosen over the original 200+ target.
- [x] 8.2 Wire the demo so colliders are real children of the scene (not just visual particles) and collisions actually trigger `onCollide` (e.g., color flip on contact) — this exercises the broad phase O(N²) loop end-to-end and confirms the cache benefit
- [x] 8.3 Add an on-screen counter overlay (reuse `Renderer.drawText` / `measureText`) showing current collider count and instant FPS, so the speedup is visually legible
- [x] 8.4 Capture a baseline before this change (or via a feature toggle / quick local revert) and an "after" measurement at the same collider count; record both numbers in a short note appended to `design.md` under a new `## Results` section
- [x] 8.5 Stress the cache invalidation path on purpose: ship a dedicated demo `5` (Rotating box) where the balls are children of a `Node2D` wrapper that rotates each frame. Walls live in the wrapper's local frame, so each ball's `worldTransform()` cache is invalidated every frame via ancestor mutation — the broad-phase still finds every pair correctly, confirming cache + invalidation stays correct under load (not just fast).
- [x] 8.6 Document the new demo in `CLAUDE.md` under the `:games:demos` section, alongside demos `1`, `2`, `3`
