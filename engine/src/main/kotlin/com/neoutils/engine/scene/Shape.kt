package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer

class Shape(
    var kind: Kind = Kind.Rect,
    var size: Vec2 = Vec2(10f, 10f),
    var color: Color = Color.WHITE,
    var filled: Boolean = true,
) : Node2D() {

    enum class Kind { Rect, Circle }

    override fun onRender(renderer: Renderer) {
        val pos = worldPosition()
        val w = size.x * transform.scale.x
        val h = size.y * transform.scale.y
        when (kind) {
            Kind.Rect -> renderer.drawRect(Rect(pos, Vec2(w, h)), color, filled)
            Kind.Circle -> renderer.drawCircle(
                center = Vec2(pos.x + w / 2f, pos.y + h / 2f),
                radius = w / 2f,
                color = color,
                filled = filled,
            )
        }
    }
}
