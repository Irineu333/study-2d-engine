package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Panel
import java.util.Locale
import kotlin.math.abs

/**
 * Screen-space debug panel exposing the [com.neoutils.engine.tree.SceneTree]
 * time controls — pause/resume, single-step, and a `− [1.00x] +` speed stepper
 * (clamped to the [SPEED_PRESETS] ends). The buttons mutate `tree.paused` /
 * `tree.timeScale` / `tree.requestStep()` directly and, because they live under
 * the `ScreenDebugCanvas`, are driven through `SceneTree.hitTestUI` — so they
 * remain operable while paused (the loop still runs `hitTestUI` and
 * `process(0f)`).
 *
 * Keyboard shortcuts are polled by the engine-internal [TimeControlShortcutNode]
 * under `process`, alive under pause for the same reason; its speed keys step
 * the presets the same way the − / + buttons do. Mirrors `DebugHud`'s
 * build-panel-on-enable lifecycle.
 */
class TimeControlWidget : ScreenDebugWidget() {

    override val title: String = "Time"

    override val defaultSlot: DockSlot = DockSlot.TOP_LEFT

    private var panel: Panel? = null
    private var pauseButton: Button? = null
    private var speedDisplay: Button? = null
    private var lastBodyVisible: Boolean = false

    init { name = "TimeControlWidget" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (bodyVisible != lastBodyVisible) {
            lastBodyVisible = bodyVisible
            if (bodyVisible) buildPanel() else tearDownPanel()
            return
        }
        if (!bodyVisible) return
        refreshLabels()
    }

    // Keep a stable header width while collapsed (the panel is torn down, so
    // panel.size is gone) so the chrome still shows at full panel width.
    override fun bodySize(): Vec2 =
        panel?.size ?: if (collapsed) Vec2(PANEL_WIDTH, 0f) else Vec2.ZERO

    override fun drawDebug(renderer: Renderer) {
        // The base paints the chrome + header; the Buttons draw themselves
        // through the scene-graph traversal. Place the (invisible) container at
        // the body origin just before its children draw (this onDraw runs ahead
        // of the panel child in the DFS).
        panel?.position = bodyOrigin
    }

    private fun buildPanel() {
        val owningTree = tree ?: return
        val newPanel = Panel().apply {
            name = "TimeControlPanel"
            size = Vec2(PANEL_WIDTH, PANEL_HEADER + ROW_HEIGHT * 3 + ROW_GAP)
            // Invisible container: the base paints the shared chrome behind it.
            color = TRANSPARENT
            border = null
        }
        addChild(newPanel)
        panel = newPanel

        val pause = rowButton("TimeControlPause", 0, pauseLabel(owningTree.paused))
        pause.pressed.connect { tree?.let { it.paused = !it.paused } }
        newPanel.addChild(pause)
        pauseButton = pause

        val step = rowButton("TimeControlStep", 1, "Step")
        step.pressed.connect { tree?.requestStep() }
        newPanel.addChild(step)

        // Speed row: [−] [1.00x] [+]. The display is a disabled Button so it
        // never absorbs clicks; − / + step through the presets, clamped.
        val rowY = PANEL_HEADER + 2 * ROW_HEIGHT
        val minus = sizedButton("TimeControlSpeedDown", ROW_GAP, rowY, STEPPER_WIDTH, "-")
        minus.pressed.connect { tree?.let { it.timeScale = stepSpeed(it.timeScale, up = false) } }
        newPanel.addChild(minus)

        val displayX = ROW_GAP + STEPPER_WIDTH + ROW_GAP
        val plusX = PANEL_WIDTH - ROW_GAP - STEPPER_WIDTH
        val display = sizedButton(
            "TimeControlSpeedDisplay",
            displayX,
            rowY,
            plusX - ROW_GAP - displayX,
            speedValueLabel(owningTree.timeScale),
        ).apply { disabled = true }
        newPanel.addChild(display)
        speedDisplay = display

        val plus = sizedButton("TimeControlSpeedUp", plusX, rowY, STEPPER_WIDTH, "+")
        plus.pressed.connect { tree?.let { it.timeScale = stepSpeed(it.timeScale, up = true) } }
        newPanel.addChild(plus)

        panel?.position = bodyOrigin
    }

    private fun rowButton(buttonName: String, index: Int, label: String): Button =
        sizedButton(
            buttonName,
            ROW_GAP,
            PANEL_HEADER + index * ROW_HEIGHT,
            PANEL_WIDTH - ROW_GAP * 2f,
            label,
        )

    private fun sizedButton(buttonName: String, x: Float, y: Float, width: Float, label: String): Button =
        Button().apply {
            name = buttonName
            size = Vec2(width, ROW_HEIGHT - ROW_GAP)
            position = Vec2(x, y)
            text = label
            textSize = 13f
        }

    private fun tearDownPanel() {
        val current = panel ?: return
        removeChild(current)
        panel = null
        pauseButton = null
        speedDisplay = null
    }

    private fun refreshLabels() {
        val owningTree = tree ?: return
        pauseButton?.let {
            val expected = pauseLabel(owningTree.paused)
            if (it.text != expected) it.text = expected
        }
        speedDisplay?.let {
            val expected = speedValueLabel(owningTree.timeScale)
            if (it.text != expected) it.text = expected
        }
    }

    private fun pauseLabel(paused: Boolean): String = if (paused) "Resume" else "Pause"

    private fun speedValueLabel(scale: Float): String =
        "${String.format(Locale.US, "%.2f", scale)}x"

    companion object {
        /** Speed-preset list shared by the widget steppers and the shortcut node. */
        val SPEED_PRESETS: List<Float> = listOf(0.25f, 0.5f, 1f, 2f, 4f)

        /**
         * One preset up ([up] = true) or down from the entry nearest [current],
         * clamped at the ends (no wrap) — drives both the − / + buttons and the
         * speed keyboard shortcuts. Stateless, so it behaves identically
         * regardless of how `timeScale` was last set.
         */
        fun stepSpeed(current: Float, up: Boolean): Float {
            val target = (nearestPresetIndex(current) + if (up) 1 else -1)
                .coerceIn(0, SPEED_PRESETS.lastIndex)
            return SPEED_PRESETS[target]
        }

        private fun nearestPresetIndex(current: Float): Int {
            var best = 0
            var bestDiff = Float.MAX_VALUE
            SPEED_PRESETS.forEachIndexed { i, v ->
                val d = abs(v - current)
                if (d < bestDiff) {
                    bestDiff = d
                    best = i
                }
            }
            return best
        }

        private val TRANSPARENT: Color = Color(0f, 0f, 0f, 0f)
        private const val PANEL_WIDTH: Float = 140f
        private const val STEPPER_WIDTH: Float = 28f
        private const val PANEL_HEADER: Float = 6f
        private const val ROW_HEIGHT: Float = 24f
        private const val ROW_GAP: Float = 4f
    }
}
