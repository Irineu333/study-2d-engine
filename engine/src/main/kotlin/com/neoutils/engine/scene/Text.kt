package com.neoutils.engine.scene

import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer

class Text(
    var text: String = "",
    var size: Float = 12f,
    var color: Color = Color.WHITE,
) : Node2D() {

    override fun onRender(renderer: Renderer) {
        renderer.drawText(text, transform.position, size, color)
    }
}
