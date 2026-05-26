package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Single-line text drawn at the node's local origin. `SceneTree.render`
 * applies the world transform via `Renderer.pushTransform` around this
 * `onDraw`. Alignment math (via `Renderer.measureText`) remains relative
 * to the local origin.
 */
@Serializable
open class Label : Node2D() {

    @Inspect
    var text: String = ""

    @Inspect
    var size: Float = 12f

    @Inspect
    var color: Color = Color.WHITE

    override fun onDraw(renderer: Renderer) {
        renderer.drawText(text, Vec2.ZERO, size, color)
        super.onDraw(renderer)
    }
}
