package com.neoutils.engine.math

import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

@Serializable
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

    /**
     * Maps a point [p] expressed in this transform's local frame to the parent
     * frame: `position + rotate(scale ⊙ p, rotation)`. Composing
     * `world().apply(c)` over the corners of a node's `localBounds()` yields the
     * node's oriented (rotated) world-space box; [com.neoutils.engine.scene.Node2D.worldBounds]
     * takes the AABB of those four points.
     */
    fun apply(p: Vec2): Vec2 {
        val scaled = Vec2(scale.x * p.x, scale.y * p.y)
        return position + rotate(scaled, rotation)
    }

    /**
     * Exact inverse of [apply]: maps a point [p] expressed in the parent frame
     * back into this transform's local frame, `rotate(p - position, -rotation)`
     * divided component-wise by `scale`. Brings a world-space point into a
     * node's local frame so an oriented hit-test can compare it against the
     * node's `localBounds()` (precise under rotation, unlike the loose
     * world-space AABB). Undefined when a scale component is zero (degenerate
     * transform with no inverse).
     */
    fun applyInverse(p: Vec2): Vec2 {
        val unrotated = rotate(p - position, -rotation)
        return Vec2(unrotated.x / scale.x, unrotated.y / scale.y)
    }
}

internal fun rotate(v: Vec2, radians: Float): Vec2 {
    if (radians == 0f) return v
    val c = cos(radians)
    val s = sin(radians)
    return Vec2(v.x * c - v.y * s, v.x * s + v.y * c)
}
