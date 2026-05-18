package com.neoutils.engine.scene

import com.neoutils.engine.input.Input
import com.neoutils.engine.render.Renderer

open class Scene : Node() {

    /** Set by the runtime (`GameLoop`) at the start of each tick. */
    @Volatile var input: Input? = null
        internal set

    var width: Float = 0f
        private set
    var height: Float = 0f
        private set

    /**
     * `true` while the scene is inside an `onUpdate`, `onCollide` or
     * `onRender` traversal (or another physics phase). Read by `Node.addChild`
     * / `Node.removeChild` to decide between immediate mutation and enqueuing
     * onto the pending queues.
     */
    internal var isMutationDeferred: Boolean = false
        private set

    /**
     * `true` only during render traversal. `addChild`/`removeChild` called
     * while this is set are logged and dropped (decision D5 in design.md):
     * scene-graph mutation during render has no use case and would cost more
     * complexity than it saves to support.
     */
    internal var isRendering: Boolean = false
        private set

    /** Called by the runtime when the rendering surface size changes. */
    fun resize(width: Float, height: Float) {
        if (width == this.width && height == this.height) return
        this.width = width
        this.height = height
        onResize(width, height)
    }

    open fun onResize(width: Float, height: Float) {}

    fun start() {
        if (!isLive) attachToLiveTree(this)
    }

    fun stop() {
        if (isLive) detachFromLiveTree()
    }

    fun update(dt: Float) {
        if (!isLive) return
        runTraversal(rendering = false) { traverseUpdate(this, dt) }
    }

    fun render(renderer: Renderer) {
        if (!isLive) return
        runTraversal(rendering = true) { traverseRender(this, renderer) }
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

    internal fun beginPhysicsPhase() {
        isMutationDeferred = true
    }

    internal fun endPhysicsPhase() {
        isMutationDeferred = false
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

    private fun traverseUpdate(node: Node, dt: Float) {
        node.onUpdate(dt)
        for (child in node.children) traverseUpdate(child, dt)
    }

    private fun traverseRender(node: Node, renderer: Renderer) {
        node.onRender(renderer)
        for (child in node.children) traverseRender(child, renderer)
    }
}
