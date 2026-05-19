package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Renders a primitive (rectangle or circle) in world space. Position and
 * scale are honored via `worldTransform()`. Rotation is composed into the
 * world transform but **not** applied to the drawing yet — the visual
 * rotation lands when `Renderer.withTransform` is introduced in a later
 * change. Setting a non-zero rotation today will affect collider bounds but
 * not the rendered shape.
 */
@Serializable
class Shape : Node2D() {

    @Inspect
    var kind: Kind = Kind.Rect

    @Inspect
    var size: Vec2 = Vec2(10f, 10f)

    @Inspect
    var color: Color = Color.WHITE

    @Inspect
    var filled: Boolean = true

    enum class Kind { Rect, Circle }

    override fun onRender(renderer: Renderer) {
        val world = worldTransform()
        val w = size.x * world.scale.x
        val h = size.y * world.scale.y
        when (kind) {
            Kind.Rect -> renderer.drawRect(Rect(world.position, Vec2(w, h)), color, filled)
            Kind.Circle -> renderer.drawCircle(
                center = Vec2(world.position.x + w / 2f, world.position.y + h / 2f),
                radius = w / 2f,
                color = color,
                filled = filled,
            )
        }
    }
}
