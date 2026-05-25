package com.neoutils.engine.physics

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Solid body with a [velocity] slot exposed for scripts. The engine does
 * **not** integrate this velocity automatically (Godot-style): a script
 * must do `self.transform.position += self.velocity * dt` in
 * `_physics_process`. The slot's value is just a convention so other systems
 * (AI, animation) can read or set it without inventing private fields.
 */
@Serializable
open class CharacterBody2D : PhysicsBody2D() {

    @Inspect
    var velocity: Vec2 = Vec2.ZERO
}
