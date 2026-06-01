package com.neoutils.engine.debug

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
 * (the [title] text on [DebugTheme.headerBackground]) and the border, then calls
 * [drawDebug] so the subclass only draws its body — from [bodyOrigin], just
 * below the header.
 *
 * Positioning is the `DebugDock`'s job, not the widget's: the widget declares a
 * [slot] and reports its [bodySize]; the dock writes [dockOrigin]. Dragging the
 * header writes a [customOrigin] that overrides the slot. Subclasses never
 * hardcode a screen corner.
 */
abstract class ScreenDebugWidget : Node(), DebugWidget {

    override var enabled: Boolean = false

    /**
     * Corner/center this widget docks to. The `DebugDock` stacks every enabled
     * widget sharing a slot, so the default is safe even when unset; widgets
     * with a deliberate placement override it.
     */
    open val slot: DockSlot = DockSlot.TOP_LEFT

    /**
     * Screen-pixel top-left assigned by the `DebugDock` each render from [slot]
     * and [contentSize]. Used only while the panel has no drag [customOrigin];
     * read [origin] (not this) when drawing. Defaults to the origin until the
     * first relayout.
     */
    var dockOrigin: Vec2 = Vec2.ZERO
        internal set

    /**
     * Session-only position override set by dragging the panel's header. `null`
     * means "follow the dock slot"; once set, it wins over [dockOrigin]. Survives
     * the widget's enable/disable toggle and `tree.resize` (re-clamped into the
     * viewport by the dock via [reclampCustomOrigin]); never persisted to disk.
     * Cleared by [resetPosition].
     */
    var customOrigin: Vec2? = null
        private set

    /**
     * Screen-pixel top-left of the whole panel (header + body): the drag
     * [customOrigin] when present, otherwise the dock-assigned [dockOrigin].
     */
    val origin: Vec2 get() = customOrigin ?: dockOrigin

    /**
     * Top-left of the body area, just below the title-bar header. Subclasses
     * draw their content from here.
     */
    val bodyOrigin: Vec2 get() = origin + Vec2(0f, DebugTheme.headerHeight)

    /**
     * Size in screen pixels of the widget's **body** (excluding the header);
     * `(0, 0)` when there is nothing to show. Widgets of variable height
     * recompute it from current state, so the dock re-flows as they grow.
     */
    open fun bodySize(): Vec2 = Vec2.ZERO

    /**
     * Full panel size (header + body) the `DebugDock` stacks by. `(0, 0)` when
     * the body is empty, so the dock skips the panel and no header is drawn.
     */
    fun contentSize(): Vec2 {
        val body = bodySize()
        if (body.x <= 0f || body.y <= 0f) return Vec2.ZERO
        return Vec2(body.x, DebugTheme.headerHeight + body.y)
    }

    private var dragging: Boolean = false
    private var grabOffset: Vec2 = Vec2.ZERO

    final override fun onDraw(renderer: Renderer) {
        if (!enabled) return
        val full = contentSize()
        if (full.x > 0f && full.y > 0f) drawChrome(renderer, full)
        drawDebug(renderer)
    }

    /** Background + title-bar header + border shared by every screen panel. */
    private fun drawChrome(renderer: Renderer, full: Vec2) {
        val o = origin
        renderer.drawRect(Rect(o, full), DebugTheme.panelBackground, filled = true)
        renderer.drawRect(
            Rect(o, Vec2(full.x, DebugTheme.headerHeight)),
            DebugTheme.headerBackground,
            filled = true,
        )
        renderer.drawText(
            text = title,
            position = Vec2(
                o.x + DebugTheme.padding,
                o.y + (DebugTheme.headerHeight - DebugTheme.titleTextSize) / 2f,
            ),
            size = DebugTheme.titleTextSize,
            color = DebugTheme.textColor,
        )
        renderer.drawRect(Rect(o, full), DebugTheme.panelBorderColor, filled = false)
    }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        updateDrag()
    }

    /** Clears the drag override so the panel flows back to its dock slot. */
    fun resetPosition() {
        customOrigin = null
        dragging = false
    }

    /**
     * Re-clamp the drag override into [surface], called by the `DebugDock` each
     * relayout so a panel left off-screen by a shrunk window stays visible.
     * No-op when there is no override or the panel currently shows nothing.
     */
    internal fun reclampCustomOrigin(surface: Vec2) {
        val current = customOrigin ?: return
        val size = contentSize()
        if (size.x <= 0f || size.y <= 0f) return
        customOrigin = clampToSurface(current, size, surface)
    }

    /**
     * Polling drag, mirroring the engine's other debug nodes: press the title-bar
     * header to begin (capturing [grabOffset]), follow the pointer while the
     * button is held, release to end. While dragging, the panel owns the drag —
     * it flags [com.neoutils.engine.input.Input.mouseDragConsumed] so gameplay
     * pan/drag consumers stand down.
     */
    private fun updateDrag() {
        if (!enabled) {
            dragging = false
            return
        }
        val input = tree?.input ?: return
        val surface = tree?.size ?: return
        val full = contentSize()
        if (full.x <= 0f || full.y <= 0f) {
            dragging = false
            return
        }
        val down = input.isMouseDown(MouseButton.Left)
        if (dragging) {
            if (!down) {
                dragging = false
                return
            }
            customOrigin = clampToSurface(input.pointerPosition - grabOffset, full, surface)
            input.mouseDragConsumed = true
            return
        }
        // Begin only on the press edge inside the header (the drag handle).
        if (down && input.wasMouseClickedRaw(MouseButton.Left) && inHeader(input.pointerPosition, full)) {
            dragging = true
            grabOffset = input.pointerPosition - origin
            input.mouseDragConsumed = true
        }
    }

    /**
     * The drag handle is the title-bar header strip: pressing it starts a drag,
     * while the body below (rows, steppers, readouts) keeps routing clicks.
     */
    private fun inHeader(pointer: Vec2, full: Vec2): Boolean =
        Rect(origin, Vec2(full.x, DebugTheme.headerHeight)).contains(pointer)

    private fun clampToSurface(pos: Vec2, size: Vec2, surface: Vec2): Vec2 {
        val maxX = (surface.x - size.x).coerceAtLeast(0f)
        val maxY = (surface.y - size.y).coerceAtLeast(0f)
        return Vec2(pos.x.coerceIn(0f, maxX), pos.y.coerceIn(0f, maxY))
    }
}
