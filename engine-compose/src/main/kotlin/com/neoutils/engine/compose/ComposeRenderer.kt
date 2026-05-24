package com.neoutils.engine.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer

/**
 * Renderer backed by a Compose `DrawScope`. The scope is rebound each frame
 * via [bind] so a single instance can be reused across frames without
 * allocations. Text rendering goes through Compose's [TextMeasurer] so the
 * platform font fallback chain works the same way as for the rest of the
 * Compose UI.
 */
class ComposeRenderer(
    private val textMeasurer: TextMeasurer,
) : Renderer {

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

    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {
        val s = required()
        s.drawCircle(
            color = color.toUi(),
            center = Offset(center.x, center.y),
            radius = radius,
            style = if (filled) Fill else Stroke(width = thickness),
        )
    }

    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {
        val s = required()
        s.drawLine(
            color = color.toUi(),
            start = Offset(from.x, from.y),
            end = Offset(to.x, to.y),
            strokeWidth = thickness,
        )
    }

    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {
        val s = required()
        val measured = textMeasurer.measure(text = text, style = textStyle(size, color))
        s.drawText(textLayoutResult = measured, topLeft = Offset(position.x, position.y))
    }

    override fun measureText(text: String, size: Float): Vec2 {
        val measured = textMeasurer.measure(text = text, style = textStyle(size, Color.WHITE))
        return Vec2(measured.size.width.toFloat(), measured.size.height.toFloat())
    }

    override fun drawPolygon(points: List<Vec2>, color: Color) {
        if (points.size < 3) return
        val s = required()
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            close()
        }
        s.drawPath(path, color = color.toUi(), style = Fill)
    }

    private fun textStyle(size: Float, color: Color): TextStyle = TextStyle(
        color = color.toUi(),
        fontSize = TextUnit(size, TextUnitType.Sp),
    )
}

internal fun Color.toUi(): UiColor = UiColor(r, g, b, a)
