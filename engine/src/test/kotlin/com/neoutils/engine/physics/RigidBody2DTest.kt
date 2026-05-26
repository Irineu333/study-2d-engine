package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EPS = 0.001f

class RigidBody2DTest {

    @Test
    fun `applyImpulse mutates linearVelocity immediately by impulse over mass`() {
        val body = RigidBody2D().apply { mass = 2f }
        body.applyImpulse(Vec2(10f, 0f))
        assertEquals(5f, body.linearVelocity.x, EPS)
        assertEquals(0f, body.linearVelocity.y, EPS)
    }

    @Test
    fun `applyForce accumulates into appliedForce`() {
        val body = RigidBody2D()
        body.applyForce(Vec2(3f, 4f))
        body.applyForce(Vec2(1f, 0f))
        assertEquals(4f, body.appliedForce.x, EPS)
        assertEquals(4f, body.appliedForce.y, EPS)
        assertEquals(Vec2.ZERO, body.linearVelocity)
    }

    @Test
    fun `applyImpulseAt couples linear and angular velocity`() {
        val body = RigidBody2D().apply { mass = 1f; inertia = 1f }
        body.applyImpulseAt(Vec2(0f, 10f), worldPoint = Vec2(5f, 0f))
        assertEquals(10f, body.linearVelocity.y, EPS)
        assertEquals(50f, body.angularVelocity, EPS) // r.x * j.y - r.y * j.x = 5*10 - 0*0 = 50
    }

    @Test
    fun `applyTorque accumulates torque`() {
        val body = RigidBody2D()
        body.applyTorque(2.5f)
        body.applyTorque(1f)
        assertEquals(3.5f, body.appliedTorque, EPS)
    }

    @Test
    fun `effectiveInertia derives from circle shape`() {
        val body = RigidBody2D().apply { mass = 2f }
        body.addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        assertEquals(2f * 25f / 2f, body.effectiveInertia, EPS)
    }

    @Test
    fun `effectiveInertia derives from rectangle shape with parallel-axis offset`() {
        val body = RigidBody2D().apply { mass = 1f }
        body.addChild(
            CollisionShape2D().apply {
                shape = RectangleShape2D().apply { size = Vec2(6f, 4f) }
                transform = Transform(position = Vec2(3f, 0f))
            },
        )
        // I_local = 1 * (36 + 16) / 12 = 52/12 ≈ 4.333
        // I_offset = 1 * 9 = 9
        assertEquals(52f / 12f + 9f, body.effectiveInertia, EPS)
    }

    @Test
    fun `explicit inertia overrides auto derive`() {
        val body = RigidBody2D().apply { mass = 1f; inertia = 42f }
        body.addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        assertEquals(42f, body.effectiveInertia, EPS)
    }

    @Test
    fun `RigidBody2D with no shape children returns inertia 1f`() {
        val body = RigidBody2D().apply { mass = 1f }
        assertEquals(1f, body.effectiveInertia, EPS)
    }

    @Test
    fun `defaults match spec`() {
        val body = RigidBody2D()
        assertEquals(1f, body.mass, EPS)
        assertEquals(0f, body.inertia, EPS)
        assertEquals(0f, body.restitution, EPS)
        assertEquals(1f, body.friction, EPS)
        assertEquals(1f, body.gravityScale, EPS)
        assertEquals(0f, body.linearDamping, EPS)
        assertEquals(0f, body.angularDamping, EPS)
        assertEquals(Vec2.ZERO, body.linearVelocity)
        assertEquals(0f, body.angularVelocity, EPS)
    }

    @Test
    fun `RigidBody2D is instantiable as PhysicsBody2D`() {
        val body: PhysicsBody2D = RigidBody2D()
        assertTrue(body is RigidBody2D)
    }

    @Test
    fun `applyForceAt accumulates linear plus torque`() {
        val body = RigidBody2D().apply { mass = 1f }
        val root = Node()
        root.addChild(body)
        SceneTree(root).start()
        // body at world origin (0,0); force at (5,0) pushing +y
        body.applyForceAt(Vec2(0f, 10f), worldPoint = Vec2(5f, 0f))
        assertEquals(0f, body.appliedForce.x, EPS)
        assertEquals(10f, body.appliedForce.y, EPS)
        assertEquals(50f, body.appliedTorque, EPS)
    }
}
