package com.neoutils.engine.debug

import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node

/**
 * Engine-internal `Node` child of `ScreenDebugCanvas` that flushes
 * `tree.debug.draw.screen` during the UI pass of `SceneTree.render`, in
 * screen pixels (no `Camera2D` view transform). Not a `DebugWidget`: no HUD
 * row, not in `tree.debug.widgets`.
 */
internal class ImmediateScreenDrawNode : Node() {

    init { name = "ImmediateScreenDrawNode" }

    override fun onDraw(renderer: Renderer) {
        tree?.debug?.draw?.screen?.flush(renderer)
    }
}
