package com.neoutils.engine.debug

import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.collectActiveCollisionShapes
import com.neoutils.engine.render.Renderer

/**
 * World-space gizmo that outlines every active `CollisionShape2D`'s world
 * AABB. Lives under `WorldDebugContainer`, so the world pass of
 * `SceneTree.render` already applies the current `Camera2D` view transform
 * — no manual `pushTransform` here.
 */
class ColliderWidget : WorldDebugWidget() {

    override val title: String = "Colliders"

    init { name = "ColliderWidget" }

    override fun drawDebug(renderer: Renderer) {
        val owningTree = tree ?: return
        for ((shape, owner) in collectActiveCollisionShapes(owningTree)) {
            val bounds = shape.worldBounds() ?: continue
            val color = if (owner is Area2D) DEBUG_AREA_COLOR else DEBUG_BODY_COLOR
            renderer.drawRect(bounds, color, filled = false)
        }
    }
}
