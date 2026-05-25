package com.neoutils.engine.scene

import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Single-line text drawn at the node's world position. Replaces the legacy
 * `Text` node; the name mirrors Godot's `Label`.
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
        renderer.drawText(text, worldPosition(), size, color)
        super.onDraw(renderer)
    }
}
