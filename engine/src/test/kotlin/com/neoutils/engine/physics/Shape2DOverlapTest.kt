package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun rect(size: Float): RectangleShape2D =
    RectangleShape2D().apply { this.size = Vec2(size, size) }

private val QUARTER_TURN: Float = (PI / 4.0).toFloat()

class Shape2DOverlapTest {

    // Local origin is top-left, so a rectangle of size 20 at world.position p
    // spans [p.x, p.x+20] × [p.y, p.y+20] when rotation == 0. The scenarios
    // in the spec are described with that convention.

    @Test
    fun `rotated rectangles with AABBs overlapping but OBBs separated do not overlap`() {
        val a = rect(20f)
        val b = rect(20f)
        // Local origin is the corner; A's rotated diamond has corners (0,0),
        // (~14.14, ~14.14), (0, ~28.28), (~-14.14, ~14.14). Translating B by
        // (15, 15) shifts the projection on the shared edge axis by ~21.21 —
        // beyond the OBB extent of 20, so SAT separates on that axis. The
        // AABB envelopes (each 28.28×28.28) still overlap on the rectangle
        // [0.86, 14.14] × [15, 28.28].
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = QUARTER_TURN)
        val bWorld = Transform(position = Vec2(15f, 15f), rotation = QUARTER_TURN)

        assertTrue(
            a.bounds(aWorld, Vec2.ZERO).intersects(b.bounds(bWorld, Vec2.ZERO)),
            "AABB envelopes should overlap (precondition of the regression test)",
        )

        assertFalse(overlap(a, aWorld, b, bWorld))
    }

    @Test
    fun `rotated rectangles whose OBBs actually overlap return true`() {
        val a = rect(20f)
        val b = rect(20f)
        // Identical rotation and a small offset along the rotated frame —
        // OBBs deeply overlap.
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = QUARTER_TURN)
        val bWorld = Transform(position = Vec2(10f, 10f), rotation = QUARTER_TURN)

        assertTrue(overlap(a, aWorld, b, bWorld))
    }

    @Test
    fun `axis-aligned rectangles preserve existing AABB behavior`() {
        val a = rect(10f)
        val b = rect(10f)
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorldNear = Transform(position = Vec2(5f, 5f))
        val bWorldFar = Transform(position = Vec2(100f, 100f))

        assertTrue(overlap(a, aWorld, b, bWorldNear))
        assertFalse(overlap(a, aWorld, b, bWorldFar))
    }

    @Test
    fun `mixed rotated-and-axis-aligned uses OBB path`() {
        val a = rect(20f)
        val b = rect(20f)
        // A is a 20×20 rotated 45° around the world origin, so its OBB is a
        // diamond with corners (0,0), (~14.14, ~14.14), (~-14.14, ~14.14),
        // (0, ~28.28) — AABB envelope x ∈ [-14.14, 14.14], y ∈ [0, 28.28].
        // B is axis-aligned at (10, 20), occupying [10, 30] × [20, 40].
        // Envelopes overlap on [10, 14.14] × [20, 28.28]; the rotated diamond
        // however passes only the bottom-right edge at most x ≈ 8.28 for
        // y = 20, so the OBBs do not touch.
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = QUARTER_TURN)
        val bWorld = Transform(position = Vec2(10f, 20f), rotation = 0f)

        assertTrue(
            a.bounds(aWorld, Vec2.ZERO).intersects(b.bounds(bWorld, Vec2.ZERO)),
            "AABB envelopes should overlap (precondition for OBB-path exercise)",
        )

        assertFalse(overlap(a, aWorld, b, bWorld))
    }

    // Regression for the post-refactor contract of `RectangleShape2D.bounds`.
    @Test
    fun `bounds returns the axis-aligned rectangle when rotation is zero`() {
        val r = rect(10f)
        val world = Transform(position = Vec2(3f, 7f))
        val b = r.bounds(world, Vec2.ZERO)
        assertEquals(3f, b.origin.x)
        assertEquals(7f, b.origin.y)
        assertEquals(10f, b.size.x)
        assertEquals(10f, b.size.y)
    }

    @Test
    fun `bounds returns the AABB envelope of the rotated corners when rotation is non-zero`() {
        val r = rect(10f)
        val world = Transform(position = Vec2(0f, 0f), rotation = QUARTER_TURN)
        val b = r.bounds(world, Vec2.ZERO)
        // Local corners (0,0),(10,0),(0,10),(10,10) rotated 45° around origin:
        //   (0,0) → (0,0)
        //   (10,0) → (7.07, 7.07)
        //   (0,10) → (-7.07, 7.07)
        //   (10,10) → (0, 14.14)
        // Envelope: x ∈ [-7.07, 7.07], y ∈ [0, 14.14].
        val half = 10f * sqrt(2f) / 2f
        approx(-half, b.origin.x, "origin.x")
        approx(0f, b.origin.y, "origin.y")
        approx(2f * half, b.size.x, "size.x")
        approx(2f * half, b.size.y, "size.y")
    }

    private fun approx(expected: Float, actual: Float, label: String) {
        assertTrue(
            kotlin.math.abs(expected - actual) < 1e-3f,
            "$label expected $expected, got $actual",
        )
    }
}
