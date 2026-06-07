package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Panel

/**
 * Screen-space HUD listing every registered `DebugWidget` (except itself)
 * as a clickable row. Each row's label is `"[x] <title>"` when the target
 * widget is enabled, `"[ ] <title>"` otherwise. Clicking flips the target's
 * `enabled`.
 *
 * Implementation notes:
 *  - When `enabled` flips to `true`, the panel + button children are added
 *    to the scene (via the deferred-mutation pending queue). When it flips
 *    to `false`, those children are removed — so a closed HUD draws zero
 *    rects/text and consumes zero clicks.
 *  - While open, the row set tracks `tree.debug.widgets` live: a widget
 *    registered or unregistered (e.g. a demo's `onEnter`/`onExit`) rebuilds the
 *    rows on the next frame, so the HUD never shows a stale list against an
 *    already-open panel.
 *  - Docked at the top-right corner by the `DebugDock`; the panel follows
 *    `dockOrigin`, so it re-pins on `tree.resize` with no corner math here.
 */
class DebugHud : ScreenDebugWidget() {

    override val title: String = "Debug HUD"

    override val defaultSlot: DockSlot = DockSlot.TOP_RIGHT

    private var panel: Panel? = null
    private val rows: MutableList<Row> = mutableListOf()
    private var lastBodyVisible: Boolean = false

    init { name = "DebugHud" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (bodyVisible != lastBodyVisible) {
            lastBodyVisible = bodyVisible
            if (bodyVisible) buildPanel() else tearDownPanel()
            return
        }
        if (!bodyVisible) return
        // Rebuild when the registry changed under an already-open HUD (a widget
        // registered/unregistered live), so the row set reflects the current
        // `tree.debug.widgets` every frame — not only the set present at the
        // last open. Otherwise just refresh the labels in place.
        if (registryChanged()) rebuildPanel() else refreshLabels()
    }

    // True when the current non-HUD widget set differs (by identity or order)
    // from the rows last built — the cheap per-frame guard before a rebuild.
    private fun registryChanged(): Boolean {
        val owningTree = tree ?: return false
        val candidates = owningTree.debug.widgets.filter { it !== this }
        if (candidates.size != rows.size) return true
        for (i in candidates.indices) if (candidates[i] !== rows[i].widget) return true
        return false
    }

    private fun rebuildPanel() {
        tearDownPanel()
        buildPanel()
    }

    // Keep a stable header width while collapsed (the panel is torn down, so
    // panel.size is gone) so the chrome still shows at full panel width.
    override fun bodySize(): Vec2 =
        panel?.size ?: if (collapsed) Vec2(PANEL_WIDTH, 0f) else Vec2.ZERO

    override fun drawDebug(renderer: Renderer) {
        // The base paints the chrome + header; the Buttons draw themselves via
        // the scene-graph traversal. Place the (invisible) container at the body
        // origin just before its children draw (this onDraw runs ahead of the
        // panel child in the DFS).
        panel?.position = bodyOrigin
    }

    private fun buildPanel() {
        val owningTree = tree ?: return
        val candidates = owningTree.debug.widgets.filter { it !== this }
        val newPanel = Panel().apply {
            name = "DebugHudPanel"
            size = Vec2(PANEL_WIDTH, PANEL_HEADER + ROW_HEIGHT * candidates.size + ROW_GAP)
            // Invisible container: the base paints the shared chrome behind it.
            color = TRANSPARENT
            border = null
        }
        addChild(newPanel)
        panel = newPanel
        rows.clear()
        for ((idx, widget) in candidates.withIndex()) {
            val btn = Button().apply {
                name = "DebugHudRow_${widget.title}"
                size = Vec2(PANEL_WIDTH - ROW_GAP * 2f, ROW_HEIGHT - ROW_GAP)
                position = Vec2(ROW_GAP, PANEL_HEADER + idx * ROW_HEIGHT)
                text = labelFor(widget)
                textSize = 13f
            }
            btn.pressed.connect { widget.enabled = !widget.enabled }
            newPanel.addChild(btn)
            rows += Row(widget, btn)
        }
        panel?.position = bodyOrigin
    }

    private fun tearDownPanel() {
        val current = panel ?: return
        removeChild(current)
        panel = null
        rows.clear()
    }

    private fun refreshLabels() {
        for (row in rows) {
            val expected = labelFor(row.widget)
            if (row.button.text != expected) row.button.text = expected
        }
    }

    private fun labelFor(widget: DebugWidget): String =
        if (widget.enabled) "[x] ${widget.title}" else "[ ] ${widget.title}"

    private data class Row(val widget: DebugWidget, val button: Button)

    companion object {
        private val TRANSPARENT: Color = Color(0f, 0f, 0f, 0f)
        private const val PANEL_WIDTH: Float = 200f
        private const val PANEL_HEADER: Float = 6f
        private const val ROW_HEIGHT: Float = 24f
        private const val ROW_GAP: Float = 4f
    }
}
