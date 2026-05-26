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
}

private data class StackFrame(val translation: Vec2, val rotation: Float, val scale: Vec2)

/** Minimal `Renderer` recording transform-stack ops; draw methods are no-ops. */
private class FakeRenderer : Renderer {

    private val stack: ArrayDeque<StackFrame> = ArrayDeque()
    val events: MutableList<String> = mutableListOf()

    val depth: Int get() = stack.size
    fun top(): StackFrame? = stack.lastOrNull()

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
}
