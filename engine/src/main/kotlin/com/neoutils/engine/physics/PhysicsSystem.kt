package com.neoutils.engine.physics

import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree

/**
 * Naive broad-phase (O(N²)) over every live [CollisionObject2D] in the tree.
 * Each `step` snapshots the current overlapping pairs and diffs against
 * the previous snapshot to dispatch *enter* / *exit* events exactly once
 * per pair-transition. Pairs that are still overlapping fire nothing —
 * `_on_*_entered` is one-shot per begin-of-overlap.
 *
 * Cleanup: before computing the new snapshot the system drops any tracked
 * pair whose endpoint left the live tree (e.g. via `parent.removeChild` in
 * the middle of two steps). That avoids spurious `_exited` events for
 * detached nodes and lets the GC reclaim them — pairs of detached nodes
 * are simply forgotten, not "exit-dispatched."
 *
 * Density and accuracy assumptions are documented in
 * `openspec/changes/archive/2026-05-18-engine-foundation/design.md` and
 * `collision-overhaul/design.md` (D4).
 */
class PhysicsSystem {

    private val previousOverlapping: MutableSet<UnorderedPair<CollisionObject2D>> = HashSet()

    fun step(tree: SceneTree) {
        // Drop any pair whose endpoint is no longer live before testing the
        // new state, so detached objects never receive `_on_*_exited`.
        previousOverlapping.removeAll { !it.a.isLive || !it.b.isLive }

        val objects = collectObjects(tree).filter { !it.disabled }
        val n = objects.size
        val currentOverlapping = HashSet<UnorderedPair<CollisionObject2D>>()

        tree.beginPhysicsPhase()
        try {
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
                        currentOverlapping += UnorderedPair(a, b)
                    }
                }
            }

            // Exits first: pairs in previous but not in current.
            for (pair in previousOverlapping) {
                if (pair !in currentOverlapping) dispatchExit(pair)
            }
            // Then enters: pairs in current but not in previous.
            for (pair in currentOverlapping) {
                if (pair !in previousOverlapping) dispatchEnter(pair)
            }
        } finally {
            tree.endPhysicsPhase()
        }

        previousOverlapping.clear()
        previousOverlapping.addAll(currentOverlapping)
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
