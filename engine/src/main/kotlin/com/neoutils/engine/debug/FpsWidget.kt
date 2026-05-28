package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer

/**
 * Top-left screen-space FPS readout. Owns its `FpsCounter` and samples
 * `System.nanoTime()` each `onProcess` call — no host involvement, no
 * shared state. Defaults to `enabled = false`; user enables via the HUD.
 */
class FpsWidget : ScreenDebugWidget() {

    override val title: String = "FPS"

    private val counter: FpsCounter = FpsCounter()

    init { name = "FpsWidget" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (enabled) counter.record(System.nanoTime())
    }

    override fun drawDebug(renderer: Renderer) {
        renderer.drawText(
            text = "fps ${counter.current.toInt()}",
            position = Vec2(8f, 24f),
            size = 18f,
            color = Color.WHITE,
        )
    }
}
