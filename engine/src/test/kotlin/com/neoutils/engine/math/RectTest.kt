package com.neoutils.engine.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RectTest {

    @Test
    fun `overlapping rects intersect`() {
        val a = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        val b = Rect(Vec2(5f, 5f), Vec2(10f, 10f))
        assertTrue(a.intersects(b))
        assertTrue(b.intersects(a))
    }

    @Test
    fun `disjoint rects on x axis do not intersect`() {
        val a = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        val b = Rect(Vec2(20f, 0f), Vec2(10f, 10f))
        assertFalse(a.intersects(b))
    }

    @Test
    fun `disjoint rects on y axis do not intersect`() {
        val a = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        val b = Rect(Vec2(0f, 20f), Vec2(10f, 10f))
        assertFalse(a.intersects(b))
    }

    @Test
    fun `edge-touching rects do not intersect`() {
        val a = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        val b = Rect(Vec2(10f, 0f), Vec2(10f, 10f))
        assertFalse(a.intersects(b))
    }

    @Test
    fun `contains returns true for points inside`() {
        val r = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        assertTrue(r.contains(Vec2(5f, 5f)))
    }

    @Test
    fun `contains returns false for points outside`() {
        val r = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        assertFalse(r.contains(Vec2(15f, 5f)))
    }

    @Test
    fun `contains is inclusive on origin edges`() {
        val r = Rect(Vec2(10f, 20f), Vec2(30f, 40f))
        assertTrue(r.contains(Vec2(10f, 20f)))
        assertTrue(r.contains(Vec2(10f, 30f)))
        assertTrue(r.contains(Vec2(20f, 20f)))
    }

    @Test
    fun `contains is exclusive on far edges`() {
        val r = Rect(Vec2(10f, 20f), Vec2(30f, 40f))
        assertFalse(r.contains(Vec2(40f, 30f)))
        assertFalse(r.contains(Vec2(20f, 60f)))
        assertFalse(r.contains(Vec2(40f, 60f)))
    }

    @Test
    fun `corners returns four corners in TL TR BR BL order`() {
        val r = Rect(Vec2(0f, 0f), Vec2(4f, 2f))
        assertEquals(
            listOf(Vec2(0f, 0f), Vec2(4f, 0f), Vec2(4f, 2f), Vec2(0f, 2f)),
            r.corners(),
        )
    }

    @Test
    fun `corners honors a non-zero origin`() {
        val r = Rect(Vec2(-5f, -3f), Vec2(10f, 6f))
        assertEquals(
            listOf(Vec2(-5f, -3f), Vec2(5f, -3f), Vec2(5f, 3f), Vec2(-5f, 3f)),
            r.corners(),
        )
    }
}
