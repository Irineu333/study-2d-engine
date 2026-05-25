package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun circle(r: Float): CircleShape2D = CircleShape2D().apply { radius = r }
private fun rect(size: Vec2): RectangleShape2D = RectangleShape2D().apply { this.size = size }

private const val EPS = 0.001f

class SweepTest {

    @Test
    fun `swept circle-circle hits at TOI 0_1 with normal pointing from B toward A`() {
        val a = circle(5f); val b = circle(5f)
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(12f, 0f))
        val r = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(r)
        assertEquals(0.1f, r.toi, EPS)
        assertEquals(-1f, r.normal.x, EPS)
        assertEquals(0f, r.normal.y, EPS)
    }

    @Test
    fun `swept circle-rect axis-aligned hits at TOI 0_5 on left face`() {
        val c = circle(3f); val r = rect(Vec2(4f, 4f))
        val cWorld = Transform(position = Vec2(0f, 0f))
        val rWorld = Transform(position = Vec2(8f, 0f))
        val res = sweepOverlap(c, cWorld, Vec2(10f, 0f), r, rWorld)
        assertNotNull(res)
        assertEquals(0.5f, res.toi, EPS)
        assertEquals(-1f, res.normal.x, EPS)
        assertEquals(0f, res.normal.y, EPS)
    }

    @Test
    fun `swept rect-rect axis-aligned hits at TOI 0_3 on left face`() {
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(10f, 0f))
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        assertEquals(0.3f, res.toi, EPS)
        assertEquals(-1f, res.normal.x, EPS)
        assertEquals(0f, res.normal.y, EPS)
    }

    @Test
    fun `swept with no intersection returns null`() {
        val a = circle(5f); val b = circle(5f)
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(0f, 100f))
        // Motion parallel to x-axis, target far on y — never touches.
        assertNull(sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld))
    }

    @Test
    fun `rotated input falls through with null`() {
        val a = rect(Vec2(10f, 10f)); val b = rect(Vec2(10f, 10f))
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = (PI / 4.0).toFloat())
        val bWorld = Transform(position = Vec2(20f, 0f))
        assertNull(sweepOverlap(a, aWorld, Vec2(40f, 0f), b, bWorld))
    }

    @Test
    fun `starting-overlap circle-circle reports TOI 0 with separation normal`() {
        val a = circle(5f); val b = circle(5f)
        // Centers 4 units apart, both radius 5 — already overlapping.
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(4f, 0f))
        val r = sweepOverlap(a, aWorld, Vec2(10f, 0f), b, bWorld)
        assertNotNull(r)
        assertEquals(0f, r.toi)
        // Separation points from B toward A — A is to the left of B, so normal.x < 0.
        assertTrue(r.normal.x < 0f, "expected separation normal pointing left, got ${r.normal}")
    }

    @Test
    fun `starting-overlap rect-rect reports TOI 0 with separation along smallest pen axis`() {
        val a = rect(Vec2(10f, 10f)); val b = rect(Vec2(10f, 10f))
        // A at (0,0), B at (3,0) — strong overlap on x (7), full overlap on y (10).
        // Smallest penetration: push left out of B (3 to clear left edge).
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(3f, 0f))
        val r = sweepOverlap(a, aWorld, Vec2(0f, 0f), b, bWorld)
        assertNotNull(r)
        assertEquals(0f, r.toi)
        assertEquals(-1f, r.normal.x, EPS)
    }

    @Test
    fun `swept rect-vs-circle is symmetric with circle-vs-rect`() {
        val r = rect(Vec2(4f, 4f)); val c = circle(3f)
        // Rect at (0,0) moves right by (20,0); circle stationary at (10, 2).
        val rWorld = Transform(position = Vec2(0f, 0f))
        val cWorld = Transform(position = Vec2(10f, 2f))
        val res = sweepOverlap(r, rWorld, Vec2(20f, 0f), c, cWorld)
        assertNotNull(res)
        // Normal points from circle outward toward rect (mover).
        // Rect moves +x, hits circle's left side → normal points -x.
        assertTrue(res.normal.x < 0f, "expected normal pointing left, got ${res.normal}")
    }
}
