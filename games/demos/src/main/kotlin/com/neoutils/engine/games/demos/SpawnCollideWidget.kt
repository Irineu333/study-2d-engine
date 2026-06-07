package com.neoutils.engine.games.demos

import com.neoutils.engine.debug.DockSlot
import com.neoutils.engine.debug.ScreenDebugWidget
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.Panel

/**
 * Demo-local debug widget controlling the `Spawn & Collide` trap. Mirrors the
 * `ColliderModePanel` pattern — an invisible [Panel] of segmented [Button]s
 * built on [bodyVisible] and torn down on collapse/disable — exposing two rows
 * that read/write the demo's shared [SpawnCollideState]:
 *
 *  - `Trap [Despawn | Collide]` — picks [SpawnCollideState.trapMode].
 *  - `Auto-spawn [On | Off]` — picks [SpawnCollideState.autoSpawnEnabled].
 *
 * The active segment is highlighted so the current mode is always on screen.
 * Registered by [SpawnCollideDemo] in `onEnter` and unregistered in `onExit`, so
 * its HUD row appears only while the demo is loaded — the live example of the
 * `register`/`unregister` debug contract driven from a `Node` (it replaces the
 * old `AxesWidget`, which only ever exercised `register`). `:engine` never
 * references this class; it lives entirely in `:games:demos`.
 */
class SpawnCollideWidget(private val state: SpawnCollideState) : ScreenDebugWidget() {

    override val title: String = "Spawn & Collide"

    override val defaultSlot: DockSlot = DockSlot.BOTTOM_LEFT

    private var panel: Panel? = null
    private var despawnBtn: Button? = null
    private var collideBtn: Button? = null
    private var autoOnBtn: Button? = null
    private var autoOffBtn: Button? = null
    private var lastBodyVisible: Boolean = false

    init { name = "SpawnCollideWidget" }

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
        // The base paints the chrome + header; the Buttons/Labels draw themselves
        // through the scene-graph traversal. Place the (invisible) container at
        // the body origin just before its children draw.
        panel?.position = bodyOrigin
    }

    private fun buildPanel() {
        val newPanel = Panel().apply {
            name = "SpawnCollidePanelBody"
            size = Vec2(PANEL_WIDTH, PANEL_HEADER + 2f * ROW_HEIGHT)
            // Invisible container: the base paints the shared chrome behind it.
            color = TRANSPARENT
            border = null
        }
        addChild(newPanel)
        panel = newPanel

        addCaption(newPanel, "Trap", 0)
        despawnBtn = addSegment(newPanel, "Despawn", 0, 0) { state.trapMode = TrapMode.DESPAWN }
        collideBtn = addSegment(newPanel, "Collide", 0, 1) { state.trapMode = TrapMode.COLLIDE }

        addCaption(newPanel, "Auto", 1)
        autoOnBtn = addSegment(newPanel, "On", 1, 0) { state.autoSpawnEnabled = true }
        autoOffBtn = addSegment(newPanel, "Off", 1, 1) { state.autoSpawnEnabled = false }

        refreshSegments()
        newPanel.position = bodyOrigin
    }

    private fun addCaption(panel: Panel, text: String, row: Int) {
        panel.addChild(
            Label().apply {
                name = "Caption_$text"
                this.text = text
                fontSize = 12f
                color = Color(0.8f, 0.8f, 0.85f, 1f)
                position = Vec2(ROW_GAP, PANEL_HEADER + row * ROW_HEIGHT + (ROW_HEIGHT - 12f) / 2f)
            }
        )
    }

    private fun addSegment(panel: Panel, label: String, row: Int, index: Int, onPress: () -> Unit): Button {
        val btn = Button().apply {
            name = "Seg_${row}_$index"
            size = Vec2(SEG_WIDTH, ROW_HEIGHT - ROW_GAP)
            position = Vec2(
                CAPTION_WIDTH + ROW_GAP + index * (SEG_WIDTH + ROW_GAP),
                PANEL_HEADER + row * ROW_HEIGHT + ROW_GAP / 2f,
            )
            text = label
            textSize = 12f
        }
        btn.pressed.connect { onPress() }
        panel.addChild(btn)
        return btn
    }

    private fun tearDownPanel() {
        val current = panel ?: return
        removeChild(current)
        panel = null
        despawnBtn = null
        collideBtn = null
        autoOnBtn = null
        autoOffBtn = null
    }

    private fun refreshSegments() {
        highlight(despawnBtn, state.trapMode == TrapMode.DESPAWN)
        highlight(collideBtn, state.trapMode == TrapMode.COLLIDE)
        highlight(autoOnBtn, state.autoSpawnEnabled)
        highlight(autoOffBtn, !state.autoSpawnEnabled)
    }

    private fun highlight(btn: Button?, selected: Boolean) {
        btn ?: return
        btn.normalColor = if (selected) ACTIVE_COLOR else INACTIVE_COLOR
        btn.hoverColor = if (selected) ACTIVE_COLOR else INACTIVE_HOVER_COLOR
    }

    companion object {
        private val TRANSPARENT: Color = Color(0f, 0f, 0f, 0f)
        private val ACTIVE_COLOR: Color = Color(0.20f, 0.45f, 0.65f, 1f)
        private val INACTIVE_COLOR: Color = Color(0.30f, 0.30f, 0.32f, 1f)
        private val INACTIVE_HOVER_COLOR: Color = Color(0.40f, 0.40f, 0.45f, 1f)
        private const val PANEL_WIDTH: Float = 200f
        private const val PANEL_HEADER: Float = 6f
        private const val ROW_HEIGHT: Float = 26f
        private const val ROW_GAP: Float = 4f
        private const val CAPTION_WIDTH: Float = 52f
        private const val SEG_WIDTH: Float =
            (PANEL_WIDTH - CAPTION_WIDTH - ROW_GAP * 3f) / 2f
    }
}
