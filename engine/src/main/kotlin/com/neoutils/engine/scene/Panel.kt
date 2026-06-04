package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Filled rectangle in screen-space (when placed under a `CanvasLayer`) or
 * world-space (when placed elsewhere — discouraged for UI). The fill is
 * drawn first; an optional [border] is drawn on top via the same rect with
 * `filled = false`.
 *
 * `Panel` is distinct from `ColorRect` semantically: it carries a "UI frame"
 * intent and supports an outline. Use `ColorRect` for world-space fills. As a
 * [Control] it owns anchors/offsets, `visible` and `mouseFilter` (default
 * `STOP` — opaque UI); `size` is inherited from `Control`.
 */
@Serializable
open class Panel : Control() {

    init {
        size = Vec2(100f, 50f)
    }

    @Inspect
    var color: Color = Color.WHITE

    @Inspect
    var border: Border? = null

    override fun onDraw(renderer: Renderer) {
        val rect = Rect(Vec2.ZERO, size)
        renderer.drawRect(rect, color, filled = true)
        border?.let { b ->
            renderer.drawRect(rect, b.color, filled = false)
        }
        super.onDraw(renderer)
    }
}

/**
 * Outline descriptor for [Panel]. Width is in the same units as the renderer's
 * coordinate space (screen pixels under a `CanvasLayer`). Today the renderer's
 * `drawRect(filled=false)` does not honor per-call thickness — `width` is
 * carried for future extension and editor inspection.
 */
@Serializable
data class Border(
    val color: Color = Color.BLACK,
    val width: Float = 1f,
)
