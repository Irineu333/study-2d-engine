package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Shape
import kotlinx.serialization.Serializable

/**
 * Two child shapes sit at fixed local positions on a parent that rotates at
 * a constant angular velocity. Because `worldTransform()` composes the
 * parent's rotation into the children's positions, the kids orbit the
 * parent's origin — a regression visual for A1.
 */
@Serializable
class TransformOrbitDemo : Node2D() {

    init {
        name = "TransformOrbitDemo"
        if (children.isEmpty()) buildTree()
    }

    private fun buildTree() {
        val pivot = Node2D().apply {
            name = "OrbitPivot"
            transform = Transform(position = Vec2(400f, 300f))
        }
        val rotator = Rotator().apply { name = "Rotator" }
        val center = Shape().apply {
            kind = Shape.Kind.Circle
            size = Vec2(12f, 12f)
            color = Color.WHITE
            transform = Transform(position = Vec2(-6f, -6f))
            name = "Center"
        }
        val orbiterA = Shape().apply {
            kind = Shape.Kind.Circle
            size = Vec2(20f, 20f)
            color = Color(0.95f, 0.4f, 0.2f)
            transform = Transform(position = Vec2(RADIUS - 10f, -10f))
            name = "OrbiterA"
        }
        val orbiterB = Shape().apply {
            kind = Shape.Kind.Circle
            size = Vec2(20f, 20f)
            color = Color(0.2f, 0.6f, 0.95f)
            transform = Transform(position = Vec2(-RADIUS - 10f, -10f))
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

    override fun onUpdate(dt: Float) {
        transform = transform.copy(
            rotation = transform.rotation + TransformOrbitDemo.ANGULAR_VELOCITY * dt
        )
    }
}
