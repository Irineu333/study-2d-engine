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
     * cache on this node and all Node2D descendants. `open` so subclasses
     * (e.g. `RigidBody2D`) can intercept writes for diagnostics; overrides
     * SHOULD chain to `super.transform = value` to preserve cache invalidation.
     */
    @Inspect
    open var transform: Transform = Transform()
        set(value) {
            field = value
            invalidateWorldTransformRecursive()
        }

    /**
     * Sugar over `transform.position` / `transform = transform.copy(position = ...)`.
     * Writes go through the `transform` setter, so the world-transform cache
     * is invalidated automatically.
     */
    var position: Vec2
        get() = transform.position
        set(value) { transform = transform.copy(position = value) }

    /**
     * Sugar over `transform.rotation` (radians) / `transform = transform.copy(rotation = ...)`.
     * Writes go through the `transform` setter, so the world-transform cache
     * is invalidated automatically.
     */
    var rotation: Float
        get() = transform.rotation
        set(value) { transform = transform.copy(rotation = value) }

    /**
     * Sugar over `transform.scale` / `transform = transform.copy(scale = ...)`.
     * Writes go through the `transform` setter, so the world-transform cache
     * is invalidated automatically.
     */
    var scale: Vec2
        get() = transform.scale
        set(value) { transform = transform.copy(scale = value) }

    @Transient
    private var cachedWorld: Transform? = null

    /**
     * Returns the world-space `Transform` by composing every `Node2D`
     * ancestor's local transform down to `this`. The result is cached per
     * node; the cache is invalidated on local `transform` assignment,
     * reparenting, or any ancestor's `transform` assignment. Cache is
     * runtime-only and never serialized.
     *
     * Function (not property) to signal that the value is computed/cached
     * rather than a bare field read.
     */
    fun world(): Transform {
        cachedWorld?.let { return it }
        val ancestor = nearestNode2DAncestor()
        val world = ancestor?.world()?.compose(transform) ?: transform
        cachedWorld = world
        return world
    }

    private fun nearestNode2DAncestor(): Node2D? {
        var c = parent
        while (c != null) {
            if (c is Node2D) return c
            c = c.parent
        }
        return null
    }

    internal fun invalidateWorldTransformRecursive() {
        cachedWorld = null
        invalidateDescendants(this)
    }

    private fun invalidateDescendants(node: Node) {
        for (child in node.children) {
            if (child is Node2D) {
                child.cachedWorld = null
                invalidateDescendants(child)
            } else {
                invalidateDescendants(child)
            }
        }
    }
}
