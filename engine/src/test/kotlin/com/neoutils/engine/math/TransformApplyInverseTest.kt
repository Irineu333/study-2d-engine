package com.neoutils.engine.math

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertTrue

class TransformApplyInverseTest {

    @Test
    fun `applyInverse round-trips apply under translation`() {
        val t = Transform(position = Vec2(10f, -4f))
        val p = Vec2(3f, 7f)
        assertApprox(p, t.applyInverse(t.apply(p)))
    }

    @Test
    fun `applyInverse round-trips apply under scale`() {
        val t = Transform(scale = Vec2(2f, 0.5f))
        val p = Vec2(3f, 8f)
        assertApprox(p, t.applyInverse(t.apply(p)))
    }

    @Test
    fun `applyInverse round-trips apply under rotation`() {
        val t = Transform(rotation = (PI / 3.0).toFloat())
        val p = Vec2(5f, -2f)
        assertApprox(p, t.applyInverse(t.apply(p)))
    }

    @Test
    fun `applyInverse round-trips apply under combined transform`() {
        val t = Transform(
            position = Vec2(5f, 5f),
            scale = Vec2(2f, 3f),
            rotation = (PI / 4.0).toFloat(),
        )
        val p = Vec2(1f, -1f)
        assertApprox(p, t.applyInverse(t.apply(p)))
    }

    @Test
    fun `applyInverse matches hand-computed value for rotated and scaled transform`() {
        val t = Transform(
            position = Vec2(5f, 7f),
            scale = Vec2(2f, 2f),
            rotation = (PI / 2.0).toFloat(),
        )
        // From TransformApplyTest: apply((1,0)) == (5,7). Inverse must return (1,0).
        // q = world - pos = (0,0); rotate by -90° = (0,0); / scale = (0,0).
        assertApprox(Vec2(0f, 0f), t.applyInverse(Vec2(5f, 7f)))
        // world (5,9): q=(0,2); rotate -90° → (2,0); /2 → (1,0).
        assertApprox(Vec2(1f, 0f), t.applyInverse(Vec2(5f, 9f)))
    }

    private fun assertApprox(expected: Vec2, actual: Vec2, eps: Float = 1e-4f) {
        assertTrue(
            kotlin.math.abs(expected.x - actual.x) < eps &&
                kotlin.math.abs(expected.y - actual.y) < eps,
            "expected $expected, actual $actual",
        )
    }
}
