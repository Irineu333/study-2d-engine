## 1. Module scaffolding

- [x] 1.1 Update `settings.gradle.kts`: remove `:shared` and `:desktopApp`; add `:engine`, `:engine-compose`, `:games:pong`.
- [x] 1.2 Create `:engine` module (`engine/build.gradle.kts`) as Kotlin JVM library; depend only on Kotlin stdlib. Apply package `com.neoutils.engine`.
- [x] 1.3 Create `:engine-compose` module (`engine-compose/build.gradle.kts`) as Kotlin JVM library; depend on `:engine` and Compose Multiplatform (desktop). Apply package `com.neoutils.engine.compose`.
- [x] 1.4 Create `:games:pong` module (`games/pong/build.gradle.kts`) as Kotlin JVM application; depend on `:engine` and `:engine-compose`. Configure `application` plugin with `mainClass` and Compose Desktop launcher. Apply package `com.neoutils.engine.games.pong`.
- [x] 1.5 Delete legacy template directories `shared/` and `desktopApp/` (and their build artifacts) once new modules compile clean.
- [x] 1.6 Update root `README.md` to reflect the new module layout and the `./gradlew :games:pong:run` command.

## 2. Engine math primitives

- [x] 2.1 Implement `Vec2(x: Float, y: Float)` with `+`, `-`, `*` (scalar), `length`, `normalized`.
- [x] 2.2 Implement `Rect(origin: Vec2, size: Vec2)` with `intersects(other: Rect): Boolean` and `contains(point: Vec2): Boolean`.
- [x] 2.3 Implement `Transform(position: Vec2, scale: Vec2, rotation: Float)` as an immutable data class with a `copy(...)`-friendly API.
- [x] 2.4 Add unit tests covering arithmetic, intersection, and non-intersection of `Rect`.

## 3. Scene graph core

- [x] 3.1 Implement abstract `Node` with `name`, `parent`, `children`, `addChild`, `removeChild`. Track liveness propagated from the owning `Scene`.
- [x] 3.2 Implement lifecycle methods on `Node`: `onEnter`, `onUpdate(dt)`, `onRender(renderer)`, `onExit` (all `open` with empty defaults).
- [x] 3.3 Implement pre-order propagation of `onEnter` and post-order propagation of `onExit` when nodes attach/detach from a live tree.
- [x] 3.4 Implement `Node2D` adding `transform: Transform`.
- [x] 3.5 Implement `Shape` (rect or circle, with `size`, `color`, `filled`) and `Text` (string, size, color) as `Node2D` subclasses whose `onRender` delegates to `Renderer` primitives.
- [x] 3.6 Implement `Scene` extending `Node` with `update(dt)` and `render(renderer)` traversal methods (parents before children for render).
- [x] 3.7 Unit tests: child attach/detach, lifecycle order, transform isolation between siblings, render order.

## 4. Renderer and Input SPIs

- [x] 4.1 Define `Renderer` interface in `:engine` with: `clear(color)`, `drawRect(rect, color, filled)`, `drawCircle(center, radius, color, filled)`, `drawText(text, position, size, color)`.
- [x] 4.2 Define `Input` interface in `:engine` with: `isKeyDown(Key): Boolean`, `wasKeyPressed(Key): Boolean`, `pointerPosition: Vec2`. Define a `Key` enum/sealed class covering at least the alphabet, arrow keys, space, escape.
- [x] 4.3 Sanity check: `:engine`'s `build.gradle.kts` has no Compose dependency; the SPIs reference no Compose types.

## 5. Collision and physics

- [x] 5.1 Implement abstract `Collider : Node2D` with `bounds(): Rect` (world space) and `open fun onCollide(other: Collider)`.
- [x] 5.2 Implement `BoxCollider(size: Vec2)` deriving bounds from its node's transform position and size.
- [x] 5.3 Implement `PhysicsSystem.step(scene)`: collect all live `Collider` nodes, test each pair (O(N²)) via `Rect.intersects`, invoke `onCollide` on both partners. Ensure each pair is tested exactly once per tick.
- [x] 5.4 Unit tests: bounds derived correctly from transform; non-overlapping pair never triggers `onCollide`; each pair triggered at most once per `step`.

## 6. Game loop

- [x] 6.1 Implement `GameLoop(scene, renderer, input, physics)` with `tick(dtNanos: Long)`. Compute `dt: Float` in seconds; call order: `scene.update(dt)` → `physics.step(scene)` → `scene.render(renderer)`.
- [x] 6.2 Document/clamp first-frame `dt` to a reasonable maximum (~0.05s) to avoid initial spikes.
- [x] 6.3 Unit test: order of operations within a tick; nanos-to-seconds conversion.

## 7. Compose runtime

- [ ] 7.1 Implement `ComposeRenderer(drawScope: DrawScope)` translating each `Renderer` method to corresponding `DrawScope` operation.
- [ ] 7.2 Implement `ComposeInput` aggregating Compose `KeyEvent`/`PointerEvent` into snapshot state; translate Compose key codes to engine `Key`.
- [ ] 7.3 Implement `GameSurface(scene: Scene)` composable: hosts `Canvas`, wires focus + key/pointer listeners to `ComposeInput`, runs a `LaunchedEffect` loop with `withFrameNanos` calling `GameLoop.tick`, requests redraw each frame.
- [ ] 7.4 Verify `GameSurface` stops ticking when removed from composition.

## 8. DX tooling

- [ ] 8.1 Implement a `Debug` configuration object in `:engine` with mutable flags `showFps`, `colliderVisualization`, plus log-level configuration.
- [ ] 8.2 Implement `Log` facility in `:engine` supporting `d`/`i`/`w`/`e` with `tag` parameter, global minimum level, and per-tag overrides. Emit timestamped output.
- [ ] 8.3 Implement FPS overlay: compute moving average over ≥1s; expose data to runtime; in `:engine-compose`, draw the value as a `Text` overlay when `Debug.showFps` is true.
- [ ] 8.4 Implement collider visualization: in `Scene.render`, if `Debug.colliderVisualization` is true, draw each `Collider.bounds()` as an outlined `Rect` after normal rendering.
- [ ] 8.5 Unit test for `Log` filtering (global and per-tag).

## 9. Pong sample game

- [ ] 9.1 Implement `PongScene : Scene` constructing the documented tree: two paddles, ball, four boundary colliders (top, bottom, left goal, right goal), HUD with two `Score` texts and center-line decoration.
- [ ] 9.2 Implement `Paddle : Node2D` with `BoxCollider` child, configurable input bindings (default W/S for left), AI mode (right), and frame-rate-independent movement clamped within the play field.
- [ ] 9.3 Implement `Ball : Node2D` with `BoxCollider` child, constant-magnitude velocity, reflection in `onCollide` based on partner type (paddle → flip X; top/bottom wall → flip Y; goal → score and reset).
- [ ] 9.4 Implement `Score : Node2D` (wraps `Text`) reflecting an integer state; expose increment.
- [ ] 9.5 Implement `Wall` and `Goal` collider nodes (subclasses or instances of `BoxCollider` tagged by class for `is`-checks in `Ball.onCollide`).
- [ ] 9.6 Implement `main()` in `:games:pong` that opens a Compose Desktop window hosting `GameSurface(PongScene())`. Wire key listeners through Compose so `ComposeInput` receives them.
- [ ] 9.7 Manual playtest: paddle moves with W/S; ball reflects off walls and paddles; scoring works for both sides; ball resets after a goal; AI paddle tracks ball with imperfect speed.
- [ ] 9.8 Toggle FPS overlay and collider visualization mid-game; confirm both work without disturbing gameplay.

## 10. Project conventions and docs

- [ ] 10.1 Create `CLAUDE.md` at repo root with sections: Purpose, Architectural Invariants (the five listed), Module Structure & How to Run, Coding Conventions, OpenSpec Workflow, Roadmap.
- [ ] 10.2 Roadmap section lists `engine-foundation` (active), `event-driven-games` (planned), and editor change (placeholder).
- [ ] 10.3 Cross-link `CLAUDE.md` from `README.md`.
- [ ] 10.4 Verify all five architectural invariants from `design.md` Decision 1–5 are reflected in `CLAUDE.md`.

## 11. Acceptance and verification

- [ ] 11.1 Run `./gradlew build` clean from the root; no warnings about unused legacy modules.
- [ ] 11.2 Run `./gradlew :games:pong:run`; confirm Pong opens, runs at ~60fps, is fully playable.
- [ ] 11.3 Manually walk the Pong source against the `engine-core` and `compose-runtime` capability lists; confirm every feature is exercised at least once.
- [ ] 11.4 Run `openspec validate engine-foundation --strict` (or equivalent) and resolve any reported gaps.
- [ ] 11.5 Run `/opsx:verify engine-foundation` to confirm implementation matches the change artifacts before archiving.
