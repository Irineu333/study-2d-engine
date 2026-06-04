package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * How a [Control] participates in the UI hit-test (`SceneTree.hitTestUI`).
 *
 * - [STOP] — opaque: a press inside the control's screen rect is registered
 *   against it and **consumes** the click (gameplay/picker never see it).
 * - [PASS] — observed: the press registers (hover/press) but is **not**
 *   consumed, so it continues to controls/nodes behind. Full queued-event
 *   semantics land in `ui-input-events`; here PASS means "observed, not consumed".
 * - [IGNORE] — transparent: never tested; the press passes straight through.
 */
@Serializable
enum class MouseFilter { STOP, PASS, IGNORE }

/**
 * Reserved for the future `ui-focus` change. Declared so [Control] is born
 * complete; setting it has **no behavioral effect** in this change.
 */
@Serializable
enum class FocusMode { NONE, CLICK, ALL }

/**
 * Godot 4-style anchor presets. Applying one sets the four anchors of a
 * [Control] to canonical fractions of the parent rect; offsets are left to the
 * caller (a fixed-size widget keeps its offsets, a stretch preset like
 * [FULL_RECT] is typically paired with zero offsets).
 */
@Serializable
enum class LayoutPreset {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER_LEFT,
    CENTER_TOP,
    CENTER_RIGHT,
    CENTER_BOTTOM,
    CENTER,
    FULL_RECT,
}

/**
 * Abstract Godot-style base for in-game UI widgets. `Control : Node2D` so it
 * inherits the existing `transform`/`world()`/render-stack contract unchanged;
 * anchors are a **layout pass** (`SceneTree` resolves `position`/`size` from
 * anchors + offsets against the parent rect each tick) rather than a second
 * render path. See invariant #6 in `CLAUDE.md`.
 *
 * Anchors + offsets are the **source of truth**; `position`/`size` are derived
 * by the anchor layout pass. Writing `position`/`size` directly is still
 * supported (imperative ergonomics + scene-file compat): the write is mirrored
 * back into the offsets under the **current** anchors (Godot-style write-back),
 * so the next layout pass reproduces the written rect.
 *
 * `Control` is `abstract` — it is not registered in `NodeRegistry` and cannot
 * be instantiated from a scene file directly; only concrete leaves (`Panel`,
 * `Button`, `Label`, …) are registered.
 */
@Serializable
abstract class Control : Node2D() {

    /**
     * Local transform. Overridden so that writing it (directly or via the
     * `position` sugar) mirrors the new rect back into the offsets. Re-annotated
     * with `@Inspect` because property annotations are not inherited by an
     * override — without this, a Control's transform would silently drop out of
     * scene serialization.
     */
    @Inspect
    override var transform: Transform
        get() = super.transform
        set(value) {
            super.transform = value
            if (!resolvingLayout) writeBackOffsets()
        }

    /**
     * Resolved local rect dimensions. Derived by the anchor layout pass; a
     * direct write mirrors back into the offsets so the rect survives the next
     * pass. Min-size leaves (`Label`) ignore this for their bounds.
     */
    @Inspect
    var size: Vec2 = Vec2.ZERO
        set(value) {
            field = value
            if (!resolvingLayout) writeBackOffsets()
        }

    @Inspect
    var anchorLeft: Float = 0f

    @Inspect
    var anchorTop: Float = 0f

    @Inspect
    var anchorRight: Float = 0f

    @Inspect
    var anchorBottom: Float = 0f

    @Inspect
    var offsetLeft: Float = 0f

    @Inspect
    var offsetTop: Float = 0f

    @Inspect
    var offsetRight: Float = 0f

    @Inspect
    var offsetBottom: Float = 0f

    /**
     * When `false`, this control and its entire subtree are skipped by the UI
     * render pass and the UI hit-test. Replaces the `color.a = 0` hide hack.
     */
    @Inspect
    var visible: Boolean = true

    /** Hit-test participation; see [MouseFilter]. */
    @Inspect
    var mouseFilter: MouseFilter = MouseFilter.STOP

    // --- Declared-inert fields: born complete, lit up by future changes. ---

    /** Reserved for `ui-focus`. Inert in this change — setting it changes nothing. */
    @Inspect
    var focusMode: FocusMode = FocusMode.NONE

    /** Reserved for `ui-focus` (node-path-like). Inert in this change. */
    @Inspect
    var focusNeighborLeft: String = ""

    /** Reserved for `ui-focus` (node-path-like). Inert in this change. */
    @Inspect
    var focusNeighborTop: String = ""

    /** Reserved for `ui-focus` (node-path-like). Inert in this change. */
    @Inspect
    var focusNeighborRight: String = ""

    /** Reserved for `ui-focus` (node-path-like). Inert in this change. */
    @Inspect
    var focusNeighborBottom: String = ""

    /** Reserved for `ui-layout` (container sizing). Inert in this change. */
    @Inspect
    var sizeFlagsHorizontal: Int = 0

    /** Reserved for `ui-layout` (container sizing). Inert in this change. */
    @Inspect
    var sizeFlagsVertical: Int = 0

    /** True only while the layout pass writes `position`/`size`, so the
     *  write-back into the offsets is suppressed (offsets are the source). */
    @Transient
    private var resolvingLayout: Boolean = false

    /** Parent-rect size from the most recent layout pass, used by write-back to
     *  recompute offsets under non-zero anchors. `ZERO` until first laid out;
     *  irrelevant for the common top-left anchors where the anchor term is 0. */
    @Transient
    private var lastParentSize: Vec2 = Vec2.ZERO

    override fun localBounds(): Rect? = Rect(Vec2.ZERO, size)

    /**
     * Screen-space axis-aligned rect (the AABB of [localBounds] projected
     * through `world()`). `null` only when [localBounds] is `null`.
     */
    open fun screenRect(): Rect? = worldBounds()

    /** Sets the four anchors to the canonical fractions for [preset]. Offsets
     *  are left untouched (Godot's `set_anchors_preset`). */
    fun applyPreset(preset: LayoutPreset) {
        val (l, t, r, b) = anchorsFor(preset)
        anchorLeft = l
        anchorTop = t
        anchorRight = r
        anchorBottom = b
    }

    /**
     * Min-size hook for the layout pass. The default returns the anchor-derived
     * size unchanged (the control fills its anchor rect). `Label` overrides this
     * to report the measured text size, so anchors position a text-sized rect.
     */
    protected open fun layoutSize(anchorSize: Vec2): Vec2 = anchorSize

    /**
     * Resolves this control's `position`/`size` from its anchors/offsets against
     * [parentRect] (Godot 4 formula `edge = anchor*parentDim + offset`), writing
     * the **local** transform so the existing render stack composes correctly.
     * Returns the resolved rect in the same (absolute) space as [parentRect] so
     * the caller can thread it to child Controls. Called only by `SceneTree`.
     */
    internal fun resolveLayout(parentRect: Rect): Rect {
        val pw = parentRect.size.x
        val ph = parentRect.size.y
        val left = anchorLeft * pw + offsetLeft
        val top = anchorTop * ph + offsetTop
        val right = anchorRight * pw + offsetRight
        val bottom = anchorBottom * ph + offsetBottom
        val anchorSize = Vec2(right - left, bottom - top)
        val resolved = layoutSize(anchorSize)
        // Center only the positive slack: a min-size control smaller than its
        // anchor rect sits centered (full-rect Label → screen center), while a
        // point/top-left anchor (zero slack) keeps the anchor origin.
        val localPos = Vec2(
            left + maxOf(0f, anchorSize.x - resolved.x) / 2f,
            top + maxOf(0f, anchorSize.y - resolved.y) / 2f,
        )
        resolvingLayout = true
        position = localPos
        size = resolved
        resolvingLayout = false
        lastParentSize = parentRect.size
        return Rect(
            Vec2(parentRect.origin.x + localPos.x, parentRect.origin.y + localPos.y),
            resolved,
        )
    }

    private fun writeBackOffsets() {
        val pw = lastParentSize.x
        val ph = lastParentSize.y
        val pos = position
        val sz = size
        offsetLeft = pos.x - anchorLeft * pw
        offsetTop = pos.y - anchorTop * ph
        offsetRight = (pos.x + sz.x) - anchorRight * pw
        offsetBottom = (pos.y + sz.y) - anchorBottom * ph
    }

    private fun anchorsFor(preset: LayoutPreset): Anchors = when (preset) {
        LayoutPreset.TOP_LEFT -> Anchors(0f, 0f, 0f, 0f)
        LayoutPreset.TOP_RIGHT -> Anchors(1f, 0f, 1f, 0f)
        LayoutPreset.BOTTOM_LEFT -> Anchors(0f, 1f, 0f, 1f)
        LayoutPreset.BOTTOM_RIGHT -> Anchors(1f, 1f, 1f, 1f)
        LayoutPreset.CENTER_LEFT -> Anchors(0f, 0.5f, 0f, 0.5f)
        LayoutPreset.CENTER_TOP -> Anchors(0.5f, 0f, 0.5f, 0f)
        LayoutPreset.CENTER_RIGHT -> Anchors(1f, 0.5f, 1f, 0.5f)
        LayoutPreset.CENTER_BOTTOM -> Anchors(0.5f, 1f, 0.5f, 1f)
        LayoutPreset.CENTER -> Anchors(0.5f, 0.5f, 0.5f, 0.5f)
        LayoutPreset.FULL_RECT -> Anchors(0f, 0f, 1f, 1f)
    }

    private data class Anchors(val left: Float, val top: Float, val right: Float, val bottom: Float)
}
