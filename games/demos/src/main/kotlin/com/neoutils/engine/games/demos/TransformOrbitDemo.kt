package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Two child shapes sit at fixed local positions on a parent that rotates at
 * a constant angular velocity. Because `world()` composes the
 * parent's rotation into the children's positions, the kids orbit the
 * parent's origin — a regression visual for A1.
 */
@Serializable
class TransformOrbitDemo : Node2D() {

    @Transient
    private var lastSize: Vec2 = Vec2.ZERO

    init {
        name = "TransformOrbitDemo"
        if (children.isEmpty()) buildTree()
    }

    override fun onProcess(dt: Float) {
        val tree = tree ?: return
        if (tree.size == lastSize) return
        lastSize = tree.size
        val pivot = findChild("OrbitPivot") as? Node2D ?: return
        pivot.transform = pivot.transform.copy(
            position = Vec2(tree.width / 2f, tree.height / 2f),
        )
    }

    private fun buildTree() {
        val pivot = Node2D().apply {
            name = "OrbitPivot"
            transform = Transform(position = Vec2(400f, 300f))
        }
        val rotator = Rotator().apply { name = "Rotator" }
        // Circle2D draws at its local origin (Vec2.ZERO), so the local
        // position IS the visual center — no half-radius offset needed.
        val center = Circle2D().apply {
            radius = 6f
            color = Color.WHITE
            transform = Transform(position = Vec2.ZERO)
            name = "Center"
        }
        val orbiterA = Circle2D().apply {
            radius = 10f
            color = Color(0.95f, 0.4f, 0.2f)
            transform = Transform(position = Vec2(RADIUS, 0f))
            name = "OrbiterA"
        }
        val orbiterB = Circle2D().apply {
            radius = 10f
            color = Color(0.2f, 0.6f, 0.95f)
            transform = Transform(position = Vec2(-RADIUS, 0f))
            name = "OrbiterB"
        }
        addChild(pivot)
        pivot.addChild(center)
        pivot.addChild(rotator)
        rotator.addChild(orbiterA)
        rotator.addChild(orbiterB)
    }

    companion object {
        const val RADIUS: Float = 120f
        const val ANGULAR_VELOCITY: Float = 1.2f
    }
}

@Serializable
class Rotator : Node2D() {

    override fun onProcess(dt: Float) {
        transform = transform.copy(
            rotation = transform.rotation + TransformOrbitDemo.ANGULAR_VELOCITY * dt
        )
    }
}
