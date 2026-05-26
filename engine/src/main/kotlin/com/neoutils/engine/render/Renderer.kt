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

    /**
     * Pushes a new entry onto a LIFO transform stack composing
     * `translate(translation) ∘ rotate(rotation) ∘ scale(scale)` over the
     * current top. All subsequent `draw*` calls render under the resulting
     * cumulative transform until the matching [popTransform]. Pushes nest
     * (deeper pushes compose on top of shallower ones).
     *
     * [rotation] is in radians and is applied around the **new** origin
     * established by the preceding translation (i.e. `(0, 0)` in the
     * translated frame). A local point `(x, y)` drawn after the push appears
     * at `translation + R(rotation) · (scale.x · x, scale.y · y)`.
     *
     * The stack starts as identity at every backend-defined frame boundary
     * (e.g. each `SkikoRenderer.bind` or each new `DrawScope` invocation).
     * Every `pushTransform` issued during a frame MUST be matched by a
     * `popTransform` before the frame boundary ends.
     */
    fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2)

    /**
     * Pops the top entry of the transform stack, restoring whatever transform
     * was active before the matching [pushTransform]. Throws
     * [IllegalStateException] when called on an empty stack.
     */
    fun popTransform()
}
