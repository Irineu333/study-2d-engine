package com.neoutils.engine.lwjgl

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.TextMeasurer
import org.lwjgl.nanovg.NanoVG

/**
 * Off-frame [TextMeasurer] backed by NanoVG, sharing the renderer's
 * [nvgContext] and registered [fontId]. Mirrors `LwjglRenderer.measureText`
 * exactly (`nvgTextBounds` width + `nvgTextMetrics` line height). NanoVG's text
 * measurement queries are pure state reads that don't require an active
 * `nvgBeginFrame`/`nvgEndFrame`, so a `Label` can be measured before the first
 * frame. Construct via `LwjglRenderer.createTextMeasurer()` after `init()`.
 */
class LwjglTextMeasurer internal constructor(
    private val nvgContext: Long,
    private val fontId: Int,
) : TextMeasurer {

    override fun measureText(text: String, size: Float): Vec2 {
        NanoVG.nvgFontFaceId(nvgContext, fontId)
        NanoVG.nvgFontSize(nvgContext, size)
        NanoVG.nvgTextAlign(nvgContext, NanoVG.NVG_ALIGN_LEFT or NanoVG.NVG_ALIGN_TOP)
        val bounds = FloatArray(4)
        NanoVG.nvgTextBounds(nvgContext, 0f, 0f, text, bounds)
        val ascender = FloatArray(1)
        val descender = FloatArray(1)
        val lineh = FloatArray(1)
        NanoVG.nvgTextMetrics(nvgContext, ascender, descender, lineh)
        return Vec2(bounds[2] - bounds[0], lineh[0])
    }
}
