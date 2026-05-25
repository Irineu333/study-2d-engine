package com.neoutils.engine.physics

import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.tree.SceneTree
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Headless **behavioral** stress harness for [CharacterBody2D.moveAndCollide].
 * Runs the Demo 4 setup at high velocity (80 balls, 12 px, ~2000 px/s →
 * `vmax·dt ≈ 2.7·BALL_SIZE`) across three seeds and asserts that every ball
 * actually moves throughout the run — no body should freeze in place.
 *
 * This is the regression guard that would have caught the two freezes shipped
 * earlier in `kinematic-move-and-collide` (tangent-leaving + spawn-overlap):
 * the previous stress harness measured engine-state metrics (capHits,
 * iterHistogram) and happily reported "healthy" while balls were oscillating
 * in place because frozen-in-place still satisfies those engine invariants.
 * Whoever next touches the sweep math should keep this test passing.
 */
class CcdStressBenchmark {

    @Test
    fun `stress 80 balls across 3 seeds 30 simulated seconds no freezes`() {
        val seeds = listOf(0xC0FFEEL, 0xBADB0FL, 0xDECADEL)
        println("=== CCD behavioral stress: 80 balls × ${seeds.size} seeds × 30s ===")
        for (seed in seeds) {
            val report = runOneSeed(seed)
            println(
                "seed=0x${seed.toString(16).uppercase()}: " +
                    "min=${"%.0f".format(report.displacementMin)}px " +
                    "median=${"%.0f".format(report.displacementMedian)}px " +
                    "max=${"%.0f".format(report.displacementMax)}px | " +
                    "frozen-balls=${report.frozenBalls} " +
                    "worst-stuck-streak=${report.worstStuckStreak}f"
            )
            assertEquals(
                0, report.frozenBalls,
                "seed 0x${seed.toString(16)}: ${report.frozenBalls} ball(s) frozen ≥30 frames " +
                    "while |v|>50 — moveAndCollide regression",
            )
            assertTrue(
                report.displacementMin > 500f,
                "seed 0x${seed.toString(16)}: slowest ball traveled only " +
                    "${report.displacementMin}px in 30s — suspicious",
            )
        }
    }

    private data class Report(
        val displacementMin: Float,
        val displacementMedian: Float,
        val displacementMax: Float,
        val frozenBalls: Int,
        val worstStuckStreak: Int,
    )

    private fun runOneSeed(seed: Long): Report {
        val rng = Random(seed)
        val width = 800f
        val height = 600f
        val ballCount = 80
        val ballSize = 12f
        val wallThickness = 10f

        val root = Node2D()
        root.addChild(makeWall(Vec2(-wallThickness, -wallThickness), Vec2(width + 2f * wallThickness, wallThickness)))
        root.addChild(makeWall(Vec2(-wallThickness, height), Vec2(width + 2f * wallThickness, wallThickness)))
        root.addChild(makeWall(Vec2(-wallThickness, 0f), Vec2(wallThickness, height)))
        root.addChild(makeWall(Vec2(width, 0f), Vec2(wallThickness, height)))

        val balls = mutableListOf<StressBall>()
        repeat(ballCount) { i ->
            val px = ballSize + rng.nextFloat() * (width - ballSize * 2f)
            val py = ballSize + rng.nextFloat() * (height - ballSize * 2f)
            val speed = 800f + rng.nextFloat() * 1200f
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val ball = StressBall(
                initPos = Vec2(px, py),
                initVx = cos(angle) * speed,
                initVy = sin(angle) * speed,
                size = ballSize,
            ).apply { name = "Ball$i" }
            balls += ball
            root.addChild(ball)
        }

        val tree = SceneTree(root).apply { resize(width, height) }
        val loop = GameLoop(tree, NoopRenderer, NoopInput)

        val ticks = 1800
        val nanos = 16_666_666L

        // "Stuck" = |Δpos| < 0.5 px AND |v| > 50 px/s for ≥30 consecutive
        // frames (≈0.5s @ 60Hz). A healthy ball at 800–2000 px/s moves
        // 13–33 px per frame, so genuine stuck-streaks > a handful of frames
        // are the freeze signature the previous harness missed.
        val displacements = FloatArray(ballCount)
        val maxStuckFrames = IntArray(ballCount)
        val currentStuck = IntArray(ballCount)
        val prev = Array(ballCount) { balls[it].position }

        repeat(ticks) {
            loop.tick(nanos)
            for (i in balls.indices) {
                val ball = balls[i]
                val now = ball.position
                val dx = now.x - prev[i].x
                val dy = now.y - prev[i].y
                val step = sqrt(dx * dx + dy * dy)
                displacements[i] += step
                val v = ball.snapshotVelocity()
                val speed = sqrt(v.x * v.x + v.y * v.y)
                if (step < 0.5f && speed > 50f) {
                    currentStuck[i]++
                    if (currentStuck[i] > maxStuckFrames[i]) maxStuckFrames[i] = currentStuck[i]
                } else {
                    currentStuck[i] = 0
                }
                prev[i] = now
            }
        }

        val sorted = displacements.sortedArray()
        val freezeThreshold = 30
        return Report(
            displacementMin = sorted.first(),
            displacementMedian = sorted[sorted.size / 2],
            displacementMax = sorted.last(),
            frozenBalls = maxStuckFrames.count { it >= freezeThreshold },
            worstStuckStreak = maxStuckFrames.max(),
        )
    }

    private fun makeWall(position: Vec2, size: Vec2): StaticBody2D {
        val body = StaticBody2D().apply { transform = Transform(position = position) }
        body.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
        return body
    }

    private class StressBall(
        initPos: Vec2,
        initVx: Float,
        initVy: Float,
        size: Float,
    ) : CharacterBody2D() {

        private var vx: Float = initVx
        private var vy: Float = initVy

        init {
            transform = Transform(position = initPos)
            addChild(CollisionShape2D().apply {
                shape = RectangleShape2D().apply { this.size = Vec2(size, size) }
            })
        }

        override fun onPhysicsProcess(dt: Float) {
            val collision = moveAndCollide(Vec2(vx, vy) * dt) ?: return
            val reflected = Vec2(vx, vy).reflect(collision.normal)
            vx = reflected.x
            vy = reflected.y
        }

        fun snapshotVelocity(): Vec2 = Vec2(vx, vy)
    }
}

