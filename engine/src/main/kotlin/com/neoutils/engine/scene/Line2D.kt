package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Polyline drawn by chaining consecutive points with `Renderer.drawLine`.
 * `points` are in local space; the node's world transform is applied by
 * `SceneTree.render` via `Renderer.pushTransform` around this `onDraw`.
 */
@Serializable
open class Line2D : Node2D() {

    @Inspect
    var points: List<Vec2> = emptyList()

    @Inspect
    var thickness: Float = 1f

    @Inspect
    var color: Color = Color.WHITE

    override fun onDraw(renderer: Renderer) {
        if (points.size >= 2) {
            for (i in 1 until points.size) {
                renderer.drawLine(
                    from = points[i - 1],
                    to = points[i],
                    thickness = thickness,
                    color = color,
                )
            }
        }
        super.onDraw(renderer)
    }
}
