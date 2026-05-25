package com.neoutils.engine.dx

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.collectColliders
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.tree.SceneTree

val DEBUG_COLLIDER_COLOR: Color = Color(0f, 1f, 0f, 0.8f)

/**
 * Backend-agnostic overlay drawing. Called by each `GameHost` after
 * `GameLoop.tick(...)` and before the renderer is unbound. Issues zero draw
 * calls when both `Debug.showFps` and `Debug.colliderVisualization` are off.
 *
 * Splits work into two passes:
 *  1. World pass — collider bounds rendered under the same view transform that
 *     `SceneTree.render` would push, so collider outlines line up with the
 *     projected scene. Runs first, inside a try/finally that pops the pushed
 *     transform (when one was pushed).
 *  2. HUD pass — FPS counter in screen space (identity transform), so it
 *     anchors at the same surface corner regardless of camera bounds. Runs
 *     last, outside any push.
 */
fun renderDebugOverlay(renderer: Renderer, tree: SceneTree) {
    if (Debug.colliderVisualization) {
        val view = tree.currentCamera()?.computeViewTransform(tree.size)
        if (view != null) {
            renderer.pushTransform(view.first, view.second)
            try {
                drawColliders(renderer, tree)
            } finally {
                renderer.popTransform()
            }
        } else {
            drawColliders(renderer, tree)
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

private fun drawColliders(renderer: Renderer, tree: SceneTree) {
    for (collider in collectColliders(tree)) {
        renderer.drawRect(collider.bounds(), DEBUG_COLLIDER_COLOR, filled = false)
    }
}
