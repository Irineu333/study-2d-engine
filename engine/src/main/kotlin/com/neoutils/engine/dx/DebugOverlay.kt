package com.neoutils.engine.dx

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.collectColliders
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Scene

val DEBUG_COLLIDER_COLOR: Color = Color(0f, 1f, 0f, 0.8f)

/**
 * Backend-agnostic overlay drawing. Called by each `GameHost` after
 * `GameLoop.tick(...)` and before the renderer is unbound. Issues zero draw
 * calls when both `Debug.showFps` and `Debug.colliderVisualization` are off.
 */
fun renderDebugOverlay(renderer: Renderer, scene: Scene) {
    if (Debug.colliderVisualization) {
        for (collider in collectColliders(scene)) {
            renderer.drawRect(collider.bounds(), DEBUG_COLLIDER_COLOR, filled = false)
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
