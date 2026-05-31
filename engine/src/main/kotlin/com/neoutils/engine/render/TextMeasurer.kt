package com.neoutils.engine.render

import com.neoutils.engine.math.Vec2

/**
 * Off-frame font metrics. Distinct from [Renderer.measureText] in that it is
 * reachable **outside** a render pass: `Label.localBounds()` queries text size
 * without a bound canvas, so an editor or debug inspector can box a `Label`
 * that has never been drawn.
 *
 * Lives in `:engine` (Kotlin pure) and exposes only `:engine` math types — no
 * render/UI framework type leaks across this seam (invariant #2). The host
 * wires a backend implementation onto [com.neoutils.engine.tree.SceneTree.textMeasurer]
 * at startup; both shipped backends (Skiko, LWJGL) provide one.
 */
fun interface TextMeasurer {

    /** Width and height a [text] run occupies when drawn at font [size]. */
    fun measureText(text: String, size: Float): Vec2
}
