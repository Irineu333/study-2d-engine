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
    fun `swept rotated rect that misses on a far axis returns null`() {
        // After kinematic-rotated-sweep, rotated rects no longer bail out;
        // null now only comes from actual geometric miss.
        val a = rect(Vec2(10f, 10f)); val b = rect(Vec2(10f, 10f))
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = (PI / 4.0).toFloat())
        val bWorld = Transform(position = Vec2(0f, 200f), rotation = (PI / 4.0).toFloat())
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
    fun `rect tangent to wall moving outward does not report a bogus collision`() {
        // Reproduces the freeze pattern: ball sits exactly at a wall's face
        // moving AWAY. The slab method's tEnter is in the past (<= 0) and
        // tExit == 0; without the tangent-leaving guard this would be reported
        // as toi=0 with the wall's inward normal, causing the script to
        // reflect velocity back into the wall every frame.
        val ball = rect(Vec2(12f, 12f))
        val wall = rect(Vec2(10f, 600f))
        // Ball top-left at (0, 100), moving RIGHT. Wall at (-10, 0) size (10, 600).
        // Wall's right edge is x=0, so ball is touching wall on its left side.
        val ballWorld = Transform(position = Vec2(0f, 100f))
        val wallWorld = Transform(position = Vec2(-10f, 0f))
        val motion = Vec2(50f, 0f) // moving away to the right
        assertNull(sweepOverlap(ball, ballWorld, motion, wall, wallWorld))
    }

    @Test
    fun `circle tangent to rect moving outward does not report a bogus collision`() {
        val c = circle(6f)
        val r = rect(Vec2(10f, 600f))
        // Circle center at (0, 100) — touching right face of expanded rect at x = -10 + 6.
        // Wait: rect at (-10, 0), size (10, 600) → right edge x=0. Circle radius 6 with
        // center at cx tangent to right edge requires cx = 0 + 6 = 6. Moving right (away).
        val cWorld = Transform(position = Vec2(6f, 100f))
        val rWorld = Transform(position = Vec2(-10f, 0f))
        val motion = Vec2(80f, 0f)
        assertNull(sweepOverlap(c, cWorld, motion, r, rWorld))
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

    // --- kinematic-rotated-sweep scenarios ---

    @Test
    fun `swept circle-vs-rotated-rect 90deg hits at analytic TOI`() {
        // Rect 4x4 at (10,0) rotated 90° around its origin: corners become
        // (10,0), (10,4), (6,0), (6,4) — occupies world x ∈ [6,10], y ∈ [0,4].
        // Circle r=2 at (0,0) moves +x by 20. Tangency when circle right edge
        // (cx + 2) reaches rect's leftmost world x = 6 → cx = 4, t = 4/20 = 0.2.
        val c = circle(2f); val r = rect(Vec2(4f, 4f))
        val cWorld = Transform(position = Vec2(0f, 0f))
        val rWorld = Transform(position = Vec2(10f, 0f), rotation = (PI / 2.0).toFloat())
        val res = sweepOverlap(c, cWorld, Vec2(20f, 0f), r, rWorld)
        assertNotNull(res)
        assertEquals(0.2f, res.toi, 0.01f)
        // Normal points from rect outward toward circle → -x in world.
        assertEquals(-1f, res.normal.x, 0.05f)
    }

    @Test
    fun `swept rotated-rect-vs-rotated-rect same rotation 45deg face-to-face contact`() {
        // Two 4x4 rects rotated 45° (diamonds in world). A at (0,0) reaches B
        // at (10,0) via motion (20,0). A's max projection on its own x-axis
        // edge1 = (cos45,sin45) is 4 (rect width). B's min on same axis is
        // 10·cos45 ≈ 7.07. Contact at t = (7.07-4)/14.14 ≈ 0.217.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val rot = (PI / 4.0).toFloat()
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = rot)
        val bWorld = Transform(position = Vec2(10f, 0f), rotation = rot)
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        assertTrue(res.toi > 0f && res.toi < 1f, "expected TOI in (0,1); got ${res.toi}")
        assertEquals(0.217f, res.toi, 0.02f)
        // Some component of the normal must oppose A's motion (+x).
        assertTrue(res.normal.x < 0f, "expected normal x < 0; got ${res.normal}")
    }

    @Test
    fun `swept rotated-rect-vs-rotated-rect different rotation collides with valid TOI`() {
        // A axis-aligned at (0,0), B rotated 45° at (10,0). A's right edge at
        // x=4; B's leftmost vertex at x=7.17 (= 10 - 4·sin45). Contact between
        // A's face and B's vertex at some t in (0,1).
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(10f, 0f), rotation = (PI / 4.0).toFloat())
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        assertTrue(res.toi >= 0f && res.toi < 1f, "expected TOI in [0,1); got ${res.toi}")
        assertTrue(res.normal.x < 0f, "expected normal x < 0; got ${res.normal}")
    }

    @Test
    fun `swept rotated motion parallel to separator axis returns null`() {
        // Two 4x4 rects rotated 45°. A at (0,0), B offset along A's axis2 by
        // 8 units: B at (axis2 * 8) = (-5.66, 5.66). They're separated on
        // axis2 (dt = 0 along that axis with motion along axis1). Motion
        // (10,10) is purely along axis1 — never closes the axis2 gap → null.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val rot = (PI / 4.0).toFloat()
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = rot)
        val bWorld = Transform(position = Vec2(-5.66f, 5.66f), rotation = rot)
        val res = sweepOverlap(a, aWorld, Vec2(10f, 10f), b, bWorld)
        assertNull(res)
    }

    @Test
    fun `swept rotated with zero motion returns null when separated`() {
        // Motion zero between rotated rects far apart → static SAT separator
        // with dt==0 on every axis → null.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val rot = (PI / 4.0).toFloat()
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = rot)
        val bWorld = Transform(position = Vec2(100f, 0f), rotation = rot)
        assertNull(sweepOverlap(a, aWorld, Vec2.ZERO, b, bWorld))
    }

    @Test
    fun `swept rotated tangent contact moving away returns null`() {
        // Two 4x4 rects rotated 45°. A at (0,0). B placed so that B's min
        // projection on A's axis1 equals A's max (4) → tangent on axis1.
        // B's origin should be at (cos45·4, sin45·4) = (2.83, 2.83). Motion
        // away from B (= -axis1 direction) should NOT trigger a collision.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val rot = (PI / 4.0).toFloat()
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = rot)
        val bWorld = Transform(position = Vec2(2.83f, 2.83f), rotation = rot)
        // Move away: -axis1 = (-cos45, -sin45) × 20 ≈ (-14.14, -14.14).
        assertNull(sweepOverlap(a, aWorld, Vec2(-14.14f, -14.14f), b, bWorld))
    }

    // --- add-rigid-body-2d: geometric contact point ---

    @Test
    fun `point lies on circle A surface for circle-vs-circle hit`() {
        // A at (0,0) r=5, motion (20,0), B at (12,0) r=5. Toi=0.1 → A at (2,0).
        // Contact point on A's surface in direction of B = (7, 0).
        val a = circle(5f); val b = circle(5f)
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(12f, 0f))
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        assertEquals(7f, res.point.x, EPS)
        assertEquals(0f, res.point.y, EPS)
    }

    @Test
    fun `point is on rect face for axis-aligned rect-vs-rect`() {
        // A at (0,0) size 4x4 swept right by (20,0) into B at (10,0) size 4x4.
        // Contact face is B's left face (x=10); overlap on y is [0,4] → mid y=2.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(10f, 0f))
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        assertEquals(10f, res.point.x, EPS)
        assertEquals(2f, res.point.y, EPS)
    }

    @Test
    fun `point is leading corner for rotated rect-vs-rect`() {
        // A 4x4 at (0,0) rotated 45° swept by (20,0) into axis-aligned B 4x4 at (10,0).
        // A's corners after rotation around (0,0): (0,0), (2.83,2.83), (-2.83,2.83), (0,5.66).
        // Wait — obbCorners places origin at top-left. After 45° rotation around (0,0),
        // the 4 corners of a 4x4 rect with TL=(0,0) become: (0,0), (2.83,2.83), (-2.83,2.83), (0,5.66).
        // Leading corner (smallest projection on normal ≈ (-1,0)) → max x value at contact.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = (PI / 4.0).toFloat())
        val bWorld = Transform(position = Vec2(10f, 0f))
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        // Result point should NOT be A's center (its OBB center, which is ~ (0, 2.83) before motion).
        // It should be on the leading corner of A in the -normal direction.
        // Since normal points toward A (i.e. -x), -normal = +x. Leading corner is the
        // rightmost corner of A at contact — close to B's left face x=10.
        assertTrue(res.point.x > 5f, "leading corner should be near B's face, got ${res.point.x}")
    }

    @Test
    fun `swept rotated starting overlap reports TOI 0 with MTV depenetration`() {
        // Deep overlap: B's origin at (2,0) rotated 45°, while A also rotated
        // 45° at (0,0). Both diamond-shaped, heavily overlapping.
        val a = rect(Vec2(10f, 10f)); val b = rect(Vec2(10f, 10f))
        val rot = (PI / 6.0).toFloat()
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = rot)
        val bWorld = Transform(position = Vec2(5f, 0f), rotation = rot)
        val res = sweepOverlap(a, aWorld, Vec2(0f, 0f), b, bWorld)
        assertNotNull(res)
        assertEquals(0f, res.toi)
        // Depenetration should be non-zero (separation vector).
        assertTrue(res.depenetration.length > 0f, "expected non-zero MTV; got ${res.depenetration}")
        // Depenetration pushes A away from B → projection onto (A.pos - B.pos)
        // direction should be positive.
        val awayDir = Vec2(-1f, 0f) // A is to the left of B in this setup
        val dot = res.depenetration.x * awayDir.x + res.depenetration.y * awayDir.y
        assertTrue(dot > 0f, "expected depenetration to push A away from B; got ${res.depenetration}")
    }
}
