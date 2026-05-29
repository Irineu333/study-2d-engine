package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.forEachBodyVelocity
import com.neoutils.engine.render.Renderer

/**
 * World-space gizmo that draws a velocity arrow for every live, non-disabled
 * `RigidBody2D` and `CharacterBody2D`: a line from the body's world position
 * along its linear velocity, scaled by [scale], with a small arrow head at
 * the tip. A body with zero velocity draws nothing.
 *
 * Lives under `WorldDebugContainer`, so the world pass of `SceneTree.render`
 * already applies the current `Camera2D` view transform — no manual
 * `pushTransform` here.
 */
class VelocityGizmoWidget : WorldDebugWidget() {

    override val title: String = "Velocity"

    /**
     * Seconds-worth of velocity drawn as arrow length: the arrow spans
     * `velocity * velocityScale` world units. The default keeps the shipped
     * games' typical speeds (pong ball ~200-400 px/s, pool8 break shots)
     * readable; raise it for slow bodies, lower it for fast ones. Named
     * `velocityScale` (not `scale`) to avoid shadowing `Node2D.scale`.
     */
    var velocityScale: Float = 0.1f

    init { name = "VelocityGizmoWidget" }

    override fun drawDebug(renderer: Renderer) {
        val owningTree = tree ?: return
        owningTree.forEachBodyVelocity { position, velocity ->
            if (velocity.x == 0f && velocity.y == 0f) return@forEachBodyVelocity
            val tip = position + velocity * velocityScale
            renderer.drawLine(position, tip, 1f, DEBUG_VELOCITY_COLOR)
            drawArrowHead(renderer, position, tip)
        }
    }

    private fun drawArrowHead(renderer: Renderer, from: Vec2, to: Vec2) {
        val dir = (to - from).normalized
        if (dir.x == 0f && dir.y == 0f) return
        val back = to - dir * ARROW_HEAD
        val perp = Vec2(-dir.y, dir.x) * (ARROW_HEAD * 0.5f)
        renderer.drawLine(to, back + perp, 1f, DEBUG_VELOCITY_COLOR)
        renderer.drawLine(to, back - perp, 1f, DEBUG_VELOCITY_COLOR)
    }

    companion object {
        private const val ARROW_HEAD: Float = 6f
    }
}
