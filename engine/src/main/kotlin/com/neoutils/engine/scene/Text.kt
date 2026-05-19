package com.neoutils.engine.scene

import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

@Serializable
class Text : Node2D() {

    @Inspect
    var text: String = ""

    @Inspect
    var size: Float = 12f

    @Inspect
    var color: Color = Color.WHITE

    override fun onRender(renderer: Renderer) {
        renderer.drawText(text, worldPosition(), size, color)
    }
}
