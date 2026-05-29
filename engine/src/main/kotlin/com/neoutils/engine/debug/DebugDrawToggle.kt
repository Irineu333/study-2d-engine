package com.neoutils.engine.debug

import com.neoutils.engine.render.Renderer

/**
 * Proxy `ScreenDebugWidget` that gives the immediate-draw facade a single
 * `"Debug Draw"` row in the `DebugHud`. Draws nothing itself — its only job
 * is to surface and flip [DebugDraw.enabled]: [enabled] delegates straight to
 * the backing facade, so toggling the HUD row controls both the world and
 * screen canvases at once.
 */
internal class DebugDrawToggle(private val draw: DebugDraw) : ScreenDebugWidget() {

    override val title: String = "Debug Draw"

    override var enabled: Boolean
        get() = draw.enabled
        set(value) { draw.enabled = value }

    init { name = "DebugDrawToggle" }

    override fun drawDebug(renderer: Renderer) {
        // Pure toggle proxy — the backing nodes do the drawing.
    }
}
