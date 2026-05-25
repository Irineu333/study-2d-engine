package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.math.rotate
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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
        val corners = obbCorners(world, size, localOffset)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (corner in corners) {
            minX = min(minX, corner.x)
            minY = min(minY, corner.y)
            maxX = max(maxX, corner.x)
            maxY = max(maxY, corner.y)
        }
        return Rect(Vec2(minX, minY), Vec2(abs(maxX - minX), abs(maxY - minY)))
    }
}

/**
 * Four world-space corners of an axis-aligned-or-rotated rectangle whose local
 * origin sits at `world.position + localOffset` and whose local size (after
 * scale) is `size * world.scale`. Order: top-left, top-right, bottom-left,
 * bottom-right — but downstream uses (AABB envelope, SAT projections) are
 * order-invariant. Accepts `world.rotation == 0f` and returns axis-aligned
 * corners in that case.
 */
private fun obbCorners(world: Transform, size: Vec2, localOffset: Vec2): Array<Vec2> {
    val originX = world.position.x + localOffset.x
    val originY = world.position.y + localOffset.y
    val w = size.x * world.scale.x
    val h = size.y * world.scale.y
    if (world.rotation == 0f) {
        return arrayOf(
            Vec2(originX, originY),
            Vec2(originX + w, originY),
            Vec2(originX, originY + h),
            Vec2(originX + w, originY + h),
        )
    }
    val c = cos(world.rotation)
    val s = sin(world.rotation)
    val locals = arrayOf(
        Vec2(0f, 0f),
        Vec2(w, 0f),
        Vec2(0f, h),
        Vec2(w, h),
    )
    return Array(4) { i ->
        val v = locals[i]
        Vec2(v.x * c - v.y * s + originX, v.x * s + v.y * c + originY)
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
        a is RectangleShape2D && b is RectangleShape2D -> {
            if (aWorld.rotation == 0f && bWorld.rotation == 0f) {
                a.bounds(aWorld, Vec2.ZERO).intersects(b.bounds(bWorld, Vec2.ZERO))
            } else {
                obbVsObbOverlap(a, aWorld, b, bWorld)
            }
        }
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

private fun obbVsObbOverlap(
    a: RectangleShape2D,
    aWorld: Transform,
    b: RectangleShape2D,
    bWorld: Transform,
): Boolean {
    val cornersA = obbCorners(aWorld, a.size, Vec2.ZERO)
    val cornersB = obbCorners(bWorld, b.size, Vec2.ZERO)
    // Two perpendicular edges of each OBB give the four SAT axes. obbCorners
    // returns corners in order TL, TR, BL, BR — edges TL→TR and TL→BL are the
    // two sides at the top-left corner, perpendicular to each other.
    val edgeA1 = cornersA[1] - cornersA[0]
    val edgeA2 = cornersA[2] - cornersA[0]
    val edgeB1 = cornersB[1] - cornersB[0]
    val edgeB2 = cornersB[2] - cornersB[0]
    val axes = arrayOf(edgeA1, edgeA2, edgeB1, edgeB2)
    for (axis in axes) {
        val (minA, maxA) = projectOnto(axis, cornersA)
        val (minB, maxB) = projectOnto(axis, cornersB)
        if (maxA < minB || maxB < minA) return false
    }
    return true
}

private fun projectOnto(axis: Vec2, corners: Array<Vec2>): Pair<Float, Float> {
    var minP = Float.POSITIVE_INFINITY
    var maxP = Float.NEGATIVE_INFINITY
    for (corner in corners) {
        val p = corner.x * axis.x + corner.y * axis.y
        if (p < minP) minP = p
        if (p > maxP) maxP = p
    }
    return minP to maxP
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

/**
 * Result of a successful swept-overlap test: time-of-impact along the motion
 * span (`toi ∈ [0, 1]`, with `0` meaning starting-overlap), world-space
 * contact `point`, and unit `normal` pointing from the stationary target
 * outward toward the moving body. [depenetration] is the displacement the
 * mover should add to its position to resolve a pre-existing overlap; it is
 * [Vec2.ZERO] for sweeps that genuinely cross a face during [0, 1].
 */
data class SweepResult(
    val toi: Float,
    val point: Vec2,
    val normal: Vec2,
    val depenetration: Vec2 = Vec2.ZERO,
)

/**
 * Continuous swept-overlap test for the three shape pairs (circle-circle,
 * circle-rect, rect-rect). [a] sweeps from [aWorld] by [motion] over `t ∈
 * [0, 1]`; [b] is stationary at [bWorld]. Returns a [SweepResult] on hit or
 * `null` when no contact happens within the motion span.
 *
 * Both transforms may have any rotation, **provided they share the same
 * frame of origin** (in `CharacterBody2D.moveAndCollide` that's the common
 * parent local frame). Five paths are dispatched:
 *
 *  - circle-vs-circle: rotation-invariant (the disc is symmetric); always
 *    via [sweepCircleCircle].
 *  - circle-vs-rect axis-aligned: [sweepCircleRect].
 *  - circle-vs-rect rotated: [sweepCircleRotatedRect] (inverse-rotate into
 *    the rect's local frame, reuse the axis-aligned math, rotate result
 *    back).
 *  - rect-vs-circle: dual of the above via [sweepRectVsCircle] /
 *    [sweepRotatedRectVsCircle].
 *  - rect-vs-rect: [sweepRectRect] when both axis-aligned;
 *    [sweepRotatedRectRotatedRect] (temporal SAT) otherwise.
 *
 * Starting-overlap is treated explicitly across every path: `toi = 0f` is
 * returned together with a `depenetration` vector along the smallest-
 * penetration axis (the MTV in the OBB case). Callers like
 * [CharacterBody2D.moveAndCollide] add this vector to position so the next
 * frame begins from a non-overlapping configuration.
 */
fun sweepOverlap(
    a: Shape2D, aWorld: Transform, motion: Vec2,
    b: Shape2D, bWorld: Transform,
): SweepResult? {
    return when {
        a is CircleShape2D && b is CircleShape2D ->
            sweepCircleCircle(a, aWorld, motion, b, bWorld)
        a is CircleShape2D && b is RectangleShape2D ->
            if (bWorld.rotation == 0f) {
                sweepCircleRect(a, aWorld, motion, b, bWorld)
            } else {
                sweepCircleRotatedRect(a, aWorld, motion, b, bWorld)
            }
        a is RectangleShape2D && b is CircleShape2D ->
            if (aWorld.rotation == 0f) {
                sweepRectVsCircle(a, aWorld, motion, b, bWorld)
            } else {
                sweepRotatedRectVsCircle(a, aWorld, motion, b, bWorld)
            }
        a is RectangleShape2D && b is RectangleShape2D ->
            if (aWorld.rotation == 0f && bWorld.rotation == 0f) {
                sweepRectRect(a, aWorld, motion, b, bWorld)
            } else {
                sweepRotatedRectRotatedRect(a, aWorld, motion, b, bWorld)
            }
        else -> null
    }
}

private fun sweepCircleCircle(
    a: CircleShape2D, aWorld: Transform, motion: Vec2,
    b: CircleShape2D, bWorld: Transform,
): SweepResult? {
    val ra = a.radius * max(abs(aWorld.scale.x), abs(aWorld.scale.y))
    val rb = b.radius * max(abs(bWorld.scale.x), abs(bWorld.scale.y))
    val rSum = ra + rb
    val dx = aWorld.position.x - bWorld.position.x
    val dy = aWorld.position.y - bWorld.position.y
    val distSq = dx * dx + dy * dy
    val rSumSq = rSum * rSum

    if (distSq < rSumSq) {
        val sep = if (distSq == 0f) Vec2(1f, 0f) else Vec2(dx, dy).normalized
        val point = Vec2(bWorld.position.x + sep.x * rb, bWorld.position.y + sep.y * rb)
        val penetration = rSum - kotlin.math.sqrt(distSq)
        val depen = Vec2(sep.x * penetration, sep.y * penetration)
        return SweepResult(toi = 0f, point = point, normal = sep, depenetration = depen)
    }

    val mLenSq = motion.x * motion.x + motion.y * motion.y
    if (mLenSq == 0f) return null

    // |d + m·t|² = rSum² → mLenSq·t² + 2·(d·m)·t + (distSq − rSum²) = 0
    val mDotD = motion.x * dx + motion.y * dy
    val coefA = mLenSq
    val coefB = 2f * mDotD
    val coefC = distSq - rSumSq
    val disc = coefB * coefB - 4f * coefA * coefC
    if (disc < 0f) return null
    val t = (-coefB - sqrt(disc)) / (2f * coefA)
    if (t < 0f || t > 1f) return null

    val aHitX = aWorld.position.x + motion.x * t
    val aHitY = aWorld.position.y + motion.y * t
    val normal = Vec2(aHitX - bWorld.position.x, aHitY - bWorld.position.y).normalized
    val point = Vec2(bWorld.position.x + normal.x * rb, bWorld.position.y + normal.y * rb)
    return SweepResult(toi = t, point = point, normal = normal)
}

/**
 * Slab method for ray-vs-AABB. Returns `(tEnter, tExit)` along the ray
 * `origin + dir·t`, or `null` when the ray misses the slab entirely.
 */
private fun slab(origin: Float, dir: Float, lo: Float, hi: Float): Pair<Float, Float>? {
    if (dir == 0f) {
        return if (origin > lo && origin < hi) {
            Float.NEGATIVE_INFINITY to Float.POSITIVE_INFINITY
        } else null
    }
    val t1 = (lo - origin) / dir
    val t2 = (hi - origin) / dir
    return if (t1 < t2) t1 to t2 else t2 to t1
}

private fun sweepRectRect(
    a: RectangleShape2D, aWorld: Transform, motion: Vec2,
    b: RectangleShape2D, bWorld: Transform,
): SweepResult? {
    val aw = a.size.x * abs(aWorld.scale.x)
    val ah = a.size.y * abs(aWorld.scale.y)
    val bw = b.size.x * abs(bWorld.scale.x)
    val bh = b.size.y * abs(bWorld.scale.y)
    val ax0 = aWorld.position.x
    val ay0 = aWorld.position.y
    val bx0 = bWorld.position.x
    val by0 = bWorld.position.y

    // Minkowski sum: A overlaps B iff A's top-left is inside the expanded rect
    // (bx0 − aw, by0 − ah) .. (bx0 + bw, by0 + bh).
    val ex0 = bx0 - aw
    val ex1 = bx0 + bw
    val ey0 = by0 - ah
    val ey1 = by0 + bh

    if (ax0 > ex0 && ax0 < ex1 && ay0 > ey0 && ay0 < ey1) {
        // Starting overlap — pick smallest-penetration axis.
        val penLeft = ax0 - ex0
        val penRight = ex1 - ax0
        val penTop = ay0 - ey0
        val penBottom = ey1 - ay0
        val minPen = minOf(penLeft, penRight, penTop, penBottom)
        val normal = when (minPen) {
            penLeft -> Vec2(-1f, 0f)
            penRight -> Vec2(1f, 0f)
            penTop -> Vec2(0f, -1f)
            else -> Vec2(0f, 1f)
        }
        val point = Vec2(ax0 + aw / 2f, ay0 + ah / 2f)
        val depen = Vec2(normal.x * minPen, normal.y * minPen)
        return SweepResult(toi = 0f, point = point, normal = normal, depenetration = depen)
    }

    val xSlab = slab(ax0, motion.x, ex0, ex1) ?: return null
    val ySlab = slab(ay0, motion.y, ey0, ey1) ?: return null
    val tEnter = max(xSlab.first, ySlab.first)
    val tExit = min(xSlab.second, ySlab.second)
    if (tEnter > tExit) return null
    if (tEnter > 1f || tExit < 0f) return null
    val toi = max(tEnter, 0f)

    // Tangent-leaving guard: tEnter < 0 means the body was already inside the
    // expanded slab before t=0 and is now exiting — not a new collision. The
    // genuine deep-starting-overlap case is handled by the strict-inside branch
    // above. Without this guard a body touching a wall and moving away gets a
    // bogus toi=0, the script reflects velocity back into the wall, and the
    // body oscillates in place (freezing).
    if (tEnter < 0f) return null
    val normal = if (xSlab.first >= ySlab.first) {
        Vec2(if (motion.x > 0f) -1f else 1f, 0f)
    } else {
        Vec2(0f, if (motion.y > 0f) -1f else 1f)
    }
    val point = Vec2(ax0 + motion.x * toi + aw / 2f, ay0 + motion.y * toi + ah / 2f)
    return SweepResult(toi = toi, point = point, normal = normal)
}

private fun sweepCircleRect(
    circle: CircleShape2D, circleWorld: Transform, motion: Vec2,
    rect: RectangleShape2D, rectWorld: Transform,
): SweepResult? {
    val r = circle.radius * max(abs(circleWorld.scale.x), abs(circleWorld.scale.y))
    val rw = rect.size.x * abs(rectWorld.scale.x)
    val rh = rect.size.y * abs(rectWorld.scale.y)
    val cx = circleWorld.position.x
    val cy = circleWorld.position.y
    val rx0 = rectWorld.position.x
    val ry0 = rectWorld.position.y
    val rx1 = rx0 + rw
    val ry1 = ry0 + rh

    // Starting overlap: nearest point on rect to circle center already within r.
    val nearestX0 = cx.coerceIn(rx0, rx1)
    val nearestY0 = cy.coerceIn(ry0, ry1)
    val dx0 = cx - nearestX0
    val dy0 = cy - nearestY0
    if (dx0 * dx0 + dy0 * dy0 < r * r) {
        val sep: Vec2
        val penetration: Float
        if (dx0 == 0f && dy0 == 0f) {
            // Circle center strictly inside rect — pick smallest-penetration face.
            val penLeft = cx - rx0
            val penRight = rx1 - cx
            val penTop = cy - ry0
            val penBottom = ry1 - cy
            val minPen = minOf(penLeft, penRight, penTop, penBottom)
            sep = when (minPen) {
                penLeft -> Vec2(-1f, 0f)
                penRight -> Vec2(1f, 0f)
                penTop -> Vec2(0f, -1f)
                else -> Vec2(0f, 1f)
            }
            penetration = r + minPen
        } else {
            sep = Vec2(dx0, dy0).normalized
            penetration = r - kotlin.math.sqrt(dx0 * dx0 + dy0 * dy0)
        }
        val depen = Vec2(sep.x * penetration, sep.y * penetration)
        return SweepResult(toi = 0f, point = Vec2(nearestX0, nearestY0), normal = sep, depenetration = depen)
    }

    // Ray vs Minkowski-expanded rect, then refine rounded corners.
    val ex0 = rx0 - r
    val ex1 = rx1 + r
    val ey0 = ry0 - r
    val ey1 = ry1 + r

    val xSlab = slab(cx, motion.x, ex0, ex1) ?: return null
    val ySlab = slab(cy, motion.y, ey0, ey1) ?: return null
    val tEnter = max(xSlab.first, ySlab.first)
    val tExit = min(xSlab.second, ySlab.second)
    if (tEnter > tExit) return null
    if (tEnter > 1f || tExit < 0f) return null
    // Tangent-leaving guard (see sweepRectRect): tEnter < 0 means the circle
    // was already inside the expanded rect before t=0 — not a new contact.
    if (tEnter < 0f) return null
    var toi = tEnter

    val xControlled = xSlab.first >= ySlab.first
    var normal = if (xControlled) {
        Vec2(if (motion.x > 0f) -1f else 1f, 0f)
    } else {
        Vec2(0f, if (motion.y > 0f) -1f else 1f)
    }

    val hitX = cx + motion.x * toi
    val hitY = cy + motion.y * toi

    // Rounded-corner refinement: the rectangular slab over-extends into the
    // diagonal corner region, so re-solve a ray-vs-circle when the hit lies
    // past the original rect on the *perpendicular* axis.
    val cornerX: Float
    val cornerY: Float
    val needsRefine: Boolean
    if (xControlled) {
        cornerX = if (normal.x < 0f) rx0 else rx1
        cornerY = when {
            hitY < ry0 -> ry0
            hitY > ry1 -> ry1
            else -> 0f
        }
        needsRefine = hitY < ry0 || hitY > ry1
    } else {
        cornerY = if (normal.y < 0f) ry0 else ry1
        cornerX = when {
            hitX < rx0 -> rx0
            hitX > rx1 -> rx1
            else -> 0f
        }
        needsRefine = hitX < rx0 || hitX > rx1
    }

    if (needsRefine) {
        val dCx = cx - cornerX
        val dCy = cy - cornerY
        val mLenSq = motion.x * motion.x + motion.y * motion.y
        if (mLenSq == 0f) return null
        val coefB = 2f * (dCx * motion.x + dCy * motion.y)
        val coefC = dCx * dCx + dCy * dCy - r * r
        val disc = coefB * coefB - 4f * mLenSq * coefC
        if (disc < 0f) return null
        val tCorner = (-coefB - sqrt(disc)) / (2f * mLenSq)
        if (tCorner < 0f || tCorner > 1f) return null
        toi = tCorner
        val cAtHitX = cx + motion.x * toi
        val cAtHitY = cy + motion.y * toi
        normal = Vec2(cAtHitX - cornerX, cAtHitY - cornerY).normalized
    }

    val cAtHitX = cx + motion.x * toi
    val cAtHitY = cy + motion.y * toi
    val point = Vec2(cAtHitX - normal.x * r, cAtHitY - normal.y * r)
    return SweepResult(toi = toi, point = point, normal = normal)
}

private fun sweepRectVsCircle(
    rect: RectangleShape2D, rectWorld: Transform, motion: Vec2,
    circle: CircleShape2D, circleWorld: Transform,
): SweepResult? {
    // Geometric duality: rect-moves-by-motion vs stationary-circle is equivalent
    // to circle-moves-by-(-motion) vs stationary-rect. Toi is identical; the
    // returned normal points from the moving body (rect) outward toward circle
    // in the swapped frame — invert it for the original frame.
    val swapped = sweepCircleRect(
        circle, circleWorld, -motion,
        rect, rectWorld,
    ) ?: return null
    val r = circle.radius * max(abs(circleWorld.scale.x), abs(circleWorld.scale.y))
    val normal = -swapped.normal
    val point = Vec2(
        circleWorld.position.x + normal.x * r,
        circleWorld.position.y + normal.y * r,
    )
    // The swapped result's depenetration moves the circle outward; in the
    // original framing the mover is the rect, which must travel in the
    // opposite direction to achieve the same separation.
    val depen = -swapped.depenetration
    return SweepResult(toi = swapped.toi, point = point, normal = normal, depenetration = depen)
}

private fun inverseRotate(p: Vec2, radians: Float): Vec2 = rotate(p, -radians)

private fun projectScalar(v: Vec2, axis: Vec2): Float = v.x * axis.x + v.y * axis.y

/**
 * Swept OBB-vs-OBB via temporal SAT. Static SAT projects the 4 corners of
 * each OBB on the 4 candidate axes (two normals per OBB) and reports overlap
 * iff every axis sees overlap; the temporal extension treats A's projection
 * as moving by `(motion·axis)·t`, computes the per-axis `[tIn, tOut]` where
 * intervals overlap, and intersects across axes to get the global `[tEnter,
 * tExit]`. Starting-overlap (all axes overlap at `t=0`) returns `toi=0` with
 * a depenetration vector along the SAT axis of least overlap (the minimum
 * translation vector). See design.md decision D2.
 */
private fun sweepRotatedRectRotatedRect(
    a: RectangleShape2D, aWorld: Transform, motion: Vec2,
    b: RectangleShape2D, bWorld: Transform,
): SweepResult? {
    val cornersA = obbCorners(aWorld, a.size, Vec2.ZERO)
    val cornersB = obbCorners(bWorld, b.size, Vec2.ZERO)
    val rawAxes = arrayOf(
        cornersA[1] - cornersA[0],
        cornersA[2] - cornersA[0],
        cornersB[1] - cornersB[0],
        cornersB[2] - cornersB[0],
    )
    val nAxes = rawAxes.size
    val axes = Array(nAxes) { rawAxes[it].normalized }
    val intervalA = Array(nAxes) { projectOnto(axes[it], cornersA) }
    val intervalB = Array(nAxes) { projectOnto(axes[it], cornersB) }
    val dts = FloatArray(nAxes) { projectScalar(motion, axes[it]) }

    // Phase 1: static SAT (t = 0). Tangent contact (maxA == minB) counts as
    // separator — matches the strict-inside check the axis-aligned path uses
    // (ax0 > ex0 && ax0 < ex1 → tangent is not "inside"). Without this, a
    // body tangent to a wall would always be reported as starting-overlap
    // with depen=ZERO and the next reflected step would push it into the
    // wall, freezing in place.
    var anyStaticSeparator = false
    var minStaticOverlap = Float.POSITIVE_INFINITY
    var mtvAxisIndex = -1
    for (i in 0 until nAxes) {
        val minA = intervalA[i].first; val maxA = intervalA[i].second
        val minB = intervalB[i].first; val maxB = intervalB[i].second
        if (maxA <= minB || maxB <= minA) {
            anyStaticSeparator = true
            // Permanent separator: motion parallel to a separating axis can
            // never bring the intervals together.
            if (dts[i] == 0f) return null
        } else {
            val overlap = min(maxA, maxB) - max(minA, minB)
            if (overlap < minStaticOverlap) {
                minStaticOverlap = overlap
                mtvAxisIndex = i
            }
        }
    }

    // Phase 2: temporal SAT. dt == 0 axes already cleared above (non-
    // separating), so they don't constrain tEnter/tExit and we skip them.
    var tEnter = Float.NEGATIVE_INFINITY
    var tExit = Float.POSITIVE_INFINITY
    var enteringAxisIndex = -1
    for (i in 0 until nAxes) {
        val dt = dts[i]
        if (dt == 0f) continue
        val minA = intervalA[i].first; val maxA = intervalA[i].second
        val minB = intervalB[i].first; val maxB = intervalB[i].second
        val numIn = minB - maxA
        val numOut = maxB - minA
        val tIn: Float
        val tOut: Float
        if (dt > 0f) {
            tIn = numIn / dt
            tOut = numOut / dt
        } else {
            tIn = numOut / dt
            tOut = numIn / dt
        }
        if (tIn > tEnter) {
            tEnter = tIn
            enteringAxisIndex = i
        }
        if (tOut < tExit) tExit = tOut
    }

    if (tEnter > tExit) return null
    if (tEnter > 1f) return null
    if (tExit < 0f) return null

    val startingOverlap = !anyStaticSeparator
    if (startingOverlap) {
        if (mtvAxisIndex < 0) return null
        val axis = axes[mtvAxisIndex]
        val minA = intervalA[mtvAxisIndex].first; val maxA = intervalA[mtvAxisIndex].second
        val minB = intervalB[mtvAxisIndex].first; val maxB = intervalB[mtvAxisIndex].second
        val cA = (minA + maxA) * 0.5f
        val cB = (minB + maxB) * 0.5f
        val sign = if (cA >= cB) 1f else -1f
        val normal = axis * sign
        val depen = normal * minStaticOverlap
        val midPoint = Vec2(
            (aWorld.position.x + bWorld.position.x) * 0.5f,
            (aWorld.position.y + bWorld.position.y) * 0.5f,
        )
        return SweepResult(toi = 0f, point = midPoint, normal = normal, depenetration = depen)
    }

    // Tangent-leaving guard (same rationale as axis-aligned sweepRectRect):
    // tEnter < 0 means A was inside (in SAT sense) before t=0 and is now
    // exiting — not a new contact. Without this, a body touching a wall and
    // moving away gets a bogus toi=0 and the script reflects velocity back
    // into the wall every frame, freezing in place.
    if (tEnter < 0f) return null

    val axis = axes[enteringAxisIndex]
    val minA = intervalA[enteringAxisIndex].first; val maxA = intervalA[enteringAxisIndex].second
    val minB = intervalB[enteringAxisIndex].first; val maxB = intervalB[enteringAxisIndex].second
    val cAAtContact = (minA + maxA) * 0.5f + dts[enteringAxisIndex] * tEnter
    val cB = (minB + maxB) * 0.5f
    val sign = if (cAAtContact >= cB) 1f else -1f
    val normal = axis * sign

    // Contact point: A's OBB center at the contact moment. The OBB center is
    // the average of its 4 corners in world space — equivalent to the AABB-
    // envelope center for any rotation (rectangle symmetry).
    var aCenterX = 0f; var aCenterY = 0f
    for (c in cornersA) { aCenterX += c.x; aCenterY += c.y }
    aCenterX /= 4f; aCenterY /= 4f
    val point = Vec2(aCenterX + motion.x * tEnter, aCenterY + motion.y * tEnter)

    return SweepResult(toi = tEnter, point = point, normal = normal)
}

/**
 * Swept circle-vs-rotated-rect via frame transform. The circle is rotation-
 * invariant (a disc looks the same in every frame), so we inverse-rotate the
 * circle center and the motion vector by `-rectWorld.rotation` around the
 * rect's origin and reuse the axis-aligned [sweepCircleRect] on the result.
 * The contact `point`, `normal` and `depenetration` come out in the rect's
 * local frame; we rotate them back by `+rectWorld.rotation` (the depenetration
 * and normal are pure direction vectors; the point is rotated around the
 * rect's origin). See `openspec/changes/kinematic-rotated-sweep/design.md`
 * decision D1.
 */
private fun sweepCircleRotatedRect(
    circle: CircleShape2D, circleWorld: Transform, motion: Vec2,
    rect: RectangleShape2D, rectWorld: Transform,
): SweepResult? {
    val theta = rectWorld.rotation
    val origin = rectWorld.position
    val delta = circleWorld.position - origin
    val localDelta = inverseRotate(delta, theta)
    val localMotion = inverseRotate(motion, theta)
    val localCircleWorld = Transform(
        position = origin + localDelta,
        scale = circleWorld.scale,
        rotation = 0f,
    )
    val localRectWorld = Transform(
        position = origin,
        scale = rectWorld.scale,
        rotation = 0f,
    )
    val local = sweepCircleRect(circle, localCircleWorld, localMotion, rect, localRectWorld)
        ?: return null
    val pointLocalDelta = local.point - origin
    val pointWorld = origin + rotate(pointLocalDelta, theta)
    val normalWorld = rotate(local.normal, theta)
    val depenWorld = rotate(local.depenetration, theta)
    return SweepResult(
        toi = local.toi,
        point = pointWorld,
        normal = normalWorld,
        depenetration = depenWorld,
    )
}

/**
 * Swept rotated-rect-vs-circle via geometric duality with
 * [sweepCircleRotatedRect]: rect moving by `motion` against stationary circle
 * is equivalent to circle moving by `-motion` against stationary rect. The
 * resulting `normal` and `depenetration` are inverted in the original frame
 * (the mover is the rect, so the separation direction flips); `point` is
 * relocated to the circle's surface. See design.md decision D1.
 */
private fun sweepRotatedRectVsCircle(
    rect: RectangleShape2D, rectWorld: Transform, motion: Vec2,
    circle: CircleShape2D, circleWorld: Transform,
): SweepResult? {
    val swapped = sweepCircleRotatedRect(
        circle, circleWorld, -motion,
        rect, rectWorld,
    ) ?: return null
    val r = circle.radius * max(abs(circleWorld.scale.x), abs(circleWorld.scale.y))
    val normal = -swapped.normal
    val point = Vec2(
        circleWorld.position.x + normal.x * r,
        circleWorld.position.y + normal.y * r,
    )
    val depen = -swapped.depenetration
    return SweepResult(toi = swapped.toi, point = point, normal = normal, depenetration = depen)
}
