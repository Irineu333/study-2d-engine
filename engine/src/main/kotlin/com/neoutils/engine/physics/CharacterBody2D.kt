package com.neoutils.engine.physics

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.Inspect
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
     * motion completes uninterrupted. On contact, the body's `position` is
     * advanced exactly to the contact moment (`position + motion * toi`);
     * the caller can then reflect [velocity] across `collision.normal` and
     * (optionally) call [moveAndCollide] again with `collision.remainder`
     * to slide along the surface.
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
     * - The shape-pair sweep is axis-aligned only (see [sweepOverlap]). A
     *   shape with non-zero composed rotation in the parent frame returns
     *   `null` for every pair involving it — that motion falls through to
     *   the unswept advance and any resulting overlap will be detected by
     *   the next [PhysicsSystem.step]. Rotated-sweep support is deferred to
     *   a future `kinematic-rotated-sweep` change.
     */
    fun moveAndCollide(motion: Vec2): KinematicCollision2D? {
        val tree = tree ?: run {
            position = position + motion
            return null
        }
        val parent = this.parent
        if (parent == null) {
            position = position + motion
            return null
        }
        val ownShapes = collectActiveShapes()
        if (ownShapes.isEmpty()) {
            position = position + motion
            return null
        }

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
                    if (bestHit == null || res.toi < bestHit.toi) {
                        bestHit = res
                        bestCollider = target
                    }
                }
            }
        }

        return if (bestHit == null) {
            position = position + motion
            null
        } else {
            val toi = bestHit.toi
            position = position + motion * toi
            KinematicCollision2D(
                point = bestHit.point,
                normal = bestHit.normal,
                collider = bestCollider!!,
                remainder = motion * (1f - toi),
            )
        }
    }
}
