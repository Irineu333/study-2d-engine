package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Border
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
 *  - Pinned to the top-right corner with a 12 px inset; re-pinned each
 *    frame so it follows `tree.resize`.
 */
class DebugHud : ScreenDebugWidget() {

    override val title: String = "Debug HUD"

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
        repositionPanel()
    }

    override fun drawDebug(renderer: Renderer) {
        // The Panel + Buttons draw themselves via the scene-graph traversal;
        // this widget itself emits nothing.
    }

    private fun buildPanel() {
        val owningTree = tree ?: return
        val candidates = owningTree.debug.widgets.filter { it !== this }
        val newPanel = Panel().apply {
            name = "DebugHudPanel"
            size = Vec2(PANEL_WIDTH, PANEL_HEADER + ROW_HEIGHT * candidates.size + ROW_GAP)
            color = PANEL_COLOR
            border = Border(color = PANEL_BORDER, width = 1f)
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
        repositionPanel()
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

    private fun repositionPanel() {
        val p = panel ?: return
        val owningTree = tree ?: return
        p.position = Vec2(owningTree.size.x - PANEL_WIDTH - PANEL_MARGIN, PANEL_MARGIN)
    }

    private fun labelFor(widget: DebugWidget): String =
        if (widget.enabled) "[x] ${widget.title}" else "[ ] ${widget.title}"

    private data class Row(val widget: DebugWidget, val button: Button)

    companion object {
        private const val PANEL_WIDTH: Float = 200f
        private const val PANEL_MARGIN: Float = 12f
        private const val PANEL_HEADER: Float = 6f
        private const val ROW_HEIGHT: Float = 24f
        private const val ROW_GAP: Float = 4f
        private val PANEL_COLOR: Color = Color(0.10f, 0.10f, 0.12f, 0.85f)
        private val PANEL_BORDER: Color = Color(0.55f, 0.55f, 0.60f, 1f)
    }
}
