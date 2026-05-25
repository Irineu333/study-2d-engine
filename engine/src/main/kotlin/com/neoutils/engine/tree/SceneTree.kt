package com.neoutils.engine.tree

import com.neoutils.engine.input.Input
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.Node

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
        onResize?.invoke(width, height)
    }

    fun start() {
        if (!root.isLive) root.attachToLiveTree(this)
    }

    fun stop() {
        if (root.isLive) root.detachFromLiveTree()
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
        val view = currentCamera()?.computeViewTransform(size)
        if (view != null) {
            renderer.pushTransform(view.first, view.second)
            try {
                runTraversal(rendering = true) { traverseDraw(root, renderer) }
            } finally {
                renderer.popTransform()
            }
        } else {
            runTraversal(rendering = true) { traverseDraw(root, renderer) }
        }
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

    private fun traverseDraw(node: Node, renderer: Renderer) {
        node.onDraw(renderer)
        for (child in node.children) traverseDraw(child, renderer)
    }
}
