package com.neoutils.engine.scene

import com.neoutils.engine.input.Input
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
open class Scene : Node() {

    /** Set by the runtime (`GameLoop`) at the start of each tick. */
    @Transient
    @Volatile var input: Input? = null
        internal set

    /**
     * Surface size in pixels, kept current by the host via [resize]. The
     * canonical world extent when no `Camera2D` is active; see [viewport].
     */
    @Transient
    var size: Vec2 = Vec2.ZERO
        private set

    val width: Float get() = size.x
    val height: Float get() = size.y

    /**
     * Visible world rect. Resolves to the active `Camera2D.bounds` when a
     * camera is current, or to `Rect(Vec2.ZERO, size)` otherwise. Computed
     * on demand: there is no cached "active camera" state in this change.
     */
    val viewport: Rect
        get() = currentCamera()?.bounds ?: Rect(Vec2.ZERO, size)

    /**
     * `true` while the scene is inside an `onProcess`, `onPhysicsProcess`,
     * `onCollide` or `onDraw` traversal (or another physics phase). Read by
     * `Node.addChild` / `Node.removeChild` to decide between immediate
     * mutation and enqueuing onto the pending queues.
     */
    @Transient
    internal var isMutationDeferred: Boolean = false
        private set

    /**
     * `true` only during render traversal. `addChild`/`removeChild` called
     * while this is set are logged and dropped (decision D5 in design.md):
     * scene-graph mutation during render has no use case and would cost more
     * complexity than it saves to support.
     */
    @Transient
    internal var isRendering: Boolean = false
        private set

    /** Called by the runtime when the rendering surface size changes. */
    fun resize(width: Float, height: Float) {
        if (width == size.x && height == size.y) return
        size = Vec2(width, height)
        onResize(width, height)
    }

    open fun onResize(width: Float, height: Float) {}

    fun start() {
        if (!isLive) attachToLiveTree(this)
    }

    fun stop() {
        if (isLive) detachFromLiveTree()
    }

    fun process(dt: Float) {
        if (!isLive) return
        runTraversal(rendering = false) { traverseProcess(this, dt) }
    }

    fun physicsProcess(dt: Float) {
        if (!isLive) return
        runTraversal(rendering = false) { traversePhysicsProcess(this, dt) }
    }

    fun render(renderer: Renderer) {
        if (!isLive) return
        val view = currentCamera()?.computeViewTransform(size)
        if (view != null) {
            renderer.pushTransform(view.first, view.second)
            try {
                runTraversal(rendering = true) { traverseDraw(this, renderer) }
            } finally {
                renderer.popTransform()
            }
        } else {
            runTraversal(rendering = true) { traverseDraw(this, renderer) }
        }
    }

    /**
     * Drains pending child mutations enqueued during the previous traversal.
     * Drained in post-order (children first), removals before additions, so
     * lifecycle is coherent across the whole subtree before the next phase
     * begins.
     */
    fun applyPending() {
        drainPending()
    }

    /**
     * Returns every live `Node` reachable from this scene whose [Node.groups]
     * contains [name]. Pre-order walk; O(N) in the scene size. Mutable groups
     * are tracked per-node, so the result reflects the state at the moment
     * of the call.
     */
    fun getNodesInGroup(name: String): List<Node> {
        val out = mutableListOf<Node>()
        collectInGroup(this, name, out)
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

    internal fun currentCamera(): Camera2D? {
        return findCurrentCamera(this)
    }

    /**
     * Converts a surface (pixel) coordinate to a world coordinate via the
     * scene's current `Camera2D`. Returns the input unchanged when no current
     * camera exists or its bounds are degenerate (identity fallback) — same
     * semantics as `Scene.render` not pushing a transform in that case.
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
