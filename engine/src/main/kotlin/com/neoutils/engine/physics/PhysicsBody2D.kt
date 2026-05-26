package com.neoutils.engine.physics

import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Base of solid collision bodies — anything that conceptually blocks other
 * bodies. Concrete subtypes: [StaticBody2D] (no velocity slot, moved by
 * script), [CharacterBody2D] (velocity slot, still moved by script), and
 * [RigidBody2D] (engine-integrated dynamic body).
 *
 * `friction` and `restitution` live here so that the [RigidBody2D] impulse
 * solver can read them off any other [PhysicsBody2D] it collides with via
 * the same property — a `StaticBody2D` wall with `friction = 0.4f` will
 * combine with a rigid square's `friction = 1f` to produce a non-zero
 * Coulomb cap on the tangential impulse. Defaults: `friction = 1f` (full
 * tangential coupling), `restitution = 0f` (inelastic, Godot canon).
 */
@Serializable
abstract class PhysicsBody2D : CollisionObject2D() {

    @Inspect var friction: Float = 1f
    @Inspect var restitution: Float = 0f
}
