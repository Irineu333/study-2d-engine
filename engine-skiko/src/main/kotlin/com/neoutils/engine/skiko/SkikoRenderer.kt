package com.neoutils.engine.skiko

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface

/**
 * `Renderer` backed by a Skia `Canvas`. Bound per frame via [bind] so the same
 * instance can be reused without allocation. Text uses Skia's `Font` +
 * `TextLine` so `measureText` and `drawText` agree on the rasterized size.
 *
 * `Paint` and `TextLine` are pooled because both wrap native handles —
 * allocating one per `draw*` call dominated the per-frame cost on Pong (10 %
 * FPS gap vs Compose). See change `add-skiko-runtime` section 12 for the
 * measurement that motivated this.
 */
class SkikoRenderer : Renderer {

    private var canvas: Canvas? = null
    private val defaultTypeface: Typeface = resolveDefaultTypeface()
    private val fontCache: HashMap<Float, Font> = HashMap()
    private val textLineCache: HashMap<TextLineKey, TextLine> = HashMap()
    private val sharedPaint: Paint = Paint().apply { isAntiAlias = true }
    private var transformDepth: Int = 0

    fun bind(canvas: Canvas) {
        this.canvas = canvas
        transformDepth = 0
    }

    fun unbind() {
        val leaked = transformDepth
        canvas = null
        check(leaked == 0) {
            "SkikoRenderer.unbind() with $leaked unmatched pushTransform call(s); every push MUST be matched by pop within a frame."
        }
    }

    private fun required(): Canvas = checkNotNull(canvas) {
        "SkikoRenderer used outside a bound Skia Canvas; call bind() first."
    }

    private fun fontOf(size: Float): Font = fontCache.getOrPut(size) { Font(defaultTypeface, size) }

    private fun textLineOf(text: String, font: Font, size: Float): TextLine =
        textLineCache.getOrPut(TextLineKey(text, size)) { TextLine.make(text, font) }

    private fun configurePaint(color: Color, filled: Boolean, thickness: Float): Paint {
        val p = sharedPaint
        p.color = color.toSkiaArgb()
        p.mode = if (filled) PaintMode.FILL else PaintMode.STROKE
        p.strokeWidth = thickness
        return p
    }

    override fun clear(color: Color) {
        required().clear(color.toSkiaArgb())
    }

    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {
        val c = required()
        val skRect = SkRect.makeXYWH(rect.origin.x, rect.origin.y, rect.size.x, rect.size.y)
        c.drawRect(skRect, configurePaint(color, filled, thickness = 1f))
    }

    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {
        val c = required()
        c.drawCircle(center.x, center.y, radius, configurePaint(color, filled, thickness))
    }

    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {
        val c = required()
        c.drawLine(from.x, from.y, to.x, to.y, configurePaint(color, filled = false, thickness))
    }

    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {
        val c = required()
        val font = fontOf(size)
        val line = textLineOf(text, font, size)
        // `position` is the top-left baseline anchor the engine expects.
        // Skia's `drawTextLine(x, y, …)` treats `y` as the baseline, so we
        // shift down by the ascent so the visual top edge matches `position.y`.
        val baselineY = position.y + (-font.metrics.ascent)
        c.drawTextLine(line, position.x, baselineY, configurePaint(color, filled = true, thickness = 1f))
    }

    override fun measureText(text: String, size: Float): Vec2 {
        val font = fontOf(size)
        val line = textLineOf(text, font, size)
        return Vec2(line.width, font.metrics.height)
    }

    override fun drawPolygon(points: List<Vec2>, color: Color) {
        if (points.size < 3) return
        val c = required()
        val builder = PathBuilder()
        builder.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) builder.lineTo(points[i].x, points[i].y)
        builder.closePath()
        c.drawPath(builder.snapshot(), configurePaint(color, filled = true, thickness = 1f))
    }

    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {
        val c = required()
        c.save()
        c.translate(translation.x, translation.y)
        // Skia's `Canvas.rotate` expects degrees; engine `Transform.rotation` is radians.
        c.rotate((rotation * 180f / kotlin.math.PI.toFloat()))
        c.scale(scale.x, scale.y)
        transformDepth++
    }

    override fun popTransform() {
        check(transformDepth > 0) { "popTransform on empty transform stack (SkikoRenderer)" }
        required().restore()
        transformDepth--
    }
}

private data class TextLineKey(val text: String, val size: Float)

// `FontMgr.default.matchFamilyStyle(null, ...)` may return an empty typeface on
// some platforms (its docs warn that "most systems don't have a default system
// family"). With zeroed metrics, `font.metrics.ascent == 0` and `drawText`'s
// baseline math collapses, putting glyphs above the requested top-anchored y.
// Resolve against a prioritized list of platform families, then any enumerated
// family, before falling back to the empty typeface.
internal fun resolveDefaultTypeface(): Typeface {
    val mgr = FontMgr.default
    val preferred: Array<String?> = arrayOf(
        "SF Pro Display", "SF Pro Text", "Helvetica Neue", "Helvetica",
        "Arial", "Segoe UI", "DejaVu Sans", "Liberation Sans",
    )
    mgr.matchFamiliesStyle(preferred, FontStyle.NORMAL)?.let { return it }
    val familiesCount = mgr.familiesCount
    for (idx in 0 until familiesCount) {
        val name = mgr.getFamilyName(idx)
        val typeface = mgr.matchFamilyStyle(name, FontStyle.NORMAL)
        if (typeface != null) return typeface
    }
    return Typeface.makeEmpty()
}

internal fun Color.toSkiaArgb(): Int {
    val a = (a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() and 0xFF
    val r = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt() and 0xFF
    val g = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt() and 0xFF
    val b = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt() and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
