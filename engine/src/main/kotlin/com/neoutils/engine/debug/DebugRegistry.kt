package com.neoutils.engine.debug

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree

/**
 * Per-`SceneTree` runtime registry of debug widgets. Holds the engine
 * built-in widgets ([fps], [colliders], [momentum], [log], [hud],
 * [timeControls], [profiler], plus the physics gizmos [shapeGizmo],
 * [velocityGizmo], [contactGizmo]) and the
 * immediate-draw facade ([draw], surfaced by an internal `"Debug Draw"`
 * toggle widget) as fields for ergonomic direct access, the per-tree contact
 * buffer ([contacts]) feeding [contactGizmo], plus a flat list of every
 * registered widget (built-ins + user-registered, in registration order).
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
    val log: LogOverlayWidget = LogOverlayWidget()
    val hud: DebugHud = DebugHud()

    /** Real-geometry collider gizmo (circle outline / rotated-rect quad). */
    val shapeGizmo: ShapeGizmoWidget = ShapeGizmoWidget()

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

    /** Owns the world-space pick selection (breadcrumb + read-only property panel). */
    val scenePicker: ScenePickerWidget = ScenePickerWidget()

    /**
     * Oriented-box highlight of `scenePicker.selected` in world space. The
     * picker's world-space arm — driven by `scenePicker.enabled`, auto-inserted
     * under the world container but intentionally absent from [widgets]/HUD.
     */
    val selectionGizmo: SelectionGizmoWidget = SelectionGizmoWidget()

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
     * Called by `DebugLayer.onEnter`. Captures the container references and,
     * on first attach, routes the five built-ins through [register]. On
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
            register(log)
            register(hud)
            register(drawToggle)
            register(shapeGizmo)
            register(velocityGizmo)
            register(contactGizmo)
            register(timeControls)
            register(profiler)
            register(scenePicker)
            // The gizmo is the picker's world-space arm, not a standalone
            // widget: attach it to the world container so it draws in the world
            // pass, but keep it out of `_widgets`/HUD — `scenePicker.enabled`
            // drives it (see SelectionGizmoWidget.enabled).
            layer.worldContainer.addChild(selectionGizmo)
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
    internal fun isOverScreenPanel(pointer: Vec2): Boolean {
        for (widget in _widgets) {
            if (widget !is ScreenDebugWidget || !widget.enabled) continue
            val size = widget.contentSize()
            if (size.x > 0f && size.y > 0f && Rect(widget.origin, size).contains(pointer)) return true
        }
        return false
    }
}
