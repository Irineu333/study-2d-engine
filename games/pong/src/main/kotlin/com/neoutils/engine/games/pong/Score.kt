package com.neoutils.engine.games.pong

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Text

class Score(
    position: Vec2,
    size: Float = 48f,
    color: Color = Color.WHITE,
) : Node2D() {

    var value: Int = 0
        private set

    private val text: Text = Text(text = "0", size = size, color = color).apply {
        transform = transform.copy(position = position)
    }

    init {
        addChild(text)
    }

    fun increment() {
        value++
        text.text = value.toString()
    }
}
