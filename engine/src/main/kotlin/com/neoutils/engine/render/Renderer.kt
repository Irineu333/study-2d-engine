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
     * Draws the region [src] of [texture] into the destination rect [dst].
     *
     * [src] is in **texture pixels** (origin top-left, y down, like the source
     * PNG) and indexes the bitmap to sample — pass `Rect(Vec2.ZERO,
     * Vec2(texture.width, texture.height))` to draw the whole image. [dst] is
     * in **local space**, under the current transform stack (the same
     * composition `draw*` calls see), so the drawn image translates, rotates
     * and scales with the node it belongs to.
     *
     * Sampling is **nearest-neighbor** (no smoothing) — a hard requirement for
     * pixel-art (16/32px sprites scaled up must stay crisp, never blurred).
     *
     * [flipH] mirrors the image horizontally about the center of [dst] (turns a
     * character to face the other way) — a purely visual flip that does **not**
     * touch any node transform, so it never feeds a negative `scale.x` into the
     * transform stack, physics or hit-testing.
     */
    fun drawImage(texture: Texture, src: Rect, dst: Rect, flipH: Boolean = false)

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

    /**
     * Pushes a rectangular clip region onto a LIFO clip stack — the natural
     * pair of [pushTransform]/[popTransform]. [rect] is interpreted under the
     * **current transform stack** (the same composition that `draw*` calls
     * see). Every subsequent `draw*` is restricted to the intersection of all
     * clip rects currently on the stack: a deeper [pushClip] intersects with
     * (never widens) the current clip.
     *
     * The clip stack starts empty (no clip) at every backend-defined frame
     * boundary, exactly like the transform stack, and every [pushClip] issued
     * during a frame MUST be matched by a [popClip] before the frame ends.
     * Clip and transform pushes/pops MUST nest correctly when interleaved
     * (e.g. `pushClip` → `pushTransform` → `popTransform` → `popClip`), since
     * backends MAY share a single native save/restore stack for both.
     */
    fun pushClip(rect: Rect)

    /**
     * Pops the top entry of the clip stack, restoring the clip to the state
     * before the matching [pushClip]. Throws [IllegalStateException] when
     * called on an empty stack.
     */
    fun popClip()
}
