package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class BoundedLeaf(private val local: Rect?) : Node2D() {
    override fun localBounds(): Rect? = local
}

class Node2DBoundsTest {

    @Test
    fun `plain Node2D has null local bounds`() {
        assertNull(Node2D().localBounds())
    }

    @Test
    fun `plain Node2D has null world bounds`() {
        val node = Node2D().apply { position = Vec2(5f, 5f) }
        assertNull(node.worldBounds())
    }

    @Test
    fun `worldBounds composes translation and scale`() {
        val leaf = BoundedLeaf(Rect(Vec2.ZERO, Vec2(10f, 10f))).apply {
            transform = Transform(position = Vec2(5f, 5f), scale = Vec2(2f, 2f))
        }
        assertEquals(Rect(Vec2(5f, 5f), Vec2(20f, 20f)), leaf.worldBounds())
    }

    @Test
    fun `worldBounds of a rotated node is the enclosing AABB`() {
        val leaf = BoundedLeaf(Rect(Vec2(-5f, -5f), Vec2(10f, 10f))).apply {
            transform = Transform(rotation = (PI / 4.0).toFloat())
        }
        val box = leaf.worldBounds()!!
        // 45°-rotated 10×10 square: half-extent grows to ~7.07 on each axis.
        assertApprox(-7.071f, box.left)
        assertApprox(-7.071f, box.top)
        assertApprox(14.142f, box.size.x)
        assertApprox(14.142f, box.size.y)
    }

    @Test
    fun `treeBounds unions children of a pivot`() {
        val pivot = Node2D()
        pivot.addChild(BoundedLeaf(Rect(Vec2.ZERO, Vec2(10f, 10f))))
        pivot.addChild(
            BoundedLeaf(Rect(Vec2.ZERO, Vec2(10f, 10f))).apply { position = Vec2(20f, 20f) },
        )
        assertEquals(Rect(Vec2(0f, 0f), Vec2(30f, 30f)), pivot.treeBounds())
    }

    @Test
    fun `treeBounds does not descend into CanvasLayer`() {
        val pivot = Node2D()
        pivot.addChild(BoundedLeaf(Rect(Vec2.ZERO, Vec2(10f, 10f))))
        val layer = CanvasLayer()
        // Screen-space child far away — must be excluded from the world union.
        layer.addChild(
            BoundedLeaf(Rect(Vec2.ZERO, Vec2(10f, 10f))).apply { position = Vec2(500f, 500f) },
        )
        pivot.addChild(layer)
        assertEquals(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), pivot.treeBounds())
    }

    @Test
    fun `treeBounds of an empty subtree is null`() {
        val pivot = Node2D()
        pivot.addChild(Node2D())
        assertNull(pivot.treeBounds())
    }

    @Test
    fun `treeBounds includes descendants of a pivot with null local bounds`() {
        val root = Node2D()
        val pivot = Node2D().apply { position = Vec2(10f, 0f) }
        pivot.addChild(BoundedLeaf(Rect(Vec2.ZERO, Vec2(4f, 4f))))
        root.addChild(pivot)
        assertEquals(Rect(Vec2(10f, 0f), Vec2(4f, 4f)), root.treeBounds())
    }

    private fun assertApprox(expected: Float, actual: Float, eps: Float = 1e-3f) {
        assertTrue(kotlin.math.abs(expected - actual) < eps, "expected $expected, actual $actual")
    }
}
