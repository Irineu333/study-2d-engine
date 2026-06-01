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
 *  - Docked at the top-right corner by the `DebugDock`; the panel follows
 *    `dockOrigin`, so it re-pins on `tree.resize` with no corner math here.
 */
class DebugHud : ScreenDebugWidget() {

    override val title: String = "Debug HUD"

    override val slot: DockSlot = DockSlot.TOP_RIGHT

    private var panel: Panel? = null
    private val rows: MutableList<Row> = mutableListOf()
    private var lastEnabled: Boolean = false

    init { name = "DebugHud" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (enabled != lastEnabled) {
            lastEnabled = enabled
            if (enabled) buildPanel() else tearDownPanel()
            return
        }
        if (!enabled) return
        refreshLabels()
    }

    override fun bodySize(): Vec2 = panel?.size ?: Vec2.ZERO

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
