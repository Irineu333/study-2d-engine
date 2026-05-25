package com.neoutils.engine.dx

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.collectActiveCollisionShapes
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.tree.SceneTree

/** Color used to outline `Area2D` shape bounds (triggers, e.g. goals). */
val DEBUG_AREA_COLOR: Color = Color(0f, 1f, 0f, 0.8f)

/** Color used to outline `PhysicsBody2D` shape bounds (solid bodies). */
val DEBUG_BODY_COLOR: Color = Color(1f, 0.3f, 0.3f, 0.8f)

/**
 * Backend-agnostic overlay drawing. Called by each `GameHost` after
 * `GameLoop.tick(...)` and before the renderer is unbound. Issues zero draw
 * calls when both `Debug.showFps` and `Debug.colliderVisualization` are off.
 *
 * Splits work into two passes:
 *  1. World pass — collision-shape bounds rendered under the same view
 *     transform that `SceneTree.render` would push, so outlines align with
 *     the projected scene. Runs first, inside a try/finally that pops the
 *     pushed transform (when one was pushed). `Area2D` shapes use
 *     [DEBUG_AREA_COLOR]; `PhysicsBody2D` shapes use [DEBUG_BODY_COLOR].
 *  2. HUD pass — FPS counter in screen space (identity transform), so it
 *     anchors at the same surface corner regardless of camera bounds.
 *     Runs last, outside any push.
 */
fun renderDebugOverlay(renderer: Renderer, tree: SceneTree) {
    if (Debug.colliderVisualization) {
        val view = tree.currentCamera()?.computeViewTransform(tree.size)
        if (view != null) {
            renderer.pushTransform(view.first, view.second)
            try {
                drawCollisionShapes(renderer, tree)
            } finally {
                renderer.popTransform()
            }
        } else {
            drawCollisionShapes(renderer, tree)
        }
    }
    if (Debug.showFps) {
        renderer.drawText(
            text = "fps ${Debug.currentFps.toInt()}",
            position = Vec2(8f, 24f),
            size = 18f,
            color = Color.WHITE,
        )
    }
}

private fun drawCollisionShapes(renderer: Renderer, tree: SceneTree) {
    for ((shape, owner) in collectActiveCollisionShapes(tree)) {
        val bounds = shape.worldBounds() ?: continue
        val color = if (owner is Area2D) DEBUG_AREA_COLOR else DEBUG_BODY_COLOR
        renderer.drawRect(bounds, color, filled = false)
    }
}
