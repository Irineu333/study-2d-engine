package com.neoutils.engine.scene

import com.neoutils.engine.render.Renderer

open class Scene : Node() {

    fun start() {
        if (!isLive) attachToLiveTree()
    }

    fun stop() {
        if (isLive) detachFromLiveTree()
    }

    fun update(dt: Float) {
        if (!isLive) return
        traverseUpdate(this, dt)
    }

    fun render(renderer: Renderer) {
        if (!isLive) return
        traverseRender(this, renderer)
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
