package com.neoutils.engine.scene

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
open class Node2D : Node() {

    /**
     * Local transform. Assigning a new value invalidates the world-transform
     * cache on this node and all Node2D descendants.
     */
    @Inspect
    var transform: Transform = Transform()
        set(value) {
            field = value
            invalidateWorldTransformRecursive()
        }

    @Transient
    private var cachedWorldTransform: Transform? = null

    /**
     * Returns the world-space `Transform` by composing every `Node2D`
     * ancestor's local transform down to `this`. The result is cached per
     * node; the cache is invalidated on local `transform` assignment,
     * reparenting, or any ancestor's `transform` assignment. Cache is
     * runtime-only and never serialized.
     */
    fun worldTransform(): Transform {
        cachedWorldTransform?.let { return it }
        val ancestor = nearestNode2DAncestor()
        val world = ancestor?.worldTransform()?.compose(transform) ?: transform
        cachedWorldTransform = world
        return world
    }

    fun worldPosition(): Vec2 = worldTransform().position

    private fun nearestNode2DAncestor(): Node2D? {
        var c = parent
        while (c != null) {
            if (c is Node2D) return c
            c = c.parent
        }
        return null
    }

    internal fun invalidateWorldTransformRecursive() {
        cachedWorldTransform = null
        invalidateDescendants(this)
    }

    private fun invalidateDescendants(node: Node) {
        for (child in node.children) {
            if (child is Node2D) {
                child.cachedWorldTransform = null
                invalidateDescendants(child)
            } else {
                invalidateDescendants(child)
            }
        }
    }
}
