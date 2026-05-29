package com.neoutils.engine.debug

import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D

/**
 * Engine-internal `Node2D` child of `WorldDebugContainer` that flushes
 * `tree.debug.draw.world` during the world pass of `SceneTree.render`. It
 * lives under the world container, so the pass already has the active
 * `Camera2D` view transform applied — it issues no `pushTransform` of its
 * own, just replays the buffered commands. Not a `DebugWidget`: no HUD row,
 * not in `tree.debug.widgets`.
 */
internal class ImmediateWorldDrawNode : Node2D() {

    init { name = "ImmediateWorldDrawNode" }

    override fun onDraw(renderer: Renderer) {
        tree?.debug?.draw?.world?.flush(renderer)
    }
}
