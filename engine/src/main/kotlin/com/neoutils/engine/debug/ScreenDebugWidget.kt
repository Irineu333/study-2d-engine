package com.neoutils.engine.debug

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node

/**
 * Base for debug widgets that render in screen pixels (no `Camera2D` view
 * transform). Lives under the `ScreenDebugCanvas` (`CanvasLayer`) child of
 * the auto-inserted `DebugLayer`.
 *
 * The base owns the shared panel **chrome**: when [enabled] and the widget has
 * a non-empty [bodySize], `onDraw` paints the background, a title-bar header
 * and the border. The header carries three interactive zones — a drag grip
 * (2×3 dots) at the left, then the title, then the window controls at the right:
 * collapse (`[_]`) and close (`[x]`). The body ([drawDebug]) is painted just
 * below the header, but only while [bodyVisible].
 *
 * Window controls, hit-tested manually in [updateDrag] (same polling style as
 * the drag):
 *  - **close** (`[x]`) sets [enabled] = `false` — a soft close, the panel
 *    reopens from the `DebugHud` (which itself reopens via `debugHudKey`).
 *  - **collapse/expand** toggles [collapsed]: the header stays, the body is
 *    hidden, and the `DebugDock` re-flows the other panels from the reduced
 *    [contentSize]. Its glyph is state-aware — a dash (`[_]`, minimize) when
 *    expanded, a hollow box (`[□]`, maximize) when collapsed.
 *
 * Positioning is the `DebugDock`'s job, not the widget's: the widget declares a
 * [defaultSlot] and reports its [bodySize]; the dock writes [dockOrigin]. The
 * runtime position is an explicit two-state model — **docked** ([currentSlot] +
 * [orderInSlot], placed by the dock) or **floating** ([floatingPosition], a free
 * position over any slot). Dragging resolves a drop target per frame and applies
 * it on release: re-dock into another slot, reorder within a slot, or float in
 * the center. Subclasses never hardcode a screen corner.
 */
abstract class ScreenDebugWidget : Node(), DebugWidget {

    override var enabled: Boolean = false

    /**
     * Whether the header draws and hit-tests the close (`[x]`) control. Default
     * `true`; a subclass that declares `false` (the Inspector's slave detail
     * panel) drops the control from the header — it is not drawn, the press over
     * its former area falls through to the normal drag/header path, and the panel
     * can only be turned off by whoever governs its [enabled].
     */
    open val closable: Boolean = true

    /**
     * Whether the header draws and hit-tests the collapse (`[_]`) control.
     * Default `true`; a subclass that declares `false` stays always-expanded and
     * the press over its former area falls through to the normal drag/header
     * path.
     */
    open val collapsible: Boolean = true

    /**
     * Corner/center this widget docks to by default — the class-declared anchor
     * and the target of a reset. Independent of [currentSlot], which the user
     * may move; the `DebugDock` stacks every enabled widget sharing a slot, so
     * the default is safe even when unset; widgets with a deliberate placement
     * override it.
     */
    open val defaultSlot: DockSlot = DockSlot.TOP_LEFT

    // Lazily defaults to defaultSlot so a subclass `override val defaultSlot`
    // (initialized after the base constructor) is respected — reading it eagerly
    // in a field initializer would capture the base default instead.
    private var movedSlot: DockSlot? = null

    /**
     * The slot the panel is **currently** docked in. Starts at [defaultSlot];
     * dragging the panel onto another dock band moves it here without touching
     * [defaultSlot]. Session-only; reset restores it to [defaultSlot].
     */
    var currentSlot: DockSlot
        get() = movedSlot ?: defaultSlot
        internal set(value) { movedSlot = value }

    /**
     * Default stacking order assigned by the `DebugDock` at registration
     * (registration order, globally increasing). The target [orderInSlot] a
     * reset restores to.
     */
    internal var defaultOrder: Int = 0

    /**
     * Mutable stacking position within [currentSlot]: the dock stacks the slot's
     * docked panels in ascending [orderInSlot] (not registration/DFS order), and
     * a reorder drag edits it. Session-only; reset restores it to [defaultOrder].
     */
    var orderInSlot: Int = 0
        internal set

    /**
     * Screen-pixel top-left assigned by the `DebugDock` each render from
     * [currentSlot], [orderInSlot] and [contentSize]. Used only while the panel
     * is docked (no [floatingPosition], not mid-drag); read [origin] (not this)
     * when drawing. Defaults to the origin until the first relayout.
     */
    var dockOrigin: Vec2 = Vec2.ZERO
        internal set

    /**
     * Session-only free position, set only when a drag is released in the miolo
     * (center) of the viewport — `null` means "docked, follow [currentSlot]".
     * Wins over [dockOrigin] while set. Survives the widget's enable/disable
     * toggle and `tree.resize` (re-clamped into the viewport by the dock via
     * [reclampFloating]); never persisted to disk. Cleared by [resetPosition]
     * or by re-docking.
     */
    var floatingPosition: Vec2? = null
        internal set

    /** Transient drag position while a drag is in flight; follows the pointer. */
    private var dragOrigin: Vec2? = null

    /** Whether this panel is floating (a free position) rather than docked. */
    val isFloating: Boolean get() = floatingPosition != null

    /** Whether a header drag is currently in flight on this panel. */
    val isDragging: Boolean get() = dragging

    /**
     * Session-only collapse state toggled by the header's `[_]` control. While
     * `true` only the header is drawn (the body is hidden and the dock re-flows
     * the reduced height). Mirrors [floatingPosition]'s lifetime: survives the
     * enable/disable toggle and `tree.resize`, never persists to disk; cleared
     * by [resetPosition].
     */
    var collapsed: Boolean = false
        private set

    /** Flips [collapsed]; backs the header's `[_]` control. */
    fun toggleCollapsed() {
        collapsed = !collapsed
    }

    /**
     * Effective body visibility: the body (`drawDebug` and any child-node
     * content) shows only when the widget is [enabled] **and** not [collapsed].
     * The chrome (header) is drawn whenever [enabled]. Widgets that mount child
     * nodes (`DebugHud`, `TimeControlWidget`) watch this — not `enabled` — to
     * build/tear down those children, so collapsing fully un-mounts them.
     */
    val bodyVisible: Boolean get() = enabled && !collapsed

    /**
     * Screen-pixel top-left of the whole panel (header + body): the live
     * [dragOrigin] while dragging, else the [floatingPosition] when floating,
     * else the dock-assigned [dockOrigin].
     */
    val origin: Vec2 get() = dragOrigin ?: floatingPosition ?: dockOrigin

    /**
     * Top-left of the body area, just below the title-bar header. Subclasses
     * draw their content from here.
     */
    val bodyOrigin: Vec2 get() = origin + Vec2(0f, DebugTheme.headerHeight)

    /**
     * The **content extent** in screen pixels — the intrinsic size of the
     * widget's full body (every row), excluding the header; `(0, 0)` when there
     * is nothing to show. The base derives the bounded viewport from this (see
     * [contentSize]) and scrolls when the extent exceeds the viewport, so the
     * subclass always reports the full height and never truncates. Widgets of
     * variable height recompute it from current state.
     */
    open fun bodySize(): Vec2 = Vec2.ZERO

    // Single piece of scroll state: the logical offset, clamped on read against
    // the current frame's extent/viewport. Grabber and bar are derived per frame
    // from (extent, viewport, offset), never stored — so resize/redock re-clamp
    // and re-derive automatically, with no resize-specific code path.
    private var rawScrollOffset: Float = 0f

    // Cached from the last contentSize() so scroll math outside the draw (the
    // wheel routing in hitTestUI, the row hit-test in onProcess) sees the same
    // extent/viewport the body was last drawn with.
    private var measuredExtent: Vec2 = Vec2.ZERO
    private var measuredViewportY: Float = 0f

    private var scrollbarDragging: Boolean = false
    private var scrollbarGrabDy: Float = 0f

    /** Whether a scrollbar-grabber drag is currently in flight on this panel. */
    val isScrollbarDragging: Boolean get() = scrollbarDragging

    /**
     * Bounded viewport height for a body of intrinsic height [extentY]:
     * `min(extentY, maxBodyHeight)`, where `maxBodyHeight` leaves room for the
     * header and the screen margins. Below the cap the panel auto-sizes (small
     * panels are unaffected); above it the panel claims only the viewport and the
     * remainder is reached by scrolling.
     */
    private fun viewportHeight(extentY: Float): Float {
        val surface = tree?.size ?: return extentY
        val maxH = surface.y - DebugTheme.margin * 2f - DebugTheme.headerHeight
        return if (maxH <= 0f) extentY else minOf(extentY, maxH)
    }

    /** Scroll offset clamped to `0 .. max(0, extentY - viewportY)` for this frame. */
    private fun effectiveOffset(extentY: Float, viewportY: Float): Float =
        rawScrollOffset.coerceIn(0f, (extentY - viewportY).coerceAtLeast(0f))

    /**
     * Effective (clamped) scroll offset for the current frame, derived from the
     * last measured extent/viewport. Subclasses whose body rows are interactive
     * subtract this from the drawn `y` so the hit-test matches the drawing.
     */
    protected val scrollOffset: Float get() = effectiveOffset(measuredExtent.y, measuredViewportY)

    /**
     * Full panel size the `DebugDock` stacks by. `(0, 0)` (the dock skips it and
     * no chrome is drawn) when the body has no width. When [collapsed] only the
     * header is reported — same width as the body keeps the panel coherent
     * across states — so the dock re-flows the reduced height; otherwise it is
     * header + the bounded viewport (NOT the full content extent, so an
     * overflowing panel claims only its viewport in the dock).
     */
    fun contentSize(): Vec2 {
        val body = bodySize()
        measuredExtent = body
        measuredViewportY = 0f
        if (body.x <= 0f) return Vec2.ZERO
        if (!bodyVisible) return Vec2(body.x, DebugTheme.headerHeight)
        if (body.y <= 0f) return Vec2.ZERO
        val vp = viewportHeight(body.y)
        measuredViewportY = vp
        return Vec2(body.x, DebugTheme.headerHeight + vp)
    }

    private var dragging: Boolean = false
    private var grabOffset: Vec2 = Vec2.ZERO

    final override fun onDraw(renderer: Renderer) {
        if (!enabled) return
        val full = contentSize()
        if (full.x <= 0f || full.y <= 0f) return
        drawChrome(renderer, full)
        if (bodyVisible) drawBody(renderer, full)
    }

    /**
     * Composes the scroll around the subclass draw: clip to the viewport (so
     * overflow does not paint over neighbors/header), translate by `-offset`
     * (so the content slides), then draw the scrollbar outside the clip. Clip
     * sits outside the transform so the LIFO native save/restore stays balanced.
     */
    private fun drawBody(renderer: Renderer, full: Vec2) {
        val extentY = measuredExtent.y
        val vp = measuredViewportY
        val offset = effectiveOffset(extentY, vp)
        renderer.pushClip(Rect(bodyOrigin, Vec2(full.x, vp)))
        renderer.pushTransform(Vec2(0f, -offset), 0f, Vec2(1f, 1f))
        drawDebug(renderer)
        renderer.popTransform()
        renderer.popClip()
        if (extentY - vp > 0f) drawScrollbar(renderer, full, extentY, vp, offset)
    }

    /** Track + proportional grabber at the panel's right edge; only when scrollable. */
    private fun drawScrollbar(renderer: Renderer, full: Vec2, extentY: Float, vp: Float, offset: Float) {
        val trackX = origin.x + full.x - SCROLLBAR_WIDTH
        renderer.drawRect(
            Rect(Vec2(trackX, bodyOrigin.y), Vec2(SCROLLBAR_WIDTH, vp)),
            DebugTheme.scrollTrackColor,
            filled = true,
        )
        val grabber = grabberRect(full, extentY, vp, offset) ?: return
        renderer.drawRect(grabber, DebugTheme.scrollGrabberColor, filled = true)
    }

    /** Derived grabber rect for this frame, or `null` when the panel is not scrollable. */
    private fun grabberRect(full: Vec2, extentY: Float, vp: Float, offset: Float): Rect? {
        val scrollable = extentY - vp
        if (scrollable <= 0f) return null
        val grabberH = (vp * (vp / extentY)).coerceIn(MIN_GRABBER_H, vp)
        val grabberY = bodyOrigin.y + (vp - grabberH) * (offset / scrollable)
        val trackX = origin.x + full.x - SCROLLBAR_WIDTH
        return Rect(Vec2(trackX, grabberY), Vec2(SCROLLBAR_WIDTH, grabberH))
    }

    /**
     * Applies a wheel [deltaY] (positive = scroll down) to the offset when the
     * panel is scrollable and [pointer] is inside the viewport; returns whether
     * it scrolled. Called by the `SceneTree.hitTestUI` scroll routing, which sets
     * `input.scrollConsumed` on a `true` return.
     */
    fun applyScroll(pointer: Vec2, deltaY: Float): Boolean {
        if (!bodyVisible) return false
        val extent = bodySize()
        if (extent.x <= 0f) return false
        val vp = viewportHeight(extent.y)
        val scrollable = extent.y - vp
        if (scrollable <= 0f) return false
        if (!Rect(bodyOrigin, Vec2(extent.x, vp)).contains(pointer)) return false
        rawScrollOffset = (rawScrollOffset + deltaY * WHEEL_STEP).coerceIn(0f, scrollable)
        return true
    }

    /** Background + title-bar header (grip + title + window controls) + border. */
    private fun drawChrome(renderer: Renderer, full: Vec2) {
        val o = origin
        renderer.drawRect(Rect(o, full), DebugTheme.panelBackground, filled = true)
        renderer.drawRect(
            Rect(o, Vec2(full.x, DebugTheme.headerHeight)),
            DebugTheme.headerBackground,
            filled = true,
        )
        drawDragGrip(renderer)
        renderer.drawText(
            text = title,
            position = Vec2(
                gripRect().right + CONTROL_GAP,
                o.y + (DebugTheme.headerHeight - DebugTheme.titleTextSize) / 2f,
            ),
            size = DebugTheme.titleTextSize,
            color = DebugTheme.textColor,
        )
        if (collapsible) drawCollapseGlyph(renderer)
        if (closable) drawCloseGlyph(renderer)
        renderer.drawRect(Rect(o, full), DebugTheme.panelBorderColor, filled = false)
    }

    /**
     * A 2×3 grid of dots at the left of the header — the universal "grab me"
     * affordance — signalling the title bar is the drag handle.
     */
    private fun drawDragGrip(renderer: Renderer) {
        val grip = gripRect()
        for (c in 0 until GRIP_COLS) {
            for (r in 0 until GRIP_ROWS) {
                val x = grip.left + c * (GRIP_DOT + GRIP_GAP)
                val y = grip.top + r * (GRIP_DOT + GRIP_GAP)
                renderer.drawRect(
                    Rect(Vec2(x, y), Vec2(GRIP_DOT, GRIP_DOT)),
                    DebugTheme.headerGripColor,
                    filled = true,
                )
            }
        }
    }

    /**
     * The collapse/expand control, state-aware so minimize and maximize read
     * differently: when expanded, a bottom dash (`[_]`, "minimize" — hides the
     * body); when [collapsed], a hollow box (`[□]`, "maximize" — restores it).
     */
    private fun drawCollapseGlyph(renderer: Renderer) {
        val r = collapseRect()
        val color = DebugTheme.headerControlColor
        if (collapsed) {
            renderer.drawRect(
                Rect(
                    Vec2(r.left + GLYPH_INSET, r.top + GLYPH_INSET),
                    Vec2(r.size.x - 2f * GLYPH_INSET, r.size.y - 2f * GLYPH_INSET),
                ),
                color,
                filled = false,
            )
        } else {
            val y = r.bottom - GLYPH_INSET
            renderer.drawLine(
                Vec2(r.left + GLYPH_INSET, y),
                Vec2(r.right - GLYPH_INSET, y),
                GLYPH_THICKNESS,
                color,
            )
        }
    }

    /** The close control: two crossed diagonals inside its rect (`[x]`). */
    private fun drawCloseGlyph(renderer: Renderer) {
        val r = closeRect()
        val color = DebugTheme.headerControlColor
        renderer.drawLine(
            Vec2(r.left + GLYPH_INSET, r.top + GLYPH_INSET),
            Vec2(r.right - GLYPH_INSET, r.bottom - GLYPH_INSET),
            GLYPH_THICKNESS,
            color,
        )
        renderer.drawLine(
            Vec2(r.right - GLYPH_INSET, r.top + GLYPH_INSET),
            Vec2(r.left + GLYPH_INSET, r.bottom - GLYPH_INSET),
            GLYPH_THICKNESS,
            color,
        )
    }

    /** Bounding rect of the drag-grip dots at the header's left. */
    internal fun gripRect(): Rect {
        val gridW = GRIP_COLS * GRIP_DOT + (GRIP_COLS - 1) * GRIP_GAP
        val gridH = GRIP_ROWS * GRIP_DOT + (GRIP_ROWS - 1) * GRIP_GAP
        val o = origin
        return Rect(
            Vec2(o.x + DebugTheme.padding, o.y + (DebugTheme.headerHeight - gridH) / 2f),
            Vec2(gridW, gridH),
        )
    }

    /** Square hit/draw rect of the close (`[x]`) control at the header's right edge. */
    internal fun closeRect(): Rect {
        val o = origin
        val width = contentSize().x
        return Rect(
            Vec2(
                o.x + width - DebugTheme.padding - CONTROL_SIZE,
                o.y + (DebugTheme.headerHeight - CONTROL_SIZE) / 2f,
            ),
            Vec2(CONTROL_SIZE, CONTROL_SIZE),
        )
    }

    /** Square hit/draw rect of the collapse (`[_]`) control, left of [closeRect]. */
    internal fun collapseRect(): Rect {
        val close = closeRect()
        return Rect(
            Vec2(close.left - CONTROL_GAP - CONTROL_SIZE, close.top),
            Vec2(CONTROL_SIZE, CONTROL_SIZE),
        )
    }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        updateDrag()
    }

    /**
     * Restores the panel to its default layout — back to [defaultSlot] with
     * [defaultOrder], un-floated and expanded — the single "restore default
     * layout" gesture. Also ends any in-flight drag so the dock stops tracking
     * it.
     */
    fun resetPosition() {
        if (dragging) tree?.debug?.dock?.endDrag(this)
        dragging = false
        dragOrigin = null
        scrollbarDragging = false
        rawScrollOffset = 0f
        movedSlot = null
        orderInSlot = defaultOrder
        floatingPosition = null
        collapsed = false
    }

    /**
     * Re-clamp the floating position into [surface], called by the `DebugDock`
     * each relayout so a panel left off-screen by a shrunk window stays visible.
     * No-op when the panel is docked or currently shows nothing.
     */
    internal fun reclampFloating(surface: Vec2) {
        val current = floatingPosition ?: return
        val size = contentSize()
        if (size.x <= 0f || size.y <= 0f) return
        floatingPosition = clampToSurface(current, size, surface)
    }

    /**
     * Polling drag, mirroring the engine's other debug nodes: press the title-bar
     * header to begin (capturing [grabOffset]), follow the pointer while the
     * button is held, release to resolve a drop target. Before arming a drag, a
     * press on a window control runs its action instead. While dragging, the
     * panel owns the drag — it flags
     * [com.neoutils.engine.input.Input.mouseDragConsumed] so gameplay pan/drag
     * consumers stand down — and the `DebugDock` is asked each frame for the live
     * drop target (so the insertion indicator tracks the pointer). On release the
     * dock resolves the final target: a band docks the panel into the resolved
     * `(slot, index)`; the miolo floats it where it was dropped.
     */
    private fun updateDrag() {
        if (!enabled) {
            stopDrag()
            stopScrollbarDrag()
            return
        }
        val input = tree?.input ?: return
        val dock = tree?.debug?.dock ?: return
        val surface = tree?.size ?: return
        val full = contentSize()
        if (full.x <= 0f || full.y <= 0f) {
            stopDrag()
            stopScrollbarDrag()
            return
        }
        val down = input.isMouseDown(MouseButton.Left)
        // A scrollbar-grabber drag is the second drag consumer on this panel; it
        // is resolved before the header drag and writes the offset proportionally.
        if (scrollbarDragging) {
            if (!down) {
                stopScrollbarDrag()
                return
            }
            applyScrollbarDrag(input.pointerPosition, full)
            input.mouseDragConsumed = true
            return
        }
        if (dragging) {
            val pointer = input.pointerPosition
            // Magnetism is resolved against the whole window rect, not the grabbed
            // header, so an edge reaching a band docks even with the pointer inside.
            val dragged = clampToSurface(pointer - grabOffset, full, surface)
            if (!down) {
                when (val drop = dock.resolveDropTarget(Rect(dragged, full))) {
                    is DropTarget.Dock -> dock.dockWidget(this, drop.slot, drop.index)
                    DropTarget.Floating -> floatingPosition = dragged
                }
                stopDrag()
                return
            }
            dragOrigin = dragged
            dock.updateDropTarget(this, Rect(dragged, full))
            input.mouseDragConsumed = true
            return
        }
        // Act only on the press edge, and only when `hitTestUI` elected this panel
        // as the press owner (the top-most panel under the pointer). Reading the
        // owner instead of the raw click is what keeps a press on the overlap of
        // two panels from arming both — the lower one is never the owner.
        if (!down || this !== tree?.debug?.pressOwner) return
        val pointer = input.pointerPosition
        // Window controls take precedence over starting a drag (and are carved
        // out of inHeader, so the drag path never fires over them). A suppressed
        // control is not hit-tested, so its former area drags like the header.
        if (closable && closeRect().contains(pointer)) {
            enabled = false
            consumePress(input)
            return
        }
        if (collapsible && collapseRect().contains(pointer)) {
            toggleCollapsed()
            consumePress(input)
            return
        }
        // A press landing on the grabber starts a scrollbar drag (only when the
        // panel is scrollable), distinguished from the header drag by the rect.
        val grabber = if (bodyVisible) grabberRect(full, measuredExtent.y, measuredViewportY, scrollOffset) else null
        if (grabber != null && grabber.contains(pointer)) {
            scrollbarDragging = true
            scrollbarGrabDy = pointer.y - grabber.top
            consumePress(input)
            return
        }
        if (inHeader(pointer, full)) {
            dragging = true
            grabOffset = pointer - origin
            dragOrigin = origin
            dock.beginDrag(this)
            input.mouseDragConsumed = true
        }
    }

    /** Ends an in-flight drag and tells the dock to stop tracking this panel. */
    private fun stopDrag() {
        if (dragging) tree?.debug?.dock?.endDrag(this)
        dragging = false
        dragOrigin = null
    }

    /** Ends an in-flight scrollbar-grabber drag. */
    private fun stopScrollbarDrag() {
        scrollbarDragging = false
    }

    /**
     * Maps the dragged grabber's top to a proportional offset: the grabber top
     * tracks the pointer (minus the grab anchor), clamped within the track, and
     * the offset is its fraction of the scrollable range.
     */
    private fun applyScrollbarDrag(pointer: Vec2, full: Vec2) {
        val extentY = measuredExtent.y
        val vp = measuredViewportY
        val scrollable = extentY - vp
        if (scrollable <= 0f) {
            stopScrollbarDrag()
            return
        }
        val grabberH = (vp * (vp / extentY)).coerceIn(MIN_GRABBER_H, vp)
        val travel = vp - grabberH
        if (travel <= 0f) return
        val grabberTop = (pointer.y - scrollbarGrabDy).coerceIn(bodyOrigin.y, bodyOrigin.y + travel)
        val frac = (grabberTop - bodyOrigin.y) / travel
        rawScrollOffset = (frac * scrollable).coerceIn(0f, scrollable)
    }

    /** Mark the current press as handled so it neither drags nor reaches the picker. */
    private fun consumePress(input: Input) {
        input.mouseClickConsumed = true
        input.mouseDragConsumed = true
    }

    /**
     * The drag handle is the title-bar header strip minus the three interactive
     * rects (grip, collapse, close): pressing the bare header starts a drag,
     * while the controls and the body below keep their own behavior.
     */
    private fun inHeader(pointer: Vec2, full: Vec2): Boolean {
        if (!Rect(origin, Vec2(full.x, DebugTheme.headerHeight)).contains(pointer)) return false
        if (gripRect().contains(pointer)) return false
        // A suppressed control occupies no carve-out, so its area drags normally.
        if (collapsible && collapseRect().contains(pointer)) return false
        if (closable && closeRect().contains(pointer)) return false
        return true
    }

    private fun clampToSurface(pos: Vec2, size: Vec2, surface: Vec2): Vec2 {
        val maxX = (surface.x - size.x).coerceAtLeast(0f)
        val maxY = (surface.y - size.y).coerceAtLeast(0f)
        return Vec2(pos.x.coerceIn(0f, maxX), pos.y.coerceIn(0f, maxY))
    }

    private companion object {
        const val GRIP_COLS: Int = 2
        const val GRIP_ROWS: Int = 3
        const val GRIP_DOT: Float = 2f
        const val GRIP_GAP: Float = 2f

        /** Side of each window-control hit/draw square. */
        const val CONTROL_SIZE: Float = 12f

        /** Gap after the grip and between the two window controls. */
        const val CONTROL_GAP: Float = 6f

        /** Inset of a glyph's strokes inside its control square. */
        const val GLYPH_INSET: Float = 2.5f

        /** Stroke width of the control glyphs. */
        const val GLYPH_THICKNESS: Float = 1.5f

        /** Width of the vertical scrollbar (track + grabber) at the panel's right edge. */
        const val SCROLLBAR_WIDTH: Float = 6f

        /** Smallest grabber height, so a tiny visible fraction stays grabbable. */
        const val MIN_GRABBER_H: Float = 16f

        /** Screen pixels scrolled per unit of wheel delta. */
        const val WHEEL_STEP: Float = 40f
    }
}
