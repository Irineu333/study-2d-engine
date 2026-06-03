package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun makeCharacter(size: Vec2, position: Vec2 = Vec2.ZERO): CharacterBody2D {
    val body = CharacterBody2D().apply { transform = Transform(position = position) }
    body.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
    return body
}

private fun makeStatic(size: Vec2, position: Vec2 = Vec2.ZERO): StaticBody2D {
    val body = StaticBody2D().apply { transform = Transform(position = position) }
    body.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
    return body
}

private fun makeArea(size: Vec2, position: Vec2 = Vec2.ZERO): Area2D {
    val area = Area2D().apply { transform = Transform(position = position) }
    area.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
    return area
}

private fun makeCharacterCircle(radius: Float, position: Vec2 = Vec2.ZERO): CharacterBody2D {
    val body = CharacterBody2D().apply { transform = Transform(position = position) }
    body.addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { this.radius = radius } })
    return body
}

private fun overlap(a: CharacterBody2D, b: CharacterBody2D, radius: Float): Boolean {
    val dx = a.position.x - b.position.x
    val dy = a.position.y - b.position.y
    return dx * dx + dy * dy < (2f * radius) * (2f * radius)
}

private const val EPS = 0.001f

class CharacterBody2DTest {

    @Test
    fun `moveAndCollide advances full motion and returns null when path is clear`() {
        val root = Node()
        val body = makeCharacter(Vec2(10f, 10f), position = Vec2(0f, 0f))
        root.addChild(body)
        SceneTree(root).start()
        val result = body.moveAndCollide(Vec2(40f, 0f))
        assertNull(result)
        assertEquals(40f, body.position.x, EPS)
        assertEquals(0f, body.position.y, EPS)
    }

    @Test
    fun `moveAndCollide stops at TOI against StaticBody2D and returns collision info`() {
        val root = Node()
        val body = makeCharacter(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val wall = makeStatic(Vec2(10f, 10f), position = Vec2(20f, 0f))
        root.addChild(body); root.addChild(wall)
        SceneTree(root).start()
        val collision = body.moveAndCollide(Vec2(40f, 0f))
        assertNotNull(collision)
        // Both centered: wall spans [15,25] (left face x=15). The body (spans
        // [-5,5]) touches it when its center reaches x=10 → TOI = 10/40 = 0.25.
        assertEquals(10f, body.position.x, EPS)
        assertEquals(-1f, collision.normal.x, EPS)
        assertSame(wall, collision.collider)
        // remainder = motion * (1 - toi) = (40, 0) * 0.75 = (30, 0).
        assertEquals(30f, collision.remainder.x, EPS)
    }

    @Test
    fun `moveAndCollide ignores Area2D in the path`() {
        val root = Node()
        val body = makeCharacter(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val sensor = makeArea(Vec2(10f, 10f), position = Vec2(20f, 0f))
        root.addChild(body); root.addChild(sensor)
        SceneTree(root).start()
        val result = body.moveAndCollide(Vec2(40f, 0f))
        assertNull(result)
        assertEquals(40f, body.position.x, EPS)
    }

    @Test
    fun `moveAndCollide with zero motion on a starting overlap only depenetrates`() {
        val root = Node()
        val body = makeCharacter(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val other = makeStatic(Vec2(10f, 10f), position = Vec2(3f, 0f))
        root.addChild(body); root.addChild(other)
        SceneTree(root).start()
        val collision = body.moveAndCollide(Vec2(0f, 0f))
        assertNotNull(collision)
        // Smallest-penetration: A starts 7 inside on the left edge (rect-rect
        // expanded slab penLeft = -5 − (-12) = 7) → push -x by 7.
        assertEquals(-7f, body.position.x, EPS)
        assertEquals(0f, body.position.y, EPS)
        assertEquals(-1f, collision.normal.x, EPS)
        // Zero motion: nothing to spend in the recovery → remainder stays ZERO.
        assertEquals(0f, collision.remainder.x, EPS)
        assertEquals(0f, collision.remainder.y, EPS)
    }

    @Test
    fun `moveAndCollide outward motion on a starting overlap escapes the collider`() {
        val root = Node()
        // Two characters overlapping: A at origin, B 4 to the right (+x), radius
        // 5 each → penetration 6, contact normal on A points -x.
        val a = makeCharacterCircle(radius = 5f, position = Vec2(0f, 0f))
        val b = makeCharacterCircle(radius = 5f, position = Vec2(4f, 0f))
        root.addChild(a); root.addChild(b)
        SceneTree(root).start()
        // Motion points OUT of B (along the contact normal). The recovery must
        // spend it: depenetrate, then re-sweep the outward motion into the slack.
        val collision = a.moveAndCollide(Vec2(-20f, 0f))
        assertNotNull(collision)
        assertSame(b, collision.collider)
        assertEquals(-1f, collision.normal.x, EPS)
        // Moved well beyond the bare depenetration (~-6): the outward motion was
        // spent, not discarded.
        assertTrue(a.position.x < -10f, "expected escape beyond depenetration; x=${a.position.x}")
        // No longer overlapping after the call.
        assertTrue(!overlap(a, b, 5f), "expected A and B to be disjoint; a=${a.position}, b=${b.position}")
    }

    @Test
    fun `moveAndCollide inward motion on a starting overlap makes no forward progress`() {
        val root = Node()
        val a = makeCharacterCircle(radius = 5f, position = Vec2(0f, 0f))
        val b = makeCharacterCircle(radius = 5f, position = Vec2(4f, 0f))
        root.addChild(a); root.addChild(b)
        SceneTree(root).start()
        // Motion points INTO B (+x). The body must rest against the surface
        // (depenetrated to its own side), never tunneling toward/past B at +4.
        val collision = a.moveAndCollide(Vec2(20f, 0f))
        assertNotNull(collision)
        assertTrue(a.position.x < 0f, "expected body to stay on its side; x=${a.position.x}")
        // Inward motion was left unspent: remainder still carries most of it.
        assertTrue(collision.remainder.x > 15f, "expected inward motion unspent; remainder=${collision.remainder}")
    }

    @Test
    fun `moveAndCollide on rotated body collides via temporal SAT and stops at contact`() {
        // After kinematic-rotated-sweep, rotated bodies sweep correctly:
        // the body's local rotation contributes to the OBB but the body
        // still moves frontally into a wall in the parent frame.
        val root = Node()
        val body = makeCharacter(Vec2(10f, 10f), position = Vec2(0f, 0f)).apply {
            rotation = (PI / 4.0).toFloat()
        }
        val wall = makeStatic(Vec2(10f, 100f), position = Vec2(40f, -50f))
        root.addChild(body); root.addChild(wall)
        SceneTree(root).start()
        val collision = body.moveAndCollide(Vec2(80f, 0f))
        assertNotNull(collision)
        // Sanity: body advanced toward the wall but did not pass through it.
        assertTrue(body.position.x in 10f..50f, "expected body to stop before wall; got x=${body.position.x}")
    }
}
