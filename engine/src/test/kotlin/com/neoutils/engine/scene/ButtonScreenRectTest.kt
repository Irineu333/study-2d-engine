package com.neoutils.engine.scene

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.tree.SceneTree
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ClickInput(private val pointer: Vec2) : Input {
    override val pointerPosition: Vec2 get() = pointer
    override var mouseClickConsumed: Boolean = false
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = false
    override fun isMouseDown(button: MouseButton): Boolean = button == MouseButton.Left
    override fun wasMouseClickedRaw(button: MouseButton): Boolean = button == MouseButton.Left
}

class ButtonScreenRectTest {

    @Test
    fun `unrotated rect matches translated and scaled size`() {
        val button = Button().apply {
            size = Vec2(80f, 24f)
            transform = Transform(position = Vec2(10f, 5f), scale = Vec2(1f, 1f))
        }
        assertEquals(com.neoutils.engine.math.Rect(Vec2(10f, 5f), Vec2(80f, 24f)), button.screenRect())
    }

    @Test
    fun `rotated rect is the enclosing AABB with swapped extents`() {
        val button = Button().apply {
            size = Vec2(80f, 24f)
            transform = Transform(rotation = (PI / 2.0).toFloat())
        }
        val rect = button.screenRect()
        // 90° rotation about the origin swaps width and height of the AABB.
        assertApprox(80f, rect.size.y)
        assertApprox(24f, rect.size.x)
    }

    @Test
    fun `hit-test registers a click inside the rotated rect`() {
        val tree = SceneTree(Node())
        tree.start()
        val layer = CanvasLayer()
        // Rotate 90° about origin: local (0..80, 0..24) maps to x∈[-24,0], y∈[0,80].
        val button = Button().apply {
            size = Vec2(80f, 24f)
            transform = Transform(rotation = (PI / 2.0).toFloat())
        }
        layer.addChild(button)
        tree.root.addChild(layer)

        // A point at (-12, 40): inside the rotated rect, outside the old
        // scale-only rect (which was x∈[0,80], y∈[0,24]).
        val input = ClickInput(Vec2(-12f, 40f))
        tree.hitTestUI(input)
        assertTrue(input.mouseClickConsumed, "click should be absorbed by the rotated button")
    }

    private fun assertApprox(expected: Float, actual: Float, eps: Float = 1e-3f) {
        assertTrue(kotlin.math.abs(expected - actual) < eps, "expected $expected, actual $actual")
    }
}
