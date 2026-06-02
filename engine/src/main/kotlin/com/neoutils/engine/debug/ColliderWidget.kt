package com.neoutils.engine.debug

import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CircleShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.collectActiveCollisionShapes
import com.neoutils.engine.render.Renderer
import kotlin.math.abs
import kotlin.math.max

/**
 * How [ColliderWidget] visualizes each active collider:
 *
 *  - [AABB] — the broad-phase axis-aligned bounds (the box the broad-phase
 *    actually tests against).
 *  - [REAL] — the real geometry: a circle outline / the rotated world-corner
 *    quad of a rectangle.
 */
enum class ColliderDrawMode { AABB, REAL }

/**
 * World-space gizmo that outlines every active `CollisionShape2D`. The [mode]
 * picks what is drawn — the real shape geometry or the broad-phase AABB
 * (default [ColliderDrawMode.REAL]). Toggling between the two teaches the
 * difference between the shape and the bounding envelope the broad-phase uses.
 *
 * Lives under `WorldDebugContainer`, so the world pass of `SceneTree.render`
 * already applies the current `Camera2D` view transform — no manual
 * `pushTransform` here.
 */
class ColliderWidget : WorldDebugWidget() {

    override val title: String = "Colliders"

    /** Selected at runtime by the internal `ColliderModePanel`; a game may also set it directly. */
    var mode: ColliderDrawMode = ColliderDrawMode.REAL

    init { name = "ColliderWidget" }

    override fun drawDebug(renderer: Renderer) {
        val owningTree = tree ?: return
        for ((collisionShape, owner) in collectActiveCollisionShapes(owningTree)) {
            val color = if (owner is Area2D) DEBUG_AREA_COLOR else DEBUG_BODY_COLOR
            when (mode) {
                ColliderDrawMode.AABB -> {
                    collisionShape.broadPhaseBounds()?.let { bounds ->
                        renderer.drawRect(bounds, color, filled = false)
                    }
                }
                ColliderDrawMode.REAL -> {
                    val world = collisionShape.world()
                    when (val shape = collisionShape.shape) {
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
                        else -> {}
                    }
                }
            }
        }
    }
}
