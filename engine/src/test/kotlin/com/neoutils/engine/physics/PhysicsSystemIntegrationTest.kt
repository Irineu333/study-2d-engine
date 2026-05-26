package com.neoutils.engine.physics

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EPS = 0.001f

class PhysicsSystemIntegrationTest {

    @Test
    fun `free fall under gravity gains expected velocity`() {
        val root = Node()
        val body = RigidBody2D()
        root.addChild(body)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem().apply { gravity = Vec2(0f, 100f) }
        system.step(tree, 0.5f)
        assertEquals(0f, body.linearVelocity.x, EPS)
        assertEquals(50f, body.linearVelocity.y, EPS)
        assertEquals(25f, body.position.y, EPS) // v=50, dt=0.5 → moves 25
    }

    @Test
    fun `gravityScale zero ignores gravity`() {
        val root = Node()
        val body = RigidBody2D().apply { gravityScale = 0f }
        root.addChild(body)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem().apply { gravity = Vec2(0f, 100f) }
        system.step(tree, 1f)
        assertEquals(0f, body.linearVelocity.y, EPS)
        assertEquals(0f, body.position.y, EPS)
    }

    @Test
    fun `linear damping reduces velocity`() {
        val root = Node()
        val body = RigidBody2D().apply {
            linearVelocity = Vec2(100f, 0f)
            linearDamping = 1f
        }
        root.addChild(body)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()
        system.step(tree, 0.5f)
        // velocity *= max(0, 1 - 1 * 0.5) = 0.5 → 50
        assertEquals(50f, body.linearVelocity.x, EPS)
    }

    @Test
    fun `body with velocity and no obstacle advances by velocity dt`() {
        val root = Node()
        val body = RigidBody2D().apply { linearVelocity = Vec2(100f, 0f) }
        root.addChild(body)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()
        system.step(tree, 0.1f)
        assertEquals(10f, body.position.x, EPS)
    }

    @Test
    fun `default gravity is zero`() {
        val system = PhysicsSystem()
        assertEquals(Vec2.ZERO, system.gravity)
    }

    @Test
    fun `step signature includes dt`() {
        // Compile-time check: signature is (SceneTree, Float).
        val root = Node()
        val tree = SceneTree(root).also { it.start() }
        PhysicsSystem().step(tree, 0.016f)
        assertTrue(true)
    }
}
