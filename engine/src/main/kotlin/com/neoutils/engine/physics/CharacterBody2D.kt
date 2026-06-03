package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.serialization.Inspect
import com.neoutils.engine.tree.SceneTree
import kotlinx.serialization.Serializable

/**
 * Solid body with a [velocity] slot exposed for scripts and a Godot-style
 * [moveAndCollide] for CCD-correct kinematic motion. The engine does **not**
 * integrate [velocity] automatically (Godot-style): a script chooses how to
 * apply velocity to position (either `position += velocity * dt` in
 * `_physics_process` or via [moveAndCollide]).
 *
 * Use [moveAndCollide] when the body should stop on contact with other
 * `PhysicsBody2D`s with no tunneling at high velocity. The legacy
 * `position += velocity * dt` path continues to work for bodies that don't
 * need swept resolution; collisions with those will be detected discretely
 * by [PhysicsSystem.step] and dispatched via `_on_body_entered` on the next
 * physics frame, with the residual-overlap penalties documented in
 * `collision-iterative-resolution/design.md`.
 */
@Serializable
open class CharacterBody2D : PhysicsBody2D() {

    @Inspect
    var velocity: Vec2 = Vec2.ZERO

    /**
     * Sweeps the body's active [CollisionShape2D]s from the current position
     * by [motion] against every other non-disabled [PhysicsBody2D] in the
     * tree and returns the first contact (lowest TOI) or `null` when the
     * motion completes uninterrupted. On a clean contact ahead (`toi > 0`)
     * the body's `position` is advanced exactly to the contact moment
     * (`position + motion * toi`); the caller can then reflect [velocity]
     * across `collision.normal` and (optionally) call [moveAndCollide] again
     * with `collision.remainder` to slide along the surface.
     *
     * **Starting-overlap recovery:** when the body already overlaps a collider
     * at `t == 0` (`toi == 0`), the method applies the sweep's depenetration
     * to leave that collider AND then re-sweeps the still-unspent [motion] from
     * the separated position, repeating up to a small fixed cap
     * ([RECOVERY_ITERATIONS]). The effect is Godot-like: motion pointing **out
     * of** the collider is spent (the body always escapes an overlap it is
     * trying to leave — even while a peer keeps re-pressing a marginal
     * overlap), while motion pointing **into** the collider makes no forward
     * progress (the body rests against the surface, never tunnels). The
     * returned [KinematicCollision2D] reports the **first** contact's `point`,
     * `normal` and `collider`, with `remainder` equal to the portion of
     * [motion] left unspent after the recovery. A starting overlap with
     * `motion == Vec2.ZERO` only depenetrates and returns `remainder == ZERO`.
     *
     * **Frame:** [motion] is interpreted in the body's **parent** local
     * frame. For top-level bodies (parent is the scene root with no rotation)
     * this matches world space. For nested bodies, the script's velocity
     * should be expressed in the same frame the body lives in — see Demo 5
     * for the rotating-frame pattern.
     *
     * **Limitations:**
     * - Only same-parent targets are considered. A target whose parent
     *   differs from this body's parent is silently skipped (rare in the
     *   demos; revisit when an actual cross-frame jump is needed).
     */
    fun moveAndCollide(motion: Vec2): KinematicCollision2D? {
        val tree = tree ?: run {
            position = position + motion
            return null
        }
        val parent = this.parent ?: run {
            position = position + motion
            return null
        }
        val ownShapes = collectActiveShapes()
        if (ownShapes.isEmpty()) {
            position = position + motion
            return null
        }

        var remaining = motion
        var firstHit: SweepResult? = null
        var firstCollider: CollisionObject2D? = null

        // Bounded recovery loop. The common path (a clean contact, or no
        // contact) resolves in the first iteration — extra iterations only run
        // on a starting overlap, where each one depenetrates and re-sweeps the
        // unspent motion from the separated position. The cap guarantees
        // termination under sustained re-pressing (no infinite churn).
        var iter = 0
        while (iter < RECOVERY_ITERATIONS) {
            iter++
            val hit = scanBestHit(tree, parent, ownShapes, remaining)
            if (hit == null) {
                // Nothing in the way of the remaining motion: spend it all.
                position = position + remaining
                remaining = Vec2.ZERO
                break
            }
            val (collider, res) = hit
            if (firstHit == null) {
                firstHit = res
                firstCollider = collider
                stageContact(tree, parent, res)
            }
            if (res.toi > 0f) {
                // Clean contact ahead: stop exactly at the contact moment.
                position = position + remaining * res.toi + res.depenetration
                remaining = remaining * (1f - res.toi)
                break
            }
            // toi == 0: starting overlap. Leave this collider via the
            // depenetration, then re-sweep the still-unspent motion from the
            // separated position. Outward motion finds slack next iteration and
            // is spent; inward motion re-detects a ~zero-depenetration contact
            // (resting on the surface) and stops here without tunneling.
            position = position + res.depenetration
            val depSq = res.depenetration.x * res.depenetration.x +
                res.depenetration.y * res.depenetration.y
            if (depSq < PENETRATION_EPS_SQ) break
        }

        return firstHit?.let {
            KinematicCollision2D(
                point = it.point,
                normal = it.normal,
                collider = firstCollider!!,
                remainder = remaining,
            )
        }
    }

    /**
     * Sweeps [ownShapes] by [motion] against every live same-[parent]
     * [PhysicsBody2D] and returns the lowest-TOI contact, or `null` when the
     * path is clear. Kept local to the kinematic path: `RigidBody2D` has its
     * own `sweepBestHit` in `PhysicsSystem` typed to the rigid body and tied
     * to the impulse solver — sharing one helper across the two would couple
     * the kinematic path (no impulse) to `PhysicsSystem` internals without
     * making either clearly cleaner (D4).
     */
    private fun scanBestHit(
        tree: SceneTree,
        parent: Node,
        ownShapes: List<Pair<CollisionShape2D, Rect>>,
        motion: Vec2,
    ): Pair<CollisionObject2D, SweepResult>? {
        var bestHit: SweepResult? = null
        var bestCollider: CollisionObject2D? = null
        for (target in collectObjects(tree)) {
            if (target === this) continue
            if (target !is PhysicsBody2D) continue
            if (target.disabled) continue
            if (target.parent !== parent) continue
            val targetShapes = target.collectActiveShapes()
            if (targetShapes.isEmpty()) continue
            for ((aShape, _) in ownShapes) {
                val aShapeRes = aShape.shape ?: continue
                val aTransformInParent = this.transform.compose(aShape.transform)
                for ((bShape, _) in targetShapes) {
                    val bShapeRes = bShape.shape ?: continue
                    val bTransformInParent = target.transform.compose(bShape.transform)
                    val res = sweepOverlap(
                        aShapeRes, aTransformInParent, motion,
                        bShapeRes, bTransformInParent,
                    ) ?: continue
                    // A grazing contact at t==0 with negligible penetration
                    // (a circle resting exactly on a rect corner reports
                    // `toi == 0` with a ~zero depenetration) only blocks when
                    // the motion drives INTO it. When the motion runs along or
                    // away from the contact normal the body is leaving — skip
                    // it so the recovery spends the outward motion instead of
                    // freezing the body on a corner it is already separating
                    // from. A real overlap (penetration above the epsilon) is
                    // always kept so its depenetration recovery runs.
                    val depSq = res.depenetration.x * res.depenetration.x +
                        res.depenetration.y * res.depenetration.y
                    val intoContact = motion.x * res.normal.x + motion.y * res.normal.y < 0f
                    if (res.toi == 0f && depSq < PENETRATION_EPS_SQ && !intoContact) continue
                    if (bestHit == null || res.toi < bestHit.toi) {
                        bestHit = res
                        bestCollider = target
                    }
                }
            }
        }
        return if (bestHit != null && bestCollider != null) bestCollider to bestHit else null
    }

    /**
     * Stage the resolved contact for `ContactGizmoWidget`. Gated by the same
     * flag as the rigid path; staged (not appended) because `step` clears the
     * buffer after `_physics_process` runs — the fold in `step` consolidates
     * it. Early-out when recording is off.
     */
    private fun stageContact(
        tree: SceneTree,
        parent: Node,
        res: SweepResult,
    ) {
        val contacts = tree.debug.contacts
        if (!contacts.recording) return
        // Normalize to world space so the world-pass gizmo draws nested-body
        // contacts correctly; identity for top-level.
        val (worldPoint, worldNormal) = worldContact(parent, res.point, res.normal)
        contacts.stage(worldPoint, worldNormal)
    }

    companion object {
        // Cap for the starting-overlap recovery loop, aligned with the
        // RigidBody2D TOI loop's TOI_ITERATIONS = 4: "a few contacts per frame".
        private const val RECOVERY_ITERATIONS = 4

        // Penetration² below this is "grazing", not a real overlap: a circle
        // resting exactly on a rect corner reports toi == 0 with a sub-1e-3px
        // depenetration. Above it, the depenetration recovery runs.
        private const val PENETRATION_EPS_SQ = 1e-6f
    }
}
