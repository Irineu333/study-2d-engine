package com.neoutils.engine.physics

import com.neoutils.engine.dx.Log
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Dynamic body whose `position`, `linearVelocity`, and `angularVelocity` are
 * integrated by [PhysicsSystem.step] each physics tick — not by scripts.
 * Scripts influence motion through accumulators ([applyForce], [applyImpulse],
 * [applyForceAt], [applyImpulseAt], [applyTorque]) and through direct read/
 * write of [linearVelocity] / [angularVelocity].
 *
 * Sits alongside [StaticBody2D] (immovable, script-moved) and
 * [CharacterBody2D] (script-moved with helper sweep) as the third path. The
 * solver treats `StaticBody2D` and `CharacterBody2D` as infinite-mass kinematic
 * obstacles from a `RigidBody2D`'s perspective: collision with them imparts
 * impulse to the `RigidBody2D`, but the obstacle does not recoil. To "kick"
 * a `RigidBody2D` from a `CharacterBody2D` paddle, the script can call
 * [applyImpulse] on the rigid manually inside its `_on_body_entered` handler.
 *
 * Example (elastic bounce):
 *
 * ```kotlin
 * val ball = RigidBody2D().apply {
 *     mass = 1f
 *     restitution = 1f
 *     friction = 0f
 *     linearVelocity = Vec2(200f, 0f)
 * }
 * ```
 *
 * See `openspec/changes/add-rigid-body-2d/design.md` for the integrator,
 * impulse equation, and combine rules.
 */
@Serializable
open class RigidBody2D : PhysicsBody2D() {

    @Inspect var mass: Float = 1f

    /**
     * `0f` is a sentinel — the system derives the moment of inertia from
     * attached [CollisionShape2D] children on each read of [effectiveInertia].
     * Setting a non-zero value overrides the auto-derived inertia verbatim.
     */
    @Inspect var inertia: Float = 0f

    // friction and restitution are inherited from PhysicsBody2D
    // (default friction=1f, restitution=0f). Override per body as needed.

    @Inspect var gravityScale: Float = 1f
    @Inspect var linearDamping: Float = 0f
    @Inspect var angularDamping: Float = 0f

    @Transient var linearVelocity: Vec2 = Vec2.ZERO
    @Transient var angularVelocity: Float = 0f

    @Transient internal var appliedForce: Vec2 = Vec2.ZERO
    @Transient internal var appliedTorque: Float = 0f

    @Transient internal var warnedAboutTeleport: Boolean = false

    /**
     * Set to `true` by [PhysicsSystem] around its own position/rotation writes
     * during the integrator + solver, so the teleport warning only fires for
     * **external** (script) mutations. Never touched by user code.
     */
    @Transient internal var inEngineWrite: Boolean = false

    override var transform: Transform
        get() = super.transform
        set(value) {
            if (isLive && !inEngineWrite && !warnedAboutTeleport) {
                Log.w(TAG, "RigidBody2D '$name' was teleported via direct transform write — physics resolution is bypassed for this mutation")
                warnedAboutTeleport = true
            }
            super.transform = value
        }

    /**
     * Returns [inertia] verbatim when non-zero; otherwise derives from the
     * body's active [CollisionShape2D] children (parallel-axis sum). A body
     * with no shape children and `inertia == 0f` returns `1f` (solver divides
     * by inertia; sentinel avoids NaN).
     */
    val effectiveInertia: Float
        get() {
            if (inertia != 0f) return inertia
            var total = 0f
            for (child in children) {
                if (child !is CollisionShape2D) continue
                if (child.disabled) continue
                val shape = child.shape ?: continue
                val localInertia = when (shape) {
                    is CircleShape2D -> mass * shape.radius * shape.radius / 2f
                    is RectangleShape2D -> mass * (shape.size.x * shape.size.x + shape.size.y * shape.size.y) / 12f
                }
                val offset = child.transform.position
                val offsetSq = offset.x * offset.x + offset.y * offset.y
                total += localInertia + mass * offsetSq
            }
            return if (total == 0f) 1f else total
        }

    /** Accumulates a continuous force consumed across the next integration `dt`. */
    fun applyForce(force: Vec2) {
        appliedForce = appliedForce + force
    }

    /** Instantaneous change to [linearVelocity]: `linearVelocity += impulse / mass`. */
    fun applyImpulse(impulse: Vec2) {
        linearVelocity = linearVelocity + impulse * (1f / mass)
    }

    /**
     * Continuous force plus its torque about the body center.
     * `worldPoint` is the world-space point of application.
     */
    fun applyForceAt(force: Vec2, worldPoint: Vec2) {
        appliedForce = appliedForce + force
        val r = worldPoint - world().position
        appliedTorque += r.x * force.y - r.y * force.x
    }

    /**
     * Instantaneous linear + angular impulse about the body center.
     * `worldPoint` is the world-space point of application.
     */
    fun applyImpulseAt(impulse: Vec2, worldPoint: Vec2) {
        linearVelocity = linearVelocity + impulse * (1f / mass)
        val r = worldPoint - world().position
        val torqueImpulse = r.x * impulse.y - r.y * impulse.x
        angularVelocity += torqueImpulse / effectiveInertia
    }

    /** Continuous torque consumed across the next integration `dt`. */
    fun applyTorque(torque: Float) {
        appliedTorque += torque
    }

    internal fun clearAccumulators() {
        appliedForce = Vec2.ZERO
        appliedTorque = 0f
    }

    // --- Python snake_case aliases (Godot-canonical naming) ---

    @Suppress("PropertyName")
    var linear_velocity: Vec2
        get() = linearVelocity
        set(value) { linearVelocity = value }

    @Suppress("PropertyName")
    var angular_velocity: Float
        get() = angularVelocity
        set(value) { angularVelocity = value }

    @Suppress("PropertyName")
    var gravity_scale: Float
        get() = gravityScale
        set(value) { gravityScale = value }

    @Suppress("PropertyName")
    var linear_damping: Float
        get() = linearDamping
        set(value) { linearDamping = value }

    @Suppress("PropertyName")
    var angular_damping: Float
        get() = angularDamping
        set(value) { angularDamping = value }

    fun apply_force(force: Vec2) = applyForce(force)
    fun apply_impulse(impulse: Vec2) = applyImpulse(impulse)
    fun apply_central_force(force: Vec2) = applyForce(force)
    fun apply_central_impulse(impulse: Vec2) = applyImpulse(impulse)
    fun apply_force_at(force: Vec2, worldPoint: Vec2) = applyForceAt(force, worldPoint)
    fun apply_impulse_at(impulse: Vec2, worldPoint: Vec2) = applyImpulseAt(impulse, worldPoint)
    fun apply_torque(torque: Float) = applyTorque(torque)

    companion object {
        private const val TAG = "RigidBody2D"
    }
}
