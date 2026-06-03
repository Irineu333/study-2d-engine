package com.neoutils.engine.debug

import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D

/**
 * World-space gizmo that outlines the selected node's **oriented** box — the
 * four corners of its `localBounds()` projected through `world().apply`, so a
 * rotated/scaled node shows a rotated quad (not a loose AABB). Reads the
 * selection from `tree.debug.inspector`; draws nothing when there is no
 * selection or the selected node has no `localBounds()`.
 *
 * This is the **world-space arm of the single Inspector tool**, not an
 * independent widget: its [enabled] is derived from `inspector.enabled` (one
 * HUD row controls both) and direct writes are ignored. It is auto-inserted
 * under `WorldDebugContainer` — so the world pass of `SceneTree.render` applies
 * the current `Camera2D` view transform (no manual `pushTransform`) — but is
 * deliberately kept out of `DebugRegistry.widgets` and the HUD.
 */
class SelectionGizmoWidget : WorldDebugWidget() {

    override val title: String = "Selection"

    /**
     * Derived from the owning tree's `inspector.enabled`: the gizmo turns on
     * and off together with the Inspector. The setter is a no-op so nothing can
     * desync the two halves of the one tool.
     */
    override var enabled: Boolean
        get() = tree?.debug?.inspector?.enabled ?: false
        set(_) {}

    init { name = "SelectionGizmoWidget" }

    override fun drawDebug(renderer: Renderer) {
        val owningTree = tree ?: return
        val node = owningTree.debug.inspector.selected as? Node2D ?: return
        val local = node.localBounds() ?: return
        val world = node.world()
        val corners = local.corners().map { world.apply(it) }
        for (i in corners.indices) {
            renderer.drawLine(corners[i], corners[(i + 1) % corners.size], 2f, DEBUG_SELECTION_COLOR)
        }
    }
}
