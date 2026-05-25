package com.neoutils.engine.physics

import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.tree.SceneTree
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

private const val FRAME_NANOS = 16_666_666L // 60 Hz
private const val DT = 1f / 60f

private fun runFrames(tree: SceneTree, count: Int, dtNanos: Long = FRAME_NANOS) {
    val loop = GameLoop(tree, NoopRenderer, NoopInput, PhysicsSystem())
    repeat(count) { loop.tick(dtNanos) }
}

private fun recordPositions(node: Node2D, tree: SceneTree, count: Int): List<Vec2> {
    val loop = GameLoop(tree, NoopRenderer, NoopInput, PhysicsSystem())
    val out = ArrayList<Vec2>(count + 1)
    out += node.position
    repeat(count) {
        loop.tick(FRAME_NANOS)
        out += node.position
    }
    return out
}

private class BouncingBall(
    initPosition: Vec2,
    initVelocity: Vec2,
    shape: Shape2D,
    private val localRotation: Float = 0f,
) : CharacterBody2D() {

    var vx: Float = initVelocity.x
    var vy: Float = initVelocity.y
    var lastCollisionFrame: Int = -1
    var frameCounter: Int = 0

    init {
        transform = Transform(position = initPosition, rotation = localRotation)
        addChild(CollisionShape2D().apply { this.shape = shape })
    }

    override fun onPhysicsProcess(dt: Float) {
        frameCounter += 1
        val collision = moveAndCollide(Vec2(vx, vy) * dt) ?: return
        val reflected = Vec2(vx, vy).reflect(collision.normal)
        vx = reflected.x
        vy = reflected.y
        lastCollisionFrame = frameCounter
    }
}

private fun makeWall(position: Vec2, size: Vec2, rotation: Float = 0f): StaticBody2D {
    return StaticBody2D().apply {
        transform = Transform(position = position, rotation = rotation)
        addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
    }
}

private fun rectShape(size: Vec2): RectangleShape2D = RectangleShape2D().apply { this.size = size }

class BehavioralSweepTest {

    @Test
    fun `axis-aligned bounce separates from the wall and does not re-collide for 3 frames`() {
        // Ball moves +x into a wall on the right. After the bounce frame, the
        // ball should be moving left and the distance from the wall must grow
        // monotonically for the next K frames.
        val root = Node()
        val ball = BouncingBall(
            initPosition = Vec2(0f, 0f),
            initVelocity = Vec2(600f, 0f),
            shape = rectShape(Vec2(10f, 10f)),
        )
        val wallX = 50f
        val wall = makeWall(position = Vec2(wallX, -200f), size = Vec2(10f, 400f))
        root.addChild(ball)
        root.addChild(wall)
        val tree = SceneTree(root)

        val positions = recordPositions(ball, tree, count = 30)

        // Detect bounce frame: the velocity sign on x flips. We see it as the
        // first frame where x decreases vs. the previous one (after frames
        // where it was increasing).
        var bounceFrame = -1
        for (i in 2 until positions.size) {
            if (positions[i].x < positions[i - 1].x && positions[i - 1].x >= positions[i - 2].x) {
                bounceFrame = i
                break
            }
        }
        assertTrue(bounceFrame > 0, "expected a bounce to happen; positions=$positions")

        // For 3 frames after the bounce, x must strictly decrease (separating
        // from the wall). If it freezes or flips back, the tangent-leaving
        // guard regressed.
        val K = 3
        for (i in bounceFrame + 1..bounceFrame + K) {
            if (i >= positions.size) break
            assertTrue(
                positions[i].x < positions[i - 1].x,
                "expected continued separation after bounce at frame $i; " +
                    "previous=${positions[i - 1].x}, current=${positions[i].x}",
            )
        }
    }

    @Test
    fun `axis-aligned spawn-overlap separates two bodies in at most 5 frames`() {
        // Two CharacterBody2D rects spawned overlapping: with motion zero the
        // first frame's moveAndCollide must apply depenetration; each
        // subsequent frame keeps separating. By frame 5, AABBs must be
        // disjoint.
        val root = Node()
        val a = BouncingBall(
            initPosition = Vec2(0f, 0f),
            initVelocity = Vec2.ZERO,
            shape = rectShape(Vec2(10f, 10f)),
        )
        val b = BouncingBall(
            initPosition = Vec2(3f, 0f),
            initVelocity = Vec2.ZERO,
            shape = rectShape(Vec2(10f, 10f)),
        )
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)

        runFrames(tree, count = 5)

        val aRect = Rect(a.position, Vec2(10f, 10f))
        val bRect = Rect(b.position, Vec2(10f, 10f))
        assertTrue(
            !aRect.intersects(bRect),
            "expected bodies to separate within 5 frames; a=${a.position}, b=${b.position}",
        )
    }

    // --- kinematic-rotated-sweep behavioral scenarios ---

    @Test
    fun `rotated bounce body separates from rotated wall and reflects velocity`() {
        // Ball locally rotated 30°; wall axis-aligned. The pair routes
        // through sweepRotatedRectRotatedRect (one rotation is non-zero).
        // The bounce should send the body in the -x direction (the wall
        // blocks +x) — verify trajectory direction changes and the body
        // keeps separating for 3 frames after contact.
        val root = Node()
        val rot = (PI / 6.0).toFloat() // 30°
        val ball = BouncingBall(
            initPosition = Vec2(0f, 0f),
            initVelocity = Vec2(600f, 0f),
            shape = rectShape(Vec2(10f, 10f)),
            localRotation = rot,
        )
        val wall = makeWall(position = Vec2(50f, -200f), size = Vec2(10f, 400f))
        root.addChild(ball); root.addChild(wall)
        val tree = SceneTree(root)

        val positions = recordPositions(ball, tree, count = 30)

        var bounceFrame = -1
        for (i in 2 until positions.size) {
            if (positions[i].x < positions[i - 1].x && positions[i - 1].x >= positions[i - 2].x) {
                bounceFrame = i
                break
            }
        }
        assertTrue(bounceFrame > 0, "expected a bounce; positions=$positions")

        val K = 3
        for (i in bounceFrame + 1..bounceFrame + K) {
            if (i >= positions.size) break
            assertTrue(
                positions[i].x < positions[i - 1].x,
                "rotated bounce regressed at frame $i: ${positions[i - 1].x} -> ${positions[i].x}",
            )
        }
    }

    @Test
    fun `rotated spawn-overlap separates two rotated bodies in at most 5 frames`() {
        // Two CharacterBody2D both rotated 45° locally, spawned overlapping.
        // moveAndCollide(Vec2.ZERO) on the first frame must apply the MTV
        // depenetration; subsequent frames keep separating.
        val root = Node()
        val rot = (PI / 4.0).toFloat()
        val a = BouncingBall(
            initPosition = Vec2(0f, 0f),
            initVelocity = Vec2.ZERO,
            shape = rectShape(Vec2(10f, 10f)),
            localRotation = rot,
        )
        val b = BouncingBall(
            initPosition = Vec2(3f, 0f),
            initVelocity = Vec2.ZERO,
            shape = rectShape(Vec2(10f, 10f)),
            localRotation = rot,
        )
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)

        runFrames(tree, count = 5)

        // Distance between origins must exceed initial 3 — they moved apart.
        val dx = a.position.x - b.position.x
        val dy = a.position.y - b.position.y
        val dist = sqrt(dx * dx + dy * dy)
        assertTrue(
            dist > 3f,
            "expected rotated bodies to push apart; final separation=$dist " +
                "(a=${a.position}, b=${b.position})",
        )
    }

    @Test
    fun `rotated arena bouncing covers at least half the free-flight distance`() {
        // Ball rotated 30°, walls rotated 45°. Each sweep collision hits the
        // rotated-rect-vs-rotated-rect path. Same threshold as the axis-
        // aligned arena: if anything freezes, traveled distance falls below
        // half free-flight.
        val root = Node()
        val speed = 300f
        val angle = (PI / 5.0).toFloat()
        val velocity = Vec2(cos(angle) * speed, sin(angle) * speed)
        val ballRot = (PI / 6.0).toFloat()
        val ball = BouncingBall(
            initPosition = Vec2(100f, 100f),
            initVelocity = velocity,
            shape = rectShape(Vec2(10f, 10f)),
            localRotation = ballRot,
        )
        val wallRot = (PI / 4.0).toFloat()
        // Walls forming a coarse rotated frame around (100, 100). They don't
        // form a perfect diamond — what matters is each collision passing
        // through the rotated sweep path without freezing.
        root.addChild(makeWall(Vec2(-10f, -10f), Vec2(220f, 10f), rotation = wallRot)) // tilted top
        root.addChild(makeWall(Vec2(-10f, 200f), Vec2(220f, 10f), rotation = wallRot)) // tilted bottom
        root.addChild(makeWall(Vec2(-10f, 0f), Vec2(10f, 200f), rotation = wallRot))   // tilted left
        root.addChild(makeWall(Vec2(200f, 0f), Vec2(10f, 200f), rotation = wallRot))   // tilted right
        root.addChild(ball)
        val tree = SceneTree(root)

        val positions = recordPositions(ball, tree, count = 60)

        var traveled = 0f
        for (i in 1 until positions.size) {
            val dx = positions[i].x - positions[i - 1].x
            val dy = positions[i].y - positions[i - 1].y
            traveled += sqrt(dx * dx + dy * dy)
        }
        val freeFlight = speed * DT * 60f
        assertTrue(
            traveled > freeFlight * 0.5f,
            "rotated arena ball traveled $traveled but expected > ${freeFlight * 0.5f} " +
                "(free-flight=$freeFlight)",
        )
    }

    @Test
    fun `axis-aligned arena bouncing covers at least half the free-flight distance`() {
        // Ball inside 4 walls, moderate velocity, 60 frames. If freezing or
        // pathological oscillation regresses, total distance traveled falls
        // below half the free-flight expectation.
        val root = Node()
        val speed = 300f
        val angle = (PI / 5.0).toFloat() // arbitrary non-axis-aligned angle
        val velocity = Vec2(cos(angle) * speed, sin(angle) * speed)
        val ball = BouncingBall(
            initPosition = Vec2(100f, 100f),
            initVelocity = velocity,
            shape = rectShape(Vec2(10f, 10f)),
        )
        // Arena: 0..200 on x and y, walls thick = 10.
        root.addChild(makeWall(Vec2(-10f, -10f), Vec2(220f, 10f))) // top
        root.addChild(makeWall(Vec2(-10f, 200f), Vec2(220f, 10f))) // bottom
        root.addChild(makeWall(Vec2(-10f, 0f), Vec2(10f, 200f)))   // left
        root.addChild(makeWall(Vec2(200f, 0f), Vec2(10f, 200f)))   // right
        root.addChild(ball)
        val tree = SceneTree(root)

        val positions = recordPositions(ball, tree, count = 60)

        var traveled = 0f
        for (i in 1 until positions.size) {
            val dx = positions[i].x - positions[i - 1].x
            val dy = positions[i].y - positions[i - 1].y
            traveled += sqrt(dx * dx + dy * dy)
        }
        val freeFlight = speed * DT * 60f
        assertTrue(
            traveled > freeFlight * 0.5f,
            "ball traveled $traveled but expected > ${freeFlight * 0.5f} (free-flight=$freeFlight)",
        )
    }
}
