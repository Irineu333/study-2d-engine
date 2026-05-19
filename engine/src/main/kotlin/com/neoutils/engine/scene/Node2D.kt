package com.neoutils.engine.scene

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

@Serializable
open class Node2D : Node() {

    @Inspect
    var transform: Transform = Transform()

    /**
     * Returns the world-space `Transform` obtained by composing every
     * `Node2D` ancestor's local transform down to `this`. Walks the chain
     * top-down (root first) so parent rotation and scale apply to descendant
     * frames before composition.
     */
    fun worldTransform(): Transform {
        val chain = ArrayDeque<Node2D>()
        var current: Node? = this
        while (current != null) {
            if (current is Node2D) chain.addFirst(current)
            current = current.parent
        }
        var world = Transform()
        for (node in chain) world = world.compose(node.transform)
        return world
    }

    fun worldPosition(): Vec2 = worldTransform().position
}
