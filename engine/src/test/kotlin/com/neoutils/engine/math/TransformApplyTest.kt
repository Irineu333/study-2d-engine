package com.neoutils.engine.math

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformApplyTest {

    @Test
    fun `apply translates and scales a local point`() {
        val t = Transform(position = Vec2(10f, 0f), scale = Vec2(2f, 2f))
        assertEquals(Vec2(16f, 8f), t.apply(Vec2(3f, 4f)))
    }

    @Test
    fun `apply with identity returns the point unchanged`() {
        assertEquals(Vec2(3f, 4f), Transform().apply(Vec2(3f, 4f)))
    }

    @Test
    fun `apply rotates a local point about the origin`() {
        val t = Transform(rotation = (PI / 2.0).toFloat())
        val out = t.apply(Vec2(1f, 0f))
        assertApprox(Vec2(0f, 1f), out)
    }

    @Test
    fun `apply composes translation rotation and scale`() {
        val t = Transform(
            position = Vec2(5f, 5f),
            scale = Vec2(2f, 2f),
            rotation = (PI / 2.0).toFloat(),
        )
        // local (1,0) → scale → (2,0) → rotate 90° → (0,2) → +pos → (5,7)
        assertApprox(Vec2(5f, 7f), t.apply(Vec2(1f, 0f)))
    }

    private fun assertApprox(expected: Vec2, actual: Vec2, eps: Float = 1e-4f) {
        assertTrue(
            kotlin.math.abs(expected.x - actual.x) < eps &&
                kotlin.math.abs(expected.y - actual.y) < eps,
            "expected $expected, actual $actual",
        )
    }
}
