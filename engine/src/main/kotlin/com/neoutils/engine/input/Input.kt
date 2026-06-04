package com.neoutils.engine.input

import com.neoutils.engine.math.Vec2

interface Input {

    /**
     * Pointer position in **the same coordinate space as `SceneTree.size`**.
     * Backends MUST keep these two aligned — game code uses
     * `tree.screenToWorld(input.pointerPosition)` and assumes they live in the
     * same units. Today both happen to be in physical pixels on `:engine-skiko`
     * (mouse × `SkiaLayer.contentScale`, surface = render buffer dimensions);
     * the implicit "physical pixels" contract is a known debt to be formalized
     * in a future `surface-units-spec` change (logical units everywhere with
     * HiDPI absorbed at the backend edge). Until then, new backends must
     * mirror the alignment.
     */
    val pointerPosition: Vec2

    fun isKeyDown(key: Key): Boolean

    fun wasKeyPressed(key: Key): Boolean

    fun isMouseDown(button: MouseButton): Boolean

    /**
     * Raw mouse-click query, always reflecting the bare hardware event for the
     * current tick — never affected by UI consumption. Use this when code needs
     * to observe the click even if UI absorbed it (rare).
     */
    fun wasMouseClickedRaw(button: MouseButton): Boolean

    /**
     * Mouse-click query honoring UI consumption: returns [wasMouseClickedRaw]
     * unless [mouseClickConsumed] is `true` for the current tick (set by
     * [com.neoutils.engine.tree.SceneTree.hitTestUI] when a `Button` absorbs
     * the click). In MVP, only `MouseButton.Left` is suppressed when consumed;
     * other buttons fall through.
     *
     * Most gameplay scripts call this and get UI-aware behavior for free.
     */
    fun wasMouseClicked(button: MouseButton): Boolean =
        if (mouseClickConsumed && button == MouseButton.Left) false
        else wasMouseClickedRaw(button)

    /**
     * Set to `true` by [com.neoutils.engine.tree.SceneTree.hitTestUI] when a
     * UI widget (e.g. `Button`) absorbs the left click for the current tick.
     * `SceneTree.hitTestUI` resets this to `false` at its start, so each tick
     * begins with a clean slate.
     */
    var mouseClickConsumed: Boolean

    /**
     * Per-tick drag-consumption signal mirroring [mouseClickConsumed]: reset to
     * `false` by [com.neoutils.engine.tree.SceneTree.hitTestUI] at the start of
     * each tick and set to `true` while a debug panel owns a pointer drag.
     * Gameplay drag consumers (a camera pan, a "drag and drop" of world objects)
     * MUST check this before acting on a held [isMouseDown] so dragging a debug
     * panel does not also drag the camera/world along.
     *
     * Defaults to a no-op (always reads `false`, writes ignored) so an `Input`
     * that never participates in debug-panel dragging needs no extra storage;
     * the shipped backends override it with stored, per-tick state.
     */
    var mouseDragConsumed: Boolean
        get() = false
        set(_) {}

    /**
     * Mouse-wheel delta accumulated during the current tick, where positive
     * `y` means scrolling **down** (toward later content) and positive `x`
     * means scrolling right. Reads [Vec2.ZERO] on any tick with no wheel
     * motion, and is reset at the start of every tick (during `beginTick()`
     * or equivalent), exactly like the per-tick click state.
     */
    val scrollDelta: Vec2
        get() = Vec2.ZERO

    /**
     * Per-tick scroll-consumption signal mirroring [mouseDragConsumed]: reset
     * to `false` at the start of each tick and set to `true` by the
     * [com.neoutils.engine.tree.SceneTree.hitTestUI] phase (or its scroll
     * sibling) when a debug panel absorbs the wheel for the current tick.
     * Gameplay wheel consumers (camera zoom etc.) MUST check this before
     * acting on [scrollDelta] so scrolling over a debug panel does not also
     * drive gameplay.
     *
     * Defaults to a no-op (always reads `false`, writes ignored) so an `Input`
     * that never participates in scroll consumption needs no extra storage;
     * the shipped backends override it with stored, per-tick state.
     */
    var scrollConsumed: Boolean
        get() = false
        set(_) {}
}
