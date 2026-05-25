package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Each ball is a rectangular `CharacterBody2D` with a non-zero local
 * rotation, bouncing inside an axis-aligned arena. Every ball-vs-wall sweep
 * routes through `sweepRotatedRectRotatedRect` (the body is rotated) and
 * every ball-vs-ball sweep too — exercises the rotated CCD path under load.
 *
 * Before `kinematic-rotated-sweep`, `sweepOverlap` bailed out with `null`
 * whenever any input transform had `rotation != 0f`, so this scene's balls
 * would either tunnel through the walls or freeze at the first contact.
 */
private const val BALL_COUNT = 16
private const val BALL_W = 22f
private const val BALL_H = 10f
private const val WALL_THICKNESS = 10f

@Serializable
class RotatedSweepDemo : Node2D() {

    @Transient
    private val rng = Random(0xC0FFEE07L)

    @Transient
    private var instantFps: Float = 0f

    init {
        name = "RotatedSweepDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        val w = tree.width
        val h = tree.height
        addChild(makeWall(Vec2(-WALL_THICKNESS, -WALL_THICKNESS), Vec2(w + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "topWall" })
        addChild(makeWall(Vec2(-WALL_THICKNESS, h), Vec2(w + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "bottomWall" })
        addChild(makeWall(Vec2(-WALL_THICKNESS, 0f), Vec2(WALL_THICKNESS, h)).apply { name = "leftWall" })
        addChild(makeWall(Vec2(w, 0f), Vec2(WALL_THICKNESS, h)).apply { name = "rightWall" })
        repeat(BALL_COUNT) { i ->
            val px = BALL_W + rng.nextFloat() * (w - BALL_W * 2)
            val py = BALL_H + rng.nextFloat() * (h - BALL_H * 2)
            val speed = 80f + rng.nextFloat() * 120f
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val localRotation = rng.nextFloat() * 2f * Math.PI.toFloat()
            addChild(
                RotatedBall(
                    color = hue(i.toFloat() / BALL_COUNT),
                    initPos = Vec2(px, py),
                    initVx = cos(angle) * speed,
                    initVy = sin(angle) * speed,
                    localRotation = localRotation,
                ).apply { name = "RotatedBall$i" }
            )
        }
    }

    private fun makeWall(position: Vec2, size: Vec2): StaticBody2D {
        val body = StaticBody2D().apply { transform = Transform(position = position) }
        body.addChild(
            CollisionShape2D().apply {
                shape = RectangleShape2D().apply { this.size = size }
            }
        )
        return body
    }

    override fun onProcess(dt: Float) {
        if (dt > 0f) instantFps = 1f / dt
    }

    override fun onDraw(renderer: Renderer) {
        val text = "rotated balls: $BALL_COUNT | fps: ${instantFps.roundToInt()}"
        val sceneW = tree?.width ?: 800f
        val textW = renderer.measureText(text, 14f).x
        renderer.drawText(text, Vec2(sceneW - textW - 8f, 18f), size = 14f, color = Color.WHITE)
    }

    private fun hue(h: Float): Color {
        val i = (h * 6f).toInt()
        val f = h * 6f - i
        return when (i % 6) {
            0 -> Color(1f, f, 0f)
            1 -> Color(1f - f, 1f, 0f)
            2 -> Color(0f, 1f, f)
            3 -> Color(0f, 1f - f, 1f)
            4 -> Color(f, 0f, 1f)
            else -> Color(1f, 0f, 1f - f)
        }
    }
}

class RotatedBall(
    color: Color,
    initPos: Vec2,
    initVx: Float,
    initVy: Float,
    localRotation: Float,
) : CharacterBody2D() {

    @Transient
    internal var vx: Float = initVx

    @Transient
    internal var vy: Float = initVy

    @Transient
    private val fillColor: Color = color

    init {
        // Local rotation is the key bit: every sweep against this body's
        // CollisionShape2D routes through the rotated path because
        // `aWorld.rotation = transform.rotation != 0`.
        transform = Transform(position = initPos, rotation = localRotation)
        addChild(
            CollisionShape2D().apply {
                shape = RectangleShape2D().apply { size = Vec2(BALL_W, BALL_H) }
            }
        )
    }

    override fun onPhysicsProcess(dt: Float) {
        val collision = moveAndCollide(Vec2(vx, vy) * dt) ?: return
        val reflected = Vec2(vx, vy).reflect(collision.normal)
        vx = reflected.x
        vy = reflected.y
    }

    override fun onDraw(renderer: Renderer) {
        // Render the OBB outline so the rotation is visible. Local corners
        // (0,0), (W,0), (0,H), (W,H) rotated by world().rotation around
        // world().position.
        val world = world()
        val c = cos(world.rotation)
        val s = sin(world.rotation)
        val locals = listOf(
            Vec2(0f, 0f),
            Vec2(BALL_W, 0f),
            Vec2(BALL_W, BALL_H),
            Vec2(0f, BALL_H),
        )
        val worldPts = locals.map { v ->
            Vec2(v.x * c - v.y * s + world.position.x, v.x * s + v.y * c + world.position.y)
        }
        renderer.drawPolygon(worldPts, fillColor)
        super.onDraw(renderer)
    }
}
