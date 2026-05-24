package com.neoutils.engine.render

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2

interface Renderer {

    fun clear(color: Color)

    fun drawRect(rect: Rect, color: Color, filled: Boolean = true)

    fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean = true, thickness: Float = 1f)

    fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)

    fun drawText(text: String, position: Vec2, size: Float, color: Color)

    fun measureText(text: String, size: Float): Vec2

    /**
     * Fills the polygon defined by [points] (at least 3 vertices, in world
     * coordinates) with [color]. Edges close from the last point back to the
     * first. Backends decompose into a path; concavity is allowed but
     * self-intersection is undefined.
     */
    fun drawPolygon(points: List<Vec2>, color: Color)
}
