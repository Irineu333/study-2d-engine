package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.tree.SceneTree
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactNormalizationTest {

    private val eps = 1e-3f

    // --- worldContact helper ---

    @Test
    fun `worldContact maps point and normal from a rotated translated parent into world`() {
        val parent = Node2D().apply {
            transform = Transform(position = Vec2(100f, 50f), rotation = (PI / 2).toFloat())
        }
        val (worldPoint, worldNormal) = worldContact(parent, Vec2(10f, 0f), Vec2(1f, 0f))
        // Local (10,0) rotated 90 deg -> (0,10), plus parent position (100,50) = (100,60).
        assertEquals(100f, worldPoint.x, eps)
        assertEquals(60f, worldPoint.y, eps)
        // Direction (1,0) rotated 90 deg -> (0,1), still unit length.
        assertEquals(0f, worldNormal.x, eps)
        assertEquals(1f, worldNormal.y, eps)
        assertEquals(1f, worldNormal.length, eps)
    }

    @Test
    fun `worldContact is the identity for a top-level body`() {
        // null parent, a plain Node, and an identity-transform Node2D all leave
        // the contact unchanged.
        for (parent in listOf<Node?>(null, Node(), Node2D())) {
            val (worldPoint, worldNormal) = worldContact(parent, Vec2(7f, 8f), Vec2(0f, 1f))
            assertEquals(Vec2(7f, 8f), worldPoint)
            assertEquals(Vec2(0f, 1f), worldNormal)
        }
    }

    // --- end-to-end: nested bodies record in world space ---

    @Test
    fun `nested CharacterBody2D stages its contact in world space`() {
        val box = Node2D().apply {
            transform = Transform(position = Vec2(200f, 100f), rotation = (PI / 2).toFloat())
        }
        val character = CharacterBody2D().apply {
            transform = Transform(position = Vec2(0f, 0f))
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        val wall = StaticBody2D().apply {
            transform = Transform(position = Vec2(20f, 0f))
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        box.addChild(character)
        box.addChild(wall)
        val tree = SceneTree(Node().apply { addChild(box) }).also { it.start() }
        tree.debug.contacts.recording = true

        val collision = character.moveAndCollide(Vec2(100f, 0f))!!
        val record = tree.debug.contacts.staged.single()

        // The staged record is the parent-frame contact run through the same
        // world normalization (helper correctness is covered by its own tests).
        val (expectedPoint, expectedNormal) = worldContact(box, collision.point, collision.normal)
        assertEquals(expectedPoint.x, record.point.x, eps)
        assertEquals(expectedPoint.y, record.point.y, eps)
        assertEquals(expectedNormal.x, record.normal.x, eps)
        assertEquals(expectedNormal.y, record.normal.y, eps)
        // Normalization actually happened (parent is rotated+translated).
        assertTrue(record.point != collision.point)
        assertEquals(1f, record.normal.length, eps)
    }

    @Test
    fun `nested RigidBody2D records its contact in world space`() {
        val boxPos = Vec2(200f, 100f)
        val box = Node2D().apply {
            transform = Transform(position = boxPos, rotation = (PI / 2).toFloat())
        }
        val a = RigidBody2D().apply {
            transform = Transform(position = Vec2(0f, 0f))
            mass = 1f; restitution = 1f; friction = 0f
            linearVelocity = Vec2(100f, 0f)
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        val b = RigidBody2D().apply {
            transform = Transform(position = Vec2(20f, 0f))
            mass = 1f; restitution = 1f; friction = 0f
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        box.addChild(a)
        box.addChild(b)
        val tree = SceneTree(Node().apply { addChild(box) }).also { it.start() }
        tree.debug.contacts.recording = true

        PhysicsSystem().step(tree, 0.5f)

        val record = tree.debug.contacts.records.first()
        // The local contact sits within ~25px of the box origin; in world space
        // it lands near the box's world position. A non-normalized (parent-frame)
        // point would be near local origin (0..20, 0), far from boxPos.
        val distToBox = hypot((record.point.x - boxPos.x).toDouble(), (record.point.y - boxPos.y).toDouble())
        assertTrue(distToBox < 40.0, "expected world-space contact near $boxPos, got ${record.point}")
        assertEquals(1f, record.normal.length, eps)
    }
}
