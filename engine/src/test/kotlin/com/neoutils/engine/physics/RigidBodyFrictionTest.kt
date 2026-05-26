package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EPS = 0.001f

class RigidBodyFrictionTest {

    private fun rigidRect(
        size: Vec2,
        position: Vec2,
        mass: Float = 1f,
        restitution: Float = 0f,
        friction: Float = 1f,
        linearVelocity: Vec2 = Vec2.ZERO,
        inertia: Float = 0f,
    ): RigidBody2D = RigidBody2D().apply {
        transform = Transform(position = position)
        this.mass = mass
        this.restitution = restitution
        this.friction = friction
        this.linearVelocity = linearVelocity
        if (inertia != 0f) this.inertia = inertia
        addChild(CollisionShape2D().apply {
            transform = Transform(position = Vec2(-size.x / 2f, -size.y / 2f))
            shape = RectangleShape2D().apply { this.size = size }
        })
    }

    private fun staticBoxRect(size: Vec2, position: Vec2): StaticBody2D = StaticBody2D().apply {
        transform = Transform(position = position)
        addChild(CollisionShape2D().apply {
            transform = Transform(position = Vec2(-size.x / 2f, -size.y / 2f))
            shape = RectangleShape2D().apply { this.size = size }
        })
    }

    @Test
    fun `tangential velocity with friction induces spin on collision`() {
        // Body moving diagonally hits a vertical wall. Tangential vel induces friction torque.
        val root = Node()
        val a = rigidRect(
            size = Vec2(10f, 10f),
            position = Vec2(0f, 0f),
            mass = 1f,
            restitution = 1f,
            friction = 1f,
            linearVelocity = Vec2(100f, 100f),
        )
        val wall = staticBoxRect(size = Vec2(10f, 200f), position = Vec2(20f, -50f))
        root.addChild(a); root.addChild(wall)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()
        system.step(tree, 0.5f)
        // Body should bounce on x (restitution=1) and friction should slow y-velocity AND induce spin.
        assertTrue(a.linearVelocity.x < 0f, "x velocity should reverse, got ${a.linearVelocity.x}")
        // angularVelocity should be non-zero
        assertTrue(kotlin.math.abs(a.angularVelocity) > EPS, "friction should induce spin, got ${a.angularVelocity}")
    }

    @Test
    fun `frictionless body does not spin on glancing hit`() {
        val root = Node()
        val a = rigidRect(
            size = Vec2(10f, 10f),
            position = Vec2(0f, 0f),
            mass = 1f,
            restitution = 1f,
            friction = 0f,
            linearVelocity = Vec2(100f, 100f),
        )
        val wall = staticBoxRect(size = Vec2(10f, 200f), position = Vec2(20f, -50f))
        root.addChild(a); root.addChild(wall)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()
        system.step(tree, 0.5f)
        assertEquals(0f, a.angularVelocity, EPS) // no spin with friction=0
    }
}
