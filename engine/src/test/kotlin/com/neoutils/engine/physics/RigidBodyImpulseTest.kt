package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Helpers create bodies whose CollisionShape2D is centered on the body's
// position — convention for rigid bodies (rotation/mass center = position).
private fun rigidCircle(
    radius: Float,
    position: Vec2,
    mass: Float = 1f,
    restitution: Float = 1f,
    friction: Float = 0f,
    linearVelocity: Vec2 = Vec2.ZERO,
    angularVelocity: Float = 0f,
    inertia: Float = 0f,
): RigidBody2D = RigidBody2D().apply {
    transform = Transform(position = position)
    this.mass = mass
    this.restitution = restitution
    this.friction = friction
    this.linearVelocity = linearVelocity
    this.angularVelocity = angularVelocity
    if (inertia != 0f) this.inertia = inertia
    // CircleShape2D.position IS the circle center (not the bounding box TL),
    // so no offset is needed for it to sit on the body's position.
    addChild(CollisionShape2D().apply {
        shape = CircleShape2D().apply { this.radius = radius }
    })
}

private fun rigidRect(
    size: Vec2,
    position: Vec2,
    mass: Float = 1f,
    restitution: Float = 1f,
    friction: Float = 0f,
    linearVelocity: Vec2 = Vec2.ZERO,
    angularVelocity: Float = 0f,
    inertia: Float = 0f,
): RigidBody2D = RigidBody2D().apply {
    transform = Transform(position = position)
    this.mass = mass
    this.restitution = restitution
    this.friction = friction
    this.linearVelocity = linearVelocity
    this.angularVelocity = angularVelocity
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

private const val EPS = 0.5f

class RigidBodyImpulseTest {

    @Test
    fun `equal mass elastic head-on swaps velocities`() {
        // Two circles moving toward each other along x-axis. Equal mass, e=1.
        val root = Node()
        val a = rigidCircle(radius = 5f, position = Vec2(0f, 0f), mass = 1f, restitution = 1f, linearVelocity = Vec2(100f, 0f))
        val b = rigidCircle(radius = 5f, position = Vec2(20f, 0f), mass = 1f, restitution = 1f, linearVelocity = Vec2(0f, 0f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()
        // dt large enough for A to cross B in one tick
        system.step(tree, 0.5f)
        // Post-resolution: A should be slow/stopped, B should carry the momentum.
        assertTrue(a.linearVelocity.x < 50f, "A should have lost most velocity, got ${a.linearVelocity.x}")
        assertTrue(b.linearVelocity.x > 50f, "B should have gained velocity, got ${b.linearVelocity.x}")
    }

    @Test
    fun `mass-heavy vs mass-light produces expected post velocities`() {
        // A m=10 v=100, B m=1 v=0 — elastic.
        // Expected: vA' = (m1-m2)/(m1+m2)*v1 = 9/11*100 ≈ 81.81; vB' = 2m1/(m1+m2)*v1 = 20/11*100 ≈ 181.81
        val root = Node()
        val a = rigidCircle(radius = 5f, position = Vec2(0f, 0f), mass = 10f, restitution = 1f, linearVelocity = Vec2(100f, 0f))
        val b = rigidCircle(radius = 5f, position = Vec2(20f, 0f), mass = 1f, restitution = 1f)
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()
        system.step(tree, 0.5f)
        assertTrue(a.linearVelocity.x > 70f && a.linearVelocity.x < 90f, "A expected ~81.81, got ${a.linearVelocity.x}")
        assertTrue(b.linearVelocity.x > 170f && b.linearVelocity.x < 195f, "B expected ~181.81, got ${b.linearVelocity.x}")
    }

    @Test
    fun `inelastic head-on equalizes normal velocities`() {
        val root = Node()
        val a = rigidCircle(radius = 5f, position = Vec2(0f, 0f), mass = 1f, restitution = 0f, linearVelocity = Vec2(100f, 0f))
        val b = rigidCircle(radius = 5f, position = Vec2(20f, 0f), mass = 1f, restitution = 0f)
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()
        system.step(tree, 0.5f)
        // After inelastic e=0, vRelN should be ~0 after impulse
        val relVx = a.linearVelocity.x - b.linearVelocity.x
        assertTrue(kotlin.math.abs(relVx) < 1f, "relative normal velocity should be ~0, got $relVx (A=${a.linearVelocity.x}, B=${b.linearVelocity.x})")
    }

    @Test
    fun `static body perfect bounce`() {
        val root = Node()
        val a = rigidCircle(radius = 5f, position = Vec2(0f, 0f), mass = 1f, restitution = 1f, linearVelocity = Vec2(100f, 0f))
        val wall = staticBoxRect(size = Vec2(10f, 1000f), position = Vec2(20f, -500f))
        root.addChild(a); root.addChild(wall)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()
        system.step(tree, 0.5f)
        // A should bounce back: vx ≈ -100
        assertTrue(a.linearVelocity.x < -50f, "A expected to bounce back, got ${a.linearVelocity.x}")
    }

    @Test
    fun `pair already separating is not impulsed`() {
        val root = Node()
        // Bodies overlapping but separating
        val a = rigidCircle(radius = 5f, position = Vec2(0f, 0f), mass = 1f, restitution = 1f, linearVelocity = Vec2(-100f, 0f))
        val b = rigidCircle(radius = 5f, position = Vec2(4f, 0f), mass = 1f, restitution = 1f, linearVelocity = Vec2(100f, 0f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()
        val aVxBefore = a.linearVelocity.x
        val bVxBefore = b.linearVelocity.x
        system.step(tree, 0.001f) // small dt, depen may apply but no impulse should fire
        // Velocities shouldn't reverse — they're already moving apart on contact normal
        assertTrue(a.linearVelocity.x <= aVxBefore + EPS, "A should not gain rightward velocity from separating pair")
        assertTrue(b.linearVelocity.x >= bVxBefore - EPS, "B should not gain leftward velocity from separating pair")
    }

    @Test
    fun `off-center hit produces angular velocity`() {
        // Rect A hits a static wall offset from its center.
        val root = Node()
        val a = rigidRect(size = Vec2(10f, 10f), position = Vec2(0f, 0f), mass = 1f, restitution = 1f, linearVelocity = Vec2(100f, 0f))
        // Wall positioned so A's top-right corner hits first (asymmetric)
        val wall = staticBoxRect(size = Vec2(10f, 5f), position = Vec2(15f, -3f))
        root.addChild(a); root.addChild(wall)
        val tree = SceneTree(root).also { it.start() }
        val system = PhysicsSystem()
        system.step(tree, 0.5f)
        // Some angular spin should result from the off-center hit
        assertTrue(kotlin.math.abs(a.angularVelocity) > 0f, "off-center hit should induce spin, got ${a.angularVelocity}")
    }
}
