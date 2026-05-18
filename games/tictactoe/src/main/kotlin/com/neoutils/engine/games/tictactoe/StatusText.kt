package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node

class StatusText(
    var text: String = "",
    var size: Float = 22f,
    var color: Color = Color.WHITE,
    var baselineY: Float = 0f,
) : Node() {

    override fun onRender(renderer: Renderer) {
        val scene = rootScene() ?: return
        val bounds = renderer.measureText(text, size)
        val x = scene.width / 2f - bounds.x / 2f
        renderer.drawText(text, Vec2(x, baselineY), size, color)
    }
}
