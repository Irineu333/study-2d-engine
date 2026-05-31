package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShapeLocalBoundsTest {

    @Test
    fun `RectangleShape2D local bounds is centered`() {
        val shape = RectangleShape2D().apply { size = Vec2(10f, 6f) }
        assertEquals(Rect(Vec2(-5f, -3f), Vec2(10f, 6f)), shape.localBounds())
    }

    @Test
    fun `CircleShape2D local bounds is a centered square`() {
        val shape = CircleShape2D().apply { radius = 4f }
        assertEquals(Rect(Vec2(-4f, -4f), Vec2(8f, 8f)), shape.localBounds())
    }

    @Test
    fun `CollisionObject2D treeBounds encloses child shape via recursion`() {
        val area = Area2D()
        area.addChild(
            CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 4f } },
        )
        // Circle is centered at the body origin → AABB [-4,-4]..[4,4].
        assertEquals(Rect(Vec2(-4f, -4f), Vec2(8f, 8f)), area.treeBounds())
    }

    @Test
    fun `CollisionObject2D treeBounds honors the body world position`() {
        val root = Node()
        val area = Area2D().apply { position = Vec2(100f, 50f) }
        area.addChild(
            CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(10f, 6f) } },
        )
        root.addChild(area)
        assertEquals(Rect(Vec2(95f, 47f), Vec2(10f, 6f)), area.treeBounds())
    }

    @Test
    fun `disabled collision shape has null local bounds`() {
        val s = CollisionShape2D().apply {
            shape = RectangleShape2D().apply { size = Vec2(10f, 10f) }
            disabled = true
        }
        assertNull(s.localBounds())
    }

    @Test
    fun `shapeless collision shape has null local bounds`() {
        assertNull(CollisionShape2D().localBounds())
    }
}
