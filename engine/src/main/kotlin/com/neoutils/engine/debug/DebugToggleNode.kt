package com.neoutils.engine.debug

import com.neoutils.engine.scene.Node

/**
 * Engine-internal `Node` that polls `tree.debugHudKey` each tick and flips
 * `tree.debug.hud.enabled` on the edge. Not a `DebugWidget` — it has no
 * `drawDebug`, no row in the HUD, and does not appear in
 * `tree.debug.widgets`. Lives under `ScreenDebugCanvas` purely because it
 * needs to be in the live tree to hook `onProcess`.
 */
internal class DebugToggleNode : Node() {

    init { name = "DebugToggleNode" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        val owningTree = tree ?: return
        val input = owningTree.input ?: return
        if (input.wasKeyPressed(owningTree.debugHudKey)) {
            owningTree.debug.hud.enabled = !owningTree.debug.hud.enabled
        }
    }
}
