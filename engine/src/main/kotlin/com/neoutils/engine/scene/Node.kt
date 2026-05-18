package com.neoutils.engine.scene

import com.neoutils.engine.render.Renderer

abstract class Node {

    var name: String = this::class.simpleName ?: "Node"

    var parent: Node? = null
        private set

    private val _children: MutableList<Node> = mutableListOf()
    val children: List<Node> get() = _children

    var isLive: Boolean = false
        private set

    fun addChild(child: Node) {
        require(child.parent == null) { "Node '${child.name}' already has a parent" }
        require(child !== this) { "Cannot add a node as its own child" }
        child.parent = this
        _children.add(child)
        if (isLive) child.attachToLiveTree()
    }

    fun removeChild(child: Node) {
        require(child.parent === this) { "Node '${child.name}' is not a child of '$name'" }
        if (isLive) child.detachFromLiveTree()
        _children.remove(child)
        child.parent = null
    }

    internal fun attachToLiveTree() {
        if (isLive) return
        isLive = true
        onEnter()
        for (child in _children) child.attachToLiveTree()
    }

    internal fun detachFromLiveTree() {
        if (!isLive) return
        for (child in _children) child.detachFromLiveTree()
        onExit()
        isLive = false
    }

    /** Walks up the parent chain to locate the owning Scene, if any. */
    fun rootScene(): Scene? {
        var n: Node? = this
        while (n != null) {
            if (n is Scene) return n
            n = n.parent
        }
        return null
    }

    open fun onEnter() {}
    open fun onUpdate(dt: Float) {}
    open fun onRender(renderer: Renderer) {}
    open fun onExit() {}
}
