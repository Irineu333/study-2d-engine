package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RectangleWorldCornersTest {

    private val eps = 1e-4f

    @Test
    fun `unrotated rectangle corners match its AABB`() {
        val rect = RectangleShape2D().apply { size = Vec2(10f, 20f) }
        val corners = rect.worldCorners(Transform())
        assertEquals(4, corners.size)
        // Documented order: TL, TR, BR, BL.
        assertCorner(Vec2(0f, 0f), corners[0])
        assertCorner(Vec2(10f, 0f), corners[1])
        assertCorner(Vec2(10f, 20f), corners[2])
        assertCorner(Vec2(0f, 20f), corners[3])
    }

    @Test
    fun `rotated rectangle yields a rotated quad with centroid at world center`() {
        val size = Vec2(10f, 20f)
        val rect = RectangleShape2D().apply { this.size = size }
        val rotation = (PI / 6).toFloat()
        val position = Vec2(5f, 7f)
        val world = Transform(position = position, rotation = rotation)
        val corners = rect.worldCorners(world)

        // Not axis-aligned: the top edge (TL→TR) tilts, so its endpoints differ
        // in BOTH x and y — impossible for an axis-aligned rectangle.
        assertTrue(corners[0].x != corners[1].x)
        assertTrue(corners[0].y != corners[1].y)

        // Centroid of the four corners equals the rect's rotated world center
        // (origin + R(rotation) · (w/2, h/2)).
        val centroidX = corners.sumOf { it.x.toDouble() }.toFloat() / 4f
        val centroidY = corners.sumOf { it.y.toDouble() }.toFloat() / 4f
        val halfX = size.x / 2f
        val halfY = size.y / 2f
        val c = cos(rotation)
        val s = sin(rotation)
        val expectedX = position.x + (halfX * c - halfY * s)
        val expectedY = position.y + (halfX * s + halfY * c)
        assertEquals(expectedX, centroidX, eps)
        assertEquals(expectedY, centroidY, eps)
    }

    private fun assertCorner(expected: Vec2, actual: Vec2) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
    }
}
