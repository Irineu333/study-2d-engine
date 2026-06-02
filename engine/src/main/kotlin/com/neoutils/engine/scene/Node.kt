package com.neoutils.engine.scene

import com.neoutils.engine.dx.Log
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import com.neoutils.engine.tree.SceneTree
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
open class Node {

    @Inspect
    var name: String = this::class.simpleName ?: "Node"

    @Transient
    var parent: Node? = null
        private set

    @Transient
    private val _children: MutableList<Node> = mutableListOf()
    val children: List<Node> get() = _children

    /**
     * Owning `SceneTree` while this node is live. Populated by
     * `attachToLiveTree` before `onEnter` runs and cleared by
     * `detachFromLiveTree` after `onExit` returns. Lets gameplay code reach
     * tree-wide state (`tree?.width`, `tree?.input`, ...) in O(1) instead of
     * walking the parent chain every frame.
     */
    @Transient
    var tree: SceneTree? = null
        internal set

    /** `true` while attached to a live `SceneTree`; derived from [tree]. */
    val isLive: Boolean get() = tree != null

    /**
     * Monotonic counter bumped each time the node re-enters the live tree.
     * Read by `NodeRef` to detect that its cached resolution may be stale
     * (path-relative resolution can land elsewhere after a re-attach).
     */
    @Transient
    internal var attachGeneration: Long = 0L
        private set

    @Transient
    internal var scriptInstance: ScriptInstanceContract? = null

    /**
     * Bundle-relative path of the script attached to this node, mirroring
     * `NodeEntry.script`. Set by `SceneLoader` on load so `save` can round-trip
     * the field without consulting a script registry.
     */
    @Transient
    internal var scriptPath: String? = null

    @Transient
    private val _groups: MutableSet<String> = mutableSetOf()
    val groups: Set<String> get() = _groups

    @Transient
    private val pendingAdd: MutableList<Node> = mutableListOf()

    @Transient
    private val pendingRemove: MutableList<Node> = mutableListOf()

    @Transient
    private val pendingRaise: MutableList<Node> = mutableListOf()

    fun addChild(child: Node) {
        require(child.parent == null) { "Node '${child.name}' already has a parent" }
        require(child !== this) { "Cannot add a node as its own child" }
        val owningTree = tree
        if (owningTree != null && owningTree.isMutationDeferred) {
            if (owningTree.isRendering) {
                Log.w(TAG, "addChild called during onDraw; ignored ('${child.name}' -> '$name')")
                return
            }
            pendingAdd += child
            return
        }
        applyAdd(child)
    }

    fun removeChild(child: Node) {
        require(child.parent === this) { "Node '${child.name}' is not a child of '$name'" }
        val owningTree = tree
        if (owningTree != null && owningTree.isMutationDeferred) {
            if (owningTree.isRendering) {
                Log.w(TAG, "removeChild called during onDraw; ignored ('${child.name}' from '$name')")
                return
            }
            pendingRemove += child
            return
        }
        applyRemove(child)
    }

    /**
     * Moves an existing direct [child] to the end of the children list — the top
     * of the paint and DFS order among its siblings. No-op when [child] is not a
     * direct child. Does not change `child.parent` nor fire lifecycle hooks
     * (`onEnter`/`onExit`); the relative order of the other children is preserved.
     *
     * Governed by the same mutation-during-traversal contract as
     * [addChild]/[removeChild]: applied immediately outside traversal, deferred to
     * the next drain when called while a `SceneTree` traversal is in progress, and
     * logged-and-dropped when called during `onDraw`.
     */
    fun raiseChildToTop(child: Node) {
        if (child.parent !== this) return
        val owningTree = tree
        if (owningTree != null && owningTree.isMutationDeferred) {
            if (owningTree.isRendering) {
                Log.w(TAG, "raiseChildToTop called during onDraw; ignored ('${child.name}' in '$name')")
                return
            }
            pendingRaise += child
            return
        }
        applyRaise(child)
    }

    private fun applyRaise(child: Node) {
        if (child.parent !== this) return
        if (_children.lastOrNull() === child) return
        _children.remove(child)
        _children.add(child)
    }

    private fun applyAdd(child: Node) {
        child.name = uniqueChildName(child.name)
        child.parent = this
        _children.add(child)
        (child as? Node2D)?.invalidateWorldTransformRecursive()
        val owningTree = tree
        if (owningTree != null) child.attachToLiveTree(owningTree)
    }

    private fun uniqueChildName(desired: String): String {
        if (_children.none { it.name == desired }) return desired
        var i = 2
        while (_children.any { it.name == "${desired}_$i" }) i++
        return "${desired}_$i"
    }

    private fun applyRemove(child: Node) {
        if (child.isLive) child.detachFromLiveTree()
        _children.remove(child)
        child.parent = null
        (child as? Node2D)?.invalidateWorldTransformRecursive()
    }

    internal fun attachToLiveTree(owningTree: SceneTree) {
        if (isLive) return
        tree = owningTree
        attachGeneration++
        onEnter()
        for (child in _children) child.attachToLiveTree(owningTree)
    }

    internal fun detachFromLiveTree() {
        if (!isLive) return
        for (child in _children) child.detachFromLiveTree()
        onExit()
        tree = null
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
        // Raises last so a reorder lands on top of nodes added in the same drain.
        if (pendingRaise.isNotEmpty()) {
            val drained = pendingRaise.toList()
            pendingRaise.clear()
            for (child in drained) {
                if (child.parent === this) applyRaise(child)
            }
        }
    }

    /**
     * Single-level lookup of a direct child by `name`. Returns `null` when
     * absent. Used by `NodeRef` to walk down a relative path.
     */
    fun findChild(name: String): Node? = _children.firstOrNull { it.name == name }

    fun addToGroup(name: String) {
        _groups.add(name)
    }

    fun removeFromGroup(name: String) {
        _groups.remove(name)
    }

    fun isInGroup(name: String): Boolean = name in _groups

    open fun onEnter() {
        scriptInstance?.onEnter()
    }

    open fun onProcess(dt: Float) {
        scriptInstance?.onProcess(dt)
    }

    open fun onPhysicsProcess(dt: Float) {
        scriptInstance?.onPhysicsProcess(dt)
    }

    open fun onDraw(renderer: Renderer) {
        scriptInstance?.onDraw(renderer)
    }

    open fun onExit() {
        scriptInstance?.onExit()
    }

    companion object {
        private const val TAG = "Node"
    }
}
