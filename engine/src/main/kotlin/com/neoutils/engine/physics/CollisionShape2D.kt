package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Node child of a [CollisionObject2D] that carries a polymorphic [Shape2D]
 * resource. Inactive when [shape] is `null` or [disabled] is `true`; in that
 * case [worldBounds] returns `null` and [PhysicsSystem] ignores it.
 *
 * Only meaningful as a direct child of a [CollisionObject2D]; placing it
 * elsewhere does not crash but is simply not enumerated by the physics step.
 */
@Serializable
open class CollisionShape2D : Node2D() {

    @Inspect
    var shape: Shape2D? = null

    @Inspect
    var disabled: Boolean = false

    fun worldBounds(): Rect? {
        if (disabled) return null
        val s = shape ?: return null
        return s.bounds(world(), Vec2.ZERO)
    }
}
