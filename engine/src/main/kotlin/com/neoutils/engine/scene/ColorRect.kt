package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Filled rectangle anchored at the node's local origin with the given
 * [size]. `SceneTree.render` applies the node's world transform (position,
 * rotation, scale) via `Renderer.pushTransform` around this `onDraw`, so
 * ancestor rotation and scale now reach the rect visually.
 */
@Serializable
open class ColorRect : Node2D() {

    @Inspect
    var size: Vec2 = Vec2(10f, 10f)

    @Inspect
    var color: Color = Color.WHITE

    override fun onDraw(renderer: Renderer) {
        renderer.drawRect(Rect(Vec2.ZERO, size), color, filled = true)
        super.onDraw(renderer)
    }
}
