package com.neoutils.engine.tree

import com.neoutils.engine.audio.AudioBackend
import com.neoutils.engine.debug.DebugLayer
import com.neoutils.engine.debug.DebugRegistry
import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.TextMeasurer
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.Control
import com.neoutils.engine.scene.MouseFilter
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.UiStretchMode
import com.neoutils.engine.scene.fitTransform
import com.neoutils.engine.scene.toAspectMode

/**
 * Live owner of the scene graph. Holds the driver/host/query concerns that used
 * to belong to `Scene`: the `input` injected by the loop, the surface `size`
 * injected by the host, the computed `viewport`, the loop-phase flags, the
 * traversal entry points (`process`, `physicsProcess`, `render`, `applyPending`),
 * and the tree-walk queries (`getNodesInGroup`, `currentCamera`, `screenToWorld`,
 * `worldToScreen`).
 *
 * Not a `Node` — has no `parent`, no `children`, no `transform`, no lifecycle
 * hooks of its own. Not `@Serializable` — every field is runtime state.
 *
 * The single [root] reference can be any `Node`. Populate the initial tree from
 * the root's `onEnter()` (code-only) or load it via `SceneLoader.load(...)` /
 * `BundleLoader.fromResources(...)` and wrap the returned `Node` in
 * `SceneTree(root = ...)`.
 */
class SceneTree(val root: Node) {

    /** Set by the runtime (`GameLoop`) at the start of each tick. */
    @Volatile var input: Input? = null
        internal set

    /**
     * Per-tree registry of debug widgets. Holds the engine built-ins
     * (`fps`, `colliders`, `momentum`, `log`, `hud`, `timeControls`, plus the
     * physics gizmos `shapeGizmo`, `velocityGizmo`, `contactGizmo`) and the
     * immediate-draw
     * facade (`draw`) as convenience fields and the full registration list.
     * Game code adds custom widgets via
     * `tree.debug.register(...)` after `start()`. Read by the auto-inserted
     * `DebugLayer` and rendered as ordinary scene nodes.
     */
    val debug: DebugRegistry = DebugRegistry(this)

    /**
     * Key the engine-internal `DebugToggleNode` polls each tick to toggle
     * `tree.debug.hud.enabled`. `GameHost` implementations assign
     * `config.debugHudKey` here once during startup; gameplay code does not
     * touch this property.
     */
    var debugHudKey: Key = Key.F1

    /**
     * Off-frame font metrics, assigned by the host at startup (before the first
     * frame) so engine code that needs text size outside a draw pass — notably
     * [com.neoutils.engine.scene.Label.localBounds] — can measure without a
     * bound `Renderer`. `null` until a host wires it (e.g. unit tests driving
     * the tree directly), in which case `Label.localBounds()` degrades to
     * `null`. Backend-supplied; never serialized.
     */
    var textMeasurer: TextMeasurer? = null

    /**
     * Off-frame audio backend, assigned by the host at startup (before the
     * first frame), mirroring [textMeasurer]. Nodes and scripts trigger sound
     * effects off-frame via `node.tree.audio?.play(...)`. `null` until a host
     * wires it (e.g. unit tests, headless runs), in which case every
     * `audio?.play(...)` is a graceful no-op. Disposed exactly once in [stop].
     * Backend-supplied; never serialized.
     */
    @Volatile var audio: AudioBackend? = null

    /**
     * Gameplay time multiplier applied by `GameLoop` before accumulating the
     * frame delta for physics and before `process`. `1f` is normal speed,
     * `0.25f` slow-motion, `2f` fast-forward, `0f` a soft freeze (no physics,
     * `process` runs at `dt = 0`). First-class on the tree — gameplay may use
     * it as a slow-mo feature; the debug `TimeControlWidget` is only a UI over
     * this. Coerced to `>= 0f` in the setter (negative time has no meaning).
     * Runtime-only state — never serialized, never shared across trees.
     */
    var timeScale: Float = 1f
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    /**
     * Hard freeze independent of [timeScale]. When `true`, `GameLoop` runs no
     * physics and invokes `process(0f)` (rather than skipping `process`), so
     * debug nodes that poll input and screen-space UI stay alive while the
     * frozen frame and HUD keep drawing. Runtime-only state — never serialized.
     */
    var paused: Boolean = false

    /** Single-use step flag; consumed by `GameLoop` each tick (see [requestStep]). */
    private var pendingStep: Boolean = false

    /**
     * Requests exactly one fixed physics step on the next tick while the tree
     * is frozen ([paused] or `timeScale == 0`). Single-use: `GameLoop` consumes
     * and clears the flag every tick, so each call advances exactly one step
     * and a step requested while time is flowing is a no-op (consumed and
     * ignored by the running path).
     */
    fun requestStep() {
        pendingStep = true
    }

    /**
     * Consumed by `GameLoop` once per tick: returns whether a step was pending
     * and clears the flag unconditionally, so a request never survives a tick.
     */
    internal fun consumePendingStep(): Boolean {
        val pending = pendingStep
        pendingStep = false
        return pending
    }

    /** Test/inspection hook: whether a step request is currently pending. */
    internal val hasPendingStep: Boolean get() = pendingStep

    /**
     * Set by [com.neoutils.engine.loop.GameLoop] at construction so engine-side
     * collision queries (e.g. [com.neoutils.engine.physics.Area2D.getOverlappingAreas])
     * can reach the active physics state. `null` while the tree is not driven
     * by a GameLoop (e.g. unit tests that exercise the tree directly).
     */
    internal var physicsSystem: PhysicsSystem? = null

    /**
     * Surface size in pixels, kept current by the host via [resize]. The
     * canonical world extent when no `Camera2D` is active; see [viewport].
     */
    var size: Vec2 = Vec2.ZERO
        private set

    val width: Float get() = size.x
    val height: Float get() = size.y

    /**
     * Reference resolution UI (`CanvasLayer`s with `followStretch = true`) is
     * authored against. Defaulted once at [start] from the current
     * `Camera2D.bounds` size when a camera exists — then it is **stable**, so a
     * panning or zooming gameplay camera never disturbs the HUD. With **no**
     * camera there is no fixed reference, so it tracks the live surface size on
     * [resize], keeping the UI stretch identity (raw screen-space) at every
     * window size. Settable for the rare scene that needs a design resolution
     * decoupled from the camera; an explicit write before [start] suppresses the
     * default derivation and freezes the value (no surface tracking).
     */
    var designSize: Vec2
        get() = _designSize
        set(value) {
            _designSize = value
            designSizeInitialized = true
            designSizeTracksSurface = false
        }

    private var _designSize: Vec2 = Vec2.ZERO
    private var designSizeInitialized = false
    private var designSizeTracksSurface = false

    /**
     * How [designSize] maps onto the surface for stretched `CanvasLayer`s.
     * `FIT` (default) letterboxes uniformly to match a `FIT` `Camera2D`;
     * `DISABLED` keeps the UI in raw screen pixels.
     */
    var uiStretchMode: UiStretchMode = UiStretchMode.FIT

    /**
     * The UI stretch transform `(translation, scale)` mapping
     * `Rect(Vec2.ZERO, designSize)` onto `Rect(Vec2.ZERO, size)` under
     * [uiStretchMode], carrying only the resolution-fit scale and the letterbox
     * centering — never a `Camera2D` pan/zoom. `null` (treated as identity, no
     * push) when [uiStretchMode] is `DISABLED`, when [designSize] is degenerate,
     * or when it would equal identity (`designSize == size`).
     */
    fun uiStretchTransform(): Pair<Vec2, Vec2>? {
        val mode = uiStretchMode.toAspectMode() ?: return null
        return fitTransform(designSize, size, mode)
    }

    /**
     * Visible world rect. Resolves to the active `Camera2D.bounds` when a
     * camera is current, or to `Rect(Vec2.ZERO, size)` otherwise. Computed
     * on demand: there is no cached "active camera" state.
     */
    val viewport: Rect
        get() = currentCamera()?.bounds ?: Rect(Vec2.ZERO, size)

    /**
     * `true` while the tree is inside an `onProcess`, `onPhysicsProcess`,
     * collision event dispatch or `onDraw` traversal (or another physics
     * phase). Read by
     * `Node.addChild` / `Node.removeChild` to decide between immediate
     * mutation and enqueuing onto the pending queues.
     */
    internal var isMutationDeferred: Boolean = false
        private set

    /**
     * `true` only during render traversal. `addChild`/`removeChild` called
     * while this is set are logged and dropped: scene-graph mutation during
     * render has no use case and would cost more complexity than it saves.
     */
    internal var isRendering: Boolean = false
        private set

    /**
     * Optional listener invoked by [resize] when the surface size changes.
     * Single-slot by design — `SceneTree` is not subclassable; consumers that
     * need to react to resize install a callback here. Promoted to a `Signal`
     * in a future change if demand for multi-listener emerges.
     */
    var onResize: ((Float, Float) -> Unit)? = null

    /** Called by the runtime when the rendering surface size changes. */
    fun resize(width: Float, height: Float) {
        if (width == size.x && height == size.y) return
        size = Vec2(width, height)
        // No-camera trees have no fixed design reference: keep designSize glued
        // to the surface so the UI stretch stays identity (raw screen-space).
        if (designSizeTracksSurface) _designSize = size
        onResize?.invoke(width, height)
    }

    fun start() {
        if (!root.isLive) {
            root.attachToLiveTree(this)
            // Auto-insert AFTER the root's onEnter so existing roots that key
            // off `children.isEmpty()` for first-run setup are not surprised
            // by the debug layer being present at attach time. Idempotent —
            // a re-start whose root already carries the layer is a no-op.
            ensureDebugLayer()
            initDesignSize()
        }
    }

    /**
     * Captures the stable [designSize] default the first time the tree starts,
     * unless a caller already set it explicitly. The camera and surface are both
     * known here (the host resized before the first tick and the root's
     * `onEnter` populated the tree), so the current `Camera2D.bounds` is the
     * natural reference — UI and world then share a design space and their
     * letterbox bars coincide. Falls back to the surface size when there is no
     * camera (then the stretch is identity and UI behaves as raw screen-space).
     */
    private fun initDesignSize() {
        if (designSizeInitialized) return
        val bounds = currentCamera()?.bounds
        if (bounds != null && bounds.size.x > 0f && bounds.size.y > 0f) {
            // Camera present: freeze the design resolution to its bounds so UI
            // and world share a design space and their letterbox bars coincide.
            designSize = bounds.size
        } else {
            // No camera: the surface itself is the reference resolution. Track
            // it on resize so the stretch stays identity at every size (raw
            // screen-space) instead of freezing the initial size and
            // letterboxing/scaling the UI when the window changes.
            _designSize = size
            designSizeInitialized = true
            designSizeTracksSurface = true
        }
    }

    private fun ensureDebugLayer() {
        if (root.findChild(DebugLayer.NODE_NAME) == null) {
            root.addChild(DebugLayer())
        }
    }

    fun stop() {
        if (root.isLive) root.detachFromLiveTree()
        // Release native audio lines/mixer on teardown (symmetry with the
        // host wiring `audio` at startup). Cleared so a double stop disposes
        // at most once.
        audio?.dispose()
        audio = null
    }

    /**
     * UI hit-test phase. Runs at the start of each tick (between
     * `input.beginTick()` and `tree.process(dt)`). Resets
     * [Input.mouseClickConsumed] and [Input.mouseDragConsumed] to `false`
     * (the drag flag is then set later in the tick by a debug panel that owns
     * an active drag), then — if the left mouse button
     * fired this tick — walks every reachable `CanvasLayer` top-most-first
     * (sorted by `(layer desc, dfs-order desc)`); inside each layer subtree
     * the first enabled `Button` whose `screenRect()` contains the pointer
     * absorbs the click: it arms its internal press cycle and sets
     * `input.mouseClickConsumed = true`, after which the walk stops. A press over
     * any enabled debug panel brings that panel to the front of the z-order —
     * even when the press lands on a `Button` inside it (the button still acts;
     * only the panel's stacking changes). When no `Button` absorbs the click, the
     * top-most panel under the pointer additionally becomes the press owner (the
     * sole panel that may arm a drag) and the click is consumed — the panels are
     * opaque UI, so it does not re-pick via [hitTestPick] nor reach gameplay.
     */
    fun hitTestUI(input: Input) {
        input.mouseClickConsumed = false
        input.mouseDragConsumed = false
        input.scrollConsumed = false
        debug.pressOwner = null
        if (!root.isLive) return
        runAnchorLayout()
        routeScroll(input)
        if (!input.wasMouseClickedRaw(MouseButton.Left)) return
        val pointer = input.pointerPosition
        // Bring-to-front first, independent of what absorbs the click: a press over
        // a panel raises it even when a Button inside the panel handles the press.
        val panel = debug.topPanelAt(pointer)
        if (panel != null) debug.raisePanelToTop(panel)
        // collectCanvasLayers returns (layer asc, dfs-order asc) via stable sort;
        // reversing flips to (layer desc, dfs-order desc) — top-most-first.
        val stretch = uiStretchTransform()
        for (layer in collectCanvasLayers().asReversed()) {
            // A stretched layer is hit in design-space: map the screen pointer
            // back through the inverse stretch so a click lands on the Button
            // where it is drawn. Raw layers test against the raw pointer.
            val layerPointer = if (layer.followStretch && stretch != null) {
                val (t, s) = stretch
                Vec2((pointer.x - t.x) / s.x, (pointer.y - t.y) / s.y)
            } else {
                pointer
            }
            val hit = findUiConsumer(layer, layerPointer)
            if (hit != null) {
                input.mouseClickConsumed = true
                return
            }
        }
        // No button absorbed it: the top-most panel under the pointer owns the
        // press — only that panel arms its drag (so a press on the overlap never
        // arms two). A click on any panel is consumed regardless (panels are opaque
        // UI) so it neither re-picks via hitTestPick nor reaches gameplay.
        if (panel != null) {
            debug.pressOwner = panel
            input.mouseClickConsumed = true
        }
    }

    /**
     * Wheel routing, resolved in the UI phase (before `process`) so a gameplay
     * node cannot react to a wheel a debug panel absorbed. Resolves the top-most
     * panel under the pointer; if it is a scrollable `ScreenDebugWidget` whose
     * viewport contains the pointer, the wheel scrolls it and is consumed. The
     * offset itself stays owned by the widget.
     */
    private fun routeScroll(input: Input) {
        val deltaY = input.scrollDelta.y
        if (deltaY == 0f) return
        val panel = debug.topPanelAt(input.pointerPosition) ?: return
        if (panel.applyScroll(input.pointerPosition, deltaY)) {
            input.scrollConsumed = true
        }
    }

    /**
     * Reverse-DFS walk of [layer]'s subtree returning the first `Control` that
     * **consumes** the press at [pointer] — a `STOP` control (opaque UI) or an
     * enabled `STOP` `Button`, which is armed as a side effect. Reverse order
     * means the last-drawn (top-most) sibling at every level wins overlap within
     * the layer. Invisible Control subtrees are skipped entirely; `IGNORE`
     * controls are never tested; `PASS` controls register (arming an enabled
     * Button) but do not consume, so the walk continues to whatever is behind.
     * Nested CanvasLayers are skipped — they own their own pass.
     */
    private fun findUiConsumer(layer: CanvasLayer, pointer: Vec2): Control? {
        for (child in layer.children.asReversed()) {
            val hit = findUiConsumerInSubtree(child, pointer)
            if (hit != null) return hit
        }
        return null
    }

    private fun findUiConsumerInSubtree(node: Node, pointer: Vec2): Control? {
        if (node is CanvasLayer) return null
        // Invisibility hides the whole subtree from the hit-test (conjunctive).
        if (node is Control && !node.visible) return null
        for (child in node.children.asReversed()) {
            val hit = findUiConsumerInSubtree(child, pointer)
            if (hit != null) return hit
        }
        if (node is Control && node.mouseFilter != MouseFilter.IGNORE) {
            val rect = node.screenRect()
            if (rect != null && rect.contains(pointer)) {
                if (node is Button) {
                    if (!node.disabled) {
                        node.armPress()
                        if (node.mouseFilter == MouseFilter.STOP) return node
                    }
                } else if (node.mouseFilter == MouseFilter.STOP) {
                    return node
                }
                // PASS (and disabled buttons): observed, not consumed.
            }
        }
        return null
    }

    /**
     * Anchor layout pass. Resolves every reachable `Control`'s `position`/`size`
     * from its anchors/offsets against its parent rect, top-down so a parent
     * Control resolves before its children. The parent rect is the resolved
     * rect of the nearest ancestor `Control`, or — at each `CanvasLayer`
     * boundary (and at the root) — the design rect `Rect(ZERO, designSize)`
     * when the layer `followStretch`es, else the surface rect `Rect(ZERO, size)`.
     * Runs before the UI render pass and the UI hit-test each tick, so a surface
     * or parent resize reflows anchored controls with no per-frame script code.
     */
    private fun runAnchorLayout() {
        layoutWalk(root, Rect(Vec2.ZERO, size))
    }

    private fun layoutWalk(node: Node, parentRect: Rect) {
        if (node is CanvasLayer) {
            // A CanvasLayer establishes screen-space: a stretched layer resolves
            // its children against the design rect (the UI stretch transform
            // projects it onto the surface at render time), a raw layer against
            // the surface — independent of any world ancestor rect.
            val layerRect = if (node.followStretch) {
                Rect(Vec2.ZERO, designSize)
            } else {
                Rect(Vec2.ZERO, size)
            }
            for (child in node.children) layoutWalk(child, layerRect)
            return
        }
        if (node is Control) {
            val resolved = node.resolveLayout(parentRect)
            for (child in node.children) layoutWalk(child, resolved)
        } else {
            for (child in node.children) layoutWalk(child, parentRect)
        }
    }

    /**
     * Scene-pick hit-test phase. Runs in `GameLoop.tick` right after
     * [hitTestUI] and before `tree.process`, gated on
     * `debug.inspector.enabled`. When disabled it is a strict no-op — no tree
     * walk, no selection change, [Input.mouseClickConsumed] untouched — so the
     * picker costs nothing while off.
     *
     * When enabled and the UI did not already absorb the left click, it claims
     * the click ([Input.mouseClickConsumed] = `true`, pre-empting gameplay) and
     * resolves the selection: every world-space `Node2D` with a non-null
     * `localBounds()` (skipping `CanvasLayer` subtrees, invariant #6) whose
     * world `worldBounds()` AABB contains the click is broad-phase accepted,
     * then confirmed by the oriented test
     * `localBounds().contains(world().applyInverse(clickWorld))`. Confirmed
     * candidates come out in DFS draw-order; the picker selects the front-most
     * on a fresh click and cycles through the stack on repeated near-same
     * clicks. Never mutates the tree (invariant #1).
     */
    fun hitTestPick(input: Input) {
        val picker = debug.inspector
        if (!picker.enabled) return
        if (!root.isLive) return
        if (input.mouseClickConsumed) return
        if (!input.wasMouseClickedRaw(MouseButton.Left)) return
        input.mouseClickConsumed = true
        val clickWorld = screenToWorld(input.pointerPosition)
        val candidates = mutableListOf<Node2D>()
        collectPickCandidates(root, clickWorld, candidates)
        // DFS pre-order appends a node before its children, so later entries are
        // painted on top; reversing yields front-most-first.
        picker.applyPick(input.pointerPosition, candidates.asReversed())
    }

    private fun collectPickCandidates(node: Node, clickWorld: Vec2, out: MutableList<Node2D>) {
        if (node is CanvasLayer) return
        if (node is Node2D) {
            val aabb = node.worldBounds()
            if (aabb != null && aabb.contains(clickWorld)) {
                val local = node.localBounds()
                if (local != null && local.contains(node.world().applyInverse(clickWorld))) {
                    out += node
                }
            }
        }
        for (child in node.children) collectPickCandidates(child, clickWorld, out)
    }

    fun process(dt: Float) {
        if (!root.isLive) return
        runTraversal(rendering = false) { traverseProcess(root, dt) }
    }

    fun physicsProcess(dt: Float) {
        if (!root.isLive) return
        runTraversal(rendering = false) { traversePhysicsProcess(root, dt) }
    }

    fun render(renderer: Renderer) {
        if (!root.isLive) return
        runAnchorLayout()
        // Place every screen-space debug widget by its DockSlot before drawing;
        // this re-flows on resize and as variable-height widgets change size.
        debug.dock.relayout(size)
        runTraversal(rendering = true) {
            // World pass: skip CanvasLayer subtrees, apply Camera2D view transform.
            val view = currentCamera()?.computeViewTransform(size)
            if (view != null) {
                renderer.pushTransform(view.first, 0f, view.second)
                try {
                    traverseWorldDraw(root, renderer)
                } finally {
                    renderer.popTransform()
                }
            } else {
                traverseWorldDraw(root, renderer)
            }
            // UI pass: each CanvasLayer in (layer asc, dfs-order asc). A
            // followStretch layer pushes the UI stretch transform around its
            // subtree (design-space → surface); a raw layer starts from identity.
            val layers = collectCanvasLayers()
            val stretch = uiStretchTransform()
            for (layer in layers) {
                if (layer.followStretch && stretch != null) {
                    renderer.pushTransform(stretch.first, 0f, stretch.second)
                    try {
                        traverseCanvasLayerSubtree(layer, renderer)
                    } finally {
                        renderer.popTransform()
                    }
                } else {
                    traverseCanvasLayerSubtree(layer, renderer)
                }
            }
            // Dock overlay (insertion indicator) on top of the UI, screen pixels.
            debug.dock.drawOverlay(renderer)
        }
        // Immediate-draw buffers are single-frame: the backing nodes flushed
        // them during the two passes above, so discard them here. Commands
        // enqueued in process/physics of this tick were drawn exactly once.
        debug.draw.clearFrame()
    }

    /**
     * Drains pending child mutations enqueued during the previous traversal.
     * Drained in post-order (children first), removals before additions, so
     * lifecycle is coherent across the whole subtree before the next phase
     * begins.
     */
    fun applyPending() {
        root.drainPending()
    }

    /**
     * Returns every live `Node` reachable from [root] whose `groups` contain
     * [name]. Pre-order walk; O(N) in the tree size.
     */
    fun getNodesInGroup(name: String): List<Node> {
        val out = mutableListOf<Node>()
        collectInGroup(root, name, out)
        return out
    }

    private fun collectInGroup(node: Node, name: String, out: MutableList<Node>) {
        if (node.isInGroup(name)) out += node
        for (child in node.children) collectInGroup(child, name, out)
    }

    internal fun beginPhysicsPhase() {
        isMutationDeferred = true
    }

    internal fun endPhysicsPhase() {
        isMutationDeferred = false
    }

    internal fun currentCamera(): Camera2D? = findCurrentCamera(root)

    /**
     * Converts a surface (pixel) coordinate to a world coordinate via the
     * current `Camera2D`. Returns the input unchanged when no current camera
     * exists or its bounds are degenerate (identity fallback) — same semantics
     * as `render` not pushing a transform in that case.
     */
    fun screenToWorld(screenPosition: Vec2): Vec2 =
        currentCamera()?.screenToWorld(screenPosition, size) ?: screenPosition

    /** Inverse of [screenToWorld]; identity fallback under the same conditions. */
    fun worldToScreen(worldPosition: Vec2): Vec2 =
        currentCamera()?.worldToScreen(worldPosition, size) ?: worldPosition

    private fun findCurrentCamera(node: Node): Camera2D? {
        if (node is Camera2D && node.current) return node
        for (child in node.children) {
            val found = findCurrentCamera(child)
            if (found != null) return found
        }
        return null
    }

    private inline fun runTraversal(rendering: Boolean, block: () -> Unit) {
        isMutationDeferred = true
        isRendering = rendering
        try {
            block()
        } finally {
            isRendering = false
            isMutationDeferred = false
        }
    }

    private fun traverseProcess(node: Node, dt: Float) {
        node.onProcess(dt)
        for (child in node.children) traverseProcess(child, dt)
    }

    private fun traversePhysicsProcess(node: Node, dt: Float) {
        node.onPhysicsProcess(dt)
        for (child in node.children) traversePhysicsProcess(child, dt)
    }

    /**
     * World-pass DFS draw. Visits every reachable Node2D/Node, applying
     * per-Node2D `pushTransform`/`popTransform`. CanvasLayer subtrees are
     * skipped entirely — they are drawn during the UI pass.
     */
    private fun traverseWorldDraw(node: Node, renderer: Renderer) {
        if (node is CanvasLayer) return
        if (node is Control && !node.visible) return
        if (node is Node2D) {
            val t = node.transform
            renderer.pushTransform(t.position, t.rotation, t.scale)
            try {
                node.onDraw(renderer)
                for (child in node.children) traverseWorldDraw(child, renderer)
            } finally {
                renderer.popTransform()
            }
        } else {
            node.onDraw(renderer)
            for (child in node.children) traverseWorldDraw(child, renderer)
        }
    }

    /**
     * UI-pass draw of a CanvasLayer's subtree. The renderer enters this call
     * with identity transform (no Camera2D view in effect) — the layer itself
     * does not push, only its Node2D descendants do.
     */
    private fun traverseCanvasLayerSubtree(layer: CanvasLayer, renderer: Renderer) {
        layer.onDraw(renderer)
        for (child in layer.children) traverseUiDraw(child, renderer)
    }

    private fun traverseUiDraw(node: Node, renderer: Renderer) {
        // Nested CanvasLayers are flattened at collection time: each is rendered
        // once in the global (layer, dfs-order) sequence, so we skip them here.
        if (node is CanvasLayer) return
        // An invisible Control hides itself and its whole subtree.
        if (node is Control && !node.visible) return
        if (node is Node2D) {
            val t = node.transform
            renderer.pushTransform(t.position, t.rotation, t.scale)
            try {
                node.onDraw(renderer)
                for (child in node.children) traverseUiDraw(child, renderer)
            } finally {
                renderer.popTransform()
            }
        } else {
            node.onDraw(renderer)
            for (child in node.children) traverseUiDraw(child, renderer)
        }
    }

    /**
     * Collects every reachable CanvasLayer in stable `(layer asc, dfs-order asc)`
     * order. DFS pre-order gives discovery order; the sort by `layer` is stable
     * so DFS order survives as tie-break.
     */
    internal fun collectCanvasLayers(): List<CanvasLayer> {
        val out = mutableListOf<CanvasLayer>()
        collectCanvasLayers(root, out)
        return out.sortedBy { it.layer }
    }

    private fun collectCanvasLayers(node: Node, out: MutableList<CanvasLayer>) {
        if (node is CanvasLayer) out += node
        for (child in node.children) collectCanvasLayers(child, out)
    }
}
