package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.Inspect
import com.neoutils.engine.serialization.Signal
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Base class for nodes that participate in collision: [Area2D] (trigger) and
 * [PhysicsBody2D] (solid). Owns the four lifecycle hooks dispatched by
 * [PhysicsSystem] on enter/exit, mirrored by four built-in [Signal]s that
 * external observers can subscribe to without subclassing.
 *
 * Collision shapes live in **direct children** of this node, each as a
 * [CollisionShape2D] holding a polymorphic [Shape2D]. Nested
 * `CollisionShape2D` deeper in the hierarchy is intentionally not
 * enumerated by [collectActiveShapes].
 */
@Serializable
abstract class CollisionObject2D : Node2D() {

    @Inspect
    var disabled: Boolean = false

    @Transient
    val areaEntered: Signal<Area2D> = Signal()

    @Transient
    val areaExited: Signal<Area2D> = Signal()

    @Transient
    val bodyEntered: Signal<PhysicsBody2D> = Signal()

    @Transient
    val bodyExited: Signal<PhysicsBody2D> = Signal()

    open fun onAreaEntered(area: Area2D) {
        scriptInstance?.onAreaEntered(area)
    }

    open fun onAreaExited(area: Area2D) {
        scriptInstance?.onAreaExited(area)
    }

    open fun onBodyEntered(body: PhysicsBody2D) {
        scriptInstance?.onBodyEntered(body)
    }

    open fun onBodyExited(body: PhysicsBody2D) {
        scriptInstance?.onBodyExited(body)
    }

    /**
     * Returns the `(CollisionShape2D, worldBounds)` pairs of every direct
     * child shape that is currently active (not disabled, has a non-null
     * shape). The owning object's [disabled] flag is **not** consulted here;
     * the caller ([PhysicsSystem]) already filters disabled objects.
     */
    fun collectActiveShapes(): List<Pair<CollisionShape2D, Rect>> {
        val out = mutableListOf<Pair<CollisionShape2D, Rect>>()
        for (child in children) {
            if (child !is CollisionShape2D) continue
            val bounds = child.broadPhaseBounds() ?: continue
            out += child to bounds
        }
        return out
    }
}
