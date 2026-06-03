package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Panel

/**
 * Screen-space control surface for the world-space [ColliderWidget]: a single
 * segmented row `AABB | REAL` that picks `colliders.mode`, with the active
 * segment highlighted so the current mode is always on screen.
 *
 * This is the **screen-space arm of the one colliders tool**, mirroring the
 * Inspector's master/slave split (`inspector` + `SelectionGizmoWidget`): its [enabled] proxies
 * `colliders.enabled` (get and set), so toggling the HUD's "Colliders" row
 * shows/hides this panel, and the panel's close `[x]` turns the gizmo off.
 * Auto-inserted under `ScreenDebugCanvas` but kept out of
 * `DebugRegistry.widgets`/HUD, so it never adds a second "Colliders" row.
 *
 * Replaces keyboard-only mode cycling with a visible, clickable control —
 * no hidden shortcut to remember. `colliders.mode` stays a public `var` for
 * programmatic control. Mirrors `TimeControlWidget`'s build-panel-on-enable
 * lifecycle.
 */
class ColliderModePanel : ScreenDebugWidget() {

    override val title: String = "Colliders"

    override val defaultSlot: DockSlot = DockSlot.BOTTOM_RIGHT

    // Two halves of one tool: visibility and on/off follow `colliders`, and the
    // panel's [x] writes back through so closing it disables the gizmo too.
    override var enabled: Boolean
        get() = tree?.debug?.colliders?.enabled ?: false
        set(value) { tree?.debug?.colliders?.enabled = value }

    private var panel: Panel? = null
    private val segments: MutableList<Pair<ColliderDrawMode, Button>> = mutableListOf()
    private var lastBodyVisible: Boolean = false

    init { name = "ColliderModePanel" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (bodyVisible != lastBodyVisible) {
            lastBodyVisible = bodyVisible
            if (bodyVisible) buildPanel() else tearDownPanel()
            return
        }
        if (!bodyVisible) return
        refreshSegments()
    }

    // Keep a stable header width while collapsed (the panel is torn down, so
    // panel.size is gone) so the chrome still shows at full panel width.
    override fun bodySize(): Vec2 =
        panel?.size ?: if (collapsed) Vec2(PANEL_WIDTH, 0f) else Vec2.ZERO

    override fun drawDebug(renderer: Renderer) {
        // The base paints the chrome + header; the Buttons draw themselves
        // through the scene-graph traversal. Place the (invisible) container at
        // the body origin just before its children draw.
        panel?.position = bodyOrigin
    }

    private fun buildPanel() {
        val newPanel = Panel().apply {
            name = "ColliderModePanelBody"
            size = Vec2(PANEL_WIDTH, PANEL_HEADER + ROW_HEIGHT + ROW_GAP)
            // Invisible container: the base paints the shared chrome behind it.
            color = TRANSPARENT
            border = null
        }
        addChild(newPanel)
        panel = newPanel
        segments.clear()

        val count = ColliderDrawMode.entries.size
        val segWidth = (PANEL_WIDTH - ROW_GAP * (count + 1)) / count
        ColliderDrawMode.entries.forEachIndexed { i, mode ->
            val btn = Button().apply {
                name = "ColliderMode_${mode.name}"
                size = Vec2(segWidth, ROW_HEIGHT - ROW_GAP)
                position = Vec2(ROW_GAP + i * (segWidth + ROW_GAP), PANEL_HEADER)
                text = mode.name
                textSize = 12f
            }
            btn.pressed.connect { tree?.debug?.colliders?.mode = mode }
            newPanel.addChild(btn)
            segments += mode to btn
        }
        refreshSegments()
        panel?.position = bodyOrigin
    }

    private fun tearDownPanel() {
        val current = panel ?: return
        removeChild(current)
        panel = null
        segments.clear()
    }

    private fun refreshSegments() {
        val active = tree?.debug?.colliders?.mode ?: return
        for ((mode, btn) in segments) {
            val selected = mode == active
            btn.normalColor = if (selected) ACTIVE_COLOR else INACTIVE_COLOR
            btn.hoverColor = if (selected) ACTIVE_COLOR else INACTIVE_HOVER_COLOR
        }
    }

    companion object {
        private val TRANSPARENT: Color = Color(0f, 0f, 0f, 0f)
        private val ACTIVE_COLOR: Color = Color(0.20f, 0.45f, 0.65f, 1f)
        private val INACTIVE_COLOR: Color = Color(0.30f, 0.30f, 0.32f, 1f)
        private val INACTIVE_HOVER_COLOR: Color = Color(0.40f, 0.40f, 0.45f, 1f)
        private const val PANEL_WIDTH: Float = 186f
        private const val PANEL_HEADER: Float = 6f
        private const val ROW_HEIGHT: Float = 24f
        private const val ROW_GAP: Float = 4f
    }
}
