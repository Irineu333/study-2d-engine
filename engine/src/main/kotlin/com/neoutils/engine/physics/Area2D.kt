package com.neoutils.engine.physics

import kotlinx.serialization.Serializable

/**
 * Trigger-only collision object: receives enter/exit events but does **not**
 * block other bodies. Use for goals, pickups, hitboxes, sensors.
 *
 * Provides Godot-style persistent overlap queries ([getOverlappingAreas],
 * [getOverlappingBodies]) that return the *current* overlap set after the
 * last [PhysicsSystem.step] dispatch. Read inside `_process` /
 * `_physics_process` / `_on_*_entered`: the answer is consistent with the
 * events the script just received.
 */
@Serializable
open class Area2D : CollisionObject2D() {

    /**
     * Every [Area2D] currently overlapping this one (post-dispatch snapshot).
     * Returns `emptyList()` when detached or when the tree has no active
     * [PhysicsSystem] (unit tests that drive the tree directly).
     */
    fun getOverlappingAreas(): List<Area2D> =
        peers().filterIsInstance<Area2D>()

    /**
     * Every [PhysicsBody2D] currently overlapping this area (post-dispatch).
     */
    fun getOverlappingBodies(): List<PhysicsBody2D> =
        peers().filterIsInstance<PhysicsBody2D>()

    private fun peers(): List<CollisionObject2D> {
        val system = tree?.physicsSystem ?: return emptyList()
        return system.overlappingPeersOf(this)
    }
}
