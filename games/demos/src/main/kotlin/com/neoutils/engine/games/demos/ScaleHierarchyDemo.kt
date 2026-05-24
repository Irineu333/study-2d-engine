package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Line2D
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlin.math.sin

/**
 * A parent `Node2D` with a scale that oscillates between MIN_SCALE and
 * MAX_SCALE. The child `ColorRect` keeps a fixed local size — the rendered
 * size grows and shrinks because `ColorRect.onDraw` reads
 * `worldTransform().scale`. This would have stayed visually static before
 * the change since the old code only honored the child's own scale.
 */
@Serializable
class ScaleHierarchyDemo : Node2D() {

    init {
        name = "ScaleHierarchyDemo"
        if (children.isEmpty()) buildTree()
    }

    private fun buildTree() {
        val pivot = ScalePivot().apply {
            name = "ScaleParent"
            transform = Transform(position = Vec2(400f, 300f))
        }
        val child = ColorRect().apply {
            size = Vec2(80f, 80f)
            color = Color(0.6f, 0.85f, 0.3f)
            transform = Transform(position = Vec2(-40f, -40f))
            name = "ScaleChild"
        }
        // Reference outline marking the unscaled bounds. ColorRect is always
        // filled, so we trace the four edges with a Line2D loop instead.
        val reference = Line2D().apply {
            points = listOf(
                Vec2(0f, 0f),
                Vec2(80f, 0f),
                Vec2(80f, 80f),
                Vec2(0f, 80f),
                Vec2(0f, 0f),
            )
            thickness = 1f
            color = Color(1f, 1f, 1f, 0.3f)
            transform = Transform(position = Vec2(360f, 260f))
            name = "ScaleReference"
        }
        addChild(reference)
        addChild(pivot)
        pivot.addChild(child)
    }

    companion object {
        const val MIN_SCALE: Float = 0.5f
        const val MAX_SCALE: Float = 2.0f
        const val SPEED: Float = 1.5f
    }
}

@Serializable
class ScalePivot : Node2D() {

    @kotlinx.serialization.Transient
    private var t: Float = 0f

    override fun onProcess(dt: Float) {
        t += dt
        val s = ScaleHierarchyDemo.MIN_SCALE +
            (ScaleHierarchyDemo.MAX_SCALE - ScaleHierarchyDemo.MIN_SCALE) *
            (0.5f + 0.5f * sin(t * ScaleHierarchyDemo.SPEED))
        transform = transform.copy(scale = Vec2(s, s))
    }
}
