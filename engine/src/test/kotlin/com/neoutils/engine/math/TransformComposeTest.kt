package com.neoutils.engine.math

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformComposeTest {

    @Test
    fun `identity composed with t equals t on both sides`() {
        val t = Transform(
            position = Vec2(10f, 20f),
            scale = Vec2(2f, 3f),
            rotation = 0.5f,
        )
        assertEquals(t, Transform().compose(t))
        assertEquals(t, t.compose(Transform()))
    }

    @Test
    fun `scale composes component-wise`() {
        val parent = Transform(scale = Vec2(2f, 3f))
        val child = Transform(scale = Vec2(4f, 5f))
        assertEquals(Vec2(8f, 15f), parent.compose(child).scale)
    }

    @Test
    fun `rotation sums`() {
        val parent = Transform(rotation = 0.5f)
        val child = Transform(rotation = 0.25f)
        assertEquals(0.75f, parent.compose(child).rotation)
    }

    @Test
    fun `parent rotation and scale transform child position`() {
        val parent = Transform(
            position = Vec2(10f, 20f),
            scale = Vec2(2f, 2f),
            rotation = (PI / 2.0).toFloat(),
        )
        val child = Transform(position = Vec2(5f, 0f))
        val composed = parent.compose(child)
        assertApprox(Vec2(10f, 30f), composed.position)
    }

    private fun assertApprox(expected: Vec2, actual: Vec2, eps: Float = 1e-4f) {
        assertTrue(
            kotlin.math.abs(expected.x - actual.x) < eps &&
                kotlin.math.abs(expected.y - actual.y) < eps,
            "expected $expected, actual $actual",
        )
    }
}
