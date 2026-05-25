package com.neoutils.engine.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Vec2Test {

    @Test
    fun `plus returns a new instance and does not mutate`() {
        val a = Vec2(1f, 2f)
        val b = Vec2(3f, 4f)
        val sum = a + b
        assertEquals(Vec2(4f, 6f), sum)
        assertEquals(Vec2(1f, 2f), a)
        assertEquals(Vec2(3f, 4f), b)
    }

    @Test
    fun `minus subtracts components`() {
        assertEquals(Vec2(-2f, -2f), Vec2(1f, 2f) - Vec2(3f, 4f))
    }

    @Test
    fun `times scales components`() {
        assertEquals(Vec2(2f, 4f), Vec2(1f, 2f) * 2f)
    }

    @Test
    fun `length is Euclidean norm`() {
        assertEquals(5f, Vec2(3f, 4f).length)
    }

    @Test
    fun `normalized has unit length`() {
        val n = Vec2(3f, 4f).normalized
        assertTrue(kotlin.math.abs(n.length - 1f) < 1e-6f)
    }

    @Test
    fun `normalizing zero returns zero`() {
        assertEquals(Vec2.ZERO, Vec2.ZERO.normalized)
    }

    @Test
    fun `reflect across vertical normal flips x`() {
        // v = (3, 4), n = (-1, 0)  →  v − 2(v·n)·n = (3, 4) − 2·(−3)·(−1, 0) = (−3, 4)
        assertEquals(Vec2(-3f, 4f), Vec2(3f, 4f).reflect(Vec2(-1f, 0f)))
    }

    @Test
    fun `reflect across horizontal normal flips y`() {
        assertEquals(Vec2(5f, -2f), Vec2(5f, 2f).reflect(Vec2(0f, -1f)))
    }

    @Test
    fun `reflect parallel to normal inverts the vector`() {
        // v = (4, 0) into n = (-1, 0): hitting a wall head-on → bounces back.
        assertEquals(Vec2(-4f, 0f), Vec2(4f, 0f).reflect(Vec2(-1f, 0f)))
    }
}
