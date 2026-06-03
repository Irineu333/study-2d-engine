package com.neoutils.engine.debug

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree

/**
 * Per-`SceneTree` runtime registry of debug widgets. Holds the engine
 * built-in widgets ([colliders], [log], [hud], [timeControls], [profiler],
 * plus the physics gizmos [velocityGizmo], [contactGizmo]) and the
 * immediate-draw facade ([draw], surfaced by an internal `"Debug Draw"`
 * toggle widget) as fields for ergonomic direct access, the per-tree contact
 * buffer ([contacts]) feeding [contactGizmo], plus a flat list of every
 * registered widget (built-ins + user-registered, in registration order).
 *
 * fps is folded into [profiler] (its own `fps` line), the real-geometry
 * collider drawing into [colliders] (its `ColliderDrawMode`), and the momentum
 * overlay was removed — so there are no `fps`, `shapeGizmo`, or `momentum`
 * fields.
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

    val colliders: ColliderWidget = ColliderWidget()
    val log: LogOverlayWidget = LogOverlayWidget()
    val hud: DebugHud = DebugHud()

    /** Per-body linear velocity arrows for `RigidBody2D`/`CharacterBody2D`. */
    val velocityGizmo: VelocityGizmoWidget = VelocityGizmoWidget()

    /** Draws the contacts recorded into [contacts] by `PhysicsSystem.step`. */
    val contactGizmo: ContactGizmoWidget = ContactGizmoWidget()

    /** Pause/step/speed controls over `tree.paused` / `tree.timeScale`. */
    val timeControls: TimeControlWidget = TimeControlWidget()

    /** Per-frame tick phase timings, written by `GameLoop.tick` when profiling. */
    val frameProfile: FrameProfile = FrameProfile()

    /** Per-phase profiler HUD; its `enabled` drives the loop's measurement. */
    val profiler: ProfilerWidget = ProfilerWidget()

    /**
     * Master arm of the Inspector tool: the navigable scene-tree view. Owns the
     * selection (`selected` + `select`), the `enabled` state and the single
     * Inspector HUD row.
     */
    val inspector: SceneTreeWidget = SceneTreeWidget()

    /**
     * Read-only detail panel of `inspector.selected`. The Inspector's
     * screen-space slave arm — driven by `inspector.enabled`, auto-inserted
     * under the screen canvas (docked) but intentionally absent from
     * [widgets]/HUD so it adds no second row.
     */
    val nodeInspector: NodeInspectorWidget = NodeInspectorWidget()

    /**
     * Oriented-box highlight of `inspector.selected` in world space. The
     * Inspector's world-space slave arm — driven by `inspector.enabled`,
     * auto-inserted under the world container but intentionally absent from
     * [widgets]/HUD.
     */
    val selectionGizmo: SelectionGizmoWidget = SelectionGizmoWidget()

    /**
     * Screen-space segmented control (`AABB | REAL`) for [colliders]. The
     * colliders tool's screen-space arm — driven by `colliders.enabled`,
     * auto-inserted under the screen canvas but intentionally absent from
     * [widgets]/HUD (so there is no second "Colliders" row).
     */
    val colliderModePanel: ColliderModePanel = ColliderModePanel()

    /**
     * Per-tree buffer of resolved contacts captured during the last physics
     * step. Recording is gated by `contactGizmo.enabled` (mirrored onto
     * [PhysicsContactBuffer.recording]).
     */
    val contacts: PhysicsContactBuffer = PhysicsContactBuffer()

    /**
     * Immediate-mode drawing facade reached via `tree.debug.draw`. Its single
     * HUD row is the [drawToggle] proxy registered alongside the other
     * built-ins; the facade itself is not a widget.
     */
    val draw: DebugDraw = DebugDraw()

    private val drawToggle: DebugDrawToggle = DebugDrawToggle(draw)

    /**
     * Screen-space layout coordinator. Positions every registered
     * `ScreenDebugWidget` by its declared `DockSlot`; re-flowed by
     * `SceneTree.render` each frame. See [DebugDock].
     */
    val dock: DebugDock = DebugDock()

    val widgets: List<DebugWidget> get() = _widgets

    private var worldContainer: WorldDebugContainer? = null
    private var screenContainer: ScreenDebugCanvas? = null
    private var builtinsAttached: Boolean = false

    /**
     * Panel elected by `SceneTree.hitTestUI` as the owner of the current press —
     * the top-most enabled panel under the pointer on a raw left click. Read by
     * `ScreenDebugWidget.updateDrag` so that only the owner arms its drag, so a
     * press over two overlapping panels never arms both. Cleared at the start of
     * every tick (in `hitTestUI`), so it is non-null only on the press-edge tick.
     */
    internal var pressOwner: ScreenDebugWidget? = null

    /**
     * Called by `DebugLayer.onEnter`. Captures the container references and,
     * on first attach, routes the built-ins through [register]. On re-start
     * (stop → start) the layer keeps its children — only the container refs
     * are refreshed.
     */
    internal fun bindLayer(layer: DebugLayer) {
        worldContainer = layer.worldContainer
        screenContainer = layer.screenContainer
        if (!builtinsAttached) {
            builtinsAttached = true
            register(colliders)
            register(log)
            register(hud)
            register(drawToggle)
            register(velocityGizmo)
            register(contactGizmo)
            register(timeControls)
            register(profiler)
            register(inspector)
            // The detail panel is the Inspector's screen-space slave arm: docked
            // for layout but kept out of `_widgets`/HUD so it never adds a second
            // "Inspector" row — `inspector.enabled` drives it (see
            // NodeInspectorWidget.enabled).
            layer.screenContainer.addChild(nodeInspector)
            dock.add(nodeInspector)
            // The gizmo is the Inspector's world-space slave arm: attach it to
            // the world container so it draws in the world pass, but keep it out
            // of `_widgets`/HUD — `inspector.enabled` drives it (see
            // SelectionGizmoWidget.enabled).
            layer.worldContainer.addChild(selectionGizmo)
            // Likewise the collider mode panel is the colliders tool's
            // screen-space arm: docked for layout but kept out of `_widgets`/HUD
            // so it never adds a second "Colliders" row — `colliders.enabled`
            // drives it (see ColliderModePanel.enabled).
            layer.screenContainer.addChild(colliderModePanel)
            dock.add(colliderModePanel)
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
            is ScreenDebugWidget -> {
                screen.addChild(widget)
                dock.add(widget)
            }
            else -> error(
                "DebugWidget '${widget.title}' is neither ScreenDebugWidget nor " +
                    "WorldDebugWidget — extend one of those bases.",
            )
        }
        _widgets += widget
    }

    fun unregister(widget: DebugWidget) {
        if (widget is ScreenDebugWidget) dock.remove(widget)
        val node = widget as? Node ?: return
        node.parent?.removeChild(node)
        _widgets -= widget
    }

    inline fun <reified T : DebugWidget> find(): T? =
        widgets.firstOrNull { it is T } as T?

    /**
     * Restores every screen widget to its default layout — back to its
     * `defaultSlot` with the default order, un-floated and expanded. Backs the
     * "reset all" layout gesture (polled by the engine-internal
     * `DebugLayoutShortcutNode`); a game can also call it to restore the default
     * debug layout programmatically.
     */
    fun resetAllPanelPositions() {
        for (widget in _widgets) (widget as? ScreenDebugWidget)?.resetPosition()
    }

    /**
     * Whether [pointer] (screen pixels) lands on any enabled screen panel's
     * full rect. `SceneTree.hitTestUI` uses this to consume clicks over a debug
     * panel — the panels are opaque UI, so a click on one must not fall through
     * to the scene picker or gameplay (and a header-drag can begin in `process`
     * without the same click re-picking the world).
     */
    internal fun isOverScreenPanel(pointer: Vec2): Boolean = topPanelAt(pointer) != null

    /**
     * The top-most enabled screen panel whose full rect contains [pointer]
     * (screen pixels), or `null` when none does. Resolved in the
     * `ScreenDebugCanvas`'s child order reversed — last child paints on top, so
     * walking children back-to-front yields the painted top-most panel first.
     * Backs both the press-owner election in `SceneTree.hitTestUI` and the
     * opaque-UI click consumption ([isOverScreenPanel]).
     */
    internal fun topPanelAt(pointer: Vec2): ScreenDebugWidget? {
        val screen = screenContainer ?: return null
        for (child in screen.children.asReversed()) {
            if (child !is ScreenDebugWidget || !child.enabled) continue
            val size = child.contentSize()
            if (size.x > 0f && size.y > 0f && Rect(child.origin, size).contains(pointer)) return child
        }
        return null
    }

    /** Moves [widget] to the top of the `ScreenDebugCanvas` z-order (front-most). */
    internal fun raisePanelToTop(widget: ScreenDebugWidget) {
        screenContainer?.raiseChildToTop(widget)
    }
}
