package com.neoutils.engine.physics

import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree

/**
 * Naive broad phase: O(N^2) pair test over all live colliders. Adequate for
 * the densities required by the sample games in `engine-foundation`. Documented
 * as a known evolution point in `design.md` / `CLAUDE.md`.
 */
class PhysicsSystem {

    fun step(tree: SceneTree) {
        val colliders = collectColliders(tree)
        val n = colliders.size
        tree.beginPhysicsPhase()
        try {
            for (i in 0 until n) {
                val a = colliders[i]
                if (!a.isLive) continue
                val ab = a.bounds()
                for (j in i + 1 until n) {
                    val b = colliders[j]
                    if (!b.isLive) continue
                    if (ab.intersects(b.bounds())) {
                        a.onCollide(b)
                        b.onCollide(a)
                    }
                }
            }
        } finally {
            tree.endPhysicsPhase()
        }
    }
}

/**
 * Enumerates every live `Collider` reachable from the tree. Exposed publicly so
 * the integrating runtime (e.g. the Compose `GameSurface`) can draw a debug
 * overlay without the engine core depending on the DX layer.
 */
fun collectColliders(tree: SceneTree): List<Collider> {
    val out = mutableListOf<Collider>()
    collect(tree.root, out)
    return out
}

private fun collect(node: Node, out: MutableList<Collider>) {
    if (!node.isLive) return
    if (node is Collider) out += node
    for (child in node.children) collect(child, out)
}
