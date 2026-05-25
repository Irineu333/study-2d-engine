package com.neoutils.engine.physics

import com.neoutils.engine.math.Vec2

/**
 * Outcome of a successful [CharacterBody2D.moveAndCollide] call: the body
 * stopped at [point] against [collider] with the given surface [normal], and
 * [remainder] is the unused portion of the requested motion that the script
 * can choose to slide along the normal (or simply discard).
 *
 * Coordinates ([point], [normal], [remainder]) live in the frame that
 * [CharacterBody2D.moveAndCollide] operated in — usually the body's parent
 * frame. For a top-level body that frame coincides with world space.
 */
data class KinematicCollision2D(
    val point: Vec2,
    val normal: Vec2,
    val collider: CollisionObject2D,
    val remainder: Vec2,
)
