package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Filled circle centered at the node's local origin. `SceneTree.render`
 * applies the node's world transform (position, rotation, scale) via
 * `Renderer.pushTransform` around this `onDraw`, so the rendered position
 * and radius scale follow the ancestor chain automatically.
 */
@Serializable
open class Circle2D : Node2D() {

    @Inspect
    var radius: Float = 5f

    @Inspect
    var color: Color = Color.WHITE

    override fun onDraw(renderer: Renderer) {
        renderer.drawCircle(
            center = Vec2.ZERO,
            radius = radius,
            color = color,
            filled = true,
        )
        super.onDraw(renderer)
    }
}
