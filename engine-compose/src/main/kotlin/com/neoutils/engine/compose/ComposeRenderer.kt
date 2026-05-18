package com.neoutils.engine.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint

/**
 * Renderer backed by a Compose `DrawScope`. The scope is rebound each frame
 * via [bind] so a single instance can be reused across frames without
 * allocations.
 */
class ComposeRenderer : Renderer {

    private var scope: DrawScope? = null

    fun bind(drawScope: DrawScope) {
        scope = drawScope
    }

    fun unbind() {
        scope = null
    }

    private fun required(): DrawScope = checkNotNull(scope) {
        "ComposeRenderer used outside a DrawScope; call bind() first."
    }

    override fun clear(color: Color) {
        val s = required()
        s.drawRect(color = color.toUi(), topLeft = Offset.Zero, size = s.size)
    }

    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {
        val s = required()
        s.drawRect(
            color = color.toUi(),
            topLeft = Offset(rect.origin.x, rect.origin.y),
            size = Size(rect.size.x, rect.size.y),
            style = if (filled) Fill else Stroke(width = 1f),
        )
    }

    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean) {
        val s = required()
        s.drawCircle(
            color = color.toUi(),
            center = Offset(center.x, center.y),
            radius = radius,
            style = if (filled) Fill else Stroke(width = 1f),
        )
    }

    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {
        val s = required()
        val paint = Paint().apply {
            this.color = color.toSkiaArgb()
            isAntiAlias = true
        }
        val font = Font(null, size)
        s.drawIntoCanvas { canvas ->
            canvas.skiaCanvas.drawString(text, position.x, position.y + size, font, paint)
        }
    }
}

internal fun Color.toUi(): UiColor = UiColor(r, g, b, a)

internal fun Color.toSkiaArgb(): Int {
    val ai = (a.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
    val ri = (r.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
    val gi = (g.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
    val bi = (b.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
    return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
}
