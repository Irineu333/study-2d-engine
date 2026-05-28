package com.neoutils.engine.debug

import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D

/**
 * Base for debug widgets that draw in world coordinates. Lives under the
 * `WorldDebugContainer` (`Node2D`) child of the auto-inserted `DebugLayer`,
 * so `SceneTree.render`'s world pass applies the current `Camera2D` view
 * transform automatically — subclasses draw world rects/lines as if they
 * were any other scene Node, no manual `pushTransform`.
 */
abstract class WorldDebugWidget : Node2D(), DebugWidget {

    override var enabled: Boolean = false

    final override fun onDraw(renderer: Renderer) {
        if (enabled) drawDebug(renderer)
    }
}
