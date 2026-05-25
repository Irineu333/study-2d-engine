package com.neoutils.engine.scene

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldTransformTest {

    @Test
    fun `pure translation chain sums positions`() {
        val root = Node()
        val a = Node2D().apply { transform = Transform(position = Vec2(10f, 20f)) }
        val b = Node2D().apply { transform = Transform(position = Vec2(3f, 4f)) }
        root.addChild(a)
        a.addChild(b)
        val world = b.worldTransform()
        assertEquals(Vec2(13f, 24f), world.position)
        assertEquals(Vec2(1f, 1f), world.scale)
        assertEquals(0f, world.rotation)
    }

    @Test
    fun `parent scale multiplies child position and propagates scale`() {
        val root = Node()
        val parent = Node2D().apply { transform = Transform(scale = Vec2(2f, 3f)) }
        val child = Node2D().apply { transform = Transform(position = Vec2(10f, 0f)) }
        root.addChild(parent)
        parent.addChild(child)
        val world = child.worldTransform()
        assertEquals(Vec2(20f, 0f), world.position)
        assertEquals(Vec2(2f, 3f), world.scale)
    }

    @Test
    fun `parent rotation rotates child local position`() {
        val root = Node()
        val parent = Node2D().apply {
            transform = Transform(rotation = (PI / 2.0).toFloat())
        }
        val child = Node2D().apply { transform = Transform(position = Vec2(10f, 0f)) }
        root.addChild(parent)
        parent.addChild(child)
        val world = child.worldTransform()
        assertApprox(Vec2(0f, 10f), world.position)
        assertEquals((PI / 2.0).toFloat(), world.rotation)
    }

    @Test
    fun `three-level chain composes transforms left-to-right`() {
        val root = Node()
        val a = Node2D().apply { transform = Transform(position = Vec2(10f, 20f), scale = Vec2(2f, 2f)) }
        val b = Node2D().apply { transform = Transform(position = Vec2(5f, 0f), rotation = (PI / 2.0).toFloat()) }
        val c = Node2D().apply { transform = Transform(position = Vec2(3f, 0f)) }
        root.addChild(a)
        a.addChild(b)
        b.addChild(c)
        val expected = a.transform.compose(b.transform).compose(c.transform)
        val actual = c.worldTransform()
        assertApprox(expected.position, actual.position)
        assertEquals(expected.scale, actual.scale)
        assertEquals(expected.rotation, actual.rotation)
    }

    private fun assertApprox(expected: Vec2, actual: Vec2, eps: Float = 1e-3f) {
        assertTrue(
            kotlin.math.abs(expected.x - actual.x) < eps &&
                kotlin.math.abs(expected.y - actual.y) < eps,
            "expected $expected, actual $actual",
        )
    }
}
