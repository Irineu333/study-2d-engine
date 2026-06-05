package com.neoutils.engine.scene

import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Scope inside the scene tree whose descendants render in screen-space,
 * decoupled from any `Camera2D` view transform. `SceneTree.render` walks
 * the world subtree first (skipping every `CanvasLayer` and its descendants),
 * then walks all reachable `CanvasLayer`s in `(layer asc, dfs-order asc)`
 * order, starting each subtree from identity transform.
 *
 * Extends `Node` (not `Node2D`) because it does not contribute a local
 * transform to the stack — it interrupts the chain. Direct children that
 * are `Node2D` resume normal `Renderer.pushTransform` semantics rooted at
 * identity established by the layer boundary.
 */
@Serializable
open class CanvasLayer : Node() {

    @Inspect
    var layer: Int = 0

    /**
     * When `true` (default), this layer's subtree is laid out, rendered and
     * hit-tested in **design-space** (`tree.designSize`) with the tree's UI
     * stretch transform applied, so the UI scales and letterboxes onto the
     * surface in step with the world pass. When `false`, the subtree renders in
     * **raw screen pixels** (`tree.size`) — the pre-stretch behavior, used by
     * pixel-locked overlays such as the debug screen canvas.
     */
    @Inspect
    var followStretch: Boolean = true
}
