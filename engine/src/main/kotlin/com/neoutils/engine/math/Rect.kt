package com.neoutils.engine.math

import kotlinx.serialization.Serializable

@Serializable
data class Rect(val origin: Vec2, val size: Vec2) {

    val left: Float get() = origin.x
    val top: Float get() = origin.y
    val right: Float get() = origin.x + size.x
    val bottom: Float get() = origin.y + size.y

    fun intersects(other: Rect): Boolean =
        left < other.right && right > other.left &&
            top < other.bottom && bottom > other.top

    fun contains(point: Vec2): Boolean =
        point.x >= left && point.x < right &&
            point.y >= top && point.y < bottom

    /**
     * Four corners in stable order — top-left, top-right, bottom-right,
     * bottom-left — forming a closed loop. Feeding each through
     * [Transform.apply] is how [com.neoutils.engine.scene.Node2D.worldBounds]
     * and oriented-box consumers project a local rect into world space.
     */
    fun corners(): List<Vec2> = listOf(
        Vec2(left, top),
        Vec2(right, top),
        Vec2(right, bottom),
        Vec2(left, bottom),
    )
}
