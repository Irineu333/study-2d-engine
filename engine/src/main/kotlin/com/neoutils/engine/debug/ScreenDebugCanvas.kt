package com.neoutils.engine.debug

import com.neoutils.engine.scene.CanvasLayer

/**
 * `CanvasLayer` child of `DebugLayer` that hosts every `ScreenDebugWidget`.
 * Pinned at `layer = Int.MAX_VALUE - 1` so it paints on top of any game UI
 * but still leaves `Int.MAX_VALUE` available for a hypothetical
 * even-higher-priority layer in the future.
 */
class ScreenDebugCanvas : CanvasLayer() {

    init {
        name = "ScreenDebugCanvas"
        layer = LAYER_INDEX
    }

    companion object {
        const val LAYER_INDEX: Int = Int.MAX_VALUE - 1
    }
}
