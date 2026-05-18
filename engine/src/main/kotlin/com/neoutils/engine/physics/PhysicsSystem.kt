package com.neoutils.engine.physics

import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Scene

/**
 * Naive broad phase: O(N^2) pair test over all live colliders. Adequate for
 * the densities required by the sample games in `engine-foundation`. Documented
 * as a known evolution point in `design.md` / `CLAUDE.md`.
 */
class PhysicsSystem {

    fun step(scene: Scene) {
        val colliders = mutableListOf<Collider>()
        collect(scene, colliders)
        val n = colliders.size
        for (i in 0 until n) {
            val a = colliders[i]
            val ab = a.bounds()
            for (j in i + 1 until n) {
                val b = colliders[j]
                if (ab.intersects(b.bounds())) {
                    a.onCollide(b)
                    b.onCollide(a)
                }
            }
        }
    }

    private fun collect(node: Node, out: MutableList<Collider>) {
        if (!node.isLive) return
        if (node is Collider) out += node
        for (child in node.children) collect(child, out)
    }
}
