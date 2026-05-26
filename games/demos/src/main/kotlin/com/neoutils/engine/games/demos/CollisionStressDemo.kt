package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.roundToInt
import kotlin.random.Random

private const val BALL_COUNT = 30
private const val BALL_SIZE = 20f
private const val WALL_THICKNESS = 10f

@Serializable
class CollisionStressDemo : Node2D() {

    @Transient
    private val rng = Random(0xC0FFEE)

    @Transient
    private var instantFps: Float = 0f

    init {
        name = "CollisionStressDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        val w = tree.width
        val h = tree.height
        // Walls bracket the play field. Each is a StaticBody2D + CollisionShape2D
        // with a RectangleShape2D positioned in world coordinates; `moveAndCollide`
        // sweeps balls against these and reflects them on the returned normal.
        addChild(makeWall(Vec2(-WALL_THICKNESS, -WALL_THICKNESS), Vec2(w + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "topWall" })
        addChild(makeWall(Vec2(-WALL_THICKNESS, h), Vec2(w + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "bottomWall" })
        addChild(makeWall(Vec2(-WALL_THICKNESS, 0f), Vec2(WALL_THICKNESS, h)).apply { name = "leftWall" })
        addChild(makeWall(Vec2(w, 0f), Vec2(WALL_THICKNESS, h)).apply { name = "rightWall" })
        repeat(BALL_COUNT) { i ->
            val px = BALL_SIZE + rng.nextFloat() * (w - BALL_SIZE * 2)
            val py = BALL_SIZE + rng.nextFloat() * (h - BALL_SIZE * 2)
            val speed = 80f + rng.nextFloat() * 120f
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            addChild(
                Ball(
                    id = i,
                    color = hue(i.toFloat() / BALL_COUNT),
                    initPos = Vec2(px, py),
                    initVx = kotlin.math.cos(angle) * speed,
                    initVy = kotlin.math.sin(angle) * speed,
                ).apply { name = "Ball$i" }
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
        val text = "balls: $BALL_COUNT | fps: ${instantFps.roundToInt()}"
        val textSize = 14f
        val sceneW = tree?.width ?: 800f
        val textW = renderer.measureText(text, textSize).x
        renderer.drawText(
            text,
            Vec2(sceneW - textW - 8f, 18f),
            size = textSize,
            color = Color.WHITE,
        )
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

class Ball(
    val id: Int,
    color: Color,
    initPos: Vec2,
    initVx: Float,
    initVy: Float,
) : CharacterBody2D() {

    @Transient
    internal var vx: Float = initVx

    @Transient
    internal var vy: Float = initVy

    @Transient
    private val baseColor: Color = color

    @Transient
    internal var flashTimer: Float = 0f

    init {
        transform = Transform(position = initPos)
        addChild(
            CollisionShape2D().apply {
                shape = RectangleShape2D().apply { size = Vec2(BALL_SIZE, BALL_SIZE) }
            }
        )
        addChild(
            Circle2D().apply {
                name = "art"
                radius = BALL_SIZE / 2f
                this.color = color
                // Body's AABB lives at local (0,0)..(BALL_SIZE,BALL_SIZE);
                // center the visual circle inside that box.
                transform = Transform(position = Vec2(BALL_SIZE / 2f, BALL_SIZE / 2f))
            }
        )
    }

    override fun onPhysicsProcess(dt: Float) {
        // CCD-correct kinematic move: sweep against every body until contact,
        // reflect velocity on the contact normal, then optionally slide the
        // remainder. For pure bouncing the residual is discarded — the next
        // frame starts fresh from the post-reflect velocity.
        val collision = moveAndCollide(Vec2(vx, vy) * dt) ?: return
        val reflected = Vec2(vx, vy).reflect(collision.normal)
        vx = reflected.x
        vy = reflected.y

        // Cosmetic flash: both sides flash white when ball-vs-ball.
        setArtColor(Color.WHITE)
        flashTimer = 0.15f
        val other = collision.collider
        if (other is Ball) {
            other.setArtColor(Color.WHITE)
            other.flashTimer = 0.15f
        }
    }

    override fun onProcess(dt: Float) {
        if (flashTimer > 0f) {
            flashTimer -= dt
            if (flashTimer <= 0f) setArtColor(baseColor)
        }
    }

    internal fun setArtColor(c: Color) {
        (findChild("art") as? Circle2D)?.color = c
    }
}
