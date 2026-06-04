package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Filled rectangle anchored at the node's local origin with the given `size`
 * (inherited from [Control]). `SceneTree.render` applies the node's world
 * transform (position, rotation, scale) via `Renderer.pushTransform` around
 * this `onDraw`, so ancestor rotation and scale reach the rect visually.
 *
 * Non-interactive by default (`mouseFilter = IGNORE`): a press passes through
 * a `ColorRect` to whatever is behind it.
 */
@Serializable
open class ColorRect : Control() {

    init {
        size = Vec2(10f, 10f)
        mouseFilter = MouseFilter.IGNORE
    }

    @Inspect
    var color: Color = Color.WHITE

    override fun onDraw(renderer: Renderer) {
        renderer.drawRect(Rect(Vec2.ZERO, size), color, filled = true)
        super.onDraw(renderer)
    }
}
