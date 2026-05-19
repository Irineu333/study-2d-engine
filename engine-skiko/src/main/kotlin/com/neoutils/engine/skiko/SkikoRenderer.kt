package com.neoutils.engine.skiko

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.TextLine

/**
 * `Renderer` backed by a Skia `Canvas`. Bound per frame via [bind] so the same
 * instance can be reused without allocation. Text uses Skia's `Font` +
 * `TextLine` so `measureText` and `drawText` agree on the rasterized size.
 */
class SkikoRenderer : Renderer {

    private var canvas: Canvas? = null
    private val fontCache: HashMap<Float, Font> = HashMap()

    fun bind(canvas: Canvas) {
        this.canvas = canvas
    }

    fun unbind() {
        canvas = null
    }

    private fun required(): Canvas = checkNotNull(canvas) {
        "SkikoRenderer used outside a bound Skia Canvas; call bind() first."
    }

    private fun fontOf(size: Float): Font = fontCache.getOrPut(size) { Font(null, size) }

    private fun paint(color: Color, filled: Boolean, thickness: Float): Paint = Paint().apply {
        this.color = color.toSkiaArgb()
        mode = if (filled) PaintMode.FILL else PaintMode.STROKE
        strokeWidth = thickness
        isAntiAlias = true
    }

    override fun clear(color: Color) {
        required().clear(color.toSkiaArgb())
    }

    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {
        val c = required()
        val skRect = SkRect.makeXYWH(rect.origin.x, rect.origin.y, rect.size.x, rect.size.y)
        c.drawRect(skRect, paint(color, filled, thickness = 1f))
    }

    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {
        val c = required()
        c.drawCircle(center.x, center.y, radius, paint(color, filled, thickness))
    }

    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {
        val c = required()
        c.drawLine(from.x, from.y, to.x, to.y, paint(color, filled = false, thickness))
    }

    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {
        val c = required()
        val font = fontOf(size)
        val line = TextLine.make(text, font)
        // `position` is the top-left baseline anchor the engine expects.
        // Skia's `drawTextLine(x, y, …)` treats `y` as the baseline, so we
        // shift down by the ascent so the visual top edge matches `position.y`.
        val baselineY = position.y + (-font.metrics.ascent)
        c.drawTextLine(line, position.x, baselineY, paint(color, filled = true, thickness = 1f))
    }

    override fun measureText(text: String, size: Float): Vec2 {
        val font = fontOf(size)
        val line = TextLine.make(text, font)
        return Vec2(line.width, font.metrics.height)
    }
}

internal fun Color.toSkiaArgb(): Int {
    val a = (a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() and 0xFF
    val r = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt() and 0xFF
    val g = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt() and 0xFF
    val b = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt() and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
