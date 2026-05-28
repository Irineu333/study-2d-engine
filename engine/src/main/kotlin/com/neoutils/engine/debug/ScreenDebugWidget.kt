package com.neoutils.engine.debug

import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node

/**
 * Base for debug widgets that render in screen pixels (no `Camera2D` view
 * transform). Lives under the `ScreenDebugCanvas` (`CanvasLayer`) child of
 * the auto-inserted `DebugLayer`. The final `onDraw` gates `drawDebug` on
 * [enabled], so subclasses focus on the visualization without re-checking
 * the flag.
 */
abstract class ScreenDebugWidget : Node(), DebugWidget {

    override var enabled: Boolean = false

    final override fun onDraw(renderer: Renderer) {
        if (enabled) drawDebug(renderer)
    }
}
