package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
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

    /**
     * Drawn rect in the local frame, measured off-frame via
     * `tree.textMeasurer`. `null` when the label is detached from any tree or
     * its tree has no measurer wired — measuring text needs font metrics this
     * pure query cannot synthesize. Correct even before the first draw.
     */
    override fun localBounds(): Rect? {
        val measurer = tree?.textMeasurer ?: return null
        return Rect(Vec2.ZERO, measurer.measureText(text, size))
    }

    override fun onDraw(renderer: Renderer) {
        renderer.drawText(text, Vec2.ZERO, size, color)
        super.onDraw(renderer)
    }
}
