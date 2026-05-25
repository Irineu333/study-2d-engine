package com.neoutils.engine.physics

import com.neoutils.engine.dx.Log
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree

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

    fun step(tree: SceneTree) {
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
