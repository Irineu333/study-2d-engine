package com.neoutils.engine.lwjgl

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG
import org.lwjgl.nanovg.NanoVGGL3
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import java.io.File

/**
 * `Renderer` backed by NanoVG running over an OpenGL 3.3 core context (see
 * `LwjglHost` for window/context bootstrap). NanoVG state lives inside the
 * `nvgContext` handle, so we don't pool `Paint`-like objects the way
 * `SkikoRenderer` does — every primitive issues `nvgBeginPath` → set color →
 * `nvgFill`/`nvgStroke` directly.
 *
 * The default font is resolved from a per-OS candidate list of system TTF/TTC
 * files; if none resolve, [init] fails-fast with the list of paths tried —
 * see Decision 6 in `openspec/changes/engine-lwjgl/design.md` for the
 * trade-off vs. shipping a bundled fallback.
 */
class LwjglRenderer : Renderer {

    private var nvgContext: Long = NULL
    private var transformDepth: Int = 0
    private var clipDepth: Int = 0
    private var defaultFontId: Int = -1

    fun init() {
        checkNotNull(GL.getCapabilities()) {
            "LwjglRenderer.init() called without a current GL context — call glfwMakeContextCurrent + GL.createCapabilities first."
        }
        val ctx = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS or NanoVGGL3.NVG_STENCIL_STROKES)
        check(ctx != NULL) { "NanoVGGL3.nvgCreate returned NULL — GL context likely not 3.3 core." }
        nvgContext = ctx
        defaultFontId = registerDefaultFont(ctx)
    }

    fun bind(windowWidth: Int, windowHeight: Int, pixelRatio: Float) {
        NanoVG.nvgBeginFrame(requiredCtx(), windowWidth.toFloat(), windowHeight.toFloat(), pixelRatio)
        transformDepth = 0
        clipDepth = 0
    }

    fun unbind() {
        val leakedTransform = transformDepth
        val leakedClip = clipDepth
        NanoVG.nvgEndFrame(requiredCtx())
        check(leakedTransform == 0) {
            "LwjglRenderer.unbind() with $leakedTransform unmatched pushTransform call(s); every push MUST be matched by pop within a frame."
        }
        check(leakedClip == 0) {
            "LwjglRenderer.unbind() with $leakedClip unmatched pushClip call(s); every push MUST be matched by pop within a frame."
        }
    }

    /**
     * Off-frame text measurer sharing this renderer's NanoVG context and
     * registered font. Call after [init]; the returned measurer matches
     * [measureText] exactly and is safe to call outside a frame. Wired onto
     * `SceneTree.textMeasurer` by `LwjglHost` at startup.
     */
    fun createTextMeasurer(): LwjglTextMeasurer = LwjglTextMeasurer(requiredCtx(), defaultFontId)

    fun shutdown() {
        if (nvgContext != NULL) {
            NanoVGGL3.nvgDelete(nvgContext)
            nvgContext = NULL
            defaultFontId = -1
        }
    }

    /**
     * `Renderer.clear` is invoked by `LwjglHost` BEFORE `bind` per frame. The
     * implementation issues `glClear` directly: NanoVG draws additively, and
     * filling a fullscreen rect inside the frame would still leave depth/
     * stencil dirty for the next frame.
     */
    override fun clear(color: Color) {
        GL11.glClearColor(
            color.r.coerceIn(0f, 1f),
            color.g.coerceIn(0f, 1f),
            color.b.coerceIn(0f, 1f),
            color.a.coerceIn(0f, 1f),
        )
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_STENCIL_BUFFER_BIT)
    }

    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {
        val ctx = requiredCtx()
        NanoVG.nvgBeginPath(ctx)
        NanoVG.nvgRect(ctx, rect.origin.x, rect.origin.y, rect.size.x, rect.size.y)
        if (filled) {
            withColor(color) { c -> NanoVG.nvgFillColor(ctx, c); NanoVG.nvgFill(ctx) }
        } else {
            NanoVG.nvgStrokeWidth(ctx, 1f)
            withColor(color) { c -> NanoVG.nvgStrokeColor(ctx, c); NanoVG.nvgStroke(ctx) }
        }
    }

    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {
        val ctx = requiredCtx()
        NanoVG.nvgBeginPath(ctx)
        NanoVG.nvgCircle(ctx, center.x, center.y, radius)
        if (filled) {
            withColor(color) { c -> NanoVG.nvgFillColor(ctx, c); NanoVG.nvgFill(ctx) }
        } else {
            NanoVG.nvgStrokeWidth(ctx, thickness)
            withColor(color) { c -> NanoVG.nvgStrokeColor(ctx, c); NanoVG.nvgStroke(ctx) }
        }
    }

    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {
        val ctx = requiredCtx()
        NanoVG.nvgBeginPath(ctx)
        NanoVG.nvgMoveTo(ctx, from.x, from.y)
        NanoVG.nvgLineTo(ctx, to.x, to.y)
        NanoVG.nvgStrokeWidth(ctx, thickness)
        withColor(color) { c -> NanoVG.nvgStrokeColor(ctx, c); NanoVG.nvgStroke(ctx) }
    }

    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {
        val ctx = requiredCtx()
        NanoVG.nvgFontFaceId(ctx, defaultFontId)
        NanoVG.nvgFontSize(ctx, size)
        NanoVG.nvgTextAlign(ctx, NanoVG.NVG_ALIGN_LEFT or NanoVG.NVG_ALIGN_TOP)
        withColor(color) { c -> NanoVG.nvgFillColor(ctx, c); NanoVG.nvgText(ctx, position.x, position.y, text) }
    }

    override fun measureText(text: String, size: Float): Vec2 {
        val ctx = requiredCtx()
        NanoVG.nvgFontFaceId(ctx, defaultFontId)
        NanoVG.nvgFontSize(ctx, size)
        NanoVG.nvgTextAlign(ctx, NanoVG.NVG_ALIGN_LEFT or NanoVG.NVG_ALIGN_TOP)
        val bounds = FloatArray(4)
        NanoVG.nvgTextBounds(ctx, 0f, 0f, text, bounds)
        val ascender = FloatArray(1)
        val descender = FloatArray(1)
        val lineh = FloatArray(1)
        NanoVG.nvgTextMetrics(ctx, ascender, descender, lineh)
        return Vec2(bounds[2] - bounds[0], lineh[0])
    }

    override fun drawPolygon(points: List<Vec2>, color: Color) {
        if (points.size < 3) return
        val ctx = requiredCtx()
        NanoVG.nvgBeginPath(ctx)
        NanoVG.nvgMoveTo(ctx, points[0].x, points[0].y)
        for (i in 1 until points.size) NanoVG.nvgLineTo(ctx, points[i].x, points[i].y)
        NanoVG.nvgClosePath(ctx)
        withColor(color) { c -> NanoVG.nvgFillColor(ctx, c); NanoVG.nvgFill(ctx) }
    }

    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {
        val ctx = requiredCtx()
        NanoVG.nvgSave(ctx)
        NanoVG.nvgTranslate(ctx, translation.x, translation.y)
        NanoVG.nvgRotate(ctx, rotation)
        NanoVG.nvgScale(ctx, scale.x, scale.y)
        transformDepth++
    }

    override fun popTransform() {
        check(transformDepth > 0) { "popTransform on empty transform stack (LwjglRenderer)" }
        NanoVG.nvgRestore(requiredCtx())
        transformDepth--
    }

    // NanoVG has no scissor-pop, so the LIFO clip stack is emulated over its
    // own state stack: nvgSave snapshots the current scissor, nvgIntersectScissor
    // narrows it (so a deeper clip intersects the current one), and nvgRestore
    // pops back. `pushTransform` shares this same nvgSave/nvgRestore discipline,
    // so clip and transform pushes nest correctly when interleaved.
    override fun pushClip(rect: Rect) {
        val ctx = requiredCtx()
        NanoVG.nvgSave(ctx)
        NanoVG.nvgIntersectScissor(ctx, rect.origin.x, rect.origin.y, rect.size.x, rect.size.y)
        clipDepth++
    }

    override fun popClip() {
        check(clipDepth > 0) { "popClip on empty clip stack (LwjglRenderer)" }
        NanoVG.nvgRestore(requiredCtx())
        clipDepth--
    }

    private fun requiredCtx(): Long {
        check(nvgContext != NULL) { "LwjglRenderer used without init(); call init() after the GL context is current." }
        return nvgContext
    }

    private inline fun withColor(color: Color, block: (NVGColor) -> Unit) {
        MemoryStack.stackPush().use { stack ->
            val c = NVGColor.calloc(stack)
            NanoVG.nvgRGBAf(
                color.r.coerceIn(0f, 1f),
                color.g.coerceIn(0f, 1f),
                color.b.coerceIn(0f, 1f),
                color.a.coerceIn(0f, 1f),
                c,
            )
            block(c)
        }
    }
}

// `nvgCreateFont(ctx, name, path)` accepts only filesystem paths (not Java
// resource streams). Each OS gets a prioritized candidate list of TTF/TTC
// files known to ship by default; if none resolves we fail-fast with the
// tried list, as a bundled fallback is intentionally deferred (Decision 6).
private fun registerDefaultFont(ctx: Long): Int {
    val candidates = defaultFontCandidates()
    for (path in candidates) {
        if (!File(path).isFile) continue
        val id = NanoVG.nvgCreateFont(ctx, "default", path)
        if (id != -1) return id
    }
    error(
        "LwjglRenderer: failed to register default font; tried these system paths:\n" +
            candidates.joinToString("\n") { "  - $it" },
    )
}

private fun defaultFontCandidates(): List<String> {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") -> listOf(
            "/System/Library/Fonts/Helvetica.ttc",
            "/System/Library/Fonts/HelveticaNeue.ttc",
            "/Library/Fonts/Arial.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf",
            "/System/Library/Fonts/Geneva.ttf",
        )
        os.contains("win") -> listOf(
            "C:\\Windows\\Fonts\\arial.ttf",
            "C:\\Windows\\Fonts\\segoeui.ttf",
            "C:\\Windows\\Fonts\\tahoma.ttf",
        )
        else -> listOf(
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
            "/usr/share/fonts/TTF/DejaVuSans.ttf",
            "/usr/share/fonts/dejavu/DejaVuSans.ttf",
        )
    }
}
