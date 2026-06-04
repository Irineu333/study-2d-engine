package com.neoutils.engine.games.helloworld

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Label

class CenteredLabel : Label() {

    override fun onDraw(renderer: Renderer) {
        val surface = tree?.size ?: return

        val measured = renderer.measureText(text, fontSize)

        val position = Vec2(
            x = (surface.x - measured.x) / 2f,
            y = (surface.y - measured.y) / 2f
        )

        renderer.drawText(
            text,
            position,
            fontSize,
            color,
        )
    }
}
