package com.neoutils.engine.render

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RendererTransformStackTest {

    @Test
    fun `push and pop balanced track depth`() {
        val r = FakeRenderer()
        r.pushTransform(Vec2(10f, 20f), 0f, Vec2.ONE)
        r.pushTransform(Vec2.ZERO, 0f, Vec2(2f, 2f))
        r.popTransform()
        r.popTransform()
        assertEquals(0, r.depth)
        assertEquals(listOf("push", "push", "pop", "pop"), r.events)
    }

    @Test
    fun `empty pop fails fast`() {
        val r = FakeRenderer()
        assertFailsWith<IllegalStateException> { r.popTransform() }
    }

    @Test
    fun `nested pop after extra push still throws when over-popped`() {
        val r = FakeRenderer()
        r.pushTransform(Vec2.ZERO, 0f, Vec2.ONE)
        r.popTransform()
        assertFailsWith<IllegalStateException> { r.popTransform() }
    }

    @Test
    fun `top reflects last push`() {
        val r = FakeRenderer()
        r.pushTransform(Vec2(5f, 5f), 0f, Vec2(2f, 2f))
        r.pushTransform(Vec2(1f, 1f), 1.5f, Vec2(0.5f, 0.5f))
        val top = r.top()
        assertTrue(top != null)
        assertEquals(Vec2(1f, 1f), top.translation)
        assertEquals(1.5f, top.rotation)
        assertEquals(Vec2(0.5f, 0.5f), top.scale)
    }

    @Test
    fun `nested clips intersect`() {
        val r = FakeRenderer()
        r.pushClip(Rect(Vec2(0f, 0f), Vec2(100f, 100f)))
        r.pushClip(Rect(Vec2(50f, 50f), Vec2(150f, 150f)))
        // Effective clip is the intersection (50,50)..(100,100).
        assertEquals(Rect(Vec2(50f, 50f), Vec2(50f, 50f)), r.effectiveClip())
        r.popClip()
        assertEquals(Rect(Vec2(0f, 0f), Vec2(100f, 100f)), r.effectiveClip())
        r.popClip()
        assertEquals(null, r.effectiveClip())
    }

    @Test
    fun `empty popClip fails fast`() {
        val r = FakeRenderer()
        assertFailsWith<IllegalStateException> { r.popClip() }
    }

    @Test
    fun `clip and transform interleave restores both`() {
        val r = FakeRenderer()
        r.pushClip(Rect(Vec2(10f, 10f), Vec2(40f, 40f)))
        r.pushTransform(Vec2(5f, 5f), 0f, Vec2.ONE)
        r.popTransform()
        r.popClip()
        assertEquals(0, r.depth)
        assertEquals(null, r.effectiveClip())
        assertEquals(listOf("clip", "push", "pop", "unclip"), r.events)
    }
}

private data class StackFrame(val translation: Vec2, val rotation: Float, val scale: Vec2)

/** Minimal `Renderer` recording transform- and clip-stack ops; draw methods are no-ops. */
private class FakeRenderer : Renderer {

    private val stack: ArrayDeque<StackFrame> = ArrayDeque()
    // Each entry is the effective (already-intersected) clip at that depth, so
    // the top is the rect every draw would be restricted to.
    private val clips: ArrayDeque<Rect> = ArrayDeque()
    val events: MutableList<String> = mutableListOf()

    val depth: Int get() = stack.size
    fun top(): StackFrame? = stack.lastOrNull()
    fun effectiveClip(): Rect? = clips.lastOrNull()

    override fun clear(color: Color) {}
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {}
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {}
    override fun measureText(text: String, size: Float): Vec2 = Vec2.ZERO
    override fun drawPolygon(points: List<Vec2>, color: Color) {}

    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {
        stack.addLast(StackFrame(translation, rotation, scale))
        events += "push"
    }

    override fun popTransform() {
        check(stack.isNotEmpty()) { "popTransform on empty transform stack" }
        stack.removeLast()
        events += "pop"
    }

    override fun pushClip(rect: Rect) {
        val current = clips.lastOrNull()
        clips.addLast(if (current == null) rect else intersect(current, rect))
        events += "clip"
    }

    override fun popClip() {
        check(clips.isNotEmpty()) { "popClip on empty clip stack" }
        clips.removeLast()
        events += "unclip"
    }

    private fun intersect(a: Rect, b: Rect): Rect {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        return Rect(Vec2(left, top), Vec2((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)))
    }
}
