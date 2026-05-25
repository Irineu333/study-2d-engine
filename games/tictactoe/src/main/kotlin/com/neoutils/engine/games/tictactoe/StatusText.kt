package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class StatusText : Node() {

    @Transient
    var text: String = ""

    @Inspect
    var size: Float = 22f

    @Inspect
    var color: Color = Color.WHITE

    @Inspect
    var baselineY: Float = 0f

    override fun onDraw(renderer: Renderer) {
        val tree = tree ?: return
        val bounds = renderer.measureText(text, size)
        // Center on the world width (viewport) — the camera projects this
        // onto the surface, so we get correct centering on any window size.
        val x = tree.viewport.size.x / 2f - bounds.x / 2f
        renderer.drawText(text, Vec2(x, baselineY), size, color)
    }
}
