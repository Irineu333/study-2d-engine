package com.neoutils.engine.debug

import com.neoutils.engine.scene.Node

/**
 * Engine-owned aggregator auto-inserted under `SceneTree.root` (named
 * `__debug`). Carries two sibling sub-containers:
 *
 *  - [worldContainer] (`WorldDebugContainer : Node2D`) — world pass; gets
 *    the active `Camera2D` view transform.
 *  - [screenContainer] (`ScreenDebugCanvas : CanvasLayer`) — UI pass at
 *    `layer = Int.MAX_VALUE - 1`.
 *
 * Plain `Node` (not `Node2D`, not `CanvasLayer`) — has no draw or
 * transform of its own; it exists only to keep the two containers grouped
 * under a single named child of the root and to give the engine a single
 * `onEnter` hook to populate the built-in widgets.
 */
class DebugLayer : Node() {

    val worldContainer: WorldDebugContainer = WorldDebugContainer()
    val screenContainer: ScreenDebugCanvas = ScreenDebugCanvas()

    init {
        name = NODE_NAME
        addChild(worldContainer)
        addChild(screenContainer)
        screenContainer.addChild(DebugToggleNode())
        screenContainer.addChild(TimeControlShortcutNode())
        screenContainer.addChild(DebugLayoutShortcutNode())
        // Backing nodes for the immediate-draw facade: one per space, flushing
        // tree.debug.draw.world/screen during the respective render pass.
        worldContainer.addChild(ImmediateWorldDrawNode())
        screenContainer.addChild(ImmediateScreenDrawNode())
    }

    override fun onEnter() {
        super.onEnter()
        tree?.debug?.bindLayer(this)
    }

    companion object {
        const val NODE_NAME: String = "__debug"
    }
}
