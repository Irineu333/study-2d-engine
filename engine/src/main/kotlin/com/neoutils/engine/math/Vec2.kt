package com.neoutils.engine.math

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

@Serializable
data class Vec2(val x: Float, val y: Float) {

    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)
    operator fun unaryMinus(): Vec2 = Vec2(-x, -y)

    val length: Float get() = sqrt(x * x + y * y)

    val normalized: Vec2
        get() {
            val l = length
            return if (l == 0f) ZERO else Vec2(x / l, y / l)
        }

    /**
     * Reflects this vector across the surface described by [normal] (assumed
     * unit length): `v − 2·(v·n)·n`. Bouncing a velocity vector on a contact
     * normal returned by [com.neoutils.engine.physics.CharacterBody2D.moveAndCollide]
     * is the canonical use; pre-normalize the normal if it might not be unit.
     */
    fun reflect(normal: Vec2): Vec2 {
        val dot = x * normal.x + y * normal.y
        return Vec2(x - 2f * dot * normal.x, y - 2f * dot * normal.y)
    }

    companion object {
        val ZERO: Vec2 = Vec2(0f, 0f)
        val ONE: Vec2 = Vec2(1f, 1f)
    }
}
