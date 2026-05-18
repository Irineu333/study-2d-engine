package com.neoutils.engine.games.pong

import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D

class Score(
    val textSize: Float = 48f,
    val color: Color = Color.WHITE,
) : Node2D() {

    var value: Int = 0
        private set

    fun increment() {
        value++
    }

    override fun onRender(renderer: Renderer) {
        renderer.drawText(value.toString(), worldPosition(), textSize, color)
    }
}
