package com.neoutils.engine.physics

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree

/**
 * Sum of `m · v` over every live, non-disabled [RigidBody2D] in the tree.
 * Returns [Vec2.ZERO] when the tree has no rigid bodies. Pre-order walk;
 * O(N) in tree size. Use to verify linear momentum conservation through
 * elastic + inelastic collisions (it should stay constant in the absence
 * of external forces / gravity).
 */
fun SceneTree.totalLinearMomentum(): Vec2 {
    var sumX = 0f
    var sumY = 0f
    forEachRigid(root) { r ->
        sumX += r.mass * r.linearVelocity.x
        sumY += r.mass * r.linearVelocity.y
    }
    return Vec2(sumX, sumY)
}

/**
 * Sum of `I · ω + m · (x · vy − y · vx)` over every live, non-disabled
 * [RigidBody2D] (the second term is the orbital angular momentum about the
 * world origin). Returns `0f` when the tree has no rigid bodies.
 */
fun SceneTree.totalAngularMomentum(): Float {
    var sum = 0f
    forEachRigid(root) { r ->
        sum += r.effectiveInertia * r.angularVelocity +
            r.mass * (r.position.x * r.linearVelocity.y - r.position.y * r.linearVelocity.x)
    }
    return sum
}

/**
 * Sum of `0.5 · m · |v|² + 0.5 · I · ω²` over every live, non-disabled
 * [RigidBody2D]. Returns `0f` when the tree has no rigid bodies. Use as a
 * sanity check: elastic collisions conserve this; inelastic collisions
 * dissipate it monotonically.
 */
fun SceneTree.totalKineticEnergy(): Float {
    var sum = 0f
    forEachRigid(root) { r ->
        val vSq = r.linearVelocity.x * r.linearVelocity.x + r.linearVelocity.y * r.linearVelocity.y
        sum += 0.5f * r.mass * vSq + 0.5f * r.effectiveInertia * r.angularVelocity * r.angularVelocity
    }
    return sum
}

private fun forEachRigid(node: Node, block: (RigidBody2D) -> Unit) {
    if (!node.isLive) return
    if (node is RigidBody2D && !node.disabled) block(node)
    for (child in node.children) forEachRigid(child, block)
}
