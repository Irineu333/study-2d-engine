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
        // A's top-left starts at 0, wall's left at 20 → TOI = (20-10)/40 = 0.25.
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
    fun `moveAndCollide on starting overlap returns TOI 0 with separation normal`() {
        val root = Node()
        val body = makeCharacter(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val other = makeStatic(Vec2(10f, 10f), position = Vec2(3f, 0f))
        root.addChild(body); root.addChild(other)
        SceneTree(root).start()
        val collision = body.moveAndCollide(Vec2(0f, 0f))
        assertNotNull(collision)
        // Position unchanged on TOI=0.
        assertEquals(0f, body.position.x, EPS)
        assertEquals(0f, body.position.y, EPS)
        // Smallest-penetration: A starts 3 inside on the left edge → push -x.
        assertEquals(-1f, collision.normal.x, EPS)
    }

    @Test
    fun `moveAndCollide on rotated body falls through to no-sweep and advances unchanged`() {
        val root = Node()
        val body = makeCharacter(Vec2(10f, 10f), position = Vec2(0f, 0f)).apply {
            rotation = (PI / 4.0).toFloat()
        }
        val wall = makeStatic(Vec2(10f, 10f), position = Vec2(20f, 0f))
        root.addChild(body); root.addChild(wall)
        SceneTree(root).start()
        val collision = body.moveAndCollide(Vec2(40f, 0f))
        assertNull(collision)
        assertEquals(40f, body.position.x, EPS)
    }
}
