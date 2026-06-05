## 1. Stretch transform primitive

- [ ] 1.1 Extract a pure `fitTransform(designSize: Vec2, surfaceSize: Vec2, mode): Pair<Vec2, Vec2>?` (scale + centering translation, no origin/pan term) into a shared location reachable by `:engine`, returning `null` for degenerate/identity cases.
- [ ] 1.2 Refactor `Camera2D.computeViewTransform` to reuse `fitTransform` for the resolution-fit part and add the `−bounds.origin·scale` pan term on top, preserving existing camera behavior and tests.
- [ ] 1.3 Add a `UiStretchMode { FIT, FILL, STRETCH, DISABLED }` enum.

## 2. SceneTree design resolution + stretch

- [ ] 2.1 Add `designSize: Vec2` and `uiStretchMode: UiStretchMode` (default `FIT`) to `SceneTree`, settable, with KDoc noting `designSize` is stable (not recomputed per frame).
- [ ] 2.2 Initialize `designSize` default at startup from the current `Camera2D.bounds` size when present, else from the initial surface size (`GameConfig`); ensure the wiring lives where the camera/surface is first known.
- [ ] 2.3 Add `uiStretchTransform(): Pair<Vec2, Vec2>?` computing `fitTransform(designSize, size, uiStretchMode)`, returning `null` on `DISABLED`/degenerate/identity.

## 3. CanvasLayer followStretch

- [ ] 3.1 Add `followStretch: Boolean = true` as `@Inspect` to `CanvasLayer`; verify serialization round-trips via the standard properties bag.
- [ ] 3.2 Mark the engine-inserted `ScreenDebugCanvas` (`layer = Int.MAX_VALUE - 1`) with `followStretch = false` where the `DebugLayer` is constructed.

## 4. Render / layout / hit-test consume the stretch

- [ ] 4.1 In the UI render pass, for each `CanvasLayer` with `followStretch = true` and non-null `uiStretchTransform`, push the transform around the subtree walk; raw layers keep identity.
- [ ] 4.2 In `runAnchorLayout`, at a `CanvasLayer` boundary use `Rect(ZERO, designSize)` as parent rect when `followStretch = true`, else `Rect(ZERO, size)`.
- [ ] 4.3 In `hitTestUI`, for a `followStretch` layer map `input.pointerPosition` into design-space (inverse of `uiStretchTransform`) before testing `Button` rects; raw layers use the raw pointer.

## 5. Engine tests

- [ ] 5.1 `fitTransform` per mode: FIT letterbox+center (e.g. 800×600 design → 1200×600 surface ⇒ `(200,0), (1,1)`), FIT downscale (→400×300 ⇒ `(0,0),(0.5,0.5)`), FILL, STRETCH, and `null` on identity/degenerate.
- [ ] 5.2 `uiStretchTransform` ignores camera pan: changing camera position leaves the transform unchanged.
- [ ] 5.3 Render: `followStretch = true` Panel scales (2x case), `followStretch = false` Panel stays raw pixels.
- [ ] 5.4 Anchor layout: stretched control resolves against design rect and is resize-stable in design-space; raw control still reflows against surface.
- [ ] 5.5 Hit-test: click at screen pixel maps through the stretch onto the drawn `Button`; raw layer unaffected.
- [ ] 5.6 DebugLayer immunity: `ScreenDebugCanvas` content stays pixel-locked under a non-trivial stretch.
- [ ] 5.7 `designSize` default derivation: from current camera bounds with a camera; from surface without one (identity stretch).

## 6. Game migration

- [ ] 6.1 Pong: confirm `designSize` inherits the camera `bounds`; verify `leftScore`/`rightScore` now align and scale with the field on resize (no scene rework beyond design-space confirmation).
- [ ] 6.2 Snake: verify `ScoreLabel` and `GameOverLabel` align and scale on resize under design-space; remove any now-redundant compensation.
- [ ] 6.3 Tictactoe: migrate `status` to design-space (anchor/center against design rect); remove the per-frame `recenter_status` in `board.lua` if made redundant.
- [ ] 6.4 Manually smoke-test each migrated game (Skiko) at the design size and at a resized window; confirm HUD tracks the board/field. `hello-world` unchanged.

## 7. Docs

- [ ] 7.1 Update `CLAUDE.md` invariant #6 to describe `followStretch` + design-space stretch (done at archive).
- [ ] 7.2 Close the "HUD screen-space não acompanha o resize" debt entry in `ROADMAP.md`.
