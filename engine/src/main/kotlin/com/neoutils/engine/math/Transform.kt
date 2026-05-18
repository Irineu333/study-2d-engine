package com.neoutils.engine.math

import kotlin.math.cos
import kotlin.math.sin

data class Transform(
    val position: Vec2 = Vec2.ZERO,
    val scale: Vec2 = Vec2.ONE,
    val rotation: Float = 0f,
) {

    /**
     * Returns the world transform of a child whose local coordinates are
     * expressed in this transform's frame. Scales component-wise, sums
     * rotations, and rotates+scales the child's local position before adding
     * it to this transform's position.
     */
    fun compose(child: Transform): Transform {
        val scaledChildPos = Vec2(scale.x * child.position.x, scale.y * child.position.y)
        val rotated = rotate(scaledChildPos, rotation)
        return Transform(
            position = position + rotated,
            scale = Vec2(scale.x * child.scale.x, scale.y * child.scale.y),
            rotation = rotation + child.rotation,
        )
    }
}

internal fun rotate(v: Vec2, radians: Float): Vec2 {
    if (radians == 0f) return v
    val c = cos(radians)
    val s = sin(radians)
    return Vec2(v.x * c - v.y * s, v.x * s + v.y * c)
}
