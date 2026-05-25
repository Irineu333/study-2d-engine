package com.neoutils.engine.physics

import kotlinx.serialization.Serializable

/**
 * Solid body with no velocity slot. Position is changed by script (or stays
 * still). Models walls, platforms, and Pong paddles (which are driven by
 * input but never expose a `velocity`).
 */
@Serializable
open class StaticBody2D : PhysicsBody2D()
