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
 *
 * `Label` is a [Control] **min-size** leaf: its rect is the measured text size,
 * not a settable `size: Vec2`. The font height lives in [fontSize] (renamed
 * from the former `size` so it does not collide with `Control.size`). Anchors
 * position the measured rect; a full-rect Label centers its text on the surface
 * (the slack-centering rule in `Control.resolveLayout`), which is how a
 * game-over banner centers without measuring text in a `_draw` hack.
 * Non-interactive by default (`mouseFilter = IGNORE`).
 */
@Serializable
open class Label : Control() {

    init {
        mouseFilter = MouseFilter.IGNORE
    }

    @Inspect
    var text: String = ""

    /** Font height in renderer units (formerly named `size`). */
    @Inspect
    var fontSize: Float = 12f

    @Inspect
    var color: Color = Color.WHITE

    /**
     * Drawn rect in the local frame, measured off-frame via
     * `tree.textMeasurer`. `null` when the label is detached from any tree or
     * its tree has no measurer wired — measuring text needs font metrics this
     * pure query cannot synthesize. Correct even before the first draw. Does
     * **not** use the inherited `Control.size` (a Label is min-size).
     */
    override fun localBounds(): Rect? {
        val measurer = tree?.textMeasurer ?: return null
        return Rect(Vec2.ZERO, measurer.measureText(text, fontSize))
    }

    /** Min-size for the anchor layout pass: the measured text size when a
     *  measurer is reachable, otherwise the anchor-derived size (no centering). */
    override fun layoutSize(anchorSize: Vec2): Vec2 {
        val measurer = tree?.textMeasurer ?: return anchorSize
        return measurer.measureText(text, fontSize)
    }

    override fun onDraw(renderer: Renderer) {
        renderer.drawText(text, Vec2.ZERO, fontSize, color)
        super.onDraw(renderer)
    }
}
