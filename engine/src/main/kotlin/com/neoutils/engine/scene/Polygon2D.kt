package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Filled polygon defined by vertices in local space. The node's world
 * transform (position, rotation, scale) is applied by `SceneTree.render`
 * via `Renderer.pushTransform` around this `onDraw`, so `points` are
 * submitted directly to `drawPolygon`. Concavity is allowed;
 * self-intersection is undefined.
 */
@Serializable
open class Polygon2D : Node2D() {

    @Inspect
    var points: List<Vec2> = emptyList()

    @Inspect
    var color: Color = Color.WHITE

    override fun onDraw(renderer: Renderer) {
        if (points.size >= 3) renderer.drawPolygon(points, color)
        super.onDraw(renderer)
    }
}
