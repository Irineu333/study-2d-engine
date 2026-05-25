package com.neoutils.engine.physics

import kotlinx.serialization.Serializable

/**
 * Trigger-only collision object: receives enter/exit events but does **not**
 * block other bodies. Use for goals, pickups, hitboxes, sensors.
 */
@Serializable
open class Area2D : CollisionObject2D()
