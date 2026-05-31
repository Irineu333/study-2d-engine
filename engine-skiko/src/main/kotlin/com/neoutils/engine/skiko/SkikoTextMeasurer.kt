package com.neoutils.engine.skiko

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.TextMeasurer
import org.jetbrains.skia.Font
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface

/**
 * Off-frame [TextMeasurer] backed by Skia's `Font` + `TextLine`. Mirrors
 * `SkikoRenderer.measureText` exactly — same default typeface and the same
 * `Vec2(line.width, font.metrics.height)` formula — so a `Label`'s measured
 * bounds agree with what the renderer rasterizes. Needs no bound `Canvas`:
 * `Font`/`TextLine` are pure metric objects, so the host can wire this onto
 * `SceneTree.textMeasurer` before the first frame.
 */
class SkikoTextMeasurer : TextMeasurer {

    private val typeface: Typeface = resolveDefaultTypeface()
    private val fontCache: HashMap<Float, Font> = HashMap()
    private val lineCache: HashMap<Pair<String, Float>, TextLine> = HashMap()

    private fun fontOf(size: Float): Font = fontCache.getOrPut(size) { Font(typeface, size) }

    private fun lineOf(text: String, size: Float): TextLine =
        lineCache.getOrPut(text to size) { TextLine.make(text, fontOf(size)) }

    override fun measureText(text: String, size: Float): Vec2 =
        Vec2(lineOf(text, size).width, fontOf(size).metrics.height)
}
