package com.neoutils.engine.debug

import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CircleShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.collectActiveCollisionShapes
import com.neoutils.engine.render.Renderer
import kotlin.math.abs
import kotlin.math.max

/**
 * World-space gizmo that draws the **real geometry** of every active
 * `CollisionShape2D` — a non-filled outline for circles, the closed
 * world-corner quad for rectangles (so a rotated rect shows as a rotated
 * quad, not a box). Complements `ColliderWidget`, which draws the AABB the
 * broad-phase actually uses: seeing both side by side teaches the difference
 * between real shape and bounding envelope.
 *
 * Lives under `WorldDebugContainer`, so the world pass of `SceneTree.render`
 * already applies the current `Camera2D` view transform — no manual
 * `pushTransform` here.
 */
class ShapeGizmoWidget : WorldDebugWidget() {

    override val title: String = "Shapes"

    init { name = "ShapeGizmoWidget" }

    override fun drawDebug(renderer: Renderer) {
        val owningTree = tree ?: return
        for ((collisionShape, owner) in collectActiveCollisionShapes(owningTree)) {
            val shape = collisionShape.shape ?: continue
            val color = if (owner is Area2D) DEBUG_AREA_COLOR else DEBUG_BODY_COLOR
            val world = collisionShape.world()
            when (shape) {
                is CircleShape2D -> {
                    val r = shape.radius * max(abs(world.scale.x), abs(world.scale.y))
                    renderer.drawCircle(world.position, r, color, filled = false)
                }
                is RectangleShape2D -> {
                    val corners = shape.worldCorners(world)
                    for (i in corners.indices) {
                        val a = corners[i]
                        val b = corners[(i + 1) % corners.size]
                        renderer.drawLine(a, b, 1f, color)
                    }
                }
            }
        }
    }
}
