package com.neoutils.engine.debug

import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree

/**
 * Per-`SceneTree` runtime registry of debug widgets. Holds the four engine
 * built-ins ([fps], [colliders], [momentum], [hud]) as fields for ergonomic
 * direct access, plus a flat list of every registered widget (built-ins +
 * user-registered, in registration order).
 *
 * Not a `Node`, never `@Serializable`, never shared across trees —
 * `SceneTree` owns one instance via its constructor.
 *
 * Routing of [register] / [unregister] depends on the widget's space:
 * `WorldDebugWidget` is added to the `WorldDebugContainer` (world pass),
 * `ScreenDebugWidget` is added to the `ScreenDebugCanvas` (UI pass). The
 * container references are bound by `DebugLayer.onEnter` via [bindLayer].
 */
class DebugRegistry internal constructor(private val tree: SceneTree) {

    private val _widgets: MutableList<DebugWidget> = mutableListOf()

    val fps: FpsWidget = FpsWidget()
    val colliders: ColliderWidget = ColliderWidget()
    val momentum: MomentumWidget = MomentumWidget()
    val hud: DebugHud = DebugHud()

    val widgets: List<DebugWidget> get() = _widgets

    private var worldContainer: WorldDebugContainer? = null
    private var screenContainer: ScreenDebugCanvas? = null
    private var builtinsAttached: Boolean = false

    /**
     * Called by `DebugLayer.onEnter`. Captures the container references and,
     * on first attach, routes the four built-ins through [register]. On
     * re-start (stop → start) the layer keeps its children — only the
     * container refs are refreshed.
     */
    internal fun bindLayer(layer: DebugLayer) {
        worldContainer = layer.worldContainer
        screenContainer = layer.screenContainer
        if (!builtinsAttached) {
            builtinsAttached = true
            register(fps)
            register(colliders)
            register(momentum)
            register(hud)
        }
    }

    fun register(widget: DebugWidget) {
        val world = worldContainer
        val screen = screenContainer
        check(world != null && screen != null) {
            "DebugRegistry.register called before the DebugLayer attached — " +
                "register after tree.start()."
        }
        when (widget) {
            is WorldDebugWidget -> world.addChild(widget)
            is ScreenDebugWidget -> screen.addChild(widget)
            else -> error(
                "DebugWidget '${widget.title}' is neither ScreenDebugWidget nor " +
                    "WorldDebugWidget — extend one of those bases.",
            )
        }
        _widgets += widget
    }

    fun unregister(widget: DebugWidget) {
        val node = widget as? Node ?: return
        node.parent?.removeChild(node)
        _widgets -= widget
    }

    inline fun <reified T : DebugWidget> find(): T? =
        widgets.firstOrNull { it is T } as T?
}
