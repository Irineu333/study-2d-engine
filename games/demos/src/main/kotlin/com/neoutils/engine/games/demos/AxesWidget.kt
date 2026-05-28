package com.neoutils.engine.games.demos

import com.neoutils.engine.debug.WorldDebugWidget
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer

/**
 * Example custom debug widget: draws world-space axes at the origin.
 * Registered by both `Main.kt` (Skiko) and `MainLwjgl.kt` (LWJGL) after
 * `tree.start()` to demonstrate that adding a debug visualization to a
 * game project is one file + one `tree.debug.register(...)` call — no
 * engine changes, no host changes.
 */
class AxesWidget : WorldDebugWidget() {

    override val title: String = "Axes"

    init { name = "AxesWidget" }

    override fun drawDebug(renderer: Renderer) {
        renderer.drawLine(Vec2.ZERO, Vec2(100f, 0f), thickness = 2f, color = Color(1f, 0.3f, 0.3f, 1f))
        renderer.drawLine(Vec2.ZERO, Vec2(0f, 100f), thickness = 2f, color = Color(0.3f, 1f, 0.3f, 1f))
    }
}
