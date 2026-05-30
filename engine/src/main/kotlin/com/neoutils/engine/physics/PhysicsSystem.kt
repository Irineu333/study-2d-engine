package com.neoutils.engine.physics

import com.neoutils.engine.debug.PhysicsContactBuffer
import com.neoutils.engine.dx.Log
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.math.max

/**
 * Naive broad-phase (O(N²)) over every live [CollisionObject2D] in the tree.
 * Each `step` snapshots the current overlapping pairs and diffs against
 * the previous snapshot to dispatch *enter* / *exit* events exactly once
 * per pair-transition. Pairs that are still overlapping fire nothing —
 * `_on_*_entered` is one-shot per begin-of-overlap.
 *
 * **Convergence loop.** Within a single [step], dispatching a
 * `*_entered` / `*_exited` may itself mutate transforms (a script
 * separates two bodies, swaps velocities, etc.); that mutation can in
 * turn introduce or remove overlap with *other* objects. The system
 * therefore recomputes the overlapping set and re-dispatches the new
 * transitions until the set stabilises or [MAX_RESOLUTION_ITERATIONS]
 * is reached. The common case (no script mutation, or mutations that
 * don't cascade) converges in a single iteration — same cost as before.
 * If the cap is hit while transitions are still being emitted, a warning
 * is logged and the step returns normally — no infinite loops.
 *
 * Cleanup: before computing the new snapshot the system drops any tracked
 * pair whose endpoint left the live tree (e.g. via `parent.removeChild` in
 * the middle of two steps). That cleanup runs **once** at the top of the
 * step (not per iteration), so detached objects never receive `_on_*_exited`.
 *
 * Density and accuracy assumptions are documented in
 * `openspec/changes/archive/2026-05-18-engine-foundation/design.md` and
 * `collision-overhaul/design.md` (D4). The convergence loop is documented
 * in `openspec/changes/collision-iterative-resolution/design.md`.
 */
class PhysicsSystem {

    private val previousOverlapping: MutableSet<UnorderedPair<CollisionObject2D>> = HashSet()

    /**
     * Global gravity applied to every [RigidBody2D] each integration step,
     * scaled per-body by [RigidBody2D.gravityScale]. Default `Vec2.ZERO`
     * (zero-gravity sandbox). Setting non-zero gravity (e.g. `Vec2(0f, 980f)`)
     * makes bodies fall.
     */
    var gravity: Vec2 = Vec2.ZERO

    /**
     * One physics tick. Order within the tick:
     *
     * 1. **Integrate forces** on every live [RigidBody2D]: add `gravity *
     *    gravityScale + appliedForce/mass` and angular `appliedTorque/inertia`
     *    to velocities, apply linear/angular damping, clear accumulators.
     * 2. **Advance + resolve contacts** (TOI loop, up to R=4 iterations per
     *    body) — sweep each [RigidBody2D] against same-parent
     *    [PhysicsBody2D]s, apply bilateral impulse at each contact.
     * 3. **Integrate angular**: `rotation += angularVelocity * dt`.
     * 4. **Dispatch enter/exit** for the new overlapping set (existing
     *    behavior, unchanged).
     *
     * Stages 1-3 produce no signals — only motion. Stage 4 sees post-resolution
     * positions, so `_on_body_entered` reports the contact after impulse.
     */
    fun step(tree: SceneTree, dt: Float) {
        // Contact recording is gated by ContactGizmoWidget.enabled (mirrored
        // onto the buffer's `recording` flag). When off, `contactSink` is null
        // and the solver pays no per-contact recording cost.
        val contactBuffer = tree.debug.contacts
        val contactSink = if (contactBuffer.recording) {
            // Clear the previous step's records, then fold in the contacts
            // staged by `CharacterBody2D.moveAndCollide` during this substep's
            // `_physics_process`, before appending this step's rigid contacts.
            contactBuffer.also {
                it.clear()
                it.takeStaged()
            }
        } else {
            null
        }
        integrate(tree, dt)
        advanceAndResolve(tree, dt, contactSink)
        integrateAngular(tree, dt)

        // Drop any pair whose endpoint is no longer live before testing the
        // new state, so detached objects never receive `_on_*_exited`.
        previousOverlapping.removeAll { !it.a.isLive || !it.b.isLive }

        val objects = collectObjects(tree).filter { !it.disabled }

        tree.beginPhysicsPhase()
        try {
            var iteration = 0
            var dispatchedSomething = true
            var newlyEnteredCount = 0
            var newlyExitedCount = 0
            while (dispatchedSomething && iteration < MAX_RESOLUTION_ITERATIONS) {
                val currentOverlapping = computeOverlapping(objects)
                val newlyEntered = currentOverlapping - previousOverlapping
                val newlyExited = previousOverlapping - currentOverlapping
                newlyEnteredCount = newlyEntered.size
                newlyExitedCount = newlyExited.size
                dispatchedSomething = newlyEntered.isNotEmpty() || newlyExited.isNotEmpty()
                // Snapshot becomes "current" before dispatch so that handlers
                // observing this state via Area2D.getOverlappingAreas/Bodies
                // see the post-transition truth (D3 in
                // kinematic-move-and-collide/design.md).
                previousOverlapping.clear()
                previousOverlapping.addAll(currentOverlapping)
                for (pair in newlyExited) dispatchExit(pair)
                for (pair in newlyEntered) dispatchEnter(pair)
                iteration++
            }
            if (iteration == MAX_RESOLUTION_ITERATIONS && dispatchedSomething) {
                Log.w(
                    TAG,
                    "step hit MAX_RESOLUTION_ITERATIONS=$MAX_RESOLUTION_ITERATIONS — " +
                        "pile-up not converged " +
                        "(pairs still in transition: enter=$newlyEnteredCount, exit=$newlyExitedCount)"
                )
            }
        } finally {
            tree.endPhysicsPhase()
        }
    }

    private fun computeOverlapping(
        objects: List<CollisionObject2D>,
    ): HashSet<UnorderedPair<CollisionObject2D>> {
        val n = objects.size
        val out = HashSet<UnorderedPair<CollisionObject2D>>()
        for (i in 0 until n) {
            val a = objects[i]
            if (!a.isLive) continue
            val aShapes = a.collectActiveShapes()
            if (aShapes.isEmpty()) continue
            for (j in i + 1 until n) {
                val b = objects[j]
                if (!b.isLive) continue
                val bShapes = b.collectActiveShapes()
                if (bShapes.isEmpty()) continue
                if (anyShapePairOverlaps(aShapes, bShapes)) {
                    out += UnorderedPair(a, b)
                }
            }
        }
        return out
    }

    private fun anyShapePairOverlaps(
        aShapes: List<Pair<CollisionShape2D, com.neoutils.engine.math.Rect>>,
        bShapes: List<Pair<CollisionShape2D, com.neoutils.engine.math.Rect>>,
    ): Boolean {
        for ((aShape, aRect) in aShapes) {
            val aShapeRes = aShape.shape ?: continue
            for ((bShape, bRect) in bShapes) {
                val bShapeRes = bShape.shape ?: continue
                // Cheap AABB rejection first, exact test only on overlap.
                if (!aRect.intersects(bRect)) continue
                if (overlap(aShapeRes, aShape.world(), bShapeRes, bShape.world())) return true
            }
        }
        return false
    }

    private fun dispatchEnter(pair: UnorderedPair<CollisionObject2D>) {
        val a = pair.a
        val b = pair.b
        when {
            a is Area2D && b is Area2D -> {
                a.onAreaEntered(b); a.areaEntered.emit(b)
                b.onAreaEntered(a); b.areaEntered.emit(a)
            }
            a is Area2D && b is PhysicsBody2D -> {
                a.onBodyEntered(b); a.bodyEntered.emit(b)
                b.onAreaEntered(a); b.areaEntered.emit(a)
            }
            a is PhysicsBody2D && b is Area2D -> {
                a.onAreaEntered(b); a.areaEntered.emit(b)
                b.onBodyEntered(a); b.bodyEntered.emit(a)
            }
            a is PhysicsBody2D && b is PhysicsBody2D -> {
                a.onBodyEntered(b); a.bodyEntered.emit(b)
                b.onBodyEntered(a); b.bodyEntered.emit(a)
            }
        }
    }

    private fun dispatchExit(pair: UnorderedPair<CollisionObject2D>) {
        val a = pair.a
        val b = pair.b
        when {
            a is Area2D && b is Area2D -> {
                a.onAreaExited(b); a.areaExited.emit(b)
                b.onAreaExited(a); b.areaExited.emit(a)
            }
            a is Area2D && b is PhysicsBody2D -> {
                a.onBodyExited(b); a.bodyExited.emit(b)
                b.onAreaExited(a); b.areaExited.emit(a)
            }
            a is PhysicsBody2D && b is Area2D -> {
                a.onAreaExited(b); a.areaExited.emit(b)
                b.onBodyExited(a); b.bodyExited.emit(a)
            }
            a is PhysicsBody2D && b is PhysicsBody2D -> {
                a.onBodyExited(b); a.bodyExited.emit(b)
                b.onBodyExited(a); b.bodyExited.emit(a)
            }
        }
    }

    private fun integrate(tree: SceneTree, dt: Float) {
        forEachRigidBody(tree) { r ->
            val gravContrib = gravity * r.gravityScale
            val forceContrib = if (r.mass != 0f) r.appliedForce * (1f / r.mass) else Vec2.ZERO
            r.linearVelocity = r.linearVelocity + (gravContrib + forceContrib) * dt
            val inertia = r.effectiveInertia
            r.angularVelocity += if (inertia != 0f) r.appliedTorque / inertia * dt else 0f
            r.linearVelocity = r.linearVelocity * max(0f, 1f - r.linearDamping * dt)
            r.angularVelocity *= max(0f, 1f - r.angularDamping * dt)
            r.clearAccumulators()
        }
    }

    /**
     * For every live [RigidBody2D], sweep its active shapes against every
     * other same-parent [PhysicsBody2D] up to [TOI_ITERATIONS] times, resolve
     * each contact with the impulse solver, and advance the body. Cross-body
     * deduplication is solver-owned (the impulse mutates both sides in one
     * call; the other side's own loop sees `v_rel·n >= 0` and skips).
     */
    private fun advanceAndResolve(tree: SceneTree, dt: Float, contactSink: PhysicsContactBuffer?) {
        val rigids = collectRigidBodies(tree)
        for (r in rigids) {
            if (!r.isLive) continue
            val parent = r.parent ?: continue
            r.inEngineWrite = true
            try {
                var dtRemaining = dt
                var iter = 0
                while (iter < TOI_ITERATIONS && dtRemaining > 0f) {
                    iter++
                    val motion = r.linearVelocity * dtRemaining
                    if (motion.x == 0f && motion.y == 0f) break
                    val hit = sweepBestHit(r, motion, parent) ?: run {
                        r.position = r.position + motion
                        break
                    }
                    val (other, sweepResult) = hit
                    val toi = sweepResult.toi
                    r.position = r.position + motion * toi + sweepResult.depenetration
                    resolveImpulse(
                        r = r,
                        other = other,
                        normal = sweepResult.normal,
                        contactPoint = sweepResult.point,
                    )
                    if (contactSink != null) {
                        // Normalize to world space (identity for top-level
                        // bodies) so nested rigid contacts draw in the right
                        // place; same helper the kinematic path uses.
                        val worldHit = worldContact(parent, sweepResult.point, sweepResult.normal)
                        contactSink.append(worldHit.first, worldHit.second)
                    }
                    val frac = 1f - toi
                    if (frac <= 0f) break
                    dtRemaining *= frac
                    // Tiny residual motion guard
                    val vMagSq = r.linearVelocity.x * r.linearVelocity.x + r.linearVelocity.y * r.linearVelocity.y
                    if (vMagSq * dtRemaining * dtRemaining < MIN_MOTION_SQ) break
                }
            } finally {
                r.inEngineWrite = false
            }
        }
    }

    private fun integrateAngular(tree: SceneTree, dt: Float) {
        forEachRigidBody(tree) { r ->
            if (r.angularVelocity != 0f) {
                r.inEngineWrite = true
                try {
                    r.rotation = r.rotation + r.angularVelocity * dt
                } finally {
                    r.inEngineWrite = false
                }
            }
        }
    }

    private fun sweepBestHit(
        r: RigidBody2D,
        motion: Vec2,
        parent: Node,
    ): Pair<PhysicsBody2D, SweepResult>? {
        val ownShapes = r.collectActiveShapes()
        if (ownShapes.isEmpty()) return null
        var bestHit: SweepResult? = null
        var bestOther: PhysicsBody2D? = null
        val tree = r.tree ?: return null
        for (target in collectObjects(tree)) {
            if (target === r) continue
            if (target !is PhysicsBody2D) continue
            if (target.disabled) continue
            if (target.parent !== parent) continue
            val targetShapes = target.collectActiveShapes()
            if (targetShapes.isEmpty()) continue
            for ((aShape, _) in ownShapes) {
                val aShapeRes = aShape.shape ?: continue
                val aTransformInParent = r.transform.compose(aShape.transform)
                for ((bShape, _) in targetShapes) {
                    val bShapeRes = bShape.shape ?: continue
                    val bTransformInParent = target.transform.compose(bShape.transform)
                    val res = sweepOverlap(
                        aShapeRes, aTransformInParent, motion,
                        bShapeRes, bTransformInParent,
                    ) ?: continue
                    if (bestHit == null || res.toi < bestHit.toi) {
                        bestHit = res
                        bestOther = target
                    }
                }
            }
        }
        return if (bestHit != null && bestOther != null) bestOther to bestHit else null
    }

    /**
     * Bilateral impulse resolution at contact `contactPoint` along `normal`
     * (pointing from `other` toward `r`). Both `r` and `other` (when Rigid)
     * are mutated in this single call; the "reciprocal" pass is implicit.
     * `StaticBody2D` and `CharacterBody2D` are treated as infinite-mass
     * obstacles (their velocity is unaffected).
     *
     * Combine rules: `e = max(eA, eB)`, `μ = sqrt(μA * μB)` (Box2D-style).
     * Applies Coulomb friction after the normal impulse, capped at `μ·|jn|`.
     */
    private fun resolveImpulse(
        r: RigidBody2D,
        other: PhysicsBody2D,
        normal: Vec2,
        contactPoint: Vec2,
    ) {
        val otherRigid = other as? RigidBody2D
        val centroA = r.position
        val rA = contactPoint - centroA
        val centroB = otherRigid?.position ?: other.position
        val rB = contactPoint - centroB

        val vA = r.linearVelocity + perp(r.angularVelocity, rA)
        val vB = if (otherRigid != null) {
            otherRigid.linearVelocity + perp(otherRigid.angularVelocity, rB)
        } else {
            Vec2.ZERO
        }
        val vRel = vA - vB
        val vRelN = vRel.x * normal.x + vRel.y * normal.y
        if (vRelN >= 0f) return // already separating

        val invMA = 1f / r.mass
        val invIA = 1f / r.effectiveInertia
        val invMB = if (otherRigid != null) 1f / otherRigid.mass else 0f
        val invIB = if (otherRigid != null) 1f / otherRigid.effectiveInertia else 0f

        val eA = r.restitution
        val eB = other.restitution
        val muA = r.friction
        val muB = other.friction
        val eCombined = max(eA, eB)
        val muCombined = kotlin.math.sqrt(muA * muB)

        val rAcrossN = rA.x * normal.y - rA.y * normal.x
        val rBcrossN = rB.x * normal.y - rB.y * normal.x
        val denomN = invMA + invMB + rAcrossN * rAcrossN * invIA + rBcrossN * rBcrossN * invIB
        if (denomN == 0f) return

        val jn = -(1f + eCombined) * vRelN / denomN

        r.linearVelocity = r.linearVelocity + normal * (jn * invMA)
        r.angularVelocity += rAcrossN * jn * invIA
        if (otherRigid != null) {
            otherRigid.linearVelocity = otherRigid.linearVelocity - normal * (jn * invMB)
            otherRigid.angularVelocity -= rBcrossN * jn * invIB
        }

        // Tangential friction (Coulomb)
        val vTangX = vRel.x - vRelN * normal.x
        val vTangY = vRel.y - vRelN * normal.y
        val vTangMag = kotlin.math.sqrt(vTangX * vTangX + vTangY * vTangY)
        if (vTangMag < FRICTION_EPS) return

        val tx = vTangX / vTangMag
        val ty = vTangY / vTangMag
        val rAcrossT = rA.x * ty - rA.y * tx
        val rBcrossT = rB.x * ty - rB.y * tx
        val denomT = invMA + invMB + rAcrossT * rAcrossT * invIA + rBcrossT * rBcrossT * invIB
        if (denomT == 0f) return

        val jtBrake = vTangMag / denomT
        val jt = kotlin.math.min(jtBrake, muCombined * kotlin.math.abs(jn))

        r.linearVelocity = r.linearVelocity + Vec2(-tx, -ty) * (jt * invMA)
        r.angularVelocity += rAcrossT * -jt * invIA
        if (otherRigid != null) {
            otherRigid.linearVelocity = otherRigid.linearVelocity - Vec2(-tx, -ty) * (jt * invMB)
            otherRigid.angularVelocity -= rBcrossT * -jt * invIB
        }
    }

    /** `cross(ω, r) = (-ω·r.y, ω·r.x)` — the linear velocity at point `r` from angular `ω`. */
    private fun perp(omega: Float, r: Vec2): Vec2 = Vec2(-omega * r.y, omega * r.x)

    private fun forEachRigidBody(tree: SceneTree, block: (RigidBody2D) -> Unit) {
        forEachRigidBodyImpl(tree.root, block)
    }

    private fun forEachRigidBodyImpl(node: Node, block: (RigidBody2D) -> Unit) {
        if (!node.isLive) return
        if (node is RigidBody2D && !node.disabled) block(node)
        for (child in node.children) forEachRigidBodyImpl(child, block)
    }

    private fun collectRigidBodies(tree: SceneTree): List<RigidBody2D> {
        val out = mutableListOf<RigidBody2D>()
        forEachRigidBody(tree) { out += it }
        return out
    }

    /**
     * Snapshot of every [CollisionObject2D] that is currently overlapping
     * [obj] (post-dispatch of the last [step]). Used by
     * [Area2D.getOverlappingAreas] / [Area2D.getOverlappingBodies] to expose
     * Godot-style persistent overlap queries without forcing scripts to
     * maintain a parallel set themselves. Cost is O(K) in the size of the
     * currently-tracked pair set.
     */
    internal fun overlappingPeersOf(obj: CollisionObject2D): List<CollisionObject2D> {
        val out = mutableListOf<CollisionObject2D>()
        for (pair in previousOverlapping) {
            when {
                pair.a === obj -> out += pair.b
                pair.b === obj -> out += pair.a
            }
        }
        return out
    }

    companion object {
        private const val TAG = "PhysicsSystem"
        private const val MAX_RESOLUTION_ITERATIONS = 8
        private const val TOI_ITERATIONS = 4
        private const val MIN_MOTION_SQ = 1e-10f
        private const val FRICTION_EPS = 1e-4f
    }
}

/**
 * Enumerates every live [CollisionObject2D] reachable from the tree in
 * pre-order. Exposed publicly so debug overlays can iterate without
 * depending on a particular renderer module.
 */
fun collectObjects(tree: SceneTree): List<CollisionObject2D> {
    val out = mutableListOf<CollisionObject2D>()
    collect(tree.root, out)
    return out
}

private fun collect(node: Node, out: MutableList<CollisionObject2D>) {
    if (!node.isLive) return
    if (node is CollisionObject2D) out += node
    for (child in node.children) collect(child, out)
}

/**
 * Enumerates every live [CollisionShape2D] that is a direct child of an
 * active (non-disabled) [CollisionObject2D]. Used by the debug overlay to
 * render shape bounds with the correct owner-aware coloring.
 */
fun collectActiveCollisionShapes(tree: SceneTree): List<Pair<CollisionShape2D, CollisionObject2D>> {
    val out = mutableListOf<Pair<CollisionShape2D, CollisionObject2D>>()
    for (obj in collectObjects(tree)) {
        if (obj.disabled) continue
        for (child in obj.children) {
            if (child is CollisionShape2D && !child.disabled && child.shape != null) {
                out += child to obj
            }
        }
    }
    return out
}
