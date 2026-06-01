package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer

/**
 * Top-left screen-space FPS readout. Owns its `FpsCounter` and samples
 * `System.nanoTime()` each `onProcess` call — no host involvement, no
 * shared state. Defaults to `enabled = false`; user enables via the HUD.
 *
 * Draws the `fps NN` readout in the panel body; the base paints the chrome.
 */
class FpsWidget : ScreenDebugWidget() {

    override val title: String = "FPS"

    override val defaultSlot: DockSlot = DockSlot.TOP_LEFT

    private val counter: FpsCounter = FpsCounter()

    init { name = "FpsWidget" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (enabled) counter.record(System.nanoTime())
    }

    override fun bodySize(): Vec2 = Vec2(
        WIDTH,
        DebugTheme.padding * 2f + DebugTheme.bodyTextSize,
    )

    override fun drawDebug(renderer: Renderer) {
        val body = bodyOrigin
        renderer.drawText(
            text = "fps ${counter.current.toInt()}",
            position = Vec2(body.x + DebugTheme.padding, body.y + DebugTheme.padding),
            size = DebugTheme.bodyTextSize,
            color = DebugTheme.textColor,
        )
    }

    companion object {
        private const val WIDTH: Float = 88f
    }
}
