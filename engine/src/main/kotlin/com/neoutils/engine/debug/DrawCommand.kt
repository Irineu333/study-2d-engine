package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color

/**
 * One enqueued immediate-mode draw primitive, mirroring a single `Renderer`
 * verb. Allocated per call by [DebugCanvas] and discarded at the end of the
 * frame ([DebugDraw.clearFrame]). Immutable data classes — the MVP accepts
 * per-command allocation for clarity over zero-GC (see `design.md` D5).
 */
sealed interface DrawCommand {

    data class Line(
        val from: Vec2,
        val to: Vec2,
        val color: Color,
        val thickness: Float,
    ) : DrawCommand

    data class Rect(
        val rect: com.neoutils.engine.math.Rect,
        val color: Color,
        val filled: Boolean,
    ) : DrawCommand

    data class Circle(
        val center: Vec2,
        val radius: Float,
        val color: Color,
        val filled: Boolean,
        val thickness: Float,
    ) : DrawCommand

    data class Polygon(
        val points: List<Vec2>,
        val color: Color,
    ) : DrawCommand

    data class Text(
        val position: Vec2,
        val text: String,
        val color: Color,
        val size: Float,
    ) : DrawCommand
}
