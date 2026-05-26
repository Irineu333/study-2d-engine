package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals

private const val EPS = 0.01f

class MomentumDiagnosticsTest {

    @Test
    fun `single body total linear momentum equals m times v`() {
        val root = Node()
        val body = RigidBody2D().apply {
            mass = 3f
            linearVelocity = Vec2(4f, -2f)
        }
        root.addChild(body)
        val tree = SceneTree(root).also { it.start() }
        val p = tree.totalLinearMomentum()
        assertEquals(12f, p.x, EPS)
        assertEquals(-6f, p.y, EPS)
    }

    @Test
    fun `tree without rigid bodies returns zero diagnostics`() {
        val root = Node()
        root.addChild(CharacterBody2D())
        root.addChild(Area2D())
        val tree = SceneTree(root).also { it.start() }
        assertEquals(Vec2.ZERO, tree.totalLinearMomentum())
        assertEquals(0f, tree.totalAngularMomentum(), EPS)
        assertEquals(0f, tree.totalKineticEnergy(), EPS)
    }

    @Test
    fun `kinetic energy of single body matches half m v squared plus half I omega squared`() {
        val root = Node()
        val body = RigidBody2D().apply {
            mass = 2f
            inertia = 4f
            linearVelocity = Vec2(3f, 0f)
            angularVelocity = 2f
        }
        root.addChild(body)
        val tree = SceneTree(root).also { it.start() }
        val ke = tree.totalKineticEnergy()
        // 0.5*2*9 + 0.5*4*4 = 9 + 8 = 17
        assertEquals(17f, ke, EPS)
    }
}
