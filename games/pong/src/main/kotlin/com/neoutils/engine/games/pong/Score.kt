package com.neoutils.engine.games.pong

import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class Score : Node2D() {

    @Inspect
    var textSize: Float = 48f

    @Inspect
    var color: Color = Color.WHITE

    @Transient
    var value: Int = 0
        private set

    fun increment() {
        value++
    }

    override fun onRender(renderer: Renderer) {
        renderer.drawText(value.toString(), worldPosition(), textSize, color)
    }
}
