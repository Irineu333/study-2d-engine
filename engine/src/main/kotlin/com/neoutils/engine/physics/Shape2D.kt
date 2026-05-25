package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Resource-style shape held by a [CollisionShape2D]. Polymorphic via
 * `kotlinx.serialization`'s built-in sealed-class handling — every subtype
 * carries a `type` discriminator in the serialized form.
 *
 * Subclasses describe the geometry; the AABB they return from [bounds] is
 * what [PhysicsSystem] uses for broad-phase pair tests. Exact overlap (rect-
 * rect / circle-circle / rect-circle) is computed by [overlap]; rotated
 * shapes fall back to AABB approximation — Asteroids will add OBB if needed.
 */
@Serializable
sealed class Shape2D {

    /**
     * Axis-aligned bounding box of this shape in world space, given the
     * world [Transform] of the owning [CollisionShape2D] and an extra local
     * offset (almost always [Vec2.ZERO]).
     */
    abstract fun bounds(world: Transform, localOffset: Vec2): Rect
}

@Serializable
class RectangleShape2D : Shape2D() {

    @Inspect
    var size: Vec2 = Vec2(10f, 10f)

    override fun bounds(world: Transform, localOffset: Vec2): Rect {
        val originX = world.position.x + localOffset.x
        val originY = world.position.y + localOffset.y
        val w = size.x * world.scale.x
        val h = size.y * world.scale.y
        if (world.rotation == 0f) {
            return Rect(Vec2(originX, originY), Vec2(w, h))
        }
        val c = cos(world.rotation)
        val s = sin(world.rotation)
        val corners = arrayOf(
            Vec2(0f, 0f),
            Vec2(w, 0f),
            Vec2(0f, h),
            Vec2(w, h),
        )
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (corner in corners) {
            val rx = corner.x * c - corner.y * s + originX
            val ry = corner.x * s + corner.y * c + originY
            minX = min(minX, rx)
            minY = min(minY, ry)
            maxX = max(maxX, rx)
            maxY = max(maxY, ry)
        }
        return Rect(Vec2(minX, minY), Vec2(abs(maxX - minX), abs(maxY - minY)))
    }
}

@Serializable
class CircleShape2D : Shape2D() {

    @Inspect
    var radius: Float = 5f

    override fun bounds(world: Transform, localOffset: Vec2): Rect {
        val r = radius * max(abs(world.scale.x), abs(world.scale.y))
        val cx = world.position.x + localOffset.x
        val cy = world.position.y + localOffset.y
        return Rect(Vec2(cx - r, cy - r), Vec2(2f * r, 2f * r))
    }
}

/**
 * Exact-as-possible overlap test for two shapes given their world transforms.
 * Rect-rect uses AABB intersection; circle-circle uses center distance vs
 * sum of radii; rect-circle uses closest-point-on-rect-to-circle-center.
 * Rotated rectangles approximate via their AABB (good enough for the
 * didactic cases targeted by this change).
 */
fun overlap(a: Shape2D, aWorld: Transform, b: Shape2D, bWorld: Transform): Boolean {
    return when {
        a is RectangleShape2D && b is RectangleShape2D ->
            a.bounds(aWorld, Vec2.ZERO).intersects(b.bounds(bWorld, Vec2.ZERO))
        a is CircleShape2D && b is CircleShape2D -> {
            val ra = a.radius * max(abs(aWorld.scale.x), abs(aWorld.scale.y))
            val rb = b.radius * max(abs(bWorld.scale.x), abs(bWorld.scale.y))
            val dx = aWorld.position.x - bWorld.position.x
            val dy = aWorld.position.y - bWorld.position.y
            val sum = ra + rb
            dx * dx + dy * dy < sum * sum
        }
        a is RectangleShape2D && b is CircleShape2D ->
            rectCircleOverlap(a, aWorld, b, bWorld)
        a is CircleShape2D && b is RectangleShape2D ->
            rectCircleOverlap(b, bWorld, a, aWorld)
        else -> false
    }
}

private fun rectCircleOverlap(
    rect: RectangleShape2D,
    rectWorld: Transform,
    circle: CircleShape2D,
    circleWorld: Transform,
): Boolean {
    val r = circle.radius * max(abs(circleWorld.scale.x), abs(circleWorld.scale.y))
    val cx = circleWorld.position.x
    val cy = circleWorld.position.y
    val aabb = rect.bounds(rectWorld, Vec2.ZERO)
    val nearestX = cx.coerceIn(aabb.left, aabb.right)
    val nearestY = cy.coerceIn(aabb.top, aabb.bottom)
    val dx = cx - nearestX
    val dy = cy - nearestY
    return dx * dx + dy * dy < r * r
}
