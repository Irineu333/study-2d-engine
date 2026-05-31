package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
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

    /**
     * Spatial extent of this node in its **own local frame** — the `Rect` the
     * node actually draws, before its own `transform` is applied. Intrinsic and
     * orientable; no origin convention is imposed (`Panel`/`Button` return
     * `Rect(ZERO, size)`, `Circle2D` returns a centered rect). `null` means
     * "pure transform node (pivot) with no extent", which is real information to
     * a future editor — such a node is not selectable by box.
     *
     * Pure query: it takes no `Renderer` and must not depend on a render pass
     * being active. Shipped visual leaves override it; game subclasses may too.
     * Composing `world().apply(c)` over `corners()` of this rect yields the
     * tight **oriented** box (OBB) a consumer draws as a single-node highlight;
     * the engine deliberately ships no OBB method — see [worldBounds] for the
     * axis-aligned aggregate instead.
     */
    open fun localBounds(): Rect? = null

    /**
     * Axis-aligned bounding box (AABB) of [localBounds] in **world space**:
     * `AABB( world().apply(c) for c in localBounds().corners() )`, accounting for
     * the node's full world rotation and scale. `null` when [localBounds] is
     * `null`. `final` — it falls out of composition and is not meant to be
     * overridden. Use for marquee selection and zoom-to-fit on a single node;
     * for a tight oriented highlight, compose `localBounds` with `world()`.
     */
    fun worldBounds(): Rect? {
        val local = localBounds() ?: return null
        val world = world()
        return aabbOf(local.corners().map { world.apply(it) })
    }

    /**
     * AABB union, in world space, of this node's [worldBounds] and every
     * descendant's [worldBounds] reached by a depth-first walk — **except** the
     * walk does not descend into any `CanvasLayer` subtree, which lives in
     * screen-space and breaks the world transform chain (invariant #6). A node
     * whose own [localBounds] is `null` still contributes its descendants'
     * bounds. `null` when neither this node nor any included descendant has
     * bounds. `final`; inherently axis-aligned (there is no "unioning
     * orientation"), which is exactly what group boxes and zoom-to-fit want.
     */
    fun treeBounds(): Rect? {
        var acc: Rect? = worldBounds()
        for (child in children) {
            acc = unionNullable(acc, treeBoundsOf(child))
        }
        return acc
    }

    private fun treeBoundsOf(node: Node): Rect? {
        if (node is CanvasLayer) return null
        var acc: Rect? = if (node is Node2D) node.worldBounds() else null
        for (child in node.children) {
            acc = unionNullable(acc, treeBoundsOf(child))
        }
        return acc
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

/** Smallest axis-aligned `Rect` enclosing every point in [points]. */
private fun aabbOf(points: List<Vec2>): Rect {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (p in points) {
        if (p.x < minX) minX = p.x
        if (p.y < minY) minY = p.y
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
    }
    return Rect(Vec2(minX, minY), Vec2(maxX - minX, maxY - minY))
}

/** Axis-aligned union of two optional rects; `null` only when both are `null`. */
private fun unionNullable(a: Rect?, b: Rect?): Rect? {
    if (a == null) return b
    if (b == null) return a
    val minX = minOf(a.left, b.left)
    val minY = minOf(a.top, b.top)
    val maxX = maxOf(a.right, b.right)
    val maxY = maxOf(a.bottom, b.bottom)
    return Rect(Vec2(minX, minY), Vec2(maxX - minX, maxY - minY))
}
