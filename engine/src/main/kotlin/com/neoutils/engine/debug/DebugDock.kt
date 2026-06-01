package com.neoutils.engine.debug

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer

/**
 * Where a dragged panel will land when released, resolved each frame from the
 * pointer: a [Dock] band target (slot + insertion index) or [Floating] (miolo).
 */
sealed interface DropTarget {
    /** A dock band: the panel lands in [slot] at insertion [index]. */
    data class Dock(val slot: DockSlot, val index: Int) : DropTarget

    /** The center region: the panel floats where it was dropped. */
    data object Floating : DropTarget
}

/**
 * Per-`SceneTree` layout coordinator and drop-target resolver for
 * `ScreenDebugWidget`s. Each widget docks in its `currentSlot`; the dock stacks
 * every enabled, non-empty, **docked** widget of a slot from its corner/center
 * inward in ascending `orderInSlot` (not registration/DFS order), separated by
 * `DebugTheme.gutter` and inset by `DebugTheme.margin`. The result is written to
 * each widget's `dockOrigin`, which the widget draws from — no widget hardcodes
 * a corner.
 *
 * The dock also owns the **drag interaction's spatial logic**: it maps the
 * pointer to a [DropTarget] ([resolveDropTarget]) using top/bottom dock bands
 * fileted into three horizontal thirds (the six slots) with the center as the
 * floating region, applies a drop ([dockWidget] re-docks/reorders with stable
 * renumbering), and draws the insertion indicator ([drawOverlay]). The panel
 * under drag is excluded from its slot's stacking so it does not reserve space
 * for itself and the insertion index stays stable.
 *
 * [relayout] is called by `SceneTree.render` each frame, so the layout re-flows
 * automatically on `tree.resize` and as variable-height widgets change size,
 * re-clamping floating panels into the viewport. Plain class, never a `Node`;
 * owned by `DebugRegistry`.
 */
class DebugDock {

    // Registration order, mirroring DebugRegistry.register; seeds each widget's
    // defaultOrder so the initial stacking matches registration.
    private val widgets: MutableList<ScreenDebugWidget> = mutableListOf()
    private var nextOrder: Int = 0

    /** Last surface seen by [relayout]; drives band geometry and re-clamps. */
    private var surface: Vec2 = Vec2.ZERO

    /** Panel currently under a header drag, excluded from slot stacking. */
    var dragging: ScreenDebugWidget? = null
        private set

    /** Live drop target while [dragging]; drives the insertion indicator. */
    private var dropTarget: DropTarget = DropTarget.Floating

    internal fun add(widget: ScreenDebugWidget) {
        if (widget in widgets) return
        widgets += widget
        widget.defaultOrder = nextOrder++
        widget.orderInSlot = widget.defaultOrder
    }

    internal fun remove(widget: ScreenDebugWidget) {
        widgets -= widget
        endDrag(widget)
    }

    /** Recompute every docked widget's `dockOrigin` for the current [surface]. */
    fun relayout(surface: Vec2) {
        this.surface = surface
        for (slot in DockSlot.values()) {
            val items = stacked(slot).mapNotNull { w ->
                val size = w.contentSize()
                if (size.x > 0f && size.y > 0f) w to size else null
            }
            if (items.isEmpty()) continue
            layoutSlot(slot, surface, items)
        }
        // Keep floating panels inside the viewport after a resize.
        for (w in widgets) {
            if (w.enabled && w.floatingPosition != null) w.reclampFloating(surface)
        }
    }

    /**
     * The docked widgets of [slot] in stacking order (ascending `orderInSlot`),
     * excluding floating panels and the one under drag.
     */
    private fun stacked(slot: DockSlot): List<ScreenDebugWidget> =
        widgets
            .filter {
                it.currentSlot == slot && it.enabled &&
                    it.floatingPosition == null && it !== dragging
            }
            .sortedBy { it.orderInSlot }

    private fun layoutSlot(
        slot: DockSlot,
        surface: Vec2,
        items: List<Pair<ScreenDebugWidget, Vec2>>,
    ) {
        val margin = DebugTheme.margin
        val gutter = DebugTheme.gutter
        if (slot.isTop) {
            var y = margin
            for ((widget, size) in items) {
                widget.dockOrigin = Vec2(slotX(slot, surface, size.x), y)
                y += size.y + gutter
            }
        } else {
            // Bottom slots grow upward: the first widget hugs the bottom edge.
            var bottom = surface.y - margin
            for ((widget, size) in items) {
                val y = bottom - size.y
                widget.dockOrigin = Vec2(slotX(slot, surface, size.x), y)
                bottom = y - gutter
            }
        }
    }

    private fun slotX(slot: DockSlot, surface: Vec2, width: Float): Float {
        val margin = DebugTheme.margin
        return when (slot) {
            DockSlot.TOP_LEFT, DockSlot.BOTTOM_LEFT -> margin
            DockSlot.TOP_RIGHT, DockSlot.BOTTOM_RIGHT -> maxOf(margin, surface.x - margin - width)
            DockSlot.TOP_CENTER, DockSlot.BOTTOM_CENTER -> maxOf(margin, (surface.x - width) / 2f)
        }
    }

    // ---- Drag interaction ---------------------------------------------------

    internal fun beginDrag(widget: ScreenDebugWidget) {
        dragging = widget
        dropTarget = DropTarget.Floating
    }

    internal fun updateDropTarget(widget: ScreenDebugWidget, panel: Rect) {
        if (dragging === widget) dropTarget = resolveDropTarget(panel)
    }

    internal fun endDrag(widget: ScreenDebugWidget) {
        if (dragging === widget) {
            dragging = null
            dropTarget = DropTarget.Floating
        }
    }

    /**
     * Maps the dragged [panel]'s rectangle to a [DropTarget] for the current
     * [surface]. Magnetism considers the **whole window**, not the grabbed
     * header: the panel docks to the top band when its top edge enters the band,
     * or to the bottom band when its bottom edge does (so pushing a panel against
     * an edge snaps it even though the header — and the pointer — stays well
     * inside the viewport). When both edges reach a band the deeper-penetrating
     * edge wins; otherwise it floats.
     *
     * Each band's reach **follows the slot's occupied stack**: a top slot already
     * filled down to some `y` extends its magnetic zone to cover that stack (plus
     * a gutter, so the next panel docks just below it), never shrinking below the
     * base [DebugTheme.dockBandThickness]. Without this, stacking a second panel
     * would require dragging it up *into* the first, since the first already
     * reaches past the fixed band.
     *
     * The slot's third is chosen by the panel's horizontal center; the insertion
     * index is taken from the leading edge (top edge for top slots, bottom edge
     * for bottom slots), so the drop slips into the gap that edge is nearest.
     */
    fun resolveDropTarget(panel: Rect): DropTarget {
        if (surface.x <= 0f || surface.y <= 0f) return DropTarget.Floating
        val band = DebugTheme.dockBandThickness
        val gutter = DebugTheme.gutter
        val cx = panel.left + panel.size.x / 2f
        val topSlot = topThird(cx)
        val bottomSlot = bottomThird(cx)
        // Reach down past the top stack / up past the bottom stack (gutter slack),
        // clamped so the zone never falls below the base band thickness.
        val topReach = maxOf(band, occupiedBottom(topSlot) + gutter)
        val bottomReach = minOf(surface.y - band, occupiedTop(bottomSlot) - gutter)
        val topPen = topReach - panel.top         // > 0 when the top edge sits inside the top zone
        val botPen = panel.bottom - bottomReach   // > 0 when the bottom edge sits inside the bottom zone
        val slot = when {
            topPen > 0f && botPen > 0f -> if (topPen >= botPen) topSlot else bottomSlot
            topPen > 0f -> topSlot
            botPen > 0f -> bottomSlot
            else -> return DropTarget.Floating
        }
        val refY = if (slot.isTop) panel.top else panel.bottom
        val others = stacked(slot)
        val index = if (slot.isTop) {
            others.count { centerY(it) < refY }
        } else {
            others.count { centerY(it) > refY }
        }
        return DropTarget.Dock(slot, index)
    }

    /** Bottom edge of [slot]'s occupied stack (top margin when empty). */
    private fun occupiedBottom(slot: DockSlot): Float {
        val list = stacked(slot)
        if (list.isEmpty()) return DebugTheme.margin
        return list.maxOf { it.dockOrigin.y + it.contentSize().y }
    }

    /** Top edge of [slot]'s occupied stack (bottom margin when empty). */
    private fun occupiedTop(slot: DockSlot): Float {
        val list = stacked(slot)
        if (list.isEmpty()) return surface.y - DebugTheme.margin
        return list.minOf { it.dockOrigin.y }
    }

    /** Degenerate [resolveDropTarget] over a zero-size panel at [pointer]. */
    fun resolveDropTarget(pointer: Vec2): DropTarget =
        resolveDropTarget(Rect(pointer, Vec2.ZERO))

    private fun topThird(x: Float): DockSlot = when {
        x < surface.x / 3f -> DockSlot.TOP_LEFT
        x < surface.x * 2f / 3f -> DockSlot.TOP_CENTER
        else -> DockSlot.TOP_RIGHT
    }

    private fun bottomThird(x: Float): DockSlot = when {
        x < surface.x / 3f -> DockSlot.BOTTOM_LEFT
        x < surface.x * 2f / 3f -> DockSlot.BOTTOM_CENTER
        else -> DockSlot.BOTTOM_RIGHT
    }

    private fun centerY(w: ScreenDebugWidget): Float =
        w.dockOrigin.y + w.contentSize().y / 2f

    /**
     * Re-docks [widget] into [slot] at insertion [index], un-floating it and
     * renumbering that slot's `orderInSlot` to a stable 0..n so the others shift
     * by exactly one. [index] is the position among the slot's other docked
     * panels (the value returned by [resolveDropTarget]).
     */
    internal fun dockWidget(widget: ScreenDebugWidget, slot: DockSlot, index: Int) {
        widget.floatingPosition = null
        widget.currentSlot = slot
        // Reorder relative to the *visible* stack (the same set [stacked] lays
        // out and [resolveDropTarget] indexes against); disabled panels in the
        // slot keep their order and rejoin when re-enabled.
        val ordered = widgets
            .filter { it !== widget && it.enabled && it.floatingPosition == null && it.currentSlot == slot }
            .sortedBy { it.orderInSlot }
            .toMutableList()
        ordered.add(index.coerceIn(0, ordered.size), widget)
        ordered.forEachIndexed { i, w -> w.orderInSlot = i }
    }

    /**
     * Draws the insertion indicator in the target slot when there is a dock drop
     * target — a thin bar in the gap where the panel will land. Nothing is drawn
     * for a floating target. Called by `SceneTree.render` after the UI pass, with
     * the transform stack back at identity (screen pixels).
     */
    fun drawOverlay(renderer: Renderer) {
        val drop = dropTarget as? DropTarget.Dock ?: return
        val dragged = dragging ?: return
        val list = stacked(drop.slot)
        val width = dragged.contentSize().x.coerceAtLeast(MIN_INDICATOR_WIDTH)
        val x = slotX(drop.slot, surface, width)
        val y = gapY(drop.slot, list, drop.index)
        renderer.drawRect(
            Rect(Vec2(x, y - INDICATOR_THICKNESS / 2f), Vec2(width, INDICATOR_THICKNESS)),
            DebugTheme.insertionIndicatorColor,
            filled = true,
        )
    }

    /** Screen Y of the gap before insertion [index] among the laid-out [list]. */
    private fun gapY(slot: DockSlot, list: List<ScreenDebugWidget>, index: Int): Float {
        val margin = DebugTheme.margin
        val half = DebugTheme.gutter / 2f
        fun top(w: ScreenDebugWidget) = w.dockOrigin.y
        fun bottom(w: ScreenDebugWidget) = w.dockOrigin.y + w.contentSize().y
        if (slot.isTop) {
            // list is top-first (ascending orderInSlot == ascending y).
            return when {
                list.isEmpty() -> margin
                index <= 0 -> top(list.first()) - half
                index >= list.size -> bottom(list.last()) + half
                else -> (bottom(list[index - 1]) + top(list[index])) / 2f
            }
        }
        // Bottom: list is bottom-first (ascending orderInSlot == descending y).
        return when {
            list.isEmpty() -> surface.y - margin
            index <= 0 -> bottom(list.first()) + half
            index >= list.size -> top(list.last()) - half
            else -> (bottom(list[index - 1]) + top(list[index])) / 2f
        }
    }

    private companion object {
        const val INDICATOR_THICKNESS: Float = 3f
        const val MIN_INDICATOR_WIDTH: Float = 40f
    }
}
