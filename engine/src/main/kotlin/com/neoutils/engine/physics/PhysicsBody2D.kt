package com.neoutils.engine.physics

import kotlinx.serialization.Serializable

/**
 * Base of solid collision bodies — anything that conceptually blocks other
 * bodies. Concrete subtypes: [StaticBody2D] (no velocity slot, moved by
 * script) and [CharacterBody2D] (velocity slot, still moved by script).
 *
 * The engine itself does not implement collision **response** for bodies in
 * this change — only detection. Resolving contacts (slide, stop, bounce) is
 * the script's job. A future change introduces `move_and_slide()` once a
 * game needs it.
 */
@Serializable
abstract class PhysicsBody2D : CollisionObject2D()
