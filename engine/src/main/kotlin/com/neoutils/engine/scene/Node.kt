package com.neoutils.engine.scene

import com.neoutils.engine.dx.Log
import com.neoutils.engine.render.Renderer

abstract class Node {

    var name: String = this::class.simpleName ?: "Node"

    var parent: Node? = null
        private set

    private val _children: MutableList<Node> = mutableListOf()
    val children: List<Node> get() = _children

    var isLive: Boolean = false
        private set

    /**
     * Cached owning `Scene` while this node is live. Populated by
     * `attachToLiveTree` before `onEnter` runs and cleared by
     * `detachFromLiveTree` after `onExit` returns. Lets `rootScene()` run in
     * O(1) instead of walking the parent chain every frame.
     */
    var scene: Scene? = null
        internal set

    /**
     * Monotonic counter bumped each time the node re-enters the live tree.
     * Read by `NodeRef` to detect that its cached resolution may be stale
     * (path-relative resolution can land elsewhere after a re-attach).
     */
    internal var attachGeneration: Long = 0L
        private set

    private val pendingAdd: MutableList<Node> = mutableListOf()
    private val pendingRemove: MutableList<Node> = mutableListOf()

    fun addChild(child: Node) {
        require(child.parent == null) { "Node '${child.name}' already has a parent" }
        require(child !== this) { "Cannot add a node as its own child" }
        val owning = if (this is Scene) this else scene
        if (owning != null && owning.isMutationDeferred) {
            if (owning.isRendering) {
                Log.w(TAG, "addChild called during onRender; ignored ('${child.name}' -> '$name')")
                return
            }
            pendingAdd += child
            return
        }
        applyAdd(child)
    }

    fun removeChild(child: Node) {
        require(child.parent === this) { "Node '${child.name}' is not a child of '$name'" }
        val owning = if (this is Scene) this else scene
        if (owning != null && owning.isMutationDeferred) {
            if (owning.isRendering) {
                Log.w(TAG, "removeChild called during onRender; ignored ('${child.name}' from '$name')")
                return
            }
            pendingRemove += child
            return
        }
        applyRemove(child)
    }

    private fun applyAdd(child: Node) {
        child.name = uniqueChildName(child.name)
        child.parent = this
        _children.add(child)
        if (isLive) {
            val owning = if (this is Scene) this else scene
            if (owning != null) child.attachToLiveTree(owning)
        }
    }

    private fun uniqueChildName(desired: String): String {
        if (_children.none { it.name == desired }) return desired
        var i = 2
        while (_children.any { it.name == "${desired}_$i" }) i++
        return "${desired}_$i"
    }

    private fun applyRemove(child: Node) {
        if (isLive) child.detachFromLiveTree()
        _children.remove(child)
        child.parent = null
    }

    internal fun attachToLiveTree(owningScene: Scene) {
        if (isLive) return
        scene = owningScene
        attachGeneration++
        isLive = true
        onEnter()
        for (child in _children) child.attachToLiveTree(owningScene)
    }

    internal fun detachFromLiveTree() {
        if (!isLive) return
        for (child in _children) child.detachFromLiveTree()
        onExit()
        isLive = false
        scene = null
    }

    /**
     * Drains the per-node pending queues in post-order (children first),
     * applying removals before additions to prevent reattaching a node that
     * was just scheduled for removal in the same drain.
     */
    internal fun drainPending() {
        for (child in _children.toList()) child.drainPending()
        if (pendingRemove.isNotEmpty()) {
            val drained = pendingRemove.toList()
            pendingRemove.clear()
            for (child in drained) {
                if (child.parent === this) applyRemove(child)
            }
        }
        if (pendingAdd.isNotEmpty()) {
            val drained = pendingAdd.toList()
            pendingAdd.clear()
            for (child in drained) {
                if (child.parent == null) applyAdd(child)
            }
        }
    }

    /** Returns the owning `Scene` in O(1) when live, or `null` otherwise. */
    fun rootScene(): Scene? = scene

    /**
     * Single-level lookup of a direct child by `name`. Returns `null` when
     * absent. Used by `NodeRef` to walk down a relative path.
     */
    fun findChild(name: String): Node? = _children.firstOrNull { it.name == name }

    open fun onEnter() {}
    open fun onUpdate(dt: Float) {}
    open fun onRender(renderer: Renderer) {}
    open fun onExit() {}

    companion object {
        private const val TAG = "Scene"
    }
}
