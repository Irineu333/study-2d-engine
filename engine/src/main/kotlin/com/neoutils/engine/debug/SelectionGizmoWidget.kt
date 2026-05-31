package com.neoutils.engine.debug

import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D

/**
 * World-space gizmo that outlines the selected node's **oriented** box — the
 * four corners of its `localBounds()` projected through `world().apply`, so a
 * rotated/scaled node shows a rotated quad (not a loose AABB). Reads the
 * selection from `tree.debug.scenePicker`; draws nothing when there is no
 * selection or the selected node has no `localBounds()`.
 *
 * Lives under `WorldDebugContainer`, so the world pass of `SceneTree.render`
 * already applies the current `Camera2D` view transform — no manual
 * `pushTransform` here.
 */
class SelectionGizmoWidget : WorldDebugWidget() {

    override val title: String = "Selection"

    init { name = "SelectionGizmoWidget" }

    override fun drawDebug(renderer: Renderer) {
        val owningTree = tree ?: return
        val node = owningTree.debug.scenePicker.selected as? Node2D ?: return
        val local = node.localBounds() ?: return
        val world = node.world()
        val corners = local.corners().map { world.apply(it) }
        for (i in corners.indices) {
            renderer.drawLine(corners[i], corners[(i + 1) % corners.size], 2f, DEBUG_SELECTION_COLOR)
        }
    }
}
