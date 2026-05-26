package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EPS = 1f

class RigidBodyConservationTest {

    private fun rigidCircle(
        radius: Float,
        position: Vec2,
        mass: Float = 1f,
        restitution: Float = 1f,
        linearVelocity: Vec2 = Vec2.ZERO,
    ): RigidBody2D = RigidBody2D().apply {
        transform = Transform(position = position)
        this.mass = mass
        this.restitution = restitution
        this.friction = 0f
        this.linearVelocity = linearVelocity
        addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { this.radius = radius } })
    }

    @Test
    fun `elastic collision conserves linear momentum and kinetic energy`() {
        val root = Node()
        val a = rigidCircle(radius = 5f, position = Vec2(0f, 0f), mass = 1f, linearVelocity = Vec2(100f, 0f))
        val b = rigidCircle(radius = 5f, position = Vec2(20f, 0f), mass = 2f, linearVelocity = Vec2(-50f, 0f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()

        val pBefore = a.linearVelocity * a.mass + b.linearVelocity * b.mass
        val keBefore = 0.5f * a.mass * (a.linearVelocity.x * a.linearVelocity.x + a.linearVelocity.y * a.linearVelocity.y) +
            0.5f * b.mass * (b.linearVelocity.x * b.linearVelocity.x + b.linearVelocity.y * b.linearVelocity.y)

        system.step(tree, 0.5f)

        val pAfter = a.linearVelocity * a.mass + b.linearVelocity * b.mass
        val keAfter = 0.5f * a.mass * (a.linearVelocity.x * a.linearVelocity.x + a.linearVelocity.y * a.linearVelocity.y) +
            0.5f * b.mass * (b.linearVelocity.x * b.linearVelocity.x + b.linearVelocity.y * b.linearVelocity.y)

        assertEquals(pBefore.x, pAfter.x, EPS)
        assertEquals(pBefore.y, pAfter.y, EPS)
        assertEquals(keBefore, keAfter, keBefore * 0.01f) // 1% tolerance
    }

    @Test
    fun `inelastic collision dissipates KE but conserves linear momentum`() {
        val root = Node()
        val a = rigidCircle(radius = 5f, position = Vec2(0f, 0f), mass = 1f, restitution = 0f, linearVelocity = Vec2(100f, 0f))
        val b = rigidCircle(radius = 5f, position = Vec2(20f, 0f), mass = 1f, restitution = 0f, linearVelocity = Vec2(-50f, 0f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()

        val pBefore = a.linearVelocity * a.mass + b.linearVelocity * b.mass
        val keBefore = 0.5f * a.mass * (a.linearVelocity.x * a.linearVelocity.x + a.linearVelocity.y * a.linearVelocity.y) +
            0.5f * b.mass * (b.linearVelocity.x * b.linearVelocity.x + b.linearVelocity.y * b.linearVelocity.y)

        system.step(tree, 0.5f)

        val pAfter = a.linearVelocity * a.mass + b.linearVelocity * b.mass
        val keAfter = 0.5f * a.mass * (a.linearVelocity.x * a.linearVelocity.x + a.linearVelocity.y * a.linearVelocity.y) +
            0.5f * b.mass * (b.linearVelocity.x * b.linearVelocity.x + b.linearVelocity.y * b.linearVelocity.y)

        assertEquals(pBefore.x, pAfter.x, EPS)
        assertEquals(pBefore.y, pAfter.y, EPS)
        assertTrue(keAfter < keBefore, "KE should dissipate, before=$keBefore after=$keAfter")
    }
}
