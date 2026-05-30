package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.math.rotate
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D

/**
 * Converts a resolved contact `(point, normal)` — expressed in [parent]'s
 * local frame, the frame the sweep operates in — into **world space**, so the
 * `ContactGizmoWidget` (which draws in the world pass) renders it correctly
 * regardless of body nesting. The [point] is transformed as a position
 * (parent world transform composed with the local point); the [normal] as a
 * unit direction (rotated by the parent's world rotation, re-normalized).
 *
 * For a top-level body whose [parent] applies no rotation/translation/scale
 * (or is a plain `Node` / `null`), the result is the identity — the contact is
 * returned unchanged. Both the `RigidBody2D` path ([PhysicsSystem]) and the
 * `CharacterBody2D` path ([CharacterBody2D.moveAndCollide]) call this so they
 * never diverge.
 *
 * Non-uniform parent scale would skew the normal's direction (it would need
 * the inverse-transpose); that case is out of scope — collision bodies in the
 * demos/games don't use non-uniform scale.
 */
internal fun worldContact(parent: Node?, point: Vec2, normal: Vec2): Pair<Vec2, Vec2> {
    val parentWorld = (parent as? Node2D)?.world() ?: Transform()
    val worldPoint = parentWorld.compose(Transform(position = point)).position
    val worldNormal = rotate(normal, parentWorld.rotation).normalized
    return worldPoint to worldNormal
}
