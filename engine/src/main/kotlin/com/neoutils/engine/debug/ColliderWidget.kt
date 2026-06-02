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
 *  - [BOTH] — the AABB first, then the real geometry on top, so the two are
 *    seen side by side.
 */
enum class ColliderDrawMode { AABB, REAL, BOTH }

/**
 * World-space gizmo that outlines every active `CollisionShape2D`. The [mode]
 * picks what is drawn — the broad-phase AABB, the real shape geometry, or both
 * (default [ColliderDrawMode.REAL]). Drawing the real shape next to its AABB
 * teaches the difference between the shape and the bounding envelope the
 * broad-phase uses.
 *
 * Lives under `WorldDebugContainer`, so the world pass of `SceneTree.render`
 * already applies the current `Camera2D` view transform — no manual
 * `pushTransform` here.
 */
class ColliderWidget : WorldDebugWidget() {

    override val title: String = "Colliders"

    /** Cycled at runtime by the internal `ColliderModeShortcutNode`; a game may also set it directly. */
    var mode: ColliderDrawMode = ColliderDrawMode.REAL

    init { name = "ColliderWidget" }

    override fun drawDebug(renderer: Renderer) {
        val owningTree = tree ?: return
        for ((collisionShape, owner) in collectActiveCollisionShapes(owningTree)) {
            val color = if (owner is Area2D) DEBUG_AREA_COLOR else DEBUG_BODY_COLOR
            if (mode == ColliderDrawMode.AABB || mode == ColliderDrawMode.BOTH) {
                collisionShape.broadPhaseBounds()?.let { bounds ->
                    renderer.drawRect(bounds, color, filled = false)
                }
            }
            if (mode == ColliderDrawMode.REAL || mode == ColliderDrawMode.BOTH) {
                val shape = collisionShape.shape ?: continue
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
}
